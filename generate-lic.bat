@echo off
REM 一键生成并替换许可证（Windows）

cd /d "%~dp0"

REM 确保 JAR 已构建
if not exist "mateclaw-server\target\qingwenclaws-server.jar" (
    echo 🔨 构建项目...
    cd mateclaw-server
    call mvn clean package -DskipTests
    cd ..
)

REM 生成许可证
echo 🔧 生成许可证...
cd mateclaw-server
call mvn exec:java -Dexec.mainClass="vip.mate.license.LicenseGenerator" -Dexec.args="--customer 擎问科技试用客户 --days 30 --output ../license.lic" -q
cd ..

echo.
echo ✅ 已生成 license.lic (30天有效期)
echo.
echo 📋 快速部署:
echo    • 当前目录已有 license.lic
echo    • Docker: 重启容器自动加载
echo    • 桌面版: 复制到 JAR 同目录后重启

pause
