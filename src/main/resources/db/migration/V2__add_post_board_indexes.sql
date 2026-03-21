-- Index on post(board_id) for FK lookups and JOINs (avoids full table scan).
-- Composite (board_id, created_at) supports getPostsByBoardIdOrderByCreatedAtAsc.
CREATE INDEX IF NOT EXISTS post_board_id_created_at_idx ON post (board_id, created_at);
