package com.orderwaymark.planner;

import java.util.List;

/** Full planner output: the decision, every candidate considered, and each strategy's pick. */
public record PlanReport(Order order, PlanStatus status, Candidate winner, String explanation,
                         List<Candidate> candidates, List<StrategyResult> strategies) {

    public StrategyResult result(Strategy strategy) {
        return strategies.stream().filter(s -> s.strategy() == strategy).findFirst().orElseThrow();
    }
}
