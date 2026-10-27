import java.math.BigDecimal;
import java.sql.*;
import java.util.*;

/**
 * Configurable rule-based demonstration engine for SENTINEL.
 *
 * Supported detection types:
 * HIGH_AMOUNT:
 *   Triggers when transaction amount >= threshold_amount.
 * REPEATED_SMALL_TRANSFERS:
 *   Triggers when transaction amount <= threshold_amount and at least
 *   min_occurrences completed transfers from the same sender to receiver
 *   occur within window_minutes ending at this transaction's timestamp.
 *
 * The engine reads rule type, threshold, risk score, and window settings
 * from fraud_rules. Existing public scan(Connection) API is preserved.
 */
public class FraudDetectionEngine {

    private static final String TYPE_HIGH_AMOUNT = "HIGH_AMOUNT";
    private static final String TYPE_REPEATED_SMALL_TRANSFERS = "REPEATED_SMALL_TRANSFERS";

    private static class Rule {
    final String name;
    final String type;
    final BigDecimal threshold;
    final int riskScore;
    final int windowMinutes;
    final int minOccurrences;

    Rule(String name, String type, BigDecimal threshold,
         int riskScore, int windowMinutes, int minOccurrences) {
        this.name = name;
        this.type = type;
        this.threshold = threshold;
        this.riskScore = riskScore;
        this.windowMinutes = windowMinutes;
        this.minOccurrences = minOccurrences;
    }
}

/**
 * Scans all completed transactions for configured fraud rules.
 * Preserves the existing API used by the investigator dashboard.
 */
public static int scan(Connection con) throws SQLException {
    return scanTransactions(con, null);
}

/**
 * Scans one specific completed transaction.
 * Used for automatic fraud detection after a customer transfer.
 */
public static int scanTransaction(Connection con, int transactionId)
        throws SQLException {
    if (transactionId <= 0) {
        throw new IllegalArgumentException(
            "Transaction ID must be positive."
        );
    }

    return scanTransactions(con, transactionId);
}

private static int scanTransactions(Connection con, Integer targetId)
        throws SQLException {

    List<Rule> rules = loadRules(con);
    if (rules.isEmpty()) {
        return 0;
    }

    String transactionsSql =
        "SELECT transaction_id, sender_account_id, receiver_account_id, " +
        "amount, transaction_time FROM transactions " +
        "WHERE payment_status = 'COMPLETED' " +
        (targetId == null ? "" : "AND transaction_id = ? ") +
        "ORDER BY transaction_time DESC";

    int created = 0;

    try (PreparedStatement txPs = con.prepareStatement(transactionsSql)) {
        if (targetId != null) {
            txPs.setInt(1, targetId);
        }

        try (ResultSet rs = txPs.executeQuery()) {
            while (rs.next()) {
                int txId = rs.getInt("transaction_id");
                int senderId = rs.getInt("sender_account_id");
                int receiverId = rs.getInt("receiver_account_id");
                BigDecimal amount = rs.getBigDecimal("amount");
                Timestamp time = rs.getTimestamp("transaction_time");

                if (amount == null || time == null || hasAlert(con, txId)) {
                    continue;
                }

                List<String> matchedNames = new ArrayList<>();
                int score = 0;

                for (Rule rule : rules) {
                    if (matchesRule(
                            con, rule, senderId, receiverId, amount, time)) {
                        matchedNames.add(rule.name);
                        score = Math.min(100, score + rule.riskScore);
                    }
                }

                if (!matchedNames.isEmpty()
                        && insertAlert(
                            con, txId, score,
                            String.join(", ", matchedNames))) {
                    created++;
                }
            }
        }
    }

    return created;
}
    private static List<Rule> loadRules(Connection con) throws SQLException {
        List<Rule> rules = new ArrayList<>();
        String sql =
            "SELECT rule_id, rule_name, detection_type, threshold_amount, risk_score, " +
            "window_minutes, min_occurrences " +
            "FROM fraud_rules WHERE is_active = 1 ORDER BY rule_id";

        try (PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String type = rs.getString("detection_type");
                BigDecimal threshold = rs.getBigDecimal("threshold_amount");
                int riskScore = rs.getInt("risk_score");
                int windowMinutes = rs.getInt("window_minutes");
                int minOccurrences = rs.getInt("min_occurrences");

                if (type == null || type.trim().isEmpty()) {
                    continue;
                }
                // Threshold is required for both currently supported types.
                if (threshold == null) {
                    continue;
                }
                if (riskScore < 1 || riskScore > 100) {
                    continue;
                }

                rules.add(new Rule(
                        rs.getString("rule_name"),
                        type.trim().toUpperCase(Locale.ROOT),
                        threshold,
                        riskScore,
                        Math.max(1, windowMinutes),
                        Math.max(2, minOccurrences)
                ));
            }
        }
        return rules;
    }


    private static boolean matchesRule(Connection con, Rule rule, int senderId,
                                    int receiverId, BigDecimal amount,
                                    Timestamp time) throws SQLException {
        switch (rule.type) {
            case TYPE_HIGH_AMOUNT:
                return amount.compareTo(rule.threshold) >= 0;

            case TYPE_REPEATED_SMALL_TRANSFERS:
                return isInRepeatedSmallTransferPattern(
                    con, senderId, receiverId, amount, time, rule
                );

            default:
                return false;
        }
    }





    private static boolean isInRepeatedSmallTransferPattern(
        Connection con, int senderId, int receiverId,
        BigDecimal amount, Timestamp transactionTime,
        Rule rule) throws SQLException {

    if (amount == null || transactionTime == null
            || amount.compareTo(rule.threshold) > 0) {
        return false;
    }

    String sql =
        "SELECT COUNT(*) FROM transactions t " +
        "WHERE t.sender_account_id = ? " +
        "AND t.receiver_account_id = ? " +
        "AND t.amount <= ? " +
        "AND t.payment_status = 'COMPLETED' " +
        "AND t.transaction_time BETWEEN " +
        "    DATE_SUB(?, INTERVAL ? MINUTE) AND ?";

    try (PreparedStatement ps = con.prepareStatement(sql)) {
        ps.setInt(1, senderId);
        ps.setInt(2, receiverId);
        ps.setBigDecimal(3, rule.threshold);
        ps.setTimestamp(4, transactionTime);
        ps.setInt(5, rule.windowMinutes);
        ps.setTimestamp(6, transactionTime);

        try (ResultSet rs = ps.executeQuery()) {
            return rs.next()
                    && rs.getInt(1) >= rule.minOccurrences;
        }
    }
}


    private static boolean hasAlert(Connection con, int transactionId) throws SQLException {
        String sql = "SELECT 1 FROM fraud_alerts WHERE transaction_id = ? LIMIT 1";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, transactionId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }



    private static boolean insertAlert(
        Connection con,
        int transactionId,
        int score,
        String triggeredRules) throws SQLException {

    String sql =
        "INSERT INTO fraud_alerts " +
        "(transaction_id, risk_score, triggered_rules, alert_status) " +
        "VALUES (?, ?, ?, 'NEW') " +
        "ON DUPLICATE KEY UPDATE transaction_id = transaction_id";

    try (PreparedStatement ps = con.prepareStatement(sql)) {
        ps.setInt(1, transactionId);
        ps.setInt(2, Math.min(100, Math.max(0, score)));

        ps.setString(3, triggeredRules);

        return ps.executeUpdate() == 1;
    }
}

}
