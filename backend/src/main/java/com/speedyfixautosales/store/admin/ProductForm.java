package com.speedyfixautosales.store.admin;

import com.speedyfixautosales.store.catalog.Condition;
import com.speedyfixautosales.store.catalog.Product;
import com.speedyfixautosales.store.catalog.ProductPhotoStore;
import com.speedyfixautosales.store.catalog.ProductStatus;
import com.speedyfixautosales.store.catalog.Shipping;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;

/**
 * The Create listing / Edit listing form. A draft can be saved without a price or photo; AdminCatalogService checks
 * what publishing needs. keepPhotoIds lists the part's current photos to keep (null keeps them all); removePhotoIds
 * lists photos from its eBay listing to take off (null or empty keeps them all).
 * descriptionHtml is the listing description as HTML; null leaves it as it is, blank removes it. salePercent is the
 * percentage off the price during a sale; null for none. weightKg is the packed part's weight for FedEx's
 * delivery prices; null uses the category's typical weight.
 */
public record ProductForm(
        @NotBlank(message = "Enter the title shoppers will see.") @Size(max = 200, message = "Keep the title under 200 characters.") String name,
        @NotBlank(message = "Choose a category.") String categoryId,
        @NotNull(message = "Choose a condition.") Condition condition,
        @Pattern(regexp = "^$|^[ABC]$", message = "Grade must be A, B or C.") String grade,
        @DecimalMin(value = "0.01", message = "Enter a price above £0.") @DecimalMax(value = "99999.99", message = "Enter a price under £100,000.")
        @Digits(integer = 5, fraction = 2, message = "Enter the price in pounds and pence, like 149.00.") BigDecimal price,
        @Size(max = 40, message = "Keep the part number under 40 characters.") String partNumber,
        @Size(max = 20, message = "Keep the engine code under 20 characters.") String engineCode,
        @Size(max = 60, message = "Keep the brand under 60 characters.") String brand,
        @Size(max = 60, message = "Keep the sub type under 60 characters.") String subType,
        @Size(max = AdminCatalogService.MAX_REFINEMENTS, message = "Add up to 5 featured refinements.")
        List<@Size(max = 60, message = "Keep each featured refinement under 60 characters.") String> refinements,
        @NotNull(message = "Choose how the part ships.") Shipping shipping,
        @NotNull(message = "Enter the stock quantity.") @Min(value = 0, message = "Stock can’t be below 0.") @Max(value = 999, message = "Stock must be 999 or fewer.") Integer stock,
        boolean bestSeller,
        @Size(max = MAX_VEHICLES, message = "A part can list up to " + MAX_VEHICLES + " vehicles.") List<String> vehicleIds,
        @Size(max = ProductPhotoStore.MAX_PER_PART) List<String> keepPhotoIds,
        @Size(max = ProductPhotoStore.MAX_PER_PART) List<String> removePhotoIds,
        @NotNull ProductStatus status,
        @Size(max = AdminCatalogService.MAX_DESCRIPTION_HTML, message = "Keep the description under 100,000 characters.") String descriptionHtml,
        @Min(value = 1, message = "A sale takes at least 1% off. Leave it empty for no sale.")
        @Max(value = Product.MAX_SALE_PERCENT, message = "A sale can take up to 90% off.") Integer salePercent,
        @DecimalMin(value = "0.1", message = "Enter a weight of at least 0.1 kg, or leave it empty.")
        @DecimalMax(value = "999", message = "Enter a weight under 1,000 kg.")
        @Digits(integer = 3, fraction = 2, message = "Enter the weight in kilograms, like 7.5.") BigDecimal weightKg) {

    /** Vehicles on one part: a common engine fits many models and years. */
    public static final int MAX_VEHICLES = 500;
}
