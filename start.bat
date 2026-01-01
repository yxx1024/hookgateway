@echo off
setlocal enabledelayedexpansion

:: ======================================================================
:: HookGateway Windows 启动脚本
:: ======================================================================

:: 1. 数据库配置 (MySQL RDS)
set "DB_HOST=rm-bp1048net06j9495z5o.mysql.rds.aliyuncs.com"
set "DB_PORT=3306"
set "DB_NAME=webhook"
set "DB_USERNAME=yxx"
set "DB_PASSWORD=Yxx@0503"

:: 使用 set "VAR=VALUE" 语法可以避免 & 符号被识别为命令分隔符，且不会在变量值中包含引号
set "DB_URL=jdbc:mysql://%DB_HOST%:%DB_PORT%/%DB_NAME%?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true"
set "DB_DRIVER=com.mysql.cj.jdbc.Driver"

:: 2. Redis 配置
set "REDIS_HOST=127.0.0.1"
set "REDIS_PORT=6379"

:: 3. 运行模式配置
set "DISTRIBUTION_MODE=redis"
set "INGEST_MODE=redis"

:: 4. 强制指定方言 (防止在无法连接元数据时报错)
set "SPRING_JPA_PROPERTIES_HIBERNATE_DIALECT=org.hibernate.dialect.MySQL8Dialect"

echo ======================================================================
echo   HookGateway 正在启动...
echo   目标数据库: %DB_HOST%
echo   Redis 地址: %REDIS_HOST%
echo   运行模式: 分布式 (Redis) + 异步摄入 (Write-Behind)
echo ======================================================================

:: 强制清理并打包 (用户要求)
echo [INFO] 正在执行 mvn clean package...
call mvn clean package -DskipTests

if %ERRORLEVEL% NEQ 0 (
    echo [ERROR] 编译打包失败，请检查报错信息。
    pause
    exit /b %ERRORLEVEL%
)

:: 启动应用
java -jar target\hookgateway-0.0.1-SNAPSHOT.jar

pause
