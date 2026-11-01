package com.speedyfixautosales.store.catalog;

import java.util.List;

/** A column in the Products mega menu. */
public record MenuGroup(String title, List<MenuLink> links) {

    /** imageUrl is the category's photo, shown when the link is hovered; null for links that aren't a category. */
    public record MenuLink(String label, String href, String imageUrl) {

        public MenuLink(String label, String href) {
            this(label, href, null);
        }

        /** The category a "/products?category=…" link opens, or null. */
        public String categoryId() {
            int i = href.indexOf("category=");
            if (i < 0) {
                return null;
            }
            String rest = href.substring(i + "category=".length());
            int end = rest.indexOf('&');
            return end < 0 ? rest : rest.substring(0, end);
        }
    }
}
