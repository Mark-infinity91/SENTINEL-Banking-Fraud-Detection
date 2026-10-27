
import java.math.BigDecimal;
import java.sql.*;
import java.util.UUID;

public class TransferCommittedRecoveryTest {

    public static void main(String[] args) throws Exception {
        String url = System.getenv("SENTINEL_DB_URL");
        String user = System.getenv("SENTINEL_DB_USER");
        String password = System.getenv("SENTINEL_DB_PASSWORD");

        if (url == null || user == null || password == null
                || !url.matches(
        "(?i)^jdbc:mysql://localhost:3306/sentinel_test_db(?:\\?.*)?$")) {
            throw new IllegalStateException(
                    "Safety check failed: use sentinel_test_db only.");
        }

        String reference = UUID.randomUUID().toString();
        boolean recoveryInserted = false;
        boolean transactionInserted = false;

        BigDecimal senderBefore = getBalance(url, user, password, 1);
        BigDecimal receiverBefore = getBalance(url, user, password, 2);

        try {
            // Simulate the durable recovery record being left PENDING.
            try (Connection con =
                         DriverManager.getConnection(url, user, password);
                 PreparedStatement ps = con.prepareStatement(
                         "INSERT INTO transfer_recovery " +
                         "(transfer_reference, sender_account_id, " +
                         "receiver_account_id, amount, recovery_status) " +
                         "VALUES (?, 1, 2, 10.00, 'PENDING')")) {
                ps.setString(1, reference);
                ps.executeUpdate();
                recoveryInserted = true;
            }

            // Simulate a transaction that committed successfully.
            // This inserts a test record only; it does NOT move money.
            try (Connection con =
                         DriverManager.getConnection(url, user, password);
                 PreparedStatement ps = con.prepareStatement(
                         "INSERT INTO transactions " +
                         "(sender_account_id, receiver_account_id, amount, " +
                         "transaction_source, payment_status, transfer_reference) " +
                         "VALUES (1, 2, 10.00, 'SIMULATED', 'COMPLETED', ?)")) {
                ps.setString(1, reference);
                ps.executeUpdate();
                transactionInserted = true;
            }

            // Recovery should recognize the committed transaction,
            // finalize its recovery record, and stop blocking new transfers.
            boolean blocked = TransferFrame.hasUnresolvedRecovery(1);

            if (blocked) {
                throw new AssertionError(
                        "Committed transaction still blocks recovery.");
            }
            System.out.println(
                    "Committed transaction detected: PASS");

            String status;
            try (Connection con =
                         DriverManager.getConnection(url, user, password);
                 PreparedStatement ps = con.prepareStatement(
                         "SELECT recovery_status FROM transfer_recovery " +
                         "WHERE transfer_reference = ?")) {
                ps.setString(1, reference);

                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        throw new AssertionError(
                                "Recovery record disappeared.");
                    }
                    status = rs.getString("recovery_status");
                }
            }

            if (!"COMPLETED".equals(status)) {
                throw new AssertionError(
                        "Expected COMPLETED recovery status, got " + status);
            }
            System.out.println(
                    "PENDING recovery finalized as COMPLETED: PASS");

            if (getBalance(url, user, password, 1)
                        .compareTo(senderBefore) != 0
                    || getBalance(url, user, password, 2)
                        .compareTo(receiverBefore) != 0) {
                throw new AssertionError(
                        "Account balances changed during the test.");
            }
            System.out.println(
                    "No account balance changes: PASS");

        } finally {
            // Delete only the unique records created by this test.
            if (transactionInserted) {
                try (Connection con =
                             DriverManager.getConnection(url, user, password);
                     PreparedStatement ps = con.prepareStatement(
                             "DELETE FROM transactions " +
                             "WHERE transfer_reference = ? " +
                             "AND transaction_source = 'SIMULATED'")) {
                    ps.setString(1, reference);
                    ps.executeUpdate();
                }
            }

            if (recoveryInserted) {
                try (Connection con =
                             DriverManager.getConnection(url, user, password);
                     PreparedStatement ps = con.prepareStatement(
                             "DELETE FROM transfer_recovery " +
                             "WHERE transfer_reference = ?")) {
                    ps.setString(1, reference);
                    ps.executeUpdate();
                }
            }
        }

        System.out.println("RESULT: PASS");
    }

    private static BigDecimal getBalance(
            String url, String user, String password, int accountId)
            throws SQLException {

        try (Connection con =
                     DriverManager.getConnection(url, user, password);
             PreparedStatement ps = con.prepareStatement(
                     "SELECT balance FROM bank_accounts WHERE account_id = ?")) {
            ps.setInt(1, accountId);

            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new SQLException(
                            "Test account not found: " + accountId);
                }
                return rs.getBigDecimal("balance");
            }
        }
    }
}
    