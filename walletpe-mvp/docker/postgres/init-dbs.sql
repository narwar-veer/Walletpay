-- Initialize microservice databases
CREATE DATABASE walletpe_user;
CREATE DATABASE walletpe_kyc;
CREATE DATABASE walletpe_wallet;

-- Grant privileges
GRANT ALL PRIVILEGES ON DATABASE walletpe_user TO postgres;
GRANT ALL PRIVILEGES ON DATABASE walletpe_kyc TO postgres;
GRANT ALL PRIVILEGES ON DATABASE walletpe_wallet TO postgres;
