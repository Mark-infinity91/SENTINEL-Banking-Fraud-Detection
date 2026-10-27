
import javax.swing.*;
import java.util.concurrent.ExecutionException;
import java.awt.*;
import java.sql.Connection;
import java.sql.DriverManager;

public class TransferFrame extends JFrame {
private static final long serialVersionUID = 1L;
    private String pendingTransferReference;
    private String pendingTransferReceiver;
    private java.math.BigDecimal pendingTransferAmount;
    private boolean transferOutcomeUnknown = false;
    private boolean startupRecoveryReady = false;
    private String username;

    private static final String DB_URL =
            System.getenv("SENTINEL_DB_URL");
    private static final String DB_USER =
            System.getenv("SENTINEL_DB_USER");
    private static final String DB_PASSWORD =
            System.getenv("SENTINEL_DB_PASSWORD");
    private JTextField receiverField;
    private JTextField amountField;
    private JButton transferButton;
    private JLabel balanceLabel;

    public TransferFrame(String username) {
        super("SENTINEL | Money Transfer");
        this.username=username;
        setSize(450, 350);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setLocationRelativeTo(null);

        JLabel heading = new JLabel("TRANSFER MONEY");
        heading.setFont(new Font("Arial", Font.BOLD, 22));
        
        balanceLabel = new JLabel("Available Balance: Loading...");
        balanceLabel.setFont(new Font("Arial", Font.BOLD, 14));


        JLabel receiverLabel = new JLabel("Receiver Account Number:");
        receiverField = new JTextField(18);

        JLabel amountLabel = new JLabel("Amount (₹):");
        amountField = new JTextField(18);

        transferButton = new JButton("Transfer");
        transferButton.setEnabled(false);
        JButton cancelButton = new JButton("Cancel");

        transferButton.addActionListener(e -> {
            transferButton.setEnabled(false);
            transferMoney();
        });
        cancelButton.addActionListener(e -> dispose());

        JPanel formPanel = new JPanel(new GridLayout(4, 1, 8, 8));
        formPanel.add(receiverLabel);
        formPanel.add(receiverField);
        formPanel.add(amountLabel);
        formPanel.add(amountField);

        JPanel buttonPanel = new JPanel();
        buttonPanel.add(transferButton);
        buttonPanel.add(cancelButton);

        setLayout(new BorderLayout(15, 15));
        JPanel topPanel = new JPanel(new GridLayout(2, 1, 5, 5));
        topPanel.add(heading);
        topPanel.add(balanceLabel);
        add(topPanel, BorderLayout.NORTH);
        add(formPanel, BorderLayout.CENTER);
        add(buttonPanel, BorderLayout.SOUTH);
        loadAvailableBalance();
        checkStartupRecovery();
    }



    private void checkStartupRecovery() {
    transferButton.setEnabled(false);

    new SwingWorker<Boolean, Void>() {
        @Override
        protected Boolean doInBackground() throws Exception {
            int senderId;

            String sql =
                    "SELECT ba.account_id " +
                    "FROM bank_accounts ba " +
                    "JOIN customers c ON ba.customer_id = c.customer_id " +
                    "JOIN users u ON c.user_id = u.user_id " +
                    "WHERE u.username = ? " +
                    "AND ba.account_status = 'ACTIVE' " +
                    "ORDER BY ba.account_id LIMIT 1";

            try (Connection connection = getConnection();
                 java.sql.PreparedStatement ps =
                         connection.prepareStatement(sql)) {

                ps.setString(1, username);

                try (java.sql.ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        throw new java.sql.SQLException(
                                "No active sender account was found."
                        );
                    }

                    senderId = rs.getInt("account_id");
                }
            }

            return !hasUnresolvedRecovery(senderId);
        }

        @Override
        protected void done() {
            try {
                boolean recovered = get();

                if (recovered) {
                    startupRecoveryReady = true;
                    transferButton.setEnabled(true);
                } else {
                    startupRecoveryReady = false;
                    transferButton.setEnabled(false);

                    JOptionPane.showMessageDialog(
                            TransferFrame.this,
                            "A previous transfer needs reconciliation. " +
                            "New transfers are disabled. Contact an " +
                            "administrator with the transfer reference.",
                            "Recovery Required",
                            JOptionPane.WARNING_MESSAGE
                    );
                }
            } catch (Exception ex) {
                startupRecoveryReady = false;
                transferButton.setEnabled(false);

                JOptionPane.showMessageDialog(
                        TransferFrame.this,
                        "Could not verify previous transfers. " +
                        "Transfers remain disabled. Check the database " +
                        "connection and contact an administrator.",
                        "Recovery Check Failed",
                        JOptionPane.ERROR_MESSAGE
                );
            }
        }
    }.execute();
}



    private void transferMoney() {
    String receiverNumber = receiverField.getText().trim();
    String amountText = amountField.getText().trim();

    if (receiverNumber.isEmpty() || amountText.isEmpty()) {
        JOptionPane.showMessageDialog(
                this, "Please enter the receiver account and amount."
        );
        resetTransferButton();
        return;
    }

    java.math.BigDecimal amount;

    try {
        amount = new java.math.BigDecimal(amountText);

        if (amount.compareTo(java.math.BigDecimal.ZERO) <= 0) {
            JOptionPane.showMessageDialog(
                    this, "Amount must be greater than zero."
            );
            resetTransferButton();
            return;
        }

        if (amount.scale() > 2) {
            JOptionPane.showMessageDialog(
                    this, "Amount can have at most two decimal places."
            );
            resetTransferButton();
            return;
        }
    } catch (NumberFormatException ex) {
        JOptionPane.showMessageDialog(
                this, "Please enter a valid amount, such as 100 or 100.50."
        );
        resetTransferButton();
        return;
    }

if (transferOutcomeUnknown) {
    JOptionPane.showMessageDialog(
            this,
            "A previous transfer is still unconfirmed.\n" +
            "Do not submit another transfer until its status is verified.",
            "Transfer Verification Pending",
            JOptionPane.WARNING_MESSAGE
    );
    resetTransferButton();
    return;
}

    int confirm = JOptionPane.showConfirmDialog(
            this,
            "Transfer ₹" + amount + " to account " + receiverNumber + "?",
            "Confirm Transfer",
            JOptionPane.YES_NO_OPTION
    );

    if (confirm != JOptionPane.YES_OPTION) {
        resetTransferButton();
        return;
    }

    final java.math.BigDecimal transferAmount = amount;
    final String transferReceiver = receiverNumber;

    if (pendingTransferReference == null
            || !transferReceiver.equals(pendingTransferReceiver)
            || transferAmount.compareTo(pendingTransferAmount) != 0) {

        pendingTransferReference = java.util.UUID.randomUUID().toString();
        pendingTransferReceiver = transferReceiver;
        pendingTransferAmount = transferAmount;
    }

    final String transferReference = pendingTransferReference;

    new SwingWorker<Void, Void>() {
        private String errorMessage;
        private boolean transferCommitted;
        private boolean commitOutcomeUnknown;
        private boolean recoveryRecordCreated = false;
        private boolean recoveryRecordRejected = false;

        @Override
        protected Void doInBackground() {
            try (Connection connection = getConnection()) {
                connection.setAutoCommit(false);

                try {

                    int senderId;
                    int receiverId;
                    java.math.BigDecimal senderBalance;

                    // Find the logged-in customer's active sender account.
                    String senderSql =
                            "SELECT ba.account_id " +
                            "FROM bank_accounts ba " +
                            "JOIN customers c ON ba.customer_id = c.customer_id " +
                            "JOIN users u ON c.user_id = u.user_id " +
                            "WHERE u.username = ? " +
                            "AND ba.account_status = 'ACTIVE' " +
                            "ORDER BY ba.account_id LIMIT 1";

                    try (java.sql.PreparedStatement ps =
                                connection.prepareStatement(senderSql)) {
                        ps.setString(1, username);

                        try (java.sql.ResultSet rs = ps.executeQuery()) {
                            if (!rs.next()) {
                                throw new Exception(
                                        "Your active sender account was not found."
                                );
                            }

                            senderId = rs.getInt("account_id");
                        }
                    }

                    // Find the active receiver account ID.
                    String receiverSql =
                            "SELECT account_id FROM bank_accounts " +
                            "WHERE account_number = ? " +
                            "AND account_status = 'ACTIVE'";

                    try (java.sql.PreparedStatement ps =
                                connection.prepareStatement(receiverSql)) {
                        ps.setString(1, receiverNumber);

                        try (java.sql.ResultSet rs = ps.executeQuery()) {
                            if (!rs.next()) {
                                throw new Exception(
                                        "Receiver account was not found or is inactive."
                                );
                            }

                            receiverId = rs.getInt("account_id");
                        }
                    }

                    // Lock both accounts in ascending account_id order.
                    int firstId = Math.min(senderId, receiverId);
                    int secondId = Math.max(senderId, receiverId);

                    for (int accountId : new int[]{firstId, secondId}) {
                        String lockSql =
                                "SELECT account_id FROM bank_accounts " +
                                "WHERE account_id = ? " +
                                "AND account_status = 'ACTIVE' FOR UPDATE";

                        try (java.sql.PreparedStatement ps =
                                    connection.prepareStatement(lockSql)) {
                            ps.setInt(1, accountId);

                            try (java.sql.ResultSet rs = ps.executeQuery()) {
                                if (!rs.next()) {
                                    throw new Exception(
                                            "An account was not found or is inactive."
                                    );
                                }
                            }
                        }
                    }

                    if (hasUnresolvedRecovery(senderId)) {
                        throw new Exception(
                                "A previous transfer is still unresolved. " +
                                "Do not submit another transfer. Contact an administrator " +
                                "to reconcile the reference."
                        );
                    }
                    // Read the sender's balance after acquiring the locks.
                    String balanceSql =
                            "SELECT balance FROM bank_accounts " +
                            "WHERE account_id = ? AND account_status = 'ACTIVE'";

                    try (java.sql.PreparedStatement ps =
                                connection.prepareStatement(balanceSql)) {
                        ps.setInt(1, senderId);

                        try (java.sql.ResultSet rs = ps.executeQuery()) {
                            if (!rs.next()) {
                                throw new Exception(
                                        "Your active sender account was not found."
                                );
                            }

                            senderBalance = rs.getBigDecimal("balance");
                        }
                    }

                    if (senderId == receiverId) {
                        throw new Exception(
                                "You cannot transfer money to the same account."
                        );
                    }

                    if (senderBalance.compareTo(transferAmount) < 0) {
                        throw new Exception("Insufficient balance.");
                    }

                    try {
                        createRecoveryRecord(
                                transferReference,
                                senderId,
                                receiverId,
                                transferAmount
                        );
                        recoveryRecordCreated = true;
                    } catch (Exception ex) {
                        throw new Exception(
                                "Could not create the recovery record. " +
                                "The transfer was stopped before any balance update.",
                                ex
                        );
                    }

                    
                    String debitSql =
                            "UPDATE bank_accounts " +
                            "SET balance = balance - ? " +
                            "WHERE account_id = ? " +
                            "AND balance >= ? " +
                            "AND account_status = 'ACTIVE'";

                    try (java.sql.PreparedStatement ps =
                                connection.prepareStatement(debitSql)) {
                        ps.setBigDecimal(1, transferAmount);
                        ps.setInt(2, senderId);
                        ps.setBigDecimal(3, transferAmount);

                        if (ps.executeUpdate() != 1) {
                            throw new Exception(
                                    "Debit failed. Check your balance and account status."
                            );
                        }
                    }



                    String creditSql =
                            "UPDATE bank_accounts SET balance = balance + ? " +
                            "WHERE account_id = ? AND account_status = 'ACTIVE'";

                    try (java.sql.PreparedStatement ps =
                                connection.prepareStatement(creditSql)) {

                        ps.setBigDecimal(1, transferAmount);
                        ps.setInt(2, receiverId);

                        int rowsUpdated = ps.executeUpdate();

                        if (rowsUpdated != 1) {
                            throw new Exception(
                                    "Receiver credit failed. The receiver account " +
                                    "may be inactive or unavailable."
                            );
                        }
                    }




                    String transactionSql =
                            "INSERT INTO transactions " +
                            "(sender_account_id, receiver_account_id, amount, " +
                            "transaction_source, payment_status, transfer_reference) " +
                            "VALUES (?, ?, ?, 'MANUAL', 'COMPLETED', ?)";

                    try (java.sql.PreparedStatement ps =
                                connection.prepareStatement(transactionSql)) {
                        ps.setInt(1, senderId);
                        ps.setInt(2, receiverId);
                        ps.setBigDecimal(3, transferAmount);
                        ps.setString(4, transferReference);
                        ps.executeUpdate();
                    }

                    try {
                        connection.commit();
                        transferCommitted = true;

                        try {
                            updateRecoveryStatus(transferReference, "COMPLETED");
                        } catch (Exception statusEx) {
                            // The transaction is committed. Do not retry it because
                            // updating the separate recovery record failed.
                        }

                    } catch (java.sql.SQLException commitEx) {
                        commitOutcomeUnknown = true;
                        transferOutcomeUnknown = true;
                        errorMessage =
                                "The transfer outcome is unknown. Do not submit it again yet. " +
                                "Reference: " + transferReference;
                        throw commitEx;
                    }



                } catch (Exception ex) {
                    if (!commitOutcomeUnknown) {
                        boolean rollbackConfirmed = false;

                        try {
                            connection.rollback();
                            rollbackConfirmed = true;
                        } catch (java.sql.SQLException rollbackEx) {
                            ex.addSuppressed(rollbackEx);
                        }

                        if (recoveryRecordCreated && rollbackConfirmed) {
                            try {
                                updateRecoveryStatus(transferReference, "REJECTED");
                                recoveryRecordRejected = true;
                            } catch (Exception statusEx) {
                                ex.addSuppressed(statusEx);
                            }
                        }
                    }

                    throw ex;
                }



            } catch (Exception ex) {
                if (errorMessage == null) {
                    errorMessage = ex.getMessage();
                }
            }

            return null;
        }

        @Override
        protected void done() {            
            try {
                try {
                    get();
                } catch (ExecutionException workerEx) {
                    if (!commitOutcomeUnknown) {
                        throw workerEx;
                    }
                    // Uncertain commit: continue to database verification.
                }

                if (commitOutcomeUnknown) {
                    try {
                        boolean verified = verifyTransferCommitted(transferReference);



                        if (verified) {
                            transferCommitted = true;

                            boolean recoveryUpdated = false;

                            try {
                                updateRecoveryStatus(transferReference, "COMPLETED");
                                recoveryUpdated = true;
                            } catch (Exception statusEx) {
                                // The transfer is committed, but recovery reconciliation failed.
                                // Keep the unresolved state until recovery can be confirmed.
                            }

                            if (recoveryUpdated) {
                                transferOutcomeUnknown = false;
                                errorMessage = null;
                                pendingTransferReference = null;
                                pendingTransferReceiver = null;
                                pendingTransferAmount = null;
                            } else {
                                transferOutcomeUnknown = true;
                                errorMessage =
                                        "The transfer was committed, but recovery confirmation failed. " +
                                        "DO NOT submit this transfer again.\nReference: " +
                                        transferReference;
                            }
                        }
                        else {
                            errorMessage =
                                    "Transfer status is still unconfirmed. " +
                                    "DO NOT submit this transfer again.\nReference: " +
                                    transferReference;
                        }

                    } catch (Exception verificationEx) {
                        errorMessage =
                                "Could not verify the transfer status. " +
                                "DO NOT submit this transfer again.\nReference: " +
                                transferReference;
                    }
                }

                if (transferCommitted && errorMessage == null) {
                    JOptionPane.showMessageDialog(
                            TransferFrame.this,
                            "Transfer successful!\nAmount: ₹" + transferAmount +
                            "\nReceiver: " + receiverNumber
                    );
                    pendingTransferReference = null;
                    pendingTransferReceiver = null;
                    pendingTransferAmount = null;

                    java.awt.Window[] windows = java.awt.Window.getWindows();
                    for (java.awt.Window window : windows) {
                        if (window instanceof CustomerDashboard) {
                            ((CustomerDashboard) window).refreshDashboard();
                        }
                    }

                    loadAvailableBalance();

                    receiverField.setText("");
                    amountField.setText("");
                    
                } else {
                    if (recoveryRecordRejected) {
                        pendingTransferReference = null;
                        pendingTransferReceiver = null;
                        pendingTransferAmount = null;
                    }

                    JOptionPane.showMessageDialog(
                            TransferFrame.this,
                            "Transfer failed:\n" + errorMessage,
                            "Transfer Error",
                            JOptionPane.ERROR_MESSAGE
                    );
                }
            } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();

                        if (commitOutcomeUnknown) {
                            transferOutcomeUnknown = true;
                            JOptionPane.showMessageDialog(
                                    TransferFrame.this,
                                    "Transfer outcome is unknown. DO NOT submit it again.\n" +
                                    "Reference: " + transferReference,
                                    "Transfer Status Unknown",
                                    JOptionPane.WARNING_MESSAGE
                            );
                        } else {
                            JOptionPane.showMessageDialog(
                                    TransferFrame.this,
                                    "Transfer processing was interrupted. Check its status " +
                                    "before attempting another transfer.",
                                    "Transfer Interrupted",
                                    JOptionPane.WARNING_MESSAGE
                            );
                        }
            } catch (ExecutionException ex) {
                JOptionPane.showMessageDialog(
                        TransferFrame.this,
                        "Unexpected transfer error: " + ex.getCause().getMessage(),
                        "Transfer Error",
                        JOptionPane.ERROR_MESSAGE
                );
            } finally {
                resetTransferButton();
            }
        }
    }.execute();
}

    private void resetTransferButton() {
        transferButton.setEnabled(startupRecoveryReady);
    }

    private void loadAvailableBalance() {
        balanceLabel.setText("Available Balance: Loading...");

        new SwingWorker<java.math.BigDecimal, Void>() {
            @Override
            protected java.math.BigDecimal doInBackground() throws Exception {
                String sql =
                        "SELECT ba.balance " +
                        "FROM bank_accounts ba " +
                        "JOIN customers c ON ba.customer_id = c.customer_id " +
                        "JOIN users u ON c.user_id = u.user_id " +
                        "WHERE u.username = ? " +
                        "AND ba.account_status = 'ACTIVE' " +
                        "ORDER BY ba.account_id LIMIT 1";

                try (Connection connection = getConnection();
                    java.sql.PreparedStatement ps =
                            connection.prepareStatement(sql)) {

                    ps.setString(1, username);

                    try (java.sql.ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            return rs.getBigDecimal("balance");
                        }
                    }
                }

                return null;
            }

            @Override
            protected void done() {
                try {
                    java.math.BigDecimal balance = get();

                    if (balance == null) {
                        balanceLabel.setText("Available Balance: Not found");
                    } else {
                        balanceLabel.setText(
                                "Available Balance: ₹" +
                                balance.setScale(2, java.math.RoundingMode.HALF_UP)
                        );
                    }
                } catch (Exception ex) {
                    balanceLabel.setText("Available Balance: Could not load");
                }
            }
        }.execute();
    }



    private static Connection getConnection() throws Exception {
        if (DB_URL == null
                || !DB_URL.matches(
                    "(?i)^jdbc:mysql://localhost:3306/sentinel_test_db(?:\\?.*)?$")
                || DB_USER == null
                || DB_PASSWORD == null) {
            throw new IllegalStateException(
                    "Connection blocked: only the approved local test database is allowed.");
        }

        return DriverManager.getConnection(
                DB_URL, DB_USER, DB_PASSWORD
        );
    }



    private static boolean verifyTransferCommitted(String transferReference)
        throws Exception {

    String sql =
            "SELECT payment_status FROM transactions " +
            "WHERE transfer_reference = ?";

    try (Connection connection = getConnection();
         java.sql.PreparedStatement ps =
                 connection.prepareStatement(sql)) {

        ps.setString(1, transferReference);

        try (java.sql.ResultSet rs = ps.executeQuery()) {
            if (!rs.next()) {
                throw new java.sql.SQLException(
                        "No transaction record found. Outcome remains unresolved."
                );
            }

            String status = rs.getString("payment_status");

            if ("COMPLETED".equalsIgnoreCase(status)) {
                return true;
            }

            throw new java.sql.SQLException(
                    "Transaction status is " + status +
                    ". Outcome remains unresolved."
            );
        }
    }
}


    private void createRecoveryRecord(
        String transferReference,
        int senderId,
        int receiverId,
        java.math.BigDecimal amount
) throws Exception {

    String sql =
            "INSERT INTO transfer_recovery " +
            "(transfer_reference, sender_account_id, receiver_account_id, amount, recovery_status) " +
            "VALUES (?, ?, ?, ?, 'PENDING')";

    try (Connection connection = getConnection();
         java.sql.PreparedStatement ps = connection.prepareStatement(sql)) {

        ps.setString(1, transferReference);
        ps.setInt(2, senderId);
        ps.setInt(3, receiverId);
        ps.setBigDecimal(4, amount);

        ps.executeUpdate();
    }
}


    private static void updateRecoveryStatus(
        String transferReference,
        String status
) throws Exception {

    if (!"COMPLETED".equals(status) && !"REJECTED".equals(status)) {
        throw new IllegalArgumentException("Invalid recovery status.");
    }

    String sql =
            "UPDATE transfer_recovery " +
            "SET recovery_status = ? " +
            "WHERE transfer_reference = ? " +
            "AND recovery_status = 'PENDING'";

    try (Connection connection = getConnection();
         java.sql.PreparedStatement ps =
                 connection.prepareStatement(sql)) {

        ps.setString(1, status);
        ps.setString(2, transferReference);

        int rowsUpdated = ps.executeUpdate();

        if (rowsUpdated == 1) {
            return;
        }
    }

    // A repeated request for the same final status is safe.
    String checkSql =
            "SELECT recovery_status FROM transfer_recovery " +
            "WHERE transfer_reference = ?";

    try (Connection connection = getConnection();
         java.sql.PreparedStatement ps =
                 connection.prepareStatement(checkSql)) {

        ps.setString(1, transferReference);

        try (java.sql.ResultSet rs = ps.executeQuery()) {
            if (!rs.next()) {
                throw new java.sql.SQLException(
                        "Recovery record was not found."
                );
            }

            String currentStatus = rs.getString("recovery_status");

            if (status.equals(currentStatus)) {
                return;
            }

            throw new java.sql.SQLException(
                    "Recovery status conflict. Current status: " +
                    currentStatus + ", requested status: " + status
            );
        }
    }
}

    static boolean hasUnresolvedRecovery(int senderId) throws Exception {
        String sql =
                "SELECT transfer_reference FROM transfer_recovery " +
                "WHERE sender_account_id = ? AND recovery_status = 'PENDING'";

        java.util.List<String> references = new java.util.ArrayList<>();

        try (Connection connection = getConnection();
            java.sql.PreparedStatement ps = connection.prepareStatement(sql)) {

            ps.setInt(1, senderId);

            try (java.sql.ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    references.add(rs.getString("transfer_reference"));
                }
            }
        }

        for (String reference : references) {
            final boolean committed;

            try {
                committed = verifyTransferCommitted(reference);
            } catch (Exception verificationEx) {
                return true;
            }

            if (!committed) {
                return true;
            }

            try {
                updateRecoveryStatus(reference, "COMPLETED");
            } catch (Exception statusEx) {
                return true;
            }
        }

        return false;
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            new TransferFrame("demo_customer").setVisible(true);
        });
    }
}
