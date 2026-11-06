package com.orderwaymark.web;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Either {@code {"scenario": "two-boxes-win"}} or a full custom order:
 * deadline, items (skus), warehouses with stock, and box quotes in integer cents.
 */
public record PlanRequest(String scenario,
                          LocalDate deadline,
                          List<String> items,
                          List<WarehouseIn> warehouses,
                          List<QuoteIn> quotes) {

    public record WarehouseIn(String id, String name, Map<String, Integer> stock) {
    }

    public record QuoteIn(String warehouse, List<String> items, Long priceCents, LocalDate arrives) {
    }
}
