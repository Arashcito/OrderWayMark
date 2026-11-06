package com.orderwaymark.olist;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Joins Olist orders, order_items and products into two-item baskets.
 *
 * <ul>
 *   <li>order_items grouped by order_id; keep orders with exactly two item rows</li>
 *   <li>inner join orders (needs purchase timestamp and estimated delivery date)</li>
 *   <li>inner join products for every item (category, weight)</li>
 *   <li>sorted by order_id for a deterministic sample, then truncated to {@code limit}</li>
 * </ul>
 */
public final class OlistImporter {

    public static final String ORDERS = "olist_orders_dataset.csv";
    public static final String ORDER_ITEMS = "olist_order_items_dataset.csv";
    public static final String PRODUCTS = "olist_products_dataset.csv";

    private OlistImporter() {
    }

    public static boolean available(Path dir) {
        return Files.isRegularFile(dir.resolve(ORDERS)) && Files.isRegularFile(dir.resolve(ORDER_ITEMS))
                && Files.isRegularFile(dir.resolve(PRODUCTS));
    }

    public static List<OlistBasket> load(Path dir, int limit) throws IOException {
        // 1. order_items -> product ids per order, ordered by order_item_id
        Map<String, TreeMap<Integer, String>> itemsByOrder = new HashMap<>();
        Csv.read(dir.resolve(ORDER_ITEMS), row -> {
            String orderId = row.get("order_id");
            int seq = parseIntOr(row.get("order_item_id"), Integer.MAX_VALUE);
            itemsByOrder.computeIfAbsent(orderId, k -> new TreeMap<>()).put(seq, row.get("product_id"));
        });

        // 2. orders with exactly two items
        record OrderRow(LocalDate purchase, LocalDate estimated) {
        }
        Map<String, OrderRow> orders = new TreeMap<>();
        Csv.read(dir.resolve(ORDERS), row -> {
            String id = row.get("order_id");
            var items = itemsByOrder.get(id);
            if (items == null || items.size() != 2) return;
            LocalDate purchase = date(row.get("order_purchase_timestamp"));
            LocalDate estimated = date(row.get("order_estimated_delivery_date"));
            if (purchase == null || estimated == null) return;
            orders.put(id, new OrderRow(purchase, estimated));
        });

        Set<String> neededProducts = new HashSet<>();
        orders.keySet().forEach(id -> neededProducts.addAll(itemsByOrder.get(id).values()));

        // 3. products
        Map<String, OlistBasket.Product> products = new HashMap<>();
        Csv.read(dir.resolve(PRODUCTS), row -> {
            String id = row.get("product_id");
            if (!neededProducts.contains(id)) return;
            String cat = row.getOrDefault("product_category_name", "");
            products.put(id, new OlistBasket.Product(id, cat.isBlank() ? "unknown" : cat,
                    parseIntOr(row.get("product_weight_g"), 0)));
        });

        List<OlistBasket> out = new ArrayList<>();
        for (var e : orders.entrySet()) { // TreeMap -> sorted by order_id
            if (out.size() >= limit) break;
            List<OlistBasket.Product> items = new ArrayList<>();
            for (String pid : itemsByOrder.get(e.getKey()).values()) {
                OlistBasket.Product p = products.get(pid);
                if (p == null) break;
                items.add(p);
            }
            if (items.size() != 2) continue;
            out.add(new OlistBasket(e.getKey(), e.getValue().purchase(), e.getValue().estimated(), items));
        }
        return out;
    }

    private static LocalDate date(String ts) {
        if (ts == null || ts.length() < 10) return null;
        try {
            return LocalDate.parse(ts.substring(0, 10));
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static int parseIntOr(String s, int fallback) {
        try {
            return (int) Math.round(Double.parseDouble(s.trim()));
        } catch (RuntimeException e) {
            return fallback;
        }
    }
}
