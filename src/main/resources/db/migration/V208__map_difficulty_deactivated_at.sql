ALTER TABLE map_difficulties ADD COLUMN deactivated_at TIMESTAMPTZ;

UPDATE map_difficulties
SET deactivated_at = updated_at
WHERE deactivation_reason IS NOT NULL
   OR (active = false AND status <> 'campaign');
