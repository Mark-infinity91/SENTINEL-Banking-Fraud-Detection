
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class IsolatedRollbackTest {
    public static void main(String[] args) throws Exception {
        String url = System.getenv("SENTINEL_DB_URL");
        String user = System.getenv("SENTINEL_DB_USER");
        String password = System.getenv("SENTINEL_DB_PASSWORD");

        if (url == null || !url.matches(
        "(?i)^jdbc:mysql://localhost:3306/sentinel_test_db(?:\\?.*)?$")
                || user == null || password == null) {
            throw new IllegalStateException(
                "Missing credentials or test database URL is incorrect.");
        }

        try (Connection con =
                DriverManager.getConnection(url, user, password)) {
            con.setAutoCommit(false);

            try {
                BigDecimal senderBefore = balance(con, 1);
                BigDecimal receiverBefore = balance(con, 2);
                BigDecimal amount = new BigDecimal("10.00");

                try (PreparedStatement ps = con.prepareStatement(
                        "UPDATE bank_accounts SET balance = balance - ? " +
                        "WHERE account_id = 1 AND balance >= ?")) {
                    ps.setBigDecimal(1, amount);
                    ps.setBigDecimal(2, amount);
                    if (ps.executeUpdate() != 1) {
                        throw new IllegalStateException("Test debit failed.");
                    }
                }

                // Intentional failure: no account 999 exists.
                try (PreparedStatement ps = con.prepareStatement(
                        "UPDATE bank_accounts SET balance = balance + ? " +
                        "WHERE account_id = 999")) {
                    ps.setBigDecimal(1, amount);
                    if (ps.executeUpdate() != 0) {
                        throw new IllegalStateException(
                            "Unexpected test result.");
                    }
                }

                con.rollback();

                BigDecimal senderAfter = balance(con, 1);
                BigDecimal receiverAfter = balance(con, 2);

                boolean passed =
                    senderBefore.compareTo(senderAfter) == 0
                    && receiverBefore.compareTo(receiverAfter) == 0;

                System.out.println("Sender before:   " + senderBefore);
                System.out.println("Sender after:    " + senderAfter);
                System.out.println("Receiver before: " + receiverBefore);
                System.out.println("Receiver after:  " + receiverAfter);
                System.out.println("Rollback test: " + (passed ? "PASS" : "FAIL"));

                if (!passed) {
                    throw new IllegalStateException(
                        "Balances changed unexpectedly.");
                }
            } catch (Exception ex) {
                try {
                    con.rollback();
                } catch (Exception rollbackEx) {
                    ex.addSuppressed(rollbackEx);
                }
                throw ex;
            }
        }
    }

    private static BigDecimal balance(Connection con, int accountId)
            throws Exception {
        try (PreparedStatement ps = con.prepareStatement(
                "SELECT balance FROM bank_accounts WHERE account_id = ?")) {
            ps.setInt(1, accountId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new IllegalStateException(
                        "Test account not found: " + accountId);
                }
                return rs.getBigDecimal("balance");
            }
        }
    }
}
