ALTER TABLE clan_standing_events DROP CONSTRAINT chk_clan_standing_events_source;
ALTER TABLE clan_standing_events ADD CONSTRAINT chk_clan_standing_events_source
    CHECK (source IN ('war_break', 'war_win', 'mission'));
