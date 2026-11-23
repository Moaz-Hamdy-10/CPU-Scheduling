package model;

/**
 * Immutable final result of one process: one row of the results table.
 *
 * It only holds values. The formulas live in MetricsCalculator:
 *   turnaround = finish - arrival
 *   waiting    = turnaround - burst   (context-switch time counts as waiting)
 *   response   = first time on CPU - arrival
 */
public final class ProcessResult {

    private final String name;
    private final int arrivalTime;
    private final int burstTime;
    private final int finishTime;
    private final int turnaroundTime;
    private final int waitingTime;
    private final int responseTime;
    private final boolean demoted; // ACS: was it ever moved from Tier 1 to Tier 2 (always false for SJF)

    public ProcessResult(String name, int arrivalTime, int burstTime, int finishTime,
                         int turnaroundTime, int waitingTime, int responseTime, boolean demoted) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("process name is required");
        }
        if (finishTime < arrivalTime + burstTime) {
            throw new IllegalArgumentException(name + ": finish time can not be before arrival + burst");
        }
        if (turnaroundTime < 0 || waitingTime < 0 || responseTime < 0) {
            throw new IllegalArgumentException(name + ": metrics can not be negative");
        }
        this.name = name;
        this.arrivalTime = arrivalTime;
        this.burstTime = burstTime;
        this.finishTime = finishTime;
        this.turnaroundTime = turnaroundTime;
        this.waitingTime = waitingTime;
        this.responseTime = responseTime;
        this.demoted = demoted;
    }

    public String getName() {
        return name;
    }

    public int getArrivalTime() {
        return arrivalTime;
    }

    public int getBurstTime() {
        return burstTime;
    }

    public int getFinishTime() {
        return finishTime;
    }

    public int getTurnaroundTime() {
        return turnaroundTime;
    }

    public int getWaitingTime() {
        return waitingTime;
    }

    public int getResponseTime() {
        return responseTime;
    }

    public boolean wasDemoted() {
        return demoted;
    }

    @Override
    public String toString() {
        return name + " | arrival=" + arrivalTime + " burst=" + burstTime + " finish=" + finishTime
                + " TAT=" + turnaroundTime + " WT=" + waitingTime + " RT=" + responseTime
                + (demoted ? " (demoted)" : "");
    }
}