CREATE EXTENSION IF NOT EXISTS "pgcrypto";

CREATE TABLE users (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email           VARCHAR(255) NOT NULL UNIQUE,
    password_hash   VARCHAR(255) NOT NULL,
    full_name       VARCHAR(255),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE job_applications (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    company_name    VARCHAR(255) NOT NULL,
    role_title      VARCHAR(255) NOT NULL,
    status          VARCHAR(50) NOT NULL DEFAULT 'APPLIED',
    source          VARCHAR(20) NOT NULL DEFAULT 'MANUAL',
    job_url         TEXT,
    applied_date    DATE,
    notes           TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_job_applications_user_id ON job_applications(user_id);
CREATE INDEX idx_job_applications_status ON job_applications(status);

CREATE TABLE email_events (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id             UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    application_id      UUID REFERENCES job_applications(id) ON DELETE SET NULL,
    email_message_id    VARCHAR(512) NOT NULL,
    subject             TEXT,
    sender              VARCHAR(255),
    received_at         TIMESTAMPTZ,
    detected_company    VARCHAR(255),
    detected_status     VARCHAR(50),
    raw_snippet         TEXT,
    processed           BOOLEAN NOT NULL DEFAULT false,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (user_id, email_message_id)
);

CREATE INDEX idx_email_events_user_id ON email_events(user_id);
CREATE INDEX idx_email_events_application_id ON email_events(application_id);
