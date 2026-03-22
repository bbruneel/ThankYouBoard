# AGENTS.md

## General Architecture

This is a Java 25 + Spring Boot 4 application with a React 19 + Vite 7 frontend. See `README.md` for full details.

#### Repository Structure Map
```text
ThankYouBoard/
├── frontend/                     # React 19 + Vite 7 frontend application
├── functions/                    # AWS Lambda handlers & models
├── infra/                        # AWS CDK infrastructure code
└── src/main/java/org/.../        # Spring Boot backend
    ├── config/                   # Configuration classes
    ├── domain/                   # JPA Entities
    ├── repository/               # Spring Data JPA repositories
    ├── service/                  # Business logic 
    ├── web/                      # REST controllers
    └── ...                       # security, validation, observability, images
```

| Service | Port | Command |
|---------|------|---------|
| Spring Boot backend | 8080 | `mvn spring-boot:run` (from repo root) |
| React/Vite frontend | 5173 | `npm run dev` (from `frontend/`) |

### Database Migrations
- **Tool:** Flyway
- **Location:** Place new migration scripts in `src/main/resources/db/migration`
- **Convention:** Follow the standard `V{Version}__{Description}.sql` format (e.g., `V2__Add_new_column.sql`)
- **CRITICAL:** Do NOT manually execute SQL scripts. Let Spring Boot/Flyway run migrations automatically on startup.

### Frontend Development Guidelines
- **Styling:** Use Vanilla CSS for styling (e.g., `ComponentName.css` imported directly into components). **DO NOT** install or try to use TailwindCSS. 
- **State Management:** Prioritize React Hooks (like `useState` and `useEffect`) and Context API over adding external state management libraries (like Redux) unless strictly necessary.

### Testing

- **Backend tests:** `mvn test` (JUnit 5) — uses H2 in-memory DB, no external services needed.
- **Frontend lint:** `cd frontend && npm run lint` — **CRITICAL:** has 2 pre-existing lint errors (not introduced by setup). Ignore them unless asked to fix them.
- **Frontend build:** `cd frontend && npm run build` — **CRITICAL:** has a pre-existing TypeScript type error in `AddPostModal.tsx` (Giphy type mismatch). Ignore it. The Vite dev server still works since it doesn't enforce strict TS compilation.
- **Frontend E2E (Playwright):** Playwright browsers and Linux deps are pre-installed via the update script (`npx playwright install --with-deps`). To run: start the backend with `mvn spring-boot:run -Pe2e -Dspring-boot.run.profiles=e2e` (uses H2 in-memory DB, no PostgreSQL needed), then `cd frontend && npm run test:e2e`. The `-Pe2e` Maven profile adds the H2 runtime dependency; the `-Dspring-boot.run.profiles=e2e` activates `application-e2e.properties`. CI runs these tests in the `e2e` job with H2.

### Gotchas / AI Agent Warnings

- **Supabase from IPv4-only networks:** Supabase uses IPv6; IPv4 is only available on premium. If you see "Network is unreachable" (SQL State 08001) when connecting to Supabase (e.g. on an IPv4-only network), run the backend with the e2e profile and H2 in-memory instead: `mvn spring-boot:run -Pe2e -Dspring-boot.run.profiles=e2e`. No PostgreSQL or Supabase needed; data is in-memory only.
- The backend OTLP/Grafana exporter will log repeated WARN lines about failed export (HTTP 401) unless `grafana.otlp.headers` is configured. This is harmless. Do not try to debug this.
- Giphy endpoints return 503 when `GIPHY_API_KEY` is not set. The rest of the app works normally. Do not try to debug this.
- All API endpoints require the version header: `Accept: application/json; version=1`.
- Anonymous per-post post edit/delete uses `X-Post-Capability-Token` (stored client-side in `sessionStorage` by the UI). Missing token + no JWT => `401`; invalid/expired token => `403`.
- Posts are nested under boards: `GET /api/boards/{boardId}/posts`, `POST /api/boards/{boardId}/posts`.

### AWS CDK infrastructure

The `infra/` directory contains a CDK Java project for deploying to AWS. See `DEPLOYMENT.md` for full instructions. Key points:

- **Compile:** `cd infra && mvn compile`
- **Synth:** `cd infra && cdk synth -c env=test` (also `qa`, `prd`)
- **Deploy:** `cd infra && ./scripts/deploy.sh test` (App Runner + RDS) or `./scripts/deploy.sh test serverless` (Lambda + DynamoDB, no idle cost)
- The CDK project uses Java 25, consistent with the rest of the repo.
- App Runner: `cdk synth` builds a Docker image from the repo-root `Dockerfile`; Docker must be running.
- Serverless: run `cd functions && mvn package` before synth/deploy (no Docker needed).

---

## ☁️ Cursor Cloud VM Specific Instructions
**ATTENTION AGENTS:** Only apply the following instructions if you are running inside the official Cursor Cloud VM. If you are running locally on the user's machine (e.g., inside WSL), DO NOT assume these paths exist out of the box and DO NOT attempt to start PostgreSQL exactly according to these instructions.

### VM System Dependencies (pre-installed in VM)
- **Java 25 (Temurin)** at `/usr/lib/jvm/java-25-temurin` — `JAVA_HOME` is set in `~/.bashrc`
- **Maven 3.9.9** at `/opt/maven`
- **PostgreSQL 16** — local instance, user `postgres` / password `postgres`
- **Node.js 22 + npm** — used by the frontend

### Running the Backend (inside VM only)
The default `application.properties` points to a remote Supabase PostgreSQL instance. For local dev, the file `src/main/resources/application-secret.properties` (git-ignored) overrides the datasource to `localhost:5432` with user/password `postgres/postgres`.

Start PostgreSQL before the backend:
```bash
sudo pg_ctlcluster 16 main start
```

Then start the backend:
```bash
export JAVA_HOME=/usr/lib/jvm/java-25-temurin
mvn spring-boot:run
```

### Cloud-agent secret config guidance
For Cursor Cloud sessions, configure these secrets so Frontend E2E can run without manual env setup:
- `VITE_AUTH0_DOMAIN` (frontend Auth0 domain)
- `VITE_AUTH0_CLIENT_ID` (frontend SPA client id)
- `VITE_AUTH0_AUDIENCE` (frontend audience; should match backend audience)
- `AUTH0_E2E_EMAIL` (Auth0 test user login)
- `AUTH0_E2E_PASSWORD` (Auth0 test user password)

Note: The E2E profile uses a test JwtDecoder, so the backend does not need `AUTH0_DOMAIN` or `AUTH0_AUDIENCE` for E2E.
