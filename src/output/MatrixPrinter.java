package output;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import model.ProcessResult;
import model.SimulationConfig;
import model.SimulationResult;
import model.Snapshot;

/**
 * Formats a SimulationResult as text, in the style of the assignment PDF:
 *
 * header algorithm, Q, CS, aging, number of processes, total time
 * matrix Time | Process | Tier, with merged cells (see formatMatrix)
 * analysis table one row per process (arrival, burst, finish, TAT, WT, RT,
 * demoted)
 * summary averages and CPU time breakdown
 *
 * All format* methods return Strings (so the GUI and the report can reuse
 * them); the
 * print* methods are thin wrappers around System.out / a PrintStream.
 * The class only reads from SimulationResult, so it works for SJF and ACS
 * alike.
 *
 * Locale.ROOT is used everywhere so numbers always print as "4.33", never
 * "4,33".
 */
public final class MatrixPrinter {

    private static final int WIDTH = 50;

    /**
     * Queues longer than this are split one process per line so the matrix stays
     * narrow.
     */
    private static final int MAX_QUEUE_LINE = 75;

    private MatrixPrinter() {
    }

    // =====================================================================
    // Header
    // =====================================================================

    public static String formatHeader(SimulationResult result) {
        SimulationConfig c = result.getConfig();
        StringBuilder sb = new StringBuilder();
        sb.append(banner("SCHEDULING SIMULATION"));
        sb.append(field("Algorithm", result.getAlgorithmName()));
        sb.append(field("Quantum", String.valueOf(c.getQuantum())));
        sb.append(field("Context Switch", String.valueOf(c.getContextSwitch())));
        sb.append(field("Aging Interval", String.valueOf(c.getAgingInterval())));
        sb.append(field("Total Processes", String.valueOf(result.getProcessResults().size())));
        sb.append(field("Total Time", String.valueOf(result.getTotalTime())));
        sb.append("=".repeat(WIDTH)).append('\n');
        return sb.toString();
    }

    private static String field(String label, String value) {
        return String.format(Locale.ROOT, "%-16s: %s\n", label, value);
    }

    // =====================================================================
    // Schedule matrix (PDF style)
    // =====================================================================

    /**
     * Three columns like the PDF: Time | Process | Tier.
     *
     * - Consecutive ticks with the same CPU state AND the same queues are merged
     * into
     * one row ("1-2" means the ticks starting at 1 and 2).
     * - The Process cell is merged like in the PDF: when the same process keeps the
     * CPU
     * over several rows (because the queues changed in between), its name appears
     * once and the cell stays open below it.
     * - A last row shows the finish time with all queues empty (as in the PDF).
     * - A context switch shows "CS" in the Process column and its description
     * ("Context switch saves P1 and loads P2") as the first line of the Tier cell.
     * - Each queue is on its own line. A long queue is split one process per line.
     */
    public static String formatMatrix(SimulationResult result) {
        List<Snapshot> snapshots = result.getSnapshots();
        if (snapshots.isEmpty()) {
            return "(no snapshots)\n";
        }

        // 1. Group consecutive identical snapshots.
        List<int[]> groups = new ArrayList<>(); // each: {firstIndex, lastIndex}
        int start = 0;
        for (int i = 1; i < snapshots.size(); i++) {
            if (!snapshots.get(i).sameStateAs(snapshots.get(start))) {
                groups.add(new int[] { start, i - 1 });
                start = i;
            }
        }
        groups.add(new int[] { start, snapshots.size() - 1 });

        // 2. Build the cell texts and measure the column widths.
        // One extra row at the end shows the final (empty) queues at the finish time,
        // like the last row of the PDF tables.
        int n = groups.size();
        int rows = n + 1;
        String[] timeLabels = new String[rows];
        String[] cpuLabels = new String[rows];
        boolean[] newCpuRun = new boolean[rows];
        List<List<String>> details = new ArrayList<>();

        String tierHeader = tierHeader(snapshots.get(0));
        int timeWidth = "Time".length();
        int cpuWidth = "Process".length();
        int detailWidth = tierHeader.length();

        String previousKey = null;
        for (int i = 0; i < n; i++) {
            Snapshot first = snapshots.get(groups.get(i)[0]);
            Snapshot last = snapshots.get(groups.get(i)[1]);

            timeLabels[i] = (first.getTime() == last.getTime())
                    ? String.valueOf(first.getTime())
                    : first.getTime() + "-" + last.getTime();
            cpuLabels[i] = cpuLabel(first);

            // Same process (or same switch / idle stretch) as the row above -> merged cell.
            String key = cpuLabels[i] + "|" + first.getEvent();
            newCpuRun[i] = !key.equals(previousKey);
            previousKey = key;

            List<String> lines = detailLines(first);
            details.add(lines);

            timeWidth = Math.max(timeWidth, timeLabels[i].length());
            cpuWidth = Math.max(cpuWidth, cpuLabels[i].length());
            for (String line : lines) {
                detailWidth = Math.max(detailWidth, line.length());
            }
        }

        // Final row: time = finish time, nobody on the CPU, every queue empty.
        Snapshot lastSnapshot = snapshots.get(snapshots.size() - 1);
        timeLabels[n] = String.valueOf(lastSnapshot.getTime() + 1);
        cpuLabels[n] = "";
        newCpuRun[n] = true;
        List<String> finalLines = new ArrayList<>();
        for (Snapshot.QueueView q : lastSnapshot.getQueues()) {
            finalLines.add(q.getQueueName() + ": [Empty]");
        }
        details.add(finalLines);
        timeWidth = Math.max(timeWidth, timeLabels[n].length());
        for (String line : finalLines) {
            detailWidth = Math.max(detailWidth, line.length());
        }

        // 3. Render.
        String closed = rule(timeWidth, cpuWidth, detailWidth, true);
        String open = rule(timeWidth, cpuWidth, detailWidth, false);

        StringBuilder sb = new StringBuilder();
        sb.append(closed).append('\n');
        sb.append(tableRow("Time", "Process", tierHeader, timeWidth, cpuWidth, detailWidth)).append('\n');
        sb.append(closed).append('\n');

        for (int i = 0; i < rows; i++) {
            List<String> lines = details.get(i);
            int count = Math.max(1, lines.size());
            for (int l = 0; l < count; l++) {
                String time = (l == 0) ? timeLabels[i] : "";
                String cpu = (l == 0 && newCpuRun[i]) ? cpuLabels[i] : "";
                String text = lines.isEmpty() ? "" : lines.get(l);
                sb.append(tableRow(time, cpu, text, timeWidth, cpuWidth, detailWidth)).append('\n');
            }

            boolean last = (i == rows - 1);
            boolean nextKeepsCpuCell = !last && !newCpuRun[i + 1];
            sb.append(nextKeepsCpuCell ? open : closed).append('\n');
        }
        return sb.toString();
    }

    private static String cpuLabel(Snapshot s) {
        switch (s.getCpuState()) {
            case RUNNING:
                return s.getRunningProcess();
            case CONTEXT_SWITCH:
                return "CS";
            default:
                return "IDLE";
        }
    }

    /**
     * "Tier" for ACS (queues named "Tier 1", "Tier 2"), "Queue" for SJF ("Ready").
     */
    private static String tierHeader(Snapshot first) {
        if (first.getQueues().isEmpty()) {
            return "Queues";
        }
        return first.getQueues().get(0).getQueueName().startsWith("Tier") ? "Tier" : "Queue";
    }

    /** Event text first (if any), then one entry per queue. */
    private static List<String> detailLines(Snapshot s) {
        List<String> lines = new ArrayList<>();
        if (!s.getEvent().isEmpty()) {
            lines.add(s.getEvent());
        }
        for (Snapshot.QueueView q : s.getQueues()) {
            addQueueLines(lines, q);
        }
        return lines;
    }

    private static void addQueueLines(List<String> lines, Snapshot.QueueView q) {
        String oneLine = q.toString();
        List<Snapshot.QueueEntry> entries = q.getEntries();
        if (oneLine.length() <= MAX_QUEUE_LINE || entries.size() <= 1) {
            lines.add(oneLine);
            return;
        }
        String prefix = q.getQueueName() + ": [";
        String indent = " ".repeat(prefix.length());
        for (int i = 0; i < entries.size(); i++) {
            String begin = (i == 0) ? prefix : indent;
            String end = (i == entries.size() - 1) ? "]" : ",";
            lines.add(begin + entries.get(i) + end);
        }
    }

    /**
     * Horizontal line. When cpuCellClosed is false the Process cell stays open
     * (merged).
     */
    private static String rule(int timeWidth, int cpuWidth, int detailWidth, boolean cpuCellClosed) {
        return "+" + "-".repeat(timeWidth + 2)
                + "+" + (cpuCellClosed ? "-" : " ").repeat(cpuWidth + 2)
                + "+" + "-".repeat(detailWidth + 2) + "+";
    }

    private static String tableRow(String time, String cpu, String detail,
            int timeWidth, int cpuWidth, int detailWidth) {
        return "| " + padRight(time, timeWidth)
                + " | " + padRight(cpu, cpuWidth)
                + " | " + padRight(detail, detailWidth) + " |";
    }

    // =====================================================================
    // Process analysis table
    // =====================================================================

    public static String formatResultsTable(SimulationResult result) {
        String[] headers = { "Process", "Arrival", "Burst", "Finish", "TAT", "WT", "RT", "Demoted" };
        List<String[]> rows = new ArrayList<>();
        for (ProcessResult r : result.getProcessResults()) {
            rows.add(new String[] {
                    r.getName(),
                    String.valueOf(r.getArrivalTime()),
                    String.valueOf(r.getBurstTime()),
                    String.valueOf(r.getFinishTime()),
                    String.valueOf(r.getTurnaroundTime()),
                    String.valueOf(r.getWaitingTime()),
                    String.valueOf(r.getResponseTime()),
                    r.wasDemoted() ? "Yes" : "No" });
        }
        return boxTable(headers, rows);
    }

    /** Simple left-aligned table with borders. */
    private static String boxTable(String[] headers, List<String[]> rows) {
        int[] widths = new int[headers.length];
        for (int i = 0; i < headers.length; i++) {
            widths[i] = headers[i].length();
        }
        for (String[] row : rows) {
            for (int i = 0; i < row.length; i++) {
                widths[i] = Math.max(widths[i], row[i].length());
            }
        }

        StringBuilder rule = new StringBuilder("+");
        for (int w : widths) {
            rule.append("-".repeat(w + 2)).append('+');
        }

        StringBuilder sb = new StringBuilder();
        sb.append(rule).append('\n');
        sb.append(boxRow(headers, widths)).append('\n');
        sb.append(rule).append('\n');
        for (String[] row : rows) {
            sb.append(boxRow(row, widths)).append('\n');
        }
        sb.append(rule).append('\n');
        return sb.toString();
    }

    private static String boxRow(String[] cells, int[] widths) {
        StringBuilder sb = new StringBuilder("|");
        for (int i = 0; i < cells.length; i++) {
            sb.append(' ').append(padRight(cells[i], widths[i])).append(" |");
        }
        return sb.toString();
    }

    // =====================================================================
    // Summary
    // =====================================================================

    /**
     * Averages, then total / busy / context switch / idle time, utilization,
     * demoted count.
     */
    public static String formatSummary(SimulationResult result) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format(Locale.ROOT, "Average Waiting Time    : %.2f\n", result.getAverageWaitingTime()));
        sb.append(String.format(Locale.ROOT, "Average Turnaround Time : %.2f\n", result.getAverageTurnaroundTime()));
        sb.append(String.format(Locale.ROOT, "Average Response Time   : %.2f\n", result.getAverageResponseTime()));
        sb.append('\n');
        sb.append(String.format(Locale.ROOT, "Total Time          : %d\n", result.getTotalTime()));
        sb.append(String.format(Locale.ROOT, "CPU Busy Time       : %d\n", result.getBusyTime()));
        sb.append(String.format(Locale.ROOT, "Context Switch Time : %d\n", result.getContextSwitchTime()));
        sb.append(String.format(Locale.ROOT, "CPU Idle Time       : %d\n", result.getIdleTime()));
        sb.append(String.format(Locale.ROOT, "CPU Utilization     : %.2f%%\n", result.getCpuUtilization() * 100));
        sb.append(String.format(Locale.ROOT, "Demoted Processes   : %d\n", result.getDemotedCount()));
        return sb.toString();
    }

    // =====================================================================
    // Everything together
    // =====================================================================

    /** Header + matrix + analysis table + summary, as one String. */
    public static String formatAll(SimulationResult result) {
        StringBuilder sb = new StringBuilder();
        sb.append(formatHeader(result)).append('\n');

        sb.append(banner("SCHEDULING MATRIX"));
        sb.append(formatMatrix(result)).append('\n');

        sb.append(banner("PROCESS SCHEDULING ANALYSIS"));
        sb.append(formatResultsTable(result)).append('\n');

        sb.append(banner("SIMULATION SUMMARY"));
        sb.append(formatSummary(result));
        sb.append("=".repeat(WIDTH)).append('\n');
        return sb.toString();
    }

    public static void printMatrix(SimulationResult result, PrintStream out) {
        out.print(formatMatrix(result));
    }

    public static void printResultsTable(SimulationResult result, PrintStream out) {
        out.print(formatResultsTable(result));
    }

    public static void printSummary(SimulationResult result, PrintStream out) {
        out.print(formatSummary(result));
    }

    public static void printAll(SimulationResult result, PrintStream out) {
        out.print(formatAll(result));
    }

    public static void printAll(SimulationResult result) {
        printAll(result, System.out);
    }

    // =====================================================================
    // Helpers
    // =====================================================================

    /** Centered title between two lines of '='. */
    private static String banner(String title) {
        String rule = "=".repeat(WIDTH);
        int pad = Math.max(0, (WIDTH - title.length()) / 2);
        return rule + "\n" + " ".repeat(pad) + title + "\n" + rule + "\n";
    }

    private static String padRight(String text, int width) {
        if (text.length() >= width) {
            return text;
        }
        return text + " ".repeat(width - text.length());
    }
}