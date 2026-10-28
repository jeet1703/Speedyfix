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

    /** Takes sold items out of stock (a negative quantity puts them back). Returns false, changing nothing, if there aren't enough. */
    boolean reduceStock(String id, int quantity);

    List<Slide> slides();

    List<Feature> features();

    List<MenuGroup> menu();
}
