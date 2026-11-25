package output;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

import experiment.ExperimentRunner.ExperimentRow;
import model.ProcessResult;
import model.SimulationConfig;
import model.SimulationResult;
import model.Snapshot;

/**
 * Writes simulation data to CSV files that open in Excel / Google Sheets /
 * pandas.
 *
 * Three exports:
 * exportResults - one row per process (the results table)
 * exportTimeline - one row per tick (the un-merged time-step matrix)
 * exportExperiment - one row per (test case, algorithm, Q, CS) run, for the
 * report graphs
 *
 * Each export has a format*Csv method that returns the text (easy to test, no
 * files
 * needed) and an export* method that writes it to disk, creating missing
 * folders.
 *
 * Rules followed:
 * - Fields containing a comma, quote or line break are wrapped in quotes and
 * internal quotes are doubled (queue strings like "P1 (remaining: 4, Priority:
 * 2)"
 * contain commas).
 * - Numbers use Locale.ROOT, so decimals are always "4.33", never "4,33".
 * - Files are UTF-8, lines end with "\n".
 */
public final class CsvExporter {

    private CsvExporter() {
    }

    // =====================================================================
    // 1. Per-process results
    // =====================================================================

    /**
     * Columns: algorithm, quantum, context_switch, aging_interval, process,
     * arrival,
     * burst, finish, turnaround, waiting, response, demoted
     */
    public static String formatResultsCsv(SimulationResult result) {
        SimulationConfig c = result.getConfig();
        StringBuilder sb = new StringBuilder();
        sb.append(line("algorithm", "quantum", "context_switch", "aging_interval", "process",
                "arrival", "burst", "finish", "turnaround", "waiting", "response", "demoted"));

        for (ProcessResult r : result.getProcessResults()) {
            sb.append(line(
                    result.getAlgorithmName(),
                    c.getQuantum(),
                    c.getContextSwitch(),
                    c.getAgingInterval(),
                    r.getName(),
                    r.getArrivalTime(),
                    r.getBurstTime(),
                    r.getFinishTime(),
                    r.getTurnaroundTime(),
                    r.getWaitingTime(),
                    r.getResponseTime(),
                    r.wasDemoted()));
        }
        return sb.toString();
    }

    public static void exportResults(SimulationResult result, Path path) throws IOException {
        write(path, formatResultsCsv(result));
    }

    // =====================================================================
    // 2. Timeline (one row per tick, not merged)
    // =====================================================================

    /**
     * Columns: time, cpu_state, running_process, event, queues
     * The "queues" column holds every queue as text, separated by " | ",
     * e.g. "Tier 1: [Empty] | Tier 2: [P1 (remaining: 1, Priority: 2)]".
     */
    public static String formatTimelineCsv(SimulationResult result) {
        StringBuilder sb = new StringBuilder();
        sb.append(line("time", "cpu_state", "running_process", "event", "queues"));

        for (Snapshot s : result.getSnapshots()) {
            StringBuilder queues = new StringBuilder();
            for (Snapshot.QueueView q : s.getQueues()) {
                if (queues.length() > 0) {
                    queues.append(" | ");
                }
                queues.append(q);
            }
            sb.append(line(
                    s.getTime(),
                    s.getCpuState().name(),
                    s.getRunningProcess(), // null (idle / switching) becomes an empty field
                    s.getEvent(),
                    queues.toString()));
        }
        return sb.toString();
    }

    public static void exportTimeline(SimulationResult result, Path path) throws IOException {
        write(path, formatTimelineCsv(result));
    }

    // =====================================================================
    // 3. Experiment sweep (data for the report graphs)
    // =====================================================================

    /**
     * Columns: test_case, algorithm, quantum, context_switch, aging_interval,
     * avg_waiting,
     * avg_turnaround, avg_response, cpu_utilization_percent, context_switch_time,
     * demoted
     *
     * Easy chart recipe: filter by test_case and algorithm, put quantum on the
     * x-axis,
     * one line per context_switch, y = avg_waiting (then again with
     * avg_turnaround).
     */
    public static String formatExperimentCsv(List<ExperimentRow> rows) {
        StringBuilder sb = new StringBuilder();
        sb.append(line("test_case", "algorithm", "quantum", "context_switch", "aging_interval",
                "avg_waiting", "avg_turnaround", "avg_response",
                "cpu_utilization_percent", "context_switch_time", "demoted"));

        for (ExperimentRow r : rows) {
            sb.append(line(
                    r.getTestCase(),
                    r.getAlgorithm(),
                    r.getQuantum(),
                    r.getContextSwitch(),
                    r.getAgingInterval(),
                    decimal(r.getAvgWaitingTime(), 2),
                    decimal(r.getAvgTurnaroundTime(), 2),
                    decimal(r.getAvgResponseTime(), 2),
                    decimal(r.getCpuUtilization() * 100, 1),
                    r.getContextSwitchTime(),
                    r.getDemotedCount()));
        }
        return sb.toString();
    }

    public static void exportExperiment(List<ExperimentRow> rows, Path path) throws IOException {
        write(path, formatExperimentCsv(rows));
    }

    // =====================================================================
    // Helpers
    // =====================================================================

    /**
     * Quotes a field if needed (comma, quote, CR or LF) and doubles internal
     * quotes.
     * null becomes an empty field.
     */
    public static String escape(String field) {
        if (field == null) {
            return "";
        }
        boolean needsQuotes = field.indexOf(',') >= 0
                || field.indexOf('"') >= 0
                || field.indexOf('\n') >= 0
                || field.indexOf('\r') >= 0;
        if (!needsQuotes) {
            return field;
        }
        return "\"" + field.replace("\"", "\"\"") + "\"";
    }

    /**
     * Turns a label such as "Test Case 1 - PDF" into something safe for a file
     * name:
     * "Test_Case_1_-_PDF". Anything other than letters, digits, '-' and '_' becomes
     * '_'.
     */
    public static String fileSafe(String name) {
        if (name == null || name.isBlank()) {
            return "unnamed";
        }
        return name.trim().replaceAll("[^A-Za-z0-9_-]+", "_");
    }

    /** Joins the fields with commas (escaped) and ends the line. */
    private static String line(Object... fields) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < fields.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(escape(fields[i] == null ? null : String.valueOf(fields[i])));
        }
        return sb.append('\n').toString();
    }

    private static String decimal(double value, int digits) {
        return String.format(Locale.ROOT, "%." + digits + "f", value);
    }

    /** Creates the parent folder if needed, then writes the text as UTF-8. */
    private static void write(Path path, String content) throws IOException {
        Path parent = path.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.writeString(path, content, StandardCharsets.UTF_8);
    }
}