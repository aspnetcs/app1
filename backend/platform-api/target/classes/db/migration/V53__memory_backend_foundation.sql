create table if not exists memory_consent (
    user_id uuid primary key,
    enabled boolean not null default false,
    created_at timestamp with time zone not null default now(),
    updated_at timestamp with time zone not null default now()
);

create table if not exists memory_entry (
    id uuid primary key,
    user_id uuid not null,
    content text not null,
    summary text null,
    source_type varchar(32) not null default 'manual',
    status varchar(20) not null default 'active',
    consent_snapshot boolean not null default true,
    last_recalled_at timestamp with time zone null,
    expires_at timestamp with time zone null,
    metadata_json jsonb not null default '{}'::jsonb,
    created_at timestamp with time zone not null default now(),
    updated_at timestamp with time zone not null default now()
);

create index if not exists idx_memory_entry_user_status
    on memory_entry (user_id, status);

create index if not exists idx_memory_entry_user_updated
    on memory_entry (user_id, updated_at desc);

create table if not exists memory_audit (
    id uuid primary key,
    user_id uuid not null,
    action varchar(32) not null,
    status varchar(20) not null default 'success',
    summary text not null,
    detail_json jsonb not null default '{}'::jsonb,
    created_at timestamp with time zone not null default now()
);

create index if not exists idx_memory_audit_user_created
    on memory_audit (user_id, created_at desc);

create index if not exists idx_memory_audit_action_created
    on memory_audit (action, created_at desc);
