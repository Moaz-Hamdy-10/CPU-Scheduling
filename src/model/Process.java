package model;

/**
 * One process in the simulation.
 *
 * Input data (name, arrival, burst, initial priority) never changes.
 * Runtime state (remaining time, tier, first run, finish) is mutated by the schedulers.
 *
 * Rules:
 *  - Lower priority number = higher priority.
 *  - Effective (aged) priority is NOT stored here; AgingPolicy computes it on demand.
 *  - Metrics (WT / TAT / RT) are NOT computed here; MetricsCalculator does that.
 *  - Never share a Process between runs: call copy() to get a clean instance.
 */
public class Process {

    public static final int NOT_SET = -1;

    // ---- input data (immutable) ----
    private final String name;
    private final int arrivalTime;
    private final int burstTime;
    private final int initialPriority;

    // ---- runtime state ----
    private int remainingTime;
    private Tier tier;
    private int tier2EntryTime;   // when the process entered Tier 2 (aging reference)
    private int firstRunTime;     // first time the process got the CPU (for response time)
    private int finishTime;

    public Process(String name, int arrivalTime, int burstTime, int initialPriority) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("process name is required");
        }
        if (arrivalTime < 0) {
            throw new IllegalArgumentException(name + ": arrivalTime must be >= 0");
        }
        if (burstTime <= 0) {
            throw new IllegalArgumentException(name + ": burstTime must be > 0");
        }
        this.name = name;
        this.arrivalTime = arrivalTime;
        this.burstTime = burstTime;
        this.initialPriority = initialPriority;
        resetRuntimeState();
    }

    private void resetRuntimeState() {
        this.remainingTime = burstTime;
        this.tier = Tier.NONE;
        this.tier2EntryTime = NOT_SET;
        this.firstRunTime = NOT_SET;
        this.finishTime = NOT_SET;
    }

    /** Fresh copy with the same input data and a clean runtime state. */
    public Process copy() {
        return new Process(name, arrivalTime, burstTime, initialPriority);
    }

    // ---- runtime behaviour ----

    /** Runs the process on the CPU for the given number of time units. */
    public void consume(int units) {
        if (units <= 0) {
            throw new IllegalArgumentException(name + ": units must be > 0");
        }
        if (units > remainingTime) {
            throw new IllegalStateException(
                    name + ": cannot consume " + units + " units, only " + remainingTime + " remaining");
        }
        remainingTime -= units;
    }

    public boolean isFinished() {
        return remainingTime == 0;
    }

    /** Records the first CPU time. Calling it again later has no effect. */
    public void markStarted(int time) {
        if (firstRunTime == NOT_SET) {
            firstRunTime = time;
        }
    }

    public void markFinished(int time) {
        this.finishTime = time;
        this.tier = Tier.NONE;
    }

    /** Places the process in a tier. Entering Tier 2 also starts its aging clock. */
    public void moveToTier(Tier newTier, int currentTime) {
        this.tier = newTier;
        if (newTier == Tier.TIER2) {
            this.tier2EntryTime = currentTime;
        }
    }

    // ---- getters ----

    public String getName() {
        return name;
    }

    public int getArrivalTime() {
        return arrivalTime;
    }

    public int getBurstTime() {
        return burstTime;
    }

    public int getInitialPriority() {
        return initialPriority;
    }

    public int getRemainingTime() {
        return remainingTime;
    }

    public Tier getTier() {
        return tier;
    }

    public int getTier2EntryTime() {
        return tier2EntryTime;
    }

    public int getFirstRunTime() {
        return firstRunTime;
    }

    public boolean hasStarted() {
        return firstRunTime != NOT_SET;
    }

    public int getFinishTime() {
        return finishTime;
    }

    @Override
    public String toString() {
        return name + " (remaining: " + remainingTime + ", Priority: " + initialPriority + ")";
    }
}