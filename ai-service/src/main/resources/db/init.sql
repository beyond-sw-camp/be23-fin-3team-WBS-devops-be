-- PostgreSQL initialization for AI service (RAG / pgvector)
-- Executed automatically by the pgvector/pgvector:pg16 container
-- when it is started with an empty data volume.

-- 1) Required extensions
CREATE EXTENSION IF NOT EXISTS vector;       -- pgvector: embedding storage + ANN search
CREATE EXTENSION IF NOT EXISTS hstore;       -- Spring AI metadata storage
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";  -- UUID generation for primary keys

-- 2) Table creation is delegated to Spring AI (initialize-schema: true).
--    The default "vector_store" table will be created on first boot with:
--      id UUID, content TEXT, metadata JSONB, embedding vector(<dimensions>)
--    Do NOT create it here to avoid dimension/column drift.
