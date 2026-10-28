-- Shop categories for the eBay stock (144 eBay categories map onto these; see ebay/CategoryMapper.java).
-- Staff can re-point any eBay category by editing ebay_category_map; the next import uses it.
INSERT INTO categories (id, name, popular, sort_order) VALUES
    ('ecus-electronics',     'ECUs and electronics',     false, 12),
    ('starters-alternators', 'Starters and alternators', false, 13),
    ('engine-parts',         'Engine parts',             false, 14),
    ('egr-emissions',        'EGR and emissions',        false, 15),
    ('drivetrain',           'Drivetrain and axles',     false, 16),
    ('body-panels',          'Body panels and doors',    false, 17),
    ('lights-mirrors',       'Lights and mirrors',       false, 18),
    ('interior',             'Interior',                 false, 19),
    ('steering-suspension',  'Steering and suspension',  false, 20),
    ('other-parts',          'Other parts',              false, 21);
