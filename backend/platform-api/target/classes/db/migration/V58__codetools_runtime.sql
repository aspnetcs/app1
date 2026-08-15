create table if not exists ai_code_tools_task (
    id uuid primary key,
    user_id uuid not null,
    kind varchar(64) not null,
    status varchar(32) not null default 'pending',
    input_json text,
    created_at timestamp with time zone not null default now(),
    updated_at timestamp with time zone not null default now()
);

create index if not exists idx_ai_code_tools_task_user_created on ai_code_tools_task(user_id, created_at desc);
create index if not exists idx_ai_code_tools_task_status_updated on ai_code_tools_task(status, updated_at desc);

create table if not exists ai_code_tools_task_approval (
    id uuid primary key,
    task_id uuid not null unique,
    status varchar(32) not null default 'pending',
    decided_by uuid,
    decided_at timestamp with time zone,
    note text,
    created_at timestamp with time zone not null default now(),
    updated_at timestamp with time zone not null default now(),
    constraint fk_ai_code_tools_task_approval_task foreign key (task_id) references ai_code_tools_task(id) on delete cascade
);

create index if not exists idx_ai_code_tools_task_approval_status_updated on ai_code_tools_task_approval(status, updated_at desc);

create table if not exists ai_code_tools_task_log (
    id bigserial primary key,
    task_id uuid not null,
    level varchar(16) not null default 'INFO',
    message text not null,
    created_at timestamp with time zone not null default now(),
    constraint fk_ai_code_tools_task_log_task foreign key (task_id) references ai_code_tools_task(id) on delete cascade
);

create index if not exists idx_ai_code_tools_task_log_task_created on ai_code_tools_task_log(task_id, created_at asc);

create table if not exists ai_code_tools_task_artifact (
    id bigserial primary key,
    task_id uuid not null,
    artifact_type varchar(32) not null,
    name varchar(255),
    mime varchar(128),
    content_text text,
    content_url text,
    created_at timestamp with time zone not null default now(),
    constraint fk_ai_code_tools_task_artifact_task foreign key (task_id) references ai_code_tools_task(id) on delete cascade
);

create index if not exists idx_ai_code_tools_task_artifact_task_created on ai_code_tools_task_artifact(task_id, created_at desc);

