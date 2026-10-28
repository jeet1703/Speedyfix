-- The shop's categories. Popular ones show on the homepage in sort order: engines, gearboxes, accessories, turbochargers.
-- More categories for the eBay stock (ECUs, body panels and so on) arrive with the reviewed category map.
INSERT INTO categories (id, name, popular, sort_order) VALUES
    ('engines',           'Engines',               true,  1),
    ('gearboxes',         'Gearboxes',             true,  2),
    ('accessories',       'Accessories',           true,  3),
    ('turbochargers',     'Turbochargers',         true,  4),
    ('rebuild-kits',      'Rebuild kits',          false, 5),
    ('cylinder-heads',    'Cylinder heads',        false, 6),
    ('engine-mounts',     'Engine mounts',         false, 7),
    ('fuel-injection',    'Fuel injection',        false, 8),
    ('cooling-ac',        'Cooling and A/C',       false, 9),
    ('electrics-sensors', 'Electrics and sensors', false, 10),
    ('brakes',            'Brakes',                false, 11);
