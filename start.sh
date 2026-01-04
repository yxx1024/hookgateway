#!/bin/bash
set -e

# Define colors
GREEN='\033[0;32m'
RED='\033[0;31m'
NC='\033[0m' # No Color

echo -e "${GREEN}=== HookGateway Startup Script ===${NC}"

# ==========================================
# 🔧 Environment Configuration (Edit here)
# ==========================================

# 1. Database Configuration (MySQL)
# Uncomment and fill in to enable persistent MySQL storage
export DB_URL="jdbc:mysql://rm-bp1048net06j9495z5o.mysql.rds.aliyuncs.com:3306/webhook?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true"
export DB_USERNAME="yxx"
export DB_PASSWORD="Yxx@0503"
export DB_DRIVER="com.mysql.cj.jdbc.Driver"

# 2. Redis Configuration (Optional, for High Performance)
export REDIS_HOST="localhost"
export REDIS_PORT=6379
# export REDIS_PASSWORD=""

# 3. Security
# Set a fixed initial admin password (prevents random generation on new install)
# export ADMIN_INIT_PASSWORD="secure_admin_password"

# 4. Advanced Features (Enabled by Redis)
# Distribution Mode: 'redis' (Reliable, supports clustering) vs 'async' (Memory-only, default)
export DISTRIBUTION_MODE="redis"

# Ingest Mode: 'redis' (High throughput Write-Behind) vs 'sync' (Direct DB write, default)
# Uncomment the line below to enable high-performance ingestion (Writes to Redis first, then DB)
export INGEST_MODE="redis"

# 5. Debugging
export JPA_SHOW_SQL="true"

# ==========================================

# Check Java Version
if ! command -v java &> /dev/null; then
    echo -e "${RED}Error: Java is not installed.${NC}"
    exit 1
fi

JAVA_VER=$(java -version 2>&1 | head -n 1 | awk -F '"' '{print $2}' | cut -d'.' -f1)
echo "Detected Java version: $JAVA_VER"

# Build the project
echo -e "${GREEN}>>> Building project...${NC}"
# Try mvn wrapper first, then global mvn
if [ -f "./mvnw" ]; then
    ./mvnw clean package -DskipTests
elif command -v mvn &> /dev/null; then
    mvn clean package -DskipTests
else
    echo -e "${RED}Error: Maven not found.${NC}"
    exit 1
fi

# Find the JAR file
JAR_FILE=$(find target -name "hookgateway-*.jar" | grep -v "original" | head -n 1)

if [ -z "$JAR_FILE" ]; then
    echo -e "${RED}Error: JAR file not found in target directory.${NC}"
    exit 1
fi

# Check if port 8080 is in use and kill the process
echo -e "${GREEN}>>> Checking port 8080...${NC}"
PID=$(lsof -ti:8080 || true)
if [ -n "$PID" ]; then
    echo -e "${RED}Warning: Port 8080 is in use by PID $PID. Killing it...${NC}"
    kill -9 $PID
    sleep 1
else
    echo "Port 8080 is free."
fi

echo -e "${GREEN}>>> Starting $JAR_FILE...${NC}"

# Run the application
exec java -jar "$JAR_FILE"
