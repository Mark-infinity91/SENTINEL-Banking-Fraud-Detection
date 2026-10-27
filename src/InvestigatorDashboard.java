
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class InvestigatorDashboard extends JFrame {
    private static final long serialVersionUID = 1L;


    private static final String DB_URL =
            System.getenv("SENTINEL_DB_URL");
    private static final String DB_USER =
            System.getenv("SENTINEL_DB_USER");
    private static final String DB_PASSWORD =
            System.getenv("SENTINEL_DB_PASSWORD");


    private DefaultTableModel model;
    private final JTable table;
    private final JLabel status;
    private final JButton refreshButton;
    private final String loggedInUsername;

    private final Color navy = new Color(8, 20, 38);
    private final Color card = new Color(16, 32, 54);
    private final Color blue = new Color(22, 119, 210);
    private final Color white = new Color(235, 242, 250);
    private final Color cyan = new Color(80, 210, 255);

    public InvestigatorDashboard(String username) {
        this.loggedInUsername = username;
        setTitle("SENTINEL | Investigator Dashboard");
        setSize(1100, 650);
        setMinimumSize(new Dimension(850, 500));
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setLocationRelativeTo(null);

        JPanel root = new JPanel(new BorderLayout(0, 18));
        root.setBackground(navy);
        root.setBorder(BorderFactory.createEmptyBorder(25, 28, 20, 28));
        setContentPane(root);

        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);

        JPanel titles = new JPanel();
        titles.setOpaque(false);
        titles.setLayout(new BoxLayout(titles, BoxLayout.Y_AXIS));

        JLabel brand = new JLabel("SENTINEL");
        brand.setFont(new Font("SansSerif", Font.BOLD, 27));
        brand.setForeground(white);

        JLabel subtitle = new JLabel("FRAUD INTELLIGENCE PLATFORM");
        subtitle.setFont(new Font("SansSerif", Font.BOLD, 11));
        subtitle.setForeground(cyan);

        titles.add(brand);
        titles.add(Box.createVerticalStrut(5));
        titles.add(subtitle);

        refreshButton = new JButton("REFRESH ALERTS");
        JButton fraudRulesButton = new JButton("FRAUD RULES");
        fraudRulesButton.setBackground(new Color(0, 150, 160));
        fraudRulesButton.setForeground(Color.WHITE);
        fraudRulesButton.setFocusPainted(false);
        fraudRulesButton.setBorderPainted(false);


        styleActionButton(refreshButton, blue);
        refreshButton.addActionListener(e -> loadAlerts());

        JButton scanButton = new JButton("SCAN TRANSACTIONS");
        styleActionButton(scanButton, new Color(30, 125, 90));
        scanButton.addActionListener(e -> scanTransactions());

        JButton transactionsButton = new JButton("TRANSACTION HISTORY");
        styleActionButton(transactionsButton, new Color(34, 90, 130));
        transactionsButton.addActionListener(e -> showTransactionHistory());

        JButton riskButton = new JButton("CUSTOMER RISK");
        styleActionButton(riskButton, new Color(130, 88, 25));
        riskButton.addActionListener(e -> showCustomerRiskIndicators());

        JButton historyButton = new JButton("INVESTIGATION HISTORY");
        styleActionButton(historyButton, new Color(95, 70, 145));
        historyButton.addActionListener(e -> askAndShowInvestigationHistory());

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actions.setOpaque(false);
        actions.add(transactionsButton);
        actions.add(riskButton);
        actions.add(historyButton);
        actions.add(scanButton);
        actions.add(refreshButton);

        // NEW: Fraud Rules button
        fraudRulesButton.setBackground(new Color(0, 150, 160));
        fraudRulesButton.setForeground(Color.WHITE);
        fraudRulesButton.setFocusPainted(false);
        fraudRulesButton.setBorderPainted(false);

        fraudRulesButton.addActionListener(e -> {
            if (!InvestigatorDashboard.isAuthorizedInvestigator(loggedInUsername)) {
                JOptionPane.showMessageDialog(
                        this,
                        "Access denied. An active investigator account is required.",
                        "SENTINEL | Access Denied",
                        JOptionPane.ERROR_MESSAGE
                );
                return;
            }

            new FraudRulesManagement(loggedInUsername).setVisible(true);
        });
        actions.add(fraudRulesButton);

        JButton logoutButton = new JButton("LOGOUT");
        styleActionButton(logoutButton, new Color(170, 55, 55));

        logoutButton.addActionListener(e -> {
            int choice = JOptionPane.showConfirmDialog(
                this,
                "Are you sure you want to logout?",
                "SENTINEL | Logout",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE
            );

            if (choice == JOptionPane.YES_OPTION) {
                dispose();
                LoginFrame loginFrame = new LoginFrame();
                loginFrame.setVisible(true);
            }
        });

        actions.add(logoutButton);



        header.add(titles, BorderLayout.WEST);
        header.add(actions, BorderLayout.EAST);
        root.add(header, BorderLayout.NORTH);

        String[] columns = {
            "Alert ID", "Transaction ID", "Amount",
            "Risk Score", "Triggered Rules", "Status", "Created At"
        };

        model = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        table = new JTable(model);
        table.setRowHeight(36);
        table.setFont(new Font("SansSerif", Font.PLAIN, 12));
        table.setForeground(white);
        table.setBackground(card);
        table.setGridColor(new Color(40, 60, 82));
        table.setSelectionBackground(new Color(31, 77, 120));
        table.setSelectionForeground(Color.WHITE);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);

        table.getTableHeader().setBackground(new Color(24, 45, 70));
        table.getTableHeader().setForeground(Color.WHITE);
        table.getTableHeader().setFont(new Font("SansSerif", Font.BOLD, 12));

        int[] widths = {75, 110, 110, 95, 280, 110, 160};
        for (int i = 0; i < widths.length; i++) {
            table.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        }

        table.setToolTipText("Double-click an alert to investigate it");
        table.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2 && table.getSelectedRow() >= 0) {
                    int row = table.convertRowIndexToModel(table.getSelectedRow());
                    int alertId = ((Number) model.getValueAt(row, 0)).intValue();
                    openInvestigation(alertId);
                }
            }
        });

        JScrollPane scroll = new JScrollPane(table);
        scroll.getViewport().setBackground(card);
        scroll.setBorder(BorderFactory.createLineBorder(new Color(40, 60, 82)));

        JPanel tableCard = new JPanel(new BorderLayout(0, 12));
        tableCard.setBackground(card);
        tableCard.setBorder(BorderFactory.createEmptyBorder(18, 18, 18, 18));

        JLabel tableTitle = new JLabel("FRAUD ALERTS  |  Double-click a row to investigate");
        tableTitle.setForeground(white);
        tableTitle.setFont(new Font("SansSerif", Font.BOLD, 14));

        tableCard.add(tableTitle, BorderLayout.NORTH);
        tableCard.add(scroll, BorderLayout.CENTER);
        root.add(tableCard, BorderLayout.CENTER);

        status = new JLabel("Connecting to database...");
        status.setForeground(new Color(160, 180, 200));
        root.add(status, BorderLayout.SOUTH);

        loadAlerts();
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

        return DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
    }

    public static boolean isAuthorizedInvestigator(String username) {
    if (username == null || username.trim().isEmpty()) {
        return false;
    }

    String sql = "SELECT role, account_status FROM users WHERE username = ?";
    String url = System.getenv("SENTINEL_DB_URL");
    String dbUser = System.getenv("SENTINEL_DB_USER");
    String dbPassword = System.getenv("SENTINEL_DB_PASSWORD");

    if (url == null || dbUser == null || dbPassword == null
            || !url.matches(
                "(?i)^jdbc:mysql://localhost:3306/sentinel_test_db(?:\\?.*)?$")) {
        return false;
    }

    try (Connection con = DriverManager.getConnection(url, dbUser, dbPassword);
         PreparedStatement ps = con.prepareStatement(sql)) {

        ps.setString(1, username);

        try (ResultSet rs = ps.executeQuery()) {
            return rs.next()
                    && "INVESTIGATOR".equalsIgnoreCase(rs.getString("role"))
                    && "ACTIVE".equalsIgnoreCase(rs.getString("account_status"));
        }
    } catch (SQLException ex) {
        return false;
    }
}    

    private void scanTransactions() {
        int choice = JOptionPane.showConfirmDialog(this,
                "Scan completed transactions for the configured demo fraud rules?\n" +
                "Existing alerts will not be duplicated.",
                "SENTINEL Fraud Scan", JOptionPane.YES_NO_OPTION);
        if (choice != JOptionPane.YES_OPTION) return;

        refreshButton.setEnabled(false);
        status.setText("Scanning transactions...");
        SwingWorker<Integer, Void> worker = new SwingWorker<>() {
            @Override
            protected Integer doInBackground() throws Exception {
                try (Connection con = connect()) {
                    return FraudDetectionEngine.scan(con);
                }
            }
            @Override
            protected void done() {
                try {
                    int created = get();
                    refreshButton.setEnabled(true);
                    JOptionPane.showMessageDialog(InvestigatorDashboard.this,
                            "Scan complete. New alerts created: " + created +
                            "\nTransactions with existing alerts were skipped.",
                            "Fraud Scan Complete", JOptionPane.INFORMATION_MESSAGE);
                    loadAlerts();
                } catch (Exception ex) {
                    showError("Fraud scan failed", ex);
                    refreshButton.setEnabled(true);
                    status.setText("Fraud scan failed");
                }
            }
        };
        worker.execute();
    }

    private void loadAlerts() {
        refreshButton.setEnabled(false);
        status.setText("Loading alerts...");

        SwingWorker<List<Object[]>, Void> worker = new SwingWorker<>() {
            @Override
            protected List<Object[]> doInBackground() throws Exception {
                List<Object[]> rows = new ArrayList<>();

                String sql =
                    "SELECT a.alert_id, a.transaction_id, t.amount, " +
                    "a.risk_score, a.triggered_rules, a.alert_status, a.created_at " +
                    "FROM fraud_alerts a " +
                    "LEFT JOIN transactions t ON a.transaction_id = t.transaction_id " +
                    "ORDER BY a.created_at DESC";

                try (Connection con = connect();
                     PreparedStatement ps = con.prepareStatement(sql);
                     ResultSet rs = ps.executeQuery()) {

                    while (rs.next()) {
                        Object amount = rs.getObject("amount");
                        rows.add(new Object[] {
                            rs.getInt("alert_id"),
                            rs.getInt("transaction_id"),
                            amount == null ? "N/A" : "₹" + amount,
                            rs.getInt("risk_score"),
                            rs.getString("triggered_rules"),
                            rs.getString("alert_status"),
                            rs.getTimestamp("created_at")
                        });
                    }
                }
                return rows;
            }

            @Override
            protected void done() {
                try {
                    List<Object[]> rows = get();
                    model.setRowCount(0);
                    for (Object[] row : rows) {
                        model.addRow(row);
                    }
                    status.setText("Connected | " + rows.size() + " alert(s) loaded");
                } catch (Exception ex) {
                    showError("Could not load alerts", ex);
                    status.setText("Database error");
                } finally {
                    refreshButton.setEnabled(true);
                }
            }
        };

        worker.execute();
    }


    // Consistent styling for the Batch 4 dashboard actions.
    private void styleActionButton(JButton button, Color background) {
        button.setBackground(background);
        button.setForeground(Color.WHITE);
        button.setFocusPainted(false);
        button.setBorderPainted(false);
        button.setFont(new Font("SansSerif", Font.BOLD, 10));
        button.setMargin(new Insets(9, 10, 9, 10));
    }

    // Batch 4: show the most recent 100 transactions with both customer names.
    private void showTransactionHistory() {
        String sql =
            "SELECT t.transaction_id AS 'Transaction ID', " +
            "t.transaction_time AS 'Transaction Time', " +
            "t.amount AS 'Amount (INR)', " +
            "sc.full_name AS 'Sender', sa.account_number AS 'Sender Account', " +
            "rc.full_name AS 'Receiver', ra.account_number AS 'Receiver Account', " +
            "t.transaction_source AS 'Source', t.payment_status AS 'Payment Status' " +
            "FROM transactions t " +
            "LEFT JOIN bank_accounts sa ON t.sender_account_id = sa.account_id " +
            "LEFT JOIN customers sc ON sa.customer_id = sc.customer_id " +
            "LEFT JOIN bank_accounts ra ON t.receiver_account_id = ra.account_id " +
            "LEFT JOIN customers rc ON ra.customer_id = rc.customer_id " +
            "ORDER BY t.transaction_time DESC LIMIT 100";

        showQueryTable("Recent Transaction History", sql,
            new String[] {"Transaction ID", "Transaction Time", "Amount (INR)",
                "Sender", "Sender Account", "Receiver", "Receiver Account",
                "Source", "Payment Status"});
    }

    // Batch 4: count each alert once, even when sender and receiver share a customer.
    private void showCustomerRiskIndicators() {
        String sql =
            "SELECT c.customer_id AS 'Customer ID', " +
            "c.full_name AS 'Customer Name', " +
            "COUNT(DISTINCT fa.alert_id) AS 'Total Alerts', " +
            "COUNT(DISTINCT CASE WHEN fa.risk_score >= 80 " +
            "THEN fa.alert_id END) AS 'High-Risk Alerts', " +
            "COALESCE(MAX(fa.risk_score), 0) AS 'Highest Risk Score' " +
            "FROM customers c " +
            "JOIN bank_accounts ba ON c.customer_id = ba.customer_id " +
            "LEFT JOIN transactions t ON ba.account_id = t.sender_account_id " +
            "OR ba.account_id = t.receiver_account_id " +
            "LEFT JOIN fraud_alerts fa ON t.transaction_id = fa.transaction_id " +
            "GROUP BY c.customer_id, c.full_name " +
            "ORDER BY `Total Alerts` DESC, c.full_name";

        showQueryTable("Customer Risk Indicators", sql,
            new String[] {"Customer ID", "Customer Name", "Total Alerts",
                "High-Risk Alerts", "Highest Risk Score"});
    }

    // Ask which alert's investigation trail should be displayed.
    private void askAndShowInvestigationHistory() {
        String value = JOptionPane.showInputDialog(this,
            "Enter the Alert ID to view its investigation history:",
            "Investigation History", JOptionPane.QUESTION_MESSAGE);
        if (value == null) return;

        final int alertId;
        try {
            alertId = Integer.parseInt(value.trim());
            if (alertId <= 0) throw new NumberFormatException();
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this,
                "Please enter a valid positive Alert ID.",
                "Invalid Alert ID", JOptionPane.WARNING_MESSAGE);
            return;
        }

        String sql =
            "SELECT i.investigation_id AS 'Investigation ID', " +
            "i.alert_id AS 'Alert ID', u.username AS 'Investigator', " +
            "i.decision AS 'Decision', i.investigation_note AS 'Investigation Notes', " +
            "i.investigation_status AS 'Investigation Status', " +
            "i.evidence AS 'Evidence', i.findings AS 'Findings', " +
            "i.follow_up_action AS 'Follow-up Action', " +
            "i.reviewed_at AS 'Reviewed At' " +
            "FROM investigations i " +
            "LEFT JOIN users u ON i.investigator_user_id = u.user_id " +
            "WHERE i.alert_id = " + alertId + " " +
            "ORDER BY i.reviewed_at DESC";

        showQueryTable("Investigation History — Alert #" + alertId, sql,
            new String[] {"Investigation ID", "Alert ID", "Investigator",
                "Decision", "Investigation Notes", "Investigation Status",
                "Evidence", "Findings", "Follow-up Action", "Reviewed At"});
    }

    // Shared read-only table window used by all Batch 4 features.
    private void showQueryTable(String title, String sql, String[] columns) {
        DefaultTableModel queryModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        JDialog dialog = new JDialog(this, title, true);
        dialog.setSize(850, 750);
        dialog.setMinimumSize(new Dimension(700, 600));
        dialog.setLocationRelativeTo(this);

        JTable dataTable = new JTable(queryModel);
        dataTable.setRowHeight(28);
        dataTable.setFont(new Font("SansSerif", Font.PLAIN, 12));
        dataTable.setForeground(white);
        dataTable.setBackground(card);
        dataTable.setGridColor(new Color(40, 60, 82));
        dataTable.setSelectionBackground(new Color(31, 77, 120));
        dataTable.setSelectionForeground(Color.WHITE);
        dataTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        dataTable.getTableHeader().setBackground(new Color(24, 45, 70));
        dataTable.getTableHeader().setForeground(Color.WHITE);
        dataTable.getTableHeader().setFont(new Font("SansSerif", Font.BOLD, 12));

        JScrollPane scroll = new JScrollPane(dataTable);
        scroll.getViewport().setBackground(card);
        scroll.setBorder(BorderFactory.createLineBorder(new Color(40, 60, 82)));

        JLabel message = new JLabel("Loading data from sentinel_db...");
        message.setForeground(white);
        message.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));

        JPanel content = new JPanel(new BorderLayout(0, 8));
        content.setBackground(navy);
        content.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        content.add(message, BorderLayout.NORTH);
        content.add(scroll, BorderLayout.CENTER);
        dialog.setContentPane(content);

        SwingWorker<List<Object[]>, Void> worker = new SwingWorker<>() {
            @Override
            protected List<Object[]> doInBackground() throws Exception {
                List<Object[]> rows = new ArrayList<>();
                try (Connection con = connect();
                     PreparedStatement ps = con.prepareStatement(sql);
                     ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Object[] row = new Object[columns.length];
                        for (int i = 0; i < columns.length; i++) {
                            row[i] = rs.getObject(i + 1);
                        }
                        rows.add(row);
                    }
                }
                return rows;
            }

            @Override
            protected void done() {
                try {
                    List<Object[]> rows = get();
                    for (Object[] row : rows) queryModel.addRow(row);
                    message.setText(rows.size() + " record(s) loaded");
                    if (rows.isEmpty()) message.setText("No records found.");
                    for (int i = 0; i < dataTable.getColumnCount(); i++) {
                        dataTable.getColumnModel().getColumn(i).setPreferredWidth(
                            i == 4 && title.startsWith("Investigation History") ? 380 :
                            (i == 1 ? 170 : 135));
                    }
                } catch (Exception ex) {
                    message.setText("Could not load data.");
                    showError("Could not load " + title, ex);
                }
            }
        };

        worker.execute();
        dialog.setVisible(true);
    }


    private void openInvestigation(int alertId) {
    String sql =
        "SELECT a.alert_id, a.transaction_id, a.risk_score, " +
        "a.triggered_rules, a.alert_status, a.created_at, " +
        "t.sender_account_id, t.receiver_account_id, t.amount, " +
        "t.transaction_time, t.transaction_source, t.payment_status, " +

        "sa.account_number AS sender_account_number, " +
        "sa.account_type AS sender_account_type, " +
        "sa.balance AS sender_balance, " +
        "sa.account_status AS sender_account_status, " +
        "sc.full_name AS sender_name, " +
        "sc.customer_type AS sender_customer_type, " +
        "sc.email AS sender_email, " +
        "sc.phone AS sender_phone, " +

        "ra.account_number AS receiver_account_number, " +
        "ra.account_type AS receiver_account_type, " +
        "ra.balance AS receiver_balance, " +
        "ra.account_status AS receiver_account_status, " +
        "rc.full_name AS receiver_name, " +
        "rc.customer_type AS receiver_customer_type, " +
        "rc.email AS receiver_email, " +
        "rc.phone AS receiver_phone " +

        "FROM fraud_alerts a " +
        "JOIN transactions t ON a.transaction_id = t.transaction_id " +
        "JOIN bank_accounts sa ON t.sender_account_id = sa.account_id " +
        "LEFT JOIN customers sc ON sa.customer_id = sc.customer_id " +
        "JOIN bank_accounts ra ON t.receiver_account_id = ra.account_id " +
        "LEFT JOIN customers rc ON ra.customer_id = rc.customer_id " +
        "WHERE a.alert_id = ?";

    try (Connection con = connect();
         PreparedStatement ps = con.prepareStatement(sql)) {

        ps.setInt(1, alertId);

        try (ResultSet rs = ps.executeQuery()) {
            if (!rs.next()) {
                JOptionPane.showMessageDialog(this,
                    "Alert or linked customer/account details were not found.");
                return;
            }

            showInvestigationForm(
                rs.getInt("alert_id"),
                rs.getInt("transaction_id"),
                rs.getInt("risk_score"),
                rs.getString("triggered_rules"),
                rs.getString("alert_status"),
                rs.getInt("sender_account_id"),
                rs.getInt("receiver_account_id"),
                rs.getBigDecimal("amount").toPlainString(),
                rs.getTimestamp("transaction_time"),
                rs.getString("transaction_source"),
                rs.getString("payment_status"),

                rs.getString("sender_account_number"),
                rs.getString("sender_account_type"),
                rs.getBigDecimal("sender_balance").toPlainString(),
                rs.getString("sender_account_status"),
                rs.getString("sender_name"),
                rs.getString("sender_customer_type"),
                rs.getString("sender_email"),
                rs.getString("sender_phone"),

                rs.getString("receiver_account_number"),
                rs.getString("receiver_account_type"),
                rs.getBigDecimal("receiver_balance").toPlainString(),
                rs.getString("receiver_account_status"),
                rs.getString("receiver_name"),
                rs.getString("receiver_customer_type"),
                rs.getString("receiver_email"),
                rs.getString("receiver_phone")
            );
        }
    } catch (Exception ex) {
        showError("Could not open investigation", ex);
    }
}



    private void showInvestigationForm(
        int alertId, int transactionId, int riskScore,
        String rules, String alertStatus, int senderId, int receiverId,
        String amount, Timestamp transactionTime, String source,
        String paymentStatus,
        String senderAccountNumber, String senderAccountType,
        String senderBalance, String senderAccountStatus,
        String senderName, String senderCustomerType,
        String senderEmail, String senderPhone,
        String receiverAccountNumber, String receiverAccountType,
        String receiverBalance, String receiverAccountStatus,
        String receiverName, String receiverCustomerType,
        String receiverEmail, String receiverPhone) {

    JDialog dialog = new JDialog(
        this, "SENTINEL | Investigate Alert #" + alertId, true);
    dialog.setSize(900, 780);
    dialog.setMinimumSize(new Dimension(760, 650));
    dialog.setLocationRelativeTo(this);

    JPanel panel = new JPanel(new BorderLayout(10, 10));
    panel.setBackground(navy);
    panel.setBorder(BorderFactory.createEmptyBorder(12, 14, 12, 14));
    dialog.setContentPane(panel);

    JTextArea details = new JTextArea();
    details.setEditable(false);
    details.setLineWrap(false);
    details.setFont(new Font("Monospaced", Font.PLAIN, 12));
    details.setForeground(white);
    details.setBackground(card);
    details.setMargin(new Insets(10, 10, 10, 10));
    details.setText(
        "========== FRAUD ALERT ==========\n" +
        "Alert ID:          " + alertId +
        "\nTransaction ID:    " + transactionId +
        "\nAmount:            ₹" + amount +
        "\nRisk Score:        " + riskScore + "/100" +
        "\nTriggered Rules:   " + rules +
        "\nAlert Status:      " + alertStatus +
        "\nTransaction Time:  " + transactionTime +
        "\nTransaction Source:" + source +
        "\nPayment Status:    " + paymentStatus +
        "\n\n========== SENDER ==========\n" +
        "Customer Name:     " + senderName +
        "\nCustomer Type:     " + senderCustomerType +
        "\nEmail:             " + display(senderEmail) +
        "\nPhone:             " + display(senderPhone) +
        "\nAccount ID:        " + senderId +
        "\nAccount Number:    " + senderAccountNumber +
        "\nAccount Type:      " + senderAccountType +
        "\nAccount Balance:   ₹" + senderBalance +
        "\nAccount Status:    " + senderAccountStatus +
        "\n\n========== RECEIVER ==========\n" +
        "Customer Name:     " + receiverName +
        "\nCustomer Type:     " + receiverCustomerType +
        "\nEmail:             " + display(receiverEmail) +
        "\nPhone:             " + display(receiverPhone) +
        "\nAccount ID:        " + receiverId +
        "\nAccount Number:    " + receiverAccountNumber +
        "\nAccount Type:      " + receiverAccountType +
        "\nAccount Balance:   ₹" + receiverBalance +
        "\nAccount Status:    " + receiverAccountStatus
    );

    JScrollPane detailsScroll = new JScrollPane(
        details, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
        JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
    detailsScroll.setBorder(BorderFactory.createTitledBorder(
        BorderFactory.createLineBorder(new Color(60, 85, 110)),
        "ALERT & ACCOUNT DETAILS"));
    detailsScroll.setPreferredSize(new Dimension(850, 280));

    JPanel form = new JPanel();
    form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
    form.setBackground(Color.WHITE);
    form.setBorder(BorderFactory.createEmptyBorder(12, 14, 12, 14));

    JLabel decisionLabel = makeFormLabel("Investigation decision");
    JComboBox<String> decision = new JComboBox<>(new String[] {
        "SUSPECTED_FRAUD", "LEGITIMATE", "NEEDS_MORE_INFO"
    });
    decision.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
    decision.setPreferredSize(new Dimension(700, 34));
    decision.setAlignmentX(Component.LEFT_ALIGNMENT);

    JLabel noteLabel = makeFormLabel("Investigation notes (required)");
    JTextArea note = makeFormTextArea();
    JLabel evidenceLabel = makeFormLabel("Evidence (optional)");
    JTextArea evidence = makeFormTextArea();
    JLabel findingsLabel = makeFormLabel("Investigation findings (optional)");
    JTextArea findings = makeFormTextArea();
    JLabel followUpLabel = makeFormLabel("Follow-up action (optional)");
    JTextArea followUp = makeFormTextArea();

    addFormField(form, decisionLabel, decision, 36);
    addFormField(form, noteLabel, new JScrollPane(note), 82);
    addFormField(form, evidenceLabel, new JScrollPane(evidence), 82);
    addFormField(form, findingsLabel, new JScrollPane(findings), 82);
    addFormField(form, followUpLabel, new JScrollPane(followUp), 82);

    JScrollPane formScroll = new JScrollPane(
        form, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
        JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
    formScroll.getVerticalScrollBar().setUnitIncrement(18);
    formScroll.setBorder(BorderFactory.createTitledBorder(
        BorderFactory.createLineBorder(new Color(60, 85, 110)),
        "INVESTIGATION WORKSPACE"));

    JButton save = new JButton("SAVE INVESTIGATION");
    save.setBackground(blue);
    save.setForeground(Color.WHITE);
    save.setFocusPainted(false);
    save.setFont(new Font("SansSerif", Font.BOLD, 12));
    save.setMargin(new Insets(10, 18, 10, 18));

    JButton cancel = new JButton("CANCEL");
    cancel.setFont(new Font("SansSerif", Font.BOLD, 12));
    cancel.setMargin(new Insets(10, 18, 10, 18));
    cancel.addActionListener(e -> dialog.dispose());

    JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 4));
    buttons.setBackground(navy);
    buttons.add(cancel);
    buttons.add(save);

    JSplitPane split = new JSplitPane(
        JSplitPane.VERTICAL_SPLIT, detailsScroll, formScroll);
    split.setResizeWeight(0.38);
    split.setDividerLocation(280);
    split.setContinuousLayout(true);
    split.setBorder(null);
    split.setBackground(navy);

    panel.add(split, BorderLayout.CENTER);
    panel.add(buttons, BorderLayout.SOUTH);

    save.addActionListener(e -> {
        String noteText = note.getText().trim();
        String evidenceText = evidence.getText().trim();
        String findingsText = findings.getText().trim();
        String followUpText = followUp.getText().trim();

        if (noteText.isEmpty()) {
            JOptionPane.showMessageDialog(dialog,
                "Please enter investigation notes.");
            return;
        }

        save.setEnabled(false);
        save.setText("SAVING...");

        SwingWorker<Void, Void> worker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() throws Exception {
                try (Connection con = connect()) {
                    con.setAutoCommit(false);

                    try {

                        String selectedDecision = (String) decision.getSelectedItem();

                        String newStatus = "NEEDS_MORE_INFO".equals(selectedDecision)
                                ? "IN_PROGRESS" : "CLOSED";

                        String newAlertStatus = newStatus;

                        if (!isAuthorizedInvestigator(loggedInUsername)) {
                            throw new SQLException(
                                "Access denied. An active investigator account is required.");
                        }

                        int investigatorId = getInvestigatorUserId(con);
                        int investigationId;

                        String insert =
                                "INSERT INTO investigations " +
                                "(alert_id, investigator_user_id, decision, " +
                                "investigation_note, investigation_status, " +
                                "evidence, findings, follow_up_action) " +
                                "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

                        try (PreparedStatement ps = con.prepareStatement(
                                insert, Statement.RETURN_GENERATED_KEYS)) {

                            ps.setInt(1, alertId);
                            ps.setInt(2, investigatorId);
                            ps.setString(3, selectedDecision);
                            ps.setString(4, noteText);
                            ps.setString(5, newStatus);
                            ps.setString(6, evidenceText);
                            ps.setString(7, findingsText);
                            ps.setString(8, followUpText);
                            ps.executeUpdate();

                            try (ResultSet keys = ps.getGeneratedKeys()) {
                                if (!keys.next()) {
                                    throw new SQLException(
                                            "Could not retrieve the investigation ID.");
                                }
                                investigationId = keys.getInt(1);
                            }
                        }

                        // Record the investigation creation in the audit trail.
                        String auditSql =
                                "INSERT INTO investigation_audit " +
                                "(investigation_id, changed_by, action_type, " +
                                "old_value, new_value, change_note) " +
                                "VALUES (?, ?, ?, ?, ?, ?)";

                        try (PreparedStatement ps = con.prepareStatement(auditSql)) {
                            ps.setInt(1, investigationId);
                            ps.setInt(2, investigatorId);
                            ps.setString(3, "INVESTIGATION_CREATED");
                            ps.setNull(4, Types.VARCHAR);
                            ps.setString(5,
                                    "Decision=" + selectedDecision +
                                    "; Investigation Status=" + newStatus +
                                    "; Evidence=" + evidenceText +
                                    "; Findings=" + findingsText +
                                    "; Follow-up Action=" + followUpText);
                            ps.setString(6, noteText);
                            ps.executeUpdate();
                            System.out.println("AUDIT INSERT EXECUTED");
                        }

                        // Keep the alert status aligned with the investigation.
String update =
        "UPDATE fraud_alerts SET alert_status = ? " +
        "WHERE alert_id = ?";

                        try (PreparedStatement ps = con.prepareStatement(update)) {
                            ps.setString(1, newAlertStatus);
                            ps.setInt(2, alertId);

                            if (ps.executeUpdate() != 1) {
                                throw new SQLException("The fraud alert was not found.");
                            }
                        }

                        con.commit();

                    } catch (Exception ex) {
                        con.rollback();
                        throw ex;
                    }
                }
                return null;
            }

            @Override
            protected void done() {
                try {
                    get();
                    JOptionPane.showMessageDialog(dialog,
                        "Investigation saved successfully.");
                    dialog.dispose();
                    loadAlerts();
                } catch (Exception ex) {
                    showError("Could not save investigation", ex);
                    save.setEnabled(true);
                    save.setText("SAVE INVESTIGATION");
                }
            }
        };

        worker.execute();
    });

    dialog.setVisible(true);
}

    private JLabel makeFormLabel(String text) {
        JLabel label = new JLabel(text);
        label.setForeground(new Color(25, 35, 48));
        label.setFont(new Font("SansSerif", Font.BOLD, 13));
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        return label;
    }

    private JTextArea makeFormTextArea() {
        JTextArea area = new JTextArea(3, 40);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setBackground(Color.WHITE);
        area.setForeground(new Color(20, 25, 32));
        area.setCaretColor(Color.BLACK);
        area.setFont(new Font("SansSerif", Font.PLAIN, 13));
        area.setMargin(new Insets(6, 7, 6, 7));
        return area;
    }

    private void addFormField(JPanel form, JLabel label,
                              Component input, int inputHeight) {
        label.setBorder(BorderFactory.createEmptyBorder(5, 0, 5, 0));
        label.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        input.setPreferredSize(new Dimension(760, inputHeight));
        input.setMinimumSize(new Dimension(100, inputHeight));
        input.setMaximumSize(new Dimension(Integer.MAX_VALUE, inputHeight));
        if (input instanceof JComponent) {
            ((JComponent) input).setAlignmentX(Component.LEFT_ALIGNMENT);
        }
        form.add(label);
        form.add(input);
        form.add(Box.createVerticalStrut(4));
    }

    private String display(String value) {
    return value == null || value.trim().isEmpty()
        ? "Not provided" : value;
}


    private int getInvestigatorUserId(Connection con) throws SQLException {
    String sql =
        "SELECT user_id FROM users " +
        "WHERE username = ? " +
        "AND role = 'INVESTIGATOR' " +
        "AND account_status = 'ACTIVE'";

    try (PreparedStatement ps = con.prepareStatement(sql)) {
        ps.setString(1, loggedInUsername);

        try (ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                return rs.getInt("user_id");
            }
        }
    }

    throw new SQLException(
        "The logged-in investigator account is invalid or inactive.");
}


    private void showError(String title, Exception ex) {
        Throwable cause = ex;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }

        JOptionPane.showMessageDialog(this,
                title + ":\n" + cause.getMessage(),
                "SENTINEL Error", JOptionPane.ERROR_MESSAGE);
    }

    public static void main(String[] args) {
    SwingUtilities.invokeLater(() -> {
        JOptionPane.showMessageDialog(
                null,
                "Please launch SENTINEL through LoginFrame.",
                "Access Restricted",
                JOptionPane.WARNING_MESSAGE
        );
    });
}
}
