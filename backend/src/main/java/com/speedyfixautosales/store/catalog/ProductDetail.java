package com.speedyfixautosales.store.catalog;

import java.math.BigDecimal;
import java.util.List;

/**
 * Everything the product page shows. fitsVehicles lists vehicles staff have confirmed;
 * an empty list means fitment is checked by part number before dispatch.
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
        List<ProductView> related) {
}
