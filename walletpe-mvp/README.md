# Local PostgreSQL

PostgreSQL runs on the host machine and can be managed through pgAdmin. Create the service databases (`walletpe_user`, `walletpe_kyc`, `walletpe_wallet`, `walletpe_ledger`, `walletpe_notification`, `walletpe_audit`, and `walletpe_reporting`) by copying `.env.example` to `.env`, setting your local connection values, and running `./create-databases.ps1` (requires PostgreSQL command-line tools on PATH). Alternatively, run `infra/postgres/create-service-databases.sql` in pgAdmin against your existing `postgres` maintenance database.

```powershell
Copy-Item .env.example .env
```

Run a service from PowerShell using `./run-service.ps1 -Service user-service` (replace with any service name from the list above). The helper loads `DB_HOST`, `DB_PORT`, `DB_USERNAME`, and `DB_PASSWORD` from `.env` for that process. For an IDE launch, set the same variables in the run configuration because Spring Boot does not load `.env` automatically.
