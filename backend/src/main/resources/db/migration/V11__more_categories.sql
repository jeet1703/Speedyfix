-- Categories for eBay groups that were buried inside broader shop categories: doors (from body panels), air intake
-- and throttle bodies (from fuel injection), ignition (from electrics) and instruments and infotainment (from ECUs).
-- Renames only apply where staff haven't renamed the category themselves.

INSERT INTO categories (id, name, popular, sort_order) VALUES
    ('air-intake', 'Air intake and throttle bodies', false, 9),
    ('ignition', 'Ignition', false, 12),
    ('instruments-infotainment', 'Instruments and infotainment', false, 15),
    ('doors', 'Doors', false, 21)
ON CONFLICT (id) DO NOTHING;

UPDATE categories SET name = 'Body panels' WHERE id = 'body-panels' AND name = 'Body panels and doors';
UPDATE categories SET name = 'Exhaust and emissions' WHERE id = 'egr-emissions' AND name = 'EGR and emissions';

-- A clear order, the new categories next to the ones they came from.
UPDATE categories c SET sort_order = o.pos FROM (VALUES
    ('engines', 1), ('gearboxes', 2), ('accessories', 3), ('turbochargers', 4), ('rebuild-kits', 5),
    ('cylinder-heads', 6), ('engine-mounts', 7), ('fuel-injection', 8), ('air-intake', 9), ('cooling-ac', 10),
    ('electrics-sensors', 11), ('ignition', 12), ('brakes', 13), ('ecus-electronics', 14), ('instruments-infotainment', 15),
    ('starters-alternators', 16), ('engine-parts', 17), ('egr-emissions', 18), ('drivetrain', 19), ('body-panels', 20),
    ('doors', 21), ('lights-mirrors', 22), ('interior', 23), ('steering-suspension', 24), ('other-parts', 25)
) AS o(id, pos) WHERE c.id = o.id;

-- Future eBay listings in these eBay categories land in the new ones (unless staff mapped them elsewhere).
UPDATE ebay_category_map SET category_id = 'doors'
    WHERE category_id = 'body-panels' AND lower(ebay_name) = 'doors & door skins';
UPDATE ebay_category_map SET category_id = 'air-intake'
    WHERE category_id = 'fuel-injection' AND lower(ebay_name) IN ('throttle bodies', 'intake manifolds', 'air filter housings',
        'air intake & fuel sensors', 'other air & fuel delivery');
UPDATE ebay_category_map SET category_id = 'ignition'
    WHERE category_id = 'electrics-sensors' AND lower(ebay_name) IN ('ignition coils', 'spark plugs', 'ignition leads');
UPDATE ebay_category_map SET category_id = 'instruments-infotainment'
    WHERE category_id IN ('ecus-electronics', 'interior') AND lower(ebay_name) IN ('instrument clusters', 'car stereos & head units',
        'screens', 'amplifiers', 'individual gauges');

-- Parts already in the shop move by their titles (the eBay category isn't stored on each part).
UPDATE products SET category_id = 'doors', updated_at = now()
    WHERE category_id = 'body-panels' AND name ~* '\mdoors?\M'
      AND name !~* '(handle|mirror|lock|seal|hinge|regulator|speaker|card|panel trim)';
UPDATE products SET category_id = 'air-intake', updated_at = now()
    WHERE category_id = 'fuel-injection'
      AND name ~* '(throttle|intake manifold|inlet manifold|air filter|air box|airbox|air intake|\mmaf\M|mass air)';
UPDATE products SET category_id = 'ignition', updated_at = now()
    WHERE category_id = 'electrics-sensors' AND name ~* '(ignition|coil pack|\mcoils?\M)';
UPDATE products SET category_id = 'instruments-infotainment', updated_at = now()
    WHERE category_id = 'ecus-electronics'
      AND name ~* '(instrument cluster|speedo|\mclocks\M|stereo|radio|head unit|cd player|screen|display|amplifier|\mamp\M|sat ?nav|navigation|infotainment|multimedia)';

-- Cut-out photos (in R2) for the new categories; body panels gets a bonnet now that doors have their own.
UPDATE categories SET image_key = 'categories/doors-v1.jpg' WHERE id = 'doors' AND image_key IS NULL;
UPDATE categories SET image_key = 'categories/air-intake-v1.jpg' WHERE id = 'air-intake' AND image_key IS NULL;
UPDATE categories SET image_key = 'categories/ignition-v1.jpg' WHERE id = 'ignition' AND image_key IS NULL;
UPDATE categories SET image_key = 'categories/instruments-infotainment-v1.jpg' WHERE id = 'instruments-infotainment' AND image_key IS NULL;
UPDATE categories SET image_key = 'categories/body-panels-v2.jpg' WHERE id = 'body-panels' AND image_key = 'categories/body-panels-v1.jpg';
