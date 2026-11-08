package com.orderwaymark.stream;

import com.orderwaymark.planner.InventoryValuation;
import com.orderwaymark.planner.QuoteProvider;
import com.orderwaymark.planner.Warehouse;

import java.time.LocalDate;
import java.util.List;

/** Everything the replay needs besides the orders: network, restocking, quotes and the forecast. */
public interface ReplayWorld {

    /** Warehouses with their order-up-to stock; every restock resets stock to these levels. */
    List<Warehouse> warehouses();

    /** Restock happens on {@link #firstDay()} + k * this many days. 0 or less means never. */
    int restockEveryDays();

    LocalDate firstDay();

    /** Box quotes for this order's destination. */
    QuoteProvider quotes(StreamOrder order);

    /** Expected future stock cost, given how many days remain until the next restock. */
    InventoryValuation forecast(StreamOrder order, int daysUntilRestock);
}
