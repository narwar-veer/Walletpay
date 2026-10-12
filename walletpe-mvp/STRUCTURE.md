# WalletPe MVP Microservices Structure

This skeleton is based on the Phase 1 MVP and HLD in `docs/Requirements 3cdd919f3f46817ab964d1bdc696498c.md`.
Files are placeholders only; no implementation code has been added.

## Root

- `api-gateway/` - external entry point for routing, JWT enforcement, role checks, rate limiting, correlation IDs, and request validation.
- `services/user-service/` - registration, email/mobile OTP verification, authentication, OAuth identity mapping, refresh tokens, user profile, roles, and account lifecycle.
- `services/kyc-service/` - KYC submission, manual review state, rejection reasons, resubmission policy, document references, KYC status, and protected storage boundaries.
- `services/wallet-service/` - one-user-one-wallet lifecycle, wallet ID, wallet status, balance view, eligibility checks, and wallet blocking.
- `services/transaction-ledger-service/` - internal transfers, idempotency, atomic debit/credit, ledger entries, transaction history, promotional credit, reversal, reconciliation, and transactional outbox.
- `services/notification-service/` - in-app notifications, notification preferences, delivery state, duplicate-safe event consumption, and retry jobs.
- `services/admin-reporting-service/` - admin KYC workflows, operational reports, manual refresh, admin-created notifications, and daily reporting jobs.
- `services/audit-service/` - append-only audit trail for security-sensitive, administrative, KYC, wallet, financial, report, and notification actions.
- `shared/contracts/` - OpenAPI and event schema placeholders for service contracts.
- `shared/libraries/` - common cross-cutting library placeholders for errors, security, validation, and correlation IDs.
- `infra/` - deployment, PostgreSQL, Kafka, Redis, and observability placeholders.
- `config/` - environment and secret-management placeholders.
- `tests/` - cross-service contract, integration, load, and security test placeholders.

## Service Folder Convention

Each service follows the same high-level layout:

- `pom.xml` - service build placeholder.
- `Dockerfile` - container image placeholder.
- `src/main/resources/application.yml` - service configuration placeholder.
- `src/main/java/com/walletpe/<domain>/api/controllers/` - inbound API entry points.
- `src/main/java/com/walletpe/<domain>/application/usecases/` - requirement-level application operations.
- `src/main/java/com/walletpe/<domain>/domain/` - domain models, statuses, events, and business concepts.
- `src/main/java/com/walletpe/<domain>/infrastructure/` - persistence, messaging, storage, and external adapters.
- `src/main/java/com/walletpe/<domain>/jobs/` - scheduled or background jobs where required.
- `src/test/java/com/walletpe/<domain>/` - contract, idempotency, concurrency, and service-level test placeholders.

## Requirement Mapping

- User eligibility and lifecycle: `services/user-service/`, `services/kyc-service/`, `services/wallet-service/`
- Registration and OTP verification: `services/user-service/`
- Authentication and authorization: `api-gateway/`, `services/user-service/`, `shared/libraries/security/`
- KYC management and protected document handling: `services/kyc-service/`, `services/admin-reporting-service/`
- Wallet creation and wallet status: `services/wallet-service/`
- Promotional credit: `services/transaction-ledger-service/`
- Internal transfers, double-spend prevention, idempotency, and ledger: `services/transaction-ledger-service/`
- Receiving money and transaction history: `services/transaction-ledger-service/`
- Notifications: `services/notification-service/`
- Admin reporting and manual refresh: `services/admin-reporting-service/`
- Auditability: `services/audit-service/`
- Event-driven side effects and outbox: `services/transaction-ledger-service/`, `shared/contracts/events/`, `infra/kafka/`
- Scheduled jobs: `services/*/src/main/java/com/walletpe/*/jobs/`
- CI/CD: `Jenkinsfile`
- Deployment architecture: `infra/kubernetes/`, `docker-compose.yml`
- Observability: `infra/observability/`
- Security, rate limiting, and abuse controls: `api-gateway/`, `shared/libraries/security/`, `tests/security/`
