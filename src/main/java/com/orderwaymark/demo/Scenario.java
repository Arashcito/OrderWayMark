package com.orderwaymark.demo;

import com.orderwaymark.planner.InventoryValuation;
import com.orderwaymark.planner.Order;
import com.orderwaymark.planner.PlanReport;
import com.orderwaymark.planner.Planner;
import com.orderwaymark.planner.QuoteTable;
import com.orderwaymark.planner.Warehouse;

import java.util.List;

/** A preset demo. {@code valuation} is {@link InventoryValuation#NONE} unless the scenario has a stock forecast. */
public record Scenario(String id, String title, String description,
                       List<Warehouse> warehouses, QuoteTable quotes, Order order, InventoryValuation valuation) {

    public Scenario(String id, String title, String description,
                    List<Warehouse> warehouses, QuoteTable quotes, Order order) {
        this(id, title, description, warehouses, quotes, order, InventoryValuation.NONE);
    }

    public PlanReport run() {
        return new Planner(warehouses, quotes, valuation).plan(order);
    }
}
