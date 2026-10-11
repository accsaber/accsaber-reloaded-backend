UPDATE mission_templates
SET description = 'The clan earns XP together this week. Plays, milestones, campaigns and missions all count, clan missions and wars do not.',
    event_targets = jsonb_set(event_targets, '{xp}', '15000'),
    updated_at = NOW()
WHERE code = 'clan_xp_pooled';
