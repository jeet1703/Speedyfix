-- Accessories goes back on the homepage tiles (V24 took it off), as the fourth after Complete Engines, Gearboxes
-- and Turbochargers: it sorts after them.
UPDATE categories SET popular = true WHERE id = 'accessories';
