package com.lucasdunn.kothstats.capture;

public final class KoreKothDefinition {
    private final String name;
    private final String cornerOne;
    private final String cornerTwo;

    public KoreKothDefinition(String name, String cornerOne, String cornerTwo) {
        this.name = name;
        this.cornerOne = cornerOne;
        this.cornerTwo = cornerTwo;
    }

    public String getName() {
        return name;
    }

    public String getCornerOne() {
        return cornerOne;
    }

    public String getCornerTwo() {
        return cornerTwo;
    }
}
