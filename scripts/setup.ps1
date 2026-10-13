# One-time (and safe to repeat) setup for Windows: checks tools, creates .env, starts Postgres, builds the jar.
$ErrorActionPreference = 'Stop'
Set-Location (Join-Path $PSScriptRoot '..')

function Fail($message) { Write-Error $message; exit 1 }
function RandomHex($bytes) {
    $buffer = [byte[]]::new($bytes)
    [System.Security.Cryptography.RandomNumberGenerator]::Fill($buffer)
    -join ($buffer | ForEach-Object { $_.ToString('x2') })
}

if (-not (Get-Command java -ErrorAction SilentlyContinue)) { Fail 'Java not found. Install JDK 25.' }
$versionLine = (& java -version 2>&1 | Select-Object -First 1).ToString()
if ($versionLine -notmatch 'version "(\d+)') { Fail "Cannot read the Java version from: $versionLine" }
if ([int]$Matches[1] -lt 25) { Fail "JDK 25 or newer is required (found $($Matches[1]))." }

docker info *> $null
if ($LASTEXITCODE -ne 0) { Fail 'Docker is not running. Start Rancher Desktop (dockerd/moby backend).' }

if (-not (Test-Path .env)) {
    $lines = Get-Content .env.example | ForEach-Object {
        if ($_ -like 'DB_PASSWORD=*') { "DB_PASSWORD=$(RandomHex 16)" }
        elseif ($_ -like 'JWT_SIGNING_KEY=*') { "JWT_SIGNING_KEY=$(RandomHex 32)" }
        else { $_ }
    }
    # No BOM: a BOM would glue itself to the first variable name.
    [System.IO.File]::WriteAllLines((Join-Path (Get-Location) '.env'), $lines)
    Write-Host 'Created .env with a database password and signing key.'
}
Write-Host "Add ANTHROPIC_API_KEY and OPENAI_API_KEY to .env (needed for the real model; 'run.ps1 mock' needs neither)."

docker compose up -d --wait postgres
if ($LASTEXITCODE -ne 0) { Fail 'docker compose failed.' }
& .\mvnw.cmd -B -q -DskipTests package
if ($LASTEXITCODE -ne 0) { Fail 'Build failed.' }
Write-Host 'Setup done. Start the app with scripts\run.ps1'
