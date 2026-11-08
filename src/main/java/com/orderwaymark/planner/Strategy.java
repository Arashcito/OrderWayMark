package com.orderwaymark.planner;

public enum Strategy {
    /**
     * Enumerate every assignment, keep valid plans, rank by shipping + expected future stock cost,
     * then boxes, then tie-break. Without a forecast this equals {@link #CHEAPEST_TODAY}.
     */
    OPTIMAL("Waymark planner"),
    /** Pick each item's cheapest single-item location, then price and validate the result. */
    CHEAPEST_PER_ITEM("Cheapest per item"),
    /** Among valid plans with the fewest boxes, pick the cheapest. */
    FEWEST_BOXES_FIRST("Fewest boxes first"),
    /** Cheapest valid plan by today's shipping price alone, ignoring the stock it uses up. */
    CHEAPEST_TODAY("Cheapest today");

    private final String label;

    Strategy(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
