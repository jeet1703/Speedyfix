-- Online payment through PayPal Checkout (PayPal account, or debit/credit card through PayPal).
-- paypal_order_id is PayPal's order for this payment; payment_capture_id the captured payment, once taken.
ALTER TABLE orders
    ADD COLUMN paypal_order_id    varchar(40) UNIQUE,
    ADD COLUMN payment_capture_id varchar(40);
