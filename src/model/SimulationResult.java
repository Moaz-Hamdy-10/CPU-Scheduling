package model;

import java.util.List;
import java.util.Optional;

/**
 * The only thing a Scheduler returns. Immutable.
 *
 * MatrixPrinter, CsvExporter, ExperimentRunner, tests and the GUI all read from here.
 *
 * Derived values (averages and CPU time breakdown) are computed once in the constructor,
 * so every consumer sees the same numbers:
 *   totalTime         = latest finish time (simulation starts at t = 0)
 *   busyTime          = sum of all burst times (useful work)
 *   contextSwitchTime = number of CONTEXT_SWITCH snapshots (needs one snapshot per tick)
 *   idleTime          = totalTime - busyTime - contextSwitchTime
 *   cpuUtilization    = busyTime / totalTime   (0.0 - 1.0, context switching is overhead)
 */
public final class SimulationResult {

    private final String algorithmName;
    private final SimulationConfig config;
    private final List<Snapshot> snapshots;
    private final List<ProcessResult> processResults;

    private final double averageWaitingTime;
    private final double averageTurnaroundTime;
    private final double averageResponseTime;

    private final int totalTime;
    private final int busyTime;
    private final int contextSwitchTime;
    private final int idleTime;
    private final double cpuUtilization;
    private final int demotedCount;

    public SimulationResult(String algorithmName, SimulationConfig config,
                            List<Snapshot> snapshots, List<ProcessResult> processResults) {
        this.algorithmName = algorithmName;
        this.config = config;
        this.snapshots = List.copyOf(snapshots);
        this.processResults = List.copyOf(processResults);

        double sumWt = 0;
        double sumTat = 0;
        double sumRt = 0;
        int maxFinish = 0;
        int sumBurst = 0;
        int demoted = 0;
        for (ProcessResult r : this.processResults) {
            sumWt += r.getWaitingTime();
            sumTat += r.getTurnaroundTime();
            sumRt += r.getResponseTime();
            maxFinish = Math.max(maxFinish, r.getFinishTime());
            sumBurst += r.getBurstTime();
            if (r.wasDemoted()) demoted++;
        }
        int n = this.processResults.size();
        this.averageWaitingTime = (n == 0) ? 0 : sumWt / n;
        this.averageTurnaroundTime = (n == 0) ? 0 : sumTat / n;
        this.averageResponseTime = (n == 0) ? 0 : sumRt / n;

        int csTicks = 0;
        for (Snapshot s : this.snapshots) {
            if (s.getCpuState() == Snapshot.CpuState.CONTEXT_SWITCH) csTicks++;
        }
        this.totalTime = maxFinish;
        this.busyTime = sumBurst;
        this.contextSwitchTime = csTicks;
        this.idleTime = Math.max(0, totalTime - busyTime - contextSwitchTime);
        this.cpuUtilization = (totalTime == 0) ? 0 : (double) busyTime / totalTime;
        this.demotedCount = demoted;
    }

    public String getAlgorithmName() {
        return algorithmName;
    }

    public SimulationConfig getConfig() {
        return config;
    }

    /** One snapshot per tick, in time order. */
    public List<Snapshot> getSnapshots() {
        return snapshots;
    }

    public List<ProcessResult> getProcessResults() {
        return processResults;
    }

    public Optional<ProcessResult> findResult(String processName) {
        return processResults.stream().filter(r -> r.getName().equals(processName)).findFirst();
    }

    public double getAverageWaitingTime() {
        return averageWaitingTime;
    }

    public double getAverageTurnaroundTime() {
        return averageTurnaroundTime;
    }

    public double getAverageResponseTime() {
        return averageResponseTime;
    }

    public int getTotalTime() {
        return totalTime;
    }

    public int getBusyTime() {
        return busyTime;
    }

    public int getContextSwitchTime() {
        return contextSwitchTime;
    }

    public int getIdleTime() {
        return idleTime;
    }

    /** Fraction between 0.0 and 1.0. Multiply by 100 to print a percentage. */
    public double getCpuUtilization() {
        return cpuUtilization;
    }

    /** ACS only: how many processes were demoted to Tier 2 (0 for SJF). */
    public int getDemotedCount() {
        return demotedCount;
    }

    @Override
    public String toString() {
        return String.format("%s [%s] avgWT=%.2f avgTAT=%.2f avgRT=%.2f util=%.1f%%",
                algorithmName, config, averageWaitingTime, averageTurnaroundTime,
                averageResponseTime, cpuUtilization * 100);
    }
}