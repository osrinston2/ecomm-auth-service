# deploy-podman.ps1 - Native Podman deployment without requiring docker-compose binary
Write-Host "=== Deploying E-Commerce Auth Service to Podman ===" -ForegroundColor Cyan

# 1. Stop existing pod if present
podman pod stop ecomm-auth-pod 2>$null
podman pod rm -f ecomm-auth-pod 2>$null

# 2. Create Pod with exposed ports
Write-Host "[1/4] Creating Podman Pod: ecomm-auth-pod..." -ForegroundColor Yellow
podman pod create --name ecomm-auth-pod -p 8081:8081 -p 3306:3306 -p 6379:6379

# 3. Start MySQL container inside the pod
Write-Host "[2/4] Starting MySQL 8.0 container..." -ForegroundColor Yellow
podman run -d --pod ecomm-auth-pod --name ecomm-mysql `
    -e MYSQL_ROOT_PASSWORD=rootpassword `
    -e MYSQL_DATABASE=ecomm_auth `
    -e MYSQL_USER=auth_user `
    -e MYSQL_PASSWORD=auth_password `
    docker.io/library/mysql:8.0

# 4. Start Redis container inside the pod
Write-Host "[3/4] Starting Redis 7.2 container..." -ForegroundColor Yellow
podman run -d --pod ecomm-auth-pod --name ecomm-redis `
    docker.io/library/redis:7.2-alpine

# 5. Build and Start Auth Service
Write-Host "[4/4] Building and starting Auth Service..." -ForegroundColor Yellow
if (Test-Path "target\auth-service-1.0.0-SNAPSHOT.jar") {
    podman build -f Dockerfile.fast -t ecomm-auth-service:latest .
} else {
    podman build -f Dockerfile -t ecomm-auth-service:latest .
}

podman run -d --pod ecomm-auth-pod --name ecomm-auth-service `
    -e DB_HOST=localhost `
    -e DB_PORT=3306 `
    -e DB_NAME=ecomm_auth `
    -e DB_USER=auth_user `
    -e DB_PASSWORD=auth_password `
    -e REDIS_HOST=localhost `
    -e REDIS_PORT=6379 `
    ecomm-auth-service:latest

Write-Host "`n=== Deployment Completed Successfully! ===" -ForegroundColor Green
Write-Host "All containers are running in Podman Desktop under the 'ecomm-auth-pod' pod."
Write-Host "Endpoint: http://localhost:8081/actuator/health"

