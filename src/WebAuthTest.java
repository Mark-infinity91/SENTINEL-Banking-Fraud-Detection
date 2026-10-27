
public class WebAuthTest {

    public static void main(String[] args) {
        String token = WebAuth.createSession("test_customer", "CUSTOMER");

        if (token == null || token.length() < 40) {
            throw new AssertionError("Session token was not generated correctly.");
        }

        WebAuth.Session session = WebAuth.getSession(token);

        if (session == null) {
            throw new AssertionError("New session could not be retrieved.");
        }

        if (!"test_customer".equals(session.getUsername())) {
            throw new AssertionError("Session username did not match.");
        }

        if (!"CUSTOMER".equals(session.getRole())) {
            throw new AssertionError("Session role did not match.");
        }

        if (WebAuth.getSession("invalid-token") != null) {
            throw new AssertionError("Invalid token was accepted.");
        }

        WebAuth.invalidateSession(token);

        if (WebAuth.getSession(token) != null) {
            throw new AssertionError("Invalidated session was still accepted.");
        }

        System.out.println("PASS: Session creation");
        System.out.println("PASS: Session retrieval");
        System.out.println("PASS: Invalid token rejection");
        System.out.println("PASS: Logout invalidation");
        System.out.println("WebAuthTest completed successfully.");
    }
}
