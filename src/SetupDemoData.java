
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;

public class SetupDemoData {

    public static void main(String[] args) throws Exception {
        String url = System.getenv("SENTINEL_DB_URL");
        String dbUser = System.getenv("SENTINEL_DB_USER");
        String dbPassword = System.getenv("SENTINEL_DB_PASSWORD");

        if (url == null || dbUser == null || dbPassword == null
                || !url.matches(
                    "(?i)^jdbc:mysql://localhost:3306/sentinel_test_db(?:\\?.*)?$")) {
            throw new IllegalStateException(
                    "Safety check failed: only the approved local test database is allowed.");
        }

        try (Connection con = DriverManager.getConnection(
                url, dbUser, dbPassword)) {

            String customerSql = """
                INSERT INTO customers
                (user_id, full_name, customer_type, email, phone)
                SELECT user_id, ?, ?, ?, ?
                FROM users
                WHERE username = ?
                """;

            try (PreparedStatement ps =
                    con.prepareStatement(customerSql)) {

                addCustomer(ps, "Aarav Sharma", "PERSONAL",
                    "aarav@example.com", "9000000001", "demo_customer");

                System.out.println("Customer profile created.");
            }

            String accountSql = """
                INSERT INTO bank_accounts
                (customer_id, account_number, account_type, balance)
                SELECT customer_id, ?, ?, ?
                FROM customers
                WHERE user_id = (
                    SELECT user_id FROM users
                    WHERE username = ?
                )
                """;

            try (PreparedStatement ps =
                    con.prepareStatement(accountSql)) {

                ps.setString(1, "SNT1000001");
                ps.setString(2, "PERSONAL");
                ps.setBigDecimal(3,
                    new java.math.BigDecimal("25000.00"));
                ps.setString(4, "demo_customer");
                ps.executeUpdate();

                System.out.println("Demo bank account created.");
            }

            System.out.println("Demo data setup completed.");
        }
    }

    private static void addCustomer(
            PreparedStatement ps,
            String name,
            String type,
            String email,
            String phone,
            String username) throws Exception {

        ps.setString(1, name);
        ps.setString(2, type);
        ps.setString(3, email);
        ps.setString(4, phone);
        ps.setString(5, username);
        ps.executeUpdate();
    }
}
