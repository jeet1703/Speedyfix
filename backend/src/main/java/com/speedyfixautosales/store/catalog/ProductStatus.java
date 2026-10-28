package com.speedyfixautosales.store.catalog;

/**
 * Drafts are visible to staff only; live parts appear in the shop. Ended listings (sold out on eBay, or taken
 * down) stay in the catalogue for staff and records but aren't shown to shoppers.
 */
public enum ProductStatus {
    DRAFT,
    LIVE,
    ENDED
}
