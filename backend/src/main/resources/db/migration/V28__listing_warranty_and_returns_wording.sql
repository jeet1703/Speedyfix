-- Listing descriptions carry the old template's "Warranty:" (28-day) and "Returns:" (30% restocking fee) paragraphs,
-- which contradict the shop's returns policy and UK consumer law. Both are replaced with the shop's current wording
-- (EbayImportService does the same for new imports, and the admin's listing template already says this).
-- Every quantifier is non-greedy so that each match stops at its own </p>.
UPDATE products
SET description_html = regexp_replace(
        regexp_replace(description_html,
            '<p[^>]*?>\s*?<strong[^>]*?>\s*?Warranty:??\s*?</strong>.*?</p>',
            '<p><strong>Warranty:</strong><br>Aftermarket and refurbished parts come with a <strong>3 months replacement warranty</strong> from the date of delivery. The warranty covers the part only and does not include labour or fitting costs.</p>',
            'gi'),
        '<p[^>]*?>\s*?<strong[^>]*?>\s*?Returns:??\s*?</strong>.*?</p>',
        '<p><strong>Returns:</strong><br>Changed your mind? Tell us within <strong>14 days of delivery</strong> and return the part at your own cost for a refund. If a part is faulty or not as described, you are covered by UK consumer law. See our Delivery and Returns page for the full policy, including the terms for trade buyers.</p>',
        'gi')
WHERE description_html ~* '<strong[^>]*>\s*(Warranty|Returns):?\s*</strong>';
