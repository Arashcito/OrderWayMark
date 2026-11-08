package com.orderwaymark.planner;

/**
 * Prices the future: what it is expected to cost later if this order uses {@code units} of an item
 * from a warehouse that currently has {@code onHand}. Zero means stock is not scarce there.
 */
@FunctionalInterface
public interface InventoryValuation {

    record Cost(long cents, String note) {
        public static final Cost ZERO = new Cost(0, null);
    }

    Cost value(Warehouse warehouse, LineItem item, int onHand, int units);

    /** Ignores the future: the planner then ranks by today's shipping price alone. */
    InventoryValuation NONE = (w, item, onHand, units) -> Cost.ZERO;
}
