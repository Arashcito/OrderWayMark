package com.orderwaymark.olist;

import com.orderwaymark.planner.LineItem;
import com.orderwaymark.planner.Order;
import com.orderwaymark.planner.Quote;
import com.orderwaymark.planner.QuoteProvider;
import com.orderwaymark.planner.Warehouse;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SplittableRandom;

/**
 * SIMULATED world for one real Olist basket: Montréal/Ottawa stock, box quotes and arrival dates.
 * Deterministic: every number is derived from a hash of the order id, product id and warehouse,
 * so reruns give identical results.
 */
final class Simulation {

    static final List<String[]> WAREHOUSES = List.of(new String[]{"MTL", "Montréal"}, new String[]{"OTT", "Ottawa"});

    final List<Warehouse> warehouses;
    final QuoteProvider quotes;
    final Order order;

    Simulation(OlistBasket basket) {
        Map<String, Integer> weights = new HashMap<>();
        List<LineItem> items = basket.items().stream().map(p -> {
            weights.put(p.productId(), p.weightGrams());
            return new LineItem(p.productId(), p.category() + " #" + p.productId().substring(0, Math.min(6, p.productId().length())));
        }).toList();
        this.order = new Order(basket.orderId(), items, basket.estimatedDelivery());

        this.warehouses = WAREHOUSES.stream().map(w -> {
            Map<String, Integer> stock = new HashMap<>();
            for (var p : basket.items()) {
                SplittableRandom r = rng(w[0], p.productId());
                stock.put(p.productId(), r.nextInt(100) < 80 ? 1 + r.nextInt(3) : 0); // 80% chance in stock
            }
            return new Warehouse(w[0], w[1], stock);
        }).toList();

        LocalDate purchase = basket.purchaseDate();
        String orderId = basket.orderId();
        this.quotes = (wh, contents) -> {
            SplittableRandom r = rng(wh.id(), orderId);
            long base = 400 + r.nextInt(500);       // 4.00–8.99 per box
            long perKg = 80 + r.nextInt(170);       // 0.80–2.49 per kg
            long handling = 200 + r.nextInt(1000);  // 2.00–11.99 extra per additional item in the same box
            int transit = 3 + r.nextInt(19);        // 3–21 days
            int consolidation = r.nextInt(4);       // multi-item boxes may wait 0–3 extra days
            double kg = contents.stream().mapToInt(li -> weights.getOrDefault(li.sku(), 0)).sum() / 1000.0;
            long price = base + Math.round(perKg * kg) + handling * (contents.size() - 1);
            int days = transit + (contents.size() > 1 ? consolidation : 0);
            return Optional.of(new Quote(price, purchase.plusDays(days)));
        };
    }

    private static SplittableRandom rng(String... parts) {
        long h = 1125899906842597L;
        for (String p : parts) for (int i = 0; i < p.length(); i++) h = 31 * h + p.charAt(i);
        return new SplittableRandom(h);
    }
}
