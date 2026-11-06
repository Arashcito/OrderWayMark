package com.orderwaymark.web;

import com.orderwaymark.planner.Box;
import com.orderwaymark.planner.Candidate;
import com.orderwaymark.planner.LineItem;
import com.orderwaymark.planner.Money;
import com.orderwaymark.planner.Planner;
import com.orderwaymark.planner.StrategyResult;

import java.util.LinkedHashMap;
import java.util.Map;

/** Maps planner objects to JSON-friendly maps so the planner stays free of web annotations. */
final class Json {
    private Json() {
    }

    static Map<String, Object> strategy(StrategyResult r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("strategy", r.strategy());
        m.put("label", r.label());
        m.put("status", r.status());
        m.put("plan", candidate(r.plan()));
        m.put("explanation", r.explanation());
        return m;
    }

    static Map<String, Object> candidate(Candidate c) {
        if (c == null) return null;
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("summary", Planner.describePlan(c));
        m.put("assignment", c.assignment());
        m.put("valid", c.valid());
        m.put("totalCents", c.totalCents());
        m.put("total", Money.format(c.totalCents()));
        m.put("boxCount", c.boxCount());
        m.put("latestArrival", c.latestArrival());
        m.put("boxes", c.boxes().stream().map(Json::box).toList());
        m.put("rejections", c.rejections());
        return m;
    }

    static Map<String, Object> box(Box b) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("warehouse", b.warehouseId());
        m.put("warehouseName", b.warehouseName());
        m.put("items", b.contents().stream().map(LineItem::name).toList());
        m.put("priceCents", b.priceCents());
        m.put("arrives", b.arrives());
        return m;
    }
}
