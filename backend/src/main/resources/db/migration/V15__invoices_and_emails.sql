-- VAT invoices: one per paid order. HMRC wants invoice numbers unique and in sequence, so the number is the next
-- after the highest so far (no database sequence, which can skip numbers), starting at 1001.
CREATE TABLE invoices (
    number           bigint       PRIMARY KEY,
    order_reference  varchar(20)  NOT NULL UNIQUE REFERENCES orders (reference),
    issued_at        timestamptz  NOT NULL
);

-- Order emails (the buyer's invoice, the staff's new-order note) waiting to be sent, sent, or given up on.
-- One row per order and kind, so the same email can't go twice (a PayPal capture answered twice, say).
CREATE TABLE email_outbox (
    id               bigserial    PRIMARY KEY,
    order_reference  varchar(20)  NOT NULL REFERENCES orders (reference) ON DELETE CASCADE,
    kind             varchar(30)  NOT NULL,
    recipient        varchar(200) NOT NULL,
    status           varchar(10)  NOT NULL CHECK (status IN ('PENDING', 'SENT', 'FAILED')),
    attempts         integer      NOT NULL DEFAULT 0,
    last_error       varchar(300),
    created_at       timestamptz  NOT NULL DEFAULT now(),
    next_attempt_at  timestamptz  NOT NULL DEFAULT now(),
    sent_at          timestamptz,
    UNIQUE (order_reference, kind)
);

CREATE INDEX email_outbox_due ON email_outbox (next_attempt_at) WHERE status = 'PENDING';
