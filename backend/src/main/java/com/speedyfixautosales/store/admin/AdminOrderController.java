package com.speedyfixautosales.store.admin;

import com.speedyfixautosales.store.invoice.Invoice;
import com.speedyfixautosales.store.invoice.InvoiceService;
import com.speedyfixautosales.store.mail.EmailOutbox;
import com.speedyfixautosales.store.mail.OrderEmails;
import com.speedyfixautosales.store.order.Order;
import com.speedyfixautosales.store.order.OrderService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Orders for staff to take payment for, dispatch or cancel. Every route needs a staff session (AdminWebConfig). */
@RestController
@RequestMapping("/api/admin/orders")
public class AdminOrderController {

    private final OrderService orders;
    private final InvoiceService invoices;
    private final OrderEmails emails;

    public AdminOrderController(OrderService orders, InvoiceService invoices, OrderEmails emails) {
        this.orders = orders;
        this.invoices = invoices;
        this.emails = emails;
    }

    /**
     * The order's invoice number (null until it's paid), whether email is set up, and each email it has sent or
     * tried to send.
     */
    public record InvoiceStatus(String invoiceNumber, Instant issuedAt, boolean emailOn, List<EmailOutbox.Entry> emails) {
    }

    public record StatusChange(@NotNull(message = "Choose a status.") Order.Status status,
                               @Size(max = 60, message = "Keep the courier under 60 characters.") String courier,
                               @Size(max = 60, message = "Keep the tracking number under 60 characters.") String trackingNumber,
                               /** Cancelling a dispatched order: true once its parts are back, to put them on sale again. */
                               Boolean restock) {
    }

    /** Newest first. */
    @GetMapping
    public List<Order> all() {
        return orders.all();
    }

    @GetMapping("/{reference}")
    public Order get(@PathVariable String reference) {
        return orders.get(reference);
    }

    @GetMapping("/{reference}/invoice")
    public InvoiceStatus invoice(@PathVariable String reference) {
        Order order = orders.get(reference);
        Optional<Invoice> invoice = invoices.forOrder(order);
        return new InvoiceStatus(invoice.map(Invoice::label).orElse(null), invoice.map(Invoice::issuedAt).orElse(null),
                emails.enabled(), emails.forOrder(order.reference()));
    }

    /** Emails the invoice to the buyer again, now. */
    @PostMapping("/{reference}/invoice/email")
    public InvoiceStatus emailInvoice(@PathVariable String reference) {
        emails.sendInvoiceAgain(orders.get(reference));
        return invoice(reference);
    }

    @PatchMapping("/{reference}")
    public Order update(@PathVariable String reference, @Valid @RequestBody StatusChange change) {
        return orders.setStatus(reference, change.status(), change.courier(), change.trackingNumber(),
                Boolean.TRUE.equals(change.restock()));
    }
}
