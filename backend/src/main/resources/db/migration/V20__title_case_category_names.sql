-- Category names in title case ("Rebuild Kits", "Air Intake and Throttle Bodies"), as the shop writes headings.
-- Only renamed where staff haven't changed the name themselves.
UPDATE categories SET name = 'Air Intake and Throttle Bodies' WHERE id = 'air-intake' AND name = 'Air intake and throttle bodies';
UPDATE categories SET name = 'Body Panels' WHERE id = 'body-panels' AND name = 'Body panels';
UPDATE categories SET name = 'Cylinder Heads' WHERE id = 'cylinder-heads' AND name = 'Cylinder heads';
UPDATE categories SET name = 'Drivetrain and Axles' WHERE id = 'drivetrain' AND name = 'Drivetrain and axles';
UPDATE categories SET name = 'ECUs and Electronics' WHERE id = 'ecus-electronics' AND name = 'ECUs and electronics';
UPDATE categories SET name = 'Exhaust and Emissions' WHERE id = 'egr-emissions' AND name = 'Exhaust and emissions';
UPDATE categories SET name = 'Electrics and Sensors' WHERE id = 'electrics-sensors' AND name = 'Electrics and sensors';
UPDATE categories SET name = 'Engine Mounts' WHERE id = 'engine-mounts' AND name = 'Engine mounts';
UPDATE categories SET name = 'Engine Parts' WHERE id = 'engine-parts' AND name = 'Engine parts';
UPDATE categories SET name = 'Instruments and Infotainment' WHERE id = 'instruments-infotainment' AND name = 'Instruments and infotainment';
UPDATE categories SET name = 'Lights and Mirrors' WHERE id = 'lights-mirrors' AND name = 'Lights and mirrors';
UPDATE categories SET name = 'Other Parts' WHERE id = 'other-parts' AND name = 'Other parts';
UPDATE categories SET name = 'Rebuild Kits' WHERE id = 'rebuild-kits' AND name = 'Rebuild kits';
UPDATE categories SET name = 'Starters and Alternators' WHERE id = 'starters-alternators' AND name = 'Starters and alternators';
UPDATE categories SET name = 'Steering and Suspension' WHERE id = 'steering-suspension' AND name = 'Steering and suspension';
