
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;

public class TransferRecoveryLogicTest {

    private static String url;
    private static String user;
    private static String password;

    public static void main(String[] args) throws Exception {
        url = System.getenv("SENTINEL_DB_URL");
        user = System.getenv("SENTINEL_DB_USER");
        password = System.getenv("SENTINEL_DB_PASSWORD");

        if (url == null || user == null || password == null
                || !url.matches(
        "(?i)^jdbc:mysql://localhost:3306/sentinel_test_db(?:\\?.*)?$")) {
            throw new IllegalStateException(
                    "Safety check failed: use sentinel_test_db only.");
        }

        String reference = UUID.randomUUID().toString();
        boolean inserted = false;

        BigDecimal senderBefore = getBalance(1);
        BigDecimal receiverBefore = getBalance(2);

        try {
            // Create a durable unresolved recovery record.
            try (Connection con =
                         DriverManager.getConnection(url, user, password);
                 PreparedStatement ps = con.prepareStatement(
                         "INSERT INTO transfer_recovery " +
                         "(transfer_reference, sender_account_id, " +
                         "receiver_account_id, amount, recovery_status) " +
                         "VALUES (?, 1, 2, 10.00, 'PENDING')")) {
                ps.setString(1, reference);
                ps.executeUpdate();
                inserted = true;
            }

            // Simulate a fresh session: rely on database recovery state,
            // not the previous TransferFrame's in-memory fields.
            boolean blocked = TransferFrame.hasUnresolvedRecovery(1);

            if (!blocked) {
                throw new AssertionError(
                        "Fresh session did not block unresolved recovery.");
            }

            System.out.println(
                    "Fresh-session retry guard: PASS");

            // The missing transaction must not be treated as proof
            // that the transfer failed.
            try (Connection con =
                         DriverManager.getConnection(url, user, password);
                 PreparedStatement ps = con.prepareStatement(
                         "SELECT recovery_status FROM transfer_recovery " +
                         "WHERE transfer_reference = ?")) {
                ps.setString(1, reference);

                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()
                            || !"PENDING".equals(
                                    rs.getString("recovery_status"))) {
                        throw new AssertionError(
                                "Recovery must remain PENDING.");
                    }
                }
            }

            System.out.println(
                    "Missing transaction remains unresolved: PASS");

            if (getBalance(1).compareTo(senderBefore) != 0
                    || getBalance(2).compareTo(receiverBefore) != 0) {
                throw new AssertionError(
                        "Account balances changed during recovery test.");
            }

            System.out.println(
                    "No account balance changes: PASS");

        } finally {
            // Remove only this test's uniquely identified recovery record.
            if (inserted) {
                try (Connection con =
                             DriverManager.getConnection(url, user, password);
                     PreparedStatement ps = con.prepareStatement(
                             "DELETE FROM transfer_recovery " +
                             "WHERE transfer_reference = ? " +
                             "AND recovery_status = 'PENDING'")) {
                    ps.setString(1, reference);
                    ps.executeUpdate();
                }
            }
        }

        System.out.println("RESULT: PASS");
    }

    private static BigDecimal getBalance(int accountId)
            throws Exception {
        try (Connection con =
                     DriverManager.getConnection(url, user, password);
             PreparedStatement ps = con.prepareStatement(
                     "SELECT balance FROM bank_accounts " +
                     "WHERE account_id = ?")) {
            ps.setInt(1, accountId);

            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new AssertionError(
                            "Test account not found: " + accountId);
                }
                return rs.getBigDecimal("balance");
            }
        }
    }
}
