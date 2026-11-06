package com.orderwaymark.olist;

import java.time.LocalDate;
import java.util.List;

/**
 * A real two-item Olist order. Contents and dates are historical; nothing here is simulated.
 *
 * @param estimatedDelivery the historical order_estimated_delivery_date, used as the deadline proxy
 */
public record OlistBasket(String orderId, LocalDate purchaseDate, LocalDate estimatedDelivery, List<Product> items) {

    public record Product(String productId, String category, int weightGrams) {
    }
}
