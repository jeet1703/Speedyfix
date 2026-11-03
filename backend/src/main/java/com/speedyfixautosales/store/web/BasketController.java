package com.speedyfixautosales.store.web;

import com.speedyfixautosales.store.basket.BasketService;
import com.speedyfixautosales.store.basket.BasketView;
import com.speedyfixautosales.store.shipping.DeliveryQuotes;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.util.List;
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
    private final DeliveryQuotes quotes;

    public BasketController(BasketService baskets, DeliveryQuotes quotes) {
        this.baskets = baskets;
        this.quotes = quotes;
    }

    public record DeliveryRequest(
            @NotBlank(message = "Enter your postcode.") @Pattern(regexp = OrderController.POSTCODE, message = "Enter a full UK postcode, like SW1A 1AA.") String postcode) {
    }

    /** The delivery choices to a postcode, cheapest first; checkout shows them and sends back the chosen id. */
    public record DeliveryChoices(List<DeliveryQuotes.Option> options) {
    }

    public record AddItemRequest(@NotBlank String productId, @Min(1) @Max(20) int quantity) {
    }

    public record QuantityRequest(@Min(0) @Max(20) int quantity) {
    }

    @PostMapping
    public ResponseEntity<BasketView> create() {
        return ResponseEntity.status(HttpStatus.CREATED).body(quotes.beforeCheckout(baskets.create()));
    }

    @GetMapping("/{basketId}")
    public BasketView get(@PathVariable String basketId) {
        return quotes.beforeCheckout(baskets.get(basketId));
    }

    @PostMapping("/{basketId}/delivery-options")
    public DeliveryChoices deliveryOptions(@PathVariable String basketId, @Valid @RequestBody DeliveryRequest request) {
        return new DeliveryChoices(quotes.options(baskets.get(basketId), request.postcode()));
    }

    @PostMapping("/{basketId}/items")
    public BasketView add(@PathVariable String basketId, @Valid @RequestBody AddItemRequest request) {
        return quotes.beforeCheckout(baskets.addItem(basketId, request.productId(), request.quantity()));
    }

    @PatchMapping("/{basketId}/items/{productId}")
    public BasketView setQuantity(@PathVariable String basketId, @PathVariable String productId,
                                  @Valid @RequestBody QuantityRequest request) {
        return quotes.beforeCheckout(baskets.setQuantity(basketId, productId, request.quantity()));
    }

    @DeleteMapping("/{basketId}/items/{productId}")
    public BasketView remove(@PathVariable String basketId, @PathVariable String productId) {
        return quotes.beforeCheckout(baskets.remove(basketId, productId));
    }
}
