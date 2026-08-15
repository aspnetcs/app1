create table if not exists market_catalog_item (
    id uuid primary key,
    asset_type varchar(32) not null,
    source_id varchar(120) not null,
    title varchar(180) not null default '',
    summary varchar(320),
    description text,
    category varchar(80),
    tags varchar(500) not null default '',
    cover varchar(500) not null default '',
    featured boolean not null default false,
    enabled boolean not null default true,
    sort_order integer not null default 0,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create unique index if not exists uk_market_catalog_type_source
    on market_catalog_item (asset_type, source_id);

create index if not exists idx_market_catalog_type_enabled
    on market_catalog_item (asset_type, enabled, sort_order);

create index if not exists idx_market_catalog_featured
    on market_catalog_item (featured, sort_order);

create table if not exists user_saved_asset (
    id uuid primary key,
    user_id uuid not null,
    asset_type varchar(32) not null,
    source_id varchar(120) not null,
    enabled boolean not null default true,
    sort_order integer not null default 0,
    extra_config_json jsonb not null default '{}'::jsonb,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create unique index if not exists uk_user_saved_asset_user_type_source
    on user_saved_asset (user_id, asset_type, source_id);

create index if not exists idx_user_saved_asset_user_type
    on user_saved_asset (user_id, asset_type, enabled);

create table if not exists user_preference (
    user_id uuid primary key,
    default_agent_id varchar(120),
    theme_mode varchar(20) not null default 'system',
    code_theme varchar(32) not null default 'system',
    font_scale varchar(16) not null default 'md',
    mcp_mode varchar(20) not null default 'auto',
    preferred_mcp_server_id varchar(120),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);
