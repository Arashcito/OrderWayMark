package com.orderwaymark.planner;

import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Fixed quotes keyed by warehouse and box contents (order of skus in a box doesn't matter). */
public final class QuoteTable implements QuoteProvider {
    private final Map<String, Quote> quotes = new HashMap<>();

    public QuoteTable put(String warehouseId, Collection<String> skus, long priceCents, LocalDate arrives) {
        quotes.put(key(warehouseId, skus), new Quote(priceCents, arrives));
        return this;
    }

    public QuoteTable copy() {
        QuoteTable t = new QuoteTable();
        t.quotes.putAll(quotes);
        return t;
    }

    @Override
    public Optional<Quote> quote(Warehouse warehouse, List<LineItem> contents) {
        return Optional.ofNullable(quotes.get(key(warehouse.id(), contents.stream().map(LineItem::sku).toList())));
    }

    private static String key(String warehouseId, Collection<String> skus) {
        return warehouseId + "|" + String.join("+", skus.stream().sorted().toList());
    }
}
