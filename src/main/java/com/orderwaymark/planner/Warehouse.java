package com.orderwaymark.planner;

import java.util.Map;
import java.util.Objects;

/** A shipping location with in-memory stock (sku -> units on hand). */
public record Warehouse(String id, String name, Map<String, Integer> stock) {
    public Warehouse {
        Objects.requireNonNull(id, "id");
        name = (name == null || name.isBlank()) ? id : name;
        stock = Map.copyOf(stock == null ? Map.of() : stock);
    }

    public int unitsOf(String sku) {
        return stock.getOrDefault(sku, 0);
    }

    public Warehouse withStock(String sku, int units) {
        var copy = new java.util.HashMap<>(stock);
        copy.put(sku, units);
        return new Warehouse(id, name, copy);
    }
}
