-- The client's category list (three handwritten lists, 7 November 2026), in their order and wording:
--   1. Complete Engines, Bare Engines, Short Motors, Bare Cylinder Blocks, Cylinder Heads, Crankshafts, Camshafts,
--      Sumps and Pans, Oil Pumps, Gearboxes and Transfer Cases, Differentials and Axles, Coolant Components
--   2. Turbochargers, Alternators and Starter Motors, AC Compressors, Fuel Injection Pumps and Rails, Fuel Injectors,
--      Intake and Exhaust Manifolds, Throttle Bodies, EGR Valves and Coolers, Particulate Filters and Exhausts,
--      Solenoids and Sensors
--   3. ECUs and Electronic Modules, Instrument Clusters, Audio and Media Systems, Wiring Looms, Lights and Mirrors,
--      Sensors (shares Solenoids and Sensors)
-- Existing categories keep their ids (links and photos stay) and take the client's names; the rest are new. Stock the
-- lists don't cover (body panels, doors, interior and so on) keeps its categories, listed after the client's.
-- Listings move by title, each move only from the category it was found in. updated_at isn't touched: the eBay sync
-- compares it with ebay_synced_at.

INSERT INTO categories (id, name, popular, sort_order) VALUES
    ('short-motors',    'Short Motors',                 false, 3),
    ('cylinder-blocks', 'Bare Cylinder Blocks',         false, 4),
    ('crankshafts',     'Crankshafts',                  false, 6),
    ('camshafts',       'Camshafts',                    false, 7),
    ('sumps-pans',      'Sumps and Pans',               false, 8),
    ('oil-pumps',       'Oil Pumps',                    false, 9),
    ('ac-compressors',  'AC Compressors',               false, 15),
    ('fuel-injectors',  'Fuel Injectors',               false, 17),
    ('manifolds',       'Intake and Exhaust Manifolds', false, 18),
    ('egr-valves',      'EGR Valves and Coolers',       false, 20),
    ('audio-media',     'Audio and Media Systems',      false, 25),
    ('wiring-looms',    'Wiring Looms',                 false, 26)
ON CONFLICT (id) DO NOTHING;

UPDATE categories c SET name = n.name FROM (VALUES
    ('engines', 'Complete Engines'), ('bare-engines', 'Bare Engines'), ('cylinder-heads', 'Cylinder Heads'),
    ('gearboxes', 'Gearboxes and Transfer Cases'), ('drivetrain', 'Differentials and Axles'),
    ('cooling-ac', 'Coolant Components'), ('turbochargers', 'Turbochargers'),
    ('starters-alternators', 'Alternators and Starter Motors'), ('fuel-injection', 'Fuel Injection Pumps and Rails'),
    ('air-intake', 'Throttle Bodies'), ('egr-emissions', 'Particulate Filters and Exhausts'),
    ('electrics-sensors', 'Solenoids and Sensors'), ('ecus-electronics', 'ECUs and Electronic Modules'),
    ('instruments-infotainment', 'Instrument Clusters'), ('lights-mirrors', 'Lights and Mirrors')
) AS n(id, name) WHERE c.id = n.id;

UPDATE categories c SET sort_order = o.pos FROM (VALUES
    ('engines', 1), ('bare-engines', 2), ('short-motors', 3), ('cylinder-blocks', 4), ('cylinder-heads', 5),
    ('crankshafts', 6), ('camshafts', 7), ('sumps-pans', 8), ('oil-pumps', 9), ('gearboxes', 10), ('drivetrain', 11),
    ('cooling-ac', 12),
    ('turbochargers', 13), ('starters-alternators', 14), ('ac-compressors', 15), ('fuel-injection', 16),
    ('fuel-injectors', 17), ('manifolds', 18), ('air-intake', 19), ('egr-valves', 20), ('egr-emissions', 21),
    ('electrics-sensors', 22),
    ('ecus-electronics', 23), ('instruments-infotainment', 24), ('audio-media', 25), ('wiring-looms', 26),
    ('lights-mirrors', 27),
    ('engine-parts', 28), ('engine-mounts', 29), ('ignition', 30), ('rebuild-kits', 31), ('steering-suspension', 32),
    ('brakes', 33), ('body-panels', 34), ('doors', 35), ('interior', 36), ('accessories', 37), ('other-parts', 38)
) AS o(id, pos) WHERE c.id = o.id;

-- Accessories isn't on the client's list, so it leaves the homepage tiles.
UPDATE categories SET popular = false WHERE id = 'accessories';

-- List 1: engines by how much of the engine comes with them, then the engine internals out of Engine Parts.
UPDATE products SET category_id = 'short-motors'
    WHERE category_id IN ('engines', 'bare-engines') AND name ~* '(short motor|short block|half engine)';
UPDATE products SET category_id = 'cylinder-blocks'
    WHERE category_id IN ('engines', 'bare-engines')
      AND name ~* '(cylinder block|engine block|bare block|block only)' AND name !~* '(long block|short motor|short block)';
UPDATE products SET category_id = 'crankshafts'
    WHERE category_id = 'engine-parts' AND name ~* 'crankshaft' AND name !~* '(pulley|cover|sensor)';
UPDATE products SET category_id = 'camshafts' WHERE category_id = 'engine-parts' AND name ~* 'camshaft';
UPDATE products SET category_id = 'sumps-pans' WHERE category_id = 'engine-parts' AND name ~* 'sump' AND name !~* 'pipe';
UPDATE products SET category_id = 'oil-pumps' WHERE category_id = 'engine-parts' AND name ~* 'oil pump';

-- List 2.
UPDATE products SET category_id = 'ac-compressors' WHERE category_id = 'cooling-ac' AND name ~* 'compressor';
UPDATE products SET category_id = 'fuel-injectors'
    WHERE category_id = 'fuel-injection' AND name ~* 'injector' AND name !~* '(rail|ramp)';
UPDATE products SET category_id = 'manifolds'
    WHERE category_id IN ('air-intake', 'fuel-injection', 'egr-emissions') AND name ~* 'manifold'
      AND name !~* '(sensor|bracket|mount)';
UPDATE products SET category_id = 'egr-valves' WHERE category_id = 'egr-emissions' AND name ~* '\megr\M';
UPDATE products SET category_id = 'electrics-sensors'
    WHERE category_id IN ('air-intake', 'fuel-injection', 'egr-emissions', 'ecus-electronics', 'engine-parts', 'turbochargers')
      AND name ~* '(sensor|solenoid)' AND name !~* '(dpf|particulate|particular filter|catalytic)';
-- Air filter boxes and intake pipes aren't throttle bodies; they join the other engine parts.
UPDATE products SET category_id = 'engine-parts' WHERE category_id = 'air-intake' AND name !~* 'throttle';

-- List 3.
UPDATE products SET category_id = 'ecus-electronics'
    WHERE category_id IN ('gearboxes', 'brakes') AND name ~* 'control module';
UPDATE products SET category_id = 'wiring-looms' WHERE category_id = 'electrics-sensors' AND name ~* '(wiring|loom|harness)';
UPDATE products SET category_id = 'audio-media'
    WHERE category_id = 'instruments-infotainment' AND name !~* '(speedo|instrument|cluster|gauge)';
-- Wiper motors, arms and washer bottles were filed with the sensors.
UPDATE products SET category_id = 'other-parts'
    WHERE category_id = 'electrics-sensors' AND name ~* '(wiper|washer)';

-- Future eBay listings in these eBay categories land in the new ones (unless staff mapped them elsewhere).
UPDATE ebay_category_map m SET category_id = n.category_id FROM (VALUES
    ('engines', 'engine blocks', 'cylinder-blocks'),
    ('engine-parts', 'crankshafts', 'crankshafts'),
    ('engine-parts', 'camshafts', 'camshafts'),
    ('engine-parts', 'oil sumps', 'sumps-pans'),
    ('engine-parts', 'oil pumps & parts', 'oil-pumps'),
    ('cooling-ac', 'a/c compressors & clutches', 'ac-compressors'),
    ('fuel-injection', 'fuel injectors', 'fuel-injectors'),
    ('air-intake', 'intake manifolds', 'manifolds'),
    ('egr-emissions', 'manifolds & headers', 'manifolds'),
    ('egr-emissions', 'egr parts & valves', 'egr-valves'),
    ('egr-emissions', 'oxygen sensors', 'electrics-sensors'),
    ('air-intake', 'air intake & fuel sensors', 'electrics-sensors'),
    ('ecus-electronics', 'lidar & radar sensors', 'electrics-sensors'),
    ('air-intake', 'air filter housings', 'engine-parts'),
    ('air-intake', 'other air & fuel delivery', 'engine-parts'),
    ('electrics-sensors', 'wiring harnesses, cables & connectors', 'wiring-looms'),
    ('electrics-sensors', 'wiper linkages, transmissions & motors', 'other-parts'),
    ('electrics-sensors', 'wiper arms', 'other-parts'),
    ('electrics-sensors', 'additional wiper & washer components', 'other-parts'),
    ('instruments-infotainment', 'car stereos & head units', 'audio-media'),
    ('instruments-infotainment', 'amplifiers', 'audio-media'),
    ('instruments-infotainment', 'screens', 'audio-media')
) AS n(old_id, ebay_name, category_id)
WHERE m.category_id = n.old_id AND lower(m.ebay_name) = n.ebay_name;
