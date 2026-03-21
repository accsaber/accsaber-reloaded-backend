CREATE TABLE milestone_prerequisite_links (
    id                        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    milestone_id              UUID NOT NULL REFERENCES milestones(id),
    prerequisite_milestone_id UUID NOT NULL REFERENCES milestones(id),
    blocker                   BOOLEAN NOT NULL DEFAULT false,
    active                    BOOLEAN NOT NULL DEFAULT true,
    created_at                TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at                TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT unique_milestone_prerequisite UNIQUE (milestone_id, prerequisite_milestone_id),
    CONSTRAINT no_self_reference CHECK (milestone_id != prerequisite_milestone_id)
);

CREATE INDEX idx_milestone_prereq_milestone ON milestone_prerequisite_links (milestone_id) WHERE active = true;
CREATE INDEX idx_milestone_prereq_prerequisite ON milestone_prerequisite_links (prerequisite_milestone_id) WHERE active = true;
CREATE INDEX idx_milestone_prereq_blockers ON milestone_prerequisite_links (milestone_id) WHERE active = true AND blocker = true;
