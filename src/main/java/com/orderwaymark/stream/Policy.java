package com.orderwaymark.stream;

import com.orderwaymark.planner.Strategy;

/** How each order in the stream is sourced. */
public enum Policy {
    INVENTORY_AWARE("Inventory-aware (Waymark)", Strategy.OPTIMAL, true),
    CHEAPEST_TODAY("Cheapest today", Strategy.CHEAPEST_TODAY, false),
    CHEAPEST_PER_ITEM("Cheapest per item", Strategy.CHEAPEST_PER_ITEM, false),
    FEWEST_BOXES_FIRST("Fewest boxes first", Strategy.FEWEST_BOXES_FIRST, false);

    final String label;
    final Strategy strategy;
    final boolean usesForecast;

    Policy(String label, Strategy strategy, boolean usesForecast) {
        this.label = label;
        this.strategy = strategy;
        this.usesForecast = usesForecast;
    }

    public String label() {
        return label;
    }
}
