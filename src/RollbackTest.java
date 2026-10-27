
import java.math.BigDecimal;
import java.sql.*;

public class RollbackTest {

    public static void main(String[] args) {
        String url = System.getenv("SENTINEL_DB_URL");
        String user = System.getenv("SENTINEL_DB_USER");
        String password = System.getenv("SENTINEL_DB_PASSWORD");

        if (url == null || user == null || password == null
                || !url.matches(
                    "(?i)^jdbc:mysql://localhost:3306/sentinel_test_db(?:\\?.*)?$")) {
            System.out.println(
                "TEST BLOCKED: Only the approved local test database is allowed."
            );
            return;
        }

        String selectSql =
            "SELECT balance FROM bank_accounts " +
            "WHERE account_id = ? FOR UPDATE";

        String updateSql =
            "UPDATE bank_accounts " +
            "SET balance = balance - ? " +
            "WHERE account_id = ? AND balance >= ?";

        BigDecimal amount = new BigDecimal("1.00");
        BigDecimal originalBalance = null;

        try (Connection con =
                 DriverManager.getConnection(url, user, password)) {

            con.setAutoCommit(false);

            try {
                // Lock and record the original balance.
                try (PreparedStatement ps =
                         con.prepareStatement(selectSql)) {
                    ps.setInt(1, 1);

                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            throw new SQLException(
                                "Test account was not found."
                            );
                        }
                        originalBalance = rs.getBigDecimal("balance");
                    }
                }

                // Perform a temporary debit.
                try (PreparedStatement ps =
                         con.prepareStatement(updateSql)) {
                    ps.setBigDecimal(1, amount);
                    ps.setInt(2, 1);
                    ps.setBigDecimal(3, amount);

                    if (ps.executeUpdate() != 1) {
                        throw new SQLException("Test debit failed.");
                    }
                }

                // Force a failure after the debit.
                throw new SQLException("SIMULATED_TEST_FAILURE");

            } catch (SQLException ex) {
                if (!"SIMULATED_TEST_FAILURE".equals(ex.getMessage())) {
                    throw ex;
                }

                con.rollback();

                // Check the balance again after rollback.
                try (PreparedStatement ps =
                         con.prepareStatement(
                             "SELECT balance FROM bank_accounts " +
                             "WHERE account_id = ?")) {
                    ps.setInt(1, 1);

                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            throw new SQLException(
                                "Test account disappeared."
                            );
                        }

                        BigDecimal restoredBalance =
                            rs.getBigDecimal("balance");

                        if (originalBalance.compareTo(restoredBalance) == 0) {
                            System.out.println(
                                "PASS: Rollback restored the original balance."
                            );
                        } else {
                            System.out.println(
                                "FAIL: Balance does not match the original."
                            );
                        }
                    }
                }
            }

        } catch (SQLException ex) {
            System.out.println("TEST ERROR: " + ex.getMessage());
        }
    }
}
