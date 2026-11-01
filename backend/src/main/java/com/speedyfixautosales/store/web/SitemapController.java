package com.speedyfixautosales.store.web;

import com.speedyfixautosales.store.catalog.CatalogRepository;
import com.speedyfixautosales.store.catalog.Product;
import java.time.Duration;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.HtmlUtils;

/**
 * The sitemap search engines read: the shop's pages, every category with stock and every live part, at the shop's
 * public address (SITE_URL, like https://www.speedyfixautosales.com). The shop's robots.txt points here. Checkout,
 * basket, account and admin pages are left out, as robots.txt keeps crawlers away from them.
 */
@RestController
public class SitemapController {

    private static final Set<String> PAGES = Set.of("/", "/products", "/request-a-part", "/about", "/delivery", "/faq", "/contact",
            "/terms", "/privacy");

    private final CatalogRepository catalog;
    private final String siteUrl;

    public SitemapController(CatalogRepository catalog, @Value("${site.public-url:}") String siteUrl) {
        this.catalog = catalog;
        this.siteUrl = siteUrl.replaceAll("/+$", "");
    }

    @GetMapping(value = "/sitemap.xml", produces = MediaType.APPLICATION_XML_VALUE)
    public ResponseEntity<String> sitemap() {
        StringBuilder xml = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n");
        PAGES.stream().sorted().forEach(p -> url(xml, p, null));
        Set<String> stocked = catalog.liveProducts().stream().map(Product::categoryId).collect(Collectors.toSet());
        catalog.categories().stream().filter(c -> stocked.contains(c.id())).forEach(c -> url(xml, "/products?category=" + c.id(), null));
        catalog.liveProducts().forEach(p -> url(xml, "/products/" + p.id(), p.addedOn() == null ? null : p.addedOn().toString()));
        xml.append("</urlset>\n");
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePublic())
                .contentType(MediaType.APPLICATION_XML)
                .body(xml.toString());
    }

    private void url(StringBuilder xml, String path, String lastModified) {
        xml.append("  <url><loc>").append(HtmlUtils.htmlEscape(siteUrl + path)).append("</loc>");
        if (lastModified != null) {
            xml.append("<lastmod>").append(lastModified).append("</lastmod>");
        }
        xml.append("</url>\n");
    }
}
