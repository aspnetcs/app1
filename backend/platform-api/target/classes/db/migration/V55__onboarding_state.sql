create table if not exists ai_user_onboarding_state (
    user_id uuid primary key,
    status varchar(32) not null default 'not_started',
    current_step varchar(64),
    completed_steps text not null default '',
    reset_count integer not null default 0,
    last_completed_at timestamp with time zone,
    skipped_at timestamp with time zone,
    created_at timestamp with time zone not null default now(),
    updated_at timestamp with time zone not null default now()
);

create index if not exists idx_ai_user_onboarding_status on ai_user_onboarding_state(status, updated_at desc);
create index if not exists idx_ai_user_onboarding_updated on ai_user_onboarding_state(updated_at desc);