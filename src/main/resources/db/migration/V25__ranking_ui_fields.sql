ALTER TABLE map_difficulties ADD COLUMN IF NOT EXISTS created_by UUID REFERENCES staff_users(id);

UPDATE map_difficulties SET created_by = last_updated_by WHERE last_updated_by IS NOT NULL AND created_by IS NULL;
