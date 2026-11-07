-- More listings in the wrong category, found by reading every listing's title against its category (7 November 2026):
-- A/C pumps under Coolant Components, gearbox mounts under Differentials and Axles, rear doors under Interior, a gear
-- selector under Gearboxes, and turbo pipes and hoses under Turbochargers (most such pipes are already in Engine
-- Parts). Each move names the listing and the category it's leaving, so it's a no-op if staff have already moved it.
-- updated_at isn't touched: the eBay sync compares it with ebay_synced_at.
UPDATE products SET category_id = 'ac-compressors' WHERE id = 'land-rover-discovery-sport-ac-air-conditioning-pum-366143154653' AND category_id = 'cooling-ac';
UPDATE products SET category_id = 'ac-compressors' WHERE id = 'land-rover-range-rover-evoque-ac-air-conditioning-366078241530' AND category_id = 'cooling-ac';
UPDATE products SET category_id = 'engine-mounts' WHERE id = 'volvo-v40-v60-v70-s60-s80-rear-gearbox-mount-2-0l-366078112549' AND category_id = 'drivetrain';
UPDATE products SET category_id = 'engine-mounts' WHERE id = 'volvo-v70-v60-v40-s80-s60-rear-gearbox-mount-diese-366078212396' AND category_id = 'drivetrain';
UPDATE products SET category_id = 'manifolds' WHERE id = 'suzuki-vitara-intake-mani-fold-1311061ma0-2015-202-366248342199' AND category_id = 'engine-parts';
UPDATE products SET category_id = 'doors' WHERE id = 'jaguar-f-pace-rear-left-complete-door-assembly-sto-366658474616' AND category_id = 'interior';
UPDATE products SET category_id = 'doors' WHERE id = 'jaguar-f-pace-rear-right-complete-door-assembly-st-366658469528' AND category_id = 'interior';
UPDATE products SET category_id = 'engine-parts' WHERE id = 'ford-ranger-engine-turbocharger-oil-feed-pipe-2-0l-366147328917' AND category_id = 'turbochargers';
UPDATE products SET category_id = 'engine-parts' WHERE id = 'jaguar-xj-x351-3-0-sdv6-turbocharger-intercooler-d-366731075118' AND category_id = 'turbochargers';
UPDATE products SET category_id = 'engine-parts' WHERE id = 'land-rover-discovery-sport-204dtd-turbo-oil-return-366478712532' AND category_id = 'turbochargers';
UPDATE products SET category_id = 'engine-parts' WHERE id = 'land-rover-discovery-sport-intercooler-hose-pipe-g-366245709066' AND category_id = 'turbochargers';
UPDATE products SET category_id = 'engine-parts' WHERE id = 'nissan-navara-engine-intercooler-hose-pipe-2-3l-di-366164484866' AND category_id = 'turbochargers';
UPDATE products SET category_id = 'engine-parts' WHERE id = 'nissan-navara-np300-turbo-air-intake-pipe-2-3l-die-366162445222' AND category_id = 'turbochargers';
UPDATE products SET category_id = 'electrics-sensors' WHERE id = 'volvo-v70-v60-v40-s80-turbocharger-vacuum-valve-re-366078321300' AND category_id = 'turbochargers';
-- A gear selector is an electronic module sent by parcel, not a gearbox on a pallet.
UPDATE products SET category_id = 'ecus-electronics', shipping = 'PARCEL'
WHERE id = 'jaguar-xe-f-pace1-automatic-gear-selector-hx7314c5-366427139849' AND category_id = 'gearboxes';
-- V26 moved this flywheel to Engine Parts, but every other flywheel is under Differentials and Axles (eBay's
-- "Flywheels & Flexplates" maps there), so it goes back with them.
UPDATE products SET category_id = 'drivetrain' WHERE id = 'nissan-navara-automatic-gearbox-flywheel-2-3l-dies-366175422791' AND category_id = 'engine-parts';
