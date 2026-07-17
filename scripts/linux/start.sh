#!/usr/bin/env bash
#
# SurveyMind Server launcher (Linux)
# Uses the bundled JRE in jre/ — no system Java required.
#
set -euo pipefail

APP_HOME="$(cd "$(dirname "$0")" && pwd)"
cd "$APP_HOME"

export JAVA_HOME="$APP_HOME/jre"
export PATH="$JAVA_HOME/bin:$PATH"

echo "============================================"
echo "  SurveyMind - AI Agent Platform"
echo "  Starting server..."
echo "============================================"
echo ""

if [ ! -x "$JAVA_HOME/bin/java" ]; then
    echo "[ERROR] JRE not found at $JAVA_HOME"
    echo "Please ensure the jre/ folder is present."
    exit 1
fi

if [ ! -f "$APP_HOME/license.lic" ]; then
    echo "[WARNING] license.lic not found!"
    echo "The application will start but API access will be blocked."
    echo "Please place license.lic in: $APP_HOME"
    echo ""
fi

echo "Access URL: http://localhost:18088"
echo "Default login: admin / admin123"
echo "Press Ctrl+C to stop the server."
echo ""

exec java -Xms512m -Xmx2g -jar "$APP_HOME/surveymind-server.jar"
