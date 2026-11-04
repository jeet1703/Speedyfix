package com.speedyfixautosales.store.invoice;

import com.speedyfixautosales.store.basket.BasketView;
import com.speedyfixautosales.store.order.Order;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * An order's amounts as a VAT invoice shows them. Shop prices include VAT at 20%, so each price before VAT is the
 * price divided by 1.2, to the penny; the VAT is what's left of the total, so the invoice always adds up to exactly
 * what the buyer paid.
 */
public record InvoiceAmounts(List<Line> lines, BigDecimal subtotal, BigDecimal delivery, BigDecimal vat, BigDecimal total) {

    public static final BigDecimal VAT_RATE = new BigDecimal("0.20");

    /** One line, before VAT. */
    public record Line(String description, String reference, int quantity, BigDecimal unitPrice, BigDecimal total) {
    }

    public static InvoiceAmounts of(Order order) {
        if (order.address().export()) {
            return export(order);
        }
        BigDecimal subtotal = BigDecimal.ZERO;
        List<Line> lines = new java.util.ArrayList<>();
        for (BasketView.Line l : order.lines()) {
            BigDecimal unit = exVat(l.unitPrice());
            BigDecimal lineTotal = unit.multiply(BigDecimal.valueOf(l.quantity()));
            lines.add(new Line(l.name(), l.reference(), l.quantity(), unit, lineTotal));
            subtotal = subtotal.add(lineTotal);
        }
        BigDecimal delivery = exVat(order.delivery().price());
        BigDecimal total = order.total().setScale(2, RoundingMode.HALF_UP);
        return new InvoiceAmounts(List.copyOf(lines), subtotal, delivery, total.subtract(subtotal).subtract(delivery), total);
    }

    /** A zero-rated export: the order's prices are already without VAT, and the VAT is nothing. */
    private static InvoiceAmounts export(Order order) {
        List<Line> lines = order.lines().stream()
                .map(l -> new Line(l.name(), l.reference(), l.quantity(), l.unitPrice(), l.lineTotal())).toList();
        BigDecimal subtotal = lines.stream().map(Line::total).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal delivery = order.delivery().price().setScale(2, RoundingMode.HALF_UP);
        return new InvoiceAmounts(lines, subtotal, delivery, BigDecimal.ZERO.setScale(2), order.total().setScale(2, RoundingMode.HALF_UP));
    }

    static BigDecimal exVat(BigDecimal withVat) {
        return withVat.divide(BigDecimal.ONE.add(VAT_RATE), 2, RoundingMode.HALF_UP);
    }
}
