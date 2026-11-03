-- The packed weight of a part, for FedEx's delivery prices. Null uses the category's typical box (ParcelSizes).
ALTER TABLE products ADD COLUMN weight_kg numeric(5,2) CHECK (weight_kg > 0);
