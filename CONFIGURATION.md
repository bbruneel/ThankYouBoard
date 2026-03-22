# Configuration Options

This document details the configuration properties, environment variables, and limits that can be customized for both the frontend and backend of the application.

## ⚙️ Backend (Spring Boot & AWS Lambda)

The backend can be configured via `application.properties`, an optional `application-secret.properties` (for local development), or standard System Environment Variables.

### Application Limits
These variables control the capacity and bounds of the application:
- `BOARDS_MAX_BOARDS_PER_OWNER` (Default: `100`): Maximum number of boards an authenticated user can own.
- `BOARDS_MAX_POSTS_PER_BOARD` (Default: `100`): Maximum number of posts allowed per board.

### Security & Authentication
- `AUTH0_DOMAIN`: Your Auth0 tenant domain (e.g., `your-tenant.us.auth0.com`). Used for JWT validation.
- `AUTH0_AUDIENCE`: The API Identifier setup in Auth0 for securing endpoints (e.g., `urn:your-api`).

### Database
- `SPRING_DATASOURCE_PASSWORD` (or `spring.datasource.password`): The password for the PostgreSQL database (e.g., Supabase instance).

### Image & Media Configuration
- `GIPHY_API_KEY`: API Key for the Giphy integration. Required for `/api/giphy/search` and `/api/giphy/trending` endpoints.
- `BOARDS_IMAGES_ALLOWED_HOSTS` (Default: `giphy.com,*.giphy.com,giphyusercontent.com,*.giphyusercontent.com`): Allow-list for valid Giphy URLs.

**Local Image Upload Storage (Spring Boot Default)**
When `images.storage=local`, the backend stores files to the local file system.
- `images.local.root-dir` (Default: `./.local-uploads`): Directory where uploaded posts' images are stored.
- `images.local.public-base-url` (Default: `http://localhost:8080/uploads`): Base URL returned to the frontend.
- `images.presign.expires-seconds` (Default: `600`): How long the presigned PUT URL remains valid in seconds.
- `boards.uploaded-images.allowed-hosts` (Default: `localhost,127.0.0.1`): Validates local upload domains.

**AWS Serverless Environment Variables (Lambda)**
When deploying as serverless, additional variables are used:
- `IMAGE_BUCKET`: S3 Bucket name used for storing uploaded images.
- `IMAGES_CDN_BASE_URL`: CloudFront CDN base URL used to construct the served image URLs.
- `BOARDS_UPLOADED_IMAGES_ALLOWED_HOSTS`: Allow-list for the CloudFront domain.

### PDF Export Constraints
Limits enforced during the asynchronous PDF generation process:
- `BOARDS_PDF_IMAGE_FETCH_TIMEOUT` (Default: `2s`): Timeout duration when fetching external images to embed in the document.
- `BOARDS_PDF_MAX_IMAGE_BYTES_PER_IMAGE` (Default: `1048576` / 1MB): Max byte size for a single image in a PDF.
- `BOARDS_PDF_MAX_IMAGE_BYTES_TOTAL` (Default: `10485760` / 10MB): Max total byte size for all embedded images in a PDF.

### Observability
- `GRAFANA_OTLP_HEADERS` (or `grafana.otlp.headers`): Telemetry headers required for pushing trace, metric, and log data to Grafana Cloud OTLP (e.g., `Basic <base64>`).

---

## 🎨 Frontend (Vite)

Frontend environment properties should be placed in a `frontend/.env.local` file. 

### Authentication
- `VITE_AUTH0_DOMAIN`: Your Auth0 tenant domain, used by the SPA to authenticate users.
- `VITE_AUTH0_CLIENT_ID`: Your Auth0 SPA client ID.
- `VITE_AUTH0_AUDIENCE`: The API Identifier expected by the backend API.

### Test / E2E Automation
For running the Playwright E2E test suite locally or on CI:
- `AUTH0_E2E_EMAIL`: The test user's email address for automatic login.
- `AUTH0_E2E_PASSWORD`: The test user's password.
