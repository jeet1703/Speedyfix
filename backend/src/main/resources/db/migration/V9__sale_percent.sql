-- A sale on a listing: the percentage taken off its price. Null means no sale. The shop shows the price struck
-- through next to the sale price, and charges the sale price.
ALTER TABLE products ADD COLUMN sale_percent smallint CHECK (sale_percent BETWEEN 1 AND 90);
