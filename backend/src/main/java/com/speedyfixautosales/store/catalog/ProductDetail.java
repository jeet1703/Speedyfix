package com.speedyfixautosales.store.catalog;

import java.math.BigDecimal;
import java.util.List;

/**
 * Everything the product page shows. fitsVehicles lists vehicles staff have confirmed;
 * an empty list means fitment is checked by OE number before dispatch.
 */
public record ProductDetail(
        ProductView product,
        String oeNumber,
        String engineCode,
        BigDecimal priceExVat,
        String description,
        List<String> fitsVehicles,
        List<ProductView> related) {
}
