#!/usr/bin/env bash
#
# Stop the SurveyMind Server by killing whatever listens on port 18088.
# Tries lsof, then ss, then fuser — whichever is available.
#
set -euo pipefail

PORT=18088

echo "Stopping SurveyMind Server (port $PORT)..."

PIDS=""
if command -v lsof >/dev/null 2>&1; then
    PIDS="$(lsof -ti ":$PORT" 2>/dev/null || true)"
elif command -v ss >/dev/null 2>&1; then
    PIDS="$(ss -lptnH "sport = :$PORT" 2>/dev/null | grep -oP 'pid=\K[0-9]+' | sort -u || true)"
elif command -v fuser >/dev/null 2>&1; then
    PIDS="$(fuser "$PORT/tcp" 2>/dev/null || true)"
else
    echo "[ERROR] None of lsof/ss/fuser found. Please stop the process manually."
    exit 1
fi

if [ -z "$PIDS" ]; then
    echo "No process is listening on port $PORT."
    exit 0
fi

for pid in $PIDS; do
    echo "Killing process $pid"
    kill "$pid" 2>/dev/null || true
done

echo "SurveyMind Server stopped."
