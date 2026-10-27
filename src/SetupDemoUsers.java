
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.security.SecureRandom;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.util.Base64;

public class SetupDemoUsers {

    private static String hashPassword(String password) throws Exception {
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);

        PBEKeySpec spec = new PBEKeySpec(
            password.toCharArray(), salt, 210000, 256
        );

        byte[] hash = SecretKeyFactory
            .getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(spec)
            .getEncoded();

        spec.clearPassword();

        return Base64.getEncoder().encodeToString(salt)
            + ":" + Base64.getEncoder().encodeToString(hash);
    }

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


        String sql = """
            INSERT INTO users (username, password_hash, role)
            VALUES (?, ?, ?)
            """;

        try (Connection con = DriverManager.getConnection(
                url, dbUser, dbPassword);
             PreparedStatement ps = con.prepareStatement(sql)) {

            addUser(ps, "demo_customer",
                    requireEnv("SENTINEL_DEMO_CUSTOMER_PASSWORD"), "CUSTOMER");

            addUser(ps, "demo_investigator",
                    requireEnv("SENTINEL_DEMO_INVESTIGATOR_PASSWORD"), "INVESTIGATOR");

            addUser(ps, "demo_admin",
                    requireEnv("SENTINEL_DEMO_ADMIN_PASSWORD"), "ADMIN");
            System.out.println("Demo users created successfully!");
        }
    }

    private static void addUser(
            PreparedStatement ps,
            String username,
            String password,
            String role) throws Exception {

        ps.setString(1, username);
        ps.setString(2, hashPassword(password));
        ps.setString(3, role);
        ps.executeUpdate();

        System.out.println("Created user: " + username);
    }

    private static String requireEnv(String name) {
        String value = System.getenv(name);

        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    "Required environment variable is not set: " + name);
        }

        return value;
    }
}
