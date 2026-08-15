DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = 'public' AND table_name = 'knowledge_base') THEN
        RETURN;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = 'public' AND table_name = 'knowledge_document') THEN
        RETURN;
    END IF;

    with latest_document_titles as (
        select distinct on (kd.base_id)
            kd.base_id,
            nullif(trim(regexp_replace(kd.title, '[-_ ]?v[0-9]+$', '', 'i')), '') as repaired_name
        from knowledge_document kd
        join knowledge_base kb on kb.id = kd.base_id
        where kd.deleted_at is null
          and kb.deleted_at is null
        order by kd.base_id, kd.created_at desc, kd.updated_at desc
    ),
    repair_targets as (
        select
            kb.id,
            latest_document_titles.repaired_name,
            case
                when kb.description is null then latest_document_titles.repaired_name || U&'\77E5\8BC6\5E93'
                when upper(translate(kb.description, '? ', '')) = 'FAQ' then latest_document_titles.repaired_name || ' FAQ'
                when translate(kb.description, '? ', '') = '' then latest_document_titles.repaired_name || U&'\77E5\8BC6\5E93'
                else kb.description
            end as repaired_description
        from knowledge_base kb
        join latest_document_titles on latest_document_titles.base_id = kb.id
        where kb.deleted_at is null
          and translate(kb.name, '? ', '') = ''
          and latest_document_titles.repaired_name is not null
    )
    update knowledge_base kb
    set name = repair_targets.repaired_name,
        description = repair_targets.repaired_description,
        updated_at = now()
    from repair_targets
    where kb.id = repair_targets.id;
END $$;
