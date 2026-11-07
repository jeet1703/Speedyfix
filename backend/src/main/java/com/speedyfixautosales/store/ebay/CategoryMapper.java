package com.speedyfixautosales.store.ebay;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Suggests the shop category for an eBay category name. Used the first time an eBay category is seen; after that
 * the choice lives in the ebay_category_map table, where staff can change it.
 */
final class CategoryMapper {

    static final String FALLBACK = "other-parts";

    private static final Map<String, Set<String>> BY_CATEGORY = Map.ofEntries(
            Map.entry("engines", Set.of("engines")),
            Map.entry("cylinder-blocks", Set.of("engine blocks")),
            Map.entry("cylinder-heads", Set.of("cylinder heads")),
            Map.entry("gearboxes", Set.of("automatic transmission", "manual transmission", "transfer cases",
                    "gearbox & transmission parts", "transfer case parts", "gear levers, cables & linkages")),
            Map.entry("drivetrain", Set.of("differential assemblies", "additional differential parts", "driveshafts",
                    "propshafts", "axles", "axle housings & parts", "cv joints, boots & parts", "flywheels & flexplates",
                    "clutch kits", "transmission & drivetrain mounts", "other transmission & drivetrain")),
            Map.entry("turbochargers", Set.of("turbos & parts", "turbochargers", "superchargers & parts", "intercoolers")),
            Map.entry("ecus-electronics", Set.of("ecus & computer modules", "other starters, alternators, ecus & wiring",
                    "fuses & fuse boxes", "abs control modules", "hvac controls & control units",
                    "switches & controls", "relays", "cruise control components", "car keys, fobs & remotes")),
            Map.entry("instruments-infotainment", Set.of("instrument clusters", "individual gauges")),
            Map.entry("audio-media", Set.of("car stereos & head units", "amplifiers", "screens")),
            Map.entry("starters-alternators", Set.of("alternators & generators", "starter motors")),
            Map.entry("fuel-injection", Set.of("additional fuel injection parts", "high pressure fuel pumps",
                    "fuel pumps & sending units", "fuel tanks & filler necks")),
            Map.entry("fuel-injectors", Set.of("fuel injectors")),
            Map.entry("manifolds", Set.of("intake manifolds", "manifolds & headers")),
            Map.entry("air-intake", Set.of("throttle bodies")),
            Map.entry("egr-valves", Set.of("egr parts & valves")),
            Map.entry("egr-emissions", Set.of("catalytic converters", "particulate filters", "heat shields, wraps & sleeves")),
            Map.entry("ignition", Set.of("ignition coils", "spark plugs", "ignition leads")),
            Map.entry("electrics-sensors", Set.of("oxygen sensors", "air intake & fuel sensors", "lidar & radar sensors")),
            Map.entry("wiring-looms", Set.of("wiring harnesses, cables & connectors")),
            Map.entry("ac-compressors", Set.of("a/c compressors & clutches")),
            Map.entry("cooling-ac", Set.of("a/c hoses & fittings", "radiators", "water pumps",
                    "thermostats & housings", "coolant hoses", "expansion & overflow tanks", "fan & shroud assemblies",
                    "engine oil coolers & lines", "heater cores", "hvac blower motors & wheels", "other engine cooling components")),
            Map.entry("crankshafts", Set.of("crankshafts")),
            Map.entry("camshafts", Set.of("camshafts")),
            Map.entry("sumps-pans", Set.of("oil sumps")),
            Map.entry("oil-pumps", Set.of("oil pumps & parts")),
            Map.entry("engine-parts", Set.of("other engine parts", "vacuum pumps", "tensioners & pulleys", "pulleys",
                    "air filter housings", "other air & fuel delivery", "rocker & valve covers", "oil filters",
                    "oil drain plugs, filler caps & dipsticks", "engine bearings", "hoses, lines & pipes")),
            Map.entry("engine-mounts", Set.of("engine mounts", "mounts, brackets & hardware")),
            Map.entry("doors", Set.of("doors & door skins")),
            Map.entry("body-panels", Set.of("bumpers & reinforcements", "bonnet panels",
                    "boot & hatch lids", "grilles", "fenders", "tailgates", "spoilers & wings",
                    "hinges, latches & additional bonnet components", "lift supports, latches, hinges & additional parts",
                    "body mouldings & trims", "additional panels", "other exterior parts & accessories")),
            Map.entry("lights-mirrors", Set.of("headlight assemblies", "rear light assemblies", "fog light assemblies",
                    "mirror assemblies", "rear view mirrors", "interior light assemblies")),
            Map.entry("interior", Set.of("other interior parts & accessories", "door panels", "dashboard panels", "airbag parts",
                    "steering wheels & horn buttons", "glove boxes, doors & latches", "seats", "centre & overhead consoles",
                    "trim", "pedal assemblies, pads & parts", "gear knobs")),
            Map.entry("steering-suspension", Set.of("control arms, ball joints & assemblies", "steering columns",
                    "power steering pumps", "gearboxes, racks & pinions", "gearbox, rack & pinion parts",
                    "shocks, struts & assemblies", "sway bars, links & bushings", "other steering & suspension parts")),
            Map.entry("brakes", Set.of("brake servos", "calipers & brackets", "additional abs parts")),
            Map.entry("accessories", Set.of("other car parts & accessories", "wheels with tyres")),
            Map.entry("other-parts", Set.of("wiper linkages, transmissions & motors", "wiper arms",
                    "additional wiper & washer components")));

    /** Parts in these shop categories go on a pallet; everything else as a parcel. */
    static final Set<String> PALLET_CATEGORIES = Set.of("engines", "bare-engines", "short-motors", "cylinder-blocks", "gearboxes");

    /** Titles of engines sold without their ancillaries: "Bare Engine Long Block", "Short Motor Engine Block". */
    private static final Pattern BARE_ENGINE = Pattern.compile("bare|long block|short block|short motor|engine block|block only",
            Pattern.CASE_INSENSITIVE);

    private CategoryMapper() {
    }

    /** Where a title says plainly what the part is, it wins over the eBay category it was listed in. */
    private record Fix(Set<String> from, Pattern title, String to) {
        static Fix of(Set<String> from, String title, String to) {
            return new Fix(from, Pattern.compile(title, Pattern.CASE_INSENSITIVE), to);
        }
    }

    /**
     * eBay categories that hold parts the shop files elsewhere, as found when every listing was checked (migrations
     * V21, V22, V24, V26 and V27): complete engines, bare engines, short motors and bare blocks share one eBay category,
     * automatic gearboxes sit under "Gearboxes, Racks & Pinions", doors under body panels, airbag control modules under
     * interior trim, gearbox oil coolers and selectors under gearboxes, turbo pipes under turbos, bumper brackets under
     * mounts. First match wins.
     */
    private static final List<Fix> FIXES = List.of(
            Fix.of(Set.of("engines", "bare-engines", "cylinder-blocks"), "short motor|short block|half engine", "short-motors"),
            Fix.of(Set.of("engines", "bare-engines"), "^(?!.*long block).*(cylinder block|engine block|bare block|block only)",
                    "cylinder-blocks"),
            Fix.of(Set.of("engines"), BARE_ENGINE.pattern(), "bare-engines"),
            Fix.of(Set.of("steering-suspension", "drivetrain"),
                    "^(?!.*(steering|mount|flywheel|cooler|selector)).*(automatic (engine )?gearbox|gearbox assembly|\\bgearbox\\b.*speed|transfer (box|case) assembly)",
                    "gearboxes"),
            // "Complete Door Assembly No Door Card" is a door; a door card on its own isn't.
            Fix.of(Set.of("body-panels", "interior"),
                    "^(?!.*((?<!no door )(?<!no )(door )?card|trim|handle|hinge|seal)).*(complete door|door assembly|side door)", "doors"),
            Fix.of(Set.of("interior"), "(airbag|safety bag|srs).*control (module|unit)", "ecus-electronics"),
            Fix.of(Set.of("ecus-electronics"), "starter motor", "starters-alternators"),
            Fix.of(Set.of("gearboxes", "brakes"), "control module", "ecus-electronics"),
            Fix.of(Set.of("gearboxes"), "^(?!.*(knob|surround|cable)).*selector", "ecus-electronics"),
            Fix.of(Set.of("gearboxes"), "cooler", "cooling-ac"),
            // eBay's "Transmission & Drivetrain Mounts" holds gearbox mounts; its "Mounts, Brackets & Hardware" bumper brackets.
            Fix.of(Set.of("drivetrain"), "^(?!.*(drive ?shaft|prop ?shaft)).*\\bmount", "engine-mounts"),
            Fix.of(Set.of("engine-mounts"), "bumper|bonnet|tailgate|\\bwing\\b", "body-panels"),
            Fix.of(Set.of("engine-mounts"), "drive ?shaft|prop ?shaft", "drivetrain"),
            Fix.of(Set.of("fuel-injection"), "^(?!.*(rail|ramp)).*injector", "fuel-injectors"),
            Fix.of(Set.of("cooling-ac"), "compressor|^(?!.*(pipe|hose)).*(\\ba/?c\\b|air con\\w*).*pump", "ac-compressors"),
            // Turbo oil feed and return pipes, intake pipes and intercooler hoses, like the rest of the engine's pipes.
            Fix.of(Set.of("turbochargers"), "pipe|hose", "engine-parts"),
            Fix.of(Set.of("turbochargers"), "vacuum valve|regulator", "electrics-sensors"),
            Fix.of(Set.of("fuel-injection"), "^(?!.*(fuel|inject)).*turbo", "engine-parts"),
            Fix.of(Set.of("egr-emissions"), "\\begr\\b", "egr-valves"),
            Fix.of(Set.of("air-intake", "fuel-injection", "egr-emissions", "ecus-electronics", "engine-parts", "turbochargers"),
                    "^(?!.*(dpf|particulate|particular filter|catalytic)).*(sensor|solenoid)", "electrics-sensors"),
            Fix.of(Set.of("engine-parts"), "^(?!.*(pulley|cover|sensor)).*crankshaft", "crankshafts"),
            Fix.of(Set.of("engine-parts"), "camshaft", "camshafts"),
            Fix.of(Set.of("engine-parts"), "^(?!.*pipe).*sump", "sumps-pans"),
            Fix.of(Set.of("engine-parts"), "oil pump", "oil-pumps"),
            Fix.of(Set.of("engine-parts"), "^(?!.*(bracket|mount|pipe|hose|gasket)).*mani ?fold", "manifolds"),
            Fix.of(Set.of("instruments-infotainment"), "^(?!.*(speedo|instrument|cluster|gauge))", "audio-media"));

    /** The shop category for a listing: the eBay category's, unless the title shows it belongs elsewhere (FIXES). */
    static String refine(String categoryId, String title) {
        if (title == null) {
            return categoryId;
        }
        return FIXES.stream()
                .filter(f -> f.from().contains(categoryId) && f.title().matcher(title).find())
                .map(Fix::to)
                .findFirst()
                .orElse(categoryId);
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
