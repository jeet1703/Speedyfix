package com.speedyfix.store.catalog;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * A part in the catalogue. Prices are in GBP and include VAT.
 * fitsVehicleIds lists the vehicles staff have confirmed the part fits.
 */
public record Product(
        String id,
        String name,
        String categoryId,
        Condition condition,
        String grade,
        BigDecimal price,
        String oeNumber,
        String engineCode,
        String imageUrl,
        List<String> fitsVehicleIds,
        Shipping shipping,
        int stock,
        boolean bestSeller,
        LocalDate addedOn) {

    public Product {
        fitsVehicleIds = fitsVehicleIds == null ? List.of() : List.copyOf(fitsVehicleIds);
    }

    public Product withStock(int newStock) {
        return new Product(id, name, categoryId, condition, grade, price, oeNumber, engineCode, imageUrl, fitsVehicleIds,
                shipping, newStock, bestSeller, addedOn);
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
