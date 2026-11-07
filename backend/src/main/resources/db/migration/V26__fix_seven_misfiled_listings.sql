-- Seven listings in the wrong category, found by checking every title against its category by hand (7 November 2026).
-- Each move names the listing and the category it's leaving, so it's a no-op if staff have already moved it.
-- updated_at isn't touched: the eBay sync compares it with ebay_synced_at.
UPDATE products SET category_id = 'cooling-ac' WHERE id = 'ford-ranger-automatic-gearbox-oil-cooler-2-0l-dies-366164975339' AND category_id = 'gearboxes';
UPDATE products SET category_id = 'cooling-ac' WHERE id = 'jaguar-xj-x351-3-0l-sdv6-gearbox-oil-cooler-pipe-g-366731277533' AND category_id = 'gearboxes';
-- A selector module is an electronic part sent by parcel, not a gearbox on a pallet.
UPDATE products SET category_id = 'ecus-electronics', shipping = 'PARCEL'
WHERE id = 'jaguar-f-pace-automatic-gear-selector-module-3-0l-366646597799' AND category_id = 'gearboxes';
UPDATE products SET category_id = 'body-panels' WHERE id = 'mercedes-benz-e-class-rear-right-bumper-mount-brac-366091277742' AND category_id = 'engine-mounts';
UPDATE products SET category_id = 'drivetrain' WHERE id = 'range-rover-evoque-drive-shaft-mount-gj324h473ac-2-366248422988' AND category_id = 'engine-mounts';
UPDATE products SET category_id = 'engine-parts' WHERE id = 'nissan-navara-engine-turbo-connector-897192-2010-2-366248464907' AND category_id = 'fuel-injection';
UPDATE products SET category_id = 'engine-parts' WHERE id = 'nissan-navara-automatic-gearbox-flywheel-2-3l-dies-366175422791' AND category_id = 'drivetrain';
