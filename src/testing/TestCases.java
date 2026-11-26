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
                "Test Case 1",
                List.of(
                        new Process("P1", 0, 4, 2),
                        new Process("P2", 1, 7, 3),
                        new Process("P3", 3, 2, 1)
                ),
                new SimulationConfig(3, 0)
        ));

        // Test Case 2: Assignment PDF
        testCases.add(new TestCase(
                "Test Case 2",
                List.of(
                        new Process("P1", 0, 5, 4),
                        new Process("P2", 1, 6, 2),
                        new Process("P3", 4, 4, 3)
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