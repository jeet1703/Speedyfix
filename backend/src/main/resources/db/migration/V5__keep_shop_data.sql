-- Customers, orders, baskets, part requests, settings and plate lookups move from memory to the database.

-- Part request photos are kept in the database (shown to staff only), like admin uploads in V4.
ALTER TABLE part_request_photos
    ALTER COLUMN r2_key DROP NOT NULL,
    ADD COLUMN filename varchar(200),
    ADD COLUMN data     bytea;

-- The form allows 2,000 characters of extra detail.
ALTER TABLE part_requests ALTER COLUMN notes TYPE varchar(2000);

-- References run PR-1001, PR-1002, …
CREATE SEQUENCE part_request_number START WITH 1001;

-- Until staff save the Settings page, the starting values from the server's settings apply.
ALTER TABLE settings ADD COLUMN saved boolean NOT NULL DEFAULT false;

-- Order lines keep the order they were in the basket.
ALTER TABLE order_items ADD COLUMN position smallint NOT NULL DEFAULT 0;

-- Signed-in shoppers see their orders.
CREATE INDEX orders_customer ON orders (customer_id, placed_at DESC);
CREATE INDEX orders_email ON orders (lower(customer_email), placed_at DESC);

-- Basket lines keep the order they were added in.
ALTER TABLE basket_items ADD COLUMN added_at timestamptz NOT NULL DEFAULT clock_timestamp();
CREATE INDEX baskets_updated ON baskets (updated_at);
