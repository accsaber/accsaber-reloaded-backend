ALTER TABLE map_difficulties DROP CONSTRAINT IF EXISTS map_difficulties_status_check;
ALTER TABLE map_difficulties ADD CONSTRAINT map_difficulties_status_check
    CHECK (status IN ('queue', 'qualified', 'ranked', 'campaign'));

ALTER TABLE map_difficulties ALTER COLUMN category_id DROP NOT NULL;

ALTER TABLE map_difficulties ADD COLUMN imported_by BIGINT REFERENCES users(id);

CREATE INDEX idx_map_difficulties_imported_by ON map_difficulties(imported_by)
    WHERE imported_by IS NOT NULL AND active = true;

CREATE INDEX idx_map_difficulties_status ON map_difficulties(status)
    WHERE active = true;
