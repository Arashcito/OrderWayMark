package com.orderwaymark.planner;

import com.orderwaymark.demo.DemoScenarios;
import com.orderwaymark.demo.Scenario;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.orderwaymark.demo.DemoScenarios.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class InventoryForecastTest {

    @Test
    void poissonTail() {
        assertThat(InventoryForecast.probAtLeast(1.2, 0)).isEqualTo(1.0);
        assertThat(InventoryForecast.probAtLeast(1.2, 1)).isCloseTo(1 - Math.exp(-1.2), within(1e-12));
        assertThat(InventoryForecast.probAtLeast(1.2, 2)).isCloseTo(1 - Math.exp(-1.2) * 2.2, within(1e-12));
        assertThat(InventoryForecast.probAtLeast(0, 1)).isZero();
    }

    @Test
    void expectedCostOfTakingUnits() {
        // last unit: P(D>=1) * $6 = 0.6988 * 600
        assertThat(InventoryForecast.expectedCostCents(1.2, 600, 1, 1)).isEqualTo(419);
        // both of the last two units: (P(D>=2) + P(D>=1)) * 600
        assertThat(InventoryForecast.expectedCostCents(1.2, 600, 2, 2)).isEqualTo(622);
        // plenty of stock: practically free
        assertThat(InventoryForecast.expectedCostCents(1.2, 600, 12, 1)).isZero();
    }

    @Test
    void withoutForecastNothingChanges() {
        for (Scenario s : List.of(oneBoxWins(), twoBoxesWin(), stockChanges(), lateBox(), priceTie())) {
            PlanReport r = s.run();
            assertThat(r.candidates()).allMatch(c -> c.inventoryCostCents() == 0 && c.inventoryNotes().isEmpty());
            if (r.winner() != null) {
                assertThat(r.result(Strategy.CHEAPEST_TODAY).plan()).isEqualTo(r.winner());
            }
        }
    }

    @Test
    void lastKeyboardMakesThePlannerSplitInsteadOfTakingTheCheapBox() {
        PlanReport r = DemoScenarios.lastKeyboard().run();

        Candidate ottawaBox = r.candidates().stream()
                .filter(c -> c.assignment().equals(List.of(OTT, OTT))).findFirst().orElseThrow();
        assertThat(ottawaBox.totalCents()).isEqualTo(800);
        assertThat(ottawaBox.inventoryCostCents()).isEqualTo(419);
        assertThat(ottawaBox.effectiveCents()).isEqualTo(1219);
        assertThat(ottawaBox.inventoryNotes()).singleElement().asString()
                .contains("Uses Ottawa's last Keyboard").contains("70%").contains("$4.19");

        assertThat(r.winner().assignment()).containsExactly(MTL, OTT);
        assertThat(r.winner().totalCents()).isEqualTo(1100);
        assertThat(r.winner().inventoryCostCents()).isZero();
        assertThat(r.explanation()).contains("expected future stock cost").contains("passed over").contains("$4.19");

        StrategyResult today = r.result(Strategy.CHEAPEST_TODAY);
        assertThat(today.plan().assignment()).containsExactly(OTT, OTT);
        assertThat(today.explanation()).contains("saves $3.00 in shipping today").contains("net $1.19 worse");
    }

    @Test
    void breakEvenOttawaPrice() {
        // Ottawa box wins again once 8 + 4.19 drops to the split's $11: at $6.81 it ties and wins on fewer boxes.
        assertThat(withOttawaCombined(681).winner().assignment()).containsExactly(OTT, OTT);
        assertThat(withOttawaCombined(682).winner().assignment()).containsExactly(MTL, OTT);
    }

    private static PlanReport withOttawaCombined(long cents) {
        QuoteTable q = seedQuotes().put(OTT, List.of(KEYBOARD, MOUSE), cents, FRIDAY);
        return new Planner(seedWarehouses(), q, lastKeyboardForecast()).plan(seedOrder());
    }
}
