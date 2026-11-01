package com.speedyfixautosales.store.catalog;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

/**
 * A part in the catalogue. Prices are in GBP and include VAT.
 * fitsVehicleIds lists the vehicles staff have confirmed the part fits. brand, subType and refinements drive the
 * shop's eBay-style filters (Brand, Sub type, Featured refinements). Only LIVE parts are shown to shoppers; a draft
 * may not have a price or photo yet. price is the listing's own price; salePercent (1 to 90, null for none) takes a
 * percentage off it, and sellingPrice() is what shoppers pay.
 */
public record Product(
        String id,
        String name,
        String categoryId,
        Condition condition,
        String grade,
        BigDecimal price,
        String partNumber,
        String engineCode,
        String brand,
        String subType,
        List<String> refinements,
        String imageUrl,
        List<String> fitsVehicleIds,
        Shipping shipping,
        int stock,
        boolean bestSeller,
        LocalDate addedOn,
        ProductStatus status,
        Integer salePercent) {

    public static final int MAX_SALE_PERCENT = 90;

    public Product {
        fitsVehicleIds = fitsVehicleIds == null ? List.of() : List.copyOf(fitsVehicleIds);
        refinements = refinements == null ? List.of() : List.copyOf(refinements);
        salePercent = salePercent == null || salePercent <= 0 ? null : Math.min(salePercent, MAX_SALE_PERCENT);
    }

    /** A part with no sale. */
    public Product(String id, String name, String categoryId, Condition condition, String grade, BigDecimal price,
                   String partNumber, String engineCode, String brand, String subType, List<String> refinements,
                   String imageUrl, List<String> fitsVehicleIds, Shipping shipping, int stock, boolean bestSeller,
                   LocalDate addedOn, ProductStatus status) {
        this(id, name, categoryId, condition, grade, price, partNumber, engineCode, brand, subType, refinements, imageUrl,
                fitsVehicleIds, shipping, stock, bestSeller, addedOn, status, null);
    }

    public Product withStock(int newStock) {
        return new Product(id, name, categoryId, condition, grade, price, partNumber, engineCode, brand, subType, refinements,
                imageUrl, fitsVehicleIds, shipping, newStock, bestSeller, addedOn, status, salePercent);
    }

    public boolean onSale() {
        return salePercent != null && price != null;
    }

    /** What shoppers pay: the price less the sale, to the penny (half a penny rounds up). Null while a draft has no price. */
    public BigDecimal sellingPrice() {
        if (!onSale()) {
            return price;
        }
        return price.multiply(BigDecimal.valueOf(100 - salePercent)).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }

    public boolean live() {
        return status == ProductStatus.LIVE;
    }

    /** A photo of this actual unit. The Unsplash sample photos the demo catalogue starts with don't count. */
    public boolean hasPhoto() {
        return imageUrl != null && !Images.isSample(imageUrl);
    }

    /** The reference shoppers search by: the part number when known, otherwise the engine code. */
    public String reference() {
        if (partNumber != null && !partNumber.isBlank()) {
            return "Part no. " + partNumber;
        }
        if (engineCode != null && !engineCode.isBlank()) {
            return "Engine code " + engineCode;
        }
        return null;
    }
}
