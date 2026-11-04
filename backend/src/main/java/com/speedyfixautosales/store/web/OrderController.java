package com.speedyfixautosales.store.web;

import com.speedyfixautosales.store.account.CustomerAuthService;
import com.speedyfixautosales.store.admin.AdminAuthInterceptor;
import com.speedyfixautosales.store.common.BadRequestException;
import com.speedyfixautosales.store.common.NotFoundException;
import com.speedyfixautosales.store.common.UnauthorizedException;
import com.speedyfixautosales.store.invoice.InvoiceService;
import com.speedyfixautosales.store.order.Countries;
import com.speedyfixautosales.store.order.Order;
import com.speedyfixautosales.store.order.OrderPaymentService;
import com.speedyfixautosales.store.order.OrderService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    /** Full UK postcode, any case, with or without the space. */
    static final String POSTCODE = "^\\s*[A-Za-z]{1,2}[0-9][A-Za-z0-9]?\\s*[0-9][A-Za-z]{2}\\s*$";

    private final OrderService orders;
    private final CustomerAuthService accounts;
    private final OrderPaymentService payments;
    private final InvoiceService invoices;
    private final boolean requireSignIn;

    public OrderController(OrderService orders, CustomerAuthService accounts, OrderPaymentService payments,
                           InvoiceService invoices, @Value("${orders.require-sign-in:false}") boolean requireSignIn) {
        this.orders = orders;
        this.accounts = accounts;
        this.payments = payments;
        this.invoices = invoices;
        this.requireSignIn = requireSignIn;
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

    /**
     * Starts paying an unpaid order with PayPal; the browser hands PayPal's order id to PayPal's buttons, or (card=true)
     * to the card form on the checkout page.
     */
    @PostMapping("/{reference}/paypal")
    public PayPalStarted startPayPal(@PathVariable String reference, @RequestParam(defaultValue = "false") boolean card) {
        return new PayPalStarted(payments.startPayPal(reference, card));
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
            @NotBlank(message = "Enter a phone number so the courier can reach you.") @Pattern(regexp = "^[+0-9 ()-]{7,20}$", message = "Enter a phone number, like 07700 900123 or +91 98765 43210.") String phone,
            @NotBlank(message = "Enter the first line of your address.") @Size(max = 120) String line1,
            @Size(max = 120) String line2,
            @NotBlank(message = "Enter your town or city.") @Size(max = 80) String town,
            /** Required and checked for the UK; abroad, as the country writes it (blank where there's none). */
            @Size(max = 16, message = "Enter your postcode.") @Pattern(regexp = "^[A-Za-z0-9 -]*$", message = "Enter your postcode using letters, numbers, spaces and dashes.") String postcode,
            /** ISO country code, like GB or IN; blank is the UK. */
            @Size(max = 2) String country,
            @Size(max = 500, message = "Keep delivery notes under 500 characters.") String notes,
            /** The delivery option chosen at checkout (an id from the basket's delivery options); null for the cheapest. */
            @Size(max = 60) String deliveryOption) {
    }

    /**
     * A signed-in shopper sends their account token, so the order shows in their account. With ORDERS_REQUIRE_SIGN_IN=true,
     * only signed-in shoppers can order; otherwise (the default) guests can too.
     */
    @PostMapping
    public ResponseEntity<Order> place(@Valid @RequestBody PlaceOrderRequest r, HttpServletRequest request) {
        String customerId = accounts.customerId(AdminAuthInterceptor.bearerToken(request)).orElse(null);
        if (customerId == null && requireSignIn) {
            throw new UnauthorizedException("Sign in to place your order. Your basket is kept.");
        }
        Order order = orders.place(r.basketId(),
                new Order.Customer(r.name().trim(), r.email().trim(), r.phone().trim()),
                address(r.line1(), r.line2(), r.town(), r.postcode(), r.country()),
                r.notes(), customerId, r.deliveryOption());
        return ResponseEntity.status(HttpStatus.CREATED).body(order);
    }

    @GetMapping("/{reference}")
    public Order get(@PathVariable String reference) {
        return orders.get(reference);
    }

    /**
     * The paid order's VAT invoice as a PDF, shown in the browser, or saved with download=true. Like the order itself,
     * anyone with the (unguessable) order number can open it.
     */
    @GetMapping("/{reference}/invoice.pdf")
    public ResponseEntity<byte[]> invoice(@PathVariable String reference, @RequestParam(defaultValue = "false") boolean download) {
        InvoiceService.Document doc = invoices.document(orders.get(reference));
        ContentDisposition disposition = (download ? ContentDisposition.attachment() : ContentDisposition.inline())
                .filename(doc.fileName()).build();
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .body(doc.pdf());
    }

    /** A checked delivery address: a full postcode in the UK; abroad, any country we deliver to, with its postcode or ZIP. */
    static Order.Address address(String line1, String line2, String town, String postcode, String country) {
        String c = Countries.normalise(country);
        if (!Countries.delivered(c)) {
            throw new BadRequestException("Sorry, we can’t deliver to that country. Call us and we’ll see what we can do.");
        }
        String p = postcode == null ? "" : postcode.trim();
        if (Countries.UK.equals(c)) {
            if (!p.matches(POSTCODE)) {
                throw new BadRequestException("Enter a full UK postcode, like SW1A 1AA.");
            }
            p = formatPostcode(p);
        } else {
            if (p.isEmpty()) {
                throw new BadRequestException("Enter your postcode or ZIP.");
            }
            p = p.replaceAll(" {2,}", " ").toUpperCase(java.util.Locale.ROOT);
        }
        return new Order.Address(line1.trim(), line2 == null || line2.isBlank() ? null : line2.trim(), town.trim(), p, c);
    }

    /** "sw1a1aa" becomes "SW1A 1AA". */
    static String formatPostcode(String raw) {
        String p = raw.replaceAll("\\s", "").toUpperCase();
        return p.substring(0, p.length() - 3) + " " + p.substring(p.length() - 3);
    }
}
