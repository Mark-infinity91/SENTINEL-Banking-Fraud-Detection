import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.math.BigDecimal;
import java.sql.*;

public class FraudRulesManagement extends JFrame {
    private static final long serialVersionUID = 1L;

    private static final String DB_URL =
            System.getenv("SENTINEL_DB_URL");
    private static final String DB_USER =
            System.getenv("SENTINEL_DB_USER");
    private static final String DB_PASSWORD =
            System.getenv("SENTINEL_DB_PASSWORD");


    private final Color navy = new Color(8, 20, 38);
    private final Color card = new Color(16, 32, 54);
    private final Color cyan = new Color(80, 210, 255);
    private final Color white = new Color(235, 242, 250);
    private final DefaultTableModel model;
    private final JTable table;
    private final JLabel status;
    private final String username;

    private static final String TYPE_HIGH_AMOUNT = "HIGH_AMOUNT";
    private static final String TYPE_REPEATED_SMALL_TRANSFERS = "REPEATED_SMALL_TRANSFERS";

    public FraudRulesManagement(String username) {
        this.username = username;
        
        setTitle("SENTINEL | Fraud Rules Management");
        setSize(1200, 620);
        setMinimumSize(new Dimension(900, 500));
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setLocationRelativeTo(null);

        JPanel root = new JPanel(new BorderLayout(12, 14));
        root.setBackground(navy);
        root.setBorder(BorderFactory.createEmptyBorder(22, 24, 18, 24));
        setContentPane(root);

        JPanel heading = new JPanel();
        heading.setOpaque(false);
        heading.setLayout(new BoxLayout(heading, BoxLayout.Y_AXIS));
        JLabel title = new JLabel("FRAUD RULES MANAGEMENT");
        title.setForeground(white);
        title.setFont(new Font("SansSerif", Font.BOLD, 24));
        JLabel subtitle = new JLabel("Configure detection type, threshold, risk score and rule status");
        subtitle.setForeground(cyan);
        subtitle.setFont(new Font("SansSerif", Font.PLAIN, 12));
        heading.add(title);
        heading.add(Box.createVerticalStrut(5));
        heading.add(subtitle);
        root.add(heading, BorderLayout.NORTH);

        String[] columns = {
            "Rule ID", "Rule Name", "Detection Type", "Description", "Threshold (₹)",
            "Risk Score", "Window (min)", "Min Transfers", "Active", "Last Updated"
        };
        model = new DefaultTableModel(columns, 0) {
            @Override public boolean isCellEditable(int row, int column) { return false; }
        };
        table = new JTable(model);
        table.setRowHeight(28);
        table.setFont(new Font("SansSerif", Font.PLAIN, 12));
        table.setForeground(white);
        table.setBackground(card);
        table.setGridColor(new Color(40, 60, 82));
        table.setSelectionBackground(new Color(31, 77, 120));
        table.setSelectionForeground(Color.WHITE);
        table.getTableHeader().setBackground(new Color(24, 45, 70));
        table.getTableHeader().setForeground(Color.WHITE);
        table.getTableHeader().setFont(new Font("SansSerif", Font.BOLD, 12));
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        int[] widths = {60, 200, 190, 280, 110, 85, 100, 105, 65, 150};
        for (int i = 0; i < widths.length; i++) {
            table.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        }
        JScrollPane scroll = new JScrollPane(table);
        scroll.getViewport().setBackground(card);
        root.add(scroll, BorderLayout.CENTER);

        JButton add = makeButton("ADD RULE");
        JButton edit = makeButton("EDIT SELECTED");
        JButton toggle = makeButton("ACTIVATE / DEACTIVATE");
        JButton refresh = makeButton("REFRESH");
        add.addActionListener(e -> editRule(-1));
        edit.addActionListener(e -> {
            int row = selectedRuleRow();
            if (row >= 0) editRule(row);
        });
        toggle.addActionListener(e -> toggleRule());
        refresh.addActionListener(e -> loadRules());

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        actions.setOpaque(false);
        actions.add(add);
        actions.add(edit);
        actions.add(toggle);
        actions.add(refresh);

        status = new JLabel("Loading rules...");
        status.setForeground(new Color(160, 180, 200));
        JPanel bottom = new JPanel(new BorderLayout(0, 8));
        bottom.setOpaque(false);
        bottom.add(actions, BorderLayout.NORTH);
        bottom.add(status, BorderLayout.SOUTH);
        root.add(bottom, BorderLayout.SOUTH);
        loadRules();
    }

    private JButton makeButton(String text) {
        JButton b = new JButton(text);
        b.setBackground(new Color(22, 119, 210));
        b.setForeground(Color.WHITE);
        b.setFocusPainted(false);
        b.setBorderPainted(false);
        return b;
    }

    private Connection connect() throws SQLException {
        if (DB_URL == null
                || !DB_URL.matches(
                    "(?i)^jdbc:mysql://localhost:3306/sentinel_test_db(?:\\?.*)?$")
                || DB_USER == null
                || DB_PASSWORD == null) {
            throw new SQLException(
                "Connection blocked: only the approved local test database is allowed.");
        }

        return DriverManager.getConnection(
                DB_URL, DB_USER, DB_PASSWORD);
    }

    private boolean authorizeAction() {
        if (InvestigatorDashboard.isAuthorizedInvestigator(username)) {
            return true;
        }

        JOptionPane.showMessageDialog(
            this,
            "Access denied. An active investigator account is required.",
            "SENTINEL | Access Denied",
            JOptionPane.ERROR_MESSAGE
        );

        return false;
    }
    
    private int selectedRuleRow() {
        int viewRow = table.getSelectedRow();
        if (viewRow < 0) {
            JOptionPane.showMessageDialog(this, "Select a rule from the table first.");
            return -1;
        }
        return table.convertRowIndexToModel(viewRow);
    }

    private void loadRules() {
        if (!authorizeAction()) return;
        status.setText("Loading rules...");
        String sql =
            "SELECT rule_id, rule_name, detection_type, rule_description, threshold_amount, " +
            "risk_score, window_minutes, min_occurrences, is_active, updated_at " +
            "FROM fraud_rules ORDER BY rule_id";

        try (Connection con = connect();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            model.setRowCount(0);
            while (rs.next()) {
                BigDecimal threshold = rs.getBigDecimal("threshold_amount");
                model.addRow(new Object[] {
                    rs.getInt("rule_id"),
                    rs.getString("rule_name"),
                    rs.getString("detection_type"),
                    rs.getString("rule_description"),
                    threshold == null ? "—" : threshold.toPlainString(),
                    rs.getInt("risk_score"),
                    rs.getInt("window_minutes"),
                    rs.getInt("min_occurrences"),
                    rs.getBoolean("is_active") ? "YES" : "NO",
                    rs.getTimestamp("updated_at")
                });
            }
            status.setText("Connected | " + model.getRowCount() + " rule(s) loaded");
        } catch (SQLException ex) {
            showError("Could not load fraud rules", ex);
            status.setText("Database error");
        }
    }

    private void editRule(int row) {
        if (!authorizeAction()) return;
        boolean isNew = row < 0;

        JTextField name = new JTextField(isNew ? "" : String.valueOf(model.getValueAt(row, 1)), 24);
        JComboBox<String> type = new JComboBox<>(new String[] {
            TYPE_HIGH_AMOUNT, TYPE_REPEATED_SMALL_TRANSFERS
        });
        JTextArea description = new JTextArea(isNew ? "" : String.valueOf(model.getValueAt(row, 3)), 4, 24);
        description.setLineWrap(true);
        description.setWrapStyleWord(true);

        String thresholdValue = "";
        if (!isNew && !"—".equals(String.valueOf(model.getValueAt(row, 4)))) {
            thresholdValue = String.valueOf(model.getValueAt(row, 4));
        }
        JTextField threshold = new JTextField(thresholdValue, 12);
        JSpinner riskScore = new JSpinner(new SpinnerNumberModel(
            isNew ? 50 : ((Number) model.getValueAt(row, 5)).intValue(), 1, 100, 5));
        JSpinner windowMinutes = new JSpinner(new SpinnerNumberModel(
            isNew ? 10 : Math.max(1, ((Number) model.getValueAt(row, 6)).intValue()), 1, 1440, 1));
        JSpinner minOccurrences = new JSpinner(new SpinnerNumberModel(
            isNew ? 5 : Math.max(2, ((Number) model.getValueAt(row, 7)).intValue()), 2, 1000, 1));
        JCheckBox active = new JCheckBox("Rule is active",
            isNew || "YES".equals(String.valueOf(model.getValueAt(row, 8))));
        active.setOpaque(false);

        if (!isNew) {
            type.setSelectedItem(String.valueOf(model.getValueAt(row, 2)));
        }
        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(5, 5, 5, 5);
        g.anchor = GridBagConstraints.WEST;
        g.fill = GridBagConstraints.HORIZONTAL;
        g.gridx = 0; g.gridy = 0; form.add(new JLabel("Rule name:"), g);
        g.gridx = 1; form.add(name, g);
        g.gridx = 0; g.gridy++; form.add(new JLabel("Detection type:"), g);
        g.gridx = 1; form.add(type, g);
        g.gridx = 0; g.gridy++; form.add(new JLabel("Description:"), g);
        g.gridx = 1; form.add(new JScrollPane(description), g);
        g.gridx = 0; g.gridy++; form.add(new JLabel("Threshold amount (₹):"), g);
        g.gridx = 1; form.add(threshold, g);
        g.gridx = 0; g.gridy++; form.add(new JLabel("Risk score (1–100):"), g);
        g.gridx = 1; form.add(riskScore, g);
        g.gridx = 0; g.gridy++; form.add(new JLabel("Time window (minutes):"), g);
        g.gridx = 1; form.add(windowMinutes, g);
        g.gridx = 0; g.gridy++; form.add(new JLabel("Minimum transfers:"), g);
        g.gridx = 1; form.add(minOccurrences, g);
        g.gridx = 1; g.gridy++; form.add(active, g);

        String help = "HIGH_AMOUNT: triggers at or above the threshold.\n" +
            "REPEATED_SMALL_TRANSFERS: triggers at or below the threshold when the same sender-to-receiver pair " +
            "has at least the configured number of completed transfers within the time window.";
        JOptionPane.showMessageDialog(this, help, "Detection Type Guide", JOptionPane.INFORMATION_MESSAGE);

        int choice = JOptionPane.showConfirmDialog(this, form,
            isNew ? "Add Fraud Rule" : "Edit Fraud Rule",
            JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (choice != JOptionPane.OK_OPTION) return;

        String ruleName = name.getText().trim();
        String ruleDescription = description.getText().trim();
        String thresholdText = threshold.getText().trim();
        String detectionType = String.valueOf(type.getSelectedItem());

        if (ruleName.isEmpty() || ruleDescription.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Rule name and description are required.");
            return;
        }
        if (thresholdText.isEmpty()) {
            JOptionPane.showMessageDialog(this, "A threshold is required for these detection types.");
            return;
        }

        BigDecimal amount;
        try {
            amount = new BigDecimal(thresholdText);
            if (amount.compareTo(BigDecimal.ZERO) < 0 || amount.scale() > 2) {
                throw new NumberFormatException();
            }
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this,
                "Threshold must be a non-negative number with at most 2 decimal places.");
            return;
        }

        String sql = isNew
            ? "INSERT INTO fraud_rules (rule_name, rule_description, detection_type, threshold_amount, risk_score, window_minutes, min_occurrences, is_active) VALUES (?, ?, ?, ?, ?, ?, ?, ?)"
            : "UPDATE fraud_rules SET rule_name=?, rule_description=?, detection_type=?, threshold_amount=?, risk_score=?, window_minutes=?, min_occurrences=?, is_active=? WHERE rule_id=?";

        try (Connection con = connect();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, ruleName);
            ps.setString(2, ruleDescription);
            ps.setString(3, detectionType);
            ps.setBigDecimal(4, amount);
            ps.setInt(5, ((Number) riskScore.getValue()).intValue());
            ps.setInt(6, ((Number) windowMinutes.getValue()).intValue());
            ps.setInt(7, ((Number) minOccurrences.getValue()).intValue());
            ps.setBoolean(8, active.isSelected());
            if (!isNew) {
                ps.setInt(9, ((Number) model.getValueAt(row, 0)).intValue());
            }
            ps.executeUpdate();
            JOptionPane.showMessageDialog(this, isNew ? "Fraud rule added." : "Fraud rule updated.");
            loadRules();
        } catch (SQLIntegrityConstraintViolationException ex) {
            JOptionPane.showMessageDialog(this,
                "A rule with this name may already exist. Choose a unique rule name.",
                "Rule Not Saved", JOptionPane.WARNING_MESSAGE);
        } catch (SQLException ex) {
            showError("Could not save fraud rule", ex);
        }
    }

    private void toggleRule() {
        if (!authorizeAction()) return;
        int row = selectedRuleRow();
        if (row < 0) return;
        int id = ((Number) model.getValueAt(row, 0)).intValue();
        String name = String.valueOf(model.getValueAt(row, 1));
        boolean currentlyActive = "YES".equals(String.valueOf(model.getValueAt(row, 8)));
        int confirm = JOptionPane.showConfirmDialog(this,
            (currentlyActive ? "Deactivate" : "Activate") + " rule '" + name + "'?",
            "Confirm Rule Change", JOptionPane.YES_NO_OPTION);
        if (confirm != JOptionPane.YES_OPTION) return;

        try (Connection con = connect();
        
             PreparedStatement ps = con.prepareStatement(
                 "UPDATE fraud_rules SET is_active=? WHERE rule_id=?")) {
            ps.setBoolean(1, !currentlyActive);
            ps.setInt(2, id);
            ps.executeUpdate();
            loadRules();
        } catch (SQLException ex) {
            showError("Could not change rule status", ex);
        }
    }

    private void showError(String title, Exception ex) {
        Throwable cause = ex;
        while (cause.getCause() != null) cause = cause.getCause();
        JOptionPane.showMessageDialog(this, title + ":\n" + cause.getMessage(),
            "SENTINEL Error", JOptionPane.ERROR_MESSAGE);
    }


    public static void main(String[] args) {
    SwingUtilities.invokeLater(() ->
        JOptionPane.showMessageDialog(
            null,
            "Please launch SENTINEL through LoginFrame.",
            "Access Restricted",
            JOptionPane.WARNING_MESSAGE
        )
    );
}    
}
