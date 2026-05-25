#!/usr/bin/env bash
#
# SurveyMind Trial Packaging Script
#
# Usage:
#   ./scripts/package-trial.sh [--customer "客户名"] [--days 30]
#
# Prerequisites:
#   - JDK 21+ on PATH (for building)
#   - Node.js 18+ and pnpm 10+ (for frontend build)
#   - Maven 3.9+ (for backend build)
#   - curl and unzip (for JRE download)
#
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

# Defaults
CUSTOMER="Trial Customer"
DAYS=30
JRE_VERSION="21.0.7+6"
JRE_ARCHIVE="OpenJDK21U-jre_x64_windows_hotspot_$(echo ${JRE_VERSION} | tr '+' '_').zip"
JRE_URL="https://github.com/adoptium/temurin21-binaries/releases/download/jdk-${JRE_VERSION}/OpenJDK21U-jre_x64_windows_hotspot_$(echo ${JRE_VERSION} | tr '+' '_').zip"

# Parse args
while [[ $# -gt 0 ]]; do
    case $1 in
        --customer) CUSTOMER="$2"; shift 2 ;;
        --days) DAYS="$2"; shift 2 ;;
        *) echo "Unknown arg: $1"; exit 1 ;;
    esac
done

VERSION=$(grep '<version>' "$PROJECT_ROOT/mateclaw-server/pom.xml" | head -1 | sed 's/.*<version>\(.*\)<\/version>.*/\1/')
DIST_NAME="SurveyMind-Trial-v${VERSION}"
DIST_DIR="$PROJECT_ROOT/dist/$DIST_NAME"

echo "=== SurveyMind Trial Packaging ==="
echo "Customer : $CUSTOMER"
echo "Trial    : $DAYS days"
echo "Version  : $VERSION"
echo "Output   : dist/$DIST_NAME.zip"
echo "=================================="

# Step 1: Build frontend
echo ""
echo "[1/6] Building frontend..."
cd "$PROJECT_ROOT/mateclaw-ui"
pnpm install --frozen-lockfile
NODE_OPTIONS=--max-old-space-size=6144 pnpm exec vite build \
    --outDir "$PROJECT_ROOT/mateclaw-server/src/main/resources/static" \
    --emptyOutDir

# Step 2: Build plugin-api
echo ""
echo "[2/6] Building plugin-api..."
cd "$PROJECT_ROOT/mateclaw-plugin-api"
mvn install -Dmaven.test.skip=true -q

# Step 3: Build backend JAR
echo ""
echo "[3/6] Building backend JAR..."
cd "$PROJECT_ROOT/mateclaw-server"
mvn clean package -DskipTests -q

# Step 4: Download Windows JRE
echo ""
echo "[4/6] Downloading Windows JRE 21..."
JRE_CACHE="$PROJECT_ROOT/dist/.jre-cache"
mkdir -p "$JRE_CACHE"
if [ ! -f "$JRE_CACHE/$JRE_ARCHIVE" ]; then
    curl -L -o "$JRE_CACHE/$JRE_ARCHIVE" "$JRE_URL"
fi

# Step 5: Assemble distribution
echo ""
echo "[5/6] Assembling distribution..."
rm -rf "$DIST_DIR"
mkdir -p "$DIST_DIR/data"

# JAR
cp "$PROJECT_ROOT/mateclaw-server/target/"*.jar "$DIST_DIR/surveymind-server.jar"

# JRE
cd "$DIST_DIR"
unzip -qo "$JRE_CACHE/$JRE_ARCHIVE"
# Adoptium extracts to a versioned directory; rename to jre/
mv jdk-*-jre jre 2>/dev/null || true

# Scripts
cp "$PROJECT_ROOT/scripts/windows/start.bat" "$DIST_DIR/"
cp "$PROJECT_ROOT/scripts/windows/stop.bat" "$DIST_DIR/"

# Installation guide
cp "$PROJECT_ROOT/docs/SurveyMind-安装指南.md" "$DIST_DIR/" 2>/dev/null || true

# Step 6: Generate trial license
echo ""
echo "[6/6] Generating trial license..."
cd "$DIST_DIR"
java -cp surveymind-server.jar vip.mate.license.LicenseGenerator \
    --customer "$CUSTOMER" --days "$DAYS"

# Create zip
echo ""
echo "Creating zip archive..."
cd "$PROJECT_ROOT/dist"
zip -r "${DIST_NAME}.zip" "$DIST_NAME/" -x "*.DS_Store"

echo ""
echo "=== Done! ==="
echo "Package: dist/${DIST_NAME}.zip"
echo "Size: $(du -h "${DIST_NAME}.zip" | cut -f1)"
