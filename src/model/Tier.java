package model;

/**
 * Which queue a process currently belongs to.
 * TIER1 = Round Robin queue, TIER2 = Non-Preemptive Priority queue (batch jobs).
 * NONE is used by schedulers that have no tiers (e.g. SJF) and for processes
 * that have not arrived yet or have already finished.
 */
public enum Tier {
    NONE("-"),
    TIER1("Tier 1"),
    TIER2("Tier 2");

    private final String label;

    Tier(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    @Override
    public String toString() {
        return label;
    }
}