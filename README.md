# MonthEndBuddy

An AI-powered personal finance app: import transactions, see where your money goes, plan budgets and savings goals, and ask questions in plain language. The AI copilot answers only from verified backend calculations; it never invents the figures.

> **Status:** Phases 1–2 are complete: accounts and demo data, transactions and CSV import, a dashboard of cash flow and spending, budgets, savings goals with projections, a "can I afford it?" check, and explainable alerts for unusual spending. Phase 3 adds the AI copilot: it answers questions by calling the same backend calculations as the dashboard, shows those figures as evidence cards, flags any amount it could not trace to your data, and only ever *proposes* changes for you to confirm. Its evaluation results are in [docs/ai-evaluation.md](docs/ai-evaluation.md). Next is UI polish and streaming; see [the implementation plan](docs/implementation-plan.md).

## Run it locally

You need [Docker Desktop](https://www.docker.com/products/docker-desktop/). No paid services are required.

```bash
cp .env.example .env
# Set a JWT secret in .env (at least 32 random bytes), for example:
echo "JWT_SECRET=$(openssl rand -base64 48)" >> .env
docker compose up --build
```

Open <http://localhost:8080> and choose **Explore with demo data**. That opens a shared demo account with six months of synthetic transactions, which resets every night. You can also create your own account.

`GROQ_API_KEY` is optional. Without it the app runs normally, but the AI copilot reports that it is unavailable and shows quick insights from your data instead.

### Developing

| What | Command |
|---|---|
| Database only (PostgreSQL on host port 5433) | `docker compose up -d postgres` |
| Backend on :8081 (from `backend/`) | `./mvnw spring-boot:run -Dspring-boot.run.profiles=local` |
| Backend tests (Docker must be running) | `./mvnw verify` |
| Frontend on :5173 (from `frontend/`; proxies `/api` to :8081) | `npm install && npm run dev` |
| Frontend checks | `npm run lint && npm test && npm run build` |

API documentation is at <http://localhost:8081/swagger-ui.html> while the backend runs with the `local` profile.

## Stack

Java 21 · Spring Boot · Spring AI (Groq) · PostgreSQL · Redis · Kafka · React · TypeScript · Vite · Tailwind · Docker Compose

## Documentation

- [Project specification](MonthEndBuddy_Project_Specification.md): what is being built
- [Architecture](docs/architecture.md): system design and key decisions
- [Low-level design](docs/low-level-design.md): classes, services, and patterns
- [Implementation plan](docs/implementation-plan.md): build order and progress

## Data

All demo data is synthetic. Do not commit real account numbers, credentials, or personal financial records.
