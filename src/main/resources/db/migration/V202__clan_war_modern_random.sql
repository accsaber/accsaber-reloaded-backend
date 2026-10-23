ALTER TABLE clan_wars DROP CONSTRAINT chk_clan_wars_arena,
    ADD CONSTRAINT chk_clan_wars_arena CHECK (arena IN ('mixed', 'random', 'category_turf', 'complexity_turf', 'modern_random'));

ALTER TABLE clan_level_war_modes DROP CONSTRAINT chk_clan_level_war_modes_mode,
    ADD CONSTRAINT chk_clan_level_war_modes_mode CHECK (
        (axis = 'arena' AND mode IN ('mixed', 'random', 'category_turf', 'complexity_turf', 'modern_random'))
        OR (axis = 'ruleset' AND mode IN ('duel', 'berserker')));

INSERT INTO clan_level_war_modes (axis, mode, level) VALUES ('arena', 'modern_random', 4);
