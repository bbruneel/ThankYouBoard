#!/usr/bin/env bash
# ---------------------------------------------------------------------------
#  deploy.sh – build & deploy the Thank You Board application to AWS
#
#  Usage:
#    ./scripts/deploy.sh [test|qa|prd]              # App Runner + RDS (default)
#    ./scripts/deploy.sh [test|qa|prd] serverless    # Lambda + DynamoDB (no idle cost)
#
#  Prerequisites:
#    - AWS CLI configured       (aws configure)
#    - AWS CDK CLI installed     (npm install -g aws-cdk)
#    - Docker running            (for App Runner; not needed for serverless)
#    - Node.js + npm             (for building the frontend)
#    - Java 25 and Maven 3.9+   (for CDK synth)
# ---------------------------------------------------------------------------
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
INFRA_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
REPO_ROOT="$(cd "$INFRA_DIR/.." && pwd)"

ENV="${1:-test}"
ARCH="${2:-}"
SERVERLESS=false
if [[ "$ARCH" == "serverless" ]]; then
  SERVERLESS=true
fi

# Our CDK Java app requires required context keys (`auth0Domain`, `auth0Audience`)
# during *bootstrap* as well, because `cdk bootstrap` also synthesizes the app.
COMMON_CDK_CONTEXT_ARGS=(-c "env=$ENV")
if [[ "$SERVERLESS" = true ]]; then
  COMMON_CDK_CONTEXT_ARGS+=(-c "architecture=serverless")
fi
if [[ -n "${AUTH0_DOMAIN:-}" ]]; then
  COMMON_CDK_CONTEXT_ARGS+=(-c "auth0Domain=$AUTH0_DOMAIN")
fi
if [[ -n "${AUTH0_AUDIENCE:-}" ]]; then
  COMMON_CDK_CONTEXT_ARGS+=(-c "auth0Audience=$AUTH0_AUDIENCE")
fi
if [[ -n "${GIPHY_API_KEY:-}" ]]; then
  COMMON_CDK_CONTEXT_ARGS+=(-c "giphyApiKey=$GIPHY_API_KEY")
fi

echo "========================================"
echo "  Deploying environment: ${ENV}"
echo "  Architecture: $([ "$SERVERLESS" = true ] && echo 'serverless (Lambda + DynamoDB)' || echo 'App Runner + RDS')"
echo "========================================"

# 1. Build the React frontend
echo ""
echo "--- Building frontend ---"
cd "$REPO_ROOT/frontend"
npm ci --silent
npx vite build
echo "Frontend built → frontend/dist/"

# 2. For serverless: build Lambda JAR
if [[ "$SERVERLESS" = true ]]; then
  echo ""
  echo "--- Building Lambda functions ---"
  cd "$REPO_ROOT/functions"
  mvn -q package
  echo "Lambda built → functions/target/lambda-handler.jar"
fi

# 3. CDK bootstrap (idempotent – safe to run every time)
echo ""
echo "--- Bootstrapping CDK (if needed) ---"
cd "$INFRA_DIR"
if ! cdk bootstrap "${COMMON_CDK_CONTEXT_ARGS[@]}" 2>&1; then
  echo "  (bootstrap returned non-zero – may already be bootstrapped, continuing)"
fi

# 4. Deploy the stack
echo ""
echo "--- Deploying CDK stack ---"
CDK_DEPLOY_ARGS=("${COMMON_CDK_CONTEXT_ARGS[@]}" --require-approval never)
cdk deploy "${CDK_DEPLOY_ARGS[@]}"

echo ""
echo "--- Cleaning unused CDK assets (cdk.out) ---"
cd "$INFRA_DIR"
# Uses cdk-agc (CDK Asset Garbage Collector) via npx to prune unused assets from cdk.out
npx cdk-agc

echo ""
echo "========================================"
echo "  Deployment complete!"
echo "========================================"
