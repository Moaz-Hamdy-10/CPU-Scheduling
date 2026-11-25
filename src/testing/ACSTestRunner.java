
package testing;

import model.*;
import scheduler.acs.ACSScheduler;

public class ACSTestRunner {

    public static void main(String[] args) {

        ACSScheduler acs = new ACSScheduler();

        for (TestCases.TestCase tc : TestCases.getAll()) {

            System.out.println("\n================================");
            System.out.println(tc.getName());
            System.out.println("================================");

            SimulationResult result =
                    acs.run(tc.getProcesses(), tc.getConfig());

            // Print average metrics
            System.out.printf("Average Waiting Time: %.2f%n",
                    result.getAverageWaitingTime());

            System.out.printf("Average Turnaround Time: %.2f%n",
                    result.getAverageTurnaroundTime());

            System.out.printf("Average Response Time: %.2f%n",
                    result.getAverageResponseTime());

            // Print each process result
            System.out.println("\nProcess Results:");

            for (ProcessResult p : result.getProcessResults()) {
                System.out.println(p);
            }

            // Print scheduling timeline
            System.out.println("\nScheduling Timeline:");

            for (Snapshot s : result.getSnapshots()) {
                System.out.println(s);
            }
        }
    }
}

