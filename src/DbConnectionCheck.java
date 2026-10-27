
import java.sql.Connection;
import java.sql.DriverManager;

public class DbConnectionCheck {
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

        try (Connection con =
                DriverManager.getConnection(url, user, password)) {
            System.out.println("Database connection: SUCCESS");
            System.out.println("Connected database: "
                    + con.getCatalog());
        } catch (Exception e) {
            System.out.println("Database connection: FAILED");
            System.out.println("Reason: " + e.getMessage());
        }
    }
}
