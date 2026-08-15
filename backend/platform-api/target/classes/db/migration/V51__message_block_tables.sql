create table if not exists ai_message_block (
    id uuid primary key,
    message_id uuid not null,
    conversation_id uuid not null,
    role varchar(20) not null,
    block_type varchar(32) not null,
    block_key varchar(120) not null,
    sequence_no integer not null default 0,
    status varchar(20) not null default 'final',
    payload_json text not null,
    created_at timestamp with time zone not null default now(),
    updated_at timestamp with time zone not null default now()
);

create unique index if not exists uq_ai_message_block_message_key
    on ai_message_block (message_id, block_key);

create index if not exists idx_ai_message_block_message_seq
    on ai_message_block (message_id, sequence_no, created_at);

create index if not exists idx_ai_message_block_conversation
    on ai_message_block (conversation_id, created_at);
