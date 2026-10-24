package com.speedyfixautosales.store.catalog;

import com.speedyfixautosales.store.common.NotFoundException;
import com.speedyfixautosales.store.vehicle.Vehicle;
import com.speedyfixautosales.store.vehicle.VehicleService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Predicate;
import org.springframework.stereotype.Service;

@Service
public class CatalogService {

    private static final int SECTION_SIZE = 4;

    private final CatalogRepository repository;
    private final VehicleService vehicles;

    public CatalogService(CatalogRepository repository, VehicleService vehicles) {
        this.repository = repository;
        this.vehicles = vehicles;
    }

    public List<Category> popularCategories() {
        return repository.categories().stream().filter(Category::popular).toList();
    }

    public List<ProductView> bestSelling(String vehicleId) {
        Optional<Vehicle> vehicle = vehicles.findById(vehicleId);
        return repository.liveProducts().stream()
                .filter(Product::bestSeller)
                .limit(SECTION_SIZE)
                .map(p -> view(p, vehicle))
                .toList();
    }

    public List<ProductView> latest(String vehicleId) {
        Optional<Vehicle> vehicle = vehicles.findById(vehicleId);
        return repository.liveProducts().stream()
                .sorted(Comparator.comparing(Product::addedOn).reversed())
                .limit(SECTION_SIZE)
                .map(p -> view(p, vehicle))
                .toList();
    }

    public List<ProductView> byCategory(String categoryId, String vehicleId) {
        repository.category(categoryId).orElseThrow(() -> new NotFoundException("No category called " + categoryId + "."));
        Optional<Vehicle> vehicle = vehicles.findById(vehicleId);
        return repository.liveProducts().stream()
                .filter(p -> p.categoryId().equals(categoryId))
                .map(p -> view(p, vehicle))
                .toList();
    }

    public ProductView product(String id, String vehicleId) {
        return view(getProduct(id), vehicles.findById(vehicleId));
    }

    public ShopResult shop(ShopQuery query) {
        Optional<Vehicle> vehicle = vehicles.findById(query.vehicleId());
        boolean byCategory = query.category() != null && !query.category().isBlank();
        if (byCategory) {
            repository.category(query.category())
                    .orElseThrow(() -> new NotFoundException("No category called " + query.category() + "."));
        }
        Predicate<Product> filter = p -> true;
        if (!query.ids().isEmpty()) {
            filter = filter.and(p -> query.ids().contains(p.id()));
        }
        if (byCategory) {
            filter = filter.and(p -> p.categoryId().equals(query.category()));
        }
        if (query.q() != null && !query.q().isBlank()) {
            List<String> matches = search(query.q(), Integer.MAX_VALUE, null).stream().map(ProductView::id).toList();
            filter = filter.and(p -> matches.contains(p.id()));
        }
        if (!query.conditions().isEmpty()) {
            filter = filter.and(p -> query.conditions().contains(p.condition()));
        }
        if (query.minPrice() != null) {
            filter = filter.and(p -> p.price().compareTo(query.minPrice()) >= 0);
        }
        if (query.maxPrice() != null) {
            filter = filter.and(p -> p.price().compareTo(query.maxPrice()) <= 0);
        }
        if (query.fitsOnly() && vehicle.isPresent()) {
            filter = filter.and(p -> p.fitsVehicleIds().contains(vehicle.get().id()));
        }
        List<Product> matching = repository.liveProducts().stream().filter(filter).sorted(order(query.sort())).toList();

        int pageCount = Math.max(1, (matching.size() + query.pageSize() - 1) / query.pageSize());
        int page = Math.min(query.page(), pageCount);
        List<ProductView> items = matching.stream()
                .skip((long) (page - 1) * query.pageSize())
                .limit(query.pageSize())
                .map(p -> view(p, vehicle))
                .toList();
        List<ShopResult.CategoryCount> counts = repository.categories().stream()
                .map(c -> new ShopResult.CategoryCount(c.id(), c.name(),
                        repository.liveProducts().stream().filter(p -> p.categoryId().equals(c.id())).count()))
                .toList();
        return new ShopResult(items, matching.size(), page, query.pageSize(), pageCount, counts);
    }

    public ProductDetail detail(String id, String vehicleId) {
        Product p = getProduct(id);
        Optional<Vehicle> vehicle = vehicles.findById(vehicleId);
        List<String> fits = p.fitsVehicleIds().stream()
                .map(vehicles::findById)
                .flatMap(Optional::stream)
                .map(v -> v.displayName() + ", " + v.engine())
                .toList();
        // Related: same category first, then parts for the same vehicles or engine code.
        List<ProductView> related = repository.liveProducts().stream()
                .filter(o -> !o.id().equals(p.id()))
                .filter(o -> o.categoryId().equals(p.categoryId()) || sharesFitment(o, p))
                .sorted(Comparator.comparing((Product o) -> !o.categoryId().equals(p.categoryId())))
                .limit(4)
                .map(o -> view(o, vehicle))
                .toList();
        BigDecimal exVat = p.price().multiply(BigDecimal.valueOf(5)).divide(BigDecimal.valueOf(6), 2, RoundingMode.HALF_UP);
        return new ProductDetail(view(p, vehicle), p.oeNumber(), p.engineCode(), exVat, description(p), fits, related);
    }

    private static boolean sharesFitment(Product a, Product b) {
        boolean sameEngine = a.engineCode() != null && a.engineCode().equals(b.engineCode());
        return sameEngine || a.fitsVehicleIds().stream().anyMatch(b.fitsVehicleIds()::contains);
    }

    /** Placeholder copy built from the part's data until the client supplies a description for each part. */
    private static String description(Product p) {
        String grade = p.grade() == null ? "" : " Condition grade " + p.grade() + ".";
        return switch (p.condition()) {
            case USED -> "Used part, removed from a donor vehicle and tested before listing." + grade;
            case REMANUFACTURED -> "Remanufactured part." + grade;
            case NEW -> "New part." + grade;
        };
    }

    private static Comparator<Product> order(ShopQuery.Sort sort) {
        return switch (sort) {
            case NEWEST -> Comparator.comparing(Product::addedOn).reversed();
            case PRICE_LOW -> Comparator.comparing(Product::price);
            case PRICE_HIGH -> Comparator.comparing(Product::price).reversed();
            case BEST_SELLING -> Comparator.comparing((Product p) -> !p.bestSeller())
                    .thenComparing(Comparator.comparing(Product::addedOn).reversed());
        };
    }

    /** A part shoppers can see and buy. Drafts count as not found. */
    public Product getProduct(String id) {
        return repository.product(id).filter(Product::live)
                .orElseThrow(() -> new NotFoundException("No part with id " + id + "."));
    }

    /** Any part, drafts included: a basket may still hold a part staff have since taken off sale. */
    public Product findAny(String id) {
        return repository.product(id).orElseThrow(() -> new NotFoundException("No part with id " + id + "."));
    }

    /**
     * Matches every word of the query against the name, OE number, engine code, category and the
     * vehicles a part fits. Part numbers also match with spaces and dashes removed ("C1B119D629A2H").
     */
    public List<ProductView> search(String query, int limit, String vehicleId) {
        String q = query == null ? "" : query.trim().toLowerCase(Locale.UK);
        if (q.length() < 2) {
            return List.of();
        }
        String[] words = q.split("\\s+");
        String compactQuery = compact(q);
        Optional<Vehicle> vehicle = vehicles.findById(vehicleId);
        return repository.liveProducts().stream()
                .filter(p -> {
                    String haystack = haystack(p);
                    boolean allWords = java.util.Arrays.stream(words).allMatch(haystack::contains);
                    return allWords || (compactQuery.length() > 3 && compact(haystack).contains(compactQuery));
                })
                .limit(Math.max(1, Math.min(limit, 20)))
                .map(p -> view(p, vehicle))
                .toList();
    }

    public long countFitting(String vehicleId) {
        return repository.liveProducts().stream().filter(p -> p.fitsVehicleIds().contains(vehicleId)).count();
    }

    public ProductView view(Product p, Optional<Vehicle> vehicle) {
        String categoryName = repository.category(p.categoryId()).map(Category::name).orElse("");
        return new ProductView(p.id(), p.name(), p.categoryId(), categoryName, p.condition().label(), p.grade(),
                p.price(), p.reference(), p.imageUrl(), p.shipping(), p.stock(), p.stock() > 0, fit(p, vehicle));
    }

    static FitStatus fit(Product p, Optional<Vehicle> vehicle) {
        if (vehicle.isEmpty()) {
            return FitStatus.NO_VEHICLE;
        }
        if (p.fitsVehicleIds().contains(vehicle.get().id())) {
            return FitStatus.FITS;
        }
        return p.fitsVehicleIds().isEmpty() ? FitStatus.UNCONFIRMED : FitStatus.DOES_NOT_FIT;
    }

    private String haystack(Product p) {
        StringBuilder sb = new StringBuilder(p.name()).append(' ');
        if (p.oeNumber() != null) sb.append(p.oeNumber()).append(' ');
        p.crossReferences().forEach(ref -> sb.append(ref).append(' '));
        if (p.engineCode() != null) sb.append(p.engineCode()).append(' ');
        repository.category(p.categoryId()).ifPresent(c -> sb.append(c.name()).append(' '));
        for (String id : p.fitsVehicleIds()) {
            vehicles.findById(id).ifPresent(v -> sb.append(v.make()).append(' ').append(v.model()).append(' '));
        }
        return sb.toString().toLowerCase(Locale.UK);
    }

    /** Removes spaces and dashes, so "C1B1-19D629-A2H" compares equal to "C1B119D629A2H". */
    public static String compact(String s) {
        return s.replaceAll("[\\s-]", "");
    }
}
