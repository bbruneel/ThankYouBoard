# Deployment Guide – AWS CDK (Java)

This guide explains how to deploy the Thank You Board application to AWS using the CDK infrastructure code in `infra/`.

Two architectures are available: **App Runner + RDS** (default) and **serverless** (Lambda + DynamoDB).

---

## Architecture overview

### Option A: App Runner + RDS (default)

| Component | AWS Service | Purpose |
|-----------|-------------|---------|
| **Frontend** | S3 + CloudFront | Static React/Vite SPA served via CDN |
| **Backend** | App Runner | Containerised Spring Boot API |
| **Database** | RDS PostgreSQL 16 | Persistent storage (boards, posts) |
| **Networking** | VPC (isolated subnets, no NAT) | Private connectivity between App Runner and RDS |
| **Secrets** | Secrets Manager | Auto-generated database password |

CloudFront serves **both** the frontend (`/`) and the backend API (`/api/*`), so the browser only talks to a single domain. No CORS configuration is needed.

### Option B: Serverless (Lambda + DynamoDB)

| Component | AWS Service | Purpose |
|-----------|-------------|---------|
| **Frontend** | S3 + CloudFront | Static React/Vite SPA served via CDN |
| **Backend** | Lambda + API Gateway | Pay-per-request API (no charge when idle) |
| **Database** | DynamoDB (on-demand) | NoSQL storage (no charge when idle) |

**No runtime costs when idle** – you pay only for actual requests and storage. Ideal for low-traffic or development workloads.

**Auth:** The serverless API uses an **API Gateway HTTP API JWT authorizer** (Auth0) for `GET /api/boards`, `POST /api/boards`, `PUT /api/boards/{id}`, `DELETE /api/boards/{id}`, and `GET /api/boards/{id}/pdf`. Individual board reads (`GET /api/boards/{id}`), posts, and Giphy endpoints remain anonymous.

Anonymous per-post post edit/delete (`PUT/DELETE /api/boards/{id}/posts/{postId}`) is authorized by Lambda instead: clients must send `X-Post-Capability-Token` (stored client-side in `sessionStorage` by the UI). API Gateway does not enforce a JWT authorizer for these routes.

**DynamoDB access pattern:** The dashboard list endpoint (`GET /api/boards`) queries a GSI (`ownerId-createdAt-index`) by `ownerId` (Auth0 `sub`) and sorts by `createdAt`.

**CORS:** The API Gateway is configured to allow all origins (`allowOrigins("*")`), which is suitable for public/demo APIs. It also allows the `X-Post-Capability-Token` request header needed for anonymous per-post edit/delete. For production, restrict origins to a known frontend domain.

### Estimated monthly cost (serverless, low-traffic)

| Environment | DynamoDB | Lambda | API Gateway | S3 + CF | **Total (idle)** |
|-------------|---------|--------|------------|---------|------------------|
| **TEST** | ~$0–1 (on-demand) | ~$0 | ~$0 | < $1 | **< $2** |

With light usage (e.g. 1000 requests/day): typically under $5/month.

### Estimated monthly cost (low-traffic)

| Environment | RDS | App Runner | S3 + CF | Other | **Total** |
|-------------|-----|------------|---------|-------|-----------|
| **TEST** | ~$13 (db.t4g.micro, single-AZ) | ~$5 (0.25 vCPU) | < $1 | ~$1 | **~$20** |
| **QA** | ~$13 (db.t4g.micro, single-AZ) | ~$10 (0.5 vCPU) | < $1 | ~$1 | **~$25** |
| **PRD** | ~$26 (db.t4g.small, multi-AZ) | ~$25 (1 vCPU) | ~$5 | ~$1 | **~$57** |

> Costs are approximate and may vary by region. RDS free-tier eligible accounts will pay less.

---

## Prerequisites

| Tool | Minimum version | Installation |
|------|-----------------|--------------|
| **AWS CLI** | 2.x | [Install guide](https://docs.aws.amazon.com/cli/latest/userguide/getting-started-install.html) |
| **AWS CDK CLI** | 2.x | `npm install -g aws-cdk` |
| **Java (JDK)** | 21 | [Eclipse Temurin](https://adoptium.net/) or Amazon Corretto |
| **Apache Maven** | 3.9+ | [Download](https://maven.apache.org/download.cgi) |
| **Node.js + npm** | 18+ | [Download](https://nodejs.org/) |
| **Docker** | 20+ | [Install guide](https://docs.docker.com/get-docker/) |

---

## AWS credentials and configuration

### Option A – AWS CLI profile (recommended)

```bash
aws configure
# You will be prompted for:
#   AWS Access Key ID:       <your-access-key>
#   AWS Secret Access Key:   <your-secret-key>
#   Default region name:     eu-west-1        # or your preferred region
#   Default output format:   json
```

### Option B – Environment variables

```bash
export AWS_ACCESS_KEY_ID=<your-access-key>
export AWS_SECRET_ACCESS_KEY=<your-secret-key>
export AWS_DEFAULT_REGION=eu-west-1
```

### Option C – IAM role / SSO

If you are using IAM Identity Center (SSO), configure a named profile:

```bash
aws configure sso
export AWS_PROFILE=my-sso-profile
```

> **Where to put these values:**
> - `AWS_ACCESS_KEY_ID` / `AWS_SECRET_ACCESS_KEY` — set as environment variables or via `aws configure`.
> - `AWS_DEFAULT_REGION` — set as an environment variable, in `~/.aws/config`, or pass `-c region=eu-west-1` to CDK.
> - **Never commit credentials to the repository.**

---

## Choosing an environment

Three environments are pre-configured: **test**, **qa**, and **prd**.

Pass the environment name via the CDK context variable `env`:

```bash
cdk deploy -c env=test      # TEST  (smallest, cheapest)
cdk deploy -c env=qa        # QA    (medium)
cdk deploy -c env=prd       # PRD   (production-grade, multi-AZ)
```

You can also override the AWS account and region:

```bash
cdk deploy -c env=test -c region=us-east-1 -c account=123456789012
```

If not provided, CDK falls back to:
- `CDK_DEFAULT_ACCOUNT` / `CDK_DEFAULT_REGION` environment variables
- The AWS CLI default profile

---

## First-time setup – CDK bootstrap

CDK requires a one-time bootstrap per account + region:

```bash
cd infra
cdk bootstrap aws://<ACCOUNT_ID>/<REGION>
# Example:
# cdk bootstrap aws://123456789012/eu-west-1
```

---

## Deploying

### Quick deploy (helper script)

**App Runner + RDS (default):**
```bash
cd infra
./scripts/deploy.sh test    # or qa / prd
```

**Serverless (Lambda + DynamoDB):**
```bash
cd infra
./scripts/deploy.sh test serverless
```

The script:
1. Builds the React frontend (`npx vite build`)
2. For serverless: builds the Lambda JAR (`mvn package` in `functions/`)
3. Bootstraps CDK (idempotent)
4. Runs `cdk deploy` (App Runner: builds Docker image; serverless: creates Lambda, DynamoDB, API Gateway)

### Manual step-by-step

**App Runner + RDS:**
```bash
# 1. Build the frontend
cd frontend && npm ci && npx vite build && cd ..

# 2. Deploy
cd infra
cdk deploy -c env=test --require-approval never
```

**Serverless:**
```bash
# 1. Build the frontend
cd frontend && npm ci && npx vite build && cd ..

# 2. Build Lambda functions
cd functions && mvn package && cd ..

# 3. Deploy
cd infra
cdk deploy -c env=test -c architecture=serverless --require-approval never
```

CDK will:
- **App Runner:** Build Docker image, push to ECR, create VPC, RDS, App Runner, S3, CloudFront
- **Serverless:** Create Lambda, DynamoDB tables, API Gateway, S3, CloudFront (no Docker needed)
- Upload `frontend/dist/` to S3 and invalidate CloudFront cache

### Stack outputs

After a successful deploy, CDK prints:

| Output | Description |
|--------|-------------|
| Output | Description |
|--------|-------------|
| `CloudFrontUrl` | The public URL for the application (frontend + API) |
| `AppRunnerUrl` | (App Runner) Direct URL to the backend |
| `RdsEndpoint` | (App Runner) RDS PostgreSQL hostname |
| `ApiGatewayUrl` | (Serverless) API Gateway base URL |
| `FrontendBucketName` | S3 bucket name for frontend assets |

---

## Updating the application

After code changes:

```bash
cd infra
./scripts/deploy.sh test
```

CDK is smart about changes:
- If only frontend code changed → only S3 files are re-uploaded and CloudFront is invalidated.
- If backend code changed → a new Docker image is built, pushed to ECR, and App Runner redeploys.
- If infrastructure changed → CloudFormation updates the relevant resources.

---

## Tearing down

**App Runner stack:**
```bash
cd infra
cdk destroy -c env=test
```

**Serverless stack:**
```bash
cd infra
cdk destroy thankyouboard-test-serverless -c env=test -c architecture=serverless
```

> **Note:** For `prd`, deletion protection is enabled on RDS (App Runner only). You must manually disable it in the AWS Console before destroying the stack.

---

## Environment variables reference

### CDK context variables (passed via `-c key=value`)

| Variable | Default | Description |
|----------|---------|-------------|
| `env` | `test` | Environment name: `test`, `qa`, or `prd` |
| `architecture` | *(none)* | Set to `serverless` for Lambda + DynamoDB |
| `region` | `CDK_DEFAULT_REGION` | AWS region to deploy to |
| `account` | `CDK_DEFAULT_ACCOUNT` | AWS account ID |

### AWS credentials (environment variables)

| Variable | Description |
|----------|-------------|
| `AWS_ACCESS_KEY_ID` | IAM access key |
| `AWS_SECRET_ACCESS_KEY` | IAM secret key |
| `AWS_SESSION_TOKEN` | (Optional) Temporary session token |
| `AWS_DEFAULT_REGION` | Default AWS region |
| `AWS_PROFILE` | Named AWS CLI profile |

### Application environment variables (set automatically by CDK on App Runner)

| Variable | Set by CDK | Description |
|----------|------------|-------------|
| `SPRING_DATASOURCE_URL` | ✅ | JDBC URL pointing to RDS |
| `SPRING_DATASOURCE_USERNAME` | ✅ | Database user (`postgres`) |
| `SPRING_DATASOURCE_PASSWORD` | ✅ (from Secrets Manager) | Database password |
| `SPRING_FLYWAY_ENABLED` | ✅ | Flyway migrations run on startup |
| `AUTH0_DOMAIN` | ✅ | Auth0 tenant domain (JWT issuer) |
| `AUTH0_AUDIENCE` | ✅ | Auth0 API audience (API Identifier) |
| `GIPHY_API_KEY` | ✅ optional | Read from deploy-time `GIPHY_API_KEY` (via `infra/scripts/deploy.sh` → CDK context). Defaults to blank if unset. |
| `GRAFANA_OTLP_HEADERS` | ❌ manual | (Optional) Grafana Cloud auth (App Runner only) |
| `BOARDS_MAX_BOARDS_PER_OWNER` | ❌ manual | (Optional) Spring Boot board cap per authenticated owner (default `100`) |
| `BOARDS_MAX_POSTS_PER_BOARD` | ❌ manual | (Optional) Spring Boot post cap per board (default `100`) |

### Serverless (Lambda) environment variables

| Variable | Set by CDK | Description |
|----------|------------|-------------|
| `BOARDS_TABLE` | ✅ | DynamoDB table name for boards |
| `POSTS_TABLE` | ✅ | DynamoDB table name for posts |
| `MAX_BOARDS_PER_OWNER` | ✅ | Lambda board cap per authenticated owner (default `100`) |
| `MAX_POSTS_PER_BOARD` | ✅ | Lambda post cap per board (default `100`) |
| `GIPHY_API_KEY` | ✅ optional | Read from deploy-time `GIPHY_API_KEY` (via `infra/scripts/deploy.sh` → CDK context). Defaults to blank if unset. |

**Auth0 (serverless):** The JWT authorizer is configured in `infra/src/main/java/org/bruneel/infra/ServerlessStack.java` using the values in `EnvironmentConfig` (`auth0Domain`, `auth0Audience`).

**Lambda configuration:**
- **Memory:** Default 256 MB per function. Can be increased in `ServerlessStack.java` for latency-sensitive paths (higher memory reduces cold start time).
- **Request ID / Trace ID:** API Gateway and Lambda context provide request IDs that can be included in log statements for tracing. Access via `context.getRequestId()` or `event.getRequestContext().getRequestId()`.

---

## Project layout (infrastructure)

```
infra/
├── cdk.json                                    # CDK app configuration
├── pom.xml                                     # Maven project (CDK dependencies)
├── scripts/
│   └── deploy.sh                               # One-command build + deploy
└── src/main/java/org/bruneel/infra/
    ├── InfraApp.java                           # CDK app entry point (chooses stack by architecture)
    ├── EnvironmentConfig.java                  # Per-environment sizing
    ├── AppStack.java                           # App Runner + RDS resources
    └── ServerlessStack.java                    # Lambda + DynamoDB + API Gateway resources

functions/                                      # Lambda handler (serverless only)
├── pom.xml
└── src/main/java/org/bruneel/thankyouboard/
    ├── handler/BoardsHandler.java              # Boards routes (owner-secured list/create/edit/delete/pdf; get-by-id anonymous)
    ├── handler/PostsHandler.java               # Posts routes (anonymous)
    ├── handler/GiphyHandler.java               # Giphy routes (anonymous)
    ├── service/                                # Boards, Posts, Giphy services
    └── repository/                             # DynamoDB access
```
