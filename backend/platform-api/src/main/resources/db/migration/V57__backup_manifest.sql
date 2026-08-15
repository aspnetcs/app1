create table if not exists user_backup_export (
    id uuid primary key,
    user_id uuid not null,
    schema_version int not null,
    manifest_json jsonb not null,
    created_at timestamp with time zone not null default now()
);

create index if not exists idx_user_backup_export_user_created on user_backup_export(user_id, created_at desc);

