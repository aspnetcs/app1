create table if not exists ai_agent_run (
    id uuid primary key,
    user_id uuid not null,
    agent_id uuid not null,
    requested_channel_id bigint,
    bound_channel_id bigint,
    status varchar(32) not null default 'pending',
    error_message text,
    created_at timestamp with time zone not null default now(),
    updated_at timestamp with time zone not null default now(),
    started_at timestamp with time zone,
    completed_at timestamp with time zone
);

create index if not exists idx_ai_agent_run_user_created on ai_agent_run(user_id, created_at desc);
create index if not exists idx_ai_agent_run_status_updated on ai_agent_run(status, updated_at desc);

create table if not exists ai_agent_run_approval (
    id uuid primary key,
    run_id uuid not null unique,
    status varchar(32) not null default 'pending',
    decided_by uuid,
    decided_at timestamp with time zone,
    note text,
    created_at timestamp with time zone not null default now(),
    updated_at timestamp with time zone not null default now(),
    constraint fk_ai_agent_run_approval_run foreign key (run_id) references ai_agent_run(id) on delete cascade
);

create index if not exists idx_ai_agent_run_approval_status_updated on ai_agent_run_approval(status, updated_at desc);
