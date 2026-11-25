package metrics;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import model.Process;
import model.ProcessResult;

/**
 * Single place where the scheduling metrics are computed (used by SJF and ACS).
 *
 * Formulas (as required by the assignment):
 *   turnaround = finish - arrival
 *   waiting    = turnaround - burst   (context-switch time counts as waiting)
 *   response   = first time on CPU - arrival
 *
 * Averages are plain arithmetic means over all processes.
 *
 * All methods are static and stateless. Processes must be finished and must have
 * started, otherwise an IllegalStateException is thrown (it means a scheduler bug).
 */
public final class MetricsCalculator {

    private MetricsCalculator() {
    }

    // ---------- per-process metrics ----------

    public static int turnaroundTime(Process p) {
        requireFinished(p);
        return p.getFinishTime() - p.getArrivalTime();
    }

    public static int waitingTime(Process p) {
        return turnaroundTime(p) - p.getBurstTime();
    }

    public static int responseTime(Process p) {
        requireFinished(p);
        return p.getFirstRunTime() - p.getArrivalTime();
    }

    /** Builds the result row of one finished process. */
    public static ProcessResult toResult(Process p, boolean demoted) {
        return new ProcessResult(
                p.getName(),
                p.getArrivalTime(),
                p.getBurstTime(),
                p.getFinishTime(),
                turnaroundTime(p),
                waitingTime(p),
                responseTime(p),
                demoted);
    }

    /**
     * Builds one result row per process, in the same order as the input list.
     *
     * @param demotedNames names of processes that were moved to Tier 2 (ACS);
     *                     pass an empty set (or use the other overload) for SJF
     */
    public static List<ProcessResult> toResults(List<Process> processes, Set<String> demotedNames) {
        if (processes == null || processes.isEmpty()) {
            throw new IllegalArgumentException("at least one process is required");
        }
        Set<String> demoted = (demotedNames == null) ? Collections.emptySet() : demotedNames;
        List<ProcessResult> results = new ArrayList<>(processes.size());
        for (Process p : processes) {
            results.add(toResult(p, demoted.contains(p.getName())));
        }
        return results;
    }

    /** Same as above, for algorithms with no demotion (SJF). */
    public static List<ProcessResult> toResults(List<Process> processes) {
        return toResults(processes, Collections.emptySet());
    }

    // ---------- averages ----------

    public static double averageWaitingTime(List<ProcessResult> results) {
        if (results == null || results.isEmpty()) return 0;
        double sum = 0;
        for (ProcessResult r : results) sum += r.getWaitingTime();
        return sum / results.size();
    }

    public static double averageTurnaroundTime(List<ProcessResult> results) {
        if (results == null || results.isEmpty()) return 0;
        double sum = 0;
        for (ProcessResult r : results) sum += r.getTurnaroundTime();
        return sum / results.size();
    }

    public static double averageResponseTime(List<ProcessResult> results) {
        if (results == null || results.isEmpty()) return 0;
        double sum = 0;
        for (ProcessResult r : results) sum += r.getResponseTime();
        return sum / results.size();
    }

    // ---------- internal ----------

    private static void requireFinished(Process p) {
        if (p == null) {
            throw new IllegalArgumentException("process cannot be null");
        }
        if (p.getFinishTime() == Process.NOT_SET || !p.hasStarted()) {
            throw new IllegalStateException(
                    p.getName() + ": metrics requested before the process started and finished");
        }
    }
}