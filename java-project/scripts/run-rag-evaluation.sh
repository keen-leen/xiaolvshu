#!/usr/bin/env bash
set -euo pipefail

# 真实 RAG 评测统一入口。
# 该脚本显式加载开发环境凭据，并把代码状态、本轮调整说明和记录文件路径
# 传给评测用例。普通 mvn test 不走该入口，因此不会意外调用外部服务或修改文档。

PROJECT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
REPOSITORY_DIR="$(cd "$PROJECT_DIR/.." && pwd)"
ENV_FILE="$PROJECT_DIR/.env.dev"
HISTORY_FILE="$REPOSITORY_DIR/doc/RAG_EVALUATION_HISTORY.md"

if [ "$#" -ne 1 ] || [ -z "${1//[[:space:]]/}" ]; then
  echo "用法: $0 \"本轮修改说明\"" >&2
  exit 2
fi

if [ ! -f "$ENV_FILE" ]; then
  echo "找不到 $ENV_FILE，请先根据 .env.example 配置开发环境。" >&2
  exit 1
fi

CHANGE_DESCRIPTION="$1"
GIT_REVISION="$(git -C "$REPOSITORY_DIR" rev-parse --short HEAD)"
if [ -n "$(git -C "$REPOSITORY_DIR" status --porcelain=v1)" ]; then
  WORKTREE_STATE="dirty"
else
  WORKTREE_STATE="clean"
fi

set -a
# shellcheck disable=SC1090
source "$ENV_FILE"
set +a

cd "$PROJECT_DIR"

# Maven 参数使用数组保留中文说明中的空格，避免将用户文本再次交给 shell 解析。
MAVEN_ARGS=(
  -q
  -DrunRagEvaluation=true
  "-DragEvaluationRecordFile=$HISTORY_FILE"
  "-DragEvaluationGitRevision=$GIT_REVISION"
  "-DragEvaluationWorktreeState=$WORKTREE_STATE"
  "-DragEvaluationChange=$CHANGE_DESCRIPTION"
  -Dtest=RagRetrievalEvaluationTest
  test
)

if [ -x "$PROJECT_DIR/mvnw" ]; then
  exec "$PROJECT_DIR/mvnw" "${MAVEN_ARGS[@]}"
fi

exec mvn "${MAVEN_ARGS[@]}"
