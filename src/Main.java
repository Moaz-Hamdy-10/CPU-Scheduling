import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Scanner;

import experiment.ExperimentRunner.ExperimentRow;
import experiment.ExperimentRunner;
import model.ProcessResult;
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
 * experiments, self-check)
 *
 * Main contains no scheduling logic. It only connects the pieces:
 * TestCases -> Scheduler.run() -> MatrixPrinter (console) + CsvExporter (files)
 * ExperimentRunner -> CsvExporter (sweep file for the report graphs)
 */
public class Main {

    /** All generated files go here (created automatically). */
    private static final Path OUTPUT_DIR = Path.of("results");

    private static final List<Scheduler> SCHEDULERS = List.of(new SJFScheduler(), new ACSScheduler());

    /** Width of the banners printed by the menu. */
    private static final int WIDTH = 50;

    public static void main(String[] args) {
        if (args.length > 0 && args[0].equals("--all")) {
            runAllTestCases();
            runExperiments();
            selfCheck();
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
            System.out.println(" 4. Self-check against the assignment PDF");
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
                    selfCheck();
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
    // Self-check against the two PDF test cases
    // =====================================================================

    /**
     * Compares the ACS results for Test Cases 1 and 2 with the numbers printed in
     * the
     * assignment PDF (finish times and averages). Evidence for the test-case grade.
     */
    private static boolean selfCheck() {
        System.out.println();
        banner("SELF-CHECK AGAINST ASSIGNMENT PDF (ACS)");
        ACSScheduler acs = new ACSScheduler();
        boolean ok = true;

        TestCases.TestCase tc1 = TestCases.getByIndex(0);
        SimulationResult r1 = acs.run(tc1.getProcesses(), tc1.getConfig());
        ok &= checkFinish("TC1", r1, "P1", 9);
        ok &= checkFinish("TC1", r1, "P2", 13);
        ok &= checkFinish("TC1", r1, "P3", 8);
        ok &= checkAverage("TC1 average turnaround", r1.getAverageTurnaroundTime(), 26.0 / 3);
        ok &= checkAverage("TC1 average waiting", r1.getAverageWaitingTime(), 13.0 / 3);

        TestCases.TestCase tc2 = TestCases.getByIndex(1);
        SimulationResult r2 = acs.run(tc2.getProcesses(), tc2.getConfig());
        ok &= checkFinish("TC2", r2, "P1", 18);
        ok &= checkFinish("TC2", r2, "P2", 15);
        ok &= checkFinish("TC2", r2, "P3", 20);
        ok &= checkAverage("TC2 average turnaround", r2.getAverageTurnaroundTime(), 16.00);
        ok &= checkAverage("TC2 average waiting", r2.getAverageWaitingTime(), 11.00);

        System.out.println(ok ? "ALL CHECKS PASSED" : "SOME CHECKS FAILED");
        return ok;
    }

    private static boolean checkFinish(String tcLabel, SimulationResult r, String process, int expected) {
        int actual = r.findResult(process).map(ProcessResult::getFinishTime).orElse(-1);
        boolean pass = (actual == expected);
        System.out.printf(Locale.ROOT, "%-26s expected %5d  got %5d  %s%n",
                tcLabel + " finish " + process, expected, actual, pass ? "PASS" : "FAIL");
        return pass;
    }

    private static boolean checkAverage(String label, double actual, double expected) {
        boolean pass = Math.abs(actual - expected) < 0.005;
        System.out.printf(Locale.ROOT, "%-26s expected %5.2f  got %5.2f  %s%n",
                label, expected, actual, pass ? "PASS" : "FAIL");
        return pass;
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