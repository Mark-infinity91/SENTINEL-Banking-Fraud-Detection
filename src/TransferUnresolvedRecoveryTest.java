
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;

public class TransferUnresolvedRecoveryTest {

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

        try (Connection con =
                     DriverManager.getConnection(url, user, password)) {

            try {
                // Create a PENDING recovery record only.
                try (PreparedStatement ps = con.prepareStatement(
                        "INSERT INTO transfer_recovery " +
                        "(transfer_reference, sender_account_id, " +
                        "receiver_account_id, amount, recovery_status) " +
                        "VALUES (?, 1, 2, 10.00, 'PENDING')")) {
                    ps.setString(1, reference);
                    ps.executeUpdate();
                }

                // There must be no transaction for this reference.
                try (PreparedStatement ps = con.prepareStatement(
                        "SELECT transfer_reference FROM transactions " +
                        "WHERE transfer_reference = ?")) {
                    ps.setString(1, reference);

                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            throw new AssertionError(
                                    "Unexpected matching transaction.");
                        }
                    }
                }

                // The recovery record must remain unresolved.
                try (PreparedStatement ps = con.prepareStatement(
                        "SELECT recovery_status FROM transfer_recovery " +
                        "WHERE transfer_reference = ?")) {
                    ps.setString(1, reference);

                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()
                                || !"PENDING".equals(
                                        rs.getString("recovery_status"))) {
                            throw new AssertionError(
                                    "Recovery record is not PENDING.");
                        }
                    }
                }

                System.out.println(
                        "No matching transaction: PASS");
                System.out.println(
                        "Recovery remains PENDING: PASS");

            } finally {
                // Delete only this test's recovery record.
                try (PreparedStatement ps = con.prepareStatement(
                        "DELETE FROM transfer_recovery " +
                        "WHERE transfer_reference = ?")) {
                    ps.setString(1, reference);
                    ps.executeUpdate();
                }
            }
        }
    }
}
