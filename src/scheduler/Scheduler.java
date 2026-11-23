package scheduler;

import java.util.ArrayList;
import java.util.List;

import model.Process;
import model.SimulationConfig;
import model.SimulationResult;

/**
 * Common contract of every scheduling algorithm (SJFScheduler, ACSScheduler).
 * Main, ExperimentRunner, TestCases and the GUI only talk to this interface.
 *
 * Contract every implementation must follow:
 *  1. Never modify the input list or its Process objects. Work on copies (see copyOf).
 *  2. Deterministic: the same input and config always give the same result.
 *  3. No state kept between calls: the same scheduler object can run many simulations
 *     (ExperimentRunner calls run() dozens of times).
 *  4. Record exactly one Snapshot per tick, from t = 0 until the last process finishes.
 *  5. Context switch time is charged on every switch to a different process, and counted
 *     as CONTEXT_SWITCH snapshots (see the project Conventions).
 *  6. Return a fully built SimulationResult (snapshots + one ProcessResult per process).
 */
public interface Scheduler {

    /**
     * Simulates the given processes from t = 0 until all of them finish.
     *
     * @param processes input processes (not modified)
     * @param config    quantum, context switch time, aging interval
     * @return snapshots, per-process results and averages
     * @throws IllegalArgumentException if processes is null or empty
     */
    SimulationResult run(List<Process> processes, SimulationConfig config);

    /** Display name used in output and reports, e.g. "Preemptive SJF" or "ACS". */
    String getName();

    /** Clean copies of the input processes (same order, runtime state reset). */
    static List<Process> copyOf(List<Process> processes) {
        if (processes == null || processes.isEmpty()) {
            throw new IllegalArgumentException("at least one process is required");
        }
        List<Process> copies = new ArrayList<>(processes.size());
        for (Process p : processes) {
            copies.add(p.copy());
        }
        return copies;
    }
}