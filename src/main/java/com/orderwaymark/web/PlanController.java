package com.orderwaymark.web;

import com.orderwaymark.demo.DemoScenarios;
import com.orderwaymark.demo.Scenario;
import com.orderwaymark.planner.LineItem;
import com.orderwaymark.planner.Order;
import com.orderwaymark.planner.PlanReport;
import com.orderwaymark.planner.Planner;
import com.orderwaymark.planner.Quote;
import com.orderwaymark.planner.QuoteTable;
import com.orderwaymark.planner.Warehouse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
public class PlanController {

    static final int MAX_ITEMS = 6;
    static final int MAX_WAREHOUSES = 4;

    /** The one JSON endpoint: plan a preset scenario or a custom order. */
    @PostMapping("/api/plan")
    public Map<String, Object> plan(@RequestBody PlanRequest req) {
        Map<String, Object> out = new LinkedHashMap<>();
        PlanReport report;
        if (req.scenario() != null) {
            Scenario s = DemoScenarios.byId(req.scenario())
                    .orElseThrow(() -> new BadRequestException("Unknown scenario '" + req.scenario()
                            + "'. Known: " + DemoScenarios.all().keySet()));
            out.put("scenario", Map.of("id", s.id(), "title", s.title(), "description", s.description()));
            QuoteTable quotes = s.quotes().copy();
            Quote combined = quotes.get(DemoScenarios.OTT, OTT_COMBINED).orElseThrow();
            if (req.ottawaCombinedQuoteCents() != null) {
                long cents = parseOverride(req.ottawaCombinedQuoteCents());
                combined = new Quote(cents, combined.arrives());
                quotes.put(DemoScenarios.OTT, OTT_COMBINED, combined.priceCents(), combined.arrives());
            }
            out.put("ottawaCombinedQuoteCents", combined.priceCents());
            out.put("ottawaCombinedQuoteOverridden", req.ottawaCombinedQuoteCents() != null);
            out.put("warehouses", s.warehouses());
            report = new Planner(s.warehouses(), quotes, s.valuation()).plan(s.order());
        } else {
            if (req.ottawaCombinedQuoteCents() != null)
                throw new BadRequestException("'ottawaCombinedQuoteCents' can only be used with a 'scenario'; "
                        + "for a custom order, put the price in 'quotes'.");
            Custom c = validate(req);
            out.put("warehouses", c.warehouses);
            report = new Planner(c.warehouses, c.quotes).plan(c.order);
        }
        out.put("scenarios", DemoScenarios.all().values().stream()
                .map(s -> Map.of("id", s.id(), "title", s.title())).toList());
        out.put("status", report.status());
        out.put("deadline", report.order().deadline());
        out.put("items", report.order().items());
        out.put("winner", Json.candidate(report.winner()));
        out.put("explanation", report.explanation());
        out.put("strategies", report.strategies().stream().map(Json::strategy).toList());
        out.put("candidates", report.candidates().stream().map(Json::candidate).toList());
        return out;
    }

    static final List<String> OTT_COMBINED = List.of(DemoScenarios.KEYBOARD, DemoScenarios.MOUSE);
    static final long MAX_OVERRIDE_CENTS = 100_000; // $1,000.00

    private static long parseOverride(BigDecimal v) {
        BigDecimal n = v.stripTrailingZeros();
        if (n.scale() > 0) {
            throw new BadRequestException("'ottawaCombinedQuoteCents' must be a whole number of cents (e.g. 1101 for $11.01).");
        }
        if (n.signum() < 0 || n.compareTo(BigDecimal.valueOf(MAX_OVERRIDE_CENTS)) > 0) {
            throw new BadRequestException("'ottawaCombinedQuoteCents' must be between 0 and " + MAX_OVERRIDE_CENTS + ".");
        }
        return n.longValueExact();
    }

    private record Custom(List<Warehouse> warehouses, QuoteTable quotes, Order order) {
    }

    private static Custom validate(PlanRequest r) {
        if (r.deadline() == null) throw new BadRequestException("'deadline' (YYYY-MM-DD) is required unless 'scenario' is given.");
        if (r.items() == null || r.items().isEmpty()) throw new BadRequestException("'items' must list at least one sku.");
        if (r.items().size() > MAX_ITEMS) throw new BadRequestException("At most " + MAX_ITEMS + " items are supported.");
        if (r.items().stream().anyMatch(s -> s == null || s.isBlank())) throw new BadRequestException("Item skus must be non-blank.");
        if (r.warehouses() == null || r.warehouses().isEmpty()) throw new BadRequestException("'warehouses' must not be empty.");
        if (r.warehouses().size() > MAX_WAREHOUSES) throw new BadRequestException("At most " + MAX_WAREHOUSES + " warehouses are supported.");

        Set<String> ids = new HashSet<>();
        List<Warehouse> whs = new ArrayList<>();
        for (var w : r.warehouses()) {
            if (w == null || w.id() == null || w.id().isBlank()) throw new BadRequestException("Every warehouse needs an 'id'.");
            if (!ids.add(w.id())) throw new BadRequestException("Duplicate warehouse id '" + w.id() + "'.");
            Map<String, Integer> stock = w.stock() == null ? Map.of() : w.stock();
            stock.forEach((sku, qty) -> {
                if (qty == null || qty < 0) throw new BadRequestException("Stock for '" + sku + "' at " + w.id() + " must be >= 0.");
            });
            whs.add(new Warehouse(w.id(), w.name(), stock));
        }

        QuoteTable table = new QuoteTable();
        for (var q : r.quotes() == null ? List.<PlanRequest.QuoteIn>of() : r.quotes()) {
            if (q == null || q.warehouse() == null || !ids.contains(q.warehouse()))
                throw new BadRequestException("Quote references unknown warehouse '" + (q == null ? null : q.warehouse()) + "'.");
            if (q.items() == null || q.items().isEmpty()) throw new BadRequestException("Every quote needs 'items'.");
            if (q.priceCents() == null || q.priceCents() < 0) throw new BadRequestException("'priceCents' must be a non-negative integer.");
            if (q.arrives() == null) throw new BadRequestException("Every quote needs 'arrives' (YYYY-MM-DD).");
            table.put(q.warehouse(), q.items(), q.priceCents(), q.arrives());
        }
        Order order = new Order("custom", r.items().stream().map(s -> new LineItem(s, s)).toList(), r.deadline());
        return new Custom(whs, table, order);
    }
}
