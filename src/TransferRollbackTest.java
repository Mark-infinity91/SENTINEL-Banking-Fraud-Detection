import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

public class TransferRollbackTest {

    public static void main(String[] args) throws Exception {

        String url = System.getenv("SENTINEL_DB_URL");
        String user = System.getenv("SENTINEL_DB_USER");
        String password = System.getenv("SENTINEL_DB_PASSWORD");

        if (url == null || user == null || password == null) {
            System.out.println("FAIL: Database environment variables are missing.");
            return;
        }


        if (!url.matches(
                "(?i)^jdbc:mysql://localhost:3306/sentinel_test_db(?:\\?.*)?$")) {
            System.out.println("BLOCKED: Test database URL is not approved.");
            return;
        }

        int senderId = 1;
        int receiverId = 2;
        BigDecimal amount = new BigDecimal("10.00");

        try (Connection con =
                     DriverManager.getConnection(url, user, password)) {

            con.setAutoCommit(false);

            try {
                // Lock both demo accounts in a consistent order.
                try (PreparedStatement ps = con.prepareStatement(
                        "SELECT account_id, balance, account_status " +
                        "FROM bank_accounts " +
                        "WHERE account_id IN (?, ?) " +
                        "ORDER BY account_id FOR UPDATE")) {

                    ps.setInt(1, senderId);
                    ps.setInt(2, receiverId);

                    Map<Integer, BigDecimal> balances = new HashMap<>();
                    Map<Integer, String> statuses = new HashMap<>();

                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            int id = rs.getInt("account_id");
                            balances.put(id, rs.getBigDecimal("balance"));
                            statuses.put(id, rs.getString("account_status"));
                        }
                    }

                    if (balances.size() != 2) {
                        throw new SQLException("Demo accounts 1 and 2 were not both found.");
                    }

                    if (!"ACTIVE".equals(statuses.get(senderId))
                            || !"ACTIVE".equals(statuses.get(receiverId))) {
                        throw new SQLException(
                                "Both demo accounts must be ACTIVE before testing.");
                    }

                    BigDecimal senderBefore = balances.get(senderId);
                    BigDecimal receiverBefore = balances.get(receiverId);

                    System.out.println("Starting balances:");
                    System.out.println("Sender:   " + senderBefore);
                    System.out.println("Receiver: " + receiverBefore);

                    // Simulate the sender debit.
                    String debitSql =
                            "UPDATE bank_accounts " +
                            "SET balance = balance - ? " +
                            "WHERE account_id = ? " +
                            "AND balance >= ? " +
                            "AND account_status = 'ACTIVE'";

                    try (PreparedStatement debit =
                                 con.prepareStatement(debitSql)) {
                        debit.setBigDecimal(1, amount);
                        debit.setInt(2, senderId);
                        debit.setBigDecimal(3, amount);

                        if (debit.executeUpdate() != 1) {
                            throw new SQLException("Sender debit did not affect one row.");
                        }
                    }

                    System.out.println("Sender debit: executed inside transaction.");

                    // INTENTIONAL FAILURE:
                    // The real receiver is ACTIVE, so this update should
                    // affect zero rows because it requires FROZEN.
                    String creditSql =
                            "UPDATE bank_accounts " +
                            "SET balance = balance + ? " +
                            "WHERE account_id = ? " +
                            "AND account_status = 'FROZEN'";

                    int creditRows;

                    try (PreparedStatement credit =
                                 con.prepareStatement(creditSql)) {
                        credit.setBigDecimal(1, amount);
                        credit.setInt(2, receiverId);
                        creditRows = credit.executeUpdate();
                    }

                    if (creditRows != 0) {
                        throw new SQLException(
                                "Test stopped: expected the intentional credit failure.");
                    }

                    System.out.println("Intentional receiver credit failure: PASS");

                    // Roll back the sender debit.
                    con.rollback();
                    System.out.println("Rollback executed: PASS");

                    // Verify balances after rollback.
                    Map<Integer, BigDecimal> after = new HashMap<>();

                    try (PreparedStatement check = con.prepareStatement(
                            "SELECT account_id, balance " +
                            "FROM bank_accounts " +
                            "WHERE account_id IN (?, ?) " +
                            "ORDER BY account_id")) {

                        check.setInt(1, senderId);
                        check.setInt(2, receiverId);

                        try (ResultSet rs = check.executeQuery()) {
                            while (rs.next()) {
                                after.put(
                                        rs.getInt("account_id"),
                                        rs.getBigDecimal("balance"));
                            }
                        }
                    }

                    boolean senderUnchanged =
                            senderBefore.compareTo(after.get(senderId)) == 0;

                    boolean receiverUnchanged =
                            receiverBefore.compareTo(after.get(receiverId)) == 0;

                    System.out.println("Sender after rollback:   " + after.get(senderId));
                    System.out.println("Receiver after rollback: " + after.get(receiverId));
                    System.out.println("Sender unchanged:   " +
                            (senderUnchanged ? "PASS" : "FAIL"));
                    System.out.println("Receiver unchanged: " +
                            (receiverUnchanged ? "PASS" : "FAIL"));

                    if (senderUnchanged && receiverUnchanged) {
                        System.out.println(
                                "\nRESULT: PASS — rollback restored both balances.");
                    } else {
                        System.out.println(
                                "\nRESULT: FAIL — investigate the database state.");
                    }
                }

            } catch (Exception e) {
                try {
                    con.rollback();
                } catch (SQLException rollbackError) {
                    e.addSuppressed(rollbackError);
                }
                throw e;
            } finally {
                con.setAutoCommit(true);
            }
        }
    }
}