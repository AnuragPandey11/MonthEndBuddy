# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Repository state

**Design-only. No code has been written yet.** The repo has three design documents and nothing else, and it is not yet a git repository. MonthEndBuddy will be an AI-powered personal finance app: a Spring Boot modular monolith, a React SPA, PostgreSQL, optional Redis and Kafka, and an LLM agent on Groq that calls tools.

| Document | Role |
|---|---|
| `MonthEndBuddy_Project_Specification.md` | **What** to build: requirements, roadmap (§13), proposed repo layout (§14) |
| `docs/architecture.md` | **How** to build it. Decisions D1–D14 are in §1. **It overrides the spec wherever they differ.** §18 lists the deliberate deviations. |
| `docs/low-level-design.md` | Classes, the service catalogue (§10), enums, commands and DTOs (§11), the exception→HTTP map (§12.2), the package layout (§12.4) |
| `docs/implementation-plan.md` | **Build order.** Work items P0.1–P6.5, each with its deliverables, tests and done-when check, plus the plan-level choices (I1–I11) and the changes it makes to the design (C1–C3). **Start here when implementing.** Follow the plan's rules in §1.1. |
| `docs/image.png` | A rough rendered class diagram. It has garbled names and duplicated boxes. The mermaid diagrams in the LLD are authoritative. |

Both docs under `docs/` are longer than a single Read returns (about 1,100 and 1,900 lines). Read them in pages, or grep for a section number (`## 10.`) before you rely on a section.

**Naming.** The product is **MonthEndBuddy**. The spec was once named "FinPilot", so treat any leftover "FinPilot" as stale. The conventions are:
- Java package root `dev.monthendbuddy`
- config prefix `meb.*`
- Redis keys `meb:{env}:…`
- Kafka topics `meb.<area>.v1`
- client header `X-MEB-Client: web`

## Commands

Nothing is scaffolded yet, so no command below exists. The docs name these tools; **replace this section with the real commands once the project is scaffolded** (implementation-plan P1.1 and P1.6).

- The backend is built with **Maven** through the wrapper (`./mvnw`), not Gradle. `./mvnw verify` will run unit tests (Surefire, `*Test`) and integration tests (Failsafe, `*IT`). The opt-in live-model eval is `./mvnw verify -PaiEval`, with `GROQ_API_KEY` taken from the environment; it never runs in CI because of free-tier limits, and CI uses the fake-model version. The dependency audit is OWASP `dependency-check-maven`.
- Frontend tests use Vitest and Testing Library. Playwright smoke tests run against Compose, axe checks accessibility, and `npm audit` runs in CI. Frontend API types are generated from the OpenAPI spec with `openapi-typescript`.
- Local stack is Docker Compose (planned as `infra/docker-compose.yml`), with three profiles:
  - `core` (default): postgres, backend and frontend, with `meb.cache.enabled=false`, `meb.events.mode=in-process` and `meb.ratelimit.mode=in-memory`. It is sufficient through Phase 4.
  - `full`: core plus redis and kafka, with the cache enabled, `events.mode=kafka` and `ratelimit.mode=redis`.
  - `tools`: adds kafka-ui, plus Prometheus and Grafana if those are added.
- Ports: nginx `:8080` serves the SPA and proxies `/api` to the backend. The API is on `:8081`. Actuator is on `:8082`, which nginx does not proxy. The Vite dev server proxies `/api` the same way, so the setup is same-origin and needs no CORS.

## Invariants

These rules come from several sections of the docs. Each one is a design decision, not a style preference.

**User scoping (arch §5.3, D2)**
- `userId` comes only from the security context, as `CurrentUser`. No DTO, command or tool-input record has a `userId` field.
- Every service method takes `UserId` as its first parameter. Services never read `SecurityContextHolder`.
- Repositories extend `UserOwnedRepository<T>`, which hides `findById` and `findAll`, instead of extending `JpaRepository` directly.
- Another user's resource returns **404**, the same as a missing one.
- The integration suite checks every endpoint and every agent tool with a second user's IDs.

**Money and time (arch §6, D6/D7)**
- Money is `NUMERIC(19,4)` in the database and the `Money(BigDecimal, CurrencyCode)` value object in Java, rounded `HALF_EVEN`. Never use `double`.
- On the wire, money is `{"amount": "12450.00", "currency": "INR"}`, with the amount as a string.
- The frontend formats money (`en-IN` lakh grouping) but **never does arithmetic on it**. Every dashboard figure comes from the API.
- Each user has one currency, INR. Any other currency is rejected with 422 `unsupported_currency`. There is no FX conversion.
- Dates are `LocalDate`. "This month" is resolved in the user's timezone (default `Asia/Kolkata`) through an injectable `Clock`. Query ranges are limited to 24 months.
- Missing data is not zero spending. Aggregates carry a `DataCoverage` (`transactionCount`, `firstDate`, `lastDate`) so the UI and the agent can say "no transactions recorded".
- Formulas such as savings rate, budget %, the goal contribution and the affordability verdict are defined in arch §6.3 and §9.4. They live in framework-free classes (`CashFlowMath`, `BudgetMath`, `SavingsMath`, `AffordabilityCalculator`) and are unit-tested without a Spring context.

**Module boundaries (arch §4, D1)**
- Modules are `common`, `identity`, `transactions`, `budgets`, `goals`, `analytics`, `agent` and `notifications`. Inside a module, code is split into `api/`, `application/`, `domain/` and `infrastructure/`.
- **Nothing depends on `agent`.** The app must work with the AI removed.
- A module may use another module's data only through that module's public API package. Event consumers may depend only on the publisher's `events` package.
- These boundaries are enforced in tests by Spring Modulith `ApplicationModules.verify()` or ArchUnit.
- `TransactionAggregationRepository` is the only class that issues aggregate SQL over `transactions`.
- Controllers never call repositories.

**Agent (arch §10, LLD §8, D3–D5, D13)**
- **The same service method backs each REST endpoint and its agent tool**, so the dashboard and the copilot cannot disagree about a number.
- Tools are read-only or pure calculation. A write appears only as a `proposedActions` entry, which the user confirms through the normal REST endpoint.
- The backend derives financial inputs such as income averages, savings and the emergency buffer. Values the model supplies are labelled `USER_STATED`. Every result carries `provenance` and an `assumptions[]` list labelled `DERIVED`, `USER_STATED`, `USER_PROFILE` or `DEFAULT_ASSUMPTION`.
- `ToolRegistry` is the allow-list, and it never throws. Failures go back to the model as `ToolResult.error` (`UNKNOWN_TOOL`, `INVALID_ARGUMENT`, `NOT_FOUND`).
- Tool output is capped at 50 rows, 120-character descriptions and about 8 KB in total.
- The orchestrator runs its own tool loop. Limits are under `meb.agent.*`. The gateway rules in `docs/adr/0001-…` are binding, because each one comes from a failure seen in the spike:
  - call `ChatModel` only, **never** `ChatClient`, which runs tools automatically in Spring AI 2.0;
  - build each request's options from `getOptions().mutate()`; built from scratch they replace the configured model;
  - never replay assistant metadata, because Groq rejects gpt-oss `reasoning_content` with 400.
- `SpringAiLlmGateway` is the only class that imports Spring AI. `ScriptedLlmGateway` stands in for it in tests and in the offline demo.
- Without `GROQ_API_KEY` the app still boots, and chat returns `PROVIDER_UNAVAILABLE`.
- The system prompt is version-controlled at `agent/prompts/system-v1.md`.
- Transaction descriptions and CSV content are untrusted data, because they can carry prompt injection.

**Events, cache and persistence (arch §11–12, D9–D11)**
- `EventPublisher` is chosen by `meb.events.mode`. `in-process` uses `@TransactionalEventListener(AFTER_COMMIT)`. `kafka` writes an outbox row in the caller's transaction, which `OutboxRelay` then sends to Kafka.
- Both modes use the same handler classes, and every handler extends `IdempotentHandler`, which uses the `processed_events` table.
- **Kafka is not introduced before Phase 5.**
- Redis is optional and fails open. The cache is invalidated by generation: `INCR` a per-user generation in `afterCommit` on any write.
- Readers must read the generation **before** querying the DB. Only aggregates are cached; transaction rows and agent content never are.
- Schema changes go only through Flyway (`V1__identity.sql`, `V2__transactions.sql`, …), and Hibernate runs with `ddl-auto=validate`.
- Partial unique indexes need `CREATE UNIQUE INDEX … WHERE …`, and any `ON CONFLICT` that targets one must repeat that predicate.
- Errors are RFC 9457 `ProblemDetail` responses with a stable `code` and the `traceId`.

**Frontend (arch §14, spec §7)**
- The access token lives only in memory; the refresh token is an httpOnly cookie. On a 401, `apiClient` performs a single queued refresh.
- After a mutation, invalidate the TanStack Query keys `['analytics']` and `['budgets','status']`.
- The copilot draws evidence cards from `evidence[].result` through a registry keyed by tool name.
- Respect `prefers-reduced-motion`. Wrap the app in `<MotionConfig reducedMotion="user">`.
- Never count-up animate financial figures.
- The WebGL shader appears only in the hero card and the copilot header, with a CSS-gradient fallback. Text never sits directly on the shader.

## Verify before implementing

The docs mark these as unconfirmed, so check current documentation instead of copying them:
- Spring AI and Groq were verified on 2026-10-26; see `docs/adr/0001-llm-provider-and-spring-ai-version.md`. The pins are Spring Boot 4.1.2, Spring AI 2.0.2 and Spring Modulith 2.1.2. Groq's base URL is `https://api.groq.com/openai/v1`. The fallback model is `qwen/qwen3.8-27b`. Model quality still has to pass the P3.6 eval gate.
- Current package versions for the stack, and whether Framer Motion is now the `motion` package imported from `motion/react`.

## Data and secrets

Use synthetic data only, including the seeded demo user. Do not log prompts, tool payloads or finance request bodies by default. Environment variables are listed in spec §15 and arch §18.
