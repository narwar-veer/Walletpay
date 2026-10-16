SELECT 'CREATE DATABASE walletpe_user OWNER ' || quote_ident(current_user)
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'walletpe_user')\gexec

SELECT 'CREATE DATABASE walletpe_kyc OWNER ' || quote_ident(current_user)
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'walletpe_kyc')\gexec

SELECT 'CREATE DATABASE walletpe_wallet OWNER ' || quote_ident(current_user)
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'walletpe_wallet')\gexec

SELECT 'CREATE DATABASE walletpe_ledger OWNER ' || quote_ident(current_user)
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'walletpe_ledger')\gexec

SELECT 'CREATE DATABASE walletpe_notification OWNER ' || quote_ident(current_user)
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'walletpe_notification')\gexec

SELECT 'CREATE DATABASE walletpe_audit OWNER ' || quote_ident(current_user)
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'walletpe_audit')\gexec

SELECT 'CREATE DATABASE walletpe_reporting OWNER ' || quote_ident(current_user)
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'walletpe_reporting')\gexec
