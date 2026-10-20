package com.speedyfix.store.catalog;

import java.util.List;

/** A column in the Products mega menu. */
public record MenuGroup(String title, List<MenuLink> links) {

    public record MenuLink(String label, String href) {
    }
}
