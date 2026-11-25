package scheduler.acs;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import model.Process;
import model.ProcessResult;
import model.SimulationConfig;
import model.SimulationResult;
import model.Snapshot;
import model.Tier;
import scheduler.Scheduler;

/**
 * Adaptive Container Scheduler.
 *
 * Tier 1: Round Robin
 * Tier 2: Non-Preemptive Priority with Aging
 */
public class ACSScheduler implements Scheduler {

    @Override
    public String getName() {
        return "Adaptive Container Scheduler (ACS)";
    }

    @Override
    public SimulationResult run(
            List<Process> inputProcesses,
            SimulationConfig config) {

        if (config == null) {
            throw new IllegalArgumentException("Config cannot be null");
        }

        // Always work on fresh copies.
        List<Process> processes = Scheduler.copyOf(inputProcesses);

        // Initialize scheduler components.
        Tier1RoundRobin tier1 =
                new Tier1RoundRobin(config.getQuantum());

        AgingPolicy agingPolicy =
                new AgingPolicy(config.getAgingInterval());

        Tier2Priority tier2 =
                new Tier2Priority(agingPolicy);

        List<Snapshot> snapshots = new ArrayList<>();

        // Track processes that were demoted.
        Set<String> demotedProcesses = new HashSet<>();

        int currentTime = 0;
        int finishedCount = 0;

        Process runningProcess = null;
        Process contextSwitchTarget = null;

        int quantumRemaining = 0;
        int contextSwitchRemaining = 0;

        // Event text of the ongoing context switch (same text on every CS tick).
        String contextSwitchEvent = "";

        // Last process that actually used the CPU.
        String lastProcessName = null;

        // True if that last process finished (nothing to save in the switch).
        boolean lastProcessFinished = false;

        int quantum = config.getQuantum();
        int cs = config.getContextSwitch();

        while (finishedCount < processes.size()) {

            // ----------------------------------
            // 1. Admit newly arrived processes.
            // ----------------------------------

            for (Process p : processes) {

                if (p.getArrivalTime() == currentTime) {

                    p.moveToTier(Tier.TIER1, currentTime);

                    tier1.addProcess(p);
                }
            }

            // ----------------------------------
            // 2. Capture queues before dispatch.
            // ----------------------------------

            List<Snapshot.QueueView> queueViews =
                    captureQueues(tier1, tier2, agingPolicy, currentTime);

            // ----------------------------------
            // 3. Select a process if CPU is free.
            // ----------------------------------

            // (Skipped while a context switch is in progress:
            // the target was already selected.)
            if (contextSwitchTarget == null && runningProcess == null) {

                boolean selectedFromTier1 = false;

                // Tier 1 always has selection priority.
                if (!tier1.isEmpty()) {

                    runningProcess = tier1.pollNext();
                    selectedFromTier1 = true;

                } else if (!tier2.isEmpty()) {

                    runningProcess = tier2.pollNext(currentTime);
                }

                // No ready processes.
                if (runningProcess == null) {

                    snapshots.add(new Snapshot(
                            currentTime,
                            Snapshot.CpuState.IDLE,
                            null,
                            "",
                            queueViews
                    ));

                    currentTime++;

                    // CPU was idle, so no switch is needed
                    // from the previous process.
                    lastProcessName = null;

                    continue;
                }

                // Initialize the RR quantum.
                if (selectedFromTier1) {
                    quantumRemaining = quantum;
                }

                // ----------------------------------
                // 4. Account for context switching.
                // ----------------------------------

                // Only prepares the switch; the ticks are done in step 5.
                if (lastProcessName != null
                        && !lastProcessName.equals(
                                runningProcess.getName())
                        && cs > 0) {

                    contextSwitchTarget = runningProcess;

                    contextSwitchRemaining = cs;

                    // Same format as SJFScheduler and the PDF:
                    // "Context switch saves P1 and loads P2"
                    // or "Context switch loads P2" if P1 already finished.
                    contextSwitchEvent = lastProcessFinished
                            ? "Context switch loads "
                                    + contextSwitchTarget.getName()
                            : "Context switch saves " + lastProcessName
                                    + " and loads "
                                    + contextSwitchTarget.getName();

                    runningProcess = null;
                }
            }

            // ----------------------------------
            // 5. Run one tick of the context switch (if any).
            // ----------------------------------

            if (contextSwitchTarget != null) {

                snapshots.add(new Snapshot(
                        currentTime,
                        Snapshot.CpuState.CONTEXT_SWITCH,
                        null,
                        contextSwitchEvent,
                        queueViews
                ));

                contextSwitchRemaining--;
                currentTime++;

                if (contextSwitchRemaining == 0) {

                    runningProcess = contextSwitchTarget;
                    contextSwitchTarget = null;

                    if (runningProcess.getTier() == Tier.TIER1) {
                        quantumRemaining = quantum;
                    }
                }

                continue;
            }

            // ----------------------------------
            // 6. Run the selected process.
            // ----------------------------------

            if (!runningProcess.hasStarted()) {
                runningProcess.markStarted(currentTime);
            }

            snapshots.add(new Snapshot(
                    currentTime,
                    Snapshot.CpuState.RUNNING,
                    runningProcess.getName(),
                    "",
                    queueViews
            ));

            runningProcess.consume(1);

            currentTime++;

            // ----------------------------------
            // 7. Check if process has finished.
            // ----------------------------------

            if (runningProcess.isFinished()) {

                runningProcess.markFinished(currentTime);

                lastProcessName = runningProcess.getName();
                lastProcessFinished = true;

                runningProcess = null;
                quantumRemaining = 0;

                finishedCount++;

                continue;
            }

            // ----------------------------------
            // 8. Tier 1 quantum management.
            // ----------------------------------

            if (runningProcess.getTier() == Tier.TIER1) {

                quantumRemaining--;

                if (quantumRemaining == 0) {

                    // Process did not finish within its quantum.
                    // Demote it to Tier 2.

                    Process demoted = runningProcess;

                    demoted.moveToTier(Tier.TIER2, currentTime);

                    tier2.addProcess(demoted);

                    demotedProcesses.add(demoted.getName());

                    lastProcessName = demoted.getName();
                    lastProcessFinished = false;

                    runningProcess = null;
                }
            }

            // ----------------------------------
            // 9. Tier 2 is non-preemptive.
            // ----------------------------------
            //
            // If a Tier 2 process is running, we do nothing here.
            // It continues until it finishes.
        }

        // ----------------------------------
        // 10. Build final process results.
        // ----------------------------------

        List<ProcessResult> results = new ArrayList<>();

        for (Process p : processes) {

            int finishTime = p.getFinishTime();

            int turnaroundTime =
                    finishTime - p.getArrivalTime();

            int waitingTime =
                    turnaroundTime - p.getBurstTime();

            int responseTime =
                    p.getFirstRunTime() - p.getArrivalTime();

            results.add(new ProcessResult(
                    p.getName(),
                    p.getArrivalTime(),
                    p.getBurstTime(),
                    finishTime,
                    turnaroundTime,
                    waitingTime,
                    responseTime,
                    demotedProcesses.contains(p.getName())
            ));
        }

        // ----------------------------------
        // 11. Return simulation result.
        // ----------------------------------

        return new SimulationResult(
                getName(),
                config,
                snapshots,
                results
        );
    }

    /**
     * Creates immutable queue snapshots.
     */
    private List<Snapshot.QueueView> captureQueues(
            Tier1RoundRobin tier1,
            Tier2Priority tier2,
            AgingPolicy agingPolicy,
            int currentTime) {

        List<Snapshot.QueueView> queues = new ArrayList<>();

        // Tier 1 displays initial priority.
        queues.add(
                Snapshot.QueueView.of(
                        "Tier 1",
                        tier1.getProcesses(),
                        Process::getInitialPriority
                )
        );

        // Tier 2 displays effective (aged) priority.
        queues.add(
                Snapshot.QueueView.of(
                        "Tier 2",
                        tier2.getProcesses(currentTime),
                        p -> agingPolicy.getEffectivePriority(
                                p, currentTime)
                )
        );

        return queues;
    }
}