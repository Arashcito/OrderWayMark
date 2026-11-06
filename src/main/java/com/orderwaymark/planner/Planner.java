package com.orderwaymark.planner;

import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Chooses the cheapest plan that gets the whole order there on time.
 *
 * <p>Plain Java, no framework. For n items and w warehouses it enumerates all w^n assignments
 * (4 for the two-item, two-warehouse demo), groups items by warehouse into one box per warehouse,
 * prices each box, rejects plans with insufficient stock, missing quotes or a late box, and ranks
 * the rest by total cents, then fewer boxes, then a stable warehouse-order tie-break.
 */
public final class Planner {

    /** Guard against accidental combinatorial blow-up; the demo needs 2^2. */
    public static final int MAX_ASSIGNMENTS = 4096;

    private final List<Warehouse> warehouses;
    private final QuoteProvider quotes;

    public Planner(List<Warehouse> warehouses, QuoteProvider quotes) {
        if (warehouses.isEmpty()) throw new IllegalArgumentException("need at least one warehouse");
        this.warehouses = List.copyOf(warehouses);
        this.quotes = quotes;
    }

    public PlanReport plan(Order order) {
        List<Candidate> candidates = enumerate(order);

        Optional<Candidate> best = candidates.stream().filter(Candidate::valid).min(Candidate.BY_COST);
        StrategyResult optimal = optimal(order, candidates, best);
        StrategyResult perItem = cheapestPerItem(order, candidates);
        StrategyResult fewest = fewestBoxesFirst(order, candidates);

        return new PlanReport(order, optimal.status(), best.orElse(null), optimal.explanation(),
                candidates, List.of(optimal, perItem, fewest));
    }

    // ---------------------------------------------------------------- enumeration

    List<Candidate> enumerate(Order order) {
        int n = order.items().size();
        int w = warehouses.size();
        double total = Math.pow(w, n);
        if (total > MAX_ASSIGNMENTS) {
            throw new IllegalArgumentException("too many assignments to enumerate: " + (long) total);
        }
        List<Candidate> out = new ArrayList<>();
        int[] idx = new int[n];
        for (int k = 0; k < (int) total; k++) {
            int rem = k;
            for (int i = n - 1; i >= 0; i--) { // first item varies slowest -> natural reading order
                idx[i] = rem % w;
                rem /= w;
            }
            out.add(evaluate(order, idx.clone()));
        }
        return out;
    }

    /** Builds, prices and validates the plan for one assignment (warehouse index per item). */
    Candidate evaluate(Order order, int[] warehouseIndexPerItem) {
        List<LineItem> items = order.items();

        // Group items by warehouse, in declared warehouse order, before pricing each box.
        Map<Integer, List<LineItem>> grouped = new java.util.TreeMap<>();
        for (int i = 0; i < items.size(); i++) {
            grouped.computeIfAbsent(warehouseIndexPerItem[i], x -> new ArrayList<>()).add(items.get(i));
        }

        List<String> rejections = new ArrayList<>();
        List<Box> boxes = new ArrayList<>();
        long total = 0;
        for (var e : grouped.entrySet()) {
            Warehouse wh = warehouses.get(e.getKey());
            List<LineItem> contents = e.getValue();

            Map<String, Long> needed = contents.stream()
                    .collect(Collectors.groupingBy(LineItem::sku, LinkedHashMap::new, Collectors.counting()));
            needed.forEach((sku, qty) -> {
                int onHand = wh.unitsOf(sku);
                if (onHand < qty) {
                    rejections.add("Insufficient stock: %s has %d %s, needs %d."
                            .formatted(wh.name(), onHand, nameOf(contents, sku), qty));
                }
            });

            Quote q = quotes.quote(wh, contents).orElse(null);
            Box box = new Box(wh.id(), wh.name(), contents, q);
            boxes.add(box);
            if (q == null) {
                rejections.add("No shipping quote for a %s box with %s.".formatted(wh.name(), describe(contents)));
                continue;
            }
            total += q.priceCents();
            if (q.arrives().isAfter(order.deadline())) {
                rejections.add("Late box: %s box with %s arrives %s, after the %s deadline."
                        .formatted(wh.name(), describe(contents), day(q.arrives()), day(order.deadline())));
            }
        }

        List<String> assignment = new ArrayList<>();
        List<Integer> key = new ArrayList<>();
        for (int i : warehouseIndexPerItem) {
            assignment.add(warehouses.get(i).id());
            key.add(i);
        }
        return new Candidate(assignment, key, boxes, total, rejections);
    }

    // ---------------------------------------------------------------- strategies

    private StrategyResult optimal(Order order, List<Candidate> all, Optional<Candidate> best) {
        if (best.isEmpty()) {
            return new StrategyResult(Strategy.OPTIMAL, Strategy.OPTIMAL.label(), PlanStatus.NO_ON_TIME_PLAN, null,
                    "NO_ON_TIME_PLAN: all %d possible plans were rejected (see reasons below).".formatted(all.size()));
        }
        Candidate win = best.get();
        List<Candidate> valid = all.stream().filter(Candidate::valid).sorted(Candidate.BY_COST).toList();
        StringBuilder why = new StringBuilder();
        why.append("Chose %s for %s: the cheapest of %d valid plan%s"
                .formatted(describePlan(win), Money.format(win.totalCents()), valid.size(), valid.size() == 1 ? "" : "s"));
        why.append(", with everything arriving by %s for a %s deadline.".formatted(day(win.latestArrival()), day(order.deadline())));
        if (valid.size() > 1) {
            Candidate runnerUp = valid.get(1);
            long diff = runnerUp.totalCents() - win.totalCents();
            if (diff > 0) {
                why.append(" Next best was %s at %s (%s more)."
                        .formatted(describePlan(runnerUp), Money.format(runnerUp.totalCents()), Money.format(diff)));
            } else if (runnerUp.boxCount() > win.boxCount()) {
                why.append(" It tied on price with %s and won on fewer boxes.".formatted(describePlan(runnerUp)));
            } else {
                why.append(" It tied with %s on price and box count and won the stable tie-break (earlier-listed warehouse)."
                        .formatted(describePlan(runnerUp)));
            }
        }
        long rejected = all.size() - valid.size();
        if (rejected > 0) why.append(" %d plan%s rejected.".formatted(rejected, rejected == 1 ? " was" : "s were"));
        return new StrategyResult(Strategy.OPTIMAL, Strategy.OPTIMAL.label(), PlanStatus.PLANNED, win, why.toString());
    }

    private StrategyResult cheapestPerItem(Order order, List<Candidate> all) {
        int[] pick = new int[order.items().size()];
        List<String> picks = new ArrayList<>();
        for (int i = 0; i < order.items().size(); i++) {
            LineItem item = order.items().get(i);
            int bestIdx = -1;
            long bestPrice = Long.MAX_VALUE;
            for (int w = 0; w < warehouses.size(); w++) {
                Warehouse wh = warehouses.get(w);
                if (wh.unitsOf(item.sku()) < 1) continue;
                Optional<Quote> q = quotes.quote(wh, List.of(item));
                if (q.isPresent() && q.get().priceCents() < bestPrice) { // strict '<' keeps earlier warehouse on ties
                    bestPrice = q.get().priceCents();
                    bestIdx = w;
                }
            }
            if (bestIdx < 0) {
                return new StrategyResult(Strategy.CHEAPEST_PER_ITEM, Strategy.CHEAPEST_PER_ITEM.label(),
                        PlanStatus.NO_ON_TIME_PLAN, null,
                        "No warehouse has %s in stock with a single-item quote.".formatted(item.name()));
            }
            pick[i] = bestIdx;
            picks.add("%s from %s (%s)".formatted(item.name(), warehouses.get(bestIdx).name(), Money.format(bestPrice)));
        }
        Candidate plan = evaluate(order, pick);
        String head = "Picked " + String.join(", ", picks) + ", then priced the resulting "
                + plan.boxCount() + " box" + (plan.boxCount() == 1 ? "" : "es") + ": " + Money.format(plan.totalCents()) + ".";
        if (!plan.valid()) {
            return new StrategyResult(Strategy.CHEAPEST_PER_ITEM, Strategy.CHEAPEST_PER_ITEM.label(), PlanStatus.REJECTED,
                    plan, head + " Rejected: " + String.join(" ", plan.rejections()));
        }
        return new StrategyResult(Strategy.CHEAPEST_PER_ITEM, Strategy.CHEAPEST_PER_ITEM.label(), PlanStatus.PLANNED,
                plan, head + compareToOptimal(all, plan));
    }

    private StrategyResult fewestBoxesFirst(Order order, List<Candidate> all) {
        Optional<Candidate> pick = all.stream().filter(Candidate::valid).min(Candidate.BY_BOXES);
        if (pick.isEmpty()) {
            return new StrategyResult(Strategy.FEWEST_BOXES_FIRST, Strategy.FEWEST_BOXES_FIRST.label(),
                    PlanStatus.NO_ON_TIME_PLAN, null, "No valid plan with any number of boxes.");
        }
        Candidate p = pick.get();
        String why = "Fewest boxes among valid plans is %d; cheapest of those is %s at %s."
                .formatted(p.boxCount(), describePlan(p), Money.format(p.totalCents())) + compareToOptimal(all, p);
        return new StrategyResult(Strategy.FEWEST_BOXES_FIRST, Strategy.FEWEST_BOXES_FIRST.label(),
                PlanStatus.PLANNED, p, why);
    }

    private String compareToOptimal(List<Candidate> all, Candidate p) {
        return all.stream().filter(Candidate::valid).min(Candidate.BY_COST)
                .map(best -> best.totalCents() < p.totalCents()
                        ? " That is %s more than the Waymark plan.".formatted(Money.format(p.totalCents() - best.totalCents()))
                        : " Same cost as the Waymark plan.")
                .orElse("");
    }

    // ---------------------------------------------------------------- text helpers

    /** e.g. "Ottawa ships both in 1 box" or "Montréal: Keyboard + Ottawa: Mouse". */
    public static String describePlan(Candidate c) {
        if (c.boxCount() == 1) {
            Box b = c.boxes().get(0);
            return "%s ships everything in 1 box".formatted(b.warehouseName());
        }
        return c.boxes().stream()
                .map(b -> b.warehouseName() + ": " + describe(b.contents()))
                .collect(Collectors.joining(" + ")) + " (" + c.boxCount() + " boxes)";
    }

    static String describe(List<LineItem> contents) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        Map<String, String> names = new HashMap<>();
        for (LineItem li : contents) {
            counts.merge(li.sku(), 1, Integer::sum);
            names.put(li.sku(), li.name());
        }
        return counts.entrySet().stream()
                .map(e -> e.getValue() == 1 ? names.get(e.getKey()) : e.getValue() + "× " + names.get(e.getKey()))
                .collect(Collectors.joining(", "));
    }

    private static String nameOf(List<LineItem> contents, String sku) {
        return contents.stream().filter(li -> li.sku().equals(sku)).map(LineItem::name).findFirst().orElse(sku);
    }

    static String day(LocalDate d) {
        if (d == null) return "unknown";
        return d.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.ENGLISH) + " " + d;
    }
}
