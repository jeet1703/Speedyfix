package com.speedyfixautosales.store.ebay;

import java.util.List;
import java.util.Map;

/** Where the importer gets each listing's photos and item specifics: eBay's Browse API (or a stand-in in tests). */
public interface EbayItemSource {

    /** A listing's details. imageUrls are in eBay's order, the main photo first. */
    record ItemDetails(String itemNumber, List<String> imageUrls, List<Aspect> aspects, String brand, String mpn,
                       String description) {
    }

    record Aspect(String name, String value) {
    }

    /** False until EBAY_CLIENT_ID and EBAY_CLIENT_SECRET are set. */
    boolean enabled();

    /** Details for up to 20 item numbers. Listings eBay can't return (ended, hidden while out of stock) are left out. */
    Map<String, ItemDetails> fetch(List<String> itemNumbers);
}
