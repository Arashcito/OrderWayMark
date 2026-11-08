package com.orderwaymark.stream;

import com.orderwaymark.planner.Box;
import com.orderwaymark.planner.Candidate;
import com.orderwaymark.planner.InventoryValuation;
import com.orderwaymark.planner.LineItem;
import com.orderwaymark.planner.Order;
import com.orderwaymark.planner.PlanReport;
import com.orderwaymark.planner.PlanStatus;
import com.orderwaymark.planner.Planner;
import com.orderwaymark.planner.QuoteProvider;
import com.orderwaymark.planner.StrategyResult;
import com.orderwaymark.planner.Warehouse;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Replays orders in time order against one shared, depleting inventory. Each order is sourced by
 * the policy, its units leave stock, and stock resets to order-up-to levels on restock days.
 *
 * <p>If no on-time plan exists the order still ships, late, using the same policy with the deadline
 * lifted. If no stock-feasible plan exists at all it is counted as unfilled.
 */
public final class Replay {

    private static final LocalDate NO_DEADLINE = LocalDate.of(9999, 12, 31);

    private Replay() {
    }

    public static ReplayResult run(ReplayWorld world, List<StreamOrder> orders, Policy policy) {
        List<Warehouse> base = world.warehouses();
        Map<String, Map<String, Integer>> stock = new HashMap<>();
        restock(stock, base);
        int period = world.restockEveryDays();
        long lastCycle = 0;

        int onTime = 0, late = 0, unfilled = 0;
        long shipping = 0, boxes = 0, units = 0, far = 0;

        for (StreamOrder o : orders) {
            long day = ChronoUnit.DAYS.between(world.firstDay(), o.placedAt().toLocalDate());
            int daysLeft = Integer.MAX_VALUE;
            if (period > 0) {
                long cycle = Math.floorDiv(day, period);
                if (cycle != lastCycle) {
                    restock(stock, base);
                    lastCycle = cycle;
                }
                daysLeft = (int) (period - Math.floorMod(day, period));
            }

            List<Warehouse> now = base.stream()
                    .map(w -> new Warehouse(w.id(), w.name(), stock.get(w.id()))).toList();
            QuoteProvider quotes = world.quotes(o);
            InventoryValuation valuation = policy.usesForecast ? world.forecast(o, daysLeft) : InventoryValuation.NONE;
            Planner planner = new Planner(now, quotes, valuation);

            Candidate plan = pick(planner.plan(new Order(o.orderId(), o.items(), o.deadline())), policy);
            boolean isLate = false;
            if (plan == null) {
                plan = pick(planner.plan(new Order(o.orderId(), o.items(), NO_DEADLINE)), policy);
                isLate = plan != null;
            }
            if (plan == null) {
                unfilled++;
                continue;
            }
            if (isLate) late++;
            else onTime++;

            shipping += plan.totalCents();
            boxes += plan.boxCount();
            for (Box b : plan.boxes()) {
                Map<String, Integer> s = stock.get(b.warehouseId());
                for (LineItem li : b.contents()) {
                    s.merge(li.sku(), -1, Integer::sum);
                    units++;
                    if (!b.warehouseId().equals(cheapestFor(now, quotes, li))) far++;
                }
            }
        }
        return new ReplayResult(policy, orders.size(), onTime, late, unfilled, shipping, boxes, units, far);
    }

    private static Candidate pick(PlanReport report, Policy policy) {
        StrategyResult r = report.result(policy.strategy);
        return r.status() == PlanStatus.PLANNED ? r.plan() : null;
    }

    /** Cheapest warehouse to ship this single item to this customer, ignoring stock. */
    static String cheapestFor(List<Warehouse> warehouses, QuoteProvider quotes, LineItem item) {
        String best = null;
        long bestCents = Long.MAX_VALUE;
        for (Warehouse w : warehouses) {
            var q = quotes.quote(w, List.of(item));
            if (q.isPresent() && q.get().priceCents() < bestCents) {
                bestCents = q.get().priceCents();
                best = w.id();
            }
        }
        return best;
    }

    private static void restock(Map<String, Map<String, Integer>> stock, List<Warehouse> base) {
        for (Warehouse w : base) stock.put(w.id(), new HashMap<>(w.stock()));
    }
}
