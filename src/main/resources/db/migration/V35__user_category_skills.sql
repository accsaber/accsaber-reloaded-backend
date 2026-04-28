CREATE TABLE user_category_skills (
    user_id              BIGINT       NOT NULL,
    category_id          UUID         NOT NULL,
    skill_level          NUMERIC(5,2) NOT NULL,
    rank_score           NUMERIC(5,2) NOT NULL,
    sustained_score      NUMERIC(5,2) NOT NULL,
    peak_score           NUMERIC(5,2) NOT NULL,
    combined_score       NUMERIC(5,2) NOT NULL,
    raw_ap_for_one_gain  NUMERIC(20,6),
    top_ap               NUMERIC(20,6) NOT NULL,
    category_rank        INTEGER,
    active_players       BIGINT       NOT NULL,
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_user_category_skills PRIMARY KEY (user_id, category_id),
    CONSTRAINT fk_user_category_skills_user     FOREIGN KEY (user_id)     REFERENCES users(id),
    CONSTRAINT fk_user_category_skills_category FOREIGN KEY (category_id) REFERENCES categories(id)
);

CREATE INDEX idx_user_category_skills_skill ON user_category_skills (category_id, skill_level DESC);
CREATE INDEX idx_user_category_skills_user  ON user_category_skills (user_id);

CREATE TABLE user_category_skill_snapshots (
    id              UUID         PRIMARY KEY DEFAULT uuidv7(),
    user_id         BIGINT       NOT NULL,
    category_id     UUID         NOT NULL,
    skill_level     NUMERIC(5,2) NOT NULL,
    rank_score      NUMERIC(5,2) NOT NULL,
    sustained_score NUMERIC(5,2) NOT NULL,
    peak_score      NUMERIC(5,2) NOT NULL,
    combined_score  NUMERIC(5,2) NOT NULL,
    captured_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_user_category_skill_snapshots_user     FOREIGN KEY (user_id)     REFERENCES users(id),
    CONSTRAINT fk_user_category_skill_snapshots_category FOREIGN KEY (category_id) REFERENCES categories(id)
);

CREATE INDEX idx_user_category_skill_snapshots_user_time
    ON user_category_skill_snapshots (user_id, category_id, captured_at DESC);
