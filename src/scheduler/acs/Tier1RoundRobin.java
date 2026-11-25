package scheduler.acs;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import model.Process;

/**
 * FIFO ready queue for ACS Tier 1 (Round Robin).
 *
 * This class manages queue order only. ACSScheduler owns the time quantum and
 * decides when a running process finishes or is demoted to Tier 2.
 */
public final class Tier1RoundRobin {

    private final int quantum;
    private final Deque<Process> readyQueue;

    public Tier1RoundRobin(int quantum) {
        if (quantum <= 0) {
            throw new IllegalArgumentException(
                    "quantum must be > 0 (got " + quantum + ")");
        }
        this.quantum = quantum;
        this.readyQueue = new ArrayDeque<>();
    }

    /** Adds a newly arrived process to the end of the Tier 1 queue. */
    public void addProcess(Process process) {
        if (process == null) {
            throw new IllegalArgumentException("Process cannot be null");
        }
        if (readyQueue.contains(process)) {
            throw new IllegalArgumentException(
                    process.getName() + " is already waiting in Tier 1");
        }
        readyQueue.addLast(process);
    }

    /** Removes and returns the process at the front of the Tier 1 queue. */
    public Process pollNext() {
        return readyQueue.pollFirst();
    }

    /** Returns a snapshot copy in FIFO order; the live queue is not modified. */
    public List<Process> getProcesses() {
        return new ArrayList<>(readyQueue);
    }

    public boolean isEmpty() {
        return readyQueue.isEmpty();
    }

    public int size() {
        return readyQueue.size();
    }

    public int getQuantum() {
        return quantum;
    }
}
