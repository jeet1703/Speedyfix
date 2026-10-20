package com.speedyfix.store.web;

import com.speedyfix.store.order.Order;
import com.speedyfix.store.order.OrderService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    /** Full UK postcode, any case, with or without the space. */
    static final String POSTCODE = "^\\s*[A-Za-z]{1,2}[0-9][A-Za-z0-9]?\\s*[0-9][A-Za-z]{2}\\s*$";

    private final OrderService orders;

    public OrderController(OrderService orders) {
        this.orders = orders;
    }

    public record PlaceOrderRequest(
            @NotBlank(message = "Your basket has expired. Add the parts again.") String basketId,
            @NotBlank(message = "Enter your full name.") @Size(max = 100) String name,
            @NotBlank(message = "Enter your email address.") @Email(message = "Enter an email address like name@example.com.") @Size(max = 200) String email,
            @NotBlank(message = "Enter a phone number so the courier can reach you.") @Pattern(regexp = "^[+0-9 ()-]{10,20}$", message = "Enter a UK phone number, like 07700 900123.") String phone,
            @NotBlank(message = "Enter the first line of your address.") @Size(max = 120) String line1,
            @Size(max = 120) String line2,
            @NotBlank(message = "Enter your town or city.") @Size(max = 80) String town,
            @NotBlank(message = "Enter your postcode.") @Pattern(regexp = POSTCODE, message = "Enter a full UK postcode, like SW1A 1AA.") String postcode,
            @Size(max = 500, message = "Keep delivery notes under 500 characters.") String notes) {
    }

    @PostMapping
    public ResponseEntity<Order> place(@Valid @RequestBody PlaceOrderRequest r) {
        Order order = orders.place(r.basketId(),
                new Order.Customer(r.name().trim(), r.email().trim(), r.phone().trim()),
                new Order.Address(r.line1().trim(), r.line2() == null ? null : r.line2().trim(), r.town().trim(),
                        formatPostcode(r.postcode())),
                r.notes());
        return ResponseEntity.status(HttpStatus.CREATED).body(order);
    }

    @GetMapping("/{reference}")
    public Order get(@PathVariable String reference) {
        return orders.get(reference);
    }

    /** "sw1a1aa" becomes "SW1A 1AA". */
    static String formatPostcode(String raw) {
        String p = raw.replaceAll("\\s", "").toUpperCase();
        return p.substring(0, p.length() - 3) + " " + p.substring(p.length() - 3);
    }
}
