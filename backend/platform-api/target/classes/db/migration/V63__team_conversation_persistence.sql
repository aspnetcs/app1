alter table if exists ai_conversation
    add column if not exists mode varchar(20) not null default 'chat';

alter table if exists ai_conversation
    add column if not exists captain_selection_mode varchar(32);

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = 'public' AND table_name = 'ai_conversation') THEN
        UPDATE ai_conversation
        SET mode = case
                       when coalesce(nullif(btrim(compare_models_json), ''), '[]') <> '[]' then 'compare'
                       else 'chat'
            end
        WHERE mode is null
           OR btrim(mode) = '';

        UPDATE ai_conversation
        SET mode = 'compare'
        WHERE mode = 'chat'
          AND coalesce(nullif(btrim(compare_models_json), ''), '[]') <> '[]';
    END IF;
END $$;
