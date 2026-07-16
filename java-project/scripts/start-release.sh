#!/usr/bin/env bash
set -euo pipefail

PROJECT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
ENV_FILE="$PROJECT_DIR/.env.release"

if [ ! -f "$ENV_FILE" ]; then
  echo "Missing $ENV_FILE"
  echo "Create it from .env.example: cp .env.example .env.release"
  exit 1
fi

set -a
# shellcheck disable=SC1090
source "$ENV_FILE"
set +a

required_vars=(
  DB_PASSWORD
  RABBITMQ_PASSWORD
  JWT_SECRET
  ELASTICSEARCH_PASSWORD
  AI_API_KEY
  COS_SECRET_ID
  COS_SECRET_KEY
  COS_BUCKET_NAME
)

for var_name in "${required_vars[@]}"; do
  if [ -z "${!var_name:-}" ]; then
    echo "Missing required environment variable: $var_name"
    exit 1
  fi
done

cd "$PROJECT_DIR"

if [ -x "$PROJECT_DIR/mvnw" ]; then
  exec "$PROJECT_DIR/mvnw" spring-boot:run
fi

if command -v mvn >/dev/null 2>&1; then
  exec mvn spring-boot:run
fi

echo "Maven not found. Please install Maven or add mvnw wrapper."
echo "Ubuntu/Debian: sudo apt-get update && sudo apt-get install -y maven"
exit 127
