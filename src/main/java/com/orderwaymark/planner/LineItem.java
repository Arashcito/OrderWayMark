package com.orderwaymark.planner;

import java.util.Objects;

/** One unit of a product in an order. */
public record LineItem(String sku, String name) {
    public LineItem {
        Objects.requireNonNull(sku, "sku");
        name = (name == null || name.isBlank()) ? sku : name;
    }
}
