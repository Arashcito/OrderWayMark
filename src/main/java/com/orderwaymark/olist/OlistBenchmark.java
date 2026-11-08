package com.orderwaymark.olist;

import com.orderwaymark.planner.Money;
import com.orderwaymark.planner.PlanReport;
import com.orderwaymark.planner.PlanStatus;
import com.orderwaymark.planner.Planner;
import com.orderwaymark.planner.Strategy;
import com.orderwaymark.planner.StrategyResult;

import java.io.PrintStream;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Optional offline benchmark: runs every strategy over up to 100 real two-item Olist baskets.
 * Usage: {@code java -jar target/orderwaymark.jar benchmark [dataDir] [limit]}.
 */
public final class OlistBenchmark {

    public static final int MAX_ORDERS = 100;

    private OlistBenchmark() {
    }

    public static int run(String[] args, PrintStream out) throws Exception {
        if (args.length > 0 && args[0].equals("stream")) {
            return OlistStreamBenchmark.run(java.util.Arrays.copyOfRange(args, 1, args.length), out);
        }
        Path dir = Path.of(args.length > 0 ? args[0] : "data/olist");
        int limit = Math.min(MAX_ORDERS, args.length > 1 ? Integer.parseInt(args[1]) : MAX_ORDERS);
        if (!OlistImporter.available(dir)) {
            out.printf("Olist CSVs not found in %s. Expected %s, %s and %s.%n"
                            + "The seeded demo does not need them; see README for the download.%n",
                    dir.toAbsolutePath(), OlistImporter.ORDERS, OlistImporter.ORDER_ITEMS, OlistImporter.PRODUCTS);
            return 1;
        }
        List<OlistBasket> baskets = OlistImporter.load(dir, limit);
        Summary s = summarize(baskets);
        print(s, baskets.size(), dir, out);
        return 0;
    }

    /** Aggregate results per strategy. */
    public static final class Summary {
        public final Map<Strategy, int[]> counts = new EnumMap<>(Strategy.class); // planned, rejected, noPlan, oneBox, twoBox
        public final Map<Strategy, Long> totalCents = new EnumMap<>(Strategy.class);
        public int bothPlannedVsPerItem, cheaperThanPerItem, perItemFailedButWaymarkPlanned;
        public long savedVsPerItemCents;
        public int bothPlannedVsFewest, cheaperThanFewest;
        public long savedVsFewestCents;
    }

    public static Summary summarize(List<OlistBasket> baskets) {
        Summary s = new Summary();
        for (Strategy st : Strategy.values()) {
            s.counts.put(st, new int[5]);
            s.totalCents.put(st, 0L);
        }
        for (OlistBasket b : baskets) {
            Simulation sim = new Simulation(b);
            PlanReport r = new Planner(sim.warehouses, sim.quotes).plan(sim.order);
            for (StrategyResult sr : r.strategies()) {
                int[] c = s.counts.get(sr.strategy());
                switch (sr.status()) {
                    case PLANNED -> {
                        c[0]++;
                        c[sr.plan().boxCount() == 1 ? 3 : 4]++;
                        s.totalCents.merge(sr.strategy(), sr.plan().totalCents(), Long::sum);
                    }
                    case REJECTED -> c[1]++;
                    case NO_ON_TIME_PLAN -> c[2]++;
                }
            }
            StrategyResult w = r.result(Strategy.OPTIMAL);
            StrategyResult p = r.result(Strategy.CHEAPEST_PER_ITEM);
            StrategyResult f = r.result(Strategy.FEWEST_BOXES_FIRST);
            if (w.status() == PlanStatus.PLANNED) {
                if (p.status() == PlanStatus.PLANNED) {
                    s.bothPlannedVsPerItem++;
                    long d = p.plan().totalCents() - w.plan().totalCents();
                    if (d > 0) s.cheaperThanPerItem++;
                    s.savedVsPerItemCents += d;
                } else {
                    s.perItemFailedButWaymarkPlanned++;
                }
                if (f.status() == PlanStatus.PLANNED) {
                    s.bothPlannedVsFewest++;
                    long d = f.plan().totalCents() - w.plan().totalCents();
                    if (d > 0) s.cheaperThanFewest++;
                    s.savedVsFewestCents += d;
                }
            }
        }
        return s;
    }

    private static void print(Summary s, int n, Path dir, PrintStream out) {
        out.printf("%nOrderWaymark benchmark — %d real two-item Olist baskets from %s%n", n, dir);
        out.println("REAL:      basket contents (product ids, categories, weights), purchase date,");
        out.println("           historical order_estimated_delivery_date (used as the deadline proxy).");
        out.println("SIMULATED: Montréal/Ottawa warehouse stock, shipping quotes, delivery estimates.");
        out.println();
        out.printf("%-20s %8s %9s %8s %7s %7s %12s %12s%n",
                "Strategy", "Planned", "Rejected", "No plan", "1 box", "2 boxes", "Total cost", "Avg/planned");
        for (Strategy st : Strategy.values()) {
            int[] c = s.counts.get(st);
            long tot = s.totalCents.get(st);
            out.printf("%-20s %8d %9d %8d %7d %7d %12s %12s%n", st.label(), c[0], c[1], c[2], c[3], c[4],
                    Money.format(tot), c[0] == 0 ? "—" : Money.format(Math.round((double) tot / c[0])));
        }
        out.println();
        out.printf("Waymark vs cheapest per item: cheaper on %d of %d orders both planned, saving %s in total;%n",
                s.cheaperThanPerItem, s.bothPlannedVsPerItem, Money.format(s.savedVsPerItemCents));
        out.printf("  cheapest-per-item produced an invalid (late / out-of-stock) plan on %d orders the planner solved.%n",
                s.perItemFailedButWaymarkPlanned);
        out.printf("Waymark vs fewest boxes first: cheaper on %d of %d orders, saving %s in total.%n",
                s.cheaperThanFewest, s.bothPlannedVsFewest, Money.format(s.savedVsFewestCents));
        out.println("(Totals compare only orders where both strategies planned; costs are simulated.)");
    }
}
