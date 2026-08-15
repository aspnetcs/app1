-- Rename legacy roleplay runtime keys to multi_agent_discussion.
INSERT INTO sys_config (config_key, config_value, updated_at)
SELECT 'multi_agent_discussion.enabled', config_value, CURRENT_TIMESTAMP
FROM sys_config
WHERE config_key = 'roleplay.enabled'
ON CONFLICT (config_key) DO NOTHING;

INSERT INTO sys_config (config_key, config_value, updated_at)
SELECT 'multi_agent_discussion.max_agents', config_value, CURRENT_TIMESTAMP
FROM sys_config
WHERE config_key = 'roleplay.max_roles'
ON CONFLICT (config_key) DO NOTHING;

INSERT INTO sys_config (config_key, config_value, updated_at)
SELECT 'multi_agent_discussion.max_rounds', config_value, CURRENT_TIMESTAMP
FROM sys_config
WHERE config_key = 'roleplay.max_rounds'
ON CONFLICT (config_key) DO NOTHING;

DELETE FROM sys_config
WHERE config_key IN ('roleplay.enabled', 'roleplay.max_roles', 'roleplay.max_rounds');

UPDATE sys_user_group
SET feature_flags = COALESCE((
        SELECT string_agg(flag, ',' ORDER BY first_ord)
        FROM (
                 SELECT normalized_flag AS flag, MIN(ord) AS first_ord
                 FROM (
                          SELECT CASE
                                     WHEN btrim(flag) = 'roleplay' THEN 'multi_agent_discussion'
                                     ELSE btrim(flag)
                                 END AS normalized_flag,
                                 ord
                          FROM unnest(string_to_array(COALESCE(sys_user_group.feature_flags, ''), ',')) WITH ORDINALITY AS flags(flag, ord)
                          WHERE btrim(flag) <> ''
                      ) normalized
                 GROUP BY normalized_flag
             ) deduped
    ), ''),
    updated_at = CURRENT_TIMESTAMP
WHERE COALESCE(feature_flags, '') ILIKE '%roleplay%';
