package com.orderwaymark.olist;

import com.orderwaymark.stream.Policy;
import com.orderwaymark.stream.Replay;
import com.orderwaymark.stream.ReplayResult;

import java.io.PrintStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Replays a quarter of real Olist orders through each sourcing policy against shared, depleting stock.
 * Usage: {@code java -jar target/orderwaymark.jar benchmark stream [dataDir]}.
 */
public final class OlistStreamBenchmark {

    static final double[] COVERAGE = {0.8, 1.0, 1.2};

    private OlistStreamBenchmark() {
    }

    public static int run(String[] args, PrintStream out) throws Exception {
        Path dir = Path.of(args.length > 0 ? args[0] : "data/olist");
        if (!OlistStreamWorld.available(dir)) {
            out.printf("Olist CSVs not found in %s. The stream benchmark also needs %s, %s and %s.%n",
                    dir.toAbsolutePath(), OlistStreamWorld.CUSTOMERS, OlistStreamWorld.SELLERS, OlistStreamWorld.GEOLOCATION);
            return 1;
        }
        return run(dir, OlistStreamWorld.Config.defaults(), out);
    }

    public static int run(Path dir, OlistStreamWorld.Config base, PrintStream out) throws Exception {
        OlistStreamWorld first = OlistStreamWorld.load(dir, base.withCoverage(COVERAGE[0]));
        var f = first.fits();
        var c = base;
        out.printf("%nOrderWaymark stream benchmark — %d real Olist orders, %s to %s (%d skipped)%n",
                first.orders().size(), c.testStart(), c.testEnd().minusDays(1), first.skipped());
        out.println("REAL:      order stream, baskets, customer locations, seller locations, deadlines (estimated delivery).");
        out.printf("FITTED:    box price = R$%.2f + R$%.4f/km + R$%.2f/kg   (OLS on %,d real single-item orders, %s–%s)%n",
                f.priceBase() / 100, f.pricePerKm() / 100, f.pricePerKg() / 100, f.priceRows(), c.trainStart(), c.trainEnd().minusDays(1));
        out.printf("           transit days = %.1f + %.4f/km                 (OLS on %,d real delivered orders)%n",
                f.transitBase(), f.transitPerKm(), f.transitRows());
        out.printf("SIMULATED: hubs %s (sellers grouped by state) stocking %d product categories; one box per hub.%n",
                String.join("/", c.hubStates()), c.topCategories() + 1);
        out.printf("           Stock is planned to the replay quarter's average weekly demand × coverage, restocked every %d days.%n",
                c.restockDays());
        out.printf("           The inventory-aware forecast uses only the last %d days of training (no look-ahead).%n",
                c.rateWindowDays());

        List<String> verdicts = new ArrayList<>();
        for (double cov : COVERAGE) {
            OlistStreamWorld w = cov == COVERAGE[0] ? first : OlistStreamWorld.load(dir, base.withCoverage(cov));
            out.printf("%nStock coverage %.1f× planned weekly demand (%,d units across hubs at each restock)%n", cov, w.totalStockUnits());
            out.printf("  %-26s %9s %8s %6s %9s %14s %10s %8s %9s%n",
                    "Policy", "On time", "Late", "Unfill", "On-time%", "Shipping", "Per order", "Boxes/o", "Far units");
            ReplayResult aware = null, today = null;
            for (Policy p : Policy.values()) {
                ReplayResult r = Replay.run(w, w.orders(), p);
                if (p == Policy.INVENTORY_AWARE) aware = r;
                if (p == Policy.CHEAPEST_TODAY) today = r;
                out.printf("  %-26s %,9d %,8d %,6d %8.1f%% %14s %10s %8.2f %8.1f%%%n", p.label(), r.onTime(), r.late(), r.unfilled(),
                        100 * r.onTimeRate(), brl(r.shippingCents()),
                        r.shipped() == 0 ? "—" : brl(Math.round((double) r.shippingCents() / r.shipped())),
                        r.shipped() == 0 ? 0 : (double) r.boxes() / r.shipped(), r.units() == 0 ? 0 : 100.0 * r.farUnits() / r.units());
            }
            long saved = today.shippingCents() - aware.shippingCents();
            String v = "  Inventory-aware vs cheapest today: %s %s shipping (%.2f%%), far-shipped units %.1f%% → %.1f%%, late %,d → %,d."
                    .formatted(saved >= 0 ? "saves" : "costs", brl(Math.abs(saved)),
                            100.0 * Math.abs(saved) / Math.max(1, today.shippingCents()),
                            100.0 * today.farUnits() / Math.max(1, today.units()), 100.0 * aware.farUnits() / Math.max(1, aware.units()),
                            today.late(), aware.late());
            out.println(v);
            verdicts.add(v);
        }
        out.println();
        out.println("Shipping totals only count shipped orders; unfilled counts are shown separately.");
        out.println("Far units = units shipped from a hub that was not the cheapest one for that customer.");
        return 0;
    }

    static String brl(long centavos) {
        return String.format("R$%,.2f", centavos / 100.0);
    }
}
