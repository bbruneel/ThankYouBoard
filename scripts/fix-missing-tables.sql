-- One-time fix when Flyway reports schema "up to date" but Hibernate fails with "missing table [board]".
-- Run this in Supabase SQL Editor (Dashboard → SQL Editor → New query), then restart Spring Boot.
-- Safe to run multiple times (IF NOT EXISTS).

CREATE TABLE IF NOT EXISTS board (
    id UUID PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    recipient_name VARCHAR(255) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS post (
    id UUID PRIMARY KEY,
    board_id UUID NOT NULL REFERENCES board(id) ON DELETE CASCADE,
    author_name VARCHAR(255) NOT NULL,
    message_text TEXT,
    giphy_url VARCHAR(1000),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);
