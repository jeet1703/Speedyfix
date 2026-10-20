package com.speedyfix.store.catalog;

public record Slide(
        String id,
        String kicker,
        String title,
        String text,
        String imageUrl,
        String primaryLabel,
        String primaryHref,
        String secondaryLabel,
        String secondaryHref) {
}
