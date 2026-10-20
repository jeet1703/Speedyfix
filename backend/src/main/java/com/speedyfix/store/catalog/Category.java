package com.speedyfix.store.catalog;

public record Category(String id, String name, String imageUrl, boolean popular, int sortOrder) {
}
