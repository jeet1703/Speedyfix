package com.speedyfixautosales.store.invoice;

import com.speedyfixautosales.store.config.SiteProperties;
import com.speedyfixautosales.store.order.Countries;
import com.speedyfixautosales.store.order.Order;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.springframework.stereotype.Component;

/**
 * Draws a paid order's VAT invoice as an A4 PDF, in the look of the shop's own sales invoice: the black header with
 * the logo and business details, the invoice number and dates, the buyer, the parts (before VAT), and the totals.
 * Long orders run on to more pages. Text is set in Helvetica; letters it can't show lose their accents.
 */
@Component
public class InvoicePdf {

    private static final float W = PDRectangle.A4.getWidth();
    private static final float H = PDRectangle.A4.getHeight();
    private static final float LEFT = 36;
    private static final float RIGHT = W - 36;
    /** Lowest a line of items may reach, above the footer. */
    private static final float BOTTOM = 70;

    private static final float[] BLACK = rgb(0x11, 0x11, 0x11);
    private static final float[] ORANGE = rgb(0xF2, 0x6B, 0x1D);
    private static final float[] PEACH = rgb(0xFD, 0xD9, 0xCD);
    private static final float[] GREY = rgb(0x6B, 0x6B, 0x6B);
    private static final float[] LIGHT = rgb(0xF2, 0xF2, 0xF2);
    private static final float[] RULE = rgb(0xE2, 0xE2, 0xE2);
    private static final float[] WHITE = rgb(0xFF, 0xFF, 0xFF);

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.UK).withZone(ZoneId.of("Europe/London"));
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("d MMMM yyyy, HH:mm", Locale.UK).withZone(ZoneId.of("Europe/London"));

    /** Item columns: number, description, part number, quantity, unit price, total (right edges for the last three). */
    private static final float COL_NUM = LEFT + 6;
    private static final float COL_DESC = LEFT + 24;
    private static final float COL_REF = 318;
    private static final float COL_QTY = 428;
    private static final float COL_UNIT = 494;
    private static final float COL_TOTAL = RIGHT - 6;

    private final SiteProperties site;
    private final byte[] logo;

    public InvoicePdf(SiteProperties site) {
        this.site = site;
        try (InputStream in = InvoicePdf.class.getResourceAsStream("/invoice/logo.jpg")) {
            this.logo = in == null ? null : in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** "SpeedyFix-invoice-INV-001001.pdf". */
    public static String fileName(Invoice invoice) {
        return "SpeedyFix-invoice-" + invoice.label() + ".pdf";
    }

    public byte[] render(Order order, Invoice invoice) {
        try (PDDocument doc = new PDDocument()) {
            PDDocumentInformation info = doc.getDocumentInformation();
            info.setTitle("Invoice " + invoice.label() + " (order " + order.reference() + ")");
            info.setAuthor(site.companyName());
            new Drawing(doc, order, invoice, InvoiceAmounts.of(order)).draw();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Couldn’t draw invoice " + invoice.label(), e);
        }
    }

    /** One invoice being drawn: pages are added as the items run on. */
    private final class Drawing {
        private final PDDocument doc;
        private final Order order;
        private final Invoice invoice;
        private final InvoiceAmounts amounts;
        private final PDType1Font regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
        private final PDType1Font bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
        private final List<PDPageContentStream> pages = new ArrayList<>();
        private PDPageContentStream cs;
        /** Distance from the top of the page to where the next thing goes. */
        private float top;

        Drawing(PDDocument doc, Order order, Invoice invoice, InvoiceAmounts amounts) {
            this.doc = doc;
            this.order = order;
            this.invoice = invoice;
            this.amounts = amounts;
        }

        void draw() throws IOException {
            try {
                newPage();
                header();
                fields();
                buyer();
                items();
                totals();
            } finally {
                for (PDPageContentStream p : pages) {
                    p.close();
                }
            }
        }

        private void newPage() throws IOException {
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);
            cs = new PDPageContentStream(doc, page);
            pages.add(cs);
            footer();
            top = 36;
        }

        private void header() throws IOException {
            fill(BLACK, 0, 0, W, 130);
            // The template's faint diagonal stripe.
            cs.setNonStrokingColor(0.16f, 0.16f, 0.16f);
            cs.moveTo(330, H);
            cs.lineTo(370, H);
            cs.lineTo(300, H - 130);
            cs.lineTo(260, H - 130);
            cs.closePath();
            cs.fill();
            fill(ORANGE, 0, 130, W, 6);

            if (logo != null) {
                PDImageXObject image = PDImageXObject.createFromByteArray(doc, logo, "logo");
                float lw = 128;
                float lh = lw * image.getHeight() / image.getWidth();
                fill(ORANGE, LEFT - 2, 14 - 2, lw + 4, lh + 4);
                cs.drawImage(image, LEFT, H - 14 - lh, lw, lh);
            }

            float x = 186;
            text(bold, 6.5f, ORANGE, x, 30, "ADDRESS");
            float y = 41;
            for (String line : addressLines(site.address())) {
                text(regular, 8.5f, WHITE, x, y, line);
                y += 10.5f;
            }
            text(bold, 6.5f, ORANGE, x, y + 3, "CONTACT");
            text(regular, 8.5f, WHITE, x, y + 14, site.email());
            text(regular, 8.5f, WHITE, x, y + 24.5f, site.landline().isBlank() ? site.phone() : site.landline() + "  ·  " + site.phone());
            text(bold, 8.5f, WHITE, x, y + 38, "VAT No: " + site.vatNumber());
            text(bold, 8.5f, WHITE, x, y + 48.5f, "Company No: " + site.companyNumber());

            textRight(bold, 28, WHITE, RIGHT, 58, "SALES");
            textRight(bold, 19, ORANGE, RIGHT, 82, "VAT INVOICE");
            textRight(regular, 7.5f, rgb(0xB5, 0xB5, 0xB5), RIGHT, 100, "PAID  /  ORIGINAL  /  BUYER COPY");
            top = 158;
        }

        private void fields() throws IOException {
            float gap = 12;
            float w = (RIGHT - LEFT - 2 * gap) / 3;
            field("INVOICE NO.", invoice.label(), LEFT, w);
            field("INVOICE DATE (TIME OF SUPPLY)", DATE.format(invoice.issuedAt()), LEFT + w + gap, w);
            field("ORDER / REFERENCE NO.", order.reference(), LEFT + 2 * (w + gap), w);
            top += 44;
        }

        private void buyer() throws IOException {
            bar("BUYER DETAILS");
            field("FULL NAME / COMPANY", order.customer().name(), LEFT, RIGHT - LEFT);
            top += 34;
            Order.Address a = order.address();
            String address = String.join(", ", nonBlank(a.line1(), a.line2(), a.town(), a.postcode(),
                    a.export() || !Countries.UK.equals(a.country()) ? Countries.name(a.country()) : null));
            field("DELIVERY ADDRESS", address, LEFT, RIGHT - LEFT);
            top += 34;
            float half = (RIGHT - LEFT - 12) / 2;
            field("TELEPHONE", order.customer().phone(), LEFT, half);
            field("EMAIL", order.customer().email(), LEFT + half + 12, half);
            top += 44;
        }

        private void items() throws IOException {
            bar("ITEMS");
            itemsHeading();
            int n = 0;
            for (InvoiceAmounts.Line line : amounts.lines()) {
                n++;
                List<String> desc = wrap(regular, 8.5f, line.description(), COL_REF - COL_DESC - 10);
                List<String> ref = wrap(regular, 8, line.reference() == null ? "" : line.reference(), COL_QTY - 30 - COL_REF);
                float h = Math.max(22, Math.max(desc.size(), ref.size()) * 10.5f + 11);
                if (top + h > H - BOTTOM) {
                    newPage();
                    continuedHeading();
                    itemsHeading();
                }
                float y = top + 14;
                text(bold, 8.5f, ORANGE, COL_NUM, y, Integer.toString(n));
                for (int i = 0; i < desc.size(); i++) {
                    text(regular, 8.5f, BLACK, COL_DESC, y + i * 10.5f, desc.get(i));
                }
                for (int i = 0; i < ref.size(); i++) {
                    text(regular, 8, GREY, COL_REF, y + i * 10.5f, ref.get(i));
                }
                textRight(regular, 8.5f, BLACK, COL_QTY, y, Integer.toString(line.quantity()));
                textRight(regular, 8.5f, BLACK, COL_UNIT, y, money(line.unitPrice()));
                textRight(bold, 8.5f, BLACK, COL_TOTAL, y, money(line.total()));
                top += h;
                fill(RULE, LEFT, top - 0.5f, RIGHT - LEFT, 0.5f);
            }
            String delivery = order.delivery().label() == null || order.delivery().label().isBlank() ? "Delivery" : order.delivery().label();
            top += 13;
            text(regular, 7.5f, GREY, LEFT, top, order.address().export()
                    ? "Delivery: " + delivery + ". Zero-rated export: no UK VAT. Any import duty and taxes are paid by the buyer."
                    : "Delivery: " + delivery + ". Prices on this invoice are before VAT; VAT at 20% is added in the totals.");
            top += 16;
        }

        private void itemsHeading() throws IOException {
            fill(LIGHT, LEFT, top, RIGHT - LEFT, 18);
            float y = top + 11.5f;
            text(bold, 6.5f, BLACK, COL_NUM, y, "#");
            text(bold, 6.5f, BLACK, COL_DESC, y, "DESCRIPTION");
            text(bold, 6.5f, BLACK, COL_REF, y, "PART / REF NO.");
            textRight(bold, 6.5f, BLACK, COL_QTY, y, "QTY");
            textRight(bold, 6.5f, BLACK, COL_UNIT, y, "UNIT PRICE");
            textRight(bold, 6.5f, BLACK, COL_TOTAL, y, "TOTAL");
            top += 18;
        }

        private void continuedHeading() throws IOException {
            fill(BLACK, 0, 0, W, 34);
            fill(ORANGE, 0, 34, W, 4);
            text(bold, 10, WHITE, LEFT, 22, "VAT INVOICE " + invoice.label() + " (continued)");
            textRight(regular, 8.5f, WHITE, RIGHT, 22, "Order " + order.reference());
            top = 56;
        }

        private void totals() throws IOException {
            float needed = 190;
            if (top + needed > H - BOTTOM) {
                newPage();
                continuedHeading();
            }
            float start = top + 4;
            float colW = (RIGHT - LEFT - 16) / 2;
            float rightX = LEFT + colW + 16;

            // Payment, on the left.
            top = start;
            bar("PAYMENT", LEFT, colW);
            Order.Payment p = order.payment();
            boolean paypal = p != null && p.captureId() != null;
            float boxTop = top;
            float boxH = 82;
            fill(BLACK, LEFT, boxTop, colW, boxH);
            fill(ORANGE, LEFT, boxTop, 4, boxH);
            float x = LEFT + 16;
            text(bold, 7, ORANGE, x, boxTop + 17, "PAID IN FULL");
            row(x, boxTop + 34, colW - 28, "METHOD", paypal ? "PayPal (account or card)" : "Paid to SpeedyFix directly");
            Instant paidAt = paypal && p.paidAt() != null ? p.paidAt() : invoice.issuedAt();
            row(x, boxTop + 50, colW - 28, "PAID ON", DATE_TIME.format(paidAt));
            if (paypal) {
                row(x, boxTop + 66, colW - 28, "TRANSACTION", p.captureId());
            }

            // Amounts, on the right.
            top = start;
            bar("AMOUNT", rightX, colW);
            float y = top;
            amount(rightX, colW, y, "Subtotal (before VAT)", money(amounts.subtotal()), false);
            amount(rightX, colW, y += 22, "Delivery / Shipping", money(amounts.delivery()), false);
            amount(rightX, colW, y += 22, order.address().export() ? "VAT 0% (export)" : "VAT 20% (16.67% of price)", money(amounts.vat()), false);
            amount(rightX, colW, y += 22, "Total", money(amounts.total()), true);
            amount(rightX, colW, y += 22, "Amount Paid", money(amounts.total()), false);
            y += 26;
            fill(ORANGE, rightX, y, colW, 26);
            text(bold, 10, BLACK, rightX + 10, y + 17, "BALANCE DUE");
            fill(PEACH, rightX + colW - 110, y + 3, 107, 20);
            textRight(bold, 10, BLACK, RIGHT - 8, y + 17, money(BigDecimal.ZERO));
            top = y + 40;
        }

        private void footer() throws IOException {
            fill(ORANGE, 0, H - 38, W, 3);
            fill(BLACK, 0, H - 35, W, 35);
            text(bold, 9, WHITE, LEFT, H - 14, "THANK YOU FOR YOUR BUSINESS");
            textRight(regular, 7.5f, rgb(0xB5, 0xB5, 0xB5), RIGHT, H - 14,
                    site.name() + "  |  VAT " + site.vatNumber() + "  |  Co. No. " + site.companyNumber());
        }

        /** An orange label and a peach box holding the value, its top at the cursor. */
        private void field(String label, String value, float x, float w) throws IOException {
            text(bold, 6.5f, ORANGE, x, top + 6, label);
            fill(PEACH, x, top + 10, w, 20);
            fill(ORANGE, x, top + 10, 3, 20);
            text(regular, 9, BLACK, x + 9, top + 23.5f, fit(regular, 9, value, w - 14));
        }

        /** A black section bar with an orange tab, across the page or one column. */
        private void bar(String title) throws IOException {
            bar(title, LEFT, RIGHT - LEFT);
        }

        private void bar(String title, float x, float w) throws IOException {
            fill(BLACK, x, top, w, 17);
            fill(ORANGE, x, top, 5, 17);
            text(bold, 8.5f, WHITE, x + 14, top + 12, title);
            top += 25;
        }

        private void row(float x, float y, float w, String label, String value) throws IOException {
            text(bold, 6.5f, rgb(0xB5, 0xB5, 0xB5), x, y, label);
            textRight(bold, 8.5f, WHITE, x + w, y, fit(bold, 8.5f, value, w - 60));
        }

        private void amount(float x, float w, float y, String label, String value, boolean strong) throws IOException {
            text(strong ? bold : regular, 9, BLACK, x + 10, y + 14, label);
            text(regular, 9, GREY, x + w - 124, y + 14, "£");
            fill(PEACH, x + w - 110, y + 2, 107, 18);
            textRight(strong ? bold : regular, 9, BLACK, x + w - 8, y + 14, value.substring(1));
            fill(RULE, x, y + 21.5f, w, 0.5f);
        }

        // Drawing helpers: positions are measured from the top of the page.

        private void fill(float[] color, float x, float fromTop, float w, float h) throws IOException {
            cs.setNonStrokingColor(color[0], color[1], color[2]);
            cs.addRect(x, H - fromTop - h, w, h);
            cs.fill();
        }

        private void text(PDType1Font font, float size, float[] color, float x, float baselineFromTop, String s) throws IOException {
            String clean = clean(font, s);
            if (clean.isEmpty()) {
                return;
            }
            cs.beginText();
            cs.setFont(font, size);
            cs.setNonStrokingColor(color[0], color[1], color[2]);
            cs.newLineAtOffset(x, H - baselineFromTop);
            cs.showText(clean);
            cs.endText();
        }

        private void textRight(PDType1Font font, float size, float[] color, float right, float baselineFromTop, String s) throws IOException {
            text(font, size, color, right - width(font, size, s), baselineFromTop, s);
        }
    }

    // Text helpers, kept static so they can be tested.

    /** The address as up to three lines: "Tomo Industrial Estate, Packet Boat Lane" and so on. */
    static List<String> addressLines(String address) {
        String[] parts = address.split(",\\s*");
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String part : parts) {
            if (line.length() > 0 && line.length() + part.length() > 34) {
                lines.add(line.toString());
                line.setLength(0);
            }
            line.append(line.length() > 0 ? ", " : "").append(part);
        }
        if (line.length() > 0) {
            lines.add(line.toString());
        }
        return lines;
    }

    /** "£1,234.50". */
    static String money(BigDecimal amount) {
        return "£" + String.format(Locale.UK, "%,.2f", amount);
    }

    private static List<String> nonBlank(String... parts) {
        List<String> out = new ArrayList<>();
        for (String p : parts) {
            if (p != null && !p.isBlank()) {
                out.add(p.trim());
            }
        }
        return out;
    }

    /** Letters with no accent to drop, as they're written without the stroke. */
    private static final Map<String, String> LETTERS = Map.of("Ł", "L", "ł", "l", "Đ", "D", "đ", "d", "ı", "i", "Ø", "O", "ø", "o");

    /** Only characters the font can show; others lose their accents, or become "?". Line breaks become spaces. */
    static String clean(PDType1Font font, String s) {
        if (s == null) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        s.codePoints().forEach(cp -> {
            String ch = Character.isWhitespace(cp) || Character.isISOControl(cp) ? " " : new String(Character.toChars(cp));
            if (shows(font, ch)) {
                out.append(ch);
            } else if (LETTERS.containsKey(ch)) {
                out.append(LETTERS.get(ch));
            } else {
                String plain = Normalizer.normalize(ch, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
                out.append(!plain.isEmpty() && shows(font, plain) ? plain : "?");
            }
        });
        return out.toString().replaceAll(" {2,}", " ").trim();
    }

    private static boolean shows(PDType1Font font, String ch) {
        try {
            font.encode(ch);
            return true;
        } catch (IOException | IllegalArgumentException e) {
            return false;
        }
    }

    static float width(PDType1Font font, float size, String s) {
        try {
            return font.getStringWidth(clean(font, s)) / 1000 * size;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** The text in lines no wider than max, breaking between words (or inside a word too long for a line). */
    static List<String> wrap(PDType1Font font, float size, String s, float max) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : clean(font, s).split(" ")) {
            String candidate = line.length() == 0 ? word : line + " " + word;
            if (width(font, size, candidate) <= max) {
                line.setLength(0);
                line.append(candidate);
                continue;
            }
            if (line.length() > 0) {
                lines.add(line.toString());
                line.setLength(0);
            }
            while (width(font, size, word) > max && word.length() > 1) {
                int cut = word.length() - 1;
                while (cut > 1 && width(font, size, word.substring(0, cut)) > max) {
                    cut--;
                }
                lines.add(word.substring(0, cut));
                word = word.substring(cut);
            }
            line.append(word);
        }
        if (line.length() > 0) {
            lines.add(line.toString());
        }
        return lines.isEmpty() ? List.of("") : lines;
    }

    /** The text cut to fit on one line, ending in "...". */
    static String fit(PDType1Font font, float size, String s, float max) {
        String clean = clean(font, s);
        if (width(font, size, clean) <= max) {
            return clean;
        }
        while (clean.length() > 1 && width(font, size, clean + "...") > max) {
            clean = clean.substring(0, clean.length() - 1);
        }
        return clean.trim() + "...";
    }

    private static float[] rgb(int r, int g, int b) {
        return new float[] {r / 255f, g / 255f, b / 255f};
    }
}
