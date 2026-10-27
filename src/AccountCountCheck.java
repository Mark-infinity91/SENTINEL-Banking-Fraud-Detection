
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class AccountCountCheck {
    public static void main(String[] args) {
        String url = System.getenv("SENTINEL_DB_URL");
        String user = System.getenv("SENTINEL_DB_USER");
        String password = System.getenv("SENTINEL_DB_PASSWORD");

        if (url == null || user == null || password == null
                || !url.matches(
                "(?i)^jdbc:mysql://localhost:3306/sentinel_test_db(?:\\?.*)?$")) {
            System.out.println("BLOCKED: Approved test database required.");
            return;
        }

        String sql = "SELECT role, account_status, COUNT(*) AS total "
                   + "FROM users GROUP BY role, account_status "
                   + "ORDER BY role, account_status";

        try (Connection con = DriverManager.getConnection(url, user, password);
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            System.out.println("Account counts (no personal data):");
            while (rs.next()) {
                System.out.printf("%s | %s | %d%n",
                        rs.getString("role"),
                        rs.getString("account_status"),
                        rs.getInt("total"));
            }
        } catch (Exception ex) {
            System.out.println("Report failed: "
                    + ex.getClass().getSimpleName());
        }
    }
}
