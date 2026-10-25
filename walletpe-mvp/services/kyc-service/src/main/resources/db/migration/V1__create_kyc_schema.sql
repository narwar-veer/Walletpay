CREATE TABLE kyc_applications (
    kyc_id UUID PRIMARY KEY,
    user_id UUID NOT NULL UNIQUE,
    full_name VARCHAR(255) NOT NULL,
    date_of_birth DATE NOT NULL,
    aadhaar_reference VARCHAR(255) NOT NULL,
    aadhaar_hash VARCHAR(255) NOT NULL UNIQUE,
    document_reference VARCHAR(255) NOT NULL,
    status VARCHAR(32) NOT NULL,
    rejection_reason VARCHAR(64), rejection_notes VARCHAR(1000),
    reviewed_by UUID, reviewed_at TIMESTAMP, submitted_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL, updated_at TIMESTAMP NOT NULL
);
CREATE INDEX idx_kyc_user_id ON kyc_applications(user_id);
CREATE INDEX idx_kyc_aadhaar_hash ON kyc_applications(aadhaar_hash);
CREATE TABLE kyc_document_references (
    document_id UUID PRIMARY KEY, kyc_id UUID NOT NULL REFERENCES kyc_applications(kyc_id),
    document_type VARCHAR(255) NOT NULL, storage_path VARCHAR(255) NOT NULL,
    content_type VARCHAR(255), file_size BIGINT, created_at TIMESTAMP NOT NULL
);
CREATE TABLE kyc_outbox_events (
    event_id UUID PRIMARY KEY, aggregate_type VARCHAR(255) NOT NULL, aggregate_id VARCHAR(255) NOT NULL,
    partition_key VARCHAR(255) NOT NULL, event_type VARCHAR(255) NOT NULL, payload TEXT NOT NULL,
    status VARCHAR(32) NOT NULL, attempt_count INTEGER NOT NULL DEFAULT 0, published_at TIMESTAMP, created_at TIMESTAMP NOT NULL
);
CREATE INDEX idx_outbox_status ON kyc_outbox_events(status);
