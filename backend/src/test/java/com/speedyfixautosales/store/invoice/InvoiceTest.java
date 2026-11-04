package com.speedyfixautosales.store.invoice;

import static org.assertj.core.api.Assertions.assertThat;

import com.speedyfixautosales.store.basket.BasketView;
import com.speedyfixautosales.store.config.SiteProperties;
import com.speedyfixautosales.store.order.Order;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.jdbc.core.JdbcTemplate;

public class InvoiceTest {

    public static final SiteProperties SITE = new SiteProperties("SpeedyFix Auto Sales", "", "+44 7707 704977", "", "eBay.speedyfix@gmail.com",
            "SpeedyFix Auto Sales Ltd", "Unit 2E, Tomo Industrial Estate, Packet Boat Lane, Uxbridge, Middlesex, UB8 2JP",
            "", "", "", "", "", "GB 508552979", "16664355", List.of());

    public static BasketView.Line line(String id, String name, String price, int qty) {
        BigDecimal unit = new BigDecimal(price);
        return new BasketView.Line(id, name, "OE " + id.toUpperCase(), null, unit, qty, unit.multiply(BigDecimal.valueOf(qty)), 1);
    }

    /** A paid order: lines, then the delivery price; total and VAT worked out as the basket does. */
    public static Order paidOrder(String name, String delivery, BasketView.Line... lines) {
        BigDecimal subtotal = List.of(lines).stream().map(BasketView.Line::lineTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal total = subtotal.add(new BigDecimal(delivery));
        return new Order("SF-TESTABCD", Instant.parse("2026-11-03T10:15:00Z"), Order.Status.AWAITING_DISPATCH,
                new Order.Customer(name, "ann@example.com", "07700 900123"),
                new Order.Address("1 High Street", null, "Manchester", "M1 1AE"), null, List.of(lines),
                new BasketView.Delivery("FEDEX", "FedEx Priority", new BigDecimal(delivery)), subtotal, total,
                total.divide(BigDecimal.valueOf(6), 2, java.math.RoundingMode.HALF_UP), null,
                new Order.Payment("PAYPAL-1", "CAPTURE-9XY", Instant.parse("2026-11-03T10:20:00Z")), null);
    }

    private static String text(byte[] pdf) throws IOException {
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            return new PDFTextStripper().getText(doc);
        }
    }

    @Test
    void pricesAreShownBeforeVatAndTheVatMakesUpTheTotalPaid() {
        Order order = paidOrder("Ann Smith", "9.95", line("coil", "Ignition coil", "19.99", 1), line("sensor", "Sensor", "50.00", 2));
        InvoiceAmounts a = InvoiceAmounts.of(order);
        // 19.99 / 1.2 = 16.66; 50.00 / 1.2 = 41.67, twice; delivery 9.95 / 1.2 = 8.29.
        assertThat(a.lines()).extracting(InvoiceAmounts.Line::unitPrice).containsExactly(new BigDecimal("16.66"), new BigDecimal("41.67"));
        assertThat(a.lines().get(1).total()).isEqualByComparingTo("83.34");
        assertThat(a.subtotal()).isEqualByComparingTo("100.00");
        assertThat(a.delivery()).isEqualByComparingTo("8.29");
        assertThat(a.vat()).isEqualByComparingTo("21.65");
        assertThat(a.subtotal().add(a.delivery()).add(a.vat())).isEqualByComparingTo(order.total()).isEqualByComparingTo("129.94");
    }

    @Test
    void invoicesAreNumberedInTurnAndAnOrderKeepsItsNumber() {
        Invoices invoices = new Invoices(new StaticListableBeanFactory().getBeanProvider(JdbcTemplate.class));
        Invoice first = invoices.issue("SF-A", Instant.now());
        Invoice second = invoices.issue("SF-B", Instant.now());
        assertThat(first.number()).isEqualTo(1001);
        assertThat(second.number()).isEqualTo(1002);
        assertThat(invoices.issue("SF-A", Instant.now())).isEqualTo(first);
        assertThat(first.label()).isEqualTo("INV-001001");
    }

    @Test
    void unpaidOrdersHaveNoInvoice() {
        InvoiceService service = new InvoiceService(new Invoices(new StaticListableBeanFactory().getBeanProvider(JdbcTemplate.class)), new InvoicePdf(SITE));
        Order paid = paidOrder("Ann Smith", "0", line("coil", "Ignition coil", "19.99", 1));
        Order unpaid = paid.withStatus(Order.Status.AWAITING_PAYMENT, null);
        assertThat(service.forOrder(unpaid)).isEmpty();
        assertThat(service.forOrder(paid)).hasValueSatisfying(i -> assertThat(i.issuedAt()).isEqualTo("2026-11-03T10:20:00Z"));
    }

    @Test
    void thePdfCarriesWhatAVatInvoiceNeeds() throws IOException {
        Order order = paidOrder("Ann Smith", "9.95", line("coil", "Ignition coil", "19.99", 1), line("sensor", "Sensor", "50.00", 2));
        String text = text(new InvoicePdf(SITE).render(order, new Invoice(1001, order.reference(), Instant.parse("2026-11-03T10:20:00Z"))));
        assertThat(text).contains("INV-001001", "3 November 2026", "SF-TESTABCD", "VAT No: GB 508552979", "Company No: 16664355",
                "Ann Smith", "1 High Street, Manchester, M1 1AE", "Ignition coil", "OE COIL", "£16.66", "£83.34",
                "100.00", "8.29", "21.65", "129.94", "CAPTURE-9XY", "FedEx Priority");
    }

    @Test
    void longOrdersRunOnToMorePagesAndOddLettersDontBreakIt() throws IOException {
        List<BasketView.Line> lines = new ArrayList<>();
        for (int i = 1; i <= 30; i++) {
            lines.add(line("p" + i, "Part " + i + " for Mercedes-Benz A-Class W176 A180 CDI 1.5 diesel, tested and graded, with fitting bolts", "12.50", 1));
        }
        Order order = paidOrder("Zoë Łukasz 王 <b>", "0", lines.toArray(BasketView.Line[]::new));
        byte[] pdf = new InvoicePdf(SITE).render(order, new Invoice(1002, order.reference(), Instant.now()));
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            assertThat(doc.getNumberOfPages()).isGreaterThan(1);
        }
        assertThat(text(pdf)).contains("Zoë Lukasz ? <b>", "Part 30 for", "(continued)", "375.00");
    }

    @Test
    void anExportInvoiceShowsNoVatAndTheCountry() throws IOException {
        Order uk = paidOrder("Ravi Kumar", "79.18", line("coil", "Ignition coil", "16.66", 1));
        Order export = new Order(uk.reference(), uk.placedAt(), uk.status(), uk.customer(),
                new Order.Address("12 MG Road", null, "New Delhi", "110001", "IN"), null, uk.lines(), uk.delivery(),
                uk.subtotal(), uk.total(), BigDecimal.ZERO.setScale(2), null, uk.payment(), null);
        InvoiceAmounts a = InvoiceAmounts.of(export);
        assertThat(a.vat()).isEqualByComparingTo("0");
        assertThat(a.subtotal()).isEqualByComparingTo("16.66");
        assertThat(a.delivery()).isEqualByComparingTo("79.18");
        assertThat(a.total()).isEqualByComparingTo("95.84");
        String text = text(new InvoicePdf(SITE).render(export, new Invoice(1003, export.reference(), Instant.now())));
        assertThat(text).contains("VAT 0% (export)", "New Delhi, 110001, India", "Zero-rated export");
    }
}
