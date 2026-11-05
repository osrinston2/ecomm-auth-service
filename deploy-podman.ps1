
# deploy-podman.ps1
# Native Podman deployment for ecomm-auth-service

$ErrorActionPreference = "Stop"

$Network = "ecomm-network"
$AuthContainer = "ecomm-auth-service"
$MySqlContainer = "ecomm-mysql"
$RedisContainer = "ecomm-redis"
$ImageName = $env:IMAGE_NAME
$ImageTag = $env:IMAGE_TAG
$Image = "${ImageName}:${ImageTag}"

if ([string]::IsNullOrWhiteSpace($ImageName) -or
    [string]::IsNullOrWhiteSpace($ImageTag)) {
    throw "IMAGE_NAME or IMAGE_TAG environment variable is missing."
}

Write-Host "Deploying image: $Image" -ForegroundColor Cyan

Write-Host "Verifying image: $Image" -ForegroundColor Cyan

podman image exists $Image

if ($LASTEXITCODE -ne 0) {
    throw "Image '$Image' does not exist. Build stage may have failed."
}

Write-Host "=== Deploying E-Commerce Auth Service ===" -ForegroundColor Cyan

# 1. Ensure shared network exists
podman network exists $Network

if ($LASTEXITCODE -ne 0) {
    Write-Host "Creating network: $Network" -ForegroundColor Yellow
    podman network create $Network

    if ($LASTEXITCODE -ne 0) {
        throw "Failed to create Podman network."
    }
}

# 2. Ensure MySQL exists
podman container exists $MySqlContainer

if ($LASTEXITCODE -ne 0) {
    Write-Host "Creating MySQL container..." -ForegroundColor Yellow

    podman run -d `
        --name $MySqlContainer `
        --network $Network `
        --network-alias $MySqlContainer `
        --restart unless-stopped `
        -p 3306:3306 `
        -e MYSQL_ROOT_PASSWORD=rootpassword `
        -e MYSQL_ROOT_HOST=% `
        -e MYSQL_DATABASE=ecomm_auth_db `
        -v ecomm-mysql-data:/var/lib/mysql `
        docker.io/library/mysql:8.0

    if ($LASTEXITCODE -ne 0) {
        throw "Failed to create MySQL container."
    }
}
else {
    Write-Host "MySQL container already exists. Starting it..." -ForegroundColor Yellow
    podman start $MySqlContainer 2>$null | Out-Null
}

# 3. Ensure Redis exists
podman container exists $RedisContainer

if ($LASTEXITCODE -ne 0) {
    Write-Host "Creating Redis container..." -ForegroundColor Yellow

    podman run -d `
        --name $RedisContainer `
        --network $Network `
        --network-alias $RedisContainer `
        --restart unless-stopped `
        -p 6379:6379 `
        -v ecomm-redis-data:/data `
        docker.io/library/redis:7.2-alpine

    if ($LASTEXITCODE -ne 0) {
        throw "Failed to create Redis container."
    }
}
else {
    Write-Host "Redis container already exists. Starting it..." -ForegroundColor Yellow
    podman start $RedisContainer 2>$null | Out-Null
}

# 4. Wait for MySQL to become ready
Write-Host "Waiting for MySQL..." -ForegroundColor Yellow

$MySqlReady = $false

for ($i = 0; $i -lt 60; $i++) {

    # Redirect native command output through cmd.exe
    # to prevent PowerShell treating stderr warnings as terminating errors.
    cmd.exe /c "podman exec -e MYSQL_PWD=rootpassword $MySqlContainer mysqladmin ping -h 127.0.0.1 -uroot --silent >NUL 2>&1"

    if ($LASTEXITCODE -eq 0) {
        $MySqlReady = $true
        break
    }

    Start-Sleep -Seconds 2
}

if (-not $MySqlReady) {
    throw "MySQL did not become ready within 120 seconds."
}

Write-Host "MySQL is ready!" -ForegroundColor Green


# 5. Create databases required by both services
Write-Host "Provisioning application databases..." -ForegroundColor Yellow

podman exec $MySqlContainer mysql `
    -uroot `
    -prootpassword `
    -e "CREATE DATABASE IF NOT EXISTS ecomm_auth_db; CREATE DATABASE IF NOT EXISTS ecomm_user_db;"

if ($LASTEXITCODE -ne 0) {
    throw "Failed to provision application databases."
}

# 6. Replace existing auth service container
podman rm -f $AuthContainer 2>$null | Out-Null

# 7. Deploy auth service
Write-Host "Starting Auth Service..." -ForegroundColor Yellow

podman run -d `
    --name $AuthContainer `
    --network $Network `
    --network-alias $AuthContainer `
    --restart unless-stopped `
    -p 8081:8081 `
    -e SERVER_PORT=8081 `
    -e DB_HOST=$MySqlContainer `
    -e DB_PORT=3306 `
    -e DB_NAME=ecomm_auth_db `
    -e DB_USER=root `
    -e DB_PASSWORD=rootpassword `
    -e REDIS_HOST=$RedisContainer `
    -e REDIS_PORT=6379 `
    $Image

if ($LASTEXITCODE -ne 0) {
    throw "Failed to start Auth Service."
}

Write-Host "`n=== Auth Service Deployment Completed ===" -ForegroundColor Green
Write-Host "Auth Service: http://localhost:8081"
Write-Host "Health:       http://localhost:8081/actuator/health"
Write-Host "Network:      $Network"
