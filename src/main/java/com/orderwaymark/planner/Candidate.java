package com.orderwaymark.planner;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * One way of assigning every line item to a warehouse, already grouped into boxes and priced.
 *
 * @param assignment warehouse id per line item, in order-item order
 * @param tieBreakKey index of each item's warehouse in the declared warehouse list; compared
 *                    lexicographically so earlier-declared warehouses win exact ties
 * @param totalCents shipping paid today
 * @param inventoryCostCents expected future cost of the stock this plan uses up (0 without a forecast)
 * @param rejections empty when the plan is valid
 */
public record Candidate(List<String> assignment, List<Integer> tieBreakKey, List<Box> boxes,
                        long totalCents, long inventoryCostCents, List<String> inventoryNotes,
                        List<String> rejections) {

    public Candidate {
        assignment = List.copyOf(assignment);
        tieBreakKey = List.copyOf(tieBreakKey);
        boxes = List.copyOf(boxes);
        inventoryNotes = List.copyOf(inventoryNotes);
        rejections = List.copyOf(rejections);
    }

    /** Shipping today plus expected future stock cost: what the Waymark planner minimizes. */
    public long effectiveCents() {
        return totalCents + inventoryCostCents;
    }

    public boolean valid() {
        return rejections.isEmpty();
    }

    public int boxCount() {
        return boxes.size();
    }

    public LocalDate latestArrival() {
        return boxes.stream().map(Box::arrives).filter(Objects::nonNull)
                .max(Comparator.naturalOrder()).orElse(null);
    }

    static int compareTieBreak(Candidate a, Candidate b) {
        for (int i = 0; i < Math.min(a.tieBreakKey.size(), b.tieBreakKey.size()); i++) {
            int c = Integer.compare(a.tieBreakKey.get(i), b.tieBreakKey.get(i));
            if (c != 0) return c;
        }
        return Integer.compare(a.tieBreakKey.size(), b.tieBreakKey.size());
    }

    /** Effective cost (shipping + expected future stock cost), then fewer boxes, then stable tie-break. */
    public static final Comparator<Candidate> BY_COST =
            Comparator.comparingLong(Candidate::effectiveCents)
                    .thenComparingInt(Candidate::boxCount)
                    .thenComparing(Candidate::compareTieBreak);

    /** Today's shipping price only, then fewer boxes, then stable tie-break. */
    public static final Comparator<Candidate> BY_SHIPPING =
            Comparator.comparingLong(Candidate::totalCents)
                    .thenComparingInt(Candidate::boxCount)
                    .thenComparing(Candidate::compareTieBreak);

    /** Fewer boxes, then cost, then the same stable tie-break. */
    public static final Comparator<Candidate> BY_BOXES =
            Comparator.comparingInt(Candidate::boxCount)
                    .thenComparingLong(Candidate::totalCents)
                    .thenComparing(Candidate::compareTieBreak);
}
