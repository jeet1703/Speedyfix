# Starts the backend on http://localhost:8080 against the local database (Docker: `docker compose up -d db`),
# with the keys from render.env that are safe to use locally: admin, Google sign-in, DVSA, PayPal (sandbox), Resend,
# FedEx rates, Gemini and eBay. It leaves out the live database and R2, so local tests never touch live
# data (emails only go to addresses you enter while testing). Run the frontend separately: `cd frontend; npm run dev` (http://localhost:5173).
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$envFile = Join-Path $root 'render.env'
$keep = '^(ADMIN_EMAIL|ADMIN_PASSWORD|ADMIN_JWT_SECRET|ACCOUNT_JWT_SECRET|GOOGLE_CLIENT_ID|DVSA_\w+|PAYPAL_\w+|FEDEX_\w+|GEMINI_API_KEY|EBAY_\w+|RESEND_API_KEY)$'

if (Test-Path $envFile) {
    foreach ($line in Get-Content $envFile) {
        if ($line -match '^\s*([A-Z0-9_]+)\s*=\s*(.*)$') {
            $name = $Matches[1]
            $value = $Matches[2].Trim().Trim('"')
            if ($name -match $keep) { Set-Item -Path "env:$name" -Value $value }
        }
    }
} else {
    Write-Warning "render.env not found: FedEx and PayPal stay off."
}

# Test money only, whatever render.env says.
$env:PAYPAL_ENVIRONMENT = 'sandbox'
# Guests can check out locally: Google sign-in only works on addresses registered in Google Cloud.
$env:ORDERS_REQUIRE_SIGN_IN = 'false'
# Email sign-up and sign-in: codes are emailed through Resend (or appear in this window if render.env has no key).
$env:ACCOUNT_EMAIL_SIGN_IN = 'true'
$env:SPRING_PROFILES_ACTIVE = 'postgres'
Remove-Item env:DATABASE_URL, env:DATABASE_USERNAME, env:DATABASE_PASSWORD -ErrorAction SilentlyContinue

$mvn = (Get-Command mvn -ErrorAction SilentlyContinue).Source
if (-not $mvn) {
    $mvn = Get-ChildItem "$env:USERPROFILE\.m2\wrapper\dists" -Recurse -Filter mvn.cmd -ErrorAction SilentlyContinue |
        Sort-Object FullName -Descending | Select-Object -First 1 -ExpandProperty FullName
}
if (-not $mvn) { throw 'Maven not found. Install it, or run the backend from your IDE with these settings.' }

Set-Location (Join-Path $root 'backend')
& $mvn spring-boot:run
