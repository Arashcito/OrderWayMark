package com.orderwaymark.planner;

import com.orderwaymark.demo.DemoScenarios;
import com.orderwaymark.demo.Scenario;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.orderwaymark.demo.DemoScenarios.*;
import static org.assertj.core.api.Assertions.assertThat;

class PlannerTest {

    private static PlanReport run(List<Warehouse> wh, QuoteTable q) {
        return new Planner(wh, q).plan(seedOrder());
    }

    @Test
    void enumeratesAllFourAssignmentsAndGroupsByWarehouse() {
        PlanReport r = DemoScenarios.oneBoxWins().run();
        assertThat(r.candidates()).extracting(Candidate::assignment).containsExactly(
                List.of(MTL, MTL), List.of(MTL, OTT), List.of(OTT, MTL), List.of(OTT, OTT));
        // same-warehouse items share one box, priced with the combined quote
        assertThat(r.candidates().get(0).boxCount()).isEqualTo(1);
        assertThat(r.candidates().get(0).totalCents()).isEqualTo(1200);
        assertThat(r.candidates().get(1).boxCount()).isEqualTo(2);
        assertThat(r.candidates().get(1).totalCents()).isEqualTo(1100);
    }

    @Nested
    @DisplayName("1. One box wins")
    class OneBoxWins {
        @Test
        void ottawaShipsBothForEightDollarsVersusElevenForCheapestPerItem() {
            PlanReport r = DemoScenarios.oneBoxWins().run();
            assertThat(r.status()).isEqualTo(PlanStatus.PLANNED);
            assertThat(r.winner().assignment()).containsExactly(OTT, OTT);
            assertThat(r.winner().boxCount()).isEqualTo(1);
            assertThat(r.winner().totalCents()).isEqualTo(800);

            StrategyResult perItem = r.result(Strategy.CHEAPEST_PER_ITEM);
            assertThat(perItem.status()).isEqualTo(PlanStatus.PLANNED);
            assertThat(perItem.plan().assignment()).containsExactly(MTL, OTT);
            assertThat(perItem.plan().totalCents()).isEqualTo(1100);
            assertThat(r.explanation()).contains("$8.00").contains("Ottawa");
        }
    }

    @Nested
    @DisplayName("2. Two boxes win")
    class TwoBoxesWin {
        @Test
        void plannerPicksElevenDollarSplitAndFewestBoxesPicksMontreal() {
            PlanReport r = DemoScenarios.twoBoxesWin().run();
            assertThat(r.winner().assignment()).containsExactly(MTL, OTT);
            assertThat(r.winner().boxCount()).isEqualTo(2);
            assertThat(r.winner().totalCents()).isEqualTo(1100);

            StrategyResult fewest = r.result(Strategy.FEWEST_BOXES_FIRST);
            assertThat(fewest.plan().assignment()).containsExactly(MTL, MTL);
            assertThat(fewest.plan().totalCents()).isEqualTo(1200);
        }
    }

    @Nested
    @DisplayName("3. Stock changes the answer")
    class StockChanges {
        @Test
        void everyAssignmentUsingOttawaKeyboardIsRejectedAndSplitWins() {
            PlanReport r = DemoScenarios.stockChanges().run();
            List<Candidate> usingOttKeyboard = r.candidates().stream()
                    .filter(c -> c.assignment().get(0).equals(OTT)).toList();
            assertThat(usingOttKeyboard).hasSize(2).allSatisfy(c -> {
                assertThat(c.valid()).isFalse();
                assertThat(c.rejections()).anyMatch(m -> m.contains("Insufficient stock") && m.contains("Ottawa"));
            });
            assertThat(r.candidates().stream().filter(c -> c.assignment().get(0).equals(MTL)))
                    .allMatch(Candidate::valid);
            assertThat(r.winner().assignment()).containsExactly(MTL, OTT);
            assertThat(r.winner().totalCents()).isEqualTo(1100);
        }
    }

    @Nested
    @DisplayName("4. The whole order must arrive on time")
    class OnTime {
        @Test
        void fridayArrivalQualifiesForFridayDeadline() {
            PlanReport r = DemoScenarios.oneBoxWins().run();
            assertThat(r.winner().latestArrival()).isEqualTo(FRIDAY);
            assertThat(r.candidates()).allMatch(Candidate::valid);
        }

        @Test
        void oneLateBoxDisqualifiesTheSplit() {
            Scenario s = DemoScenarios.lateBox();
            PlanReport r = s.run();
            Candidate split = r.candidates().stream()
                    .filter(c -> c.assignment().equals(List.of(MTL, OTT))).findFirst().orElseThrow();
            assertThat(split.valid()).isFalse();
            assertThat(split.rejections()).singleElement().asString().contains("Late box").contains("Ottawa");
            // the on-time Montréal box of that split doesn't rescue it
            assertThat(split.boxes()).anyMatch(b -> b.warehouseId().equals(MTL) && b.arrives().equals(FRIDAY));

            assertThat(r.winner().assignment()).containsExactly(MTL, MTL);
            assertThat(r.winner().totalCents()).isEqualTo(1200);
            assertThat(r.result(Strategy.CHEAPEST_PER_ITEM).status()).isEqualTo(PlanStatus.REJECTED);
        }

        @Test
        void everyPlanLateMeansNoOnTimePlan() {
            PlanReport r = DemoScenarios.allLate().run();
            assertThat(r.status()).isEqualTo(PlanStatus.NO_ON_TIME_PLAN);
            assertThat(r.winner()).isNull();
            assertThat(r.candidates()).hasSize(4).noneMatch(Candidate::valid);
            assertThat(r.result(Strategy.FEWEST_BOXES_FIRST).status()).isEqualTo(PlanStatus.NO_ON_TIME_PLAN);
            assertThat(r.explanation()).startsWith("NO_ON_TIME_PLAN");
        }

        @Test
        void missingQuoteRejectsPlan() {
            QuoteTable q = new QuoteTable()
                    .put(MTL, List.of(KEYBOARD, MOUSE), 1200, FRIDAY);
            PlanReport r = run(seedWarehouses(), q);
            assertThat(r.winner().assignment()).containsExactly(MTL, MTL);
            assertThat(r.candidates().stream().filter(c -> !c.valid()))
                    .hasSize(3).allMatch(c -> c.rejections().stream().anyMatch(m -> m.startsWith("No shipping quote")));
        }
    }

    @Nested
    @DisplayName("5. Ties are deterministic")
    class Ties {
        @Test
        void equalPriceFavoursFewerBoxes() {
            PlanReport r = DemoScenarios.priceTie().run();
            assertThat(r.winner().assignment()).containsExactly(OTT, OTT);
            assertThat(r.winner().totalCents()).isEqualTo(1100);
            assertThat(r.explanation()).contains("won on fewer boxes");
        }

        @Test
        void exactTieUsesStableWarehouseOrder() {
            QuoteTable q = new QuoteTable()
                    .put(MTL, List.of(KEYBOARD, MOUSE), 900, FRIDAY)
                    .put(OTT, List.of(KEYBOARD, MOUSE), 900, FRIDAY);
            PlanReport r = run(seedWarehouses(), q);
            assertThat(r.winner().assignment()).containsExactly(MTL, MTL);
            assertThat(r.explanation()).contains("tie-break");
        }

        @Test
        void repeatedRunsGiveIdenticalResults() {
            for (Scenario s : DemoScenarios.all().values()) {
                PlanReport first = s.run();
                for (int i = 0; i < 20; i++) {
                    PlanReport again = s.run();
                    assertThat(again.winner()).isEqualTo(first.winner());
                    assertThat(again.candidates()).isEqualTo(first.candidates());
                    assertThat(again.explanation()).isEqualTo(first.explanation());
                    assertThat(again.strategies()).isEqualTo(first.strategies());
                }
            }
        }
    }
}
