# MonthEndBuddy --- Project Specification

> **An AI-powered personal finance agent that turns transaction data
> into clear answers, practical budgets, and goal-based financial
> plans.**

-   **Project type:** Portfolio-grade full-stack application
-   **Primary audience:** Individuals who want to understand spending,
    plan savings, and make better day-to-day financial decisions
-   **Initial deployment target:** Local development with Docker Compose
-   **Cost target:** Build and demonstrate the project using
    free/open-source tools and free-tier services where available
-   **AI model (initial):** `openai/gpt-oss-120b` through GroqCloud,
    subject to current model availability, free-tier limits, and
    tool-calling compatibility

------------------------------------------------------------------------

## 1. Problem Statement

People often have financial information spread across bank statements,
spreadsheets, and expense-tracking applications, but still struggle to
answer practical questions:

-   Where did my salary go this month?
-   Which categories increased the most compared with last month?
-   Can I afford a planned purchase without compromising my savings
    goal?
-   How much should I save each month to reach a target by a deadline?
-   Are any transactions or spending patterns unusual?
-   What concrete actions could help me stay within budget?

Traditional expense trackers mainly display records and charts. MonthEndBuddy
aims to combine a clear finance dashboard with a conversational AI
agent that can retrieve verified financial data, use deterministic
calculation tools, and explain the results in plain language.

### Problem statement in one sentence

**Build a secure, AI-powered personal finance application that converts
transaction history into trustworthy spending insights, budget guidance,
purchase simulations, and actionable savings plans.**

### Project goals

1.  Make it easy to import, review, and categorize transactions.
2.  Provide accurate summaries of income, expenses, budgets, and
    savings.
3.  Let users ask questions in natural language.
4.  Use AI tool calling to access backend finance functions rather than
    inventing financial figures.
5.  Identify unusual spending and meaningful month-over-month changes.
6.  Provide a polished, responsive dashboard suitable for a professional
    portfolio.
7.  Demonstrate practical backend engineering: security, API design,
    testing, asynchronous processing, caching, and observability.

### Non-goals for the first version

-   Connecting directly to real bank accounts.
-   Moving money, making payments, or trading investments.
-   Providing regulated financial, tax, or investment advice.
-   Guaranteeing investment returns.
-   Supporting every currency, bank format, or financial institution at
    launch.

Use synthetic transactions during development and demos. Do not put real
account numbers, credentials, or sensitive personal financial records in
a public repository.

------------------------------------------------------------------------

## 2. Product Vision

MonthEndBuddy should feel like a premium personal-finance workspace---not
a generic admin template with a chatbot attached.

The experience combines three parts:

1.  **A visual finance cockpit:** an at-a-glance view of cash flow,
    spending, budgets, goals, and trends.
2.  **An AI finance copilot:** a conversational interface that answers
    questions using the user's actual application data.
3.  **An action layer:** budget adjustments, savings plans, and purchase
    simulations that users can review before saving.

The AI should explain the figures it uses, show relevant breakdowns, and
distinguish observed facts from estimates and assumptions.

------------------------------------------------------------------------

## 3. Target Users and Core Use Cases

### Primary users

-   Salaried individuals who want to understand monthly cash flow.
-   People working toward savings goals.
-   Users who want conversational explanations rather than manually
    exploring multiple charts.
-   Portfolio reviewers evaluating full-stack engineering and
    responsible AI integration.

### Example questions

-   "Where did most of my salary go this month?"
-   "Compare my food spending with last month."
-   "Why are my expenses higher this month?"
-   "Can I afford a ₹60,000 laptop?"
-   "I want to save ₹1,00,000 in eight months. How much should I save
    each month?"
-   "Show transactions that are unusually high for their category."
-   "If I reduce eating-out expenses by ₹2,000 per month, how much could
    I save in a year?"

The backend must perform arithmetic and retrieve financial facts. The
model's role is to interpret the request, select permitted tools, and
explain the tool results.

------------------------------------------------------------------------

## 4. Functional Requirements

### 4.1 Authentication and user data

-   User registration and login.
-   Password hashing and secure authentication.
-   User-scoped transaction, budget, and goal access.
-   Authorization checks on every finance-data endpoint and agent tool.
-   A user must never be able to retrieve another user's financial data
    by changing an ID in a request.
-   Demo mode may use seeded synthetic data, clearly labelled as demo
    data.

### 4.2 Transactions

-   Create, read, update, and delete transactions.
-   Support income and expense records.
-   Store date, amount, currency, merchant/description, category, notes,
    and source.
-   Filter by date range, category, transaction type, and text.
-   Import transactions from CSV.
-   Validate CSV columns and report row-level import errors.
-   Detect likely duplicate imports using a documented matching
    strategy.
-   Allow users to correct categories suggested by rules or AI.

### 4.3 Dashboard

Display, at minimum:

-   Total income for the selected period.
-   Total expenses.
-   Net cash flow.
-   Savings rate, with its formula documented.
-   Spending by category.
-   Monthly income-versus-expense trend.
-   Recent transactions.
-   Budget usage and warnings.
-   Progress toward savings goals.
-   Relevant spending insights.

All dashboard figures must come from backend API responses, not
hardcoded frontend calculations. The frontend may format and visualize
returned data.

### 4.4 Budgets

-   Create a monthly budget overall and/or per category.
-   Show amount spent, remaining amount, and percentage used.
-   Notify the user when a configurable threshold is reached.
-   Explain budget status using actual transactions for the period.

### 4.5 Savings goals

-   Create a goal with a target amount, current saved amount, and target
    date.
-   Calculate required periodic contributions.
-   Show progress and projected completion based on explicit
    assumptions.
-   Support "what if?" scenarios such as changing the monthly
    contribution.
-   Do not imply that a projection is guaranteed.

### 4.6 AI finance agent

The agent should:

1.  Receive the user's natural-language question.
2.  Identify which financial facts or calculations are needed.
3.  Call only the approved tools it needs.
4.  Validate tool arguments and enforce the authenticated user's scope.
5.  Use tool results to formulate an answer.
6.  Explain important figures and assumptions.
7.  Ask a clarifying question when a request is ambiguous.
8.  State when data is missing or a calculation cannot be performed.
9.  Handle model timeouts, malformed tool arguments, and unavailable
    providers gracefully.

Initial tools:

-   `getMonthlySummary(month, year)`
-   `getExpensesByCategory(startDate, endDate)`
-   `compareMonthlySpending(currentPeriod, previousPeriod)`
-   `getTransactions(filters)`
-   `calculateSavingsProjection(targetAmount, currentSavings, monthlyContribution, deadline)`
-   `simulatePurchase(purchaseAmount, currentSavings, monthlyIncome, monthlyExpenses, goalConstraints)`
-   `getBudgetStatus(period)`
-   `findUnusualTransactions(period)`

These names describe application capabilities; the final Java method
signatures should use typed request/response objects.

**Safety rule:** the LLM must not directly run arbitrary SQL, access
arbitrary URLs, execute code, or bypass service-layer authorization.
Every tool should validate inputs, enforce user scope, and return
structured results.

### 4.7 Spending anomaly insights

For the first version, use explainable rules rather than claiming
advanced machine learning:

-   Compare a transaction against the user's recent spending in that
    category.
-   Flag a category whose spending rises materially from its baseline.
-   Identify unusually large transactions using a configurable
    threshold.
-   Explain which rule triggered the alert.
-   Let the user dismiss an alert or mark it as expected.

A later version may add statistical baselines after enough
representative data exists.

### 4.8 AI response quality

-   Show a loading state while the agent works.
-   Clearly distinguish verified transaction totals from estimates.
-   Include the period and currency for financial values.
-   Do not treat missing transactions as proof that no expense occurred.
-   Do not promise investment performance or present generated text as
    professional financial advice.
-   Log technical errors without logging secrets or unnecessary
    sensitive financial details.

------------------------------------------------------------------------

## 5. Technology Stack

### Frontend

  -----------------------------------------------------------------------
  Technology                          Purpose
  ----------------------------------- -----------------------------------
  React + TypeScript                  Component-based user interface

  Vite                                Frontend development and build
                                      tooling

  Tailwind CSS                        Responsive styling and design
                                      tokens

  Framer Motion (`motion`)            Page transitions, hover states,
                                      layout transitions, and
                                      micro-interactions

  Shader/WebGL background             Subtle animated visual treatment
                                      for selected areas

  Recharts                            Finance charts and visual summaries

  React Router                        Navigation

  TanStack Query                      API state, caching, and request
                                      lifecycle

  React Hook Form + Zod               Form handling and client-side
                                      validation
  -----------------------------------------------------------------------

Use current compatible package versions and verify installation
instructions during implementation. Framer Motion's current
package/documentation may use the `motion` package and imports from
`motion/react`.

### Backend

  -----------------------------------------------------------------------
  Technology                          Purpose
  ----------------------------------- -----------------------------------
  Java 21                             Main application language

  Spring Boot                         REST APIs and application services

  Spring AI                           Model integration, prompts, and
                                      tool calling

  GroqCloud API                       Initial hosted AI model provider

  `openai/gpt-oss-120b`               Initial model candidate; verify
                                      availability and tool-call
                                      behaviour before locking it in

  Spring Security                     Authentication and authorization

  JWT                                 Stateless access-token
                                      authentication, if selected

  Spring Data JPA / Hibernate         Persistence

  Bean Validation                     Request validation

  Flyway Community                    Database schema migrations

  Springdoc OpenAPI                   API documentation

  JUnit 5 + Mockito                   Unit and service tests

  Testcontainers                      Integration tests against real
                                      service dependencies
  -----------------------------------------------------------------------

### Data and messaging

  -----------------------------------------------------------------------
  Technology                          Purpose
  ----------------------------------- -----------------------------------
  PostgreSQL                          Source of truth for users,
                                      transactions, budgets, and goals

  Redis                               Caching, rate limiting, and
                                      short-lived state where appropriate

  Apache Kafka                        Asynchronous transaction-import,
                                      analytics, and notification events

  Docker Compose                      Reproducible local development
                                      environment
  -----------------------------------------------------------------------

### Engineering and delivery

-   Git and GitHub.
-   GitHub Actions for build and test automation.
-   Docker images for application services.
-   Structured application logging.
-   Health/readiness endpoints and clear error responses.
-   A README containing architecture, setup, demo credentials for
    synthetic data, screenshots, and API examples.

### Free-development policy

-   Use the Groq free tier only within its current terms and limits.
-   Do not assume a free API quota is unlimited or permanent.
-   Keep the model provider configurable so it can be changed without
    rewriting finance logic.
-   Do not commit API keys. Store secrets in environment variables or an
    ignored local environment file.
-   PostgreSQL, Redis, and Kafka can run locally with Docker; local use
    does not require a paid hosted database.
-   The project can be developed and demonstrated locally without paid
    hosting.
-   If using a cloud AI provider, review its current retention and
    data-use policies. Use synthetic data for demos.

------------------------------------------------------------------------

## 6. Architecture

### Recommended initial architecture

Start with a **modular Spring Boot application** organized by domain,
while using PostgreSQL, Redis, and Kafka as separate infrastructure
components. This is more achievable and easier to debug than starting
with many independently deployed microservices. Domain modules can later
be extracted into services if the project benefits from that complexity.

``` text
┌────────────────────────────────────────────────────────────┐
│                    React + TypeScript                       │
│  Dashboard | Transactions | Budgets | Goals | AI Copilot    │
└─────────────────────────────┬──────────────────────────────┘
                              │ HTTPS / JSON
                              ▼
┌────────────────────────────────────────────────────────────┐
│                    Spring Boot API                          │
│                                                            │
│  Security / Auth       REST Controllers                     │
│  Transaction Module    Budget Module                        │
│  Goal Module           Analytics Module                     │
│  AI Agent Module       Notification Module (later)          │
└──────────┬──────────────────┬──────────────────┬────────────┘
           │                  │                  │
           ▼                  ▼                  ▼
     PostgreSQL             Redis              Kafka
     Source of truth        Cache              Async events
           ▲                                     │
           │                                     ▼
           │                              Event consumers
           │
┌──────────┴─────────────────────────────────────────────────┐
│ AI Agent Orchestration                                     │
│ Spring AI → Groq model → approved finance tools             │
│                     │                                      │
│                     └── Tools call application services     │
└────────────────────────────────────────────────────────────┘
```

### Domain modules

1.  **Identity:** users, authentication, and authorization.
2.  **Transactions:** transaction CRUD, CSV import, categorization.
3.  **Budgets:** budget definitions and budget usage.
4.  **Goals:** savings goals and projections.
5.  **Analytics:** summaries, category breakdowns, comparisons, anomaly
    rules.
6.  **Agent:** prompt construction, model calls, tool registry,
    conversation state.
7.  **Notifications:** in-app notifications and event-driven alerts
    (later).

### Data ownership and correctness

-   PostgreSQL is the source of truth.
-   Redis is a disposable performance layer, not the only copy of
    finance data.
-   Kafka events are for asynchronous work, not the authoritative
    transaction record.
-   Transaction writes should be committed before downstream consumers
    process their events.
-   Design consumers to be idempotent so duplicate event delivery does
    not duplicate side effects.
-   Consider the transactional outbox pattern if reliable
    database-to-Kafka publication becomes necessary.
-   Define cache invalidation when transactions, budgets, or goals
    change.
-   The AI model must not have direct database credentials or
    unrestricted database access.

### Example question flow

User asks: "Where did most of my salary go this month?"

1.  React sends the message to the authenticated Spring Boot endpoint.
2.  The agent constructs the conversation request with only the
    necessary context.
3.  The model selects `getMonthlySummary` and/or
    `getExpensesByCategory`.
4.  Spring AI dispatches the approved tool calls.
5.  The tools call analytics services, which query PostgreSQL.
6.  Structured results are returned to the model.
7.  The model explains the largest categories and references the actual
    totals.
8.  React displays the answer and, where useful, category cards or a
    chart.

The model is not responsible for inventing or independently calculating
the underlying totals.

------------------------------------------------------------------------

## 7. UI/UX Direction --- "Fynix-inspired" Premium Dashboard

The intended visual direction is a modern, polished finance dashboard
inspired by the user's Fynix reference: a premium, atmospheric interface
with restrained shader effects, smooth Framer Motion interactions, clear
typography, and highly legible financial information. This is a design
direction, not a claim that the exact reference has been reproduced.

### Visual principles

-   Dark-first, premium visual language with an optional light theme
    later.
-   Deep charcoal/ink surfaces, subtle gradients, and a restrained
    electric blue/violet accent.
-   Strong contrast and readable financial figures.
-   Generous spacing, rounded panels, thin borders, and deliberate
    hierarchy.
-   Animated visuals should support the interface---not compete with
    data.
-   Responsive layout from desktop to mobile.
-   Respect `prefers-reduced-motion`.
-   Avoid a generic admin-template look, excessive glassmorphism,
    distracting neon, and animation on every element.

### Shader effects

Use a subtle animated shader/WebGL effect selectively---for example, in
a hero/header region, an AI-copilot panel, or behind the main balance
card. The shader could resemble a slow-moving liquid gradient, soft
aurora, or flowing field.

Implementation choices: - Prefer a maintained React-compatible shader
library if it fits the design and project constraints. - Otherwise use a
small Three.js/WebGL implementation with a clear cleanup lifecycle. -
Provide a static CSS gradient fallback for unsupported devices,
reduced-motion settings, or performance issues. - Avoid rendering an
expensive full-screen shader continuously behind every chart. - Keep all
important text and financial values on solid or sufficiently opaque
surfaces.

### Framer Motion

Use `motion` / Framer Motion for: - Page and section transitions. -
Staggered entrance of dashboard cards. - Small hover and press
feedback. - Animated chart/card layout changes. - Smooth opening of
filters, menus, and the AI side panel. - Chat message appearance and
tool-execution status transitions.

Animations should be short, purposeful, and consistent. Do not animate
the value of a financial figure in a way that could imply an unverified
live balance.

### Dashboard layout

**Left navigation** - MonthEndBuddy logo/wordmark. - Overview. -
Transactions. - Budgets. - Savings goals. - AI Copilot. -
Settings/profile at the bottom.

**Top bar** - Greeting and selected date range. - Search or quick
action. - Notifications. - Profile menu.

**Main overview** - Primary cash-flow or balance summary card. - Income,
expenses, and net cash flow metrics. - Monthly cash-flow chart. -
Category spending breakdown. - Budget progress cards. - Savings goal
progress. - Recent transactions. - AI-generated insights with links to
the underlying breakdown.

**AI Copilot** - Dedicated page or collapsible side panel. - Prompt
suggestions such as "Explain my spending," "Compare this month," and
"Plan a savings goal." - Clear loading/tool-working state. - Responses
with summary, evidence, assumptions, and suggested next actions. -
Charts or compact data cards when structured results are available. -
Explicit empty, error, and provider-unavailable states.

### UI quality requirements

-   Use design tokens for color, spacing, radius, shadow, and
    typography.
-   Keep a consistent icon set.
-   Use accessible labels, keyboard navigation, and visible focus
    states.
-   Ensure responsive tables become cards or scrollable tables on narrow
    screens.
-   Include skeleton/loading states, empty states, error states, and
    success feedback.
-   Test contrast and motion accessibility.
-   Avoid using shader effects as a substitute for strong layout and
    information design.

------------------------------------------------------------------------

## 8. API Design (Initial Draft)

All endpoints should be versioned under `/api/v1`.

  ------------------------------------------------------------------------------------
  Method                  Endpoint                             Purpose
  ----------------------- ------------------------------------ -----------------------
  `POST`                  `/api/v1/auth/register`              Register a user

  `POST`                  `/api/v1/auth/login`                 Authenticate

  `GET`                   `/api/v1/transactions`               List/filter
                                                               transactions

  `POST`                  `/api/v1/transactions`               Create a transaction

  `PUT`                   `/api/v1/transactions/{id}`          Update a transaction

  `DELETE`                `/api/v1/transactions/{id}`          Delete a transaction

  `POST`                  `/api/v1/transactions/import`        Import CSV

  `GET`                   `/api/v1/analytics/summary`          Income, expenses, net
                                                               cash flow

  `GET`                   `/api/v1/analytics/categories`       Category breakdown

  `GET`                   `/api/v1/analytics/trends`           Period comparisons

  `GET`                   `/api/v1/budgets`                    List budgets

  `POST`                  `/api/v1/budgets`                    Create a budget

  `GET`                   `/api/v1/goals`                      List savings goals

  `POST`                  `/api/v1/goals`                      Create a savings goal

  `POST`                  `/api/v1/agent/chat`                 Send a message to the
                                                               agent

  `GET`                   `/api/v1/agent/conversations/{id}`   Retrieve conversation
                                                               history
  ------------------------------------------------------------------------------------

This is an initial contract. Add pagination, validation, consistent
error responses, authorization, and API documentation as endpoints are
implemented.

------------------------------------------------------------------------

## 9. Initial Data Model

### User

-   `id`
-   `email`
-   `password_hash`
-   `created_at`

### Transaction

-   `id`
-   `user_id`
-   `type` --- income or expense
-   `amount`
-   `currency`
-   `description`
-   `merchant`
-   `category`
-   `transaction_date`
-   `source`
-   `import_fingerprint` or equivalent duplicate-detection field
-   `created_at`
-   `updated_at`

### Budget

-   `id`
-   `user_id`
-   `category` (nullable for an overall budget)
-   `period` (for example, monthly)
-   `limit_amount`
-   `currency`
-   `created_at`
-   `updated_at`

### SavingsGoal

-   `id`
-   `user_id`
-   `name`
-   `target_amount`
-   `current_amount`
-   `currency`
-   `target_date`
-   `created_at`
-   `updated_at`

### AgentConversation

-   `id`
-   `user_id`
-   `title`
-   `created_at`
-   `updated_at`

### AgentMessage

-   `id`
-   `conversation_id`
-   `role`
-   `content`
-   `tool_metadata` (structured, optional)
-   `created_at`

Use database constraints and indexes appropriate to the queries. Never
rely only on frontend validation for data integrity.

------------------------------------------------------------------------

## 10. Security and Responsible AI

-   Require authentication for private finance data.
-   Scope every database query and agent tool to the authenticated user.
-   Store password hashes, not plaintext passwords.
-   Keep provider API keys in environment variables or a secret manager.
-   Add rate limits to login and AI endpoints.
-   Validate and limit tool arguments, query ranges, and response sizes.
-   Treat transaction descriptions and imported CSV content as untrusted
    data; do not let embedded text override agent instructions.
-   Do not pass unnecessary personally identifiable or sensitive
    information to the hosted model.
-   Avoid logging full prompts or financial records by default.
-   Make AI-generated insights traceable to data and rules.
-   Use synthetic data for public demos.
-   Do not present projections as guaranteed outcomes or model output as
    professional financial advice.

------------------------------------------------------------------------

## 11. Redis and Kafka: Meaningful Use Cases

### Redis

Use Redis where it solves a real problem: - Cache frequently requested
monthly summaries with short TTLs. - Rate-limit AI requests. - Store
short-lived agent execution state if needed. - Invalidate relevant
caches after transaction or budget changes.

Do not cache sensitive data without a deliberate policy, and do not
treat Redis as the source of truth.

### Kafka

Start with a small number of event types: - `TransactionImported` -
`TransactionCreated` - `BudgetThresholdReached` - `AnomalyDetected`

Potential consumers: - Recalculate analytics. - Run anomaly rules. -
Generate in-app notifications. - Record processing status for imports.

Include event IDs, schema/version information, retries, dead-letter
handling where appropriate, and idempotent consumers. Avoid introducing
Kafka before the basic transaction flow works end-to-end.

------------------------------------------------------------------------

## 12. Testing and Quality Plan

### Unit tests

-   Budget remaining and percentage calculations.
-   Savings-goal contribution calculations.
-   Cash-flow and savings-rate formulas.
-   Purchase affordability scenarios.
-   Category and anomaly rules.
-   Validation and authorization rules.

### Integration tests

-   Repository queries against PostgreSQL.
-   API authentication and user isolation.
-   CSV import and duplicate handling.
-   Redis cache behavior and invalidation.
-   Kafka consumer idempotency.
-   Agent tool invocation and malformed tool arguments.

### AI evaluation

Create a version-controlled test set with known synthetic data and
expected outcomes. Include questions such as: - "What were my total
expenses in September?" - "Which category increased the most compared
with August?" - "Can I afford a purchase of ₹60,000 under these
assumptions?" - "What is the monthly saving required for my target?" -
"What happens if the requested date range has no transactions?" - "Can
the agent access another user's transaction?"

Check numerical correctness, appropriate tool selection, clear
assumptions, refusal to expose another user's data, and graceful
behavior when the model is unavailable. Model output may vary; test the
structured financial results independently of wording.

------------------------------------------------------------------------

## 13. Development Roadmap

### Phase 1 --- Foundation

-   Create the GitHub repository and project README.
-   Scaffold the React frontend and Spring Boot backend.
-   Configure PostgreSQL and Flyway.
-   Add authentication and user-scoped data access.
-   Implement transaction CRUD and basic validation.
-   Add Docker Compose for local dependencies.

**Exit criteria:** a user can log in and manage synthetic transactions.

### Phase 2 --- Finance features

-   CSV import with validation and duplicate handling.
-   Income/expense summaries and category breakdowns.
-   Budgets and savings goals.
-   Dashboard charts and responsive layouts.

**Exit criteria:** all displayed financial figures come from tested
backend calculations.

### Phase 3 --- AI agent

-   Integrate Spring AI with Groq.
-   Verify the selected model's current tool-calling support.
-   Implement three tools first: monthly summary, category breakdown,
    and savings projection.
-   Add tool argument validation, user scoping, and error handling.
-   Build the conversational UI and evidence-oriented responses.

**Exit criteria:** the agent answers selected finance questions using
real application tools and does not fabricate the underlying totals.

### Phase 4 --- Modern UI polish

-   Implement the Fynix-inspired design system.
-   Add restrained shader effects and Framer Motion transitions.
-   Add loading, empty, error, and reduced-motion states.
-   Check accessibility and mobile layouts.

**Exit criteria:** polished desktop and mobile experiences with smooth
but non-distracting animation.

### Phase 5 --- Redis and Kafka

-   Add caching for appropriate read-heavy analytics.
-   Add cache invalidation.
-   Publish transaction events and process them idempotently.
-   Add anomaly rules and notifications.

**Exit criteria:** asynchronous features are tested and recover cleanly
from retries or duplicate messages.

### Phase 6 --- Portfolio readiness

-   Add integration tests and an AI evaluation set.
-   Document architecture and important design decisions.
-   Add API documentation and sample CSV data.
-   Add screenshots and a short demo video.
-   Document local setup, environment variables, model limitations, and
    known issues.
-   Add CI for tests and build validation.

**Exit criteria:** a reviewer can clone the project, follow the setup
guide, and reproduce the main demo without paid services.

------------------------------------------------------------------------

## 14. Repository Structure (Proposed)

``` text
monthendbuddy/
├── backend/
│   ├── src/main/java/.../
│   │   ├── identity/
│   │   ├── transactions/
│   │   ├── budgets/
│   │   ├── goals/
│   │   ├── analytics/
│   │   ├── agent/
│   │   └── common/
│   ├── src/main/resources/
│   │   ├── application.yml
│   │   └── db/migration/
│   └── src/test/
├── frontend/
│   ├── src/
│   │   ├── app/
│   │   ├── components/
│   │   ├── features/
│   │   ├── pages/
│   │   ├── hooks/
│   │   └── lib/
│   └── public/
├── infra/
│   └── docker-compose.yml
├── docs/
│   ├── architecture.md
│   ├── api.md
│   └── ai-evaluation.md
├── sample-data/
│   └── synthetic-transactions.csv
├── .env.example
├── .gitignore
└── README.md
```

Adjust package names and folder layout during implementation. Never
commit `.env` files containing real secrets.

------------------------------------------------------------------------

## 15. Environment Configuration

Keep configuration external to source code. A local `.env.example` may
document variable names without real values:

``` dotenv
SPRING_PROFILES_ACTIVE=local
DB_URL=jdbc:postgresql://localhost:5432/monthendbuddy
DB_USERNAME=monthendbuddy
DB_PASSWORD=replace-me
REDIS_HOST=localhost
REDIS_PORT=6379
KAFKA_BOOTSTRAP_SERVERS=localhost:9092
GROQ_API_KEY=replace-me
AI_MODEL=openai/gpt-oss-120b
```

This is an example configuration, not a complete production security
setup. Confirm the correct Spring AI properties and model identifier
against the versions selected for the project. Keep real `.env` files
ignored by Git.

------------------------------------------------------------------------

## 16. Definition of Done for the First Portfolio Demo

The first meaningful demo is complete when:

-   [ ] A user can log in and see their own synthetic finance data.
-   [ ] CSV transactions can be imported and duplicates are handled.
-   [ ] Dashboard totals match backend calculations.
-   [ ] Users can create a budget and savings goal.
-   [ ] The AI can answer a few finance questions through approved
    backend tools.
-   [ ] A purchase or savings simulation exposes its assumptions.
-   [ ] Another user's data cannot be accessed through API or AI tools.
-   [ ] AI provider failure produces a useful error instead of a broken
    page.
-   [ ] The UI is responsive and includes polished shader and Framer
    Motion effects.
-   [ ] Unit and integration tests cover core financial calculations and
    access controls.
-   [ ] The app runs locally from documented instructions.
-   [ ] No secrets or real personal financial records are committed.

------------------------------------------------------------------------

## 17. Resume Positioning

Suggested project heading:

**MonthEndBuddy --- AI-Powered Personal Finance Agent**\
*Java, Spring Boot, Spring AI, Groq, PostgreSQL, Redis, Kafka, React,
TypeScript, Docker*

Potential resume bullet template---use only after the corresponding
functionality is implemented:

-   Built an AI-powered personal finance platform using Spring Boot and
    React, enabling natural-language spending analysis through model
    tool calling and deterministic financial services.
-   Designed transaction, budget, and savings-goal workflows backed by
    PostgreSQL, with Redis caching and Kafka-based asynchronous
    processing.
-   Implemented user-scoped authorization, CSV import validation,
    financial calculation tests, and AI evaluation scenarios to improve
    reliability and prevent cross-user data exposure.

Do not claim performance gains, user counts, production scale, or
completed microservices unless you measure or implement them.

------------------------------------------------------------------------

## 18. Key Engineering Decisions

1.  **Accuracy before cleverness:** financial calculations belong in
    tested Java services.
2.  **Start modular, then extract:** demonstrate good boundaries before
    adding distributed-system complexity.
3.  **Keep the model replaceable:** do not tie finance-domain logic to a
    single AI provider.
4.  **Use tools, not unrestricted database access:** the model only
    invokes explicitly approved functions.
5.  **Build the core product before infrastructure polish:** transaction
    correctness and a useful dashboard come before Kafka-heavy
    workflows.
6.  **Treat free tiers as optional development conveniences:** provider
    limits and terms may change.
7.  **Make the UI memorable but usable:** shaders and animation enhance
    a readable finance dashboard; they must not obscure data.
8.  **Build for demonstrability:** synthetic data, a reproducible local
    setup, a clean README, tests, screenshots, and a short walkthrough
    are part of the project.

------------------------------------------------------------------------

## 19. Official Documentation

Check current documentation while implementing because versions, model
IDs, free-tier quotas, and configuration properties may change.

-   Spring Boot: https://spring.io/projects/spring-boot
-   Spring AI: https://spring.io/projects/spring-ai
-   Groq API documentation: https://console.groq.com/docs
-   Groq rate limits: https://console.groq.com/docs/rate-limits
-   PostgreSQL: https://www.postgresql.org/docs/
-   Redis: https://redis.io/docs/
-   Apache Kafka: https://kafka.apache.org/documentation/
-   React: https://react.dev/
-   Vite: https://vite.dev/guide/
-   Tailwind CSS: https://tailwindcss.com/docs
-   Motion for React: https://motion.dev/docs/react
-   Three.js: https://threejs.org/docs/
-   Docker Compose: https://docs.docker.com/compose/

------------------------------------------------------------------------

## Final Product Principle

**MonthEndBuddy should be an actual finance application with an AI
agent---not merely a chatbot wrapped around a dashboard.** The finance
data, calculations, security, and workflows must work independently of
the model. The AI adds a natural-language interface to those reliable
capabilities, while the visual design makes the product feel modern and
memorable.
