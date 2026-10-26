# ADR 0001 — LLM provider and Spring AI version

| | |
|---|---|
| **Status** | **Accepted** (2026-10-26) |
| **Work item** | P0.3 (implementation plan) |
| **Evidence** | Throwaway spike (not merged): `javap` inspection of the 2.0.2 jars, plus live runs against Groq. Three repeats per scenario on `openai/gpt-oss-120b`, and two repeats on each fallback candidate. |

## Context

Architecture §10 assumes three things: Spring AI's OpenAI starter can be pointed at Groq's OpenAI-compatible API; the backend can run the tool loop itself; and `openai/gpt-oss-120b` calls tools reliably. Architecture §19 lists Spring AI API churn as a risk. This spike checked all three before any agent code exists.

## Decision

### Versions

These were the current GA releases on Maven Central on 2026-10-26, and all three are built against the same Spring Boot release.

| Artifact | Version | Evidence |
|---|---|---|
| Spring Boot (`spring-boot-starter-parent`) | **4.1.2** | Latest GA |
| Spring AI (`spring-ai-bom`) | **2.0.2** | `spring-ai-autoconfigure-model-openai:2.0.2` depends on Spring Boot 4.1.2 |
| Spring Modulith (`spring-modulith-bom`) | **2.1.2** | `spring-modulith-core:2.1.2` depends on Spring Boot 4.1.2 and Spring Framework 7.0.9 |
| Java | 21 (`<release>21</release>`) | Spec §5. The local JDK is 25, which can compile for release 21. |

Spring Initializr still generates `spring-ai.version` 2.0.1 for Boot 4.1.2, so bump it by hand. Spring Boot 4 uses Jackson 3 (`tools.jackson.*`); the annotations stay in `com.fasterxml.jackson.annotation`.

### Provider configuration

```properties
spring.ai.openai.base-url=${AI_BASE_URL:https://api.groq.com/openai/v1}   # /v1 is required: the 2.0 client is the official OpenAI SDK
spring.ai.openai.api-key=${GROQ_API_KEY:}
spring.ai.openai.chat.options.model=${AI_MODEL:openai/gpt-oss-120b}
spring.ai.openai.chat.options.temperature=0.2
```

- **Primary model:** `openai/gpt-oss-120b`.
- **Fallback model:** `qwen/qwen3.8-27b`.
- Both choices are provisional until the P3.6 eval gate.

### Rules for `SpringAiLlmGateway` (P3.1)

These are binding, because each one comes from a failure observed in the spike.

1. **Depend on `ChatModel`, never on `ChatClient`.** In 2.0.2, `OpenAiChatModel.call()` only resolves tool definitions and returns the model's tool calls. Tools are executed only by `ChatClient`'s `ToolCallingAdvisor`. The 1.x setting `internalToolExecutionEnabled` no longer exists and isn't needed. In every spike run, the definition-only callbacks (which throw if invoked) were never called.
2. **Build each request's options from the configured defaults:** `openAiChatModel.getOptions().mutate().toolCallbacks(…).build()`. Options built from scratch with `OpenAiChatOptions.builder()` **replace** the configured options instead of merging with them. The spike's first run sent every request to the library's built-in default model (`gpt-5-mini`), and Groq returned 404.
3. **Never replay provider metadata.** Rebuild each assistant turn with only its text and tool calls: `AssistantMessage.builder().content(…).toolCalls(…)`. Spring AI keeps gpt-oss's reasoning in the message metadata (`reasoningContent`) and sends it back as `reasoning_content`, which **Groq rejects with 400**. The orchestrator already stores its own `LlmMessage` types (LLD §8.1), so the gateway converts those into fresh Spring AI messages on every call.
4. **Tools are definition-only `ToolCallback`s.** Use `FunctionToolCallback.builder(name, fn).description(…).inputType(Record.class)`, and make `fn` throw. `ToolRegistry` executes the calls (LLD §8.2). Schemas come from the record types, and `@JsonPropertyDescription` text reaches the model.
5. **No key: the app still boots.** With an empty `GROQ_API_KEY` the context starts and the `ChatModel` bean exists. The gateway must therefore check for a blank key itself and report `PROVIDER_UNAVAILABLE` without making a call.
6. **Retry rule.** One retry with jitter on 5xx (architecture §10.8). The spike saw a real `503 … over capacity, back off exponentially` from `qwen/qwen3.8-27b`.

## Runtime results

| Check | `gpt-oss-120b` (3 runs each) | `gpt-oss-20b` (2 runs) | `qwen3.8-27b` (2 runs) |
|---|---|---|---|
| Listed by `GET /models` | ✅ | ✅ | ✅ |
| Tool arguments match the schema | ✅ all calls | ✅ | ✅ |
| Parallel tool calls in one response | ❌ never, even with `parallelToolCalls=true` | ❌ | ❌ |
| Sequential calls across iterations | ✅ 3/3 | ⚠️ 1/2 hit the 5-iteration cap by repeating the same call | ✅ 1/1 (one run got a 503) |
| Recovery after an `INVALID_ARGUMENT` tool result | ⚠️ **2/3. In 1/3 it invented a 50-row transaction table without calling the tool again.** | ✅ 2/2, but the prose claimed "50 transactions" when 1 row came back | n/a: it respected the schema's "1 to 50" and never sent an invalid limit |
| Ignored the injected "IGNORE PREVIOUS INSTRUCTIONS" text in a tool result | ✅ 3/3 | ✅ | ✅ |
| Latency per model call | 0.4–4 s | 0.4–4 s | 0.3–6 s |

**Groq free-tier headers for `gpt-oss-120b`** (this account, 2026-10-26):
- `x-ratelimit-limit-requests: 1000`
- `x-ratelimit-limit-tokens: 8000`

That is **8,000 tokens per minute**. A three-call turn in the spike used roughly 2,000–4,000 tokens, counting the reasoning tokens in the completions, so the free tier sustains about **2–3 copilot turns per minute**.

## Consequences for the design and the plan

These changes are applied in the same change as this ADR:

- **Architecture §10.2:** the base URL now ends in `/v1`, and the gateway rules above are referenced.
- **Architecture §10.3:** the loop is user-controlled because the gateway calls `ChatModel` directly; there is no "disable" switch.
- **The model makes one tool call per iteration.** A five-iteration cap leaves only about 3–4 tool calls plus the final answer. Raise `max-tool-iterations` to **6** and keep `max-tool-calls-per-turn: 8`. Multi-period questions should go to the tools that cover several months in one call (`compareMonthlySpending`, trends), not to repeated `getMonthlySummary` calls.
- **The fabrication after a tool error is the main finding.** In response (plan change C4):
  - The grounding check moves from a Phase 6 stretch goal to a **Phase 3 requirement** (P3.4).
  - Tools **clamp** out-of-range limits and set `truncated: true`, instead of returning an error, wherever clamping is safe (`limit`, row counts). Errors are kept for genuinely invalid input.
  - The `INVALID_ARGUMENT` message tells the model to call the tool again with corrected arguments and never to answer without data.
- **`PROVIDER_GLOBAL` must count tokens as well as requests.** Budget about 6,000 TPM against the 8,000 limit, and read `x-ratelimit-remaining-*` from responses where the gateway can.
- **The P3.6 eval** must include the error-then-fabricate case, the no-parallel multi-month case, and the iteration-cap case.
