package com.orderwaymark.planner;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/** Items to ship and the date by which every one of them must arrive. */
public record Order(String id, List<LineItem> items, LocalDate deadline) {
    public Order {
        Objects.requireNonNull(deadline, "deadline");
        items = List.copyOf(items);
        if (items.isEmpty()) throw new IllegalArgumentException("order needs at least one item");
    }
}
