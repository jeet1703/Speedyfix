-- Photos staff upload in the admin, kept in the database until they move to Cloudflare R2. Each has a
-- product_photos row (source UPLOAD, r2_key "db:<id>") and its file here. Uploads take positions below zero, so a
-- staff photo comes before any downloaded from eBay and becomes the listing's main image.
CREATE TABLE product_photo_files (
    photo_id      varchar(40)  PRIMARY KEY REFERENCES product_photos (id) ON DELETE CASCADE,
    content_type  varchar(20)  NOT NULL,
    data          bytea        NOT NULL
);
