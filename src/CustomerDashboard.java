import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.text.NumberFormat;
import java.util.Locale;

public class CustomerDashboard extends JFrame {
    private static final long serialVersionUID = 1L;
    private static final Color PAGE = new Color(246, 249, 248);
    private static final Color MUTED = new Color(111, 128, 130);

    private final String username;
    private JLabel accountNumber;
    private JLabel balance;
    private JTable transactionTable;
    private DefaultTableModel transactionTableModel;
    private JLabel greetingLabel;
    private JButton refreshButton;
    private JButton overviewTab;
    private JButton securityTab;

    public CustomerDashboard(String username) {
        this.username = username;
        setTitle("SENTINEL | Customer Banking");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(980, 650));
        setSize(1240, 800);
        setLocationRelativeTo(null);
        setExtendedState(JFrame.MAXIMIZED_BOTH);

        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(PAGE);
        setContentPane(root);

        root.add(buildMainContent(), BorderLayout.CENTER);

        loadAccountData();
        loadTransactionHistory();
    }

    public void refreshDashboard() {
        loadAccountData();
        loadTransactionHistory();
    }



   
    private JPanel buildMainContent() {
        JPanel content = new JPanel(new BorderLayout(0, 0));
        content.setBackground(new Color(248, 247, 241));

        JPanel shell = new JPanel(new BorderLayout(0, 0));
        shell.setOpaque(false);
        shell.add(buildTopBar(), BorderLayout.NORTH);

        JPanel body = new JPanel(new BorderLayout(0, 22));
        body.setOpaque(false);
        body.setBorder(new EmptyBorder(26, 34, 22, 34));

        JPanel intro = new JPanel(new BorderLayout());
        intro.setOpaque(false);
        JPanel introText = new JPanel();
        introText.setOpaque(false);
        introText.setLayout(new BoxLayout(introText, BoxLayout.Y_AXIS));
        JLabel eyebrow = new JLabel("WELCOME BACK");
        eyebrow.setFont(new Font("SansSerif", Font.BOLD, 12));
        eyebrow.setForeground(new Color(58, 91, 91));
        JLabel pageTitle = new JLabel("Your account overview");
        pageTitle.setFont(new Font("Serif", Font.BOLD, 34));
        pageTitle.setForeground(new Color(22, 31, 29));
        greetingLabel = new JLabel("Here's a quick snapshot of your account and recent activity.");
        greetingLabel.setFont(new Font("Serif", Font.PLAIN, 17));
        greetingLabel.setForeground(new Color(73, 94, 111));
        introText.add(eyebrow);
        introText.add(Box.createVerticalStrut(6));
        introText.add(pageTitle);
        introText.add(Box.createVerticalStrut(5));
        introText.add(greetingLabel);
        JLabel quote = new JLabel("<html><div style='text-align:right'>“ Secure banking<br>for a safer tomorrow ”</div></html>");
        quote.setFont(new Font("Serif", Font.PLAIN, 15));
        quote.setForeground(new Color(54, 100, 95));
        intro.add(introText, BorderLayout.WEST);
        intro.add(quote, BorderLayout.EAST);

        JPanel hero = new JPanel(new GridLayout(1, 2, 16, 0));
        hero.setOpaque(false);
        hero.add(buildBalanceCard());
        hero.add(buildAccountCard());
        hero.setPreferredSize(new Dimension(1100, 210));

        JPanel transactionSection = buildTransactionSection();
        transactionSection.setPreferredSize(new Dimension(1100, 320));
        transactionSection.setMinimumSize(new Dimension(700, 280));
        body.add(intro, BorderLayout.NORTH);
        JPanel middle = new JPanel(new BorderLayout(0, 20));
        middle.setOpaque(false);
        middle.add(hero, BorderLayout.NORTH);
        middle.add(transactionSection, BorderLayout.CENTER);
        body.add(middle, BorderLayout.CENTER);

        JPanel footer = new JPanel(new BorderLayout());
        footer.setOpaque(false);
        footer.setBorder(new EmptyBorder(0, 34, 13, 34));
        JLabel footnote = new JLabel("SENTINEL   •   Academic demonstration using fictional data");
        footnote.setFont(new Font("SansSerif", Font.PLAIN, 11));
        footnote.setForeground(new Color(108, 126, 122));
        footer.add(footnote, BorderLayout.WEST);

        shell.add(body, BorderLayout.CENTER);
        content.add(shell, BorderLayout.CENTER);
        content.add(footer, BorderLayout.SOUTH);
        return content;
    }

    private JPanel buildTopBar() {
        JPanel bar = new JPanel(new BorderLayout(20, 0));
        bar.setBackground(new Color(255, 254, 250));
        bar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(230, 231, 222)),
                new EmptyBorder(14, 34, 14, 34)));

        JPanel brand = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        brand.setOpaque(false);
        JLabel mark = new JLabel("S", SwingConstants.CENTER);
        mark.setOpaque(true);
        mark.setBackground(new Color(0, 91, 73));
        mark.setForeground(Color.WHITE);
        mark.setFont(new Font("SansSerif", Font.BOLD, 25));
        mark.setPreferredSize(new Dimension(48, 48));
        mark.setBorder(BorderFactory.createLineBorder(new Color(0, 116, 92), 1));
        JPanel brandWords = new JPanel();
        brandWords.setOpaque(false);
        brandWords.setLayout(new BoxLayout(brandWords, BoxLayout.Y_AXIS));
        JLabel name = new JLabel("SENTINEL");
        name.setFont(new Font("SansSerif", Font.BOLD, 21));
        name.setForeground(new Color(0, 86, 69));
        JLabel sub = new JLabel("PERSONAL BANKING");
        sub.setFont(new Font("SansSerif", Font.BOLD, 9));
        sub.setForeground(new Color(70, 98, 111));
        brandWords.add(name);
        brandWords.add(Box.createVerticalStrut(2));
        brandWords.add(sub);
        brand.add(mark);
        brand.add(brandWords);

        JPanel nav = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 4));
        nav.setOpaque(false);
        overviewTab = topTab("⌂   Overview", true);
        overviewTab.addActionListener(e -> refreshDashboard());
        securityTab = topTab("♢   Security Centre", false);
        securityTab.addActionListener(e -> showSecurityCentre());
        nav.add(overviewTab);
        nav.add(securityTab);

        JPanel profile = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 3));
        profile.setOpaque(false);
        JLabel avatar = new JLabel(initials(username), SwingConstants.CENTER);
        avatar.setOpaque(true);
        avatar.setBackground(new Color(232, 245, 237));
        avatar.setForeground(new Color(0, 91, 73));
        avatar.setFont(new Font("Serif", Font.BOLD, 17));
        avatar.setPreferredSize(new Dimension(44, 44));
        avatar.setBorder(BorderFactory.createLineBorder(new Color(92, 173, 148), 1));
        JPanel who = new JPanel();
        who.setOpaque(false);
        who.setLayout(new BoxLayout(who, BoxLayout.Y_AXIS));
        JLabel user = new JLabel(safeDisplayName(username));
        user.setFont(new Font("Serif", Font.BOLD, 15));
        user.setForeground(new Color(0, 73, 61));
        JLabel account = new JLabel("Account  •••• 0001");
        account.setFont(new Font("Serif", Font.PLAIN, 13));
        account.setForeground(new Color(73, 94, 111));
        who.add(user);
        who.add(Box.createVerticalStrut(3));
        who.add(account);
        JButton logout = new JButton("Log out");
        logout.setFont(new Font("SansSerif", Font.BOLD, 11));
        logout.setFocusPainted(false);
        logout.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        logout.setBackground(new Color(255, 254, 250));
        logout.setForeground(new Color(0, 91, 73));
        logout.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        logout.addActionListener(e -> logout());
        profile.add(avatar);
        profile.add(who);
        profile.add(logout);

        bar.add(brand, BorderLayout.WEST);
        bar.add(nav, BorderLayout.CENTER);
        bar.add(profile, BorderLayout.EAST);
        return bar;
    }

    private JButton topTab(String text, boolean selected) {
        JButton button = new JButton(text);
        button.setFont(new Font("Serif", Font.BOLD, 15));
        button.setForeground(selected ? new Color(0, 83, 68) : new Color(29, 63, 78));
        button.setBackground(selected ? new Color(237, 244, 235) : new Color(255, 254, 250));
        button.setFocusPainted(false);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.setBorder(new EmptyBorder(15, 22, 15, 22));
        return button;
    }

    private String initials(String value) {
        if (value == null || value.trim().isEmpty()) return "CU";
        String cleaned = value.trim();
        return cleaned.substring(0, Math.min(2, cleaned.length())).toUpperCase(Locale.ROOT);
    }

        private JPanel buildBalanceCard() {
        JPanel card = new JPanel(new BorderLayout(0, 14));
        card.setBackground(new Color(0, 79, 64));
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(0, 103, 82)),
                new EmptyBorder(27, 30, 24, 30)));

        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        JLabel title = new JLabel("T O T A L   A V A I L A B L E   B A L A N C E");
        title.setFont(new Font("SansSerif", Font.BOLD, 11));
        title.setForeground(new Color(204, 232, 219));
        JButton more = new JButton("•••");
        more.setFont(new Font("SansSerif", Font.BOLD, 14));
        more.setForeground(Color.WHITE);
        more.setBackground(new Color(35, 111, 94));
        more.setFocusPainted(false);
        more.setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10));
        top.add(title, BorderLayout.WEST);
        top.add(more, BorderLayout.EAST);

        balance = new JLabel("INR --");
        balance.setFont(new Font("Serif", Font.BOLD, 48));
        balance.setForeground(Color.WHITE);

        accountNumber = new JLabel("Account  •••• 0001");
        accountNumber.setFont(new Font("Serif", Font.PLAIN, 16));
        accountNumber.setForeground(new Color(229, 242, 232));

        JPanel lower = new JPanel(new BorderLayout());
        lower.setOpaque(false);
        lower.add(accountNumber, BorderLayout.WEST);
        JLabel accountType = new JLabel("▣  SAVINGS ACCOUNT");
        accountType.setFont(new Font("SansSerif", Font.BOLD, 11));
        accountType.setForeground(new Color(226, 244, 232));
        accountType.setBorder(new EmptyBorder(9, 13, 9, 13));
        accountType.setOpaque(true);
        accountType.setBackground(new Color(0, 103, 82));
        lower.add(accountType, BorderLayout.EAST);

        JPanel middle = new JPanel();
        middle.setOpaque(false);
        middle.setLayout(new BoxLayout(middle, BoxLayout.Y_AXIS));
        middle.add(Box.createVerticalStrut(2));
        middle.add(balance);
        middle.add(Box.createVerticalStrut(15));
        middle.add(lower);

        card.add(top, BorderLayout.NORTH);
        card.add(middle, BorderLayout.CENTER);
        card.setPreferredSize(new Dimension(520, 235));
        return card;
    }

        private JPanel buildAccountCard() {
        JPanel card = new JPanel(new BorderLayout(0, 18));
        card.setBackground(new Color(255, 254, 250));
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(225, 233, 224)),
                new EmptyBorder(24, 24, 18, 24)));

        JPanel left = new JPanel(new BorderLayout(14, 0));
        left.setOpaque(false);

        JLabel plane = new JLabel("➤", SwingConstants.CENTER);
        plane.setFont(new Font("SansSerif", Font.BOLD, 34));
        plane.setForeground(new Color(0, 82, 67));
        plane.setOpaque(true);
        plane.setBackground(new Color(226, 244, 231));
        plane.setPreferredSize(new Dimension(76, 76));

        JPanel text = new JPanel();
        text.setOpaque(false);
        text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
        JLabel label = new JLabel("MONEY TRANSFER");
        label.setFont(new Font("SansSerif", Font.BOLD, 11));
        label.setForeground(new Color(65, 91, 111));
        JLabel title = new JLabel("Send money securely");
        title.setFont(new Font("Serif", Font.BOLD, 22));
        title.setForeground(new Color(17, 29, 28));
        JLabel desc = new JLabel("<html>Fast, safe and reliable transfers to<br>accounts within SENTINEL.</html>");
        desc.setFont(new Font("Serif", Font.PLAIN, 14));
        desc.setForeground(new Color(73, 94, 111));
        text.add(label);
        text.add(Box.createVerticalStrut(5));
        text.add(title);
        text.add(Box.createVerticalStrut(5));
        text.add(desc);

        left.add(plane, BorderLayout.WEST);
        left.add(text, BorderLayout.CENTER);

        JButton transfer = new JButton("Send Money  ->");
        transfer.setFont(new Font("Serif", Font.BOLD, 14));
        transfer.setForeground(Color.WHITE);
        transfer.setBackground(new Color(0, 79, 64));
        transfer.setFocusPainted(false);
        transfer.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        transfer.setBorder(new EmptyBorder(15, 17, 15, 17));
        transfer.addActionListener(e -> openTransferWindow());

        JPanel top = new JPanel(new BorderLayout(14, 0));
        top.setOpaque(false);
        top.add(left, BorderLayout.CENTER);
        top.add(transfer, BorderLayout.EAST);

        JPanel benefits = new JPanel(new GridLayout(1, 3, 8, 0));
        benefits.setOpaque(false);
        benefits.add(benefit("●", "Secure transfer"));
        benefits.add(benefit("+", "Real-time processing"));
        benefits.add(benefit("✓", "Your data is protected"));

        card.add(top, BorderLayout.CENTER);
        card.add(benefits, BorderLayout.SOUTH);
        card.setPreferredSize(new Dimension(520, 210));
        return card;
    }

    private JLabel benefit(String icon, String text) {
        JLabel label = new JLabel(icon + "  " + text);
        label.setFont(new Font("Serif", Font.PLAIN, 12));
        label.setForeground(new Color(0, 82, 67));
        label.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, new Color(225, 235, 226)));
        return label;
    }

        private JPanel buildTransactionSection() {
    JPanel section = new JPanel(new BorderLayout(0, 14));
    section.setBackground(new Color(255, 254, 250));
    section.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(228, 230, 221)),
            new EmptyBorder(20, 22, 22, 22)
    ));

    // Section heading
    JPanel titleRow = new JPanel(new BorderLayout(12, 0));
    titleRow.setOpaque(false);

    JPanel titleStack = new JPanel(new BorderLayout(12, 0));
    titleStack.setOpaque(false);

    JLabel icon = new JLabel("\u25C8", SwingConstants.CENTER);
    icon.setOpaque(true);
    icon.setBackground(new Color(232, 243, 232));
    icon.setForeground(new Color(0, 91, 73));
    icon.setFont(new Font("SansSerif", Font.BOLD, 22));
    icon.setPreferredSize(new Dimension(48, 48));

    JPanel titleText = new JPanel();
    titleText.setOpaque(false);
    titleText.setLayout(new BoxLayout(titleText, BoxLayout.Y_AXIS));

    JLabel title = new JLabel("Recent transactions");
    title.setFont(new Font("Serif", Font.BOLD, 24));
    title.setForeground(new Color(20, 30, 29));

    JLabel subtitle = new JLabel(
            "A quick look at your latest account activity");
    subtitle.setFont(new Font("Serif", Font.PLAIN, 15));
    subtitle.setForeground(new Color(73, 94, 111));

    titleText.add(title);
    titleText.add(Box.createVerticalStrut(2));
    titleText.add(subtitle);

    titleStack.add(icon, BorderLayout.WEST);
    titleStack.add(titleText, BorderLayout.CENTER);

    refreshButton = new JButton("\u27F3");
    refreshButton.setFont(new Font("SansSerif", Font.BOLD, 23));
    refreshButton.setForeground(new Color(0, 91, 73));
    refreshButton.setBackground(new Color(225, 243, 229));
    refreshButton.setFocusPainted(false);
    refreshButton.setCursor(
            Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    refreshButton.setBorder(new EmptyBorder(8, 14, 8, 14));
    refreshButton.setToolTipText("Refresh transaction activity");
    refreshButton.addActionListener(e -> refreshDashboard());

    titleRow.add(titleStack, BorderLayout.WEST);
    titleRow.add(refreshButton, BorderLayout.EAST);

    // Transaction table
    String[] columns = {
        "ID", "DATE & TIME", "TYPE", "AMOUNT (\u20B9)", "STATUS"
    };

    transactionTableModel = new DefaultTableModel(columns, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };

    transactionTable = new JTable(transactionTableModel);
    transactionTable.setRowHeight(36);
    transactionTable.setFont(new Font("Serif", Font.PLAIN, 15));
    transactionTable.setForeground(new Color(29, 54, 83));
    transactionTable.setBackground(new Color(255, 254, 250));
    transactionTable.setSelectionBackground(new Color(229, 244, 233));
    transactionTable.setSelectionForeground(new Color(0, 72, 60));
    transactionTable.setGridColor(new Color(235, 235, 226));
    transactionTable.setShowVerticalLines(false);
    transactionTable.setShowHorizontalLines(true);
    transactionTable.setIntercellSpacing(new Dimension(0, 0));
    transactionTable.setFillsViewportHeight(true);
    transactionTable.setAutoCreateRowSorter(true);
    transactionTable.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);

    JTableHeader header = transactionTable.getTableHeader();
    header.setFont(new Font("SansSerif", Font.BOLD, 11));
    header.setForeground(new Color(63, 87, 108));
    header.setBackground(new Color(239, 243, 235));
    header.setPreferredSize(new Dimension(100, 34));
    header.setReorderingAllowed(false);

    // Cell alignment
    DefaultTableCellRenderer leftRenderer =
            new DefaultTableCellRenderer();
    leftRenderer.setBorder(new EmptyBorder(0, 10, 0, 10));

    transactionTable.getColumnModel().getColumn(0)
            .setCellRenderer(leftRenderer);
    transactionTable.getColumnModel().getColumn(2)
            .setCellRenderer(leftRenderer);
    transactionTable.getColumnModel().getColumn(4)
            .setCellRenderer(new StatusRenderer());

    // Scroll area sized for four visible rows
    JScrollPane scrollPane = new JScrollPane(transactionTable);
    scrollPane.setBorder(BorderFactory.createLineBorder(
            new Color(231, 233, 225)));
    scrollPane.getViewport().setBackground(
            new Color(255, 254, 250));
    scrollPane.getVerticalScrollBar().setPreferredSize(
            new Dimension(8, 0));
    scrollPane.setPreferredSize(new Dimension(900, 190));
    scrollPane.setMinimumSize(new Dimension(600, 190));
    transactionTable.setRowHeight(36);

    section.add(titleRow, BorderLayout.NORTH);
    section.add(scrollPane, BorderLayout.CENTER);

    return section;
}

        private static class StatusRenderer extends DefaultTableCellRenderer {
            private static final long serialVersionUID = 1L;
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean isSelected, boolean hasFocus, int row, int column) {
            JLabel label = (JLabel) super.getTableCellRendererComponent(
                    table, value, isSelected, hasFocus, row, column);
            label.setBorder(new EmptyBorder(0, 10, 0, 10));
            label.setFont(new Font("SansSerif", Font.BOLD, 11));
            String status = value == null ? "" : value.toString().toUpperCase(Locale.ROOT);
            if (!isSelected && (status.contains("COMPLETED") || status.contains("SUCCESS"))) {
                label.setForeground(new Color(0, 123, 91));
            } else if (!isSelected && (status.contains("PENDING") || status.contains("REVIEW"))) {
                label.setForeground(new Color(168, 111, 0));
            } else if (!isSelected && (status.contains("FAILED") || status.contains("REJECTED"))) {
                label.setForeground(new Color(184, 60, 60));
            } else if (!isSelected) {
                label.setForeground(MUTED);
            }
            return label;
        }
    }

    private void openTransferWindow() {
        TransferFrame transferFrame = new TransferFrame(username);
        transferFrame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                refreshDashboard();
            }

            @Override
            public void windowClosed(WindowEvent e) {
                refreshDashboard();
            }
        });
        transferFrame.setVisible(true);
    }

    private void showSecurityCentre() {
        String message =
                "SENTINEL SECURITY CENTRE\n\n"
                + "DEMO MONITORING STATUS\n"
                + "Fraud detection is available in the investigator dashboard.\n\n"
                + "SECURITY TIPS\n"
                + "• Never share your password.\n"
                + "• Review transactions regularly.\n"
                + "• Report unfamiliar transfers to the investigator.\n"
                + "• Verify recipient details before transferring money.\n\n"
                + "Note: This is an academic demonstration, not a real banking security service.";
        JOptionPane.showMessageDialog(this, message,
                "SENTINEL | Security Centre", JOptionPane.INFORMATION_MESSAGE);
    }

    private void logout() {
        int choice = JOptionPane.showConfirmDialog(
                this, "Are you sure you want to log out?",
                "SENTINEL | Log out", JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE);
        if (choice == JOptionPane.YES_OPTION) {
            dispose();
            LoginFrame loginFrame = new LoginFrame();
            loginFrame.setVisible(true);
        }
    }

    private String safeDisplayName(String value) {
        if (value == null || value.trim().isEmpty()) {
            return "there";
        }
        String cleaned = value.trim();
        return cleaned.length() > 24 ? cleaned.substring(0, 24) : cleaned;
    }

    private void loadAccountData() {
        String url = System.getenv("SENTINEL_DB_URL");
        String dbUsername = System.getenv("SENTINEL_DB_USER");
        String dbPassword = System.getenv("SENTINEL_DB_PASSWORD");

        if (url == null || dbUsername == null || dbPassword == null
                || !url.matches("(?i)^jdbc:mysql://localhost:3306/sentinel_test_db(?:\\?.*)?$")) {
            System.out.println("Database connection blocked: unapproved database URL.");
            return;
        }

        String sql = "SELECT ba.account_number, ba.balance "
                + "FROM cd \"D:\\Documents\\SENTINEL\\SENTINEL_UI_EDITION\"\r\n" + //
                                        "\r\n" + //
                                        "Get-Content \"src\\CustomerDashboard.java\" | Select-Object -Skip 544 -First 26 ba "
                + "JOIN customers c ON ba.customer_id = c.customer_id "
                + "JOIN users u ON c.user_id = u.user_id "
                + "WHERE u.username = ? "
                + "AND ba.account_status = 'ACTIVE' "
                + "ORDER BY ba.account_id LIMIT 1";

        try (Connection connection = DriverManager.getConnection(url, dbUsername, dbPassword);
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, username);
            try (ResultSet result = statement.executeQuery()) {
                if (result.next()) {
                    String accNo = result.getString("account_number");
                    String maskedAccount = "•••• "
                            + accNo.substring(Math.max(0, accNo.length() - 4));
                    accountNumber.setText("Account: " + maskedAccount);

                    NumberFormat currency = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("en-IN"));
                    balance.setText(currency.format(result.getBigDecimal("balance")));
                } else {
                    accountNumber.setText("No account found");
                    balance.setText("₹ 0.00");
                }
            }
        } catch (Exception ex) {
            System.out.println("Could not load account data: " + ex.getMessage());
        }
    }

    private void loadTransactionHistory() {
        String url = System.getenv("SENTINEL_DB_URL");
        String dbUsername = System.getenv("SENTINEL_DB_USER");
        String dbPassword = System.getenv("SENTINEL_DB_PASSWORD");

        if (url == null || dbUsername == null || dbPassword == null
                || !url.matches("(?i)^jdbc:mysql://localhost:3306/sentinel_test_db(?:\\?.*)?$")) {
            System.out.println("Database connection blocked: unapproved database URL.");
            return;
        }

        String sql = "SELECT t.transaction_id, t.transaction_time, t.amount, "
                + "t.transaction_source, t.payment_status, "
                + "CASE "
                + "WHEN t.sender_account_id = t.receiver_account_id THEN 'SELF' "
                + "WHEN sender.customer_id = c.customer_id THEN 'SENT' "
                + "ELSE 'RECEIVED' END AS transaction_type "
                + "FROM transactions t "
                + "JOIN customers c ON c.user_id = "
                + "(SELECT u.user_id FROM users u WHERE u.username = ?) "
                + "JOIN bank_accounts sender ON sender.account_id = t.sender_account_id "
                + "JOIN bank_accounts receiver ON receiver.account_id = t.receiver_account_id "
                + "WHERE sender.customer_id = c.customer_id "
                + "OR receiver.customer_id = c.customer_id "
                + "ORDER BY t.transaction_time DESC";

        try (Connection connection = DriverManager.getConnection(url, dbUsername, dbPassword);
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, username);
            try (ResultSet result = statement.executeQuery()) {
                transactionTableModel.setRowCount(0);
                while (result.next()) {
                    transactionTableModel.addRow(new Object[]{
                        result.getInt("transaction_id"),
                        result.getTimestamp("transaction_time"),
                        result.getString("transaction_type"),
                        result.getBigDecimal("amount"),
                        result.getString("payment_status")
                    });
                }
                if (transactionTableModel.getRowCount() == 0) {
                    transactionTableModel.addRow(
                            new Object[]{"-", "-", "-", "-", "No transactions"});
                }
            }
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this,
                    "Could not load transactions. Check the database connection.",
                    "Transaction History", JOptionPane.ERROR_MESSAGE);
            e.printStackTrace();
        }
    }
}