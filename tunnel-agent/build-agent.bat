@echo off
echo [INFO] 正在编译 tunnel-agent...
call mvn clean package -DskipTests

if %ERRORLEVEL% NEQ 0 (
    echo [ERROR] 编译失败，请检查环境变量和 Maven 配置。
    pause
    exit /b %ERRORLEVEL%
)

echo [SUCCESS] 编译完成！jar 包位于 target\tunnel-agent.jar
pause
