
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

public final class WebAuth {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final long SESSION_LIFETIME_MS = 15L * 60L * 1000L;
    private static final int MAX_SESSIONS = 1000;

    private static final Map<String, Session> SESSIONS =
            new ConcurrentHashMap<>();

    private WebAuth() {
    }

    public static final class Session {
        private final String username;
        private final String role;
        private final long expiresAt;

        private Session(String username, String role, long expiresAt) {
            this.username = username;
            this.role = role;
            this.expiresAt = expiresAt;
        }

        public String getUsername() {
            return username;
        }

        public String getRole() {
            return role;
        }

        public long getExpiresAt() {
            return expiresAt;
        }
    }

    public static boolean verifyPassword(char[] password, String storedHash) {
        if (storedHash == null || password == null) {
            return false;
        }

        try {
            String[] parts = storedHash.split(":", -1);
            final int iterations;
            final int saltIndex;

            if (parts.length == 2) {
                iterations = 210000;
                saltIndex = 0;
            } else if (parts.length == 3) {
                iterations = Integer.parseInt(parts[0]);
                saltIndex = 1;
            } else {
                return false;
            }

            if (iterations < 210000 || iterations > 1000000) {
                return false;
            }

            byte[] salt = Base64.getDecoder().decode(parts[saltIndex]);
            byte[] expectedHash =
                    Base64.getDecoder().decode(parts[saltIndex + 1]);

            if (salt.length != 16 || expectedHash.length != 32) {
                return false;
            }

            PBEKeySpec keySpec = new PBEKeySpec(
                    password, salt, iterations, expectedHash.length * 8);

            try {
                byte[] actualHash = SecretKeyFactory
                        .getInstance("PBKDF2WithHmacSHA256")
                        .generateSecret(keySpec)
                        .getEncoded();

                return MessageDigest.isEqual(expectedHash, actualHash);
            } finally {
                keySpec.clearPassword();
            }
        } catch (Exception ex) {
            return false;
        }
    }

    public static String createSession(String username, String role) {
        if (username == null || username.isBlank()
                || role == null || role.isBlank()) {
            throw new IllegalArgumentException(
                    "A valid username and role are required.");
        }

        cleanupExpiredSessions();

        if (SESSIONS.size() >= MAX_SESSIONS) {
            throw new IllegalStateException(
                    "Session capacity reached. Try again later.");
        }

        String token;
        do {
            byte[] randomBytes = new byte[32];
            RANDOM.nextBytes(randomBytes);
            token = Base64.getUrlEncoder()
                    .withoutPadding()
                    .encodeToString(randomBytes);
        } while (SESSIONS.containsKey(token));

        long expiresAt = System.currentTimeMillis() + SESSION_LIFETIME_MS;
        SESSIONS.put(token, new Session(username, role, expiresAt));
        return token;
    }

    public static Session getSession(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }

        Session session = SESSIONS.get(token);
        if (session == null) {
            return null;
        }

        if (System.currentTimeMillis() >= session.getExpiresAt()) {
            SESSIONS.remove(token, session);
            return null;
        }

        return session;
    }

    public static void invalidateSession(String token) {
        if (token != null && !token.isBlank()) {
            SESSIONS.remove(token);
        }
    }

    public static void cleanupExpiredSessions() {
        long now = System.currentTimeMillis();
        SESSIONS.entrySet().removeIf(
                entry -> now >= entry.getValue().getExpiresAt());
    }

    public static void clearPassword(char[] password) {
        if (password != null) {
            Arrays.fill(password, '\0');
        }
    }
}
