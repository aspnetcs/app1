alter table if exists user_preference
    add column if not exists spacing_vertical varchar(16) not null default '16px';

alter table if exists user_preference
    add column if not exists spacing_horizontal varchar(16) not null default '16px';
