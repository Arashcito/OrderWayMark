package com.orderwaymark.olist;

import com.orderwaymark.planner.InventoryForecast;
import com.orderwaymark.planner.InventoryValuation;
import com.orderwaymark.planner.LineItem;
import com.orderwaymark.planner.Quote;
import com.orderwaymark.planner.QuoteProvider;
import com.orderwaymark.planner.Warehouse;
import com.orderwaymark.stream.ReplayWorld;
import com.orderwaymark.stream.StreamOrder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * A replay world built from Olist. What is real and what is simulated:
 *
 * <ul>
 *   <li>REAL: order stream (timestamps, baskets, customer postal codes, estimated delivery date used
 *       as the deadline), seller locations (hub positions), product categories and weights.</li>
 *   <li>FITTED ON REAL DATA (training window only): box price = b0 + b1*km + b2*kg from real
 *       single-item freight; transit days = t0 + t1*km from real delivered orders.</li>
 *   <li>SIMULATED: three hubs (sellers grouped by state SP/MG/PR) holding stock of each product
 *       category, order-up-to stock levels, weekly restocks, and one box per hub per order.</li>
 * </ul>
 */
public final class OlistStreamWorld implements ReplayWorld {

    public static final String CUSTOMERS = "olist_customers_dataset.csv";
    public static final String SELLERS = "olist_sellers_dataset.csv";
    public static final String GEOLOCATION = "olist_geolocation_dataset.csv";
    static final String OTHER = "other";

    public record Config(LocalDate trainStart, LocalDate trainEnd, LocalDate testStart, LocalDate testEnd,
                         int rateWindowDays, double coverage, int restockDays, int topCategories, int maxItems,
                         List<String> hubStates) {
        public static Config defaults() {
            return new Config(LocalDate.of(2017, 1, 1), LocalDate.of(2018, 1, 1),
                    LocalDate.of(2018, 1, 1), LocalDate.of(2018, 4, 1),
                    90, 1.0, 7, 12, 4, List.of("SP", "MG", "PR"));
        }

        public Config withCoverage(double c) {
            return new Config(trainStart, trainEnd, testStart, testEnd, rateWindowDays, c, restockDays,
                    topCategories, maxItems, hubStates);
        }

        public Config withWindows(LocalDate trS, LocalDate trE, LocalDate teS, LocalDate teE, int rateDays) {
            return new Config(trS, trE, teS, teE, rateDays, coverage, restockDays, topCategories, maxItems, hubStates);
        }
    }

    /** Fitted models. Prices in centavos, transit in days. */
    public record Fits(double priceBase, double pricePerKm, double pricePerKg, int priceRows,
                       double transitBase, double transitPerKm, int transitRows) {
    }

    record Hub(String id, String name, double lat, double lng) {
    }

    private final Config cfg;
    private final Fits fits;
    private final List<Hub> hubs;
    private final Map<String, double[]> zipCoords;
    private final Map<String, Double> skuKg;
    private final Map<String, Map<String, Double>> ratePerDay = new HashMap<>();   // hub -> sku -> units/day
    private final Map<String, Map<String, Long>> extraCents = new HashMap<>();     // hub -> sku -> cents
    private final List<Warehouse> warehouses;
    private final List<StreamOrder> orders;
    private final int skipped;

    private OlistStreamWorld(Config cfg, Fits fits, List<Hub> hubs, Map<String, double[]> zipCoords,
                             Map<String, Double> skuKg, List<StreamOrder> orders, int skipped,
                             List<RateSample> rateSamples, List<RateSample> testSamples) {
        this.cfg = cfg;
        this.fits = fits;
        this.hubs = hubs;
        this.zipCoords = zipCoords;
        this.skuKg = skuKg;
        this.orders = orders;
        this.skipped = skipped;

        // Demand forecast and substitution penalty from the last `rateWindowDays` of training.
        Map<String, Map<String, long[]>> agg = homeDemand(rateSamples);
        agg.forEach((hub, bySku) -> bySku.forEach((sku, a) -> {
            ratePerDay.computeIfAbsent(hub, k -> new HashMap<>()).put(sku, (double) a[0] / cfg.rateWindowDays());
            extraCents.computeIfAbsent(hub, k -> new HashMap<>()).put(sku, Math.round((double) a[1] / a[0]));
        }));

        // Stock plan: order-up-to = coverage x average weekly home demand over the replay window.
        Map<String, Map<String, long[]>> planned = homeDemand(testSamples);
        double testDays = Math.max(1, ChronoUnit.DAYS.between(cfg.testStart(), cfg.testEnd()));
        Set<String> skus = new HashSet<>(skuKg.keySet());
        List<Warehouse> whs = new ArrayList<>();
        for (Hub h : hubs) {
            Map<String, Integer> stock = new HashMap<>();
            for (String sku : skus) {
                long[] a = planned.getOrDefault(h.id(), Map.of()).get(sku);
                double weekly = a == null ? 0 : a[0] / testDays * cfg.restockDays();
                stock.put(sku, (int) Math.ceil(cfg.coverage() * weekly - 1e-9));
            }
            whs.add(new Warehouse(h.id(), h.name(), stock));
        }
        this.warehouses = List.copyOf(whs);
    }

    /** Per hub and sku: [units whose cheapest hub is this one, sum of extra cost from the second-cheapest hub]. */
    private Map<String, Map<String, long[]>> homeDemand(List<RateSample> samples) {
        Map<String, Map<String, long[]>> agg = new HashMap<>();
        for (RateSample s : samples) {
            long[] costs = hubs.stream().mapToLong(h -> priceCents(h, s.custLat, s.custLng, skuKg.getOrDefault(s.sku, 0.0))).toArray();
            int best = 0, second = -1;
            for (int i = 1; i < costs.length; i++) if (costs[i] < costs[best]) best = i;
            for (int i = 0; i < costs.length; i++) if (i != best && (second < 0 || costs[i] < costs[second])) second = i;
            long extra = second < 0 ? 0 : costs[second] - costs[best];
            long[] a = agg.computeIfAbsent(hubs.get(best).id(), k -> new HashMap<>()).computeIfAbsent(s.sku, k -> new long[2]);
            a[0]++;
            a[1] += extra;
        }
        return agg;
    }

    public Fits fits() {
        return fits;
    }

    public List<StreamOrder> orders() {
        return orders;
    }

    /** Test-window orders left out (too many items, unknown location, missing deadline). */
    public int skipped() {
        return skipped;
    }

    public Config config() {
        return cfg;
    }

    public long totalStockUnits() {
        return warehouses.stream().mapToLong(w -> w.stock().values().stream().mapToLong(Integer::longValue).sum()).sum();
    }

    @Override
    public List<Warehouse> warehouses() {
        return warehouses;
    }

    @Override
    public int restockEveryDays() {
        return cfg.restockDays();
    }

    @Override
    public LocalDate firstDay() {
        return cfg.testStart();
    }

    @Override
    public QuoteProvider quotes(StreamOrder order) {
        double[] c = zipCoords.get(order.customerKey());
        LocalDate placed = order.placedAt().toLocalDate();
        Map<String, Hub> byId = new HashMap<>();
        hubs.forEach(h -> byId.put(h.id(), h));
        return (wh, contents) -> {
            Hub h = byId.get(wh.id());
            if (h == null) return Optional.empty();
            double kg = contents.stream().mapToDouble(li -> skuKg.getOrDefault(li.sku(), 0.0)).sum();
            long price = priceCents(h, c[0], c[1], kg);
            double km = km(h.lat(), h.lng(), c[0], c[1]);
            int days = (int) Math.ceil(Math.max(1.0, fits.transitBase() + fits.transitPerKm() * km));
            return Optional.of(new Quote(price, placed.plusDays(days)));
        };
    }

    @Override
    public InventoryValuation forecast(StreamOrder order, int daysUntilRestock) {
        int days = Math.min(daysUntilRestock, cfg.restockDays());
        return (wh, item, onHand, units) -> {
            double rate = ratePerDay.getOrDefault(wh.id(), Map.of()).getOrDefault(item.sku(), 0.0);
            long extra = extraCents.getOrDefault(wh.id(), Map.of()).getOrDefault(item.sku(), 0L);
            long cents = InventoryForecast.expectedCostCents(rate * days, extra, onHand, units);
            return cents == 0 ? InventoryValuation.Cost.ZERO : new InventoryValuation.Cost(cents, null);
        };
    }

    long priceCents(Hub h, double lat, double lng, double kg) {
        double km = km(h.lat(), h.lng(), lat, lng);
        return Math.max(0, Math.round(fits.priceBase() + fits.pricePerKm() * km + fits.pricePerKg() * kg));
    }

    static double km(double lat1, double lng1, double lat2, double lng2) {
        double r = 6371.0, dLat = Math.toRadians(lat2 - lat1), dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return 2 * r * Math.asin(Math.sqrt(a));
    }

    // ------------------------------------------------------------------ loading

    public static boolean available(Path dir) {
        return OlistImporter.available(dir) && Files.isRegularFile(dir.resolve(CUSTOMERS))
                && Files.isRegularFile(dir.resolve(SELLERS)) && Files.isRegularFile(dir.resolve(GEOLOCATION));
    }

    private record OrderRow(String customerId, LocalDateTime purchase, LocalDate estimated, LocalDate delivered, String status) {
    }

    private record ItemRow(String productId, String sellerId, double freight) {
    }

    private record RateSample(String sku, double custLat, double custLng) {
    }

    public static OlistStreamWorld load(Path dir, Config cfg) throws IOException {
        // zip prefix -> mean coordinates (dropping points outside Brazil's bounding box)
        Map<String, double[]> sums = new HashMap<>();
        Csv.read(dir.resolve(GEOLOCATION), r -> {
            double lat = num(r.get("geolocation_lat")), lng = num(r.get("geolocation_lng"));
            if (Double.isNaN(lat) || Double.isNaN(lng) || lat < -34 || lat > 6 || lng < -74 || lng > -34) return;
            double[] s = sums.computeIfAbsent(r.get("geolocation_zip_code_prefix"), k -> new double[3]);
            s[0] += lat; s[1] += lng; s[2]++;
        });
        Map<String, double[]> zip = new HashMap<>();
        sums.forEach((k, s) -> zip.put(k, new double[]{s[0] / s[2], s[1] / s[2]}));

        Map<String, String> customerZip = new HashMap<>();
        Csv.read(dir.resolve(CUSTOMERS), r -> customerZip.put(r.get("customer_id"), r.get("customer_zip_code_prefix")));
        Map<String, String[]> sellers = new HashMap<>(); // id -> [zip, state]
        Csv.read(dir.resolve(SELLERS), r -> sellers.put(r.get("seller_id"),
                new String[]{r.get("seller_zip_code_prefix"), r.getOrDefault("seller_state", "")}));
        Map<String, String[]> products = new HashMap<>(); // id -> [category, weight_g]
        Csv.read(dir.resolve(OlistImporter.PRODUCTS), r -> products.put(r.get("product_id"),
                new String[]{r.getOrDefault("product_category_name", ""), r.getOrDefault("product_weight_g", "")}));
        Map<String, OrderRow> orders = new HashMap<>();
        Csv.read(dir.resolve(OlistImporter.ORDERS), r -> {
            LocalDateTime p = dateTime(r.get("order_purchase_timestamp"));
            if (p == null) return;
            orders.put(r.get("order_id"), new OrderRow(r.get("customer_id"), p, date(r.get("order_estimated_delivery_date")),
                    date(r.get("order_delivered_customer_date")), r.getOrDefault("order_status", "")));
        });
        Map<String, List<ItemRow>> items = new HashMap<>();
        Csv.read(dir.resolve(OlistImporter.ORDER_ITEMS), r -> items.computeIfAbsent(r.get("order_id"), k -> new ArrayList<>())
                .add(new ItemRow(r.get("product_id"), r.get("seller_id"), num(r.get("freight_value")))));

        // ---- training window: categories, weights, fits
        Map<String, Integer> catCount = new HashMap<>();
        Map<String, List<Double>> catKg = new HashMap<>();
        LeastSquares price = new LeastSquares(2);
        LeastSquares transit = new LeastSquares(1);
        for (var e : items.entrySet()) {
            OrderRow o = orders.get(e.getKey());
            if (o == null || !inRange(o.purchase(), cfg.trainStart(), cfg.trainEnd())) continue;
            double[] cust = zip.get(customerZip.get(o.customerId()));
            for (ItemRow it : e.getValue()) {
                String[] p = products.get(it.productId());
                String cat = category(p);
                catCount.merge(cat, 1, Integer::sum);
                double kg = p == null ? Double.NaN : num(p[1]) / 1000.0;
                if (!Double.isNaN(kg)) catKg.computeIfAbsent(cat, k -> new ArrayList<>()).add(kg);
            }
            ItemRow first = e.getValue().get(0);
            String[] s = sellers.get(first.sellerId());
            double[] sc = s == null ? null : zip.get(s[0]);
            if (cust == null || sc == null) continue;
            double km = km(sc[0], sc[1], cust[0], cust[1]);
            if (e.getValue().size() == 1) {
                String[] p = products.get(first.productId());
                double kg = p == null ? Double.NaN : num(p[1]) / 1000.0;
                if (!Double.isNaN(kg) && !Double.isNaN(first.freight())) price.add(first.freight() * 100, km, kg);
            }
            boolean oneSeller = e.getValue().stream().map(ItemRow::sellerId).distinct().count() == 1;
            if (oneSeller && o.delivered() != null && "delivered".equals(o.status())) {
                transit.add(ChronoUnit.DAYS.between(o.purchase().toLocalDate(), o.delivered()), km);
            }
        }
        double[] pb = price.solve();
        double[] tb = transit.solve();
        Fits fits = new Fits(pb[0], pb[1], pb[2], price.rows(), tb[0], tb[1], transit.rows());

        Set<String> top = new HashSet<>(catCount.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                .limit(cfg.topCategories()).map(Map.Entry::getKey).toList());
        Map<String, Double> skuKg = new LinkedHashMap<>();
        Map<String, List<Double>> skuKgs = new HashMap<>();
        catKg.forEach((cat, list) -> skuKgs.computeIfAbsent(top.contains(cat) ? cat : OTHER, k -> new ArrayList<>()).addAll(list));
        skuKgs.keySet().stream().sorted().forEach(k -> skuKg.put(k, median(skuKgs.get(k))));
        for (String t : top) skuKg.putIfAbsent(t, 0.0);
        skuKg.putIfAbsent(OTHER, 0.0);

        // ---- hubs: mean location of each state's sellers
        List<Hub> hubs = new ArrayList<>();
        for (String st : cfg.hubStates()) {
            double lat = 0, lng = 0;
            int n = 0;
            for (String[] s : sellers.values()) {
                double[] c = st.equals(s[1]) ? zip.get(s[0]) : null;
                if (c == null) continue;
                lat += c[0]; lng += c[1]; n++;
            }
            if (n == 0) throw new IllegalStateException("no sellers with coordinates in state " + st);
            hubs.add(new Hub(st, st + " hub", lat / n, lng / n));
        }

        // ---- rate samples (end of training) and test stream
        LocalDate rateStart = cfg.trainEnd().minusDays(cfg.rateWindowDays());
        List<RateSample> samples = new ArrayList<>();
        List<RateSample> testSamples = new ArrayList<>();
        List<StreamOrder> stream = new ArrayList<>();
        int skipped = 0;
        for (var e : items.entrySet()) {
            OrderRow o = orders.get(e.getKey());
            if (o == null) continue;
            boolean inRate = inRange(o.purchase(), rateStart, cfg.trainEnd());
            boolean inTest = inRange(o.purchase(), cfg.testStart(), cfg.testEnd());
            if (!inRate && !inTest) continue;
            String zipKey = customerZip.get(o.customerId());
            double[] cust = zipKey == null ? null : zip.get(zipKey);
            if (inRate && cust != null) {
                for (ItemRow it : e.getValue()) samples.add(new RateSample(sku(products.get(it.productId()), top), cust[0], cust[1]));
            }
            if (inTest) {
                boolean bad = cust == null || o.estimated() == null || e.getValue().size() > cfg.maxItems()
                        || "canceled".equals(o.status()) || "unavailable".equals(o.status());
                if (bad) {
                    skipped++;
                    continue;
                }
                List<LineItem> li = e.getValue().stream().map(it -> {
                    String sku = sku(products.get(it.productId()), top);
                    return new LineItem(sku, sku);
                }).toList();
                for (LineItem l : li) testSamples.add(new RateSample(l.sku(), cust[0], cust[1]));
                stream.add(new StreamOrder(e.getKey(), o.purchase(), zipKey, li, o.estimated()));
            }
        }
        stream.sort(Comparator.comparing(StreamOrder::placedAt).thenComparing(StreamOrder::orderId));
        samples.sort(Comparator.comparing(RateSample::sku).thenComparingDouble(RateSample::custLat).thenComparingDouble(RateSample::custLng));
        testSamples.sort(Comparator.comparing(RateSample::sku).thenComparingDouble(RateSample::custLat).thenComparingDouble(RateSample::custLng));
        return new OlistStreamWorld(cfg, fits, hubs, zip, skuKg, stream, skipped, samples, testSamples);
    }

    private static String category(String[] p) {
        return p == null || p[0] == null || p[0].isBlank() ? OTHER : p[0];
    }

    private static String sku(String[] p, Set<String> top) {
        String c = category(p);
        return top.contains(c) ? c : OTHER;
    }

    private static boolean inRange(LocalDateTime t, LocalDate from, LocalDate to) {
        LocalDate d = t.toLocalDate();
        return !d.isBefore(from) && d.isBefore(to);
    }

    private static double median(List<Double> v) {
        if (v.isEmpty()) return 0;
        List<Double> s = v.stream().sorted().toList();
        int m = s.size() / 2;
        return s.size() % 2 == 1 ? s.get(m) : (s.get(m - 1) + s.get(m)) / 2;
    }

    private static double num(String s) {
        try {
            return Double.parseDouble(s.trim());
        } catch (RuntimeException e) {
            return Double.NaN;
        }
    }

    private static LocalDate date(String ts) {
        LocalDateTime t = dateTime(ts);
        return t == null ? null : t.toLocalDate();
    }

    private static LocalDateTime dateTime(String ts) {
        if (ts == null || ts.length() < 10) return null;
        try {
            return ts.length() >= 19 ? LocalDateTime.parse(ts.substring(0, 19).replace(' ', 'T'))
                    : LocalDate.parse(ts.substring(0, 10)).atStartOfDay();
        } catch (RuntimeException e) {
            return null;
        }
    }
}
