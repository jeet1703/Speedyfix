-- Gives every listing without a photo a stock photo (Unsplash samples) chosen by its category.
-- For the DEMO copy of the database only (speedyfix_demo, made by db-clone-demo.ps1): never run this on the real one.
-- Rows are marked source = 'SAMPLE'; the eBay import replaces them with the real photos.
WITH stock (category_id, photos) AS (VALUES
    ('engines',              ARRAY['1552656967-7a0991a13906', '1616728249167-1d50a55de3d9', '1564730465543-e732e7fc9c10', '1527383418406-f85a3b146499']),
    ('cylinder-heads',       ARRAY['1786028924575-9948c6a58b22']),
    ('rebuild-kits',         ARRAY['1720244253125-f39d7aeccccf']),
    ('engine-parts',         ARRAY['1616728249167-1d50a55de3d9', '1720244253125-f39d7aeccccf', '1786028924575-9948c6a58b22']),
    ('engine-mounts',        ARRAY['1767340078222-98633f3ffd27']),
    ('gearboxes',            ARRAY['1711199694531-e982a79ea381']),
    ('drivetrain',           ARRAY['1711199694531-e982a79ea381']),
    ('turbochargers',        ARRAY['1664695221759-21ed7295f697']),
    ('fuel-injection',       ARRAY['1759148413911-5ded7ed2ec06']),
    ('egr-emissions',        ARRAY['1759148413911-5ded7ed2ec06', '1664695221759-21ed7295f697']),
    ('cooling-ac',           ARRAY['1486262715619-67b85e0b08d3']),
    ('electrics-sensors',    ARRAY['1584809923235-fabdba83d1df', '1537462715879-360eeb61a0ad']),
    ('ecus-electronics',     ARRAY['1584809923235-fabdba83d1df', '1537462715879-360eeb61a0ad']),
    ('starters-alternators', ARRAY['1584809923235-fabdba83d1df', '1537462715879-360eeb61a0ad']),
    ('brakes',               ARRAY['1696494561430-de087dd0bd69', '1763087978864-fe5b2778c9f7']),
    ('steering-suspension',  ARRAY['1696494561430-de087dd0bd69', '1763087978864-fe5b2778c9f7']),
    ('body-panels',          ARRAY['1489824904134-891ab64532f1', '1549317661-bd32c8ce0db2', '1580273916550-e323be2ae537', '1503376780353-7e6692767b70']),
    ('lights-mirrors',       ARRAY['1494976388531-d1058494cdd8', '1580273916550-e323be2ae537', '1492144534655-ae79c964c9d7']),
    ('interior',             ARRAY['1549317661-bd32c8ce0db2', '1489824904134-891ab64532f1']),
    ('accessories',          ARRAY['1537462715879-360eeb61a0ad']),
    ('other-parts',          ARRAY['1552656967-7a0991a13906'])
)
INSERT INTO product_photos (id, product_id, position, r2_key, width, height, source, source_url)
SELECT 's' || md5(p.id), p.id, 0, 'sample', 800, 800, 'SAMPLE',
       'https://images.unsplash.com/photo-' || s.photos[1 + abs(hashtext(p.id)) % array_length(s.photos, 1)]
           || '?auto=format&fit=crop&w=800&h=800&q=70'
FROM products p
JOIN stock s ON s.category_id = p.category_id
WHERE NOT EXISTS (SELECT 1 FROM product_photos ph WHERE ph.product_id = p.id);
