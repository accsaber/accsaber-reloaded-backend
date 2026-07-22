CREATE TABLE practice_scores (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name       VARCHAR(24) NOT NULL,
    score      INTEGER NOT NULL,
    level      INTEGER NOT NULL,
    accuracy   DOUBLE PRECISION NOT NULL,
    bad_cuts   INTEGER NOT NULL,
    bomb_hits  INTEGER NOT NULL,
    played_at  TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_practice_scores_score ON practice_scores (score DESC);
