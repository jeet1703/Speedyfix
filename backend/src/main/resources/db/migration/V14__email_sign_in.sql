-- Email sign-in: an account made with an email address and password (confirmed with a code) has a password_hash;
-- Google accounts don't. One email account per address, ignoring case.
ALTER TABLE customers ADD COLUMN password_hash varchar(200);

CREATE UNIQUE INDEX customers_email_account ON customers (lower(email)) WHERE password_hash IS NOT NULL;
