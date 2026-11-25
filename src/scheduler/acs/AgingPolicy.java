package scheduler.acs;

import model.Process;

/**
 * Calculates the effective priority of a process waiting in Tier 2.
 *
 * The smaller the priority number, the higher the priority. For every full
 * aging interval spent waiting in Tier 2, the priority number is reduced by 1.
 * The effective priority is capped at 1 so that it never becomes zero or
 * negative.
 *
 * Aging is measured from Process.getTier2EntryTime(), not from the process's
 * arrival time or its most recent CPU start time.
 */
public final class AgingPolicy {

    private final int agingInterval;

    public AgingPolicy(int agingInterval) {
        if (agingInterval <= 0) {
            throw new IllegalArgumentException(
                    "agingInterval must be > 0 (got " + agingInterval + ")");
        }
        this.agingInterval = agingInterval;
    }

    /**
     * Returns the process priority after applying all complete aging intervals.
     *
     * Example: initial priority 4, waiting 8 units, aging interval 4:
     * effective priority = max(1, 4 - floor(8 / 4)) = 2.
     *
     * @param process process currently waiting in Tier 2
     * @param currentTime current simulation time
     */
    public int getEffectivePriority(Process process, int currentTime) {
        if (process == null) {
            throw new IllegalArgumentException("Process cannot be null");
        }

        int tier2EntryTime = process.getTier2EntryTime();
        if (tier2EntryTime == Process.NOT_SET) {
            throw new IllegalArgumentException(
                    process.getName() + " has not entered Tier 2");
        }
        if (currentTime < tier2EntryTime) {
            throw new IllegalArgumentException(
                    "currentTime cannot be before the process entered Tier 2");
        }

        int waitingTime = currentTime - tier2EntryTime;
        int agingSteps = waitingTime / agingInterval;
        return Math.max(1, process.getInitialPriority() - agingSteps);
    }

    /** Number of complete aging intervals accumulated since entering Tier 2. */
    public int getAgingSteps(Process process, int currentTime) {
        if (process == null) {
            throw new IllegalArgumentException("Process cannot be null");
        }
        int tier2EntryTime = process.getTier2EntryTime();
        if (tier2EntryTime == Process.NOT_SET) {
            throw new IllegalArgumentException(
                    process.getName() + " has not entered Tier 2");
        }
        if (currentTime < tier2EntryTime) {
            throw new IllegalArgumentException(
                    "currentTime cannot be before the process entered Tier 2");
        }
        return (currentTime - tier2EntryTime) / agingInterval;
    }

    public int getAgingInterval() {
        return agingInterval;
    }
}