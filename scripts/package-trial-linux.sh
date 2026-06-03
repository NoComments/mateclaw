#!/usr/bin/env bash
#
# SurveyMind Trial Packaging Script (Linux target)
#
# Produces a self-contained Linux distribution (bundled Linux JRE + shell
# launchers) as a .tar.gz. Mirrors scripts/package-trial.sh, which targets
# Windows.
#
# Usage:
#   ./scripts/package-trial-linux.sh [--customer "客户名"] [--days 2] [--arch x64|aarch64]
#
#   --arch selects the bundled JRE CPU architecture. The JAR itself is
#   architecture-independent; only the embedded JRE is arch-specific, so the
#   target host's CPU must match:
#     x64      -> Intel/AMD 64-bit (default)
#     aarch64  -> ARM 64-bit (鲲鹏/飞腾/Apple-on-Linux 等信创环境)
#
# Prerequisites:
#   - JDK 21+ on PATH (for building)
#   - Node.js 18+ and pnpm 10+ (for frontend build)
#   - Maven 3.9+ (for backend build)
#   - curl and tar (for JRE download / extraction)
#
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

# Defaults
CUSTOMER="Trial Customer"
DAYS=30
ARCH="x64"
JRE_VERSION="21.0.7+6"

# Parse args
while [[ $# -gt 0 ]]; do
    case $1 in
        --customer) CUSTOMER="$2"; shift 2 ;;
        --days) DAYS="$2"; shift 2 ;;
        --arch) ARCH="$2"; shift 2 ;;
        *) echo "Unknown arg: $1"; exit 1 ;;
    esac
done

# Map CPU arch to the Adoptium artifact naming (x64 / aarch64).
case "$ARCH" in
    x64|aarch64) ;;
    *) echo "Unsupported --arch '$ARCH' (expected: x64 | aarch64)"; exit 1 ;;
esac
JRE_ARCHIVE="OpenJDK21U-jre_${ARCH}_linux_hotspot_$(echo ${JRE_VERSION} | tr '+' '_').tar.gz"
JRE_URL="https://github.com/adoptium/temurin21-binaries/releases/download/jdk-${JRE_VERSION}/${JRE_ARCHIVE}"

VERSION=$(grep '<version>' "$PROJECT_ROOT/mateclaw-server/pom.xml" | head -1 | sed 's/.*<version>\(.*\)<\/version>.*/\1/')
DIST_NAME="SurveyMind-Trial-Linux-${ARCH}-v${VERSION}"
DIST_DIR="$PROJECT_ROOT/dist/$DIST_NAME"

echo "=== SurveyMind Trial Packaging (Linux) ==="
echo "Customer : $CUSTOMER"
echo "Trial    : $DAYS days"
echo "Version  : $VERSION"
echo "Output   : dist/$DIST_NAME.tar.gz"
echo "=========================================="

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

# Step 4: Download Linux JRE
echo ""
echo "[4/6] Downloading Linux JRE 21..."
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
tar xzf "$JRE_CACHE/$JRE_ARCHIVE"
# Adoptium extracts to a versioned directory; rename to jre/
mv jdk-*-jre jre 2>/dev/null || true

# Scripts
cp "$PROJECT_ROOT/scripts/linux/start.sh" "$DIST_DIR/"
cp "$PROJECT_ROOT/scripts/linux/stop.sh" "$DIST_DIR/"
chmod +x "$DIST_DIR/start.sh" "$DIST_DIR/stop.sh"

# Installation guide
cp "$PROJECT_ROOT/docs/SurveyMind-安装指南-Linux.md" "$DIST_DIR/" 2>/dev/null || true

# Step 6: Generate trial license
# Spring Boot fat JARs nest classes under BOOT-INF/classes/ so plain -cp
# doesn't work. Temporarily unpack the JAR to access the generator class.
echo ""
echo "[6/6] Generating trial license..."
LICENSE_TMP=$(mktemp -d)
cd "$LICENSE_TMP"
jar xf "$DIST_DIR/surveymind-server.jar" BOOT-INF/classes/ BOOT-INF/lib/
java -cp "BOOT-INF/classes:BOOT-INF/lib/*" vip.mate.license.LicenseGenerator \
    --customer "$CUSTOMER" --days "$DAYS" --output "$DIST_DIR/license.lic"
rm -rf "$LICENSE_TMP"

# Create tarball
echo ""
echo "Creating tar.gz archive..."
cd "$PROJECT_ROOT/dist"
tar czf "${DIST_NAME}.tar.gz" --exclude='*.DS_Store' "$DIST_NAME/"

echo ""
echo "=== Done! ==="
echo "Package: dist/${DIST_NAME}.tar.gz"
echo "Size: $(du -h "${DIST_NAME}.tar.gz" | cut -f1)"
