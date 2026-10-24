package com.speedyfixautosales.store.catalog;

import static com.speedyfixautosales.store.catalog.Images.unsplash;

import com.speedyfixautosales.store.catalog.MenuGroup.MenuLink;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/**
 * In-memory catalogue seeded with the sample data used in the designs.
 * Swap for a JPA repository (or the client's product feed) when the real catalogue is ready.
 */
@Repository
public class CatalogRepository {

    private final Map<String, Category> categories = new LinkedHashMap<>();
    private final Map<String, Product> products = new LinkedHashMap<>();
    private final Map<String, String> sampleImages = new LinkedHashMap<>();
    private final List<Slide> slides;
    private final List<Feature> features;
    private final List<MenuGroup> menu;

    public CatalogRepository() {
        // Popular categories show on the homepage in sortOrder: engines, gearboxes, accessories, turbochargers.
        category("engines", "Engines", "1552656967-7a0991a13906", true, 1);
        category("gearboxes", "Gearboxes", "1711199694531-e982a79ea381", true, 2);
        category("accessories", "Accessories", "1537462715879-360eeb61a0ad", true, 3);
        category("turbochargers", "Turbochargers", "1664695221759-21ed7295f697", true, 4);
        category("rebuild-kits", "Rebuild kits", "1720244253125-f39d7aeccccf", false, 5);
        category("cylinder-heads", "Cylinder heads", "1786028924575-9948c6a58b22", false, 6);
        category("engine-mounts", "Engine mounts", "1767340078222-98633f3ffd27", false, 7);
        category("fuel-injection", "Fuel injection", "1759148413911-5ded7ed2ec06", false, 8);
        category("cooling-ac", "Cooling and A/C", "1486262715619-67b85e0b08d3", false, 9);
        category("electrics-sensors", "Electrics and sensors", "1584809923235-fabdba83d1df", false, 10);
        category("brakes", "Brakes", "1696494561430-de087dd0bd69", false, 11);

        product("ej20-head", "Subaru EJ20 cylinder head, remanufactured", "cylinder-heads", Condition.REMANUFACTURED, null,
                "640.00", null, "EJ20", "Subaru", "Cylinder head", List.of("Subaru Impreza cylinder head"),
                "1786028924575-9948c6a58b22", List.of("impreza"), Shipping.PARCEL, 2, false, "2026-08-02");
        product("yd25-kit", "Nissan YD25 engine rebuild kit", "rebuild-kits", Condition.NEW, null,
                "389.00", null, "YD25DDTi", "Nissan", "Engine rebuild kit", List.of("Nissan Navara rebuild kit"),
                "1720244253125-f39d7aeccccf", List.of("navara"), Shipping.PARCEL, 6, false, "2026-07-14");
        product("rb25-turbo", "Nissan RB25DET turbocharger, remanufactured", "turbochargers", Condition.REMANUFACTURED, null,
                "720.00", null, "RB25DET", "Nissan", "Turbocharger", List.of("Nissan Skyline turbo"),
                "1664695221759-21ed7295f697", List.of("r33"), Shipping.PARCEL, 1, false, "2026-06-20");
        product("bosch-pump", "Bosch high-pressure diesel fuel pump", "fuel-injection", Condition.USED, "A",
                "245.00", "0445010314", null, "Bosch", "High-pressure fuel pump", List.of("Diesel fuel pump"),
                "1759148413911-5ded7ed2ec06", List.of(), Shipping.PARCEL, 2, false, "2026-05-30");
        product("yd25-engine", "Nissan YD25DDTi 2.5 diesel engine, complete", "engines", Condition.USED, "A",
                "1895.00", null, "YD25DDTi", "Nissan", "Complete engine", List.of("Nissan Navara engine", "Diesel engine"),
                "1616728249167-1d50a55de3d9", List.of("navara"), Shipping.PALLET, 2, true, "2026-04-11");
        product("navara-gearbox", "Nissan Navara D40 manual gearbox", "gearboxes", Condition.USED, "B",
                "895.00", null, "YD25DDTi", "Nissan", "Manual gearbox", List.of("Nissan Navara gearbox", "Manual gearbox"),
                "1711199694531-e982a79ea381", List.of("navara"), Shipping.PALLET, 1, true, "2026-03-09");
        product("1fz-engine", "Toyota 1FZ-FE 4.5 petrol engine", "engines", Condition.USED, "B",
                "2450.00", null, "1FZ-FE", "Toyota", "Complete engine", List.of("Toyota Land Cruiser engine", "Petrol engine"),
                "1552656967-7a0991a13906", List.of("lc80"), Shipping.PALLET, 1, true, "2026-02-18");
        product("3ur-engine", "Toyota Tundra 5.7 V8 engine", "engines", Condition.USED, "A",
                "3950.00", null, "3UR-FE", "Toyota", "Complete engine", List.of("Toyota Tundra engine", "Petrol engine", "V8 engine"),
                "1564730465543-e732e7fc9c10", List.of("tundra"), Shipping.PALLET, 1, true, "2026-01-27");
        product("astra-dmf", "Vauxhall Astra dual-mass flywheel", "gearboxes", Condition.USED, "A",
                "289.00", null, null, "Vauxhall", "Dual-mass flywheel", List.of("Vauxhall Astra flywheel"),
                "1711199694531-e982a79ea381", List.of(), Shipping.PARCEL, 2, false, "2025-12-15");
        product("denso-ac", "Denso A/C compressor", "cooling-ac", Condition.USED, "B",
                "210.00", "447280-3260", null, "Denso", "A/C compressor", List.of(),
                "1486262715619-67b85e0b08d3", List.of(), Shipping.PARCEL, 1, false, "2025-11-03");
        product("maserati-brake", "Maserati front brake assembly", "brakes", Condition.USED, "A",
                "520.00", null, null, "Maserati", "Brake caliper assembly", List.of(),
                "1763087978864-fe5b2778c9f7", List.of(), Shipping.PARCEL, 1, false, "2025-10-21");
        product("xe-mount", "Jaguar XE engine mount", "engine-mounts", Condition.USED, "A",
                "64.00", "GX73-6A003-KC", null, "Jaguar", "Engine mount", List.of("Jaguar XE engine mount"),
                "1767340078222-98633f3ffd27", List.of("xe"), Shipping.PARCEL, 3, false, "2026-10-14");
        product("astra-loom", "Vauxhall Astra K engine bay wiring loom", "electrics-sensors", Condition.USED, "A",
                "149.00", "39071660", null, "Vauxhall", "Wiring loom", List.of("Vauxhall Astra wiring loom"),
                "1584809923235-fabdba83d1df", List.of("astra"), Shipping.PARCEL, 1, false, "2026-10-12");
        product("ford-ac", "Ford A/C compressor", "cooling-ac", Condition.USED, "A",
                "185.00", "C1B1-19D629-A2H", null, "Ford", "A/C compressor", List.of(),
                "1486262715619-67b85e0b08d3", List.of(), Shipping.PARCEL, 2, false, "2026-10-09");
        product("gm-sensor", "GM coolant temperature sensor", "electrics-sensors", Condition.NEW, null,
                "18.00", "12641073", null, "GM", "Coolant temperature sensor", List.of(),
                "1537462715879-360eeb61a0ad", List.of(), Shipping.PARCEL, 12, false, "2026-10-05");
        product("navara-cover", "Nissan Navara D40 engine cover", "accessories", Condition.USED, "A",
                "45.00", "14041-EB300", null, "Nissan", "Engine cover", List.of("Nissan Navara accessories"),
                "1616728249167-1d50a55de3d9", List.of("navara"), Shipping.PARCEL, 2, false, "2026-09-18");
        product("catch-can", "Aluminium oil catch can kit, universal", "accessories", Condition.NEW, null,
                "39.00", null, null, "Universal", "Oil catch can", List.of("Universal fit"),
                "1537462715879-360eeb61a0ad", List.of(), Shipping.PARCEL, 8, false, "2026-09-02");

        slides = List.of(
                new Slide("turbochargers", "Category spotlight", "Turbochargers",
                        "Remanufactured and used turbos, matched to your engine code and tested before dispatch.",
                        unsplash("1664695221759-21ed7295f697", 1920, 900),
                        "Shop turbochargers", "/products?category=turbochargers", "View all categories", "/products"),
                new Slide("engines", "Category spotlight", "Complete engines",
                        "Used engines removed from donor vehicles, tested and graded before they’re listed.",
                        unsplash("1527383418406-f85a3b146499", 1920, 900),
                        "Shop engines", "/products?category=engines", "View all categories", "/products"),
                new Slide("cylinder-heads", "Category spotlight", "Cylinder heads",
                        "Bare and assembled heads, with gasket and bolt kits to match.",
                        unsplash("1786028924575-9948c6a58b22", 1920, 900),
                        "Shop cylinder heads", "/products?category=cylinder-heads", "View all categories", "/products"),
                new Slide("rebuild-kits", "Category spotlight", "Rebuild kits",
                        "Pistons, rings, bearings and gaskets in one kit for your engine code.",
                        unsplash("1720244253125-f39d7aeccccf", 1920, 900),
                        "Shop rebuild kits", "/products?category=rebuild-kits", "View all categories", "/products"));

        features = List.of(
                new Feature("wrench", "Expert team", "Every part tested and graded"),
                new Feature("truck", "Our delivery partners", "[Courier names], tracked to your door"),
                new Feature("shield", "Quick UK shipping", "[X] to [X] working days, warranty included"));

        // Links go to a category when one exists, otherwise to a search for the part name.
        menu = List.of(
                group("Engine and internals", cat("Complete engines", "engines"), find("Short engines", "short engine"),
                        cat("Cylinder heads", "cylinder-heads"), find("Crankshafts", "crankshaft"),
                        find("Camshafts", "camshaft"), find("Pistons and rings", "piston"),
                        find("Connecting rods", "connecting rod"), cat("Rebuild kits", "rebuild-kits"),
                        find("Gasket kits", "gasket"), find("Timing kits", "timing")),
                group("Fuel, air and turbo", cat("Turbochargers", "turbochargers"), find("Turbo cartridges", "cartridge"),
                        cat("Fuel injection pumps", "fuel-injection"), find("Fuel injectors", "injector"),
                        find("Injector rails", "rail"), find("Throttle bodies", "throttle"), find("EGR valves", "egr")),
                group("Cooling and electrics", cat("A/C compressors", "cooling-ac"), find("Water pumps", "water pump"),
                        find("Oil pumps", "oil pump"), find("Oil coolers", "oil cooler"),
                        find("Alternators", "alternator"), find("Starter motors", "starter"),
                        cat("Sensors", "electrics-sensors"), find("Wiring looms", "loom")),
                group("Transmission and brakes", cat("Gearboxes", "gearboxes"), cat("Accessories", "accessories"), find("Flywheels", "flywheel"),
                        find("Clutch kits", "clutch"), cat("Brake assemblies", "brakes"),
                        find("Brake pads", "brake pad"), cat("Engine mounts", "engine-mounts")));
    }

    public List<Category> categories() {
        return categories.values().stream().sorted(Comparator.comparingInt(Category::sortOrder)).toList();
    }

    public Optional<Category> category(String id) {
        return Optional.ofNullable(categories.get(id));
    }

    /** Every part, drafts included. Shopper-facing code uses liveProducts(). */
    public synchronized List<Product> products() {
        return List.copyOf(products.values());
    }

    public List<Product> liveProducts() {
        return products().stream().filter(Product::live).toList();
    }

    public synchronized Optional<Product> product(String id) {
        return Optional.ofNullable(products.get(id));
    }

    /** Adds or replaces a part (admin). */
    public synchronized void save(Product product) {
        products.put(product.id(), product);
    }

    /** The sample photo a seeded part started with, used again if staff remove every uploaded photo. */
    public Optional<String> sampleImage(String id) {
        return Optional.ofNullable(sampleImages.get(id));
    }

    /** Takes sold items out of stock. Returns false, changing nothing, if there aren't enough. */
    public synchronized boolean reduceStock(String id, int quantity) {
        Product p = products.get(id);
        if (p == null || p.stock() < quantity) {
            return false;
        }
        products.put(id, p.withStock(p.stock() - quantity));
        return true;
    }

    public List<Slide> slides() {
        return slides;
    }

    public List<Feature> features() {
        return features;
    }

    public List<MenuGroup> menu() {
        return menu;
    }

    private void category(String id, String name, String photoId, boolean popular, int sortOrder) {
        categories.put(id, new Category(id, name, unsplash(photoId, 900, 1140), popular, sortOrder));
    }

    private void product(String id, String name, String categoryId, Condition condition, String grade, String price,
                         String partNumber, String engineCode, String brand, String subType, List<String> refinements,
                         String photoId, List<String> fits, Shipping shipping, int stock, boolean bestSeller, String addedOn) {
        String image = unsplash(photoId, 800, 800);
        sampleImages.put(id, image);
        products.put(id, new Product(id, name, categoryId, condition, grade, new BigDecimal(price), partNumber, engineCode,
                brand, subType, refinements, image, fits, shipping, stock, bestSeller, LocalDate.parse(addedOn), ProductStatus.LIVE));
    }

    private static MenuGroup group(String title, MenuLink... links) {
        return new MenuGroup(title, List.of(links));
    }

    private static MenuLink cat(String label, String categoryId) {
        return new MenuLink(label, "/products?category=" + categoryId);
    }

    private static MenuLink find(String label, String query) {
        return new MenuLink(label, "/products?q=" + query.replace(' ', '+'));
    }
}
