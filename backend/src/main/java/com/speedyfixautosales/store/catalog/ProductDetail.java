package com.speedyfixautosales.store.catalog;

import java.math.BigDecimal;
import java.util.List;

/**
 * Everything the product page shows. fitsVehicles lists vehicles staff have confirmed;
 * an empty list means fitment is checked by part number before dispatch. photos are all of the listing's photos in
 * order (the main one first); specifics are eBay's item specifics, like "Manufacturer Part Number".
 * descriptionHtml is the description staff wrote as HTML (null if none); the storefront cleans it before showing it.
 */
public record ProductDetail(
        ProductView product,
        String partNumber,
        String engineCode,
        String brand,
        String subType,
        BigDecimal priceExVat,
        String description,
        List<String> fitsVehicles,
        List<ProductView> related,
        List<Photo> photos,
        List<Specific> specifics,
        String descriptionHtml) {

    /** url is the full-size photo, thumbnailUrl a small copy for the gallery strip. */
    public record Photo(String url, String thumbnailUrl) {
    }

    public record Specific(String name, String value) {
    }
}
