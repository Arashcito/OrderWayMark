package com.orderwaymark.web;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Either {@code {"scenario": "two-boxes-win"}} or a full custom order:
 * deadline, items (skus), warehouses with stock, and box quotes in integer cents.
 *
 * <p>{@code ottawaCombinedQuoteCents} optionally replaces the Ottawa keyboard+mouse box price of a
 * preset scenario (integer cents, 0–100000). The box keeps that scenario's arrival date.
 */
public record PlanRequest(String scenario,
                          LocalDate deadline,
                          List<String> items,
                          List<WarehouseIn> warehouses,
                          List<QuoteIn> quotes,
                          BigDecimal ottawaCombinedQuoteCents) {

    public record WarehouseIn(String id, String name, Map<String, Integer> stock) {
    }

    public record QuoteIn(String warehouse, List<String> items, Long priceCents, LocalDate arrives) {
    }
}
