CREATE TABLE pdf_job (
    job_id      UUID PRIMARY KEY,
    board_id    UUID NOT NULL REFERENCES board(id) ON DELETE CASCADE,
    owner_id    VARCHAR(255) NOT NULL,
    status      VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    created_at  TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    download_path TEXT,
    error_code  VARCHAR(100),
    error_message TEXT
);

CREATE INDEX IF NOT EXISTS pdf_job_board_id_idx ON pdf_job (board_id);
