package com.speedyfixautosales.store.mail;

import static org.assertj.core.api.Assertions.assertThat;

import com.speedyfixautosales.store.invoice.InvoicePdf;
import com.speedyfixautosales.store.invoice.InvoiceService;
import com.speedyfixautosales.store.invoice.InvoiceTest;
import com.speedyfixautosales.store.invoice.Invoices;
import com.speedyfixautosales.store.order.InMemoryOrderStore;
import com.speedyfixautosales.store.order.Order;
import com.speedyfixautosales.store.order.OrderCancelled;
import com.speedyfixautosales.store.order.OrderPaid;
import com.speedyfixautosales.store.order.PaymentNeedsChecking;
import java.math.BigDecimal;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.jdbc.core.JdbcTemplate;

class OrderEmailsTest {

    /** Resend with a key, keeping what it was asked to send; set failure to make it refuse. */
    private static class FakeSender extends EmailSender {
        final List<Email> sent = new ArrayList<>();
        SendFailed failure;

        FakeSender(boolean keys) {
            super(keys ? "re_test" : "", "SpeedyFix <orders@example.com>", "");
        }

        @Override
        public String send(Email email, String idempotencyKey) throws IOException {
            if (failure != null) {
                throw failure;
            }
            sent.add(email);
            return "email-" + sent.size();
        }
    }

    private final InMemoryOrderStore orders = new InMemoryOrderStore();
    private final EmailOutbox outbox = new EmailOutbox(new StaticListableBeanFactory().getBeanProvider(JdbcTemplate.class));
    private final Invoices invoices = new Invoices(new StaticListableBeanFactory().getBeanProvider(JdbcTemplate.class));
    private final InvoiceService invoiceService = new InvoiceService(invoices, new InvoicePdf(InvoiceTest.SITE));

    private OrderEmails emails(FakeSender sender, String staff) {
        return new OrderEmails(sender, outbox, orders, invoiceService, InvoiceTest.SITE, "https://shop.example/", staff);
    }

    private Order paid(String name) {
        Order order = InvoiceTest.paidOrder(name, "9.95", InvoiceTest.line("coil", "Ignition coil", "19.99", 1));
        orders.save(order);
        return order;
    }

    @Test
    void aPaidOrderEmailsTheBuyerTheirInvoiceAndStaffACopy() {
        FakeSender sender = new FakeSender(true);
        OrderEmails emails = emails(sender, "orders@example.com");
        Order order = paid("Ann <b>Smith</b>");
        emails.orderPaid(new OrderPaid(order));
        emails.orderPaid(new OrderPaid(order));
        emails.sendDue();
        emails.sendDue();

        assertThat(sender.sent).hasSize(2);
        EmailSender.Email buyer = sender.sent.get(0);
        assertThat(buyer.to()).isEqualTo("ann@example.com");
        assertThat(buyer.subject()).startsWith("Order confirmed").contains("SF-TESTABCD", "INV-001001");
        assertThat(buyer.attachments()).singleElement().satisfies(a -> {
            assertThat(a.fileName()).isEqualTo("SpeedyFix-invoice-INV-001001.pdf");
            assertThat(new String(a.content(), 0, 5)).isEqualTo("%PDF-");
        });
        assertThat(buyer.html()).contains("Ann &lt;b&gt;Smith&lt;/b&gt;", "https://shop.example/order-received/SF-TESTABCD")
                .doesNotContain("<b>Smith");
        assertThat(sender.sent.get(1).to()).isEqualTo("orders@example.com");
        assertThat(sender.sent.get(1).html()).contains("https://shop.example/admin/orders/SF-TESTABCD");
        assertThat(outbox.forOrder(order.reference())).allSatisfy(e -> assertThat(e.status()).isEqualTo(EmailOutbox.Status.SENT));
    }

    @Test
    void aFailedSendIsTriedAgainLaterAndAnAddressResendRefusesIsGivenUp() {
        FakeSender sender = new FakeSender(true);
        OrderEmails emails = emails(sender, "");
        Order order = paid("Ann Smith");
        emails.orderPaid(new OrderPaid(order));

        sender.failure = new EmailSender.SendFailed("HTTP 503: down", false);
        emails.sendDue();
        assertThat(outbox.forOrder(order.reference())).singleElement().satisfies(e -> {
            assertThat(e.status()).isEqualTo(EmailOutbox.Status.PENDING);
            assertThat(e.attempts()).isEqualTo(1);
            assertThat(e.nextAttemptAt()).isAfter(e.createdAt().plusSeconds(50));
        });

        emails.sendInvoiceAgain(order);
        assertThat(outbox.forOrder(order.reference())).singleElement().satisfies(e -> assertThat(e.attempts()).isEqualTo(1));

        sender.failure = new EmailSender.SendFailed("HTTP 422: invalid to", true);
        emails.sendInvoiceAgain(order);
        assertThat(outbox.forOrder(order.reference())).singleElement().satisfies(e -> {
            assertThat(e.status()).isEqualTo(EmailOutbox.Status.FAILED);
            assertThat(e.lastError()).contains("422");
        });

        sender.failure = null;
        emails.sendInvoiceAgain(order);
        assertThat(sender.sent).hasSize(1);
        assertThat(outbox.forOrder(order.reference())).singleElement().satisfies(e -> assertThat(e.status()).isEqualTo(EmailOutbox.Status.SENT));
    }

    @Test
    void withoutEmailTheInvoiceIsStillNumberedButNothingIsQueued() {
        OrderEmails emails = emails(new FakeSender(false), "orders@example.com");
        Order order = paid("Ann Smith");
        emails.orderPaid(new OrderPaid(order));
        assertThat(invoices.find(order.reference())).isPresent();
        assertThat(outbox.forOrder(order.reference())).isEmpty();
    }

    @Test
    void aCancelledOrderTellsTheBuyerWhetherMoneyWasTaken() {
        FakeSender sender = new FakeSender(true);
        OrderEmails emails = emails(sender, "");
        Order paid = paid("Ann Smith").withStatus(Order.Status.CANCELLED, null);
        Order unpaid = new Order("SF-UNPAID01", paid.placedAt(), Order.Status.CANCELLED, paid.customer(), paid.address(), null,
                paid.lines(), paid.delivery(), paid.subtotal(), paid.total(), paid.vatIncluded(), null, null, null);
        orders.save(paid);
        orders.save(unpaid);
        emails.orderCancelled(new OrderCancelled(unpaid));
        emails.orderCancelled(new OrderCancelled(paid));
        emails.orderCancelled(new OrderCancelled(paid));
        emails.sendDue();

        assertThat(sender.sent).hasSize(2);
        assertThat(sender.sent).allSatisfy(e -> {
            assertThat(e.to()).isEqualTo("ann@example.com");
            assertThat(e.attachments()).isEmpty();
        });
        assertThat(sender.sent.get(0).subject()).contains("SF-UNPAID01", "cancelled");
        assertThat(sender.sent.get(0).text()).contains("No money was taken", "Order total");
        assertThat(sender.sent.get(1).text()).contains("will be refunded", "£29.94");
    }

    @Test
    void aPaymentThatDoesntMatchAlertsStaffOnceWithWhatPayPalSaid() {
        FakeSender sender = new FakeSender(true);
        OrderEmails emails = emails(sender, "orders@example.com");
        Order order = paid("Ann Smith");
        PaymentNeedsChecking check = new PaymentNeedsChecking(order, "COMPLETED", "GBP", new BigDecimal("10.00"));
        emails.paymentNeedsChecking(check);
        emails.paymentNeedsChecking(check);
        emails.sendDue();

        assertThat(sender.sent).singleElement().satisfies(e -> {
            assertThat(e.to()).isEqualTo("orders@example.com");
            assertThat(e.subject()).contains("SF-TESTABCD");
            assertThat(e.text()).contains("GBP 10.00", "GBP 29.94", "PAYPAL-1");
        });
    }

    @Test
    void withoutAStaffAddressAPaymentCheckIsOnlyLogged() {
        FakeSender sender = new FakeSender(true);
        Order order = paid("Ann Smith");
        emails(sender, "").paymentNeedsChecking(new PaymentNeedsChecking(order, "PENDING", null, null));
        assertThat(outbox.forOrder(order.reference())).isEmpty();
    }
}
