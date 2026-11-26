package experiment;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.ToDoubleFunction;

import model.SimulationConfig;
import model.SimulationResult;
import scheduler.SJFScheduler;
import scheduler.Scheduler;
import scheduler.acs.ACSScheduler;
import testing.TestCases;

/**
 * Runs every test case with many (Quantum, Context Switch) pairs and both
 * algorithms.
 * This is the data source for the report graphs ("for each test case apply
 * different
 * Context Switching and Quantum values").
 *
 * Sweep: Q in QUANTA x CS in CONTEXT_SWITCHES, for SJF and for ACS.
 * SJF ignores Q, so its rows repeat across Q. They are kept on purpose so the
 * data is a
 * complete grid (easy to chart or pivot): the SJF line is flat over Q and works
 * as a baseline.
 * The aging interval of each test case's own config is kept unchanged.
 *
 * Every run uses tc.getProcesses(), which returns fresh copies, so runs never
 * affect each other.
 */
public final class ExperimentRunner {

    /** Quantum values tried in the sweep. */
    public static final int[] QUANTA = { 1, 2, 3, 4, 5, 6 };

    /** Context switch values tried in the sweep. */
    public static final int[] CONTEXT_SWITCHES = { 0, 1, 2, 3 };

    private static final String SJF_NAME = new SJFScheduler().getName();

    private ExperimentRunner() {
    }

    // =====================================================================
    // One line of the sweep
    // =====================================================================

    /**
     * One test case, one algorithm, one (Q, CS) pair and the metrics of that run.
     * Immutable.
     * Created here, written to a file by output.CsvExporter.
     */
    public static final class ExperimentRow {

        private final String testCase;
        private final String algorithm;
        private final int quantum;
        private final int contextSwitch;
        private final int agingInterval;
        private final double avgWaitingTime;
        private final double avgTurnaroundTime;
        private final double avgResponseTime;
        private final double cpuUtilization; // fraction 0.0 - 1.0
        private final int contextSwitchTime; // total CPU ticks spent switching
        private final int demotedCount;

        public ExperimentRow(String testCase, String algorithm, int quantum, int contextSwitch,
                int agingInterval, double avgWaitingTime, double avgTurnaroundTime,
                double avgResponseTime, double cpuUtilization, int contextSwitchTime,
                int demotedCount) {
            this.testCase = testCase;
            this.algorithm = algorithm;
            this.quantum = quantum;
            this.contextSwitch = contextSwitch;
            this.agingInterval = agingInterval;
            this.avgWaitingTime = avgWaitingTime;
            this.avgTurnaroundTime = avgTurnaroundTime;
            this.avgResponseTime = avgResponseTime;
            this.cpuUtilization = cpuUtilization;
            this.contextSwitchTime = contextSwitchTime;
            this.demotedCount = demotedCount;
        }

        /**
         * Builds a row from a finished simulation (Q, CS and aging are read from its
         * config).
         */
        public static ExperimentRow from(String testCaseName, SimulationResult result) {
            SimulationConfig c = result.getConfig();
            return new ExperimentRow(
                    testCaseName,
                    result.getAlgorithmName(),
                    c.getQuantum(),
                    c.getContextSwitch(),
                    c.getAgingInterval(),
                    result.getAverageWaitingTime(),
                    result.getAverageTurnaroundTime(),
                    result.getAverageResponseTime(),
                    result.getCpuUtilization(),
                    result.getContextSwitchTime(),
                    result.getDemotedCount());
        }

        public String getTestCase() {
            return testCase;
        }

        public String getAlgorithm() {
            return algorithm;
        }

        public int getQuantum() {
            return quantum;
        }

        public int getContextSwitch() {
            return contextSwitch;
        }

        public int getAgingInterval() {
            return agingInterval;
        }

        public double getAvgWaitingTime() {
            return avgWaitingTime;
        }

        public double getAvgTurnaroundTime() {
            return avgTurnaroundTime;
        }

        public double getAvgResponseTime() {
            return avgResponseTime;
        }

        /** Fraction between 0.0 and 1.0. */
        public double getCpuUtilization() {
            return cpuUtilization;
        }

        public int getContextSwitchTime() {
            return contextSwitchTime;
        }

        public int getDemotedCount() {
            return demotedCount;
        }

        @Override
        public String toString() {
            return String.format(Locale.ROOT,
                    "%s | %s | Q=%d CS=%d | WT=%.2f TAT=%.2f RT=%.2f util=%.1f%%",
                    testCase, algorithm, quantum, contextSwitch,
                    avgWaitingTime, avgTurnaroundTime, avgResponseTime, cpuUtilization * 100);
        }
    }

    // =====================================================================
    // Running the sweep
    // =====================================================================

    /** Runs the sweep for every test case in TestCases.getAll(). */
    public static List<ExperimentRow> runAll() {
        List<ExperimentRow> rows = new ArrayList<>();
        for (TestCases.TestCase tc : TestCases.getAll()) {
            rows.addAll(runTestCase(tc));
        }
        return rows;
    }

    /**
     * Runs the sweep for one test case (SJF first, then ACS; Q outer loop, CS inner
     * loop).
     */
    public static List<ExperimentRow> runTestCase(TestCases.TestCase tc) {
        List<Scheduler> schedulers = List.of(new SJFScheduler(), new ACSScheduler());
        List<ExperimentRow> rows = new ArrayList<>();

        for (Scheduler scheduler : schedulers) {
            for (int q : QUANTA) {
                for (int cs : CONTEXT_SWITCHES) {
                    SimulationConfig config = tc.getConfig().withQuantum(q).withContextSwitch(cs);
                    SimulationResult result = scheduler.run(tc.getProcesses(), config);
                    rows.add(ExperimentRow.from(tc.getName(), result));
                }
            }
        }
        return rows;
    }

    // =====================================================================
    // Console summary
    // =====================================================================

    /**
     * For each test case: one table of average waiting time and one of average
     * turnaround time. Columns are CS. Rows are the quantum values for ACS, plus a
     * single
     * SJF baseline row (SJF ignores Q). In every CS column the best (lowest) ACS
     * quantum
     * is marked with '*' (no mark when all quanta give the same value).
     */
    public static void printSummary(List<ExperimentRow> rows) {
        Map<String, List<ExperimentRow>> byTestCase = new LinkedHashMap<>();
        for (ExperimentRow r : rows) {
            byTestCase.computeIfAbsent(r.getTestCase(), k -> new ArrayList<>()).add(r);
        }

        for (Map.Entry<String, List<ExperimentRow>> entry : byTestCase.entrySet()) {
            List<ExperimentRow> sjf = new ArrayList<>();
            List<ExperimentRow> acs = new ArrayList<>();
            for (ExperimentRow r : entry.getValue()) {
                if (r.getAlgorithm().equals(SJF_NAME)) {
                    sjf.add(r);
                } else {
                    acs.add(r);
                }
            }

            String rule = "=".repeat(border().length());
            System.out.println();
            System.out.println(rule);
            System.out.println(" " + entry.getKey());
            System.out.println(rule);
            printTable("Average Waiting Time", sjf, acs, ExperimentRow::getAvgWaitingTime);
            printTable("Average Turnaround Time", sjf, acs, ExperimentRow::getAvgTurnaroundTime);
        }
    }

    private static void printTable(String title, List<ExperimentRow> sjf, List<ExperimentRow> acs,
            ToDoubleFunction<ExperimentRow> metric) {
        String border = border();

        // Lowest and highest ACS value in every CS column (to mark the best quantum).
        double[] best = new double[CONTEXT_SWITCHES.length];
        double[] worst = new double[CONTEXT_SWITCHES.length];
        for (int c = 0; c < CONTEXT_SWITCHES.length; c++) {
            best[c] = Double.MAX_VALUE;
            worst[c] = -Double.MAX_VALUE;
            for (int q : QUANTA) {
                double v = lookup(acs, q, CONTEXT_SWITCHES[c], metric);
                if (!Double.isNaN(v)) {
                    best[c] = Math.min(best[c], v);
                    worst[c] = Math.max(worst[c], v);
                }
            }
        }

        System.out.println();
        System.out.println(" " + title + " (time units, * = best ACS quantum)");
        System.out.println(border);

        StringBuilder header = new StringBuilder(String.format(Locale.ROOT, "| %-10s |", "Algorithm"));
        for (int cs : CONTEXT_SWITCHES) {
            header.append(String.format(Locale.ROOT, " %-7s |", "CS=" + cs));
        }
        System.out.println(header);
        System.out.println(border);

        // SJF baseline (same for every Q, so the first Q is enough).
        StringBuilder sjfRow = new StringBuilder(String.format(Locale.ROOT, "| %-10s |", "SJF"));
        for (int cs : CONTEXT_SWITCHES) {
            sjfRow.append(" ").append(cell(lookup(sjf, QUANTA[0], cs, metric), false)).append(" |");
        }
        System.out.println(sjfRow);
        System.out.println(border);

        for (int q : QUANTA) {
            StringBuilder line = new StringBuilder(
                    String.format(Locale.ROOT, "| %-10s |", "ACS Q=" + q));
            for (int c = 0; c < CONTEXT_SWITCHES.length; c++) {
                double v = lookup(acs, q, CONTEXT_SWITCHES[c], metric);
                boolean isBest = !Double.isNaN(v)
                        && worst[c] - best[c] > 1e-9
                        && Math.abs(v - best[c]) < 1e-9;
                line.append(" ").append(cell(v, isBest)).append(" |");
            }
            System.out.println(line);
        }
        System.out.println(border);
    }

    /** 7 characters wide: right-aligned number, then '*' or a space. */
    private static String cell(double value, boolean best) {
        if (Double.isNaN(value)) {
            return String.format(Locale.ROOT, "%7s", "-");
        }
        return String.format(Locale.ROOT, "%6.2f", value) + (best ? "*" : " ");
    }

    private static String border() {
        StringBuilder sb = new StringBuilder("+").append("-".repeat(12)).append("+");
        for (int i = 0; i < CONTEXT_SWITCHES.length; i++) {
            sb.append("-".repeat(9)).append("+");
        }
        return sb.toString();
    }

    private static double lookup(List<ExperimentRow> rows, int q, int cs,
            ToDoubleFunction<ExperimentRow> metric) {
        for (ExperimentRow r : rows) {
            if (r.getQuantum() == q && r.getContextSwitch() == cs) {
                return metric.applyAsDouble(r);
            }
        }
        return Double.NaN;
    }
}