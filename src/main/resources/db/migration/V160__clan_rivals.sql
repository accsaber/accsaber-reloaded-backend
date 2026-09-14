CREATE TABLE clan_rivals (
    id             UUID        PRIMARY KEY DEFAULT uuidv7(),
    clan_id        UUID        NOT NULL REFERENCES clans(id),
    rival_clan_id  UUID        NOT NULL REFERENCES clans(id),
    declared_by    BIGINT      REFERENCES users(id),
    active         BOOLEAN     NOT NULL DEFAULT true,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_clan_rivals_pair UNIQUE (clan_id, rival_clan_id),
    CONSTRAINT chk_clan_rivals_self CHECK (clan_id <> rival_clan_id)
);

CREATE INDEX idx_clan_rivals_rival ON clan_rivals (rival_clan_id) WHERE active;
