
package scheduler.acs;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import model.Process;

/**
 * Manages Tier 2: Non-Preemptive Priority Queue.
 *
 * Lower effective priority number = higher priority.
 * Aging is calculated dynamically by AgingPolicy.
 */
public class Tier2Priority {

    private final List<Process> processes;
    private final AgingPolicy agingPolicy;

    public Tier2Priority(AgingPolicy agingPolicy) {
        if (agingPolicy == null) {
            throw new IllegalArgumentException("AgingPolicy cannot be null");
        }

        this.agingPolicy = agingPolicy;
        this.processes = new ArrayList<>();
    }

    /**
     * Adds a demoted process to Tier 2.
     */
    public void addProcess(Process process) {
        if (process == null) {
            throw new IllegalArgumentException("Process cannot be null");
        }

        if (processes.contains(process)) {
            throw new IllegalArgumentException("Process already exists in Tier 2");
        }

        processes.add(process);
    }

    /**
     * Returns and removes the process with the highest effective priority.
     *
     * currentTime is needed to calculate aging.
     */
    public Process pollNext(int currentTime) {

        if (processes.isEmpty()) {
            return null;
        }

        processes.sort(
            Comparator
                .comparingInt((Process p) ->
                    agingPolicy.getEffectivePriority(p, currentTime))
                .thenComparingInt(Process::getArrivalTime)
                .thenComparing(Process::getName)
        );

        return processes.remove(0);
    }

    /**
     * Returns a sorted copy of Tier 2 processes.
     * Does not modify the original queue.
     */
    public List<Process> getProcesses(int currentTime) {

        List<Process> result = new ArrayList<>(processes);

        result.sort(
            Comparator
                .comparingInt((Process p) ->
                    agingPolicy.getEffectivePriority(p, currentTime))
                .thenComparingInt(Process::getArrivalTime)
                .thenComparing(Process::getName)
        );

        return result;
    }

    public boolean isEmpty() {
        return processes.isEmpty();
    }

    public int size() {
        return processes.size();
    }
}
