
import java.math.BigDecimal;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class FraudEngineRollbackTest {

    private static final BigDecimal HIGH_AMOUNT =
            new BigDecimal("150000.00");

    private static final BigDecimal SMALL_AMOUNT =
            new BigDecimal("5.00");

    private static final int SENDER_ID = 1;
    private static final int RECEIVER_ID = 2;

    public static void main(String[] args) throws Exception {
        String url = System.getenv("SENTINEL_DB_URL");
        String user = System.getenv("SENTINEL_DB_USER");
        String password = System.getenv("SENTINEL_DB_PASSWORD");

        if (url == null || user == null || password == null
                || !url.matches(
                    "(?i)^jdbc:mysql://localhost:3306/sentinel_test_db(?:\\?.*)?$")) {
            throw new IllegalStateException(
                "Safety check failed: use the approved local test database only."
            );
        }

        try (Connection con =
                DriverManager.getConnection(url, user, password)) {

            con.setAutoCommit(false);

            try {
testHighAmountDetection(con);
testFiveSmallTransfers(con);
testFewerThanFiveTransfers(con);
testTransfersOutsideWindow(con);
testTargetedTransactionScan(con);

                System.out.println();
                System.out.println("ALL FRAUD DETECTION TESTS PASSED.");

            } finally {
                con.rollback();
                System.out.println(
                    "ROLLBACK complete: temporary database changes discarded."
                );
            }
        }
    }

    private static void testHighAmountDetection(Connection con)
            throws SQLException {

        int transactionId = insertTransaction(
            con, HIGH_AMOUNT, new Timestamp(System.currentTimeMillis())
        );

        FraudDetectionEngine.scan(con);

        String rules = getTriggeredRules(con, transactionId);
        int score = getRiskScore(con, transactionId);

        if (rules == null
                || !rules.contains("HIGH_TRANSACTION_AMOUNT")
                || !rules.contains("VERY_HIGH_TRANSACTION_AMOUNT")) {
            throw new AssertionError(
                "High-amount test failed. Triggered rules: " + rules
            );
        }

        if (score != 100) {
            throw new AssertionError(
                "Expected high-amount risk score 100, found " + score
            );
        }

        FraudDetectionEngine.scan(con);

        if (countAlerts(con, transactionId) != 1) {
            throw new AssertionError(
                "High-amount test created a duplicate alert."
            );
        }

        System.out.println("PASS: High-amount detection and duplicate prevention.");
    }

private static void testFiveSmallTransfers(Connection con)
        throws SQLException {

    List<Integer> ids = new ArrayList<>();
    long baseTime = System.currentTimeMillis() + 86_400_000L;

    for (int i = 0; i < 5; i++) {
        ids.add(insertTransaction(
            con,
            SMALL_AMOUNT,
            new Timestamp(baseTime + i * 60_000L)
        ));
    }

    FraudDetectionEngine.scan(con);

    // The fifth transfer is the first one whose trailing window
    // contains all five completed small transfers.
    int fifthTransactionId = ids.get(4);

    String rules = getTriggeredRules(con, fifthTransactionId);

    if (rules == null
            || !rules.contains("REPEATED_SMALL_TRANSFERS")) {
        throw new AssertionError(
            "Expected repeated-transfer alert for the fifth transaction. "
            + "Transaction ID: " + fifthTransactionId
            + ", triggered rules: " + rules
        );
    }

    int score = getRiskScore(con, fifthTransactionId);

    if (score != 30) {
        throw new AssertionError(
            "Expected risk score 30 for the fifth transaction, found "
            + score
        );
    }

    // The earlier four transfers should not match this rule yet.
    for (int i = 0; i < 4; i++) {
        String earlierRules = getTriggeredRules(con, ids.get(i));

        if (earlierRules != null
                && earlierRules.contains("REPEATED_SMALL_TRANSFERS")) {
            throw new AssertionError(
                "An earlier transfer unexpectedly triggered the rule. ID: "
                + ids.get(i)
            );
        }
    }

    System.out.println(
        "PASS: Fifth small transfer triggered the rule with risk score 30."
    );
}

    private static void testFewerThanFiveTransfers(Connection con)
            throws SQLException {

        List<Integer> ids = new ArrayList<>();
        long baseTime = System.currentTimeMillis() + 3 * 86_400_000L;

        for (int i = 0; i < 4; i++) {
            ids.add(insertTransaction(
                con,
                SMALL_AMOUNT,
                new Timestamp(baseTime + i * 60_000L)
            ));
        }

        FraudDetectionEngine.scan(con);

        for (int id : ids) {
            if (countAlerts(con, id) != 0) {
                throw new AssertionError(
                    "Unexpected alert with fewer than five transfers. ID: " + id
                );
            }
        }

        System.out.println(
            "PASS: Fewer than five small transfers did not trigger alerts."
        );
    }

    private static void testTransfersOutsideWindow(Connection con)
            throws SQLException {

        List<Integer> ids = new ArrayList<>();
        long baseTime = System.currentTimeMillis() + 5 * 86_400_000L;

        for (int i = 0; i < 5; i++) {
            // Each transfer is 11 minutes after the previous one.
            ids.add(insertTransaction(
                con,
                SMALL_AMOUNT,
                new Timestamp(baseTime + i * 11L * 60_000L)
            ));
        }

        FraudDetectionEngine.scan(con);

        for (int id : ids) {
            if (countAlerts(con, id) != 0) {
                throw new AssertionError(
                    "Unexpected alert for transfers outside the window. ID: " + id
                );
            }
        }

        System.out.println(
            "PASS: Transfers outside the 10-minute window did not trigger alerts."
        );
    }

    private static int insertTransaction(
            Connection con, BigDecimal amount, Timestamp time)
            throws SQLException {

        String sql =
            "INSERT INTO transactions " +
            "(sender_account_id, receiver_account_id, amount, " +
            "transaction_time, transaction_source, payment_status) " +
            "VALUES (?, ?, ?, ?, 'SIMULATED', 'COMPLETED')";

        try (PreparedStatement ps = con.prepareStatement(
                sql, Statement.RETURN_GENERATED_KEYS)) {

            ps.setInt(1, SENDER_ID);
            ps.setInt(2, RECEIVER_ID);
            ps.setBigDecimal(3, amount);
            ps.setTimestamp(4, time);
            ps.executeUpdate();

            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new SQLException(
                        "Could not retrieve test transaction ID."
                    );
                }

                return keys.getInt(1);
            }
        }
    }

private static void testTargetedTransactionScan(Connection con)
        throws SQLException {

    int transactionId = insertTransaction(
        con,
        HIGH_AMOUNT,
        new Timestamp(System.currentTimeMillis() + 7L * 86_400_000L)
    );

    int alertsCreated =
        FraudDetectionEngine.scanTransaction(con, transactionId);

    String rules = getTriggeredRules(con, transactionId);
    int score = getRiskScore(con, transactionId);

    if (alertsCreated < 1 || countAlerts(con, transactionId) != 1) {
        throw new AssertionError(
            "Targeted scan did not create exactly one alert. "
            + "Created: " + alertsCreated
        );
    }

    if (rules == null
            || !rules.contains("HIGH_TRANSACTION_AMOUNT")
            || !rules.contains("VERY_HIGH_TRANSACTION_AMOUNT")) {
        throw new AssertionError(
            "Targeted scan triggered unexpected rules: " + rules
        );
    }

    if (score != 100) {
        throw new AssertionError(
            "Expected targeted scan risk score 100, found " + score
        );
    }

    System.out.println(
        "PASS: Targeted transaction scan created the expected alert."
    );
}


    private static String getTriggeredRules(
            Connection con, int transactionId) throws SQLException {

        String sql =
            "SELECT triggered_rules FROM fraud_alerts WHERE transaction_id = ?";

        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, transactionId);

            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString("triggered_rules") : null;
            }
        }
    }

    private static int getRiskScore(Connection con, int transactionId)
            throws SQLException {

        String sql =
            "SELECT risk_score FROM fraud_alerts WHERE transaction_id = ?";

        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, transactionId);

            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new AssertionError(
                        "No alert found for transaction " + transactionId
                    );
                }

                return rs.getInt("risk_score");
            }
        }
    }

    private static int countAlerts(Connection con, int transactionId)
            throws SQLException {

        String sql =
            "SELECT COUNT(*) FROM fraud_alerts WHERE transaction_id = ?";

        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, transactionId);

            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new SQLException("Could not count alerts.");
                }

                return rs.getInt(1);
            }
        }
    }
}
