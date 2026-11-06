package com.orderwaymark.planner;

import java.time.LocalDate;

/** Price (integer cents) and estimated arrival for one box. */
public record Quote(long priceCents, LocalDate arrives) {
    public Quote {
        if (priceCents < 0) throw new IllegalArgumentException("priceCents must be >= 0");
    }
}
