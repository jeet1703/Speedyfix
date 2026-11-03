-- "Forgot password": one reset code per email account per day. email_key is the address in lower case; code_hash is
-- the SHA-256 of the emailed 6-digit code, cleared once it's used. requested_at stays, for the once-a-day limit.
CREATE TABLE password_resets (
    email_key     varchar(200) PRIMARY KEY,
    code_hash     varchar(64),
    expires_at    timestamptz  NOT NULL,
    attempts      integer      NOT NULL DEFAULT 0,
    requested_at  timestamptz  NOT NULL
);
