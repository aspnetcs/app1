create extension if not exists vector;

create table if not exists knowledge_base (
    id uuid primary key,
    owner_user_id uuid not null,
    name varchar(160) not null,
    description text,
    chunk_size integer not null default 800,
    chunk_overlap integer not null default 120,
    retrieval_limit integer not null default 6,
    similarity_threshold double precision not null default 0.55,
    embedding_model varchar(120) not null default 'hash-local-v1',
    rerank_model varchar(120),
    created_at timestamp with time zone not null default now(),
    updated_at timestamp with time zone not null default now(),
    deleted_at timestamp with time zone
);

create table if not exists knowledge_document (
    id uuid primary key,
    base_id uuid not null references knowledge_base(id),
    source_type varchar(32) not null,
    title varchar(255) not null,
    source_uri text,
    content_hash varchar(64) not null,
    content_text text not null,
    metadata_json text not null default '{}',
    status varchar(32) not null default 'ready',
    chunk_count integer not null default 0,
    created_at timestamp with time zone not null default now(),
    updated_at timestamp with time zone not null default now(),
    deleted_at timestamp with time zone
);

create table if not exists knowledge_chunk (
    id uuid primary key,
    base_id uuid not null references knowledge_base(id),
    document_id uuid not null references knowledge_document(id),
    chunk_no integer not null,
    chunk_text text not null,
    token_count integer not null default 0,
    metadata_json text not null default '{}',
    embedding vector(1536) not null,
    created_at timestamp with time zone not null default now(),
    deleted_at timestamp with time zone
);

create table if not exists knowledge_ingest_job (
    id uuid primary key,
    base_id uuid not null references knowledge_base(id),
    document_id uuid references knowledge_document(id),
    requested_by uuid not null,
    source_type varchar(32) not null,
    status varchar(32) not null default 'pending',
    error_message text,
    processed_chunks integer not null default 0,
    created_at timestamp with time zone not null default now(),
    started_at timestamp with time zone,
    completed_at timestamp with time zone
);

create table if not exists knowledge_conversation_binding (
    id uuid primary key,
    user_id uuid not null,
    conversation_id uuid not null,
    base_id uuid not null references knowledge_base(id),
    created_at timestamp with time zone not null default now()
);

create unique index if not exists uq_knowledge_binding_user_conversation_base
    on knowledge_conversation_binding(user_id, conversation_id, base_id);
create index if not exists idx_knowledge_base_owner on knowledge_base(owner_user_id, created_at desc);
create index if not exists idx_knowledge_document_base on knowledge_document(base_id, created_at desc);
create index if not exists idx_knowledge_document_hash on knowledge_document(base_id, content_hash);
create index if not exists idx_knowledge_chunk_base_doc on knowledge_chunk(base_id, document_id, chunk_no);
create index if not exists idx_knowledge_job_base on knowledge_ingest_job(base_id, created_at desc);
create index if not exists idx_knowledge_binding_conversation on knowledge_conversation_binding(user_id, conversation_id);
create index if not exists idx_knowledge_chunk_embedding on knowledge_chunk using ivfflat (embedding vector_cosine_ops) with (lists = 100);
