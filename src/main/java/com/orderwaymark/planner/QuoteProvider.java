package com.orderwaymark.planner;

import java.util.List;
import java.util.Optional;

/** Prices one box: everything in {@code contents} shipped together from {@code warehouse}. */
@FunctionalInterface
public interface QuoteProvider {
    Optional<Quote> quote(Warehouse warehouse, List<LineItem> contents);
}
