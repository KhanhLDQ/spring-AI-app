# Starts Postgres if needed and runs the packaged app. Usage: scripts\run.ps1 [mock]
# Default profile is "demo" (real Anthropic + OpenAI calls); "mock" needs no API keys.
param([string]$Profile = 'demo')
$ErrorActionPreference = 'Stop'
Set-Location (Join-Path $PSScriptRoot '..')

function Fail($message) { Write-Error $message; exit 1 }

if (-not (Test-Path .env)) { Fail '.env is missing. Run scripts\setup.ps1 first.' }
# Variables live in this process only; nothing is written to the system environment.
foreach ($line in Get-Content .env) {
    if ($line -match '^\s*([A-Za-z_][A-Za-z0-9_]*)\s*=\s*(.*?)\s*$') {
        $value = $Matches[2].Trim('"', "'")
        [Environment]::SetEnvironmentVariable($Matches[1], $value, 'Process')
    }
}

docker compose up -d --wait postgres
if ($LASTEXITCODE -ne 0) { Fail 'docker compose failed. Is Docker running?' }

$jar = Get-ChildItem target\spring-AI-app-*.jar -ErrorAction SilentlyContinue | Select-Object -First 1
if (-not $jar) { Fail 'No jar in target\. Run scripts\setup.ps1 first.' }

$env:SPRING_PROFILES_ACTIVE = $Profile
Write-Host "Starting on http://localhost:8080 (profile: $Profile)"
& java -jar $jar.FullName
