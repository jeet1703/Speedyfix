-- The listing description staff write in the admin, as HTML (like an eBay listing description). It's cleaned of
-- scripts before shoppers see it. products.description keeps eBay's short plain-text summary.
ALTER TABLE products ADD COLUMN description_html text;
