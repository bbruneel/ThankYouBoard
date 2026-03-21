ALTER TABLE post
    ADD COLUMN IF NOT EXISTS capability_token_hash VARCHAR(64);

ALTER TABLE post
    ADD COLUMN IF NOT EXISTS capability_token_expires_at TIMESTAMP WITH TIME ZONE;

