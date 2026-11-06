package com.orderwaymark.demo;

import com.orderwaymark.planner.Order;
import com.orderwaymark.planner.PlanReport;
import com.orderwaymark.planner.Planner;
import com.orderwaymark.planner.QuoteProvider;
import com.orderwaymark.planner.Warehouse;

import java.util.List;

public record Scenario(String id, String title, String description,
                       List<Warehouse> warehouses, QuoteProvider quotes, Order order) {
    public PlanReport run() {
        return new Planner(warehouses, quotes).plan(order);
    }
}
