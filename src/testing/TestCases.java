
package testing;

import java.util.ArrayList;
import java.util.List;

import model.Process;
import model.SimulationConfig;

public final class TestCases
{
    private TestCases()
    {
    }

    public static class TestCase
    {
        private final String name;
        private final List<Process> processes;
        private final SimulationConfig config;

        public TestCase(String name, List<Process> processes, SimulationConfig config)
        {
            this.name = name;

            this.processes = new ArrayList<>();

            for (Process process : processes)
            {
                this.processes.add(process.copy());
            }

            this.config = config;
        }

        public String getName()
        {
            return name;
        }

        public List<Process> getProcesses()
        {
            List<Process> copies = new ArrayList<>();

            for (Process process : processes)
            {
                copies.add(process.copy());
            }

            return copies;
        }

        public SimulationConfig getConfig()
        {
            return config;
        }
    }

    public static List<TestCase> getAll()
    {
        List<TestCase> testCases = new ArrayList<>();

        // Test Case 1: Assignment PDF
        testCases.add(new TestCase(
                "Test Case 1 - PDF",
                List.of(
                        new Process("P1", 0, 4, 2),
                        new Process("P2", 1, 7, 3),
                        new Process("P3", 3, 2, 1)
                ),
                new SimulationConfig(3, 0)
        ));

        // Test Case 2: Assignment PDF
        testCases.add(new TestCase(
                "Test Case 2 - PDF",
                List.of(
                        new Process("P1", 0, 5, 4),
                        new Process("P2", 1, 6, 2),
                        new Process("P3", 4, 4, 3)
                ),
                new SimulationConfig(3, 1)
        ));

        // Test Case 3: All processes arrive at the same time
        testCases.add(new TestCase(
                "Test Case 3 - Same Arrival Time",
                List.of(
                        new Process("P1", 0, 8, 3),
                        new Process("P2", 0, 4, 1),
                        new Process("P3", 0, 6, 2)
                ),
                new SimulationConfig(2, 1)
        ));

        // Test Case 4: Processes arrive at different times with an idle period
        testCases.add(new TestCase(
                "Test Case 4 - Idle CPU",
                List.of(
                        new Process("P1", 0, 3, 2),
                        new Process("P2", 8, 5, 1),
                        new Process("P3", 15, 2, 3)
                ),
                new SimulationConfig(2, 2)
        ));

        // Test Case 5: One process
        testCases.add(new TestCase(
                "Test Case 5 - Single Process",
                List.of(
                        new Process("P1", 0, 10, 5)
                ),
                new SimulationConfig(3, 1)
        ));

        return testCases;
    }

    public static TestCase getByIndex(int index)
    {
        List<TestCase> testCases = getAll();

        if (index < 0 || index >= testCases.size())
        {
            throw new IndexOutOfBoundsException("Invalid test case index: " + index);
        }

        return testCases.get(index);
    }
}
