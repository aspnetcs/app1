alter table if exists ai_conversation
    add column if not exists team_turns_json text;
