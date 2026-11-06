package com.orderwaymark.web;

import com.orderwaymark.demo.DemoScenarios;
import com.orderwaymark.demo.Scenario;
import com.orderwaymark.planner.LineItem;
import com.orderwaymark.planner.Order;
import com.orderwaymark.planner.PlanReport;
import com.orderwaymark.planner.Planner;
import com.orderwaymark.planner.QuoteTable;
import com.orderwaymark.planner.Warehouse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

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
            out.put("warehouses", s.warehouses());
            report = s.run();
        } else {
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
