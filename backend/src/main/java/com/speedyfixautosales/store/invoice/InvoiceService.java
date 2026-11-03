package com.speedyfixautosales.store.invoice;

import com.speedyfixautosales.store.common.NotFoundException;
import com.speedyfixautosales.store.order.Order;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * Every paid order gets a VAT invoice, numbered when it's paid (by PayPal, or marked paid by staff). Orders paid
 * before invoices existed get theirs the first time it's asked for. Unpaid orders have none.
 */
@Service
public class InvoiceService {

    private final Invoices invoices;
    private final InvoicePdf pdf;

    public InvoiceService(Invoices invoices, InvoicePdf pdf) {
        this.invoices = invoices;
        this.pdf = pdf;
    }

    /** The order's invoice, numbering it now if it's paid and has none; empty while it's unpaid. */
    public Optional<Invoice> forOrder(Order order) {
        Optional<Invoice> existing = invoices.find(order.reference());
        if (existing.isPresent() || !paid(order)) {
            return existing;
        }
        Instant paidAt = order.payment() != null && order.payment().paidAt() != null ? order.payment().paidAt() : Instant.now();
        return Optional.of(invoices.issue(order.reference(), paidAt));
    }

    public record Document(Invoice invoice, String fileName, byte[] pdf) {
    }

    /** The order's invoice as a PDF. */
    public Document document(Order order) {
        Invoice invoice = forOrder(order).orElseThrow(() -> new NotFoundException("The invoice is ready once the order is paid."));
        return new Document(invoice, InvoicePdf.fileName(invoice), pdf.render(order, invoice));
    }

    static boolean paid(Order order) {
        return order.status() == Order.Status.AWAITING_DISPATCH || order.status() == Order.Status.DISPATCHED;
    }
}
