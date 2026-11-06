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
 * @param rejections empty when the plan is valid
 */
public record Candidate(List<String> assignment, List<Integer> tieBreakKey, List<Box> boxes,
                        long totalCents, List<String> rejections) {

    public Candidate {
        assignment = List.copyOf(assignment);
        tieBreakKey = List.copyOf(tieBreakKey);
        boxes = List.copyOf(boxes);
        rejections = List.copyOf(rejections);
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

    /** Cost, then fewer boxes, then stable warehouse-order tie-break. */
    public static final Comparator<Candidate> BY_COST =
            Comparator.comparingLong(Candidate::totalCents)
                    .thenComparingInt(Candidate::boxCount)
                    .thenComparing(Candidate::compareTieBreak);

    /** Fewer boxes, then cost, then the same stable tie-break. */
    public static final Comparator<Candidate> BY_BOXES =
            Comparator.comparingInt(Candidate::boxCount)
                    .thenComparingLong(Candidate::totalCents)
                    .thenComparing(Candidate::compareTieBreak);
}
