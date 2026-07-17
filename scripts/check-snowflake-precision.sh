#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SRC_DIR="$ROOT_DIR/mateclaw-ui/src"

if [ ! -d "$SRC_DIR" ]; then
  exit 0
fi

# Guard the convention that backend Snowflake IDs stay as strings in the UI.
# Intentional conversions must carry a local snowflake-precision-ok comment.
violations="$(
  grep -RInE 'Number[[:space:]]*\(|parseInt[[:space:]]*\(' "$SRC_DIR" \
    --include='*.ts' --include='*.vue' 2>/dev/null \
  | grep -vE '^[^:]+:[0-9]+:[[:space:]]*//' \
  | grep -E '([A-Za-z0-9_]*(Id|ID|Ids|IDs)|conversationId|workspaceId|agentId|modelId|workflowId|kbId)' \
  | grep -v 'snowflake-precision-ok' \
  || true
)"

if [ -n "$violations" ]; then
  echo "Snowflake precision check failed."
  echo "Do not coerce Snowflake IDs with Number()/parseInt(); keep them as strings."
  echo "If the conversion is not for a Snowflake ID, add a local snowflake-precision-ok comment."
  echo
  echo "$violations"
  exit 1
fi
