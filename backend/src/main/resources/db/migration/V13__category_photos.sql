-- Category images: professional photos from Pexels (free for commercial use, no credit needed; pexels.com/license),
-- cropped to the tiles' shape and kept in the photo bucket (R2). They replace the cut-outs from V10/V11; an image
-- set any other way is left alone. The Pexels photo id is noted on each line.
UPDATE categories SET image_key = 'categories/engines-photo-v1.jpg' WHERE id = 'engines' AND (image_key IS NULL OR image_key ~ '^categories/[a-z-]+-v[0-9]+\.jpg$'); -- pexels 17575475
UPDATE categories SET image_key = 'categories/gearboxes-photo-v1.jpg' WHERE id = 'gearboxes' AND (image_key IS NULL OR image_key ~ '^categories/[a-z-]+-v[0-9]+\.jpg$'); -- pexels 28721763
UPDATE categories SET image_key = 'categories/cylinder-heads-photo-v1.jpg' WHERE id = 'cylinder-heads' AND (image_key IS NULL OR image_key ~ '^categories/[a-z-]+-v[0-9]+\.jpg$'); -- pexels 8986047
UPDATE categories SET image_key = 'categories/body-panels-photo-v1.jpg' WHERE id = 'body-panels' AND (image_key IS NULL OR image_key ~ '^categories/[a-z-]+-v[0-9]+\.jpg$'); -- pexels 40649187
UPDATE categories SET image_key = 'categories/doors-photo-v1.jpg' WHERE id = 'doors' AND (image_key IS NULL OR image_key ~ '^categories/[a-z-]+-v[0-9]+\.jpg$'); -- pexels 35503906
UPDATE categories SET image_key = 'categories/turbochargers-photo-v1.jpg' WHERE id = 'turbochargers' AND (image_key IS NULL OR image_key ~ '^categories/[a-z-]+-v[0-9]+\.jpg$'); -- pexels 14277607
UPDATE categories SET image_key = 'categories/ecus-electronics-photo-v1.jpg' WHERE id = 'ecus-electronics' AND (image_key IS NULL OR image_key ~ '^categories/[a-z-]+-v[0-9]+\.jpg$'); -- pexels 36169774
UPDATE categories SET image_key = 'categories/engine-parts-photo-v1.jpg' WHERE id = 'engine-parts' AND (image_key IS NULL OR image_key ~ '^categories/[a-z-]+-v[0-9]+\.jpg$'); -- pexels 35531283
UPDATE categories SET image_key = 'categories/starters-alternators-photo-v1.jpg' WHERE id = 'starters-alternators' AND (image_key IS NULL OR image_key ~ '^categories/[a-z-]+-v[0-9]+\.jpg$'); -- pexels 3757226
UPDATE categories SET image_key = 'categories/fuel-injection-photo-v1.jpg' WHERE id = 'fuel-injection' AND (image_key IS NULL OR image_key ~ '^categories/[a-z-]+-v[0-9]+\.jpg$'); -- pexels 7564871
UPDATE categories SET image_key = 'categories/cooling-ac-photo-v1.jpg' WHERE id = 'cooling-ac' AND (image_key IS NULL OR image_key ~ '^categories/[a-z-]+-v[0-9]+\.jpg$'); -- pexels 33074473
UPDATE categories SET image_key = 'categories/lights-mirrors-photo-v1.jpg' WHERE id = 'lights-mirrors' AND (image_key IS NULL OR image_key ~ '^categories/[a-z-]+-v[0-9]+\.jpg$'); -- pexels 30792112
UPDATE categories SET image_key = 'categories/drivetrain-photo-v1.jpg' WHERE id = 'drivetrain' AND (image_key IS NULL OR image_key ~ '^categories/[a-z-]+-v[0-9]+\.jpg$'); -- pexels 37175105
UPDATE categories SET image_key = 'categories/interior-photo-v1.jpg' WHERE id = 'interior' AND (image_key IS NULL OR image_key ~ '^categories/[a-z-]+-v[0-9]+\.jpg$'); -- pexels 15223424
UPDATE categories SET image_key = 'categories/egr-emissions-photo-v1.jpg' WHERE id = 'egr-emissions' AND (image_key IS NULL OR image_key ~ '^categories/[a-z-]+-v[0-9]+\.jpg$'); -- pexels 10827691
UPDATE categories SET image_key = 'categories/air-intake-photo-v1.jpg' WHERE id = 'air-intake' AND (image_key IS NULL OR image_key ~ '^categories/[a-z-]+-v[0-9]+\.jpg$'); -- pexels 11074558
UPDATE categories SET image_key = 'categories/accessories-photo-v1.jpg' WHERE id = 'accessories' AND (image_key IS NULL OR image_key ~ '^categories/[a-z-]+-v[0-9]+\.jpg$'); -- pexels 35641871
UPDATE categories SET image_key = 'categories/steering-suspension-photo-v1.jpg' WHERE id = 'steering-suspension' AND (image_key IS NULL OR image_key ~ '^categories/[a-z-]+-v[0-9]+\.jpg$'); -- pexels 10912797
UPDATE categories SET image_key = 'categories/ignition-photo-v1.jpg' WHERE id = 'ignition' AND (image_key IS NULL OR image_key ~ '^categories/[a-z-]+-v[0-9]+\.jpg$'); -- pexels 36086528
UPDATE categories SET image_key = 'categories/instruments-infotainment-photo-v1.jpg' WHERE id = 'instruments-infotainment' AND (image_key IS NULL OR image_key ~ '^categories/[a-z-]+-v[0-9]+\.jpg$'); -- pexels 241188
UPDATE categories SET image_key = 'categories/engine-mounts-photo-v1.jpg' WHERE id = 'engine-mounts' AND (image_key IS NULL OR image_key ~ '^categories/[a-z-]+-v[0-9]+\.jpg$'); -- pexels 5158155
UPDATE categories SET image_key = 'categories/electrics-sensors-photo-v1.jpg' WHERE id = 'electrics-sensors' AND (image_key IS NULL OR image_key ~ '^categories/[a-z-]+-v[0-9]+\.jpg$'); -- pexels 11569036
UPDATE categories SET image_key = 'categories/brakes-photo-v1.jpg' WHERE id = 'brakes' AND (image_key IS NULL OR image_key ~ '^categories/[a-z-]+-v[0-9]+\.jpg$'); -- pexels 34277926
UPDATE categories SET image_key = 'categories/rebuild-kits-photo-v1.jpg' WHERE id = 'rebuild-kits' AND (image_key IS NULL OR image_key ~ '^categories/[a-z-]+-v[0-9]+\.jpg$'); -- pexels 8986044
UPDATE categories SET image_key = 'categories/other-parts-photo-v1.jpg' WHERE id = 'other-parts' AND (image_key IS NULL OR image_key ~ '^categories/[a-z-]+-v[0-9]+\.jpg$'); -- pexels 6517339
