-- Category images: a cut-out (background removed) of a real part from the shop's own listings, on the site's light
-- grey, kept in the photo bucket (R2). Only categories without an image chosen by staff get one.
UPDATE categories SET image_key = 'categories/accessories-v1.jpg' WHERE id = 'accessories' AND image_key IS NULL;
UPDATE categories SET image_key = 'categories/body-panels-v1.jpg' WHERE id = 'body-panels' AND image_key IS NULL;
UPDATE categories SET image_key = 'categories/brakes-v1.jpg' WHERE id = 'brakes' AND image_key IS NULL;
UPDATE categories SET image_key = 'categories/cooling-ac-v1.jpg' WHERE id = 'cooling-ac' AND image_key IS NULL;
UPDATE categories SET image_key = 'categories/cylinder-heads-v1.jpg' WHERE id = 'cylinder-heads' AND image_key IS NULL;
UPDATE categories SET image_key = 'categories/drivetrain-v1.jpg' WHERE id = 'drivetrain' AND image_key IS NULL;
UPDATE categories SET image_key = 'categories/ecus-electronics-v1.jpg' WHERE id = 'ecus-electronics' AND image_key IS NULL;
UPDATE categories SET image_key = 'categories/egr-emissions-v1.jpg' WHERE id = 'egr-emissions' AND image_key IS NULL;
UPDATE categories SET image_key = 'categories/electrics-sensors-v1.jpg' WHERE id = 'electrics-sensors' AND image_key IS NULL;
UPDATE categories SET image_key = 'categories/engine-mounts-v1.jpg' WHERE id = 'engine-mounts' AND image_key IS NULL;
UPDATE categories SET image_key = 'categories/engine-parts-v1.jpg' WHERE id = 'engine-parts' AND image_key IS NULL;
UPDATE categories SET image_key = 'categories/engines-v1.jpg' WHERE id = 'engines' AND image_key IS NULL;
UPDATE categories SET image_key = 'categories/fuel-injection-v1.jpg' WHERE id = 'fuel-injection' AND image_key IS NULL;
UPDATE categories SET image_key = 'categories/gearboxes-v1.jpg' WHERE id = 'gearboxes' AND image_key IS NULL;
UPDATE categories SET image_key = 'categories/interior-v1.jpg' WHERE id = 'interior' AND image_key IS NULL;
UPDATE categories SET image_key = 'categories/lights-mirrors-v1.jpg' WHERE id = 'lights-mirrors' AND image_key IS NULL;
UPDATE categories SET image_key = 'categories/starters-alternators-v1.jpg' WHERE id = 'starters-alternators' AND image_key IS NULL;
UPDATE categories SET image_key = 'categories/steering-suspension-v1.jpg' WHERE id = 'steering-suspension' AND image_key IS NULL;
UPDATE categories SET image_key = 'categories/turbochargers-v1.jpg' WHERE id = 'turbochargers' AND image_key IS NULL;
