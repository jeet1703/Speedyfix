package com.speedyfix.store.web;

import com.speedyfix.store.basket.BasketService;
import com.speedyfix.store.basket.BasketView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/baskets")
public class BasketController {

    private final BasketService baskets;

    public BasketController(BasketService baskets) {
        this.baskets = baskets;
    }

    public record AddItemRequest(@NotBlank String productId, @Min(1) @Max(20) int quantity) {
    }

    public record QuantityRequest(@Min(0) @Max(20) int quantity) {
    }

    @PostMapping
    public ResponseEntity<BasketView> create() {
        return ResponseEntity.status(HttpStatus.CREATED).body(baskets.create());
    }

    @GetMapping("/{basketId}")
    public BasketView get(@PathVariable String basketId) {
        return baskets.get(basketId);
    }

    @PostMapping("/{basketId}/items")
    public BasketView add(@PathVariable String basketId, @Valid @RequestBody AddItemRequest request) {
        return baskets.addItem(basketId, request.productId(), request.quantity());
    }

    @PatchMapping("/{basketId}/items/{productId}")
    public BasketView setQuantity(@PathVariable String basketId, @PathVariable String productId,
                                  @Valid @RequestBody QuantityRequest request) {
        return baskets.setQuantity(basketId, productId, request.quantity());
    }

    @DeleteMapping("/{basketId}/items/{productId}")
    public BasketView remove(@PathVariable String basketId, @PathVariable String productId) {
        return baskets.remove(basketId, productId);
    }
}
