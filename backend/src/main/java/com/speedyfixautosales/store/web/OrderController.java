package com.speedyfixautosales.store.web;

import com.speedyfixautosales.store.account.CustomerAuthService;
import com.speedyfixautosales.store.admin.AdminAuthInterceptor;
import com.speedyfixautosales.store.common.NotFoundException;
import com.speedyfixautosales.store.order.Order;
import com.speedyfixautosales.store.order.OrderPaymentService;
import com.speedyfixautosales.store.order.OrderService;
import jakarta.servlet.http.HttpServletRequest;
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
    private final CustomerAuthService accounts;
    private final OrderPaymentService payments;

    public OrderController(OrderService orders, CustomerAuthService accounts, OrderPaymentService payments) {
        this.orders = orders;
        this.accounts = accounts;
        this.payments = payments;
    }

    public record PayPalStarted(String paypalOrderId) {
    }

    public record OrderLookup(
            @NotBlank(message = "Enter your order number.") @Size(max = 20) String reference,
            @NotBlank(message = "Enter the email you ordered with.") @Size(max = 200) String email) {
    }

    /**
     * For the help chat's "Where's my order?": the order, only if the email is the one it was placed with. A wrong
     * number or email gets the same answer, so this can't be used to find out which orders exist.
     */
    @PostMapping("/lookup")
    public Order lookup(@Valid @RequestBody OrderLookup q) {
        try {
            Order order = orders.get(q.reference());
            if (order.customer().email().equalsIgnoreCase(q.email().trim())) {
                return order;
            }
        } catch (NotFoundException e) {
            // Same answer as a wrong email, below.
        }
        throw new NotFoundException("We couldn’t find an order with that number and email. Check both, or call us.");
    }

    public record PayPalApproved(@NotBlank(message = "PayPal didn’t send the payment. Try again.") @Size(max = 40) String paypalOrderId) {
    }

    /** Starts paying an unpaid order with PayPal; the browser hands PayPal's order id to PayPal's buttons. */
    @PostMapping("/{reference}/paypal")
    public PayPalStarted startPayPal(@PathVariable String reference) {
        return new PayPalStarted(payments.startPayPal(reference));
    }

    /** After the shopper approves in PayPal: takes the payment and returns the order, now paid. */
    @PostMapping("/{reference}/paypal/capture")
    public Order capturePayPal(@PathVariable String reference, @Valid @RequestBody PayPalApproved approved) {
        return payments.capturePayPal(reference, approved.paypalOrderId());
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

    /** A signed-in shopper sends their account token, so the order shows in their account; guests send none. */
    @PostMapping
    public ResponseEntity<Order> place(@Valid @RequestBody PlaceOrderRequest r, HttpServletRequest request) {
        String customerId = accounts.customerId(AdminAuthInterceptor.bearerToken(request)).orElse(null);
        Order order = orders.place(r.basketId(),
                new Order.Customer(r.name().trim(), r.email().trim(), r.phone().trim()),
                new Order.Address(r.line1().trim(), r.line2() == null ? null : r.line2().trim(), r.town().trim(),
                        formatPostcode(r.postcode())),
                r.notes(), customerId);
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
