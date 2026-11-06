package com.speedyfixautosales.store.catalog;

import static com.speedyfixautosales.store.catalog.Images.stock;

import com.speedyfixautosales.store.catalog.MenuGroup.MenuLink;
import java.util.List;
import java.util.Map;

/** The homepage slides, feature tiles and Products menu. The same whichever catalogue store is in use. */
final class StorefrontContent {

    private StorefrontContent() {
    }

    /** Banner photos for categories until the business supplies its own (Unsplash samples, served as WebP from the site). */
    static final Map<String, String> CATEGORY_SAMPLE_PHOTOS = Map.of(
            "engines", stock("1779263569870-e7a0fbc25686", 900, 1140),
            "gearboxes", stock("1711199694531-e982a79ea381", 900, 1140),
            "accessories", stock("1537462715879-360eeb61a0ad", 900, 1140),
            "turbochargers", stock("1655103955676-c9fbc4b65525", 900, 1140),
            "cylinder-heads", stock("1786028924575-9948c6a58b22", 900, 1140),
            "rebuild-kits", stock("1703181226035-1c26d7e5f6ee", 900, 1140),
            "fuel-injection", stock("1759148413911-5ded7ed2ec06", 900, 1140),
            "cooling-ac", stock("1665564590151-7fe9493b478a", 900, 1140),
            "electrics-sensors", stock("1584809923235-fabdba83d1df", 900, 1140),
            "brakes", stock("1613214150384-14921ff659b2", 900, 1140));

    /**
     * The homepage's popular-category tiles: dark close-ups in the same style as the slides (Unsplash, no logos or
     * people), not the category's own photo. Different shots from the slides, except turbochargers.
     */
    static final Map<String, String> POPULAR_TILE_PHOTOS = Map.of(
            "engines", stock("1429772011165-0c2e054367b8", 900, 1140),
            "gearboxes", stock("1567093322102-6bdd32fba67d", 900, 1140),
            "accessories", stock("1537462715879-360eeb61a0ad", 900, 1140),
            "turbochargers", stock("1655103955676-c9fbc4b65525", 900, 1140));

    static final List<Slide> SLIDES = List.of(
            new Slide("turbochargers", "Category Spotlight", "Turbochargers",
                    "Remanufactured and used turbos, matched to your engine code. We test each one before dispatch.",
                    stock("1655103955676-c9fbc4b65525", 1920, 900),
                    "Shop Turbochargers", "/products?category=turbochargers", "View All Categories", "/products"),
            new Slide("engines", "Category Spotlight", "Complete Engines",
                    "Used engines from donor vehicles. We test and grade each one before we list it.",
                    stock("1779263569870-e7a0fbc25686", 1920, 900),
                    "Shop Engines", "/products?category=engines", "View All Categories", "/products"),
            new Slide("cylinder-heads", "Category Spotlight", "Cylinder Heads",
                    "Bare and assembled heads, with gasket and bolt kits to match.",
                    stock("1786028924575-9948c6a58b22", 1920, 900),
                    "Shop Cylinder Heads", "/products?category=cylinder-heads", "View All Categories", "/products"),
            new Slide("gearboxes", "Category Spotlight", "Gearboxes",
                    "Manual and automatic gearboxes, which we check against your engine and model.",
                    stock("1711199694531-e982a79ea381", 1920, 900),
                    "Shop Gearboxes", "/products?category=gearboxes", "View All Categories", "/products"));

    static final List<Feature> FEATURES = List.of(
            new Feature("wrench", "Expert Team", "We test and grade every part"),
            new Feature("truck", "Our Delivery Partners", "FedEx, UPS, Royal Mail, DHL and DPD, tracked to your door"),
            new Feature("shield", "Quick UK Shipping", "Usually within 7 working days, with a 28-day warranty"));

    // Links go to a category when one exists, otherwise to a search for the part name. Every link has stock behind it
    // in the business's eBay listings; check the counts again if the range changes.
    static final List<MenuGroup> MENU = List.of(
            group("Engine and Internals", cat("Complete Engines", "engines"), find("Short Engines", "short engine"),
                    cat("Cylinder Heads", "cylinder-heads"), cat("Engine Parts", "engine-parts"),
                    find("Crankshafts", "crankshaft"), find("Camshafts", "camshaft"), find("Timing Kits", "timing"),
                    find("Flywheels", "flywheel"), cat("Engine Mounts", "engine-mounts")),
            group("Fuel, Air and Turbo", cat("Turbochargers", "turbochargers"), cat("Fuel Injection", "fuel-injection"),
                    find("Fuel Injectors", "injector"), find("Injector Rails", "rail"),
                    cat("Air Intake and Throttle Bodies", "air-intake"), cat("Exhaust and Emissions", "egr-emissions")),
            group("Electrics and Cooling", cat("ECUs and Electronics", "ecus-electronics"),
                    cat("Instruments and Infotainment", "instruments-infotainment"),
                    cat("Starters and Alternators", "starters-alternators"), cat("Ignition", "ignition"), cat("Sensors", "electrics-sensors"),
                    cat("A/C Compressors", "cooling-ac"), find("Water Pumps", "water pump"), find("Oil Coolers", "oil cooler"),
                    find("Wiring Looms", "loom"), cat("Lights and Mirrors", "lights-mirrors")),
            group("Transmission and Body", cat("Gearboxes", "gearboxes"), cat("Drivetrain", "drivetrain"),
                    cat("Steering and Suspension", "steering-suspension"), cat("Body Panels", "body-panels"), cat("Doors", "doors"),
                    cat("Interior", "interior"), cat("Accessories", "accessories"), cat("Brakes", "brakes")));

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
