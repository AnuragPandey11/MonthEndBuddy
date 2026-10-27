# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Repository state

**Phases 1 and 2 complete**: identity, transactions, CSV import, analytics, budgets, goals, affordability, anomaly alerts, demo data, and the dashboard and finance pages. **Phase 3 complete**: the AI agent, with the tool loop, nine tools, the copilot page and the eval set. `gpt-oss-120b` passed the live gate at 10/11. **Phase 4 in progress:** P4.1 (SSE streaming), P4.2 (design system and layout), P4.3 (motion, with the light bento restyle) and P4.3a (savings clarity and the month-end review) are done; next is P4.4 (shader). Progress is tracked in `docs/implementation-plan.md`; each work item heading carries its status. MonthEndBuddy is an AI-powered personal finance app: a Spring Boot modular monolith, a React SPA, PostgreSQL, optional Redis and Kafka, and an LLM agent on Groq that calls tools. GitHub: `AnuragPandey11/MonthEndBuddy`, default branch `main`.

| Document | Role |
|---|---|
| `MonthEndBuddy_Project_Specification.md` | **What** to build: requirements, roadmap (§13), proposed repo layout (§14) |
| `docs/architecture.md` | **How** to build it. Decisions D1–D14 are in §1. **It overrides the spec wherever they differ.** §18 lists the deliberate deviations. |
| `docs/low-level-design.md` | Classes, the service catalogue (§10), enums, commands and DTOs (§11), the exception→HTTP map (§12.2), the package layout (§12.4) |
| `docs/implementation-plan.md` | **Build order.** Work items P0.1–P6.5, each with its deliverables, tests and done-when check, plus the plan-level choices (I1–I11) and the changes it makes to the design (C1–C4). **Start here when implementing.** Follow the plan's rules in §1.1. |
| `docs/image.png` | A rough rendered class diagram. It has garbled names and duplicated boxes. The mermaid diagrams in the LLD are authoritative. |

Both docs under `docs/` are longer than a single Read returns (about 1,100 and 1,900 lines). Read them in pages, or grep for a section number (`## 10.`) before you rely on a section.

**Naming.** The product is **MonthEndBuddy**. The spec was once named "FinPilot", so treat any leftover "FinPilot" as stale. The conventions are:
- Java package root `dev.monthendbuddy`
- config prefix `meb.*`
- Redis keys `meb:{env}:…`
- Kafka topics `meb.<area>.v1`
- client header `X-MEB-Client: web`

## Commands

**Backend** (run from `backend/`; Maven through the wrapper, not Gradle; Docker must be running for integration tests):

| Task | Command |
|---|---|
| Full build: unit tests (Surefire, `*Test`/`*Tests`) and integration tests (Failsafe, `*IT`, Testcontainers PostgreSQL) | `./mvnw verify` |
| Unit tests only | `./mvnw test` |
| One unit test class or method | `./mvnw test -Dtest=ModularityTests` or `-Dtest='ModularityTests#moduleBoundariesAreRespected'` |
| One integration test | `./mvnw verify -Dit.test=ApplicationIT -Dtest=skip -Dsurefire.failIfNoSpecifiedTests=false` |
| Run against the Compose database | `./mvnw spring-boot:run -Dspring-boot.run.profiles=local` |
| Run against a throwaway Testcontainers database | `./mvnw spring-boot:test-run` |
| Regenerate `sample-data/synthetic-transactions.csv` after changing the generator | `./mvnw -Psample-data process-classes` |

- Compose (`compose.yaml` at the **repo root**, so it reads the root `.env`):
  - `docker compose up --build` runs the whole app on :8080 and needs `JWT_SECRET` in `.env`.
  - `docker compose up -d postgres` runs only the database: PostgreSQL 17 on **host port 5433**, because a local PostgreSQL may already hold 5432. The backend's defaults match it.
  - Bind mounts from `~/Desktop` fail on Docker Desktop for macOS, so config is baked into images instead. The frontend image gets `infra/nginx/default.conf` through the `infra` build context.
- With the `local` profile, logs are plain text and Swagger UI is at `http://localhost:8081/swagger-ui.html`. Otherwise logs are ECS JSON and the API docs are off.
- Integration tests extend `support/IntegrationTest`, which provides one cached context with a random API port and a random management port.
- `ModularityTests` enforces the `allowedDependencies` declared in each module's `package-info.java`. Change those declarations only together with architecture §4.1.
- **Every new endpoint that takes the ID of a user-owned resource needs a row in `UserIsolationIT.cases()`, and every new copilot tool needs one in `UserIsolationIT.toolCases()`.** Their coverage guards fail the build otherwise.
- Shared test helpers live in `src/test/java/dev/monthendbuddy/support/` (`ApiClient`, `MutableClock`, `IntegrationTest`) and `transactions/TransactionFixtures`.
- Agent tests run against `ScriptedLlmGateway`: the `test` profile sets `meb.agent.provider=scripted`. Autowire it, call `reset()`, and queue replies with `thenReply` or `then(request -> …)`.
- The live model eval is `GROQ_API_KEY=… ./mvnw verify -PaiEval`. It runs only `AiEvalLiveIT` and writes `target/ai-eval/report.md`. The key comes from the environment, and the run never happens in CI. Its pass bar is in `docs/ai-evaluation.md`; don't change it to fit a result. `AiEvalScriptedIT` runs the same cases on every build.
- Planned, not built yet:
  - `-Pdependency-check`;
  - Playwright and axe (P6.1).

**Frontend** (run from `frontend/`; Node 24 or later; React 19, React Router 8, TanStack Query 5, Tailwind 4, Vitest 5):

| Task | Command |
|---|---|
| Dev server on :5173 (proxies `/api` to :8081; run the backend with the `local` profile) | `npm run dev` |
| All tests, or one file | `npm test`, `npx vitest run src/lib/api/client.test.ts` |
| Lint, format check, type-check, build (CI runs all four) | `npm run lint`, `npm run format:check`, `npm run typecheck`, `npm run build` |
| Regenerate API types after a backend contract change (backend running, `local` profile) | `npm run gen:api` |

- TypeScript is pinned to 5.9.x on purpose: `typescript-eslint` and `openapi-typescript` don't support 6 or 7 yet.
- `src/lib/api/schema.d.ts` is generated; never edit it by hand. `src/lib/api/types.ts` narrows it.
- The browser never does arithmetic on money: `lib/money.ts` only formats the API's decimal strings.
- Compose profiles (`full` and `tools` arrive in P5.1) are:
  - `core` (default): postgres, backend and frontend. The flags come from `infra/env/core.env`: cache off, in-process events, in-memory rate limits. It is sufficient through Phase 4.
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
- `POST /agent/chat` streams SSE under `Accept: text/event-stream` and returns JSON otherwise, with one orchestrator path for both (arch §10.7):
  - each stream ends with exactly one `final` or `error` event;
  - admission errors are still `ProblemDetail` responses;
  - a client disconnect never cancels the turn.
  Security permits `DispatcherType.ASYNC` so the stream can complete.
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
- Style with the design tokens in `styles.css` `@theme`, such as `rounded-card`, `text-title`, `p-card` and `shadow-overlay`, and the primitives in `components/` (`Card`, `CardHeader`, `Chip`, `Figure`, `PageHeader`, `DataList`, `Popover`, `buttonClass`), not raw values. Animate with the tokens in `lib/motion.ts`, and prefer transforms, so reduced motion turns them off. Vitest runs with `css: false`, so a misspelt utility fails silently. Grep the built CSS for any new token class.
- The WebGL shader appears only in the hero card and the copilot header, with a CSS-gradient fallback. Text never sits directly on the shader.

## Verify before implementing

The docs mark these as unconfirmed, so check current documentation instead of copying them:
- Spring AI and Groq were verified on 2026-10-26; see `docs/adr/0001-llm-provider-and-spring-ai-version.md`. The pins are Spring Boot 4.1.2, Spring AI 2.0.2 and Spring Modulith 2.1.2. Groq's base URL is `https://api.groq.com/openai/v1`. The fallback model is `qwen/qwen3.8-27b`. Model quality still has to pass the P3.6 eval gate.
- Current package versions for the stack. (Motion was confirmed as the `motion` package, 14.7.0, imported from `motion/react`.)

## Data and secrets

Use synthetic data only, including the seeded demo user. Do not log prompts, tool payloads or finance request bodies by default. Environment variables are listed in spec §15 and arch §18.
