package com.speedyfixautosales.store.mail;

import com.speedyfixautosales.store.basket.BasketView;
import com.speedyfixautosales.store.common.BadRequestException;
import com.speedyfixautosales.store.common.UnavailableException;
import com.speedyfixautosales.store.config.SiteProperties;
import com.speedyfixautosales.store.invoice.Invoice;
import com.speedyfixautosales.store.invoice.InvoiceService;
import com.speedyfixautosales.store.order.Countries;
import com.speedyfixautosales.store.order.Order;
import com.speedyfixautosales.store.order.OrderCancelled;
import com.speedyfixautosales.store.order.OrderPaid;
import com.speedyfixautosales.store.order.OrderStore;
import com.speedyfixautosales.store.order.PaymentNeedsChecking;
import com.speedyfixautosales.store.shipping.DeliveryQuotes;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

/**
 * The emails an order sends: when paid, the buyer's VAT invoice (PDF attached) and, with MAIL_STAFF_ORDERS set, a
 * new-order note to staff with the same invoice; when cancelled, a note to the buyer; and when PayPal reports a payment
 * that doesn't match the order, an alert to staff. They're queued in the EmailOutbox when the order is paid and sent every half
 * minute, retried for about 8 hours if the mail service is down. Without email set up, the invoice is still numbered
 * and shown on the order page; nothing is queued.
 */
@Service
public class OrderEmails {

    private static final Logger log = LoggerFactory.getLogger(OrderEmails.class);

    /** Wait before each retry; after the last, the email is given up on (staff can send it again from the order). */
    static final List<Duration> RETRIES = List.of(Duration.ofMinutes(1), Duration.ofMinutes(5), Duration.ofMinutes(30),
            Duration.ofHours(2), Duration.ofHours(6));

    private final EmailSender sender;
    private final EmailOutbox outbox;
    private final OrderStore orders;
    private final InvoiceService invoices;
    private final SiteProperties site;
    private final String publicUrl;
    private final String staffOrders;

    public OrderEmails(EmailSender sender, EmailOutbox outbox, OrderStore orders, InvoiceService invoices, SiteProperties site,
                       @Value("${site.public-url:}") String publicUrl, @Value("${mail.staff-orders:}") String staffOrders) {
        this.sender = sender;
        this.outbox = outbox;
        this.orders = orders;
        this.invoices = invoices;
        this.site = site;
        this.publicUrl = publicUrl.trim().replaceAll("/+$", "");
        this.staffOrders = staffOrders.trim();
    }

    public boolean enabled() {
        return sender.enabled();
    }

    /** Numbers the invoice and queues its emails. Never throws: the order is paid whatever happens here. */
    @EventListener
    public void orderPaid(OrderPaid event) {
        Order order = event.order();
        try {
            Invoice invoice = invoices.forOrder(order).orElseThrow();
            if (!enabled()) {
                log.info("Email isn’t set up, so invoice {} for order {} wasn’t emailed.", invoice.label(), order.reference());
                return;
            }
            outbox.queue(order.reference(), EmailOutbox.Kind.CUSTOMER_INVOICE, order.customer().email());
            if (!staffOrders.isEmpty()) {
                outbox.queue(order.reference(), EmailOutbox.Kind.STAFF_NEW_ORDER, staffOrders);
            }
        } catch (RuntimeException e) {
            log.error("Invoice or emails for paid order {} not set up", order.reference(), e);
        }
    }

    /** Tells the buyer their order was cancelled: unpaid (no money taken) or, if it was paid, that it'll be refunded. */
    @EventListener
    public void orderCancelled(OrderCancelled event) {
        Order order = event.order();
        try {
            if (enabled()) {
                outbox.queue(order.reference(), EmailOutbox.Kind.CUSTOMER_CANCELLED, order.customer().email());
            }
        } catch (RuntimeException e) {
            log.error("Cancellation email for order {} not queued", order.reference(), e);
        }
    }

    /** Alerts staff that money may have moved without matching the order. Once per order, however often it repeats. */
    @EventListener
    public void paymentNeedsChecking(PaymentNeedsChecking event) {
        Order order = event.order();
        try {
            if (!enabled() || staffOrders.isEmpty()) {
                log.warn("Payment for order {} needs checking in PayPal, but there's no staff email to tell.", order.reference());
                return;
            }
            String details = "PayPal reported: status " + event.paypalStatus() + ", "
                    + (event.amount() == null ? "no amount" : event.currency() + " " + event.amount().toPlainString())
                    + ". The order's total is GBP " + order.total().toPlainString() + ".";
            outbox.queue(order.reference(), EmailOutbox.Kind.STAFF_PAYMENT_CHECK, staffOrders, details);
        } catch (RuntimeException e) {
            log.error("Payment check email for order {} not queued", order.reference(), e);
        }
    }

    /** Staff's "Email the invoice again": to the buyer's address, now, even if it was sent before. */
    public void sendInvoiceAgain(Order order) {
        if (!enabled()) {
            throw new UnavailableException("Email isn’t set up yet, so the invoice can’t be emailed. Download it and send it yourself.");
        }
        if (invoices.forOrder(order).isEmpty()) {
            throw new BadRequestException("The order has no invoice until it’s paid.");
        }
        outbox.requeue(order.reference(), EmailOutbox.Kind.CUSTOMER_INVOICE, order.customer().email());
        sendDue();
    }

    public List<EmailOutbox.Entry> forOrder(String reference) {
        return outbox.forOrder(reference);
    }

    @Scheduled(fixedDelayString = "PT30S", initialDelayString = "PT20S")
    public synchronized void sendDue() {
        if (!enabled()) {
            return;
        }
        for (EmailOutbox.Entry entry : outbox.due(Instant.now(), 20)) {
            send(entry);
        }
    }

    private void send(EmailOutbox.Entry entry) {
        try {
            Optional<Order> order = orders.find(entry.orderReference());
            if (order.isEmpty()) {
                outbox.failed(entry.id(), "The order no longer exists.", null);
                return;
            }
            EmailSender.Email email = switch (entry.kind()) {
                case CUSTOMER_INVOICE -> customerEmail(order.get(), invoices.document(order.get()), entry.recipient());
                case STAFF_NEW_ORDER -> staffEmail(order.get(), invoices.document(order.get()), entry.recipient());
                case CUSTOMER_CANCELLED -> cancelledEmail(order.get(), entry.recipient());
                case STAFF_PAYMENT_CHECK -> paymentCheckEmail(order.get(), entry.details(), entry.recipient());
            };
            // Each try (and each "send again", which sets a new time) is its own request to Resend.
            sender.send(email, "order-email-" + entry.id() + "-" + entry.nextAttemptAt().toEpochMilli());
            outbox.sent(entry.id());
            log.info("Emailed {} for order {}", entry.kind(), entry.orderReference());
        } catch (IOException | RuntimeException e) {
            boolean permanent = e instanceof EmailSender.SendFailed f && f.permanent();
            Instant retryAt = permanent || entry.attempts() >= RETRIES.size() ? null : Instant.now().plus(RETRIES.get(entry.attempts()));
            outbox.failed(entry.id(), e.getMessage(), retryAt);
            log.warn("Email {} for order {} not sent ({}): {}", entry.kind(), entry.orderReference(),
                    retryAt == null ? "given up" : "will retry", e.getMessage());
        }
    }

    // The emails. Everything from the order is escaped: names, addresses and notes are typed by shoppers.

    EmailSender.Email customerEmail(Order order, InvoiceService.Document invoice, String to) {
        String first = order.customer().name().trim().split("\\s+")[0];
        String orderUrl = publicUrl + "/order-received/" + order.reference();
        String subject = "Order confirmed: " + order.reference() + " (invoice " + invoice.invoice().label() + ")";
        StringBuilder html = new StringBuilder(start());
        html.append(p("<strong style=\"font-size:18px\">Thank you, " + esc(first) + ". Your order " + order.reference() + " is confirmed.</strong>"));
        html.append(p("We’ve received your payment of " + money(order.total()) + ". Your VAT invoice <strong>" + invoice.invoice().label()
                + "</strong> is attached; keep it for your records."));
        html.append(lines(order));
        html.append(p("<strong>Delivering to</strong><br>" + address(order)));
        if (DeliveryQuotes.manual(order.delivery())) {
            html.append(p("<strong>You chose manual delivery.</strong> Delivery isn’t included in what you’ve paid. Reply to this email "
                    + "or call " + esc(site.phone()) + ", quoting " + order.reference() + ", and we’ll quote you from our other delivery "
                    + "partners (" + esc(site.couriers()) + "). We’ll send your parts once delivery is agreed."));
        } else {
            html.append(p("We’ll check and pack your parts, then send them. The courier and tracking number will show on your "
                    + "<a href=\"" + esc(orderUrl) + "\" style=\"color:#F26B1D\">order page</a> once they’re on their way."));
        }
        html.append(p("Questions? Reply to this email, or call " + esc(site.phone()) + ", quoting " + order.reference() + "."));
        html.append(end());
        String text = "Thank you, " + first + ". Your order " + order.reference() + " is confirmed.\n\n"
                + "We've received your payment of " + money(order.total()) + ". Your VAT invoice " + invoice.invoice().label() + " is attached.\n\n"
                + linesText(order) + "\nDelivering to: " + addressText(order) + "\n\n"
                + (DeliveryQuotes.manual(order.delivery())
                        ? "You chose manual delivery. Delivery isn't included in what you've paid. Reply to this email or call "
                                + site.phone() + ", quoting " + order.reference() + ", and we'll quote you from our other delivery "
                                + "partners (" + site.couriers() + "). We'll send your parts once delivery is agreed.\n\n"
                        : "We'll check and pack your parts, then send them. Your order page shows the courier and tracking number once "
                                + "they're on their way: " + orderUrl + "\n\n")
                + "Questions? Reply to this email, or call " + site.phone() + ", quoting " + order.reference() + ".\n\n"
                + footerText();
        return new EmailSender.Email(to, subject, html.toString(), text,
                List.of(new EmailSender.Attachment(invoice.fileName(), invoice.pdf())));
    }

    EmailSender.Email staffEmail(Order order, InvoiceService.Document invoice, String to) {
        String adminUrl = publicUrl + "/admin/orders/" + order.reference();
        String how = order.payment() != null && order.payment().captureId() != null ? "paid with PayPal" : "marked paid";
        String subject = "New order " + order.reference() + ": " + money(order.total()) + " (" + how + ")";
        StringBuilder html = new StringBuilder(start());
        html.append(p("<strong style=\"font-size:18px\">New order " + order.reference() + ", " + how + ".</strong>"));
        html.append(lines(order));
        html.append(p("<strong>Buyer</strong><br>" + esc(order.customer().name()) + "<br>" + esc(order.customer().email())
                + "<br>" + esc(order.customer().phone())));
        html.append(p("<strong>Deliver to</strong><br>" + address(order)));
        if (DeliveryQuotes.manual(order.delivery())) {
            html.append(p("<strong>Manual delivery:</strong> the buyer didn’t want FedEx and hasn’t paid for delivery. They’ve been asked "
                    + "to contact us for a quote from another courier; agree it and take payment before sending."));
        }
        if (order.notes() != null) {
            html.append(p("<strong>Buyer’s note</strong><br>" + esc(order.notes())));
        }
        html.append(p("<a href=\"" + esc(adminUrl) + "\" style=\"color:#F26B1D\">Open the order in the admin</a> to dispatch it. "
                + "Invoice " + invoice.invoice().label() + " is attached and has been emailed to the buyer."));
        html.append(end());
        String text = "New order " + order.reference() + ", " + how + ".\n\n" + linesText(order)
                + "\nBuyer: " + order.customer().name() + ", " + order.customer().email() + ", " + order.customer().phone()
                + "\nDeliver to: " + addressText(order)
                + (DeliveryQuotes.manual(order.delivery())
                        ? "\nManual delivery: the buyer hasn't paid for delivery and will contact us for a quote from another courier."
                        : "")
                + (order.notes() == null ? "" : "\nBuyer's note: " + order.notes())
                + "\n\nOpen the order: " + adminUrl + "\n";
        return new EmailSender.Email(to, subject, html.toString(), text,
                List.of(new EmailSender.Attachment(invoice.fileName(), invoice.pdf())));
    }

    EmailSender.Email cancelledEmail(Order order, String to) {
        String first = order.customer().name().trim().split("\\s+")[0];
        boolean paid = order.payment() != null && order.payment().captureId() != null;
        String subject = "Your " + site.name() + " order " + order.reference() + " has been cancelled";
        String why = paid
                ? "We’ve cancelled your order. Your payment of " + money(order.total()) + " will be refunded to the way you paid; refunds usually show within 5 working days."
                : "Your order wasn’t paid in time, so we’ve cancelled it and the parts have gone back on sale. No money was taken.";
        StringBuilder html = new StringBuilder(start());
        html.append(p("<strong style=\"font-size:18px\">Hello " + esc(first) + ", your order " + order.reference() + " has been cancelled.</strong>"));
        html.append(p(esc(why)));
        html.append(lines(order).replace("Total paid", paid ? "Total paid" : "Order total"));
        html.append(p(paid ? "If you didn’t ask for this, or have any questions, reply to this email or call " + esc(site.phone()) + "."
                : "Still need the parts? Add them to your basket again on our <a href=\"" + esc(publicUrl) + "\" style=\"color:#F26B1D\">website</a> while they’re in stock, or call " + esc(site.phone()) + "."));
        html.append(end());
        String text = "Hello " + first + ", your order " + order.reference() + " has been cancelled.\n\n" + why.replace('’', '\'') + "\n\n"
                + linesText(order).replace("Total paid", paid ? "Total paid" : "Order total")
                + "\n" + (paid ? "Questions? Reply to this email or call " + site.phone() + "."
                        : "Still need the parts? Order again at " + publicUrl + " or call " + site.phone() + ".")
                + "\n\n" + footerText();
        return new EmailSender.Email(to, subject, html.toString(), text, List.of());
    }

    EmailSender.Email paymentCheckEmail(Order order, String details, String to) {
        String adminUrl = publicUrl + "/admin/orders/" + order.reference();
        String paypalId = order.payment() == null ? "unknown" : order.payment().paypalOrderId();
        String subject = "Check payment for order " + order.reference() + " in PayPal";
        String body = "PayPal reported a payment for order " + order.reference() + " that doesn't match the order, so the order "
                + "wasn't marked paid. Money may have been taken. " + (details == null ? "" : details + " ")
                + "Find PayPal order " + paypalId + " in PayPal (Activity) to see what happened, then mark the order paid, "
                + "or refund the payment in PayPal and cancel the order. The buyer was asked to call with their order number.";
        StringBuilder html = new StringBuilder(start());
        html.append(p("<strong style=\"font-size:18px\">Payment for order " + order.reference() + " needs checking.</strong>"));
        html.append(p(esc(body)));
        html.append(p("<strong>Buyer</strong><br>" + esc(order.customer().name()) + "<br>" + esc(order.customer().email())
                + "<br>" + esc(order.customer().phone())));
        html.append(p("<a href=\"" + esc(adminUrl) + "\" style=\"color:#F26B1D\">Open the order in the admin</a>"));
        html.append(end());
        String text = body + "\n\nBuyer: " + order.customer().name() + ", " + order.customer().email() + ", "
                + order.customer().phone() + "\nOpen the order: " + adminUrl + "\n";
        return new EmailSender.Email(to, subject, html.toString(), text, List.of());
    }

    private String start() {
        return "<div style=\"font-family:Arial,Helvetica,sans-serif;color:#111;max-width:600px;margin:0 auto\">"
                + "<div style=\"background:#111;color:#fff;padding:18px 24px;border-bottom:5px solid #F26B1D\">"
                + "<strong style=\"font-size:20px;letter-spacing:1px\">SPEEDY<span style=\"color:#F26B1D\">FIX</span></strong>"
                + "<span style=\"font-size:12px;color:#bbb\"> &nbsp;AUTO SALES</span></div><div style=\"padding:8px 24px 16px\">";
    }

    private String end() {
        return "</div><div style=\"background:#111;color:#bbb;font-size:12px;padding:14px 24px;border-top:3px solid #F26B1D\">"
                + esc(footerText()).replace("\n", "<br>") + "</div></div>";
    }

    private String footerText() {
        return site.companyName() + ", " + site.address() + "\nVAT " + site.vatNumber() + " · Company No. " + site.companyNumber();
    }

    private static String p(String html) {
        return "<p style=\"font-size:14px;line-height:1.5;margin:14px 0\">" + html + "</p>";
    }

    private static String lines(Order order) {
        StringBuilder t = new StringBuilder("<table style=\"width:100%;border-collapse:collapse;font-size:14px;margin:14px 0\">");
        for (BasketView.Line l : order.lines()) {
            t.append(row(l.quantity() + " × " + esc(l.name()) + (l.reference() == null ? "" : "<br><span style=\"color:#777;font-size:12px\">" + esc(l.reference()) + "</span>"),
                    money(l.lineTotal()), false));
        }
        t.append(row("Delivery" + (order.delivery().label() == null ? "" : " (" + esc(order.delivery().label()) + ")"),
                deliveryPrice(order), false));
        t.append(row("Total paid " + vatNote(order), money(order.total()), true));
        return t.append("</table>").toString();
    }

    private static String row(String left, String right, boolean strong) {
        String style = "padding:8px 0;border-bottom:1px solid #e5e5e5;vertical-align:top" + (strong ? ";font-weight:bold" : "");
        return "<tr><td style=\"" + style + "\">" + left + "</td><td style=\"" + style + ";text-align:right;white-space:nowrap\">"
                + right + "</td></tr>";
    }

    private static String linesText(Order order) {
        StringBuilder t = new StringBuilder();
        for (BasketView.Line l : order.lines()) {
            t.append(l.quantity()).append(" x ").append(l.name()).append(": ").append(money(l.lineTotal())).append('\n');
        }
        t.append("Delivery: ").append(deliveryPrice(order)).append('\n');
        return t.append("Total paid: ").append(money(order.total())).append(" ").append(vatNote(order)).append("\n").toString();
    }

    private static String deliveryPrice(Order order) {
        if (DeliveryQuotes.manual(order.delivery())) {
            return "Quoted separately";
        }
        return order.delivery().price().signum() == 0 ? "Free" : money(order.delivery().price());
    }

    private static String address(Order order) {
        return esc(order.customer().name()) + "<br>" + esc(addressText(order)).replace(", ", "<br>");
    }

    private static String addressText(Order order) {
        Order.Address a = order.address();
        return a.line1() + (a.line2() == null || a.line2().isBlank() ? "" : ", " + a.line2()) + ", " + a.town()
                + (a.postcode() == null ? "" : ", " + a.postcode())
                + (Countries.UK.equals(a.country()) ? "" : ", " + Countries.name(a.country()));
    }

    /** "(includes £9.18 VAT)", or for an export "(no UK VAT: export; any import duty is paid on arrival)". */
    private static String vatNote(Order order) {
        return order.address().export()
                ? "(no UK VAT: export; any import duty is paid on arrival)"
                : "(includes " + money(order.vatIncluded()) + " VAT)";
    }

    private static String money(BigDecimal amount) {
        return "£" + String.format(Locale.UK, "%,.2f", amount);
    }

    private static String esc(String s) {
        return s == null ? "" : HtmlUtils.htmlEscape(s);
    }
}
