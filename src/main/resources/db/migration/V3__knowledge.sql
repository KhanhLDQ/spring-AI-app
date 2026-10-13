CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS hstore;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Same layout PgVectorStore would create itself; owning it here keeps schema changes in Flyway.
-- 1536 dimensions match text-embedding-3-small and MockEmbeddingModel.
CREATE TABLE vector_store (
    id        UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    content   TEXT,
    metadata  JSON,
    embedding VECTOR(1536)
);
CREATE INDEX vector_store_embedding_idx ON vector_store USING hnsw (embedding vector_cosine_ops);

CREATE TABLE semantic_cache (
    id        UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    content   TEXT,
    metadata  JSON,
    embedding VECTOR(1536)
);
CREATE INDEX semantic_cache_embedding_idx ON semantic_cache USING hnsw (embedding vector_cosine_ops);

CREATE TABLE knowledge_docs (
    id           BIGSERIAL PRIMARY KEY,
    filename     VARCHAR(100) NOT NULL UNIQUE,
    content_hash VARCHAR(64)  NOT NULL UNIQUE,
    uploaded_at  TIMESTAMPTZ  NOT NULL,
    chunk_count  INTEGER      NOT NULL CHECK (chunk_count > 0)
);
