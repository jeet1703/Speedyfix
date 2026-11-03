-- Listing descriptions copied from eBay say "Trusted eBay seller" and carry the template's "EBAY CAR PARTS TEMPLATE"
-- comment. The shop doesn't name eBay to its customers, so both go (EbayImportService cleans new imports the same way).
UPDATE products
SET description_html = regexp_replace(
        regexp_replace(description_html, '<!--[^>]*ebay[^>]*-->', '', 'gi'),
        'trusted\s+ebay\s+seller', 'Trusted seller', 'gi')
WHERE description_html ~* 'ebay';
