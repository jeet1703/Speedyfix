package com.speedyfixautosales.store.admin;

import com.speedyfixautosales.store.catalog.Condition;
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
 * The Add part / Edit part form. A draft can be saved without a price or photo; AdminCatalogService checks
 * what publishing needs. keepPhotoIds lists the part's current photos to keep (null keeps them all).
 */
public record ProductForm(
        @NotBlank(message = "Enter the title shoppers will see.") @Size(max = 200, message = "Keep the title under 200 characters.") String name,
        @NotBlank(message = "Choose a category.") String categoryId,
        @NotNull(message = "Choose a condition.") Condition condition,
        @Pattern(regexp = "^$|^[ABC]$", message = "Grade must be A, B or C.") String grade,
        @DecimalMin(value = "0.01", message = "Enter a price above £0.") @DecimalMax(value = "99999.99", message = "Enter a price under £100,000.")
        @Digits(integer = 5, fraction = 2, message = "Enter the price in pounds and pence, like 149.00.") BigDecimal price,
        @Size(max = 40, message = "Keep the OE number under 40 characters.") String oeNumber,
        @Size(max = AdminCatalogService.MAX_CROSS_REFERENCES, message = "Add up to 5 cross-reference numbers.")
        List<@Size(max = 40, message = "Keep each cross-reference under 40 characters.") String> crossReferences,
        @Size(max = 20, message = "Keep the engine code under 20 characters.") String engineCode,
        @NotNull(message = "Choose how the part ships.") Shipping shipping,
        @NotNull(message = "Enter the stock quantity.") @Min(value = 0, message = "Stock can’t be below 0.") @Max(value = 999, message = "Stock must be 999 or fewer.") Integer stock,
        boolean bestSeller,
        @Size(max = 50) List<String> vehicleIds,
        @Size(max = 10) List<String> keepPhotoIds,
        @NotNull ProductStatus status) {
}
