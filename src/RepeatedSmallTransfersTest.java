
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;

public class RepeatedSmallTransfersTest {

    private static final String DB_URL_PATTERN =
            "(?i)^jdbc:mysql://localhost:3306/sentinel_test_db(?:\\?.*)?$";

    public static void main(String[] args) throws Exception {
        String url = System.getenv("SENTINEL_DB_URL");
        String user = System.getenv("SENTINEL_DB_USER");
        String password = System.getenv("SENTINEL_DB_PASSWORD");

        if (url == null || user == null || password == null
                || !url.matches(DB_URL_PATTERN)) {
            throw new IllegalStateException(
                    "Safety check failed: use sentinel_test_db only.");
        }

        try (Connection con =
                     DriverManager.getConnection(url, user, password)) {

            con.setAutoCommit(false);

            try {
                int senderId = 1;
                int receiverId = 2;
                int inserted = 0;
                int alertsBefore = countAlerts(con);

                // Five completed small transfers within a 10-minute window.
                String insertSql =
                        "INSERT INTO transactions " +
                        "(sender_account_id, receiver_account_id, amount, " +
                        "transaction_time, transaction_source, payment_status, " +
                        "transfer_reference) " +
                        "VALUES (?, ?, ?, ?, 'SIMULATED', 'COMPLETED', ?)";

                long baseTime = System.currentTimeMillis();

                try (PreparedStatement ps = con.prepareStatement(
                        insertSql, Statement.RETURN_GENERATED_KEYS)) {

                    for (int i = 0; i < 5; i++) {
                        ps.setInt(1, senderId);
                        ps.setInt(2, receiverId);
                        ps.setBigDecimal(3, new BigDecimal("5.00"));
                        ps.setTimestamp(4, new Timestamp(baseTime + i * 1000L));
                        ps.setString(5, java.util.UUID.randomUUID().toString());
                        ps.executeUpdate();

                        try (ResultSet keys = ps.getGeneratedKeys()) {
                            if (!keys.next()) {
                                throw new SQLException(
                                        "Could not retrieve test transaction ID.");
                            }
                        }
                        inserted++;
                    }
                }

                System.out.println("Test transfers inserted: " + inserted);

                int created = FraudDetectionEngine.scan(con);

                String checkSql =
                        "SELECT COUNT(*) FROM fraud_alerts fa " +
                        "JOIN transactions t ON t.transaction_id = fa.transaction_id " +
                        "WHERE t.transaction_source = 'SIMULATED' " +
                        "AND t.sender_account_id = ? " +
                        "AND t.receiver_account_id = ? " +
                        "AND t.amount = 5.00 " +
                        "AND t.transaction_time BETWEEN ? AND ? " +
                        "AND fa.triggered_rules LIKE '%REPEATED_SMALL_TRANSFERS%'";

                int matchingAlerts;
                try (PreparedStatement ps = con.prepareStatement(checkSql)) {
                    ps.setInt(1, senderId);
                    ps.setInt(2, receiverId);
                    ps.setTimestamp(3, new Timestamp(baseTime - 1000L));
                    ps.setTimestamp(4, new Timestamp(baseTime + 10_000L));

                    try (ResultSet rs = ps.executeQuery()) {
                        rs.next();
                        matchingAlerts = rs.getInt(1);
                    }
                }

                System.out.println("Alerts created during scan: " + created);
                System.out.println(
                        "Repeated-transfer alerts for test rows: " + matchingAlerts);

                if (matchingAlerts > 0) {
                    System.out.println(
                            "PASS: REPEATED_SMALL_TRANSFERS rule triggered.");
                } else {
                    System.out.println(
                            "FAIL: No repeated-small-transfer alert found.");
                }

                System.out.println(
                        "Alert count before test: " + alertsBefore);

            } finally {
                con.rollback();
                System.out.println(
                        "ROLLBACK complete: test transaction and alerts discarded.");
            }
        }
    }

    private static int countAlerts(Connection con) throws SQLException {
        try (Statement st = con.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT COUNT(*) FROM fraud_alerts")) {
            rs.next();
            return rs.getInt(1);
        }
    }
}
