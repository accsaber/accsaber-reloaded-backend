DROP TABLE clan_war_loans;

ALTER TABLE clan_war_participants DROP COLUMN lent_by_clan_id;
ALTER TABLE clan_war_participants RENAME COLUMN duel_target_user_id TO target_user_id;
UPDATE clan_war_participants SET target_user_id = NULL;

ALTER TABLE clan_war_sides
    ADD COLUMN team TEXT,
    ADD COLUMN joined_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    ADD COLUMN left_at TIMESTAMPTZ;

UPDATE clan_war_sides s
SET team = CASE WHEN s.clan_id = w.attacker_clan_id THEN 'attack' ELSE 'defend' END,
    joined_at = w.declared_at
FROM clan_wars w
WHERE w.id = s.war_id;

ALTER TABLE clan_war_sides
    ALTER COLUMN team SET NOT NULL,
    ADD CONSTRAINT chk_clan_war_sides_team CHECK (team IN ('attack', 'defend'));

CREATE INDEX idx_clan_war_sides_war_team ON clan_war_sides (war_id, team);

ALTER TABLE clan_war_hits
    ADD COLUMN chip BOOLEAN NOT NULL DEFAULT FALSE,
    ADD CONSTRAINT chk_clan_war_hits_chip CHECK (NOT chip OR victim_score_id IS NOT NULL);

DROP INDEX uq_clan_war_hits_victim_score;
CREATE UNIQUE INDEX uq_clan_war_hits_victim_score ON clan_war_hits (war_id, attacker_user_id, victim_score_id, chip)
    WHERE victim_score_id IS NOT NULL;

CREATE TABLE clan_war_aid (
    id            UUID        PRIMARY KEY DEFAULT uuidv7(),
    war_id        UUID        NOT NULL REFERENCES clan_wars(id),
    clan_id       UUID        NOT NULL REFERENCES clans(id),
    team          TEXT        NOT NULL,
    direction     TEXT        NOT NULL,
    requested_by  BIGINT      NOT NULL REFERENCES users(id),
    status        TEXT        NOT NULL DEFAULT 'pending',
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    resolved_at   TIMESTAMPTZ,
    CONSTRAINT chk_clan_war_aid_team CHECK (team IN ('attack', 'defend')),
    CONSTRAINT chk_clan_war_aid_direction CHECK (direction IN ('request', 'offer')),
    CONSTRAINT chk_clan_war_aid_status CHECK (status IN ('pending', 'accepted', 'declined', 'cancelled')),
    CONSTRAINT chk_clan_war_aid_resolved CHECK ((status = 'pending') = (resolved_at IS NULL))
);

CREATE UNIQUE INDEX uq_clan_war_aid_open ON clan_war_aid (war_id, clan_id) WHERE status = 'pending';
CREATE INDEX idx_clan_war_aid_war ON clan_war_aid (war_id, created_at DESC);
CREATE INDEX idx_clan_war_aid_clan ON clan_war_aid (clan_id, created_at DESC);

ALTER TABLE clans ADD COLUMN pacifist_from TIMESTAMPTZ;

DELETE FROM clan_level_capacities WHERE capacity IN ('lend_slots', 'receive_slots');
ALTER TABLE clan_level_capacities DROP CONSTRAINT chk_clan_level_capacities_capacity;
ALTER TABLE clan_level_capacities ADD CONSTRAINT chk_clan_level_capacities_capacity
    CHECK (capacity IN ('member_slots', 'ally_slots', 'officer_slots', 'commander_slots'));

UPDATE clan_xp_grants SET source = 'war_break' WHERE source = 'war_loan';
ALTER TABLE clan_xp_grants DROP CONSTRAINT chk_clan_xp_grants_source;
ALTER TABLE clan_xp_grants ADD CONSTRAINT chk_clan_xp_grants_source
    CHECK (source IN ('play', 'mission', 'war_break', 'war_win'));

ALTER TABLE chat_messages
    DROP CONSTRAINT chk_chat_messages_event,
    ADD CONSTRAINT chk_chat_messages_event CHECK (event IN ('member_joined', 'member_left', 'member_kicked',
        'alliance_formed', 'alliance_ended', 'rival_declared', 'rivaled_by', 'war_declared', 'war_received',
        'war_started', 'war_hit', 'war_break', 'war_ended', 'missions_refreshed', 'war_ally_joined',
        'war_ally_left'));
