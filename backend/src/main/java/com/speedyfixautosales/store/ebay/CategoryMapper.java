package com.speedyfixautosales.store.ebay;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Suggests the shop category for an eBay category name. Used the first time an eBay category is seen; after that
 * the choice lives in the ebay_category_map table, where staff can change it.
 */
final class CategoryMapper {

    static final String FALLBACK = "other-parts";

    private static final Map<String, Set<String>> BY_CATEGORY = Map.ofEntries(
            Map.entry("engines", Set.of("engines", "engine blocks")),
            Map.entry("cylinder-heads", Set.of("cylinder heads")),
            Map.entry("gearboxes", Set.of("automatic transmission", "manual transmission", "transfer cases",
                    "gearbox & transmission parts", "transfer case parts", "gear levers, cables & linkages")),
            Map.entry("drivetrain", Set.of("differential assemblies", "additional differential parts", "driveshafts",
                    "propshafts", "axles", "axle housings & parts", "cv joints, boots & parts", "flywheels & flexplates",
                    "clutch kits", "transmission & drivetrain mounts", "other transmission & drivetrain")),
            Map.entry("turbochargers", Set.of("turbos & parts", "turbochargers", "superchargers & parts", "intercoolers")),
            Map.entry("ecus-electronics", Set.of("ecus & computer modules", "other starters, alternators, ecus & wiring",
                    "instrument clusters", "fuses & fuse boxes", "abs control modules", "hvac controls & control units",
                    "switches & controls", "relays", "car stereos & head units", "amplifiers", "screens",
                    "lidar & radar sensors", "cruise control components", "car keys, fobs & remotes")),
            Map.entry("starters-alternators", Set.of("alternators & generators", "starter motors")),
            Map.entry("fuel-injection", Set.of("fuel injectors", "additional fuel injection parts", "high pressure fuel pumps",
                    "fuel pumps & sending units", "throttle bodies", "other air & fuel delivery", "fuel tanks & filler necks",
                    "air intake & fuel sensors", "air filter housings", "intake manifolds")),
            Map.entry("egr-emissions", Set.of("egr parts & valves", "catalytic converters", "particulate filters",
                    "manifolds & headers", "oxygen sensors", "heat shields, wraps & sleeves")),
            Map.entry("electrics-sensors", Set.of("ignition coils", "wiring harnesses, cables & connectors",
                    "wiper linkages, transmissions & motors", "wiper arms", "additional wiper & washer components")),
            Map.entry("cooling-ac", Set.of("a/c compressors & clutches", "a/c hoses & fittings", "radiators", "water pumps",
                    "thermostats & housings", "coolant hoses", "expansion & overflow tanks", "fan & shroud assemblies",
                    "engine oil coolers & lines", "heater cores", "hvac blower motors & wheels", "other engine cooling components")),
            Map.entry("engine-parts", Set.of("other engine parts", "vacuum pumps", "tensioners & pulleys", "pulleys",
                    "crankshafts", "camshafts", "oil sumps", "rocker & valve covers", "oil pumps & parts", "oil filters",
                    "oil drain plugs, filler caps & dipsticks", "engine bearings", "hoses, lines & pipes")),
            Map.entry("engine-mounts", Set.of("engine mounts", "mounts, brackets & hardware")),
            Map.entry("body-panels", Set.of("doors & door skins", "bumpers & reinforcements", "bonnet panels",
                    "boot & hatch lids", "grilles", "fenders", "tailgates", "spoilers & wings",
                    "hinges, latches & additional bonnet components", "lift supports, latches, hinges & additional parts",
                    "body mouldings & trims", "additional panels", "other exterior parts & accessories")),
            Map.entry("lights-mirrors", Set.of("headlight assemblies", "rear light assemblies", "fog light assemblies",
                    "mirror assemblies", "rear view mirrors", "interior light assemblies")),
            Map.entry("interior", Set.of("other interior parts & accessories", "door panels", "dashboard panels", "airbag parts",
                    "steering wheels & horn buttons", "glove boxes, doors & latches", "seats", "centre & overhead consoles",
                    "trim", "individual gauges", "pedal assemblies, pads & parts", "gear knobs")),
            Map.entry("steering-suspension", Set.of("control arms, ball joints & assemblies", "steering columns",
                    "power steering pumps", "gearboxes, racks & pinions", "gearbox, rack & pinion parts",
                    "shocks, struts & assemblies", "sway bars, links & bushings", "other steering & suspension parts")),
            Map.entry("brakes", Set.of("brake servos", "calipers & brackets", "additional abs parts")),
            Map.entry("accessories", Set.of("other car parts & accessories", "wheels with tyres")));

    /** Parts in these shop categories go on a pallet; everything else as a parcel. */
    static final Set<String> PALLET_CATEGORIES = Set.of("engines", "gearboxes");

    private CategoryMapper() {
    }

    static String suggest(String ebayCategoryName) {
        String name = ebayCategoryName == null ? "" : ebayCategoryName.trim().toLowerCase(Locale.UK);
        return BY_CATEGORY.entrySet().stream()
                .filter(e -> e.getValue().contains(name))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(FALLBACK);
    }
}
