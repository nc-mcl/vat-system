param(
    [switch]$Start
)

$ErrorActionPreference = 'Stop'

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot '..')

Push-Location $repoRoot
try {
    $composeFile = ".devcontainer/docker-compose.yml"

    Write-Host "Running: docker compose -f $composeFile config"
    & docker compose -f $composeFile config

    Write-Host "Running: docker compose -f $composeFile build dev"
    & docker compose -f $composeFile build dev

    if ($Start) {
        Write-Host "Running: docker compose -f $composeFile up -d dev"
        & docker compose -f $composeFile up -d dev

        Write-Host "Running: docker compose -f $composeFile ps"
        & docker compose -f $composeFile ps
    }
} finally {
    Pop-Location
}
