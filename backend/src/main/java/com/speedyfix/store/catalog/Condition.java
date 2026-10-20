package com.speedyfix.store.catalog;

public enum Condition {
    USED("Used"),
    REMANUFACTURED("Remanufactured"),
    NEW("New");

    private final String label;

    Condition(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
