package com.orderwaymark.stream;

/**
 * Totals for one policy over a stream.
 *
 * @param late       shipped, but some box arrives after the deadline (no on-time plan existed)
 * @param unfilled   no warehouse combination had the stock, so the order could not ship
 * @param boxes      boxes shipped
 * @param units      units shipped
 * @param farUnits   units shipped from a warehouse that was not the cheapest one for that customer
 */
public record ReplayResult(Policy policy, int orders, int onTime, int late, int unfilled,
                           long shippingCents, long boxes, long units, long farUnits) {

    public int shipped() {
        return onTime + late;
    }

    public double onTimeRate() {
        return orders == 0 ? 0 : (double) onTime / orders;
    }
}
