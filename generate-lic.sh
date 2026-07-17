#!/bin/bash
# 一键生成并替换许可证（Linux/macOS）

cd "$(dirname "$0")"

# 确保 JAR 已构建
if [ ! -f "mateclaw-server/target/qingwenclaws-server.jar" ]; then
    echo "🔨 构建项目..."
    cd mateclaw-server && mvn clean package -DskipTests && cd ..
fi

# 设置 Java 21（如果可用）
if [ -d "/usr/lib/jvm/java-21-openjdk-amd64" ]; then
    export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
fi

# 生成许可证
echo "🔧 生成许可证..."
cd mateclaw-server
JAVA_HOME=${JAVA_HOME:-$JAVA_HOME} mvn exec:java \
    -Dexec.mainClass="vip.mate.license.LicenseGenerator" \
    -Dexec.args="--customer 擎问科技试用客户 --days 30 --output ../license.lic" \
    -q
cd ..

echo ""
echo "✅ 已生成 license.lic (30天有效期)"
echo ""
echo "📋 快速部署:"
echo "   • 当前目录已有 license.lic"
echo "   • Docker: 重启容器自动加载"
echo "   • 桌面版: 复制到 JAR 同目录后重启"
