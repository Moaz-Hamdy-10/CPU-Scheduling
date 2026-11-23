package model;

/**
 * Immutable parameters of one simulation run.
 *
 * quantum        : Round Robin time quantum used by ACS Tier 1 (ignored by SJF).
 * contextSwitch  : time units the CPU spends switching between two different processes.
 * agingInterval  : every this many time units spent waiting in Tier 2 lowers the
 *                  process priority number by 1 (a lower number = higher priority).
 */
public final class SimulationConfig {

    public static final int DEFAULT_AGING_INTERVAL = 4;

    private final int quantum;
    private final int contextSwitch;
    private final int agingInterval;

    public SimulationConfig(int quantum, int contextSwitch, int agingInterval) {
        if (quantum <= 0) {
            throw new IllegalArgumentException("quantum must be > 0 (got " + quantum + ")");
        }
        if (contextSwitch < 0) {
            throw new IllegalArgumentException("contextSwitch must be >= 0 (got " + contextSwitch + ")");
        }
        if (agingInterval <= 0) {
            throw new IllegalArgumentException("agingInterval must be > 0 (got " + agingInterval + ")");
        }
        this.quantum = quantum;
        this.contextSwitch = contextSwitch;
        this.agingInterval = agingInterval;
    }

    /** Uses the default aging interval. */
    public SimulationConfig(int quantum, int contextSwitch) {
        this(quantum, contextSwitch, DEFAULT_AGING_INTERVAL);
    }

    public int getQuantum() {
        return quantum;
    }

    public int getContextSwitch() {
        return contextSwitch;
    }

    public int getAgingInterval() {
        return agingInterval;
    }

    // Used by ExperimentRunner to sweep one parameter while keeping the others.

    public SimulationConfig withQuantum(int newQuantum) {
        return new SimulationConfig(newQuantum, contextSwitch, agingInterval);
    }

    public SimulationConfig withContextSwitch(int newContextSwitch) {
        return new SimulationConfig(quantum, newContextSwitch, agingInterval);
    }

    public SimulationConfig withAgingInterval(int newAgingInterval) {
        return new SimulationConfig(quantum, contextSwitch, newAgingInterval);
    }

    @Override
    public String toString() {
        return "Q=" + quantum + ", CS=" + contextSwitch + ", aging=" + agingInterval;
    }
}