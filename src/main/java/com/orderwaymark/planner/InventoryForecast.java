package com.orderwaymark.planner;

import java.util.HashMap;
import java.util.Map;

/**
 * Expected-future-cost valuation in the spirit of Acimovic &amp; Graves (2015).
 *
 * <p>For each (warehouse, sku): {@code expectedDemand} is how many units customers near that
 * warehouse are expected to order before it is restocked (Poisson), and {@code extraCents} is how
 * much more it costs to serve one of them from the next-best warehouse. Taking one unit from
 * {@code q} on hand creates a future shortfall exactly when demand D &ge; q, so its expected cost is
 * {@code P(D >= q) * extraCents}; taking k units sums that over q, q-1, ..., q-k+1.
 */
public final class InventoryForecast implements InventoryValuation {

    public record Entry(double expectedDemand, long extraCents) {
        public Entry {
            if (expectedDemand < 0 || extraCents < 0) throw new IllegalArgumentException("must be >= 0");
        }
    }

    private final Map<String, Entry> entries = new HashMap<>();

    public InventoryForecast put(String warehouseId, String sku, double expectedDemand, long extraCents) {
        entries.put(warehouseId + "|" + sku, new Entry(expectedDemand, extraCents));
        return this;
    }

    public Entry get(String warehouseId, String sku) {
        return entries.get(warehouseId + "|" + sku);
    }

    @Override
    public Cost value(Warehouse w, LineItem item, int onHand, int units) {
        Entry e = get(w.id(), item.sku());
        if (e == null) return Cost.ZERO;
        long cents = expectedCostCents(e.expectedDemand(), e.extraCents(), onHand, units);
        if (cents == 0) return Cost.ZERO;
        double p = probAtLeast(e.expectedDemand(), onHand);
        String what = onHand == units ? (units == 1 ? "last " + item.name() : "all " + onHand + " " + item.name())
                : units + " of " + onHand + " " + item.name();
        String note = ("Uses %s's %s: %.0f%% chance that local demand (%.1f expected before restock) needs it, "
                + "and that customer would then cost %s more from another warehouse. Expected future cost %s.")
                .formatted(w.name(), what, 100 * p, e.expectedDemand(), Money.format(e.extraCents()), Money.format(cents));
        return new Cost(cents, note);
    }

    /** P(D >= n) for D ~ Poisson(lambda). */
    public static double probAtLeast(double lambda, int n) {
        if (n <= 0) return 1.0;
        if (lambda <= 0) return 0.0;
        double term = Math.exp(-lambda); // P(D = 0)
        double cdf = term;
        for (int k = 1; k < n; k++) {
            term *= lambda / k;
            cdf += term;
        }
        return Math.max(0.0, Math.min(1.0, 1.0 - cdf));
    }

    /** Expected future cost of taking {@code units} from {@code onHand}. */
    public static long expectedCostCents(double lambda, long extraCents, int onHand, int units) {
        double sum = 0;
        for (int j = 0; j < units; j++) sum += probAtLeast(lambda, onHand - j);
        return Math.round(sum * extraCents);
    }
}
