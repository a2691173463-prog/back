CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE SCHEMA IF NOT EXISTS rag;

COMMENT ON SCHEMA rag IS 'QA interview question embeddings, documents, and retrieval metadata';
