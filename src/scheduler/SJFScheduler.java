package scheduler;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import metrics.MetricsCalculator;
import model.Process;
import model.ProcessResult;
import model.SimulationConfig;
import model.SimulationResult;
import model.Snapshot;
import model.Tier;

/**
 * Preemptive Shortest-Job-First (Shortest Remaining Time First).
 *
 * Rules:
 *  - At every tick the process with the shortest remaining time must be the one on the CPU.
 *  - A running process is preempted only by a STRICTLY shorter one (ties keep the running
 *    process, so no useless context switch).
 *  - Ties between ready processes: shorter remaining, then earlier arrival, then name.
 *  - Context switch (config.getContextSwitch()) is charged whenever the CPU moves from a
 *    process that used it (finished or preempted) to a DIFFERENT process.
 *    After an idle period no switch is charged (same convention as ACSScheduler).
 *  - During a context switch the CPU is CONTEXT_SWITCH, nothing runs, and the target is
 *    already committed (arrivals during the switch just join the ready queue).
 *  - Quantum and aging are ignored by SJF.
 *
 * Snapshot conventions are the same as ACSScheduler: one snapshot per tick, queues are
 * captured after arrivals / preemption and before the dispatcher removes the next process.
 * SJF reports one queue named "Ready".
 */
public class SJFScheduler implements Scheduler {

    private static final Comparator<Process> SHORTEST_FIRST =
            Comparator.comparingInt(Process::getRemainingTime)
                    .thenComparingInt(Process::getArrivalTime)
                    .thenComparing(Process::getName);

    @Override
    public String getName() {
        return "Preemptive SJF";
    }

    @Override
    public SimulationResult run(List<Process> inputProcesses, SimulationConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("Config cannot be null");
        }

        List<Process> processes = Scheduler.copyOf(inputProcesses);
        List<Process> ready = new ArrayList<>();
        List<Snapshot> snapshots = new ArrayList<>();

        int cs = config.getContextSwitch();
        int currentTime = 0;
        int finishedCount = 0;

        Process running = null;
        Process lastProcess = null;      // last process that used the CPU (null after idle)
        Process csTarget = null;
        Process csFrom = null;
        int csRemaining = 0;

        while (finishedCount < processes.size()) {

            // 1. Admit arrivals.
            for (Process p : processes) {
                if (p.getArrivalTime() == currentTime) {
                    p.moveToTier(Tier.NONE, currentTime);
                    ready.add(p);
                }
            }

            // 2. Preemption check (not during a context switch).
            if (csTarget == null && running != null && !ready.isEmpty()) {
                Process best = shortest(ready);
                if (best.getRemainingTime() < running.getRemainingTime()) {
                    ready.add(running);
                    lastProcess = running;
                    running = null;
                }
            }

            // 3. Capture the queue before dispatch.
            List<Snapshot.QueueView> queues = captureQueue(ready);

            // 4. Dispatch if the CPU is free and no switch is in progress.
            if (csTarget == null && running == null) {
                if (ready.isEmpty()) {
                    snapshots.add(new Snapshot(currentTime, Snapshot.CpuState.IDLE, null, "", queues));
                    currentTime++;
                    lastProcess = null; // no switch needed after idle
                    continue;
                }

                Process next = shortest(ready);
                ready.remove(next);

                if (cs > 0 && lastProcess != null && lastProcess != next) {
                    csTarget = next;
                    csFrom = lastProcess;
                    csRemaining = cs;
                } else {
                    running = next;
                }
            }

            // 5. One tick of context switching.
            if (csTarget != null) {
                String event = csFrom.isFinished()
                        ? "Context switch loads " + csTarget.getName()
                        : "Context switch saves " + csFrom.getName() + " and loads " + csTarget.getName();

                snapshots.add(new Snapshot(currentTime, Snapshot.CpuState.CONTEXT_SWITCH, null, event, queues));
                currentTime++;
                csRemaining--;

                if (csRemaining == 0) {
                    running = csTarget;
                    csTarget = null;
                    csFrom = null;
                }
                continue;
            }

            // 6. One tick of execution.
            running.markStarted(currentTime);
            snapshots.add(new Snapshot(currentTime, Snapshot.CpuState.RUNNING, running.getName(), "", queues));
            running.consume(1);
            currentTime++;

            // 7. Completion.
            if (running.isFinished()) {
                running.markFinished(currentTime);
                lastProcess = running;
                running = null;
                finishedCount++;
            }
        }

        List<ProcessResult> results = MetricsCalculator.toResults(processes);
        return new SimulationResult(getName(), config, snapshots, results);
    }

    private static Process shortest(List<Process> list) {
        Process best = list.get(0);
        for (Process p : list) {
            if (SHORTEST_FIRST.compare(p, best) < 0) {
                best = p;
            }
        }
        return best;
    }

    private static List<Snapshot.QueueView> captureQueue(List<Process> ready) {
        List<Process> sorted = new ArrayList<>(ready);
        sorted.sort(SHORTEST_FIRST);
        List<Snapshot.QueueView> queues = new ArrayList<>();
        queues.add(Snapshot.QueueView.of("Ready", sorted, Process::getInitialPriority));
        return queues;
    }
}