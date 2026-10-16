param(
    [Parameter(Mandatory = $true)]
    [ValidateSet('user-service', 'kyc-service', 'wallet-service', 'transaction-ledger-service', 'notification-service', 'audit-service', 'admin-reporting-service')]
    [string]$Service
)

$ErrorActionPreference = 'Stop'
$envFile = Join-Path $PSScriptRoot '.env'

if (-not (Test-Path -LiteralPath $envFile)) {
    throw "Missing $envFile. Copy .env.example to .env and add your local PostgreSQL credentials."
}

foreach ($line in Get-Content -LiteralPath $envFile) {
    $trimmed = $line.Trim()
    if (-not $trimmed -or $trimmed.StartsWith('#')) { continue }
    $separator = $trimmed.IndexOf('=')
    if ($separator -lt 1) { continue }
    $name = $trimmed.Substring(0, $separator).Trim()
    $value = $trimmed.Substring($separator + 1).Trim().Trim('"').Trim("'")
    [Environment]::SetEnvironmentVariable($name, $value, 'Process')
}

& mvn -f (Join-Path $PSScriptRoot "services/$Service/pom.xml") spring-boot:run
exit $LASTEXITCODE
