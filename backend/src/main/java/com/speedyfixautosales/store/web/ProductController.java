package com.speedyfixautosales.store.web;

import com.speedyfixautosales.store.catalog.CatalogService;
import com.speedyfixautosales.store.catalog.Condition;
import com.speedyfixautosales.store.catalog.ProductDetail;
import com.speedyfixautosales.store.catalog.ProductView;
import com.speedyfixautosales.store.catalog.ShopQuery;
import com.speedyfixautosales.store.catalog.ShopResult;
import com.speedyfixautosales.store.common.BadRequestException;
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
     * The shop page: /api/shop?category=&q=&condition=USED,NEW&minPrice=&maxPrice=&fitsOnly=&brand=&subType=&refinement=&sort=&page=&ids=
     * brand, subType and refinement repeat for several values. sort is best-selling (default), newest, price-low or price-high.
     */
    @GetMapping("/shop")
    public ShopResult shop(@RequestParam(required = false) String category,
                           @RequestParam(required = false) String q,
                           @RequestParam(required = false) List<String> condition,
                           @RequestParam(required = false) BigDecimal minPrice,
                           @RequestParam(required = false) BigDecimal maxPrice,
                           @RequestParam(defaultValue = "false") boolean fitsOnly,
                           @RequestParam(required = false) String vehicleId,
                           @RequestParam(required = false) List<String> brand,
                           @RequestParam(required = false) List<String> subType,
                           @RequestParam(required = false) List<String> refinement,
                           @RequestParam(defaultValue = "best-selling") String sort,
                           @RequestParam(defaultValue = "1") int page,
                           @RequestParam(defaultValue = "24") int pageSize,
                           @RequestParam(required = false) List<String> ids) {
        Set<Condition> conditions = condition == null ? Set.of() : condition.stream()
                .map(c -> parse(Condition.class, c, "condition"))
                .collect(Collectors.toSet());
        ShopQuery.Sort order = parse(ShopQuery.Sort.class, sort.replace('-', '_'), "sort");
        return catalog.shop(new ShopQuery(category, q, conditions, minPrice, maxPrice, fitsOnly, vehicleId, set(brand),
                set(subType), set(refinement), order, page, pageSize, ids));
    }

    private static Set<String> set(List<String> values) {
        return values == null ? Set.of() : values.stream().filter(v -> v != null && !v.isBlank()).collect(Collectors.toSet());
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
