# thankyouboard

[![GitHub release](https://img.shields.io/github/v/release/bbruneel/java-25-spring-boot-4?include_prereleases&sort=semver)](https://github.com/bbruneel/java-25-spring-boot-4/releases)
[![Build status](https://github.com/bbruneel/java-25-spring-boot-4/actions/workflows/build.yml/badge.svg)](https://github.com/bbruneel/java-25-spring-boot-4/actions/workflows/build.yml)
[![Codecov](https://codecov.io/gh/bbruneel/java-25-spring-boot-4/graph/badge.svg)](https://codecov.io/gh/bbruneel/java-25-spring-boot-4)
[![Contributors](https://img.shields.io/github/contributors/bbruneel/java-25-spring-boot-4)](https://github.com/bbruneel/java-25-spring-boot-4/graphs/contributors)

> **Badge setup:** Build status uses the [GitHub Actions workflow](.github/workflows/build.yml) (runs `mvn test`). For Codecov, connect this repo at [codecov.io](https://codecov.io) and add the upload step to the workflow if you want coverage reporting.

Sandbox project for **Java 25** and **Spring Boot 4**, with a **React + Vite** frontend. The backend is a REST API (message boards, Giphy proxy, observability); the frontend runs as a separate dev server and talks to the API via a proxy.

---

## What the app does

- **Frontend (React + Vite)** – **Login-required dashboard** to list **your** boards and create new ones; board view to read posts and add posts (with optional Giphy GIF picker) is **anonymous**. Served at `http://localhost:5173` in dev; API calls are proxied to the Spring Boot backend.
- **Backend (Spring Boot, port 8080)** – REST API only (no server-rendered HTML):
  - **Boards** – Create and list message boards (title, recipient) **scoped to the authenticated user** (Auth0 `sub`). Edit and delete are owner-only. Board creation is capped per owner (default `100`, configurable). Stored in PostgreSQL.
  - **Posts** – Create posts on a board (author, message text, optional Giphy URL). List posts by board, ordered by creation time. Per-board post count is capped (default `100`, configurable).
  - **Giphy** – Server-side proxy for [Giphy API](https://developers.giphy.com/docs/api): `GET /api/giphy/search?q=...` and `GET /api/giphy/trending` (requires API key).
- **API versioning** – All API endpoints use version `1` via `Accept: application/json; version=1` (required).
- **Observability** – Traces, metrics, and logs exported to Grafana Cloud over OTLP; trace IDs in responses and logback for correlation.

---

## Tech stack

| Layer | Technology |
|-------|------------|
| **Frontend** | **React 19**, **Vite 7**, TypeScript, React Router, Lucide React, Giphy React components |
| Backend runtime | **Java 25** |
| Backend framework | **Spring Boot 4.0.x** (Web MVC, Data JPA, Security, RestClient, OpenTelemetry) |
| ORM | **Hibernate ORM 7.2** (JPA) |
| Database | **PostgreSQL** (via [Supabase](#supabase)) |
| Migrations | **Flyway** (PostgreSQL) |
| Security | **Auth0 (JWT)** + Spring Security OAuth2 Resource Server (secure board list/create; anonymous board views and posts) |
| Observability | **OpenTelemetry** (traces, metrics) + **Logback OTLP appender** → Grafana Cloud |
| Optional | **Giphy API** (server-side only) |

---

## Prerequisites

- **Java 25** and **Maven 3.9+** (backend)
- **Node.js** and **npm** (frontend)
- **PostgreSQL** database (e.g. [Supabase](#supabase))
- **Auth0 tenant + SPA application** (required for the dashboard and board creation)
- Optional: **Giphy API key** for `/api/giphy/*`
- Optional: **Grafana Cloud** OTLP endpoint and auth for traces/metrics/logs

---

## Authentication (Auth0) and access rules

- **Secured (requires `Authorization: Bearer <access_token>`):**
  - `GET /api/boards` (lists **only boards created by the logged-in user**)
  - `POST /api/boards` (creates a board owned by the logged-in user)
  - `PUT /api/boards/{id}` (update title/recipient of a board you own)
  - `DELETE /api/boards/{id}` (delete a board you own)
  - `GET /api/boards/{id}/pdf` (synchronous PDF export; legacy, still available)
  - `POST /api/boards/{id}/pdf-jobs` (start async PDF generation; returns 202 with job status)
  - `GET /api/boards/{id}/pdf-jobs/{jobId}` (poll job status; returns `downloadUrl` when `SUCCEEDED`)
  - `GET /api/boards/{id}/pdf-jobs/{jobId}/download` (download the generated PDF; Spring Boot only)
- **Anonymous (no login required):**
  - `GET /api/boards/{id}`
  - `GET /api/boards/{id}/posts`
  - `POST /api/boards/{id}/posts`
  - `PUT /api/boards/{id}/posts/{postId}` (anonymous per-post edit)
  - `DELETE /api/boards/{id}/posts/{postId}` (anonymous per-post delete)
  - `GET /api/giphy/search`, `GET /api/giphy/trending`

Anonymous per-post edit/delete uses an expiring capability token:
- On post creation, the backend includes `editDeleteToken` in the response; the frontend stores it in `sessionStorage`.
- For edit/delete requests, the client must send `X-Post-Capability-Token: <editDeleteToken>`.
- If the request has no JWT and the capability token header is missing/blank, the API returns `401 Unauthorized`.
- If the capability token is present but invalid/expired, the API returns `403 Forbidden`.

Board ownership is stored as `owner_id` in PostgreSQL (Auth0 `sub` claim).  
For `GET /api/boards/{id}`, the response includes a derived `canEdit` flag (true only when the caller is authenticated and owns the board), so the UI can safely decide whether to show owner-only controls (edit/delete/PDF download) without exposing the raw owner id.

### Auth0 application settings (local dev)

Configure your Auth0 SPA application with at least:

- **Allowed Callback URLs**: `http://localhost:5173`
- **Allowed Logout URLs**: `http://localhost:5173`
- **Allowed Web Origins**: `http://localhost:5173`

If you deploy behind CloudFront, also add the CloudFront URL origin/callback/logout.

## How to run

The app has two parts: the **Spring Boot API** (port 8080) and the **React + Vite frontend** (port 5173). Run both for the full experience; the frontend proxies `/api` to the backend.

1. **Clone and build backend**
   ```bash
   git clone https://github.com/bbruneel/java-25-spring-boot-4.git thankyouboard
   cd thankyouboard
   mvn -q compile
   ```

2. **Configure database and secrets (backend)**  
   The backend reads `application.properties` and optionally `application-secret.properties` (not in git). Either:
   - **Option A:** Create `src/main/resources/application-secret.properties` with at least:
     - `spring.datasource.password=<your-postgres-password>`
     - Optionally: `GIPHY_API_KEY=...`, `grafana.otlp.headers=Basic <base64>` (see [Grafana](#grafana))
   - **Option B:** Set environment variables:
     - `SPRING_DATASOURCE_PASSWORD`
    - `BOARDS_MAX_BOARDS_PER_OWNER` (optional, default `100`)
    - `BOARDS_MAX_POSTS_PER_BOARD` (optional, default `100`)
    - `BOARDS_IMAGES_ALLOWED_HOSTS` (optional, default `giphy.com,*.giphy.com,giphyusercontent.com,*.giphyusercontent.com`)
    - `BOARDS_PDF_IMAGE_FETCH_TIMEOUT` (optional, default `2s`)
    - `BOARDS_PDF_MAX_IMAGE_BYTES_PER_IMAGE` (optional, default `1048576`)
    - `BOARDS_PDF_MAX_IMAGE_BYTES_TOTAL` (optional, default `10485760`)
     - `GIPHY_API_KEY` (optional)
     - `GRAFANA_OTLP_HEADERS` or `grafana.otlp.headers` (optional, for Grafana)

   Default datasource URL in `application.properties` points to a Supabase Postgres instance; change `spring.datasource.url` and `spring.datasource.username` if you use another host.

   **Auth0 (backend JWT validation):** set these environment variables:
   - `AUTH0_DOMAIN=your-tenant.us.auth0.com` (or your tenant domain)
   - `AUTH0_AUDIENCE=urn:your-api` (must match the API Identifier in Auth0)

3. **Start the backend (API only)**
   ```bash
   mvn spring-boot:run
   ```
   Backend runs on **port 8080** and serves only the REST API (no HTML).

4. **Start the frontend** (in a second terminal)
   ```bash
   cd frontend
   npm install
   export VITE_AUTH0_DOMAIN=your-tenant.us.auth0.com
   export VITE_AUTH0_CLIENT_ID=<your-auth0-spa-client-id>
   export VITE_AUTH0_AUDIENCE=urn:your-api
   npm run dev
   ```
   Vite serves the app at **http://localhost:5173**. Requests to `/api/*` are proxied to `http://localhost:8080`. Open [http://localhost:5173](http://localhost:5173) in your browser to use the UI.

5. **Call the API directly** (optional; version header required)
   ```bash
   # Create/list boards requires Auth0 access token
   export ACCESS_TOKEN="<paste-auth0-access-token>"

   # Create a board (secured)
   curl -s -X POST http://localhost:8080/api/boards \
     -H "Accept: application/json; version=1" -H "Content-Type: application/json" \
     -H "Authorization: Bearer $ACCESS_TOKEN" \
     -d '{"title":"My Board","recipientName":"Alice"}'

   # List boards (secured, returns only boards created by the logged-in user)
   curl -s http://localhost:8080/api/boards \
     -H "Accept: application/json; version=1" \
     -H "Authorization: Bearer $ACCESS_TOKEN"

   # Get a board by id (anonymous; response includes canEdit=false for anonymous callers)
   curl -s http://localhost:8080/api/boards/<board-uuid> -H "Accept: application/json; version=1"

   # Update a board you own (secured)
   curl -s -X PUT http://localhost:8080/api/boards/<board-uuid> \
     -H "Accept: application/json; version=1" -H "Content-Type: application/json" \
     -H "Authorization: Bearer $ACCESS_TOKEN" \
     -d '{"title":"Updated title","recipientName":"Updated recipient"}'

   # Delete a board you own (secured)
   curl -i -X DELETE http://localhost:8080/api/boards/<board-uuid> \
     -H "Accept: application/json; version=1" \
     -H "Authorization: Bearer $ACCESS_TOKEN"

  # Download a board PDF you own (synchronous, legacy)
  curl -L http://localhost:8080/api/boards/<board-uuid>/pdf \
    -H "Accept: application/json; version=1" \
    -H "Authorization: Bearer $ACCESS_TOKEN" \
    -o board-export.pdf

  # Start async PDF generation (recommended)
  curl -s -X POST http://localhost:8080/api/boards/<board-uuid>/pdf-jobs \
    -H "Accept: application/json; version=1" \
    -H "Authorization: Bearer $ACCESS_TOKEN"
  # Response: 202 { jobId, boardId, status: "PENDING", statusUrl }

  # Poll job status
  curl -s http://localhost:8080/api/boards/<board-uuid>/pdf-jobs/<job-uuid> \
    -H "Accept: application/json; version=1" \
    -H "Authorization: Bearer $ACCESS_TOKEN"
  # Response: { status: "SUCCEEDED", downloadUrl: "/api/boards/.../pdf-jobs/.../download" }

  # Download the PDF when ready
  curl -L http://localhost:8080/api/boards/<board-uuid>/pdf-jobs/<job-uuid>/download \
    -H "Accept: application/json; version=1" \
    -H "Authorization: Bearer $ACCESS_TOKEN" \
    -o board-export.pdf

   # Create a post (use a board id from above)
   curl -s -X POST http://localhost:8080/api/boards/<board-uuid>/posts \
     -H "Accept: application/json; version=1" -H "Content-Type: application/json" \
     -d '{"authorName":"Bob","messageText":"Hello!"}'

  # Update/delete your anonymous post (send capability token returned in response)
  export POST_CAPABILITY_TOKEN="<editDeleteToken from create response>"
  curl -s -X PUT http://localhost:8080/api/boards/<board-uuid>/posts/<post-uuid> \
    -H "Accept: application/json; version=1" -H "Content-Type: application/json" \
    -H "X-Post-Capability-Token: $POST_CAPABILITY_TOKEN" \
    -d '{"messageText":"Updated!"}'
  curl -i -X DELETE http://localhost:8080/api/boards/<board-uuid>/posts/<post-uuid> \
    -H "Accept: application/json; version=1" \
    -H "X-Post-Capability-Token: $POST_CAPABILITY_TOKEN"

   # Giphy search (if GIPHY_API_KEY is set)
   curl -s "http://localhost:8080/api/giphy/search?q=hello" -H "Accept: application/json; version=1"
   ```

---

## How to test

- **Backend (Java)**  
  ```bash
  mvn test
  ```
  Tests use **H2** in-memory and the Spring test slices (e.g. `@WebMvcTest`, `@RestClientTest`). No Supabase or Grafana needed for tests.

  Run a specific test class:
  ```bash
  mvn test -Dtest=BoardControllerTest
  ```

- **Frontend**  
  From `frontend/`: `npm run build` to type-check and build; `npm run lint` to run ESLint.

- **Frontend E2E (Playwright)**  
  1. Start the backend with the E2E profile (uses in-memory H2, no external Postgres needed):
     ```bash
     mvn spring-boot:run -Pe2e -Dspring-boot.run.profiles=e2e
     ```
  2. In another terminal, from `frontend/`:
     ```bash
     npm install
     npx playwright install --with-deps
     # Auth0 config for the SPA (used by the E2E browser)
     export VITE_AUTH0_DOMAIN=your-tenant.us.auth0.com
     export VITE_AUTH0_CLIENT_ID=<your-auth0-spa-client-id>
     export VITE_AUTH0_AUDIENCE=urn:your-api
     # Test user credentials (used to log in through Universal Login)
     export AUTH0_E2E_EMAIL=<your-test-user-email>
     export AUTH0_E2E_PASSWORD=<your-test-user-password>
     npm run test:e2e
     ```
  GitHub Actions also runs these tests in the `e2e` job defined in `.github/workflows/build.yml`.

---

## Image uploads

Posts can include either a Giphy GIF or a user-uploaded image, but never both at the same time. This is enforced in both the backend and frontend:

- **Mutual exclusivity**: on create, the backend validates that at most one of `giphyUrl` and `uploadedImageUrl` is set; the frontend UI also prevents picking both a GIF and an uploaded image.

### Constraints

- **Max size**: 5 MB (hard limit, enforced on upload).
- **Formats**: PNG and JPEG only (`image/png`, `image/jpeg`).
- **Validation**:
  - Spring Boot and Lambda both validate content type and size before accepting uploads/presign.
  - The frontend performs the same checks client-side before calling the backend.

### Spring Boot (App Runner / local)

- Posts table has an additional nullable column: `uploaded_image_url`.
- The backend exposes a presign endpoint:
  - `POST /api/boards/{boardId}/images/presign`
  - Request body:
    - `{ "contentType": "image/png" | "image/jpeg", "contentLengthBytes": number }`
  - Response body:
    - `{ "uploadUrl": string, "imageUrl": string, "expiresInSeconds": number, "contentType": string }`
- **Upload flow**:
  1. Frontend calls `/images/presign` to get a short-lived `uploadUrl` and the final `imageUrl`.
  2. Browser `PUT`s the raw file to `uploadUrl`.
  3. Frontend includes `uploadedImageUrl = imageUrl` when posting the message.

#### Local storage (dev / e2e)

When `images.storage=local` (the default), image files are stored on disk and served by Spring MVC:

- Properties (with defaults):
  - `images.local.root-dir=./.local-uploads` – where files are stored.
  - `images.local.public-base-url=http://localhost:8080/uploads` – used to construct `imageUrl` returned to the frontend.
  - `images.presign.expires-seconds=600` – presigned PUT URL lifetime (seconds).
- Static resources are served from `/uploads/**`.
- **Uploaded image host allow-list**:
  - The backend validates `uploadedImageUrl` against `boards.uploaded-images.allowed-hosts`.
  - Defaults (for local dev): `boards.uploaded-images.allowed-hosts=localhost,127.0.0.1`.
  - For production with local storage, set this to the hostnames that serve `/uploads` (e.g. your App Runner or reverse proxy host).

### AWS serverless (Lambda + DynamoDB)

- The Lambda-side `Post` model also has `uploadedImageUrl`, persisted in DynamoDB.
- Images are stored in a dedicated **S3 bucket** per environment:
  - Lifecycle rule: objects expire after **1 year**.
  - Storage class: **S3 Intelligent-Tiering** (automatic tiering).
- Reads go through the existing **CloudFront CDN**:
  - The images bucket is added as an origin with a `/images/*` behavior.
  - The presign endpoint returns a CDN URL like `https://<cloudfront-domain>/images/<boardId>/<uuid>.png`.
- Lambda environment variables:
  - `IMAGE_BUCKET` – name of the S3 bucket used for uploads.
  - `IMAGES_CDN_BASE_URL` – base URL of the CloudFront distribution used to construct `imageUrl`.
  - `MAX_BOARDS_PER_OWNER` – max boards a single authenticated owner can create (default `100`).
  - `MAX_POSTS_PER_BOARD` – max posts allowed per board (default `100`).
  - `BOARDS_IMAGES_ALLOWED_HOSTS` – allow-list for `giphyUrl` (Giphy domains).
  - `BOARDS_UPLOADED_IMAGES_ALLOWED_HOSTS` – allow-list for `uploadedImageUrl` (CloudFront domain).
- Serverless presign endpoint:
  - `POST /api/boards/{boardId}/images/presign`
  - Same request/response shape as the Spring Boot implementation.
  - Uses S3 presigned PUT URLs with short expirations (5–10 minutes) and enforces max size/content type via the presign policy.

### Frontend behavior

- `AddPostModal`:
  - **GIF tab** – existing Giphy picker.
  - **Upload image** – file picker for PNG/JPEG with client-side size/type checks and preview.
  - On submit:
    - Validates: name + (message or media).
    - Ensures only one of GIF or uploaded image is selected.
    - Sends at most one of `giphyUrl` and `uploadedImageUrl`.
- `PostCard`:
  - Renders an uploaded image if `uploadedImageUrl` is present; otherwise falls back to `giphyUrl`.
  - Sanitizes rich-text HTML using `DOMPurify` before rendering.

---

## Dependent systems

### Supabase (PostgreSQL)

- **Role:** Primary database (boards, posts).
- **Config:** `spring.datasource.url`, `spring.datasource.username`, and the password in `application-secret.properties` or `SPRING_DATASOURCE_PASSWORD`.
- **Migrations:** Flyway runs on startup from `src/main/resources/db/migration/` (e.g. `V1__init.sql`, `V2__add_post_board_indexes.sql`). Schema is validated by JPA (`ddl-auto=validate`).
- **If tables are missing:** You can run the one-time script `scripts/fix-missing-tables.sql` in the [Supabase SQL Editor](https://supabase.com/dashboard) (Dashboard → SQL Editor), then restart the app. See the script comments for details.

**Reference:** [Supabase Docs](https://supabase.com/docs)

---

### Grafana (Grafana Cloud OTLP)

- **Role:** Receives traces, metrics, and logs via the OpenTelemetry OTLP endpoint.
- **Config:** In `application.properties`, `grafana.url` points to the Grafana Cloud OTLP gateway. Auth is set via `grafana.otlp.headers` (e.g. `Basic <base64>`) in `application-secret.properties` or the `grafana.otlp.headers` / `GRAFANA_OTLP_HEADERS` env var.
- **What is sent:** Spring Boot OTLP tracing and metrics; Logback appender sends log events to the same OTLP logs endpoint.

**Reference:** [Grafana Cloud OTLP](https://grafana.com/docs/grafana-cloud/otlp/)

---

### Giphy API

- **Role:** Powers `/api/giphy/search` and `/api/giphy/trending` (server-side proxy).
- **Config:** Set `giphy.api-key` or `GIPHY_API_KEY`. If unset, those endpoints return 503 with a JSON error message.
- **Reference:** [Giphy API](https://developers.giphy.com/docs/api)

---

## Project layout (main)

- **`frontend/`** – React + Vite app: `src/pages/` (Dashboard, BoardView), `src/components/` (AddPostModal), `vite.config.ts` (proxy `/api` → backend). Start with `npm run dev` (port 5173).
- `src/main/java/.../` – Backend: `Board`, `Post`, `BoardController`, `PostController`, `GiphyController`, `GiphyService`, `SecurityConfig`, `CorsConfig`, `AppConfig` (API versioning), `TraceIdFilter`, `InstallOpenTelemetryAppender`, Flyway repair config, exception handler.
- `src/main/resources/` – `application.properties`, optional `application-secret.properties`, `logback-spring.xml`, `db/migration/` (Flyway).
- `functions/` – Lambda handlers for the serverless stack (DynamoDB-backed). Includes `BoardsHandler`, `PostsHandler`, `GiphyHandler`, and `PdfWorkerHandler` (SQS-triggered PDF worker).
- `infra/` – CDK Java project for deploying to AWS. See `DEPLOYMENT.md`.
- `scripts/` – `fix-missing-tables.sql` for one-time Supabase schema fix when needed.

### Async PDF export architecture

The frontend uses an **async job pattern** for PDF generation:

1. **Create job**: `POST /api/boards/{id}/pdf-jobs` returns `202 Accepted` with a job ID and status URL.
2. **Poll status**: `GET /api/boards/{id}/pdf-jobs/{jobId}` returns `PENDING`, `RUNNING`, `SUCCEEDED`, or `FAILED`.
3. **Download**: When `SUCCEEDED`, the response includes a `downloadUrl`. On Spring Boot this is a relative path (`/api/boards/.../download`); on Lambda it is a pre-signed S3 URL.

**Spring Boot implementation**: An `@Async` thread pool generates the PDF to local disk (`boards.pdf.storage-dir`). Job metadata is stored in a `pdf_job` Postgres table (Flyway migration `V4`).

**Lambda implementation**: The HTTP Lambda enqueues an SQS message. A dedicated **PdfWorkerLambda** (1024 MB, 5 min timeout) generates the PDF and writes it to S3. Job metadata is stored in a DynamoDB `PdfJobs` table with TTL-based cleanup. The status endpoint returns a short-lived pre-signed S3 URL for download.

**Frontend**: The polling interval is configurable via `VITE_PDF_JOB_POLL_INTERVAL_MS` (default 2000ms). A spinner is shown while the job is in progress.

**Cleanup**: On the Lambda side, DynamoDB TTL (`expiresAt`, 24h) automatically removes old job records. On the Spring Boot side, old `pdf_job` rows and generated PDF files on disk are **not** automatically cleaned up. For production use, consider adding a scheduled purge task or external cron job to remove completed/failed jobs and their files after a retention period.

---

## License

See repository license information.
