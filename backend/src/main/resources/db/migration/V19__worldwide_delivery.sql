-- Worldwide delivery: the delivery address's country (ISO code). Orders from before are all UK. Postcodes abroad can
-- be longer than UK ones, or blank.
ALTER TABLE orders ADD COLUMN address_country varchar(2) NOT NULL DEFAULT 'GB';
ALTER TABLE orders ALTER COLUMN address_postcode TYPE varchar(16);
ALTER TABLE orders ALTER COLUMN address_postcode DROP NOT NULL;
