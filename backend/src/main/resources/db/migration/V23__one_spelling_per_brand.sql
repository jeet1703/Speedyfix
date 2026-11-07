-- One spelling per brand, so the shop's brand filter lists each brand once (Brands.canonical does the same for every
-- brand saved from now on). updated_at isn't touched: the eBay sync compares it with ebay_synced_at.
UPDATE products SET brand = 'Mercedes-Benz' WHERE lower(brand) IN ('mercedes', 'mercedes benz', 'mercedes-benz') AND brand <> 'Mercedes-Benz';
UPDATE products SET brand = 'Volkswagen' WHERE lower(brand) = 'vw';
UPDATE products SET brand = 'Land Rover' WHERE lower(brand) IN ('range rover', 'landrover', 'land rover') AND brand <> 'Land Rover';
UPDATE products SET brand = 'Ford' WHERE lower(brand) IN ('fomoco', 'fomco');
UPDATE products SET brand = 'Peugeot' WHERE lower(brand) = 'peigeot';
-- All lower or all upper case ("jaguar", "DENSO"): capitalised like the rest. Short names (BMW, VW) stay as they are.
UPDATE products SET brand = initcap(brand)
WHERE length(brand) > 3 AND (brand = lower(brand) OR brand = upper(brand)) AND brand <> initcap(brand);
