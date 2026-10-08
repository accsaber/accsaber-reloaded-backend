DELETE FROM clan_level_capacities WHERE capacity = 'mission_slots';

ALTER TABLE clan_level_capacities DROP CONSTRAINT chk_clan_level_capacities_capacity;
ALTER TABLE clan_level_capacities ADD CONSTRAINT chk_clan_level_capacities_capacity
    CHECK (capacity IN ('member_slots', 'ally_slots', 'lend_slots', 'receive_slots', 'officer_slots', 'commander_slots'));
