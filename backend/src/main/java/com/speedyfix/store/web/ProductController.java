package com.speedyfix.store.web;

import com.speedyfix.store.catalog.CatalogService;
import com.speedyfix.store.catalog.Condition;
import com.speedyfix.store.catalog.ProductDetail;
import com.speedyfix.store.catalog.ProductView;
import com.speedyfix.store.catalog.ShopQuery;
import com.speedyfix.store.catalog.ShopResult;
import com.speedyfix.store.common.BadRequestException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class ProductController {

    private final CatalogService catalog;

    public ProductController(CatalogService catalog) {
        this.catalog = catalog;
    }

    /** /api/products?section=best-selling|latest, or /api/products?category=engines */
    @GetMapping("/products")
    public List<ProductView> products(@RequestParam(required = false) String section,
                                      @RequestParam(required = false) String category,
                                      @RequestParam(required = false) String vehicleId) {
        if (category != null && !category.isBlank()) {
            return catalog.byCategory(category, vehicleId);
        }
        if ("latest".equals(section)) {
            return catalog.latest(vehicleId);
        }
        if (section == null || "best-selling".equals(section)) {
            return catalog.bestSelling(vehicleId);
        }
        throw new BadRequestException("Unknown section " + section + ". Use best-selling or latest.");
    }

    @GetMapping("/products/{id}")
    public ProductDetail product(@PathVariable String id, @RequestParam(required = false) String vehicleId) {
        return catalog.detail(id, vehicleId);
    }

    /**
     * The shop page: /api/shop?category=&q=&condition=USED,NEW&minPrice=&maxPrice=&fitsOnly=&sort=&page=&ids=
     * sort is best-selling (default), newest, price-low or price-high.
     */
    @GetMapping("/shop")
    public ShopResult shop(@RequestParam(required = false) String category,
                           @RequestParam(required = false) String q,
                           @RequestParam(required = false) List<String> condition,
                           @RequestParam(required = false) BigDecimal minPrice,
                           @RequestParam(required = false) BigDecimal maxPrice,
                           @RequestParam(defaultValue = "false") boolean fitsOnly,
                           @RequestParam(required = false) String vehicleId,
                           @RequestParam(defaultValue = "best-selling") String sort,
                           @RequestParam(defaultValue = "1") int page,
                           @RequestParam(defaultValue = "9") int pageSize,
                           @RequestParam(required = false) List<String> ids) {
        Set<Condition> conditions = condition == null ? Set.of() : condition.stream()
                .map(c -> parse(Condition.class, c, "condition"))
                .collect(Collectors.toSet());
        ShopQuery.Sort order = parse(ShopQuery.Sort.class, sort.replace('-', '_'), "sort");
        return catalog.shop(new ShopQuery(category, q, conditions, minPrice, maxPrice, fitsOnly, vehicleId, order, page,
                pageSize, ids));
    }

    private static <E extends Enum<E>> E parse(Class<E> type, String value, String name) {
        try {
            return Enum.valueOf(type, value.trim().toUpperCase(Locale.UK));
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Unknown " + name + " " + value + ".");
        }
    }

    @GetMapping("/search")
    public List<ProductView> search(@RequestParam String q,
                                    @RequestParam(defaultValue = "8") int limit,
                                    @RequestParam(required = false) String vehicleId) {
        return catalog.search(q, limit, vehicleId);
    }
}
