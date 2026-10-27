import java.sql.Connection;
import java.sql.DriverManager;

public class DatabaseTest {
    public static void main(String[] args) {
        String url = System.getenv("SENTINEL_DB_URL");
        String username = System.getenv("SENTINEL_DB_USER");
        String password = System.getenv("SENTINEL_DB_PASSWORD");

        if (url == null || username == null || password == null
                || !url.matches(
                    "(?i)^jdbc:mysql://localhost:3306/sentinel_test_db(?:\\?.*)?$")) {
            System.out.println(
                "TEST BLOCKED: Only the approved local test database is allowed."
            );
            return;
        }
        try (Connection connection =
                DriverManager.getConnection(url, username, password)) {

            if (connection.isValid(5)) {
                System.out.println("SENTINEL database connected successfully!");
            } else {
                System.out.println("Database connection is not valid.");
            }

        } catch (Exception e) {
            System.out.println("Database connection failed.");
            System.out.println(e.getMessage());
        }
    }

}