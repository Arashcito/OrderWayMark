package com.orderwaymark.demo;

import com.orderwaymark.planner.LineItem;
import com.orderwaymark.planner.Order;
import com.orderwaymark.planner.QuoteTable;
import com.orderwaymark.planner.Warehouse;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Seeded demo data. Prices are synthetic and in integer cents.
 *
 * <pre>
 * Warehouse | Keyboard only | Mouse only | Both in one box
 * Montréal  |      $5       |     $8     |      $12
 * Ottawa    |      $7       |     $6     |       $8
 * </pre>
 * Everything is in stock and every box arrives Friday, which is also the requested date.
 */
public final class DemoScenarios {

    public static final String MTL = "MTL";
    public static final String OTT = "OTT";
    public static final String KEYBOARD = "keyboard";
    public static final String MOUSE = "mouse";

    /** Requested delivery date: a Friday. */
    public static final LocalDate FRIDAY = LocalDate.of(2026, 11, 13);
    public static final LocalDate SATURDAY = FRIDAY.plusDays(1);

    private DemoScenarios() {
    }

    public static List<Warehouse> seedWarehouses() {
        return List.of(
                new Warehouse(MTL, "Montréal", Map.of(KEYBOARD, 1, MOUSE, 1)),
                new Warehouse(OTT, "Ottawa", Map.of(KEYBOARD, 1, MOUSE, 1)));
    }

    public static QuoteTable seedQuotes() {
        return new QuoteTable()
                .put(MTL, List.of(KEYBOARD), 500, FRIDAY)
                .put(MTL, List.of(MOUSE), 800, FRIDAY)
                .put(MTL, List.of(KEYBOARD, MOUSE), 1200, FRIDAY)
                .put(OTT, List.of(KEYBOARD), 700, FRIDAY)
                .put(OTT, List.of(MOUSE), 600, FRIDAY)
                .put(OTT, List.of(KEYBOARD, MOUSE), 800, FRIDAY);
    }

    public static Order seedOrder() {
        return new Order("demo-1", List.of(new LineItem(KEYBOARD, "Keyboard"), new LineItem(MOUSE, "Mouse")), FRIDAY);
    }

    public static Scenario oneBoxWins() {
        return new Scenario("one-box-wins", "1. One box wins",
                "Seeded data. Ottawa's combined box ($8) beats splitting by cheapest item ($5 + $6 = $11).",
                seedWarehouses(), seedQuotes(), seedOrder());
    }

    public static Scenario twoBoxesWin() {
        return new Scenario("two-boxes-win", "2. Two boxes win",
                "Ottawa's combined quote rises to $14. Splitting (Montréal keyboard + Ottawa mouse, $11) is now cheapest; "
                        + "fewest-boxes-first settles for Montréal's $12 box.",
                seedWarehouses(), seedQuotes().put(OTT, List.of(KEYBOARD, MOUSE), 1400, FRIDAY), seedOrder());
    }

    public static Scenario stockChanges() {
        List<Warehouse> wh = List.of(seedWarehouses().get(0), seedWarehouses().get(1).withStock(KEYBOARD, 0));
        return new Scenario("stock-changes", "3. Stock changes the answer",
                "Ottawa runs out of keyboards. Every plan that ships a keyboard from Ottawa is rejected; the $11 split wins.",
                wh, seedQuotes(), seedOrder());
    }

    public static Scenario lateBox() {
        return new Scenario("late-box", "4a. One late box",
                "Like scenario 2, but Ottawa's mouse-only box arrives Saturday. The $11 split is late, so Montréal's $12 box wins.",
                seedWarehouses(),
                seedQuotes().put(OTT, List.of(KEYBOARD, MOUSE), 1400, FRIDAY).put(OTT, List.of(MOUSE), 600, SATURDAY),
                seedOrder());
    }

    public static Scenario allLate() {
        QuoteTable q = new QuoteTable()
                .put(MTL, List.of(KEYBOARD), 500, SATURDAY)
                .put(MTL, List.of(MOUSE), 800, SATURDAY)
                .put(MTL, List.of(KEYBOARD, MOUSE), 1200, SATURDAY)
                .put(OTT, List.of(KEYBOARD), 700, SATURDAY)
                .put(OTT, List.of(MOUSE), 600, SATURDAY)
                .put(OTT, List.of(KEYBOARD, MOUSE), 800, SATURDAY);
        return new Scenario("all-late", "4b. Nothing arrives on time",
                "Every carrier slips to Saturday. No plan meets the Friday deadline: NO_ON_TIME_PLAN.",
                seedWarehouses(), q, seedOrder());
    }

    public static Scenario priceTie() {
        return new Scenario("price-tie", "5. Price tie",
                "Ottawa's combined box costs $11, the same as the split. Equal price favours fewer boxes.",
                seedWarehouses(), seedQuotes().put(OTT, List.of(KEYBOARD, MOUSE), 1100, FRIDAY), seedOrder());
    }

    public static Map<String, Scenario> all() {
        Map<String, Scenario> m = new LinkedHashMap<>();
        for (Scenario s : List.of(oneBoxWins(), twoBoxesWin(), stockChanges(), lateBox(), allLate(), priceTie())) {
            m.put(s.id(), s);
        }
        return m;
    }

    public static Optional<Scenario> byId(String id) {
        return Optional.ofNullable(all().get(id));
    }
}
