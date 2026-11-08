package com.orderwaymark.stream;

import com.orderwaymark.planner.InventoryForecast;
import com.orderwaymark.planner.InventoryValuation;
import com.orderwaymark.planner.LineItem;
import com.orderwaymark.planner.Quote;
import com.orderwaymark.planner.QuoteProvider;
import com.orderwaymark.planner.Warehouse;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class ReplayTest {

    static final LocalDate DAY0 = LocalDate.of(2026, 1, 5);
    static final LineItem K = new LineItem("k", "Keyboard");

    /** Hubs A and B, one keyboard each. "mid" customers are $1 cheaper from A; "a" customers are $5 cheaper from A. */
    static ReplayWorld world(int restockDays, int transitDays) {
        return new ReplayWorld() {
            public List<Warehouse> warehouses() {
                return List.of(new Warehouse("A", "Hub A", Map.of("k", 1)), new Warehouse("B", "Hub B", Map.of("k", 1)));
            }

            public int restockEveryDays() {
                return restockDays;
            }

            public LocalDate firstDay() {
                return DAY0;
            }

            public QuoteProvider quotes(StreamOrder o) {
                long b = o.customerKey().equals("mid") ? 600 : 1000;
                return (w, contents) -> Optional.of(new Quote(w.id().equals("A") ? 500 : b,
                        o.placedAt().toLocalDate().plusDays(transitDays)));
            }

            public InventoryValuation forecast(StreamOrder o, int daysLeft) {
                // A's own "a" customers are expected to want 2 keyboards; serving one from B costs $5 more.
                return new InventoryForecast().put("A", "k", 2.0, 500);
            }
        };
    }

    static StreamOrder order(String id, int day, String customer) {
        return new StreamOrder(id, DAY0.plusDays(day).atTime(10, 0), customer, List.of(K), DAY0.plusDays(day + 3));
    }

    @Test
    void inventoryAwareSavesTheScarceUnitForTheCustomerWhoNeedsItMost() {
        ReplayWorld w = world(0, 1);
        List<StreamOrder> stream = List.of(order("o1", 0, "mid"), order("o2", 0, "a"));

        ReplayResult myopic = Replay.run(w, stream, Policy.CHEAPEST_TODAY);
        ReplayResult aware = Replay.run(w, stream, Policy.INVENTORY_AWARE);

        assertThat(myopic.shippingCents()).isEqualTo(500 + 1000); // mid takes A's keyboard; "a" pays for B
        assertThat(aware.shippingCents()).isEqualTo(600 + 500);   // mid pays $1 more; "a" ships from A
        assertThat(myopic.farUnits()).isEqualTo(1);
        assertThat(aware.farUnits()).isEqualTo(1);
        assertThat(aware.onTime()).isEqualTo(2);
    }

    @Test
    void stockDepletesAndUnfilledOrdersAreCounted() {
        ReplayWorld w = world(0, 1);
        List<StreamOrder> stream = List.of(order("o1", 0, "a"), order("o2", 0, "a"), order("o3", 0, "a"));
        for (Policy p : Policy.values()) {
            ReplayResult r = Replay.run(w, stream, p);
            assertThat(r.shipped()).as(p.name()).isEqualTo(2);
            assertThat(r.unfilled()).as(p.name()).isEqualTo(1);
        }
    }

    @Test
    void restockRefillsStock() {
        ReplayWorld w = world(1, 1);
        List<StreamOrder> stream = List.of(order("o1", 0, "a"), order("o2", 0, "a"), order("o3", 1, "a"), order("o4", 1, "a"));
        ReplayResult r = Replay.run(w, stream, Policy.CHEAPEST_TODAY);
        assertThat(r.unfilled()).isZero();
        assertThat(r.shippingCents()).isEqualTo(2 * (500 + 1000));
    }

    @Test
    void ordersWithNoOnTimePlanShipLate() {
        ReplayWorld w = world(0, 10); // every box takes 10 days, deadlines are 3 days out
        ReplayResult r = Replay.run(w, List.of(order("o1", 0, "a")), Policy.INVENTORY_AWARE);
        assertThat(r.late()).isEqualTo(1);
        assertThat(r.onTime()).isZero();
        assertThat(r.unfilled()).isZero();
    }

    @Test
    void deterministic() {
        ReplayWorld w = world(2, 1);
        List<StreamOrder> stream = List.of(order("o1", 0, "mid"), order("o2", 0, "a"), order("o3", 1, "mid"),
                order("o4", 2, "a"), order("o5", 3, "mid"));
        for (Policy p : Policy.values()) {
            assertThat(Replay.run(w, stream, p)).isEqualTo(Replay.run(w, stream, p));
        }
    }

}
