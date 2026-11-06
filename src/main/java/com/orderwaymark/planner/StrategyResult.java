package com.orderwaymark.planner;

/** What one strategy decided. {@code plan} is null only when the strategy could not form a plan. */
public record StrategyResult(Strategy strategy, String label, PlanStatus status, Candidate plan, String explanation) {
}
