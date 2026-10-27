
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;

public class TransferRecoveryTest {

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
                con.setAutoCommit(false);

                try (PreparedStatement ps = con.prepareStatement(
                        "INSERT INTO transfer_recovery " +
                        "(transfer_reference, sender_account_id, " +
                        "receiver_account_id, amount, recovery_status) " +
                        "VALUES (?, 1, 2, 10.00, 'PENDING')")) {
                    ps.setString(1, reference);
                    ps.executeUpdate();
                }

                try (PreparedStatement ps = con.prepareStatement(
                        "UPDATE transfer_recovery " +
                        "SET recovery_status = 'COMPLETED' " +
                        "WHERE transfer_reference = ? " +
                        "AND recovery_status = 'PENDING'")) {
                    ps.setString(1, reference);
                    if (ps.executeUpdate() != 1) {
                        throw new AssertionError(
                                "PENDING to COMPLETED failed.");
                    }
                }

                con.commit();
                System.out.println("PENDING to COMPLETED: PASS");

                // A terminal status must not be overwritten.
                con.setAutoCommit(false);
                try (PreparedStatement ps = con.prepareStatement(
                        "UPDATE transfer_recovery " +
                        "SET recovery_status = 'REJECTED' " +
                        "WHERE transfer_reference = ? " +
                        "AND recovery_status = 'PENDING'")) {
                    ps.setString(1, reference);
                    if (ps.executeUpdate() != 0) {
                        throw new AssertionError(
                                "COMPLETED was overwritten.");
                    }
                }
                con.commit();

                try (PreparedStatement ps = con.prepareStatement(
                        "SELECT recovery_status FROM transfer_recovery " +
                        "WHERE transfer_reference = ?")) {
                    ps.setString(1, reference);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()
                                || !"COMPLETED".equals(
                                        rs.getString("recovery_status"))) {
                            throw new AssertionError(
                                    "Terminal status changed unexpectedly.");
                        }
                    }
                }

                System.out.println(
                        "COMPLETED cannot be overwritten by this guarded update: PASS");
                System.out.println("Test reference: " + reference);

            } finally {
                con.setAutoCommit(false);
                try (PreparedStatement ps = con.prepareStatement(
                        "DELETE FROM transfer_recovery " +
                        "WHERE transfer_reference = ?")) {
                    ps.setString(1, reference);
                    ps.executeUpdate();
                }
                con.commit();
            }
        }
    }
}
