ALTER TABLE items ADD COLUMN downloadable BOOLEAN NOT NULL DEFAULT false;

INSERT INTO item_types (key, name, description, value_schema) VALUES
    ('saber', 'Saber',
    'Custom saber model distributed as a downloadable file.',
    '{"type":"object","properties":{"file":{"type":"string"}},"required":["file"]}'),

    ('item_pedestal', 'Item Pedestal',
    'Pedestal display model distributed as a downloadable file.',
    '{"type":"object","properties":{"file":{"type":"string"}},"required":["file"]}');
