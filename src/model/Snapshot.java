package model;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.ToIntFunction;

/**
 * Immutable picture of the system at one time step. One row of the time-step matrix.
 *
 * Conventions:
 *  - Every scheduler records exactly one Snapshot per tick, for t = 0 .. totalTime-1.
 *  - Queues are captured after arrivals / demotions at time t, before the dispatcher
 *    removes the next process to run (this is how the PDF tables show them).
 *  - Everything is stored as plain values (names, numbers), never as Process references,
 *    so later simulation steps can not change old snapshots.
 *  - Queues are generic named lists: SJF reports one queue ("Ready"), ACS reports
 *    "Tier 1" and "Tier 2". The printer does not need to know which algorithm ran.
 */
public final class Snapshot {

    public enum CpuState {
        RUNNING,
        CONTEXT_SWITCH,
        IDLE
    }

    /** One process inside a queue, as it looked when the snapshot was taken. */
    public static final class QueueEntry {
        private final String processName;
        private final int remainingTime;
        private final int priority; // effective priority at snapshot time

        public QueueEntry(String processName, int remainingTime, int priority) {
            this.processName = Objects.requireNonNull(processName);
            this.remainingTime = remainingTime;
            this.priority = priority;
        }

        /** Copies the current values of the process. */
        public static QueueEntry of(Process p, int effectivePriority) {
            return new QueueEntry(p.getName(), p.getRemainingTime(), effectivePriority);
        }

        public String getProcessName() {
            return processName;
        }

        public int getRemainingTime() {
            return remainingTime;
        }

        public int getPriority() {
            return priority;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof QueueEntry)) return false;
            QueueEntry other = (QueueEntry) o;
            return remainingTime == other.remainingTime
                    && priority == other.priority
                    && processName.equals(other.processName);
        }

        @Override
        public int hashCode() {
            return Objects.hash(processName, remainingTime, priority);
        }

        /** Same format as the PDF: P1 (remaining: 4, Priority: 2) */
        @Override
        public String toString() {
            return processName + " (remaining: " + remainingTime + ", Priority: " + priority + ")";
        }
    }

    /** A named queue and its content, in queue order. */
    public static final class QueueView {
        private final String queueName;
        private final List<QueueEntry> entries;

        public QueueView(String queueName, List<QueueEntry> entries) {
            this.queueName = Objects.requireNonNull(queueName);
            this.entries = List.copyOf(entries);
        }

        /**
         * Captures a live queue. priorityOf decides which priority is shown, e.g.
         * p -> p.getInitialPriority() for Tier 1, or the aged priority for Tier 2.
         */
        public static QueueView of(String queueName, List<Process> processes, ToIntFunction<Process> priorityOf) {
            List<QueueEntry> copy = new ArrayList<>();
            for (Process p : processes) {
                copy.add(QueueEntry.of(p, priorityOf.applyAsInt(p)));
            }
            return new QueueView(queueName, copy);
        }

        public String getQueueName() {
            return queueName;
        }

        public List<QueueEntry> getEntries() {
            return entries;
        }

        public boolean isEmpty() {
            return entries.isEmpty();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof QueueView)) return false;
            QueueView other = (QueueView) o;
            return queueName.equals(other.queueName) && entries.equals(other.entries);
        }

        @Override
        public int hashCode() {
            return Objects.hash(queueName, entries);
        }

        /** Same format as the PDF: "Tier 1: [P2 (...), P3 (...)]" or "Tier 1: [Empty]". */
        @Override
        public String toString() {
            if (entries.isEmpty()) {
                return queueName + ": [Empty]";
            }
            StringBuilder sb = new StringBuilder(queueName).append(": [");
            for (int i = 0; i < entries.size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(entries.get(i));
            }
            return sb.append("]").toString();
        }
    }

    private final int time;
    private final CpuState cpuState;
    private final String runningProcess; // null unless cpuState == RUNNING
    private final String event;          // "" when nothing special happened
    private final List<QueueView> queues;

    public Snapshot(int time, CpuState cpuState, String runningProcess, String event, List<QueueView> queues) {
        if (time < 0) {
            throw new IllegalArgumentException("time must be >= 0");
        }
        this.cpuState = Objects.requireNonNull(cpuState);
        if (cpuState == CpuState.RUNNING && runningProcess == null) {
            throw new IllegalArgumentException("runningProcess is required when the CPU is RUNNING");
        }
        this.time = time;
        this.runningProcess = (cpuState == CpuState.RUNNING) ? runningProcess : null;
        this.event = (event == null) ? "" : event;
        this.queues = List.copyOf(queues);
    }

    public int getTime() {
        return time;
    }

    public CpuState getCpuState() {
        return cpuState;
    }

    /** Name of the running process, or null if the CPU is idle / switching. */
    public String getRunningProcess() {
        return runningProcess;
    }

    /** Free text such as "P1 demoted to Tier 2" or "Context switch saves P1 and loads P2". */
    public String getEvent() {
        return event;
    }

    public List<QueueView> getQueues() {
        return queues;
    }

    /**
     * True if everything except the time is identical to the other snapshot.
     * The printer uses it to merge consecutive identical rows (like "4 5 6" in the PDF).
     */
    public boolean sameStateAs(Snapshot other) {
        return cpuState == other.cpuState
                && Objects.equals(runningProcess, other.runningProcess)
                && event.equals(other.event)
                && queues.equals(other.queues);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("T=").append(time).append(" | ");
        switch (cpuState) {
            case RUNNING:
                sb.append(runningProcess);
                break;
            case CONTEXT_SWITCH:
                sb.append("CS");
                break;
            default:
                sb.append("IDLE");
        }
        for (QueueView q : queues) {
            sb.append(" | ").append(q);
        }
        if (!event.isEmpty()) {
            sb.append(" | ").append(event);
        }
        return sb.toString();
    }
}