ALTER TABLE clan_xp_grants DROP CONSTRAINT chk_clan_xp_grants_source;

UPDATE clan_xp_grants SET source = 'play' WHERE source = 'daily_play';

ALTER TABLE clan_xp_grants ADD CONSTRAINT chk_clan_xp_grants_source
    CHECK (source IN ('play', 'mission', 'war_break', 'war_win', 'war_loan'));
