package com.orderwaymark.planner;

public enum Strategy {
    /** Enumerate every assignment, keep valid plans, rank by cost then boxes then tie-break. */
    OPTIMAL("Waymark planner"),
    /** Pick each item's cheapest single-item location, then price and validate the result. */
    CHEAPEST_PER_ITEM("Cheapest per item"),
    /** Among valid plans with the fewest boxes, pick the cheapest. */
    FEWEST_BOXES_FIRST("Fewest boxes first");

    private final String label;

    Strategy(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
