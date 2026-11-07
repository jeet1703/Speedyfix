package com.speedyfixautosales.store.admin;

import com.speedyfixautosales.store.account.CustomerService;
import com.speedyfixautosales.store.catalog.Brands;
import com.speedyfixautosales.store.catalog.CatalogRepository;
import com.speedyfixautosales.store.catalog.CatalogService;
import com.speedyfixautosales.store.catalog.Category;
import com.speedyfixautosales.store.catalog.Condition;
import com.speedyfixautosales.store.catalog.Images;
import com.speedyfixautosales.store.catalog.MenuGroup;
import com.speedyfixautosales.store.catalog.Product;
import com.speedyfixautosales.store.catalog.ProductPhotoStore;
import com.speedyfixautosales.store.catalog.ProductStatus;
import com.speedyfixautosales.store.catalog.Shipping;
import com.speedyfixautosales.store.common.BadRequestException;
import com.speedyfixautosales.store.common.NotFoundException;
import com.speedyfixautosales.store.order.Order;
import com.speedyfixautosales.store.order.OrderService;
import com.speedyfixautosales.store.partrequest.PartRequest;
import com.speedyfixautosales.store.partrequest.PartRequestService;
import com.speedyfixautosales.store.photo.PhotoUrls;
import com.speedyfixautosales.store.vehicle.LookupLog;
import com.speedyfixautosales.store.vehicle.Vehicle;
import com.speedyfixautosales.store.shipping.ParcelSizes;
import com.speedyfixautosales.store.vehicle.VehicleService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * The catalogue as staff see it: every listing with its place in the workflow, adding and editing, and the
 * Seller Hub-style overview (tasks, sales, listings).
 */
@Service
public class AdminCatalogService {

    static final int MAX_DESCRIPTION_HTML = 100_000;

    static final int MAX_REFINEMENTS = 5;
    static final int NO_MATCH_LIST_SIZE = 10;
    private static final String CATEGORY_LINK = "/products?category=";
    private static final ZoneId UK = ZoneId.of("Europe/London");
    private static final SecureRandom RANDOM = new SecureRandom();
    /** Where ProductPhotoController serves the photos staff upload. */
    private static final String UPLOADED_PHOTO_PATH = "/api/product-photos/";

    /** Where a part is in the listing workflow, most urgent first. A part shows the first that applies. */
    public enum WorkStatus {
        NEEDS_FITMENT, MISSING_PHOTO, DRAFT, LIVE
    }

    /** LOW: suggested by AI (FitmentSuggester) and not yet checked by anyone. */
    public enum Confidence {
        HIGH, MEDIUM, LOW
    }

    public record Row(String id, String name, String categoryName, String partNumber, String engineCode, String brand,
                      String subType, BigDecimal price, int stock, String imageUrl, boolean hasPhoto, ProductStatus status,
                      WorkStatus workStatus, int vehicles, LocalDate addedOn, Integer salePercent, BigDecimal sellingPrice) {
    }

    /** A vehicle a part fits, or might fit, and why we think so. */
    /** yearTo is set for a compatibility row (year is then its first year). */
    public record Fitment(String vehicleId, String vehicle, String engine, String engineCode, Integer year, Integer yearTo,
                          String source, Confidence confidence) {
    }

    public record CategoryOption(String id, String name, String group) {
    }

    public record Photo(String id, String url) {
    }

    /**
     * samplePhoto is true when imageUrl is the demo catalogue's stock photo rather than one staff uploaded.
     */
    public record Detail(String id, String name, String categoryId, Condition condition, String grade, BigDecimal price,
                         String partNumber, String engineCode, String brand, String subType, List<String> refinements,
                         Shipping shipping, int stock, boolean bestSeller, ProductStatus status, WorkStatus workStatus,
                         String imageUrl, boolean samplePhoto, List<Photo> photos, List<Photo> importedPhotos, List<Fitment> vehicles,
                         LocalDate addedOn, String descriptionHtml, Integer salePercent, BigDecimal sellingPrice,
                         BigDecimal weightKg, BigDecimal defaultWeightKg) {
    }

    /** vehicle is null for plates the data source didn't recognise. */
    public record NoMatch(String vehicle, long searches) {
    }

    /** The Overview page's to-do list, like Seller Hub's Tasks. */
    public record Tasks(long awaitingPayment, long awaitingDispatch, long newPartRequests, long needFitment,
                        long missingPhotos, long outOfStock) {
    }

    /**
     * Orders and takings over a period, cancelled orders left out. change is the percentage up or down on the
     * period just before it (the 31 days before the last 31, say); null when that period took nothing.
     */
    public record Sales(String period, long orders, BigDecimal total, Double change) {
    }

    /** One UK calendar day's takings, for the Overview's sales chart. */
    public record SalesDay(LocalDate date, long orders, BigDecimal total) {
    }

    public record OrderCounts(long all, long awaitingPayment, long awaitingDispatch, long dispatched, long cancelled) {
    }

    /** Shoppers who have signed in with Google: all of them, and those who first signed in this past week. */
    public record CustomerCounts(long total, long newThisWeek) {
    }

    /** active = live in the shop (in stock or not); outOfStock = live but none left. */
    public record Listings(long active, long drafts, long outOfStock) {
    }

    public record Dashboard(Tasks tasks, List<Sales> sales, List<SalesDay> dailySales, OrderCounts orders,
                            CustomerCounts customers, Listings listings, long needFitment, long missingPhotos,
                            long noMatchSearches, long lookupsThisMonth, String buyingTeamEmail,
                            List<Row> workQueue, List<NoMatch> noMatch) {
    }

    /** A staff plate check: the vehicle, the parts confirmed for it, and unconfirmed parts with its engine code. */
    public record PlateCheck(Vehicle vehicle, Fitment fitment, List<Row> confirmed, List<Row> sameEngineCode) {
    }

    private final CatalogRepository repository;
    private final ProductPhotoStore photos;
    private final VehicleService vehicles;
    private final LookupLog lookups;
    private final AdminSettings settings;
    private final OrderService orders;
    private final PartRequestService partRequests;
    private final CustomerService customers;
    private final PhotoUrls photoUrls;
    private final ParcelSizes parcelSizes;

    public AdminCatalogService(CatalogRepository repository, ProductPhotoStore photos, VehicleService vehicles,
                               LookupLog lookups, AdminSettings settings, OrderService orders,
                               PartRequestService partRequests, CustomerService customers, PhotoUrls photoUrls,
                               ParcelSizes parcelSizes) {
        this.photoUrls = photoUrls;
        this.parcelSizes = parcelSizes;
        this.repository = repository;
        this.photos = photos;
        this.vehicles = vehicles;
        this.lookups = lookups;
        this.settings = settings;
        this.orders = orders;
        this.partRequests = partRequests;
        this.customers = customers;
    }

    /** Every part, drafts included, most urgent first and then newest. */
    public List<Row> list() {
        return repository.products().stream()
                .map(this::row)
                .sorted(Comparator.comparing(Row::workStatus).thenComparing(Row::addedOn, Comparator.reverseOrder()))
                .toList();
    }

    /**
     * The categories to list a part in, grouped and ordered as in the shop's Products menu, so staff pick from the
     * same list shoppers browse. Categories the menu leaves out (Rebuild Kits while it's empty) come last, under Other.
     */
    /** Every brand a listing has, A to Z: the admin's brand choices, so a part is filed under a brand shoppers can filter by. */
    public List<String> brands() {
        return repository.products().stream()
                .map(Product::brand)
                .map(Brands::canonical)
                .filter(Objects::nonNull)
                .distinct()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }

    public List<CategoryOption> categories() {
        Map<String, Category> byId = new LinkedHashMap<>();
        repository.categories().forEach(c -> byId.put(c.id(), c));
        List<CategoryOption> options = new ArrayList<>();
        for (MenuGroup group : repository.menu()) {
            for (MenuGroup.MenuLink link : group.links()) {
                String id = link.href().startsWith(CATEGORY_LINK) ? link.href().substring(CATEGORY_LINK.length()) : null;
                Category c = id == null ? null : byId.remove(id);
                if (c != null) {
                    options.add(new CategoryOption(c.id(), c.name(), group.title()));
                }
            }
        }
        byId.values().forEach(c -> options.add(new CategoryOption(c.id(), c.name(), "Other")));
        return options;
    }

    public Detail get(String id) {
        return detail(find(id));
    }

    public Dashboard dashboard() {
        List<Row> rows = list();
        Instant weekAgo = Instant.now().minus(Duration.ofDays(7));
        Instant monthStart = LocalDate.now(UK).withDayOfMonth(1).atStartOfDay(UK).toInstant();
        long lookupsThisMonth = lookups.countSince(monthStart);
        List<NoMatch> noMatch = lookups.noMatchByVehicle(weekAgo).entrySet().stream()
                .limit(NO_MATCH_LIST_SIZE)
                .map(e -> new NoMatch(e.getKey() == null ? null : vehicles.findById(e.getKey()).map(AdminCatalogService::describe).orElse(null),
                        e.getValue()))
                .toList();
        // Ended listings aren't for sale, so they're not work to do.
        List<Row> open = rows.stream().filter(r -> r.status() != ProductStatus.ENDED).toList();
        long needFitment = open.stream().filter(r -> r.vehicles() == 0).count();
        long missingPhotos = open.stream().filter(r -> !r.hasPhoto()).count();
        long outOfStock = rows.stream().filter(r -> r.status() == ProductStatus.LIVE && r.stock() == 0).count();
        List<Order> all = orders.all();
        Tasks tasks = new Tasks(countOrders(all, Order.Status.AWAITING_PAYMENT), countOrders(all, Order.Status.AWAITING_DISPATCH),
                partRequests.all().stream().filter(r -> r.status() == PartRequest.Status.NEW).count(),
                needFitment, missingPhotos, outOfStock);
        LocalDate today = LocalDate.now(UK);
        Instant now = Instant.now();
        Instant todayStart = today.atStartOfDay(UK).toInstant();
        List<Sales> sales = List.of(sales("Today", all, todayStart, now),
                sales("Last 7 days", all, now.minus(Duration.ofDays(7)), now),
                sales("Last 31 days", all, now.minus(Duration.ofDays(31)), now),
                sales("Last 90 days", all, now.minus(Duration.ofDays(90)), now));
        List<SalesDay> daily = new ArrayList<>();
        for (int back = 30; back >= 0; back--) {
            LocalDate day = today.minusDays(back);
            List<Order> placed = taken(all, day.atStartOfDay(UK).toInstant(), day.plusDays(1).atStartOfDay(UK).toInstant());
            daily.add(new SalesDay(day, placed.size(), sum(placed)));
        }
        OrderCounts orderCounts = new OrderCounts(all.size(), countOrders(all, Order.Status.AWAITING_PAYMENT),
                countOrders(all, Order.Status.AWAITING_DISPATCH), countOrders(all, Order.Status.DISPATCHED),
                countOrders(all, Order.Status.CANCELLED));
        List<CustomerService.Customer> everyone = customers.all();
        CustomerCounts customerCounts = new CustomerCounts(everyone.size(),
                everyone.stream().filter(c -> c.joinedAt().isAfter(now.minus(Duration.ofDays(7)))).count());
        Listings listings = new Listings(rows.stream().filter(r -> r.status() == ProductStatus.LIVE).count(),
                rows.stream().filter(r -> r.status() == ProductStatus.DRAFT).count(), outOfStock);
        return new Dashboard(tasks, sales, daily, orderCounts, customerCounts, listings, needFitment, missingPhotos,
                lookups.noMatchSince(weekAgo), lookupsThisMonth, settings.get().buyingTeamEmail(), open, noMatch);
    }

    private static long countOrders(List<Order> all, Order.Status status) {
        return all.stream().filter(o -> o.status() == status).count();
    }

    /** Takings from `from` up to `to`, with the change on the same length of time just before. */
    private static Sales sales(String period, List<Order> all, Instant from, Instant to) {
        List<Order> placed = taken(all, from, to);
        BigDecimal total = sum(placed);
        BigDecimal before = sum(taken(all, from.minus(Duration.between(from, to)), from));
        Double change = before.signum() == 0 ? null
                : total.subtract(before).multiply(BigDecimal.valueOf(100)).divide(before, 1, RoundingMode.HALF_UP).doubleValue();
        return new Sales(period, placed.size(), total, change);
    }

    /** Orders placed in [from, to), cancelled ones left out. */
    private static List<Order> taken(List<Order> all, Instant from, Instant to) {
        return all.stream()
                .filter(o -> o.status() != Order.Status.CANCELLED && !o.placedAt().isBefore(from) && o.placedAt().isBefore(to))
                .toList();
    }

    private static BigDecimal sum(List<Order> orders) {
        return orders.stream().map(Order::total).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public Detail create(ProductForm form, List<MultipartFile> files) {
        return save(null, form, files);
    }

    public Detail update(String id, ProductForm form, List<MultipartFile> files) {
        return save(find(id), form, files);
    }

    /** Synchronized so the photo store and the catalogue change together. */
    private synchronized Detail save(Product existing, ProductForm form, List<MultipartFile> files) {
        repository.category(form.categoryId()).orElseThrow(() -> new BadRequestException("Choose a category."));
        List<String> vehicleIds = form.vehicleIds() == null ? List.of() : form.vehicleIds().stream().distinct().toList();
        for (String vehicleId : vehicleIds) {
            vehicles.findById(vehicleId).orElseThrow(() -> new BadRequestException("We don’t know the vehicle " + vehicleId + "."));
        }
        String partNumber = blankToNull(form.partNumber());
        String engineCode = blankToNull(form.engineCode());
        List<String> refinements = form.refinements() == null ? List.of() : form.refinements().stream()
                .map(AdminCatalogService::blankToNull)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        String id = existing != null ? existing.id() : newId(form.name());
        List<String> keep = form.keepPhotoIds() == null ? photos.idsFor(id) : form.keepPhotoIds();
        // Photos from the part's eBay listing: they stay unless staff took them off.
        List<String> remove = form.removePhotoIds() == null ? List.of() : form.removePhotoIds();
        List<ProductPhotoStore.Imported> imported = existing == null ? List.of() : photos.imported(id);
        List<ProductPhotoStore.Imported> importedKept = imported.stream().filter(p -> !remove.contains(p.id())).toList();
        long photoCount = photos.idsFor(id).stream().filter(keep::contains).count()
                + (files == null ? 0 : files.stream().filter(f -> !f.isEmpty()).count()) + importedKept.size();
        String sample = repository.sampleImage(id).orElse(null);
        // The publishing checks apply when a listing goes live. A listing that's already live (the eBay import's,
        // which have no photos until the photo download runs) can still have its price, stock or details changed.
        boolean goingLive = form.status() == ProductStatus.LIVE && (existing == null || !existing.live());
        if (goingLive) {
            List<String> missing = new ArrayList<>();
            if (partNumber == null && engineCode == null) {
                missing.add("a part number or engine code");
            }
            if (form.price() == null) {
                missing.add("a price");
            }
            if (photoCount == 0 && sample == null) {
                missing.add("at least one photo");
            }
            if (!missing.isEmpty()) {
                throw new BadRequestException("To publish, add " + joinWithAnd(missing) + ". Or save it as a draft.");
            }
        }

        // Check the photos before saving anything, so a bad file can't leave a half-saved listing behind.
        List<MultipartFile> added = files == null ? List.of() : files.stream().filter(f -> !f.isEmpty()).toList();
        if (!added.isEmpty() && photos.idsFor(id).stream().filter(keep::contains).count() + added.size() + importedKept.size() > ProductPhotoStore.MAX_PER_PART) {
            throw new BadRequestException("Add up to " + ProductPhotoStore.MAX_PER_PART + " photos per part.");
        }
        added.forEach(f -> ProductPhotoStore.imageType(ProductPhotoStore.read(f)));

        // The listing is saved first: in the database a photo has to belong to a listing that exists.
        // With no uploads left: the sample photo, or the image it had (an eBay photo), or "photo coming soon". Never
        // an uploaded photo, which may just have been removed.
        // For a part with eBay photos, its first one that's kept.
        String previous = existing == null || existing.imageUrl() == null || existing.imageUrl().contains(UPLOADED_PHOTO_PATH)
                || !imported.isEmpty() ? null : existing.imageUrl();
        String fallbackImage = !importedKept.isEmpty() ? importedKept.get(0).thumbnailUrl()
                : sample != null ? sample : previous != null ? previous : photoUrls.url(Images.PLACEHOLDER_KEY);
        repository.save(product(id, form, partNumber, engineCode, refinements, vehicleIds, fallbackImage, existing));
        List<String> photoIds = photos.update(id, keep, added);
        if (importedKept.size() < imported.size()) {
            photos.removeImported(id, remove);
        }
        Product saved = product(id, form, partNumber, engineCode, refinements, vehicleIds,
                photoIds.isEmpty() ? fallbackImage : photoUrl(photoIds.get(0)), existing);
        repository.save(saved);
        if (form.descriptionHtml() != null) {
            repository.saveDescriptionHtml(id, form.descriptionHtml().isBlank() ? null : form.descriptionHtml().trim());
        }
        parcelSizes.setWeight(id, form.weightKg());
        return detail(saved);
    }

    private static Product product(String id, ProductForm form, String partNumber, String engineCode, List<String> refinements,
                                   List<String> vehicleIds, String imageUrl, Product existing) {
        return new Product(id, form.name().trim(), form.categoryId(), form.condition(), blankToNull(form.grade()),
                form.price(), partNumber, engineCode, Brands.canonical(form.brand()), blankToNull(form.subType()), refinements,
                imageUrl, vehicleIds, form.shipping(), form.stock(), form.bestSeller(),
                existing != null ? existing.addedOn() : LocalDate.now(UK), form.status(), form.salePercent());
    }

    /**
     * Vehicles a part probably fits: those already confirmed for any part with the same part number (high
     * confidence), then those with the same engine code (medium: the same engine doesn't always mean the same part).
     */
    public List<Fitment> suggestFitment(List<String> partNumbers, String engineCode, String excludeProductId) {
        Set<String> wanted = partNumbers == null ? Set.of() : partNumbers.stream()
                .map(AdminCatalogService::blankToNull)
                .filter(Objects::nonNull)
                .map(AdminCatalogService::partNumberKey)
                .collect(Collectors.toSet());
        Map<String, Fitment> found = new LinkedHashMap<>();
        if (!wanted.isEmpty()) {
            for (Product p : repository.products()) {
                boolean match = !p.id().equals(excludeProductId) && wanted.contains(partNumberKey(p.partNumber()));
                if (match) {
                    p.fitsVehicleIds().forEach(vehicleId -> vehicles.findById(vehicleId).ifPresent(v ->
                            found.putIfAbsent(v.id(), fitment(v, "Part number match", Confidence.HIGH))));
                }
            }
        }
        String code = blankToNull(engineCode);
        if (code != null) {
            vehicles.all().stream()
                    .filter(v -> v.engineCode() != null && v.engineCode().equalsIgnoreCase(code))
                    .forEach(v -> found.putIfAbsent(v.id(), fitment(v, "Engine-code rule", Confidence.MEDIUM)));
        }
        return List.copyOf(found.values());
    }

    /** Staff plate checks aren't added to the shoppers' lookup log. The plate itself isn't stored. */
    public PlateCheck checkPlate(String registration) {
        Vehicle v = vehicles.lookUp(registration);
        List<Product> all = repository.products();
        List<Row> confirmed = all.stream().filter(p -> p.fitsVehicleIds().contains(v.id())).map(this::row).toList();
        List<Row> sameEngine = v.engineCode() == null ? List.of() : all.stream()
                .filter(p -> !p.fitsVehicleIds().contains(v.id()))
                .filter(p -> v.engineCode().equalsIgnoreCase(p.engineCode()))
                .map(this::row)
                .toList();
        return new PlateCheck(v, fitment(v, "Plate check", Confidence.HIGH), confirmed, sameEngine);
    }

    private Product find(String id) {
        return repository.product(id).orElseThrow(() -> new NotFoundException("No part with id " + id + "."));
    }

    private Row row(Product p) {
        String categoryName = repository.category(p.categoryId()).map(Category::name).orElse("");
        return new Row(p.id(), p.name(), categoryName, p.partNumber(), p.engineCode(), p.brand(), p.subType(), p.price(),
                p.stock(), p.imageUrl(), p.hasPhoto(), p.status(), workStatus(p), p.fitsVehicleIds().size(), p.addedOn(),
                p.salePercent(), p.sellingPrice());
    }

    private Detail detail(Product p) {
        List<Photo> uploaded = photos.idsFor(p.id()).stream().map(id -> new Photo(id, photoUrl(id))).toList();
        List<Photo> imported = photos.imported(p.id()).stream().map(i -> new Photo(i.id(), i.thumbnailUrl())).toList();
        List<Fitment> fits = p.fitsVehicleIds().stream()
                .map(vehicles::findById)
                .flatMap(Optional::stream)
                .map(v -> fitment(v, "Confirmed", Confidence.HIGH))
                .toList();
        return new Detail(p.id(), p.name(), p.categoryId(), p.condition(), p.grade(), p.price(), p.partNumber(),
                p.engineCode(), p.brand(), p.subType(), p.refinements(), p.shipping(), p.stock(), p.bestSeller(), p.status(),
                workStatus(p), p.imageUrl(), p.imageUrl() != null && !p.hasPhoto() && !Images.isPlaceholder(p.imageUrl()),
                uploaded, imported, fits, p.addedOn(), repository.descriptionHtml(p.id()).orElse(null), p.salePercent(), p.sellingPrice(),
                parcelSizes.weight(p.id()).orElse(null), ParcelSizes.typicalWeight(p.categoryId()));
    }

    static WorkStatus workStatus(Product p) {
        if (p.fitsVehicleIds().isEmpty()) {
            return WorkStatus.NEEDS_FITMENT;
        }
        if (!p.hasPhoto()) {
            return WorkStatus.MISSING_PHOTO;
        }
        return p.live() ? WorkStatus.LIVE : WorkStatus.DRAFT;
    }

    static Fitment fitment(Vehicle v, String source, Confidence confidence) {
        return new Fitment(v.id(), v.displayName(), v.engine(), v.engineCode(), v.year(), v.yearTo(), source, confidence);
    }

    /** "Nissan Navara D40, YD25DDTi", or the engine description when there's no engine code. */
    static String describe(Vehicle v) {
        return v.displayName() + ", " + (v.engineCode() != null ? v.engineCode() : v.engine());
    }

    private String newId(String name) {
        String slug = name.toLowerCase(Locale.UK).replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
        if (slug.length() > 40) {
            slug = slug.substring(0, 40).replaceAll("-+$", "");
        }
        String id;
        do {
            byte[] bytes = new byte[2];
            RANDOM.nextBytes(bytes);
            id = (slug.isEmpty() ? "part" : slug) + "-" + HexFormat.of().formatHex(bytes);
        } while (repository.product(id).isPresent());
        return id;
    }

    private String photoUrl(String photoId) {
        return photoUrls.server(UPLOADED_PHOTO_PATH + photoId);
    }

    private static String partNumberKey(String number) {
        return number == null ? "" : CatalogService.compact(number).toUpperCase(Locale.UK);
    }

    private static String joinWithAnd(List<String> items) {
        if (items.size() == 1) {
            return items.get(0);
        }
        return String.join(", ", items.subList(0, items.size() - 1)) + " and " + items.get(items.size() - 1);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
