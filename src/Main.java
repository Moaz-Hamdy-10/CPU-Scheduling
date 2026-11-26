import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

import experiment.ExperimentRunner.ExperimentRow;
import experiment.ExperimentRunner;
import model.Process;
import model.SimulationConfig;
import model.SimulationResult;
import output.CsvExporter;
import output.MatrixPrinter;
import scheduler.SJFScheduler;
import scheduler.Scheduler;
import scheduler.acs.ACSScheduler;
import testing.TestCases;

/**
 * Entry point of the simulator.
 *
 * java Main -> interactive menu
 * java Main --all -> run everything without asking (test cases, CSVs,
 * experiments)
 *
 * Main contains no scheduling logic. It only connects the pieces:
 * TestCases -> Scheduler.run() -> MatrixPrinter (console) + CsvExporter (files)
 * ExperimentRunner -> CsvExporter (sweep file for the report graphs)
 */
public class Main {

    /** All generated files go here (created automatically). */
    private static final Path OUTPUT_DIR = Path.of("results");

    private static final List<Scheduler> SCHEDULERS = List.of(new SJFScheduler(), new ACSScheduler());

    /** Upper bound accepted for typed times and priorities (just a sanity limit). */
    private static final int MAX_INPUT = 10_000;

    /** Width of the banners printed by the menu. */
    private static final int WIDTH = 50;

    public static void main(String[] args) {
        if (args.length > 0 && args[0].equals("--all")) {
            runAllTestCases();
            runExperiments();
            return;
        }
        menu();
    }

    // =====================================================================
    // Menu
    // =====================================================================

    private static void menu() {
        Scanner in = new Scanner(System.in);

        while (true) {
            System.out.println();
            banner("CPU SCHEDULING SIMULATOR");
            System.out.println(" 1. Run all test cases (SJF + ACS)");
            System.out.println(" 2. Run one test case");
            System.out.println(" 3. Run Q / CS experiments");
            System.out.println(" 4. Run a custom test case");
            System.out.println(" 0. Exit");
            System.out.println("=".repeat(WIDTH));

            int choice = readInt(in, "Choose an option: ", 0, 4);

            switch (choice) {
                case 1:
                    runAllTestCases();
                    break;
                case 2:
                    runOne(in);
                    break;
                case 3:
                    runExperiments();
                    break;
                case 4:
                    runCustom(in);
                    break;
                default: // 0 or end of input
                    System.out.println("Bye.");
                    return;
            }
            pause(in);
        }
    }

    private static void runOne(Scanner in) {
        List<TestCases.TestCase> all = TestCases.getAll();

        System.out.println();
        System.out.println(" Select a test case:");
        for (int i = 0; i < all.size(); i++) {
            System.out.println("   " + (i + 1) + ". " + all.get(i).getName());
        }
        int t = readInt(in, " Test case: ", 1, all.size());
        if (t < 0) {
            return;
        }

        System.out.println();
        System.out.println(" Select an algorithm:");
        for (int i = 0; i < SCHEDULERS.size(); i++) {
            System.out.println("   " + (i + 1) + ". " + SCHEDULERS.get(i).getName());
        }
        int s = readInt(in, " Algorithm: ", 1, SCHEDULERS.size());
        if (s < 0) {
            return;
        }

        runTestCase(all.get(t - 1), SCHEDULERS.get(s - 1));
    }

    // =====================================================================
    // Custom test case (typed by the user)
    // =====================================================================

    /**
     * Reads the processes and the parameters from the keyboard, then runs the chosen
     * algorithm(s) exactly like a built-in test case (matrix, analysis table, CSV files).
     * Processes are named P1, P2, ... in the order they are typed.
     * Typing nothing for the aging interval keeps the default.
     */
    private static void runCustom(Scanner in) {
        System.out.println();
        banner("CUSTOM TEST CASE");

        int n = readInt(in, " Number of processes (1-20): ", 1, 20);
        if (n < 0) {
            return;
        }

        List<Process> processes = new ArrayList<>();
        for (int i = 1; i <= n; i++) {
            System.out.println(" Process P" + i + ":");
            int arrival = readInt(in, "   Arrival time (>= 0): ", 0, MAX_INPUT);
            if (arrival < 0) {
                return;
            }
            int burst = readInt(in, "   Burst time (>= 1): ", 1, MAX_INPUT);
            if (burst < 0) {
                return;
            }
            int priority = readInt(in, "   Initial priority (>= 1, lower number = higher priority): ",
                    1, MAX_INPUT);
            if (priority < 0) {
                return;
            }
            processes.add(new Process("P" + i, arrival, burst, priority));
        }

        System.out.println();
        int quantum = readInt(in, " Time quantum Q (>= 1): ", 1, MAX_INPUT);
        if (quantum < 0) {
            return;
        }
        int contextSwitch = readInt(in, " Context switch time CS (>= 0): ", 0, MAX_INPUT);
        if (contextSwitch < 0) {
            return;
        }
        int aging = readIntOrDefault(in,
                " Aging interval (Enter = " + SimulationConfig.DEFAULT_AGING_INTERVAL + "): ",
                1, MAX_INPUT, SimulationConfig.DEFAULT_AGING_INTERVAL);
        if (aging < 0) {
            return;
        }

        System.out.println();
        System.out.println(" Select an algorithm:");
        for (int i = 0; i < SCHEDULERS.size(); i++) {
            System.out.println("   " + (i + 1) + ". " + SCHEDULERS.get(i).getName());
        }
        System.out.println("   " + (SCHEDULERS.size() + 1) + ". Both");
        int choice = readInt(in, " Algorithm: ", 1, SCHEDULERS.size() + 1);
        if (choice < 0) {
            return;
        }

        TestCases.TestCase tc = new TestCases.TestCase("Custom Test Case", processes,
                new SimulationConfig(quantum, contextSwitch, aging));

        for (int i = 0; i < SCHEDULERS.size(); i++) {
            if (choice == SCHEDULERS.size() + 1 || choice == i + 1) {
                runTestCase(tc, SCHEDULERS.get(i));
            }
        }
    }

    // =====================================================================
    // Test cases
    // =====================================================================

    private static void runAllTestCases() {
        for (TestCases.TestCase tc : TestCases.getAll()) {
            for (Scheduler scheduler : SCHEDULERS) {
                runTestCase(tc, scheduler);
            }
        }
        System.out.println("\nCSV files written to: " + OUTPUT_DIR.toAbsolutePath());
    }

    /**
     * Runs one test case with one scheduler, prints everything and exports the
     * CSVs.
     */
    private static void runTestCase(TestCases.TestCase tc, Scheduler scheduler) {
        System.out.println();
        System.out.println("##### " + tc.getName() + " #####");

        // getProcesses() returns fresh copies, so runs never affect each other.
        SimulationResult result = scheduler.run(tc.getProcesses(), tc.getConfig());
        MatrixPrinter.printAll(result);

        String base = CsvExporter.fileSafe(tc.getName()) + "_" + tag(scheduler);
        try {
            CsvExporter.exportResults(result, OUTPUT_DIR.resolve(base + "_results.csv"));
            CsvExporter.exportTimeline(result, OUTPUT_DIR.resolve(base + "_timeline.csv"));
        } catch (IOException e) {
            System.out.println("Could not write CSV files: " + e.getMessage());
        }
    }

    private static String tag(Scheduler scheduler) {
        return (scheduler instanceof ACSScheduler) ? "ACS" : "SJF";
    }

    // =====================================================================
    // Experiments (Q and CS sweep for the report graphs)
    // =====================================================================

    private static void runExperiments() {
        System.out.println();
        banner("Q / CS EXPERIMENTS");

        List<ExperimentRow> rows = ExperimentRunner.runAll();
        ExperimentRunner.printSummary(rows);

        Path file = OUTPUT_DIR.resolve("experiments.csv");
        try {
            CsvExporter.exportExperiment(rows, file);
            System.out.println("Experiment data written to: " + file.toAbsolutePath());
        } catch (IOException e) {
            System.out.println("Could not write experiments.csv: " + e.getMessage());
        }
    }

    // =====================================================================
    // Input helper
    // =====================================================================

    /**
     * Centered title between two lines of '=' (same look as the printed reports).
     */
    private static void banner(String title) {
        String line = "=".repeat(WIDTH);
        int pad = Math.max(0, (WIDTH - title.length()) / 2);
        System.out.println(line);
        System.out.println(" ".repeat(pad) + title);
        System.out.println(line);
    }

    /** Waits for Enter so the output is not pushed away by the next menu. */
    private static void pause(Scanner in) {
        System.out.print("\nPress Enter to return to the menu...");
        if (in.hasNextLine()) {
            in.nextLine();
        }
    }

    /**
     * Like readInt, but an empty line returns the default value. Returns -1 at end of input.
     */
    private static int readIntOrDefault(Scanner in, String prompt, int min, int max, int defaultValue) {
        while (true) {
            System.out.print(prompt);
            if (!in.hasNextLine()) {
                return -1;
            }
            String text = in.nextLine().trim();
            if (text.isEmpty()) {
                return defaultValue;
            }
            try {
                int value = Integer.parseInt(text);
                if (value >= min && value <= max) {
                    return value;
                }
            } catch (NumberFormatException ignored) {
                // fall through to the message below
            }
            System.out.println("Please enter a number from " + min + " to " + max
                    + " (or press Enter for " + defaultValue + ").");
        }
    }

    /**
     * Asks until the user types a number in [min, max]. Returns -1 at end of input.
     */
    private static int readInt(Scanner in, String prompt, int min, int max) {
        while (true) {
            System.out.print(prompt);
            if (!in.hasNextLine()) {
                return -1;
            }
            String text = in.nextLine().trim();
            try {
                int value = Integer.parseInt(text);
                if (value >= min && value <= max) {
                    return value;
                }
            } catch (NumberFormatException ignored) {
                // fall through to the message below
            }
            System.out.println("Please enter a number from " + min + " to " + max + ".");
        }
    }
}