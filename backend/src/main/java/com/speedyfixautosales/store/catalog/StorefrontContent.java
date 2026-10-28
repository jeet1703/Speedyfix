package com.speedyfixautosales.store.catalog;

import static com.speedyfixautosales.store.catalog.Images.unsplash;

import com.speedyfixautosales.store.catalog.MenuGroup.MenuLink;
import java.util.List;
import java.util.Map;

/** The homepage slides, feature tiles and Products menu. The same whichever catalogue store is in use. */
final class StorefrontContent {

    private StorefrontContent() {
    }

    /** Banner photos for categories until the business supplies its own (Unsplash samples). */
    static final Map<String, String> CATEGORY_SAMPLE_PHOTOS = Map.of(
            "engines", unsplash("1552656967-7a0991a13906", 900, 1140),
            "gearboxes", unsplash("1711199694531-e982a79ea381", 900, 1140),
            "accessories", unsplash("1537462715879-360eeb61a0ad", 900, 1140),
            "turbochargers", unsplash("1664695221759-21ed7295f697", 900, 1140),
            "cylinder-heads", unsplash("1786028924575-9948c6a58b22", 900, 1140),
            "rebuild-kits", unsplash("1720244253125-f39d7aeccccf", 900, 1140),
            "fuel-injection", unsplash("1759148413911-5ded7ed2ec06", 900, 1140),
            "cooling-ac", unsplash("1486262715619-67b85e0b08d3", 900, 1140),
            "electrics-sensors", unsplash("1584809923235-fabdba83d1df", 900, 1140),
            "brakes", unsplash("1696494561430-de087dd0bd69", 900, 1140));

    static final List<Slide> SLIDES = List.of(
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
            new Slide("gearboxes", "Category spotlight", "Gearboxes",
                    "Manual and automatic gearboxes, checked and matched to your engine and model.",
                    unsplash("1711199694531-e982a79ea381", 1920, 900),
                    "Shop gearboxes", "/products?category=gearboxes", "View all categories", "/products"));

    static final List<Feature> FEATURES = List.of(
            new Feature("wrench", "Expert team", "Every part tested and graded"),
            new Feature("truck", "Our delivery partners", "[Courier names], tracked to your door"),
            new Feature("shield", "Quick UK shipping", "[X] to [X] working days, warranty included"));

    // Links go to a category when one exists, otherwise to a search for the part name. Every link has stock behind it
    // in the business's eBay listings; check the counts again if the range changes.
    static final List<MenuGroup> MENU = List.of(
            group("Engine and internals", cat("Complete engines", "engines"), find("Short engines", "short engine"),
                    cat("Cylinder heads", "cylinder-heads"), cat("Engine parts", "engine-parts"),
                    find("Crankshafts", "crankshaft"), find("Camshafts", "camshaft"), find("Timing kits", "timing"),
                    find("Flywheels", "flywheel"), cat("Engine mounts", "engine-mounts")),
            group("Fuel, air and turbo", cat("Turbochargers", "turbochargers"), cat("Fuel injection", "fuel-injection"),
                    find("Fuel injectors", "injector"), find("Injector rails", "rail"), find("Throttle bodies", "throttle"),
                    cat("EGR and emissions", "egr-emissions")),
            group("Electrics and cooling", cat("ECUs and electronics", "ecus-electronics"),
                    cat("Starters and alternators", "starters-alternators"), cat("Sensors", "electrics-sensors"),
                    cat("A/C compressors", "cooling-ac"), find("Water pumps", "water pump"), find("Oil coolers", "oil cooler"),
                    find("Wiring looms", "loom"), cat("Lights and mirrors", "lights-mirrors")),
            group("Transmission and body", cat("Gearboxes", "gearboxes"), cat("Drivetrain", "drivetrain"),
                    cat("Steering and suspension", "steering-suspension"), cat("Body panels", "body-panels"),
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
