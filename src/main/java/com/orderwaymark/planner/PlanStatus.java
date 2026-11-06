package com.orderwaymark.planner;

public enum PlanStatus {
    /** A valid, on-time plan was chosen. */
    PLANNED,
    /** The strategy produced a plan, but it failed stock, quote or deadline checks. */
    REJECTED,
    /** No assignment satisfies stock, quotes and the deadline. */
    NO_ON_TIME_PLAN
}
