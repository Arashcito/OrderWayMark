package com.orderwaymark.planner;

import java.time.LocalDate;
import java.util.List;

/** One package leaving one warehouse. {@code quote} is null when no carrier quote exists. */
public record Box(String warehouseId, String warehouseName, List<LineItem> contents, Quote quote) {
    public Box {
        contents = List.copyOf(contents);
    }

    public boolean priced() {
        return quote != null;
    }

    public Long priceCents() {
        return quote == null ? null : quote.priceCents();
    }

    public LocalDate arrives() {
        return quote == null ? null : quote.arrives();
    }
}
