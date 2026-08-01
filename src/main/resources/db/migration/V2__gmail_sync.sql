CREATE TABLE gmail_accounts (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id             UUID NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    google_email        VARCHAR(255),
    access_token        TEXT NOT NULL,
    refresh_token       TEXT NOT NULL,
    token_expiry        TIMESTAMPTZ NOT NULL,
    last_synced_at      TIMESTAMPTZ,
    connected_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_gmail_accounts_user_id ON gmail_accounts(user_id);

-- Extra fields populated by the Claude classification step
ALTER TABLE email_events ADD COLUMN is_job_related BOOLEAN;
ALTER TABLE email_events ADD COLUMN confidence REAL;
ALTER TABLE email_events ADD COLUMN classification_error TEXT;
