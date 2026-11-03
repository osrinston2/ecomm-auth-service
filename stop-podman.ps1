# stop-podman.ps1 - Cleanly stops and removes the Podman Pod and containers
Write-Host "Stopping and removing ecomm-auth-pod..." -ForegroundColor Yellow
podman pod stop ecomm-auth-pod 2>$null
podman pod rm -f ecomm-auth-pod 2>$null
Write-Host "All containers and the pod have been stopped and removed." -ForegroundColor Green

