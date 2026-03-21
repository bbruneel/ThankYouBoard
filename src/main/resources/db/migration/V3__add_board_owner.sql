-- Add Auth0 user ownership to boards.
-- owner_id stores the Auth0 `sub` claim (e.g. "auth0|abc123").

ALTER TABLE board
    ADD COLUMN IF NOT EXISTS owner_id VARCHAR(255);

-- Backfill existing rows (if any) to a deterministic placeholder so we can enforce NOT NULL.
UPDATE board
SET owner_id = 'legacy-owner'
WHERE owner_id IS NULL;

ALTER TABLE board
    ALTER COLUMN owner_id SET NOT NULL;

CREATE INDEX IF NOT EXISTS board_owner_id_idx ON board (owner_id);

