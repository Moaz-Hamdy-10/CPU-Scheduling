import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JSpinner;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.table.DefaultTableModel;

import model.Process;
import model.ProcessResult;
import model.SimulationConfig;
import model.SimulationResult;
import model.Snapshot;
import output.MatrixPrinter;
import scheduler.SJFScheduler;
import scheduler.Scheduler;
import scheduler.acs.ACSScheduler;
import testing.TestCases;

/**
 * Bonus: Swing GUI for the simulator. It only uses the existing classes
 * (TestCases, Scheduler, MatrixPrinter); there is no scheduling logic here.
 *
 * Run with: java -cp out gui.SchedulerGUI
 *
 * Tabs:
 * Gantt Chart - colored timeline (context switches in gray, idle in white)
 * Step Viewer - slider over time: CPU state + Tier 1 / Tier 2 content at every
 * tick
 * Matrix - the same text matrix the console prints (PDF style)
 * Analysis - per-process WT / TAT / RT table and the summary (averages,
 * utilization)
 */
public class SchedulerGUI extends JFrame {

    private final List<TestCases.TestCase> presets = TestCases.getAll();
    private final List<Scheduler> schedulers = List.of(new SJFScheduler(), new ACSScheduler());

    private final JComboBox<String> presetBox = new JComboBox<>();
    private final JComboBox<String> algoBox = new JComboBox<>();
    private final JSpinner quantumSpin = new JSpinner(new SpinnerNumberModel(3, 1, 1000, 1));
    private final JSpinner csSpin = new JSpinner(new SpinnerNumberModel(0, 0, 1000, 1));
    private final JSpinner agingSpin = new JSpinner(
            new SpinnerNumberModel(SimulationConfig.DEFAULT_AGING_INTERVAL, 1, 1000, 1));

    private final DefaultTableModel inputModel = new DefaultTableModel(
            new Object[] { "Process", "Arrival", "Burst", "Priority" }, 0);
    private final JTable inputTable = new JTable(inputModel);

    private final GanttPanel gantt = new GanttPanel();
    private final JSlider stepSlider = new JSlider(0, 0, 0);
    private final JLabel stepLabel = new JLabel("t = 0");
    private final JTextArea stepText = monoArea();
    private final JTextArea matrixText = monoArea();
    private final DefaultTableModel resultModel = new DefaultTableModel(
            new Object[] { "Process", "Arrival", "Burst", "Finish", "TAT", "WT", "RT", "Demoted" }, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private final JTextArea summaryText = monoArea();
    private final JTabbedPane tabs = new JTabbedPane();

    private SimulationResult current;

    public SchedulerGUI() {
        super("CPU Scheduling Simulator - SJF & ACS");
        setDefaultCloseOperation(EXIT_ON_CLOSE);

        for (TestCases.TestCase tc : presets) {
            presetBox.addItem(tc.getName());
        }
        presetBox.addItem("Custom (empty)");
        for (Scheduler s : schedulers) {
            algoBox.addItem(s.getName());
        }
        algoBox.setSelectedIndex(1); // ACS

        presetBox.addActionListener(e -> loadPreset());

        JButton run = new JButton("Run simulation");
        run.addActionListener(e -> runSimulation());

        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 6));
        top.add(new JLabel("Preset:"));
        top.add(presetBox);
        top.add(new JLabel("Algorithm:"));
        top.add(algoBox);
        top.add(new JLabel("Quantum (Q):"));
        top.add(quantumSpin);
        top.add(new JLabel("Context switch (CS):"));
        top.add(csSpin);
        top.add(new JLabel("Aging interval:"));
        top.add(agingSpin);
        top.add(run);

        // Left: editable process table
        JButton add = new JButton("Add process");
        add.addActionListener(e -> inputModel.addRow(
                new Object[] { "P" + (inputModel.getRowCount() + 1), 0, 1, 1 }));
        JButton remove = new JButton("Remove selected");
        remove.addActionListener(e -> {
            if (inputTable.isEditing()) {
                inputTable.getCellEditor().stopCellEditing();
            }
            int row = inputTable.getSelectedRow();
            if (row >= 0) {
                inputModel.removeRow(row);
            }
        });
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        buttons.add(add);
        buttons.add(remove);

        JPanel left = new JPanel(new BorderLayout());
        left.setBorder(
                BorderFactory.createTitledBorder("Processes (editable; lower priority number = higher priority)"));
        left.add(new JScrollPane(inputTable), BorderLayout.CENTER);
        left.add(buttons, BorderLayout.SOUTH);
        left.setPreferredSize(new Dimension(330, 400));

        // Right: tabs
        tabs.addTab("Gantt Chart", new JScrollPane(gantt));
        tabs.addTab("Step Viewer", buildStepViewer());
        tabs.addTab("Matrix", new JScrollPane(matrixText));
        tabs.addTab("Analysis", buildAnalysisTab());

        stepSlider.addChangeListener(e -> updateStep());

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, left, tabs);
        split.setResizeWeight(0.0);

        getContentPane().add(top, BorderLayout.NORTH);
        getContentPane().add(split, BorderLayout.CENTER);

        loadPreset();
        runSimulation();

        setSize(1200, 700);
        setLocationRelativeTo(null);
    }

    // =====================================================================
    // Tabs
    // =====================================================================

    private JPanel buildStepViewer() {
        JPanel panel = new JPanel(new BorderLayout(6, 6));
        panel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JPanel sliderRow = new JPanel(new BorderLayout(8, 0));
        sliderRow.add(new JLabel("Time step:"), BorderLayout.WEST);
        sliderRow.add(stepSlider, BorderLayout.CENTER);
        sliderRow.add(stepLabel, BorderLayout.EAST);

        JLabel note = new JLabel(
                "Queues are shown after arrivals/demotions at time t and before the next process is dispatched.");
        note.setFont(note.getFont().deriveFont(Font.ITALIC));

        JPanel north = new JPanel(new BorderLayout(0, 4));
        north.add(sliderRow, BorderLayout.NORTH);
        north.add(note, BorderLayout.SOUTH);

        panel.add(north, BorderLayout.NORTH);
        panel.add(new JScrollPane(stepText), BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildAnalysisTab() {
        JTable table = new JTable(resultModel);
        JScrollPane tableScroll = new JScrollPane(table);
        tableScroll.setPreferredSize(new Dimension(600, 150));

        JPanel panel = new JPanel(new BorderLayout(6, 6));
        panel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        panel.add(tableScroll, BorderLayout.NORTH);
        panel.add(new JScrollPane(summaryText), BorderLayout.CENTER);
        return panel;
    }

    private static JTextArea monoArea() {
        JTextArea area = new JTextArea();
        area.setEditable(false);
        area.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        return area;
    }

    // =====================================================================
    // Actions
    // =====================================================================

    private void loadPreset() {
        inputModel.setRowCount(0);
        int index = presetBox.getSelectedIndex();
        if (index < 0 || index >= presets.size()) {
            return; // Custom: keep the table empty for the user to fill
        }
        TestCases.TestCase tc = presets.get(index);
        for (Process p : tc.getProcesses()) {
            inputModel.addRow(new Object[] {
                    p.getName(), p.getArrivalTime(), p.getBurstTime(), p.getInitialPriority() });
        }
        SimulationConfig c = tc.getConfig();
        quantumSpin.setValue(c.getQuantum());
        csSpin.setValue(c.getContextSwitch());
        agingSpin.setValue(c.getAgingInterval());
    }

    private void runSimulation() {
        if (inputTable.isEditing()) {
            inputTable.getCellEditor().stopCellEditing();
        }
        try {
            List<Process> processes = readProcesses();
            SimulationConfig config = new SimulationConfig(
                    (Integer) quantumSpin.getValue(),
                    (Integer) csSpin.getValue(),
                    (Integer) agingSpin.getValue());
            Scheduler scheduler = schedulers.get(algoBox.getSelectedIndex());
            show(scheduler.run(processes, config));
        } catch (IllegalArgumentException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Invalid input", JOptionPane.ERROR_MESSAGE);
        }
    }

    private List<Process> readProcesses() {
        if (inputModel.getRowCount() == 0) {
            throw new IllegalArgumentException("Add at least one process.");
        }
        List<Process> list = new java.util.ArrayList<>();
        Set<String> names = new HashSet<>();
        for (int r = 0; r < inputModel.getRowCount(); r++) {
            String name = String.valueOf(inputModel.getValueAt(r, 0)).trim();
            if (name.isEmpty() || !names.add(name)) {
                throw new IllegalArgumentException("Row " + (r + 1) + ": process names must be unique and not empty.");
            }
            int arrival = parse(r, 1, "Arrival");
            int burst = parse(r, 2, "Burst");
            int priority = parse(r, 3, "Priority");
            if (priority < 1) {
                throw new IllegalArgumentException("Row " + (r + 1) + ": priority must be >= 1.");
            }
            list.add(new Process(name, arrival, burst, priority)); // validates arrival >= 0, burst > 0
        }
        return list;
    }

    private int parse(int row, int col, String label) {
        try {
            return Integer.parseInt(String.valueOf(inputModel.getValueAt(row, col)).trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Row " + (row + 1) + ": " + label + " must be a whole number.");
        }
    }

    // =====================================================================
    // Showing a result
    // =====================================================================

    private void show(SimulationResult result) {
        current = result;

        gantt.setResult(result);

        int n = result.getSnapshots().size();
        stepSlider.setMaximum(Math.max(0, n - 1));
        stepSlider.setValue(0);
        stepSlider.setMajorTickSpacing(Math.max(1, n / 10));
        stepSlider.setPaintTicks(true);
        stepSlider.setPaintLabels(true);
        updateStep();

        matrixText.setText(MatrixPrinter.formatHeader(result) + "\n" + MatrixPrinter.formatMatrix(result));
        matrixText.setCaretPosition(0);

        resultModel.setRowCount(0);
        for (ProcessResult r : result.getProcessResults()) {
            resultModel.addRow(new Object[] { r.getName(), r.getArrivalTime(), r.getBurstTime(),
                    r.getFinishTime(), r.getTurnaroundTime(), r.getWaitingTime(), r.getResponseTime(),
                    r.wasDemoted() ? "Yes" : "No" });
        }
        summaryText.setText(MatrixPrinter.formatSummary(result));
        summaryText.setCaretPosition(0);
    }

    private void updateStep() {
        if (current == null || current.getSnapshots().isEmpty()) {
            return;
        }
        int i = Math.min(stepSlider.getValue(), current.getSnapshots().size() - 1);
        Snapshot s = current.getSnapshots().get(i);
        stepLabel.setText("t = " + s.getTime());
        gantt.setHighlight(i);

        StringBuilder sb = new StringBuilder();
        sb.append("Time : ").append(s.getTime()).append("\n");
        switch (s.getCpuState()) {
            case RUNNING:
                sb.append("CPU  : running ").append(s.getRunningProcess()).append("\n");
                break;
            case CONTEXT_SWITCH:
                sb.append("CPU  : context switch\n");
                break;
            default:
                sb.append("CPU  : idle\n");
        }
        if (!s.getEvent().isEmpty()) {
            sb.append("Event: ").append(s.getEvent()).append("\n");
        }
        sb.append("\n");
        for (Snapshot.QueueView q : s.getQueues()) {
            sb.append(q).append("\n");
        }
        stepText.setText(sb.toString());
    }

    // =====================================================================
    // Gantt chart
    // =====================================================================

    private static class GanttPanel extends JPanel {
        private static final int CELL = 30;
        private static final int LEFT = 20;
        private static final int TOP = 24;
        private static final int BAR_H = 46;
        private static final Color[] PALETTE = {
                new Color(0x8ecae6), new Color(0xffb703), new Color(0xb7e4c7), new Color(0xcdb4db),
                new Color(0xffafcc), new Color(0xf4a261), new Color(0xa8dadc), new Color(0xe9c46a),
                new Color(0xc7b9ff), new Color(0x90be6d) };
        private static final Color CS_COLOR = new Color(0xbdbdbd);

        private List<Snapshot> snaps = List.of();
        private final Map<String, Color> colors = new LinkedHashMap<>();
        private int highlight = -1;

        GanttPanel() {
            setBackground(Color.WHITE);
        }

        void setResult(SimulationResult result) {
            snaps = result.getSnapshots();
            colors.clear();
            int i = 0;
            for (ProcessResult r : result.getProcessResults()) {
                colors.put(r.getName(), PALETTE[i++ % PALETTE.length]);
            }
            highlight = -1;
            revalidate();
            repaint();
        }

        void setHighlight(int index) {
            highlight = index;
            repaint();
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(LEFT * 2 + CELL * snaps.size() + 20, TOP + BAR_H + 110);
        }

        private static String keyOf(Snapshot s) {
            return s.getCpuState() + ":" + s.getRunningProcess();
        }

        private Color colorOf(Snapshot s) {
            switch (s.getCpuState()) {
                case RUNNING:
                    return colors.getOrDefault(s.getRunningProcess(), Color.LIGHT_GRAY);
                case CONTEXT_SWITCH:
                    return CS_COLOR;
                default:
                    return Color.WHITE;
            }
        }

        private static String labelOf(Snapshot s) {
            switch (s.getCpuState()) {
                case RUNNING:
                    return s.getRunningProcess();
                case CONTEXT_SWITCH:
                    return "CS";
                default:
                    return "idle";
            }
        }

        @Override
        protected void paintComponent(Graphics g0) {
            super.paintComponent(g0);
            Graphics2D g = (Graphics2D) g0;
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setFont(getFont().deriveFont(Font.BOLD, 13f));
            FontMetrics fm = g.getFontMetrics();

            int n = snaps.size();
            int i = 0;
            while (i < n) {
                String key = keyOf(snaps.get(i));
                int j = i;
                while (j + 1 < n && keyOf(snaps.get(j + 1)).equals(key)) {
                    j++;
                }
                int x = LEFT + i * CELL;
                int w = (j - i + 1) * CELL;

                g.setColor(colorOf(snaps.get(i)));
                g.fillRect(x, TOP, w, BAR_H);
                g.setColor(Color.DARK_GRAY);
                g.drawRect(x, TOP, w, BAR_H);

                String label = labelOf(snaps.get(i));
                if (fm.stringWidth(label) + 4 < w) {
                    g.setColor(Color.BLACK);
                    g.drawString(label, x + (w - fm.stringWidth(label)) / 2, TOP + (BAR_H + fm.getAscent()) / 2 - 2);
                }

                String t = String.valueOf(snaps.get(i).getTime());
                g.setColor(Color.BLACK);
                g.drawString(t, x - fm.stringWidth(t) / 2, TOP + BAR_H + 18);
                i = j + 1;
            }
            if (n > 0) {
                String end = String.valueOf(n);
                g.drawString(end, LEFT + n * CELL - fm.stringWidth(end) / 2, TOP + BAR_H + 18);
            }

            if (highlight >= 0 && highlight < n) {
                g.setColor(new Color(0xd62728));
                g.setStroke(new BasicStroke(3f));
                g.drawRect(LEFT + highlight * CELL, TOP - 3, CELL, BAR_H + 6);
                g.setStroke(new BasicStroke(1f));
            }

            // Legend
            int lx = LEFT;
            int ly = TOP + BAR_H + 55;
            for (Map.Entry<String, Color> e : colors.entrySet()) {
                lx = legendItem(g, fm, lx, ly, e.getValue(), e.getKey());
            }
            lx = legendItem(g, fm, lx, ly, CS_COLOR, "Context switch");
            legendItem(g, fm, lx, ly, Color.WHITE, "Idle");
        }

        private int legendItem(Graphics2D g, FontMetrics fm, int x, int y, Color c, String text) {
            g.setColor(c);
            g.fillRect(x, y, 16, 16);
            g.setColor(Color.DARK_GRAY);
            g.drawRect(x, y, 16, 16);
            g.setColor(Color.BLACK);
            g.drawString(text, x + 22, y + 13);
            return x + 22 + fm.stringWidth(text) + 24;
        }
    }

    // =====================================================================

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {
                // default look and feel is fine
            }
            new SchedulerGUI().setVisible(true);
        });
    }
}