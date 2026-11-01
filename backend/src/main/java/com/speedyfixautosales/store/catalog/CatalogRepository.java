package com.speedyfixautosales.store.catalog;

import java.util.List;
import java.util.Optional;

/**
 * Where the catalogue lives: InMemoryCatalogRepository (sample data, no database) or JdbcCatalogRepository
 * (PostgreSQL, the "postgres" profile).
 */
public interface CatalogRepository {

    List<Category> categories();

    Optional<Category> category(String id);

    /** Every part, drafts and ended listings included. Shopper-facing code uses liveProducts(). */
    List<Product> products();

    default List<Product> liveProducts() {
        return products().stream().filter(Product::live).toList();
    }

    Optional<Product> product(String id);

    /** Adds or replaces a part (admin). */
    void save(Product product);

    /** The sample photo a seeded part started with, used again if staff remove every uploaded photo. */
    Optional<String> sampleImage(String id);

    /** Everything about a part that only its own page needs: all its photos, eBay's item specifics and description. */
    record Extras(List<ProductDetail.Photo> photos, List<ProductDetail.Specific> specifics, String description,
                  String descriptionHtml) {
    }

    default Extras extras(Product product) {
        return new Extras(List.of(new ProductDetail.Photo(product.imageUrl(), product.imageUrl())), List.of(), null,
                descriptionHtml(product.id()).orElse(null));
    }

    /** The description staff wrote as HTML, if any. */
    Optional<String> descriptionHtml(String id);

    /** Null removes it. */
    void saveDescriptionHtml(String id, String html);

    /** Takes sold items out of stock (a negative quantity puts them back). Returns false, changing nothing, if there aren't enough. */
    boolean reduceStock(String id, int quantity);

    List<Slide> slides();

    List<Feature> features();

    List<MenuGroup> menu();
}
