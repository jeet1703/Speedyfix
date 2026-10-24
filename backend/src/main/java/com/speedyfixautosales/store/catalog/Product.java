package com.speedyfixautosales.store.catalog;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * A part in the catalogue. Prices are in GBP and include VAT.
 * fitsVehicleIds lists the vehicles staff have confirmed the part fits. crossReferences are other OE numbers
 * for the same part. Only LIVE parts are shown to shoppers; a draft may not have a price or photo yet.
 */
public record Product(
        String id,
        String name,
        String categoryId,
        Condition condition,
        String grade,
        BigDecimal price,
        String oeNumber,
        List<String> crossReferences,
        String engineCode,
        String imageUrl,
        List<String> fitsVehicleIds,
        Shipping shipping,
        int stock,
        boolean bestSeller,
        LocalDate addedOn,
        ProductStatus status) {

    public Product {
        fitsVehicleIds = fitsVehicleIds == null ? List.of() : List.copyOf(fitsVehicleIds);
        crossReferences = crossReferences == null ? List.of() : List.copyOf(crossReferences);
    }

    public Product withStock(int newStock) {
        return new Product(id, name, categoryId, condition, grade, price, oeNumber, crossReferences, engineCode, imageUrl, fitsVehicleIds,
                shipping, newStock, bestSeller, addedOn, status);
    }

    public boolean live() {
        return status == ProductStatus.LIVE;
    }

    /** A photo of this actual unit. The Unsplash sample photos the demo catalogue starts with don't count. */
    public boolean hasPhoto() {
        return imageUrl != null && !Images.isSample(imageUrl);
    }

    /** The reference shoppers search by: the OE number when known, otherwise the engine code. */
    public String reference() {
        if (oeNumber != null && !oeNumber.isBlank()) {
            return "OE " + oeNumber;
        }
        if (engineCode != null && !engineCode.isBlank()) {
            return "Engine code " + engineCode;
        }
        return null;
    }
}
