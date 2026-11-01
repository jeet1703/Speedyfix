package com.speedyfixautosales.store.ebay;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Where the importer gets each listing's photos and item specifics: eBay's Browse API (or a stand-in in tests). */
public interface EbayItemSource {

    /**
     * A listing's details. imageUrls are in eBay's order, the main photo first. description is eBay's short plain-text
     * summary; descriptionHtml the listing's full description as the seller wrote it (HTML), or null.
     */
    record ItemDetails(String itemNumber, List<String> imageUrls, List<Aspect> aspects, String brand, String mpn,
                       String description, String descriptionHtml) {
        public ItemDetails(String itemNumber, List<String> imageUrls, List<Aspect> aspects, String brand, String mpn,
                           String description) {
            this(itemNumber, imageUrls, aspects, brand, mpn, description, null);
        }
    }

    record Aspect(String name, String value) {
    }

    /** One of a seller's live fixed-price listings, as eBay's search lists it. */
    record Listing(String itemNumber, String title, BigDecimal price, String condition, String categoryId,
                   String categoryName, LocalDate listedOn) {
    }

    /** False until EBAY_CLIENT_ID and EBAY_CLIENT_SECRET are set. */
    boolean enabled();

    /** Details for up to 20 item numbers. Listings eBay can't return (ended, hidden while out of stock) are left out. */
    Map<String, ItemDetails> fetch(List<String> itemNumbers);

    /** The eBay user who sells this listing, if eBay returns it. */
    default Optional<String> sellerOf(String itemNumber) throws IOException {
        return Optional.empty();
    }

    /**
     * Every live fixed-price listing of this seller. Throws if any page fails, so a partial list is never mistaken
     * for listings having ended.
     */
    default List<Listing> sellerListings(String seller) throws IOException {
        throw new IOException("Listing a seller's items isn't supported here.");
    }
}
