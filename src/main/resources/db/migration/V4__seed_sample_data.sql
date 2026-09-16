-- Dev sample data so the frontend has something to show on a fresh clone.
INSERT IGNORE INTO locations (location_name) VALUES
    ('Main Warehouse'),
    ('Site B Storage');

INSERT IGNORE INTO items (item_code, name, category, unit, min_stock_threshold) VALUES
    ('DRL-001', 'Cordless Drill', 'TOOL', 'unit', 2),
    ('HAM-001', 'Claw Hammer', 'TOOL', 'unit', 3),
    ('GLV-001', 'Safety Gloves', 'CONSUMABLE', 'pair', 10),
    ('TAPE-001', 'Duct Tape', 'CONSUMABLE', 'roll', 5),
    ('CHN-001', 'Lifting Chain 2m', 'LIFTING_GEAR', 'unit', 1),
    ('SLG-001', 'Sling Strap 3T', 'LIFTING_GEAR', 'unit', 2);

INSERT IGNORE INTO item_stocks (item_id, location_id, current_qty)
SELECT i.id, l.id, CASE i.item_code
        WHEN 'DRL-001' THEN 10
        WHEN 'HAM-001' THEN 6
        WHEN 'GLV-001' THEN 50
        WHEN 'TAPE-001' THEN 20
        WHEN 'CHN-001' THEN 3
        WHEN 'SLG-001' THEN 4
    END
FROM items i, locations l
WHERE l.location_name = 'Main Warehouse'
  AND i.item_code IN ('DRL-001', 'HAM-001', 'GLV-001', 'TAPE-001', 'CHN-001', 'SLG-001');
