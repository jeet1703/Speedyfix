-- What an email needs that the order doesn't hold: for a payment staff must check, what PayPal reported.
ALTER TABLE email_outbox ADD COLUMN details varchar(500);
