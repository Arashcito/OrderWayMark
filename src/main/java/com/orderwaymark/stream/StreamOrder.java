package com.orderwaymark.stream;

import com.orderwaymark.planner.LineItem;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** One order in a replayed stream, in purchase-time order. {@code customerKey} identifies where it ships to. */
public record StreamOrder(String orderId, LocalDateTime placedAt, String customerKey,
                          List<LineItem> items, LocalDate deadline) {
    public StreamOrder {
        items = List.copyOf(items);
    }
}
