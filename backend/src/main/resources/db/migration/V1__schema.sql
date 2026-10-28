-- SpeedyFix Auto Sales: first schema. Flyway runs this once on any new database (local Docker or the live server).
-- Money is GBP with 2 decimal places, VAT included. Times are UTC (timestamptz).

-- Catalogue -------------------------------------------------------------------------------------------------

CREATE TABLE categories (
    id          varchar(60)  PRIMARY KEY,
    name        varchar(100) NOT NULL,
    popular     boolean      NOT NULL DEFAULT false,
    sort_order  integer      NOT NULL DEFAULT 0,
    image_key   varchar(300)
);

-- eBay category number -> shop category, filled from the reviewed category map (import step C4).
CREATE TABLE ebay_category_map (
    ebay_category_id  varchar(20)  PRIMARY KEY,
    ebay_name         varchar(200) NOT NULL,
    category_id       varchar(60)  REFERENCES categories (id)
);

CREATE TABLE products (
    id              varchar(80)   PRIMARY KEY,
    name            varchar(200)  NOT NULL,
    category_id     varchar(60)   NOT NULL REFERENCES categories (id),
    condition       varchar(20)   NOT NULL CHECK (condition IN ('USED', 'REMANUFACTURED', 'NEW')),
    grade           char(1)       CHECK (grade IN ('A', 'B', 'C')),
    price           numeric(10,2) CHECK (price > 0),
    part_number     varchar(40),
    engine_code     varchar(20),
    brand           varchar(60),
    sub_type        varchar(60),
    description     text,
    shipping        varchar(10)   NOT NULL DEFAULT 'PARCEL' CHECK (shipping IN ('PARCEL', 'PALLET')),
    stock           integer       NOT NULL DEFAULT 0 CHECK (stock >= 0),
    best_seller     boolean       NOT NULL DEFAULT false,
    sold_count      integer       NOT NULL DEFAULT 0,
    status          varchar(10)   NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT', 'LIVE', 'ENDED')),
    added_on        date          NOT NULL DEFAULT current_date,
    ebay_item_id    varchar(20)   UNIQUE,
    ebay_synced_at  timestamptz,
    created_at      timestamptz   NOT NULL DEFAULT now(),
    updated_at      timestamptz   NOT NULL DEFAULT now()
);
CREATE INDEX products_status_category ON products (status, category_id);
CREATE INDEX products_part_number ON products (lower(part_number));
CREATE INDEX products_engine_code ON products (lower(engine_code));
CREATE INDEX products_brand ON products (lower(brand));

-- Featured refinements (the shop's eBay-style filter), up to 5 per part.
CREATE TABLE product_refinements (
    product_id  varchar(80) NOT NULL REFERENCES products (id) ON DELETE CASCADE,
    value       varchar(60) NOT NULL,
    PRIMARY KEY (product_id, value)
);

-- Every eBay item specific, kept as-is (Brand, Manufacturer Part Number, Type...).
CREATE TABLE product_aspects (
    product_id  varchar(80)  NOT NULL REFERENCES products (id) ON DELETE CASCADE,
    name        varchar(100) NOT NULL,
    value       varchar(500) NOT NULL,
    PRIMARY KEY (product_id, name, value)
);

-- Photos live in Cloudflare R2; r2_key is the object key ("listings/{productId}/{position}-800.webp" and so on).
CREATE TABLE product_photos (
    id          varchar(40)  PRIMARY KEY,
    product_id  varchar(80)  NOT NULL REFERENCES products (id) ON DELETE CASCADE,
    position    smallint     NOT NULL,
    r2_key      varchar(300) NOT NULL,
    width       integer,
    height      integer,
    source      varchar(10)  NOT NULL DEFAULT 'UPLOAD' CHECK (source IN ('UPLOAD', 'EBAY', 'SAMPLE')),
    source_url  varchar(1000),
    created_at  timestamptz  NOT NULL DEFAULT now(),
    UNIQUE (product_id, position)
);

-- Vehicles and fitment -------------------------------------------------------------------------------------

CREATE TABLE vehicles (
    id           varchar(80)  PRIMARY KEY,
    make         varchar(60)  NOT NULL,
    model        varchar(100) NOT NULL,
    variant      varchar(100),
    year_from    smallint,
    year_to      smallint,
    engine       varchar(100),
    engine_code  varchar(20),
    fuel         varchar(20)
);
CREATE INDEX vehicles_make_model ON vehicles (lower(make), lower(model));
CREATE INDEX vehicles_engine_code ON vehicles (lower(engine_code));

-- vehicle_id isn't tied to the vehicles table yet: vehicles still come from the plate lookup in code.
CREATE TABLE product_fitment (
    product_id  varchar(80) NOT NULL REFERENCES products (id) ON DELETE CASCADE,
    vehicle_id  varchar(80) NOT NULL,
    source      varchar(30) NOT NULL CHECK (source IN ('EBAY_COMPATIBILITY', 'PART_NUMBER', 'ENGINE_CODE', 'PLATE_CHECK', 'MANUAL')),
    confidence  varchar(10) NOT NULL CHECK (confidence IN ('HIGH', 'MEDIUM')),
    PRIMARY KEY (product_id, vehicle_id)
);
CREATE INDEX product_fitment_vehicle ON product_fitment (vehicle_id);

-- Plate lookups made by shoppers. The plate itself is never stored.
CREATE TABLE lookup_log (
    id          bigserial   PRIMARY KEY,
    looked_up   timestamptz NOT NULL DEFAULT now(),
    vehicle_id  varchar(80),
    matched     boolean     NOT NULL
);
CREATE INDEX lookup_log_time ON lookup_log (looked_up);

-- Baskets and orders ----------------------------------------------------------------------------------------

CREATE TABLE baskets (
    id          varchar(40) PRIMARY KEY,
    updated_at  timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE basket_items (
    basket_id   varchar(40) NOT NULL REFERENCES baskets (id) ON DELETE CASCADE,
    product_id  varchar(80) NOT NULL REFERENCES products (id),
    quantity    integer     NOT NULL CHECK (quantity > 0),
    PRIMARY KEY (basket_id, product_id)
);

CREATE TABLE orders (
    reference                 varchar(20)   PRIMARY KEY,
    placed_at                 timestamptz   NOT NULL DEFAULT now(),
    status                    varchar(20)   NOT NULL
        CHECK (status IN ('AWAITING_PAYMENT', 'AWAITING_DISPATCH', 'DISPATCHED', 'CANCELLED', 'EXPIRED', 'REFUNDED')),
    customer_id               varchar(60),
    customer_name             varchar(100)  NOT NULL,
    customer_email            varchar(200)  NOT NULL,
    customer_phone            varchar(20)   NOT NULL,
    address_line1             varchar(120)  NOT NULL,
    address_line2             varchar(120),
    address_town              varchar(80)   NOT NULL,
    address_postcode          varchar(10)   NOT NULL,
    notes                     varchar(500),
    delivery_method           varchar(10)   NOT NULL,
    delivery_label            varchar(100),
    delivery_price            numeric(10,2) NOT NULL,
    subtotal                  numeric(10,2) NOT NULL,
    total                     numeric(10,2) NOT NULL,
    vat_included              numeric(10,2) NOT NULL,
    stripe_payment_intent_id  varchar(60)   UNIQUE,
    paid_at                   timestamptz,
    stock_held_until          timestamptz,
    dispatch_courier          varchar(60),
    dispatch_tracking         varchar(60),
    dispatched_at             timestamptz,
    refunded_amount           numeric(10,2) NOT NULL DEFAULT 0
);
CREATE INDEX orders_status ON orders (status, placed_at DESC);

-- Each line keeps a copy of the name, reference and price at the time of the order.
CREATE TABLE order_items (
    order_reference  varchar(20)   NOT NULL REFERENCES orders (reference) ON DELETE CASCADE,
    product_id       varchar(80)   NOT NULL,
    name             varchar(200)  NOT NULL,
    reference        varchar(80),
    image_url        varchar(500),
    unit_price       numeric(10,2) NOT NULL,
    quantity         integer       NOT NULL CHECK (quantity > 0),
    line_total       numeric(10,2) NOT NULL,
    PRIMARY KEY (order_reference, product_id)
);

-- Shopper accounts (Sign in with Google) -------------------------------------------------------------------

CREATE TABLE customers (
    id              varchar(60)  PRIMARY KEY,  -- Google's account id ("sub")
    email           varchar(200) NOT NULL,
    name            varchar(200) NOT NULL,
    picture_url     varchar(500),
    joined_at       timestamptz  NOT NULL DEFAULT now(),
    last_sign_in_at timestamptz  NOT NULL DEFAULT now(),
    sign_ins        integer      NOT NULL DEFAULT 1
);

CREATE TABLE customer_searches (
    customer_id  varchar(60)  NOT NULL REFERENCES customers (id) ON DELETE CASCADE,
    query        varchar(100) NOT NULL,
    searched_at  timestamptz  NOT NULL DEFAULT now(),
    PRIMARY KEY (customer_id, query)
);

-- Part requests ---------------------------------------------------------------------------------------------

CREATE TABLE part_requests (
    reference            varchar(20)  PRIMARY KEY,
    received_at          timestamptz  NOT NULL DEFAULT now(),
    masked_registration  varchar(12)  NOT NULL,
    vehicle              varchar(200),
    part                 varchar(200) NOT NULL,
    notes                varchar(1000),
    name                 varchar(100) NOT NULL,
    phone                varchar(20),
    email                varchar(200),
    reply_by             varchar(10)  NOT NULL CHECK (reply_by IN ('WHATSAPP', 'PHONE', 'EMAIL')),
    status               varchar(10)  NOT NULL DEFAULT 'NEW' CHECK (status IN ('NEW', 'QUOTED', 'CLOSED'))
);

-- Photos in the private R2 bucket, shown to staff only.
CREATE TABLE part_request_photos (
    reference     varchar(20)  NOT NULL REFERENCES part_requests (reference) ON DELETE CASCADE,
    position      smallint     NOT NULL,
    r2_key        varchar(300) NOT NULL,
    content_type  varchar(50)  NOT NULL,
    PRIMARY KEY (reference, position)
);

-- Admin -----------------------------------------------------------------------------------------------------

-- One row of shop-wide settings (lookup cost, monthly cap, buying team email).
CREATE TABLE settings (
    id                 smallint      PRIMARY KEY DEFAULT 1 CHECK (id = 1),
    lookup_cost        numeric(8,4)  NOT NULL DEFAULT 0,
    lookup_budget      numeric(10,2) NOT NULL DEFAULT 0,
    buying_team_email  varchar(200)  NOT NULL DEFAULT ''
);
INSERT INTO settings (id) VALUES (1);

-- One row per eBay CSV import, with its summary and any problem rows.
CREATE TABLE import_runs (
    id           bigserial    PRIMARY KEY,
    file_name    varchar(300) NOT NULL,
    started_at   timestamptz  NOT NULL DEFAULT now(),
    finished_at  timestamptz,
    dry_run      boolean      NOT NULL DEFAULT false,
    created      integer      NOT NULL DEFAULT 0,
    updated      integer      NOT NULL DEFAULT 0,
    skipped      integer      NOT NULL DEFAULT 0,
    photos       integer      NOT NULL DEFAULT 0,
    errors       jsonb        NOT NULL DEFAULT '[]'
);
