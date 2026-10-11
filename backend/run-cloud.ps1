# run-cloud.ps1 — Start backend with cloud database profile

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$EnvFile = Join-Path $PSScriptRoot ".env.cloud"

if (-not (Test-Path $EnvFile)) {
    Write-Host ""
    Write-Host "ERROR: .env.cloud not found." -ForegroundColor Red
    Write-Host ""
    Write-Host "Copy .env.cloud.example to .env.cloud and fill in:"
    Write-Host "  DB_URL"
    Write-Host "  DB_USERNAME"
    Write-Host "  DB_PASSWORD"
    Write-Host ""
    exit 1
}

# Save current environment so running cloud does not affect later local runs
$oldProfile  = $env:SPRING_PROFILES_ACTIVE
$oldUrl      = $env:DB_URL
$oldUsername = $env:DB_USERNAME
$oldPassword = $env:DB_PASSWORD
$oldCloudinary = $env:CLOUDINARY_URL

try {
    # Load .env.cloud into current process
    Get-Content $EnvFile | ForEach-Object {
        $line = $_.Trim()

        if ($line -eq "" -or $line.StartsWith("#")) {
            return
        }

        $parts = $line.Split("=", 2)

        if ($parts.Count -ne 2) {
            return
        }

        $key = $parts[0].Trim()
        $value = $parts[1].Trim()

        [System.Environment]::SetEnvironmentVariable(
            $key,
            $value,
            "Process"
        )
    }

    Write-Host "Cloud profile loaded." -ForegroundColor Cyan
    Write-Host "Starting Spring Boot with profile: cloud ..." -ForegroundColor Green
    Write-Host ""

    & "$PSScriptRoot\mvnw.cmd" spring-boot:run
}
finally {
    # Restore previous environment
    $env:SPRING_PROFILES_ACTIVE = $oldProfile
    $env:DB_URL = $oldUrl
    $env:DB_USERNAME = $oldUsername
    $env:DB_PASSWORD = $oldPassword
    $env:CLOUDINARY_URL = $oldCloudinary

    Write-Host ""
    Write-Host "Cloud environment cleared. Terminal restored." -ForegroundColor Yellow
}