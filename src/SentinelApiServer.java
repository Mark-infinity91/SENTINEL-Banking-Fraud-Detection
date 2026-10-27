
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class SentinelApiServer {

    private static final Path WEB_ROOT =
            Path.of("web").toAbsolutePath().normalize();

    private static final String COOKIE_NAME = "SENTINEL_SESSION";
    private static final String EXPECTED_ORIGIN = "http://127.0.0.1:8080";
    private static final int MAX_BODY_BYTES = 4096;
    private static final int MAX_LOGIN_ATTEMPTS = 10;
    
private static final int FAILED_LOGIN_ALERT_THRESHOLD = 3;
private static final long FAILED_LOGIN_ALERT_WINDOW_MS =
        TimeUnit.MINUTES.toMillis(10);
private static final long FAILED_LOGIN_ALERT_COOLDOWN_MS =
        TimeUnit.MINUTES.toMillis(15);

private static final java.util.concurrent.ConcurrentHashMap<
        String, FailedLoginTracker> FAILED_LOGIN_TRACKERS =
        new java.util.concurrent.ConcurrentHashMap<>();

    private static final long LOCKOUT_MS = TimeUnit.MINUTES.toMillis(5);

    private static final Map<String, LoginLimit> LOGIN_LIMITS =
            new ConcurrentHashMap<>();

    private static final class LoginLimit {
        private int failures;
        private long lockedUntil;
    }
    
private static final class FailedLoginTracker {
    private int failures;
    private long windowStartedAt;
    private long lastAlertAt;
}



    public static void main(String[] args) throws IOException {
        if (!Files.isRegularFile(WEB_ROOT.resolve("index.html"))) {
            throw new IOException(
                    "Dashboard not found: " + WEB_ROOT.resolve("index.html")
                    + "\nStart the server from the project root folder.");
        }

        HttpServer server = HttpServer.create(
                new InetSocketAddress("127.0.0.1", 8080), 0);

        server.createContext("/api/health", SentinelApiServer::health);
        server.createContext("/api/login", SentinelApiServer::login);
        server.createContext("/api/me", SentinelApiServer::me);
        server.createContext("/api/logout", SentinelApiServer::logout);
        server.createContext("/api/account", SentinelApiServer::account);
        server.createContext("/api/transactions", SentinelApiServer::transactions);
        
server.createContext(
        "/api/investigator/transactions",
        SentinelApiServer::investigatorTransactions);

server.createContext(
        "/api/investigator/alerts",
        SentinelApiServer::investigatorAlerts);

server.createContext(
        "/api/investigator/investigations",
        SentinelApiServer::submitInvestigation);

server.createContext(
        "/api/investigator/investigations/details",
        SentinelApiServer::investigationDetails);


        server.createContext("/api/transfer", SentinelApiServer::transfer);        
        server.createContext("/api/security-alerts",
                SentinelApiServer::securityAlerts);
        server.createContext("/api/security-alerts/read",
                SentinelApiServer::markSecurityAlertRead);

        server.createContext("/images/", exchange -> {
    try {
        if (!"GET".equalsIgnoreCase(
                exchange.getRequestMethod())) {
            sendText(exchange, 405, "Method not allowed");
            return;
        }

        String path = exchange.getRequestURI().getPath();
        if (!"/images/bank-glass.svg".equals(path)
                && !"/images/new.png".equals(path)) {
            sendText(exchange, 404, "Page not found");
            return;
        }

        Path imagePath = WEB_ROOT.resolve("images")
                .resolve(path.substring("/images/".length()));

        byte[] image = Files.readAllBytes(imagePath);

        String contentType = path.endsWith(".png")
        ? "image/png"
        : "image/svg+xml; charset=UTF-8";

exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.getResponseHeaders().set(
                "X-Content-Type-Options", "nosniff");

        exchange.sendResponseHeaders(200, image.length);

        try (var out = exchange.getResponseBody()) {
            out.write(image);
        }
    } catch (IOException ex) {
        sendText(exchange, 404, "Image not found");
    } finally {
        exchange.close();
    }
});
        server.createContext("/", SentinelApiServer::dashboard);

        server.setExecutor(Executors.newFixedThreadPool(8));
        server.start();

        System.out.println("SENTINEL API started.");
        System.out.println("Dashboard: http://127.0.0.1:8080/");
        System.out.println("Health:    http://127.0.0.1:8080/api/health");
        System.out.println("Press Ctrl+C to stop the server.");
    }

    private static void health(HttpExchange exchange) throws IOException {
        try {
            if (!requireMethod(exchange, "GET")) {
                return;
            }

            sendJson(exchange, 200,
                    "{\"status\":\"ok\",\"service\":\"SENTINEL API\"}");
        } finally {
            exchange.close();
        }
    }

    private static void login(HttpExchange exchange) throws IOException {
        char[] password = null;

        try {
            if (!requireMethod(exchange, "POST")) {
                return;
            }

            if (!requireSameOrigin(exchange)) {
                sendJson(exchange, 403, "{\"error\":\"Request rejected\"}");
                return;
            }

            if (!"application/x-www-form-urlencoded".equalsIgnoreCase(
                    mediaType(exchange.getRequestHeaders()
                            .getFirst("Content-Type")))) {
                sendJson(exchange, 415,
                        "{\"error\":\"Unsupported content type\"}");
                return;
            }

            String client = exchange.getRemoteAddress()
                    .getAddress().getHostAddress();

            if (isLockedOut(client)) {
                sendJson(exchange, 429,
                        "{\"error\":\"Too many attempts. Try again later.\"}");
                return;
            }

            byte[] body = readLimitedBody(exchange);
            if (body == null) {
                sendJson(exchange, 413, "{\"error\":\"Request too large\"}");
                return;
            }

            Map<String, String> form;
            try {
                form = parseForm(new String(body, StandardCharsets.UTF_8));
            } catch (IllegalArgumentException ex) {
                sendJson(exchange, 400, "{\"error\":\"Invalid request\"}");
                return;
            }

            String username = form.get("username");
            String passwordText = form.get("password");

            if (username == null || passwordText == null
                    || username.isBlank()
                    || username.length() > 100
                    || passwordText.length() > 1024
                    || username.indexOf('\0') >= 0
                    || passwordText.indexOf('\0') >= 0) {
                recordFailure(client);
                sendJson(exchange, 401,
                        "{\"error\":\"Invalid username or password\"}");
                return;
            }

            password = passwordText.toCharArray();

            AuthenticatedUser user = authenticate(username, password);


            if (user == null) {
                recordFailure(client);
                recordFailedLoginAlert(username);
                sendJson(exchange, 401,
                        "{\"error\":\"Invalid username or password\"}");
                return;
            }



            clearFailures(client);

            if ("CUSTOMER".equalsIgnoreCase(user.role)) {
                createSecurityAlert(
                        user.userId,
                        "SUCCESSFUL_LOGIN",
                        "A successful login occurred on your account.");
            }

            String token;
            try {
                token = WebAuth.createSession(user.username, user.role);

            } catch (IllegalStateException ex) {
                sendJson(exchange, 503,
                        "{\"error\":\"Login temporarily unavailable\"}");
                return;
            }

            exchange.getResponseHeaders().add("Set-Cookie",
                    COOKIE_NAME + "=" + token
                    + "; Path=/; HttpOnly; SameSite=Strict; Max-Age=900");

            sendJson(exchange, 200,
                    "{\"authenticated\":true,\"role\":"
                    + jsonString(user.role) + "}");
        } catch (IOException ex) {
            throw ex;
        } catch (Exception ex) {
            System.err.println("Login request failed:");
ex.printStackTrace(System.err);
            sendJson(exchange, 500,
                    "{\"error\":\"Login temporarily unavailable\"}");
        } finally {
            WebAuth.clearPassword(password);
            exchange.close();
        }
    }

private static final class AuthenticatedUser {
    private final int userId;
    private final String username;
    private final String role;

    private AuthenticatedUser(int userId, String username, String role) {
        this.userId = userId;
        this.username = username;
        this.role = role;
    }
}

    private static AuthenticatedUser authenticate(
            String username, char[] password) throws Exception {

        String url = System.getenv("SENTINEL_DB_URL");
        String dbUser = System.getenv("SENTINEL_DB_USER");
        String dbPassword = System.getenv("SENTINEL_DB_PASSWORD");

        if (url == null || dbUser == null || dbPassword == null
                || !url.matches(
                "(?i)^jdbc:mysql://localhost:3306/sentinel_test_db(?:\\?.*)?$")) {
            throw new IllegalStateException(
                    "Approved local test database is not configured.");
        }

String sql = "SELECT user_id, password_hash, role, account_status "
        + "FROM users WHERE username = ?";


        try (Connection con =
                     DriverManager.getConnection(url, dbUser, dbPassword);
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setString(1, username);

            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }

                String storedHash = rs.getString("password_hash");
                String role = rs.getString("role");
                String status = rs.getString("account_status");

                if (status == null || !"ACTIVE".equalsIgnoreCase(status)
                        || role == null
                        || !(role.equalsIgnoreCase("CUSTOMER")
                        || role.equalsIgnoreCase("INVESTIGATOR")
                        || role.equalsIgnoreCase("ADMIN"))) {
                    return null;
                }

                if (!WebAuth.verifyPassword(password, storedHash)) {
                    return null;
                }

                
int userId = rs.getInt("user_id");
return new AuthenticatedUser(userId, username, role.toUpperCase());

            }
        }
    }


private static void createSecurityAlert(
        int userId, String alertType, String message) {

    String dbUrl = System.getenv("SENTINEL_DB_URL");
    String dbUser = System.getenv("SENTINEL_DB_USER");
    String dbPassword = System.getenv("SENTINEL_DB_PASSWORD");

    if (dbUrl == null || dbUser == null || dbPassword == null
            || !dbUrl.matches(
            "(?i)^jdbc:mysql://localhost:3306/sentinel_test_db(?:\\?.*)?$")) {
        System.err.println("Security alert skipped: database unavailable.");
        return;
    }

    String sql = "INSERT INTO security_alerts "
            + "(user_id, alert_type, message) VALUES (?, ?, ?)";

    try (Connection con =
                 DriverManager.getConnection(dbUrl, dbUser, dbPassword);
         PreparedStatement ps = con.prepareStatement(sql)) {

        ps.setInt(1, userId);
        ps.setString(2, alertType);
        ps.setString(3, message);
        ps.executeUpdate();

    } catch (Exception ex) {
        // Alert failure must not prevent an otherwise successful login.
        System.err.println("Security alert creation failed: "
                + ex.getClass().getSimpleName());
    }
}



private static void recordFailedLoginAlert(String username) {
    if (username == null || username.isBlank()
            || username.length() > 100
            || username.indexOf('\0') >= 0) {
        return;
    }

    String normalizedUsername = username.toLowerCase(
            java.util.Locale.ROOT);

    long now = System.currentTimeMillis();

    FailedLoginTracker tracker = FAILED_LOGIN_TRACKERS
            .computeIfAbsent(normalizedUsername,
                    key -> new FailedLoginTracker());

    boolean shouldAlert = false;

    synchronized (tracker) {
        if (tracker.windowStartedAt == 0
                || now - tracker.windowStartedAt
                >= FAILED_LOGIN_ALERT_WINDOW_MS) {
            tracker.failures = 0;
            tracker.windowStartedAt = now;
        }

        tracker.failures++;

        if (tracker.failures >= FAILED_LOGIN_ALERT_THRESHOLD
                && (tracker.lastAlertAt == 0
                || now - tracker.lastAlertAt
                >= FAILED_LOGIN_ALERT_COOLDOWN_MS)) {
            tracker.lastAlertAt = now;
            shouldAlert = true;
        }
    }

    if (!shouldAlert) {
        return;
    }

    String dbUrl = System.getenv("SENTINEL_DB_URL");
    String dbUser = System.getenv("SENTINEL_DB_USER");
    String dbPassword = System.getenv("SENTINEL_DB_PASSWORD");

    if (dbUrl == null || dbUser == null || dbPassword == null
            || !dbUrl.matches(
            "(?i)^jdbc:mysql://localhost:3306/sentinel_test_db(?:\\?.*)?$")) {
        return;
    }

    String sql = "SELECT u.user_id "
            + "FROM users u "
            + "JOIN customers c ON c.user_id = u.user_id "
            + "WHERE LOWER(u.username) = ? "
            + "AND u.role = 'CUSTOMER' "
            + "AND u.account_status = 'ACTIVE'";

    try (Connection con =
                 DriverManager.getConnection(dbUrl, dbUser, dbPassword);
         PreparedStatement ps = con.prepareStatement(sql)) {

        ps.setString(1, normalizedUsername);

        try (ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                createSecurityAlert(
                        rs.getInt("user_id"),
                        "MULTIPLE_FAILED_LOGINS",
                        "Multiple failed login attempts were detected for your account.");
            }
        }
    } catch (Exception ex) {
        System.err.println("Failed-login alert lookup failed: "
                + ex.getClass().getSimpleName());
    }
}


    
    private static void me(HttpExchange exchange) throws IOException {
        try {
            if (!requireMethod(exchange, "GET")) {
                return;
            }

            WebAuth.Session session = getSession(exchange);


        if (session == null) {
            sendJson(exchange, 200,
                    "{\"authenticated\":false}");
            return;
        }


            sendJson(exchange, 200,
                    "{\"authenticated\":true,\"username\":"
                    + jsonString(session.getUsername())
                    + ",\"role\":" + jsonString(session.getRole()) + "}");
        } finally {
            exchange.close();
        }
    }

    private static void logout(HttpExchange exchange) throws IOException {
        try {
            if (!requireMethod(exchange, "POST")) {
                return;
            }

            if (!requireSameOrigin(exchange)) {
                sendJson(exchange, 403, "{\"error\":\"Request rejected\"}");
                return;
            }

            String token = readSessionToken(exchange);
            WebAuth.invalidateSession(token);

            exchange.getResponseHeaders().add("Set-Cookie",
                    COOKIE_NAME
                    + "=; Path=/; HttpOnly; SameSite=Strict; Max-Age=0");

            sendJson(exchange, 200, "{\"authenticated\":false}");
        } finally {
            exchange.close();
        }
    }

private static void account(HttpExchange exchange) throws IOException {
    try {
        if (!requireMethod(exchange, "GET")) {
            return;
        }

        WebAuth.Session session = requireCustomer(exchange);
        if (session == null) {
            return;
        }

String dbUrl = System.getenv("SENTINEL_DB_URL");
String dbUser = System.getenv("SENTINEL_DB_USER");
String dbPassword = System.getenv("SENTINEL_DB_PASSWORD");

if (dbUrl == null || dbUser == null || dbPassword == null
        || !dbUrl.startsWith(
                "jdbc:mysql://localhost:3306/sentinel_test_db")) {
    sendJson(exchange, 503,
            "{\"error\":\"Database configuration unavailable\"}");
    return;
}

String sql = "SELECT ba.account_number, ba.balance "
        + "FROM bank_accounts ba "
        + "JOIN customers c ON ba.customer_id = c.customer_id "
        + "JOIN users u ON c.user_id = u.user_id "
        + "WHERE u.username = ? "
        + "AND ba.account_status = 'ACTIVE' "
        + "ORDER BY ba.account_id LIMIT 1";

try (java.sql.Connection con =
             java.sql.DriverManager.getConnection(
                     dbUrl, dbUser, dbPassword);
     java.sql.PreparedStatement ps = con.prepareStatement(sql)) {

    ps.setString(1, session.getUsername());

    try (java.sql.ResultSet rs = ps.executeQuery()) {
        if (rs.next()) {
            String accountNumber = rs.getString("account_number");
            String balance = rs.getBigDecimal("balance").toPlainString();

            String maskedAccount = "•••• "
                    + accountNumber.substring(
                            Math.max(0, accountNumber.length() - 4));

            String json = "{\"accountNumber\":"
                    + jsonString(maskedAccount)
                    + ",\"balance\":" + jsonString(balance) + "}";

            sendJson(exchange, 200, json);
        } else {
            sendJson(exchange, 404,
                    "{\"error\":\"No active account found\"}");
        }
    }
} catch (Exception ex) {
    System.err.println("Account endpoint failed: "
            + ex.getClass().getSimpleName());

    sendJson(exchange, 500,
            "{\"error\":\"Unable to load account\"}");
}
    } finally {
        exchange.close();
    }
}


private static void securityAlerts(HttpExchange exchange)
        throws IOException {
    try {
        if (!requireMethod(exchange, "GET")) {
            return;
        }

        WebAuth.Session session = requireCustomer(exchange);
        if (session == null) {
            return;
        }

        String dbUrl = System.getenv("SENTINEL_DB_URL");
        String dbUser = System.getenv("SENTINEL_DB_USER");
        String dbPassword = System.getenv("SENTINEL_DB_PASSWORD");

        if (dbUrl == null || dbUser == null || dbPassword == null
                || !dbUrl.matches(
                "(?i)^jdbc:mysql://localhost:3306/sentinel_test_db(?:\\?.*)?$")) {
            sendJson(exchange, 503,
                    "{\"error\":\"Database configuration unavailable\"}");
            return;
        }

        String sql = "SELECT sa.alert_id, sa.alert_type, sa.message, "
                + "sa.created_at, sa.is_read "
                + "FROM security_alerts sa "
                + "JOIN users u ON sa.user_id = u.user_id "
                + "WHERE u.username = ? "
                + "ORDER BY sa.created_at DESC LIMIT 100";

        try (java.sql.Connection con =
                     java.sql.DriverManager.getConnection(
                             dbUrl, dbUser, dbPassword);
             java.sql.PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setString(1, session.getUsername());

            try (java.sql.ResultSet rs = ps.executeQuery()) {
                StringBuilder json = new StringBuilder("{\"alerts\":[");
                boolean first = true;

                while (rs.next()) {
                    if (!first) {
                        json.append(",");
                    }
                    first = false;

                    json.append("{\"alertId\":")
                            .append(rs.getInt("alert_id"))
                            .append(",\"alertType\":")
                            .append(jsonString(rs.getString("alert_type")))
                            .append(",\"message\":")
                            .append(jsonString(rs.getString("message")))
                            .append(",\"createdAt\":")
                            .append(jsonString(
                                    rs.getTimestamp("created_at").toString()))
                            .append(",\"isRead\":")
                            .append(rs.getBoolean("is_read"))
                            .append("}");
                }

                json.append("]}");
                sendJson(exchange, 200, json.toString());
            }
        }
    } catch (Exception ex) {
        System.err.println("Security alerts endpoint failed: "
                + ex.getClass().getSimpleName());
        sendJson(exchange, 500,
                "{\"error\":\"Unable to load security alerts\"}");
    } finally {
        exchange.close();
    }
}

private static void markSecurityAlertRead(HttpExchange exchange)
        throws IOException {
    try {
        if (!requireMethod(exchange, "POST")) {
            return;
        }

        if (!requireSameOrigin(exchange)) {
            sendJson(exchange, 403, "{\"error\":\"Request rejected\"}");
            return;
        }

        WebAuth.Session session = requireCustomer(exchange);
        if (session == null) {
            return;
        }

        String contentType = mediaType(
                exchange.getRequestHeaders().getFirst("Content-Type"));

        if (!"application/x-www-form-urlencoded".equalsIgnoreCase(
                contentType)) {
            sendJson(exchange, 415,
                    "{\"error\":\"Unsupported content type\"}");
            return;
        }

        byte[] body = readLimitedBody(exchange);
        if (body == null) {
            sendJson(exchange, 413,
                    "{\"error\":\"Request too large\"}");
            return;
        }

        Map<String, String> form;
        try {
            form = parseForm(new String(body, StandardCharsets.UTF_8));
        } catch (IllegalArgumentException ex) {
            sendJson(exchange, 400,
                    "{\"error\":\"Invalid request\"}");
            return;
        }

        String idText = form.get("alertId");
        int alertId;

        try {
            if (idText == null || !idText.matches("[0-9]{1,10}")) {
                throw new NumberFormatException();
            }
            alertId = Integer.parseInt(idText);
            if (alertId <= 0) {
                throw new NumberFormatException();
            }
        } catch (NumberFormatException ex) {
            sendJson(exchange, 400,
                    "{\"error\":\"Invalid alert ID\"}");
            return;
        }

        String dbUrl = System.getenv("SENTINEL_DB_URL");
        String dbUser = System.getenv("SENTINEL_DB_USER");
        String dbPassword = System.getenv("SENTINEL_DB_PASSWORD");

        if (dbUrl == null || dbUser == null || dbPassword == null
                || !dbUrl.matches(
                "(?i)^jdbc:mysql://localhost:3306/sentinel_test_db(?:\\?.*)?$")) {
            sendJson(exchange, 503,
                    "{\"error\":\"Database configuration unavailable\"}");
            return;
        }

        String sql = "UPDATE security_alerts sa "
                + "JOIN users u ON sa.user_id = u.user_id "
                + "SET sa.is_read = TRUE "
                + "WHERE sa.alert_id = ? AND u.username = ?";

        try (java.sql.Connection con =
                     java.sql.DriverManager.getConnection(
                             dbUrl, dbUser, dbPassword);
             java.sql.PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setInt(1, alertId);
            ps.setString(2, session.getUsername());

            int updated = ps.executeUpdate();

            if (updated == 1) {
                sendJson(exchange, 200, "{\"updated\":true}");
            } else {
                sendJson(exchange, 404,
                        "{\"error\":\"Alert not found\"}");
            }
        }
    } catch (Exception ex) {
        System.err.println("Mark security alert read failed: "
                + ex.getClass().getSimpleName());
        sendJson(exchange, 500,
                "{\"error\":\"Unable to update security alert\"}");
    } finally {
        exchange.close();
    }
}




private static void transactions(HttpExchange exchange) throws IOException {
    try {
        if (!requireMethod(exchange, "GET")) {
            return;
        }

        WebAuth.Session session = requireCustomer(exchange);
        if (session == null) {
            return;
        }

String dbUrl = System.getenv("SENTINEL_DB_URL");
String dbUser = System.getenv("SENTINEL_DB_USER");
String dbPassword = System.getenv("SENTINEL_DB_PASSWORD");

if (dbUrl == null || dbUser == null || dbPassword == null
        || !dbUrl.startsWith(
                "jdbc:mysql://localhost:3306/sentinel_test_db")) {
    sendJson(exchange, 503,
            "{\"error\":\"Database configuration unavailable\"}");
    return;
}

String sql = "SELECT t.transaction_id, t.transaction_time, t.amount, "
        + "t.transaction_source, t.payment_status, "
        + "CASE "
        + "WHEN t.sender_account_id = t.receiver_account_id THEN 'SELF' "
        + "WHEN sender.customer_id = c.customer_id THEN 'SENT' "
        + "ELSE 'RECEIVED' END AS transaction_type "
        + "FROM transactions t "
        + "JOIN customers c ON c.user_id = "
        + "(SELECT u.user_id FROM users u WHERE u.username = ?) "
        + "JOIN bank_accounts sender "
        + "ON sender.account_id = t.sender_account_id "
        + "JOIN bank_accounts receiver "
        + "ON receiver.account_id = t.receiver_account_id "
        + "WHERE sender.customer_id = c.customer_id "
        + "OR receiver.customer_id = c.customer_id "
        + "ORDER BY t.transaction_time DESC LIMIT 100";

try (java.sql.Connection con =
             java.sql.DriverManager.getConnection(
                     dbUrl, dbUser, dbPassword);
     java.sql.PreparedStatement ps = con.prepareStatement(sql)) {

    ps.setString(1, session.getUsername());

    try (java.sql.ResultSet rs = ps.executeQuery()) {
        StringBuilder json = new StringBuilder("[");
        boolean first = true;

        while (rs.next()) {
            if (!first) json.append(",");
            first = false;

            java.sql.Timestamp timestamp =
                    rs.getTimestamp("transaction_time");
            java.math.BigDecimal amount = rs.getBigDecimal("amount");

            json.append("{")
                    .append("\"transactionId\":")
                    .append(rs.getLong("transaction_id"))
                    .append(",\"transactionTime\":")
                    .append(jsonString(timestamp == null
                            ? null : timestamp.toString()))
                    .append(",\"type\":")
                    .append(jsonString(rs.getString("transaction_type")))
                    .append(",\"amount\":")
                    .append(jsonString(amount == null
                            ? null : amount.toPlainString()))
                    .append(",\"source\":")
                    .append(jsonString(rs.getString("transaction_source")))
                    .append(",\"status\":")
                    .append(jsonString(rs.getString("payment_status")))
                    .append("}");
        }

        json.append("]");
        sendJson(exchange, 200, json.toString());
    }
} catch (Exception ex) {
    System.err.println("Transactions endpoint failed: "
            + ex.getClass().getSimpleName());

    sendJson(exchange, 500,
            "{\"error\":\"Unable to load transactions\"}");
}
    } finally {
        exchange.close();
    }
}


private static void investigatorTransactions(
        HttpExchange exchange) throws IOException {
    try {
        if (!requireMethod(exchange, "GET")) {
            return;
        }

        WebAuth.Session session = requireInvestigator(exchange);
        if (session == null) {
            return;
        }

        String dbUrl = System.getenv("SENTINEL_DB_URL");
        String dbUser = System.getenv("SENTINEL_DB_USER");
        String dbPassword = System.getenv("SENTINEL_DB_PASSWORD");

        if (dbUrl == null || dbUser == null || dbPassword == null
                || !dbUrl.matches(
                "(?i)^jdbc:mysql://localhost:3306/sentinel_test_db(?:\\?.*)?$")) {
            sendJson(exchange, 503,
                    "{\"error\":\"Database configuration unavailable\"}");
            return;
        }

        String sql =
                "SELECT transaction_id, transaction_time, amount, "
                + "transaction_source, payment_status, "
                + "sender_account_id, receiver_account_id "
                + "FROM transactions "
                + "ORDER BY transaction_time DESC LIMIT 100";

        try (java.sql.Connection con =
                     java.sql.DriverManager.getConnection(
                             dbUrl, dbUser, dbPassword);
             java.sql.PreparedStatement ps = con.prepareStatement(sql);
             java.sql.ResultSet rs = ps.executeQuery()) {

            StringBuilder json = new StringBuilder("[");
            boolean first = true;

            while (rs.next()) {
                if (!first) {
                    json.append(",");
                }
                first = false;

                java.sql.Timestamp timestamp =
                        rs.getTimestamp("transaction_time");
                java.math.BigDecimal amount =
                        rs.getBigDecimal("amount");

                json.append("{")
                        .append("\"transactionId\":")
                        .append(rs.getLong("transaction_id"))
                        .append(",\"transactionTime\":")
                        .append(jsonString(timestamp == null
                                ? null : timestamp.toString()))
                        .append(",\"amount\":")
                        .append(jsonString(amount == null
                                ? null : amount.toPlainString()))
                        .append(",\"source\":")
                        .append(jsonString(
                                rs.getString("transaction_source")))
                        .append(",\"status\":")
                        .append(jsonString(
                                rs.getString("payment_status")))
                        .append(",\"senderAccountId\":")
                        .append(rs.getLong("sender_account_id"))
                        .append(",\"receiverAccountId\":")
                        .append(rs.getLong("receiver_account_id"))
                        .append("}");
            }

            json.append("]");
            sendJson(exchange, 200, json.toString());
        }
    } catch (Exception ex) {
        System.err.println("Investigator transactions failed: "
                + ex.getClass().getSimpleName());

        sendJson(exchange, 500,
                "{\"error\":\"Unable to load investigation transactions\"}");
    } finally {
        exchange.close();
    }
}


private static void investigatorAlerts(
        HttpExchange exchange) throws IOException {
    try {
        if (!requireMethod(exchange, "GET")) {
            return;
        }

        WebAuth.Session session = requireInvestigator(exchange);
        if (session == null) {
            return;
        }

        String dbUrl = System.getenv("SENTINEL_DB_URL");
        String dbUser = System.getenv("SENTINEL_DB_USER");
        String dbPassword = System.getenv("SENTINEL_DB_PASSWORD");

        if (dbUrl == null || dbUser == null || dbPassword == null
                || !dbUrl.matches(
                "(?i)^jdbc:mysql://localhost:3306/sentinel_test_db(?:\\?.*)?$")) {
            sendJson(exchange, 503,
                    "{\"error\":\"Database configuration unavailable\"}");
            return;
        }

        String sql =
                "SELECT a.alert_id, a.transaction_id, t.amount, "
                + "a.risk_score, a.triggered_rules, "
                + "a.alert_status, a.created_at "
                + "FROM fraud_alerts a "
                + "LEFT JOIN transactions t "
                + "ON a.transaction_id = t.transaction_id "
                + "ORDER BY a.created_at DESC LIMIT 100";

        try (java.sql.Connection con =
                     java.sql.DriverManager.getConnection(
                             dbUrl, dbUser, dbPassword);
             java.sql.PreparedStatement ps = con.prepareStatement(sql);
             java.sql.ResultSet rs = ps.executeQuery()) {

            StringBuilder json = new StringBuilder("[");
            boolean first = true;

            while (rs.next()) {
                if (!first) {
                    json.append(",");
                }
                first = false;

                java.sql.Timestamp createdAt =
                        rs.getTimestamp("created_at");
                java.math.BigDecimal amount =
                        rs.getBigDecimal("amount");

                json.append("{")
                        .append("\"alertId\":")
                        .append(rs.getInt("alert_id"))
                        .append(",\"transactionId\":")
                        .append(rs.getInt("transaction_id"))
                        .append(",\"amount\":")
                        .append(jsonString(amount == null
                                ? null : amount.toPlainString()))
                        .append(",\"riskScore\":")
                        .append(rs.getInt("risk_score"))
                        .append(",\"triggeredRules\":")
                        .append(jsonString(
                                rs.getString("triggered_rules")))
                        .append(",\"status\":")
                        .append(jsonString(
                                rs.getString("alert_status")))
                        .append(",\"createdAt\":")
                        .append(jsonString(createdAt == null
                                ? null : createdAt.toString()))
                        .append("}");
            }

            json.append("]");
            sendJson(exchange, 200, json.toString());
        }
    } catch (Exception ex) {
        System.err.println("Investigator alerts failed: "
                + ex.getClass().getSimpleName());

        sendJson(exchange, 500,
                "{\"error\":\"Unable to load fraud alerts\"}");
    } finally {
        exchange.close();
    }
}





private static void investigationDetails(HttpExchange exchange)
        throws IOException {
    try {
        if (!requireMethod(exchange, "GET")) {
            return;
        }

        WebAuth.Session session = requireInvestigator(exchange);
        if (session == null) {
            return;
        }

        String query = exchange.getRequestURI().getRawQuery();
        Map<String, String> params;
        try {
            params = parseForm(query == null ? "" : query);
        } catch (IllegalArgumentException ex) {
            sendJson(exchange, 400, "{\"error\":\"Invalid request\"}");
            return;
        }

        String alertIdText = params.get("alertId");
        int alertId;
        try {
            if (alertIdText == null || !alertIdText.matches("[0-9]{1,10}")) {
                throw new NumberFormatException();
            }
            alertId = Integer.parseInt(alertIdText);
            if (alertId <= 0) throw new NumberFormatException();
        } catch (NumberFormatException ex) {
            sendJson(exchange, 400, "{\"error\":\"Invalid alert ID\"}");
            return;
        }

        String dbUrl = System.getenv("SENTINEL_DB_URL");
        String dbUser = System.getenv("SENTINEL_DB_USER");
        String dbPassword = System.getenv("SENTINEL_DB_PASSWORD");
        if (dbUrl == null || dbUser == null || dbPassword == null
                || !dbUrl.matches(
                "(?i)^jdbc:mysql://localhost:3306/sentinel_test_db(?:\\?.*)?$")) {
            sendJson(exchange, 503,
                    "{\"error\":\"Database configuration unavailable\"}");
            return;
        }

        String sql =
                "SELECT i.investigation_id, i.alert_id, a.transaction_id, "
                + "a.alert_status, a.risk_score, a.triggered_rules, "
                + "i.decision, i.investigation_note, i.investigation_status, "
                + "i.evidence, i.findings, i.follow_up_action, i.reviewed_at, "
                + "u.username AS investigator_username "
                + "FROM investigations i "
                + "JOIN users u ON u.user_id = i.investigator_user_id "
                + "JOIN fraud_alerts a ON a.alert_id = i.alert_id "
                + "WHERE i.alert_id = ? "
                + "ORDER BY i.reviewed_at DESC, i.investigation_id DESC";

        try (Connection con = DriverManager.getConnection(dbUrl, dbUser, dbPassword);
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, alertId);
            try (ResultSet rs = ps.executeQuery()) {
                StringBuilder json = new StringBuilder("{\"investigations\":[");
                boolean first = true;
                while (rs.next()) {
                    if (!first) json.append(',');
                    first = false;

                    java.sql.Timestamp reviewedAt = rs.getTimestamp("reviewed_at");
                    json.append('{')
                            .append("\"investigationId\":").append(rs.getInt("investigation_id"))
                            .append(",\"alertId\":").append(rs.getInt("alert_id"))
                            .append(",\"transactionId\":").append(rs.getInt("transaction_id"))
                            .append(",\"alertStatus\":").append(jsonString(rs.getString("alert_status")))
                            .append(",\"riskScore\":").append(rs.getInt("risk_score"))
                            .append(",\"triggeredRules\":").append(jsonString(rs.getString("triggered_rules")))
                            .append(",\"decision\":").append(jsonString(rs.getString("decision")))
                            .append(",\"note\":").append(jsonString(rs.getString("investigation_note")))
                            .append(",\"investigationStatus\":").append(jsonString(rs.getString("investigation_status")))
                            .append(",\"evidence\":").append(jsonString(rs.getString("evidence")))
                            .append(",\"findings\":").append(jsonString(rs.getString("findings")))
                            .append(",\"followUp\":").append(jsonString(rs.getString("follow_up_action")))
                            .append(",\"reviewedAt\":").append(jsonString(reviewedAt == null ? null : reviewedAt.toString()))
                            .append(",\"investigatorUsername\":").append(jsonString(rs.getString("investigator_username")))
                            .append('}');
                }
                json.append("]}");
                sendJson(exchange, 200, json.toString());
            }
        }
    } catch (Exception ex) {
        System.err.println("Investigation details failed: "
                + ex.getClass().getSimpleName());
        sendJson(exchange, 500,
                "{\"error\":\"Unable to load investigation details\"}");
    } finally {
        exchange.close();
    }
}


private static void submitInvestigation(HttpExchange exchange)
        throws IOException {
    Connection con = null;

    try {
        if (!requireMethod(exchange, "POST")) {
            return;
        }

        if (!requireSameOrigin(exchange)) {
            sendJson(exchange, 403, "{\"error\":\"Request rejected\"}");
            return;
        }

        WebAuth.Session session = requireInvestigator(exchange);
        if (session == null) {
            return;
        }

        if (!"application/x-www-form-urlencoded".equalsIgnoreCase(
                mediaType(exchange.getRequestHeaders()
                        .getFirst("Content-Type")))) {
            sendJson(exchange, 415,
                    "{\"error\":\"Unsupported content type\"}");
            return;
        }

        byte[] body = readLimitedBody(exchange);
        if (body == null) {
            sendJson(exchange, 413,
                    "{\"error\":\"Request too large\"}");
            return;
        }

        Map<String, String> form;
        try {
            form = parseForm(new String(body, StandardCharsets.UTF_8));
        } catch (IllegalArgumentException ex) {
            sendJson(exchange, 400, "{\"error\":\"Invalid request\"}");
            return;
        }

        String alertIdText = form.get("alertId");
        String decision = form.get("decision");
        String note = form.get("note");
        String evidence = form.getOrDefault("evidence", "");
        String findings = form.getOrDefault("findings", "");
        String followUp = form.getOrDefault("followUp", "");

        int alertId;
        try {
            if (alertIdText == null
                    || !alertIdText.matches("[0-9]{1,10}")) {
                throw new NumberFormatException();
            }
            alertId = Integer.parseInt(alertIdText);
            if (alertId <= 0) {
                throw new NumberFormatException();
            }
        } catch (NumberFormatException ex) {
            sendJson(exchange, 400, "{\"error\":\"Invalid alert ID\"}");
            return;
        }

        if (decision == null
                || !("LEGITIMATE".equals(decision)
                || "SUSPECTED_FRAUD".equals(decision)
                || "NEEDS_MORE_INFO".equals(decision))
                || note == null || note.isBlank()
                || note.length() > 2000
                || evidence.length() > 1000
                || findings.length() > 1000
                || followUp.length() > 1000
                || containsNullCharacter(note)
                || containsNullCharacter(evidence)
                || containsNullCharacter(findings)
                || containsNullCharacter(followUp)) {
            sendJson(exchange, 400,
                    "{\"error\":\"Check the decision and investigation fields\"}");
            return;
        }

        String dbUrl = System.getenv("SENTINEL_DB_URL");
        String dbUser = System.getenv("SENTINEL_DB_USER");
        String dbPassword = System.getenv("SENTINEL_DB_PASSWORD");

        if (dbUrl == null || dbUser == null || dbPassword == null
                || !dbUrl.matches(
                "(?i)^jdbc:mysql://localhost:3306/sentinel_test_db(?:\\?.*)?$")) {
            sendJson(exchange, 503,
                    "{\"error\":\"Database configuration unavailable\"}");
            return;
        }

        con = DriverManager.getConnection(dbUrl, dbUser, dbPassword);
        con.setAutoCommit(false);

        try {
            int investigatorId;
            String investigatorSql =
                    "SELECT user_id FROM users "
                    + "WHERE username = ? AND role = 'INVESTIGATOR' "
                    + "AND account_status = 'ACTIVE'";

            try (PreparedStatement ps =
                         con.prepareStatement(investigatorSql)) {
                ps.setString(1, session.getUsername());

                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        con.rollback();
                        sendJson(exchange, 403,
                                "{\"error\":\"Active investigator account required\"}");
                        return;
                    }
                    investigatorId = rs.getInt("user_id");
                }
            }

            String currentStatus;
            String lockSql =
                    "SELECT alert_status FROM fraud_alerts "
                    + "WHERE alert_id = ? FOR UPDATE";

            try (PreparedStatement ps = con.prepareStatement(lockSql)) {
                ps.setInt(1, alertId);

                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        con.rollback();
                        sendJson(exchange, 404,
                                "{\"error\":\"Fraud alert not found\"}");
                        return;
                    }
                    currentStatus = rs.getString("alert_status");
                }
            }

            if ("CLOSED".equalsIgnoreCase(currentStatus)) {
                con.rollback();
                sendJson(exchange, 409,
                        "{\"error\":\"This fraud alert is already closed\"}");
                return;
            }

            String investigationStatus =
                    "NEEDS_MORE_INFO".equals(decision)
                    ? "IN_PROGRESS" : "CLOSED";
            String newAlertStatus = investigationStatus;

            String insertSql =
                    "INSERT INTO investigations "
                    + "(alert_id, investigator_user_id, decision, "
                    + "investigation_note, investigation_status, "
                    + "evidence, findings, follow_up_action) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

            int investigationId;
            try (PreparedStatement ps = con.prepareStatement(
                    insertSql, java.sql.Statement.RETURN_GENERATED_KEYS)) {
                ps.setInt(1, alertId);
                ps.setInt(2, investigatorId);
                ps.setString(3, decision);
                ps.setString(4, note.trim());
                ps.setString(5, investigationStatus);
                ps.setString(6, evidence.trim());
                ps.setString(7, findings.trim());
                ps.setString(8, followUp.trim());

                if (ps.executeUpdate() != 1) {
                    throw new SQLException("Investigation insert failed");
                }

                try (ResultSet keys = ps.getGeneratedKeys()) {
                    if (!keys.next()) {
                        throw new SQLException(
                                "Could not retrieve the investigation ID");
                    }
                    investigationId = keys.getInt(1);
                }
            }

            String updateSql =
                    "UPDATE fraud_alerts SET alert_status = ? "
                    + "WHERE alert_id = ?";
            try (PreparedStatement ps = con.prepareStatement(updateSql)) {
                ps.setString(1, newAlertStatus);
                ps.setInt(2, alertId);

                if (ps.executeUpdate() != 1) {
                    throw new SQLException("Fraud alert status update failed");
                }
            }

            con.commit();

            sendJson(exchange, 200,
                    "{\"success\":true,\"investigationId\":"
                    + investigationId
                    + ",\"alertId\":" + alertId
                    + ",\"status\":" + jsonString(newAlertStatus)
                    + "}");

        } catch (Exception ex) {
            try {
                con.rollback();
            } catch (SQLException rollbackEx) {
                System.err.println("Investigation rollback failed: "
                        + rollbackEx.getClass().getSimpleName());
            }
            throw ex;
        }

    } catch (Exception ex) {
        System.err.println("Investigation submission failed: "
                + ex.getClass().getSimpleName());
        try {
            sendJson(exchange, 500,
                    "{\"error\":\"Investigation could not be saved\"}");
        } catch (IOException responseEx) {
            System.err.println("Investigation response failed");
        }
    } finally {
        if (con != null) {
            try {
                con.close();
            } catch (SQLException ex) {
                System.err.println("Investigation connection close failed");
            }
        }
        exchange.close();
    }
}

private static boolean containsNullCharacter(String value) {
    return value != null && value.indexOf('\0') >= 0;
}


private static void transfer(HttpExchange exchange)
        throws IOException {

    Connection con = null;

    try {
        if (!requireMethod(exchange, "POST")) {
            return;
        }

        if (!requireSameOrigin(exchange)) {
            sendJson(exchange, 403,
                    "{\"error\":\"Request rejected\"}");
            return;
        }

        WebAuth.Session session = requireCustomer(exchange);
        if (session == null) {
            return;
        }

        if (!"application/x-www-form-urlencoded".equalsIgnoreCase(
                mediaType(exchange.getRequestHeaders()
                        .getFirst("Content-Type")))) {
            sendJson(exchange, 415,
                    "{\"error\":\"Unsupported content type\"}");
            return;
        }

        byte[] body = readLimitedBody(exchange);

        if (body == null) {
            sendJson(exchange, 413,
                    "{\"error\":\"Request too large\"}");
            return;
        }

        Map<String, String> form;

        try {
            form = parseForm(new String(body, StandardCharsets.UTF_8));
        } catch (IllegalArgumentException ex) {
            sendJson(exchange, 400,
                    "{\"error\":\"Invalid request\"}");
            return;
        }

        String recipientNumber = form.get("recipient");
        String amountText = form.get("amount");

        if (recipientNumber == null || amountText == null
                || recipientNumber.isBlank()
                || recipientNumber.length() > 30
                || recipientNumber.indexOf('\0') >= 0
                || amountText.length() > 32) {
            sendJson(exchange, 400,
                    "{\"error\":\"Invalid transfer details\"}");
            return;
        }

        BigDecimal amount;

        try {
            amount = new BigDecimal(amountText);

            if (amount.scale() > 2) {
                sendJson(exchange, 400,
                        "{\"error\":\"Amount supports at most two decimal places\"}");
                return;
            }

            amount = amount.setScale(2);

        } catch (NumberFormatException | ArithmeticException ex) {
            sendJson(exchange, 400,
                    "{\"error\":\"Invalid transfer amount\"}");
            return;
        }

        if (amount.signum() <= 0
                || amount.precision() > 15) {
            sendJson(exchange, 400,
                    "{\"error\":\"Transfer amount is outside the allowed range\"}");
            return;
        }

        String dbUrl = System.getenv("SENTINEL_DB_URL");
        String dbUser = System.getenv("SENTINEL_DB_USER");
        String dbPassword = System.getenv("SENTINEL_DB_PASSWORD");

        if (dbUrl == null || dbUser == null || dbPassword == null
                || !dbUrl.startsWith(
                        "jdbc:mysql://localhost:3306/sentinel_test_db")) {
            sendJson(exchange, 503,
                    "{\"error\":\"Database configuration unavailable\"}");
            return;
        }

        con = java.sql.DriverManager.getConnection(
                dbUrl, dbUser, dbPassword);

        con.setAutoCommit(false);

        try {
            int senderId;

            String senderSql =
                    "SELECT ba.account_id "
                    + "FROM bank_accounts ba "
                    + "JOIN customers c "
                    + "ON c.customer_id = ba.customer_id "
                    + "JOIN users u ON u.user_id = c.user_id "
                    + "WHERE u.username = ? "
                    + "AND ba.account_status = 'ACTIVE' "
                    + "ORDER BY ba.account_id LIMIT 1";

            try (PreparedStatement ps =
                         con.prepareStatement(senderSql)) {
                ps.setString(1, session.getUsername());

                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        con.rollback();
                        sendJson(exchange, 404,
                                "{\"error\":\"No active sender account found\"}");
                        return;
                    }

                    senderId = rs.getInt("account_id");
                }
            }

            int recipientId;

            String recipientSql =
                    "SELECT account_id FROM bank_accounts "
                    + "WHERE account_number = ?";

            try (PreparedStatement ps =
                         con.prepareStatement(recipientSql)) {
                ps.setString(1, recipientNumber);

                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        con.rollback();
                        sendJson(exchange, 404,
                                "{\"error\":\"Recipient account not found\"}");
                        return;
                    }

                    recipientId = rs.getInt("account_id");

                    if (rs.next()) {
                        con.rollback();
                        sendJson(exchange, 409,
                                "{\"error\":\"Recipient account is ambiguous\"}");
                        return;
                    }
                }
            }

            if (senderId == recipientId) {
                con.rollback();
                sendJson(exchange, 400,
                        "{\"error\":\"Cannot transfer to your own account\"}");
                return;
            }

            BigDecimal senderBalance = null;
            String senderStatus = null;
            String recipientStatus = null;
            boolean senderFound = false;
            boolean recipientFound = false;

            String lockSql =
                    "SELECT account_id, balance, account_status "
                    + "FROM bank_accounts "
                    + "WHERE account_id IN (?, ?) "
                    + "ORDER BY account_id FOR UPDATE";

            try (PreparedStatement ps =
                         con.prepareStatement(lockSql)) {
                ps.setInt(1, Math.min(senderId, recipientId));
                ps.setInt(2, Math.max(senderId, recipientId));

                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        int id = rs.getInt("account_id");
                        String status = rs.getString("account_status");

                        if (id == senderId) {
                            senderBalance = rs.getBigDecimal("balance");
                            senderStatus = status;
                            senderFound = true;
                        }

                        if (id == recipientId) {
                            recipientStatus = status;
                            recipientFound = true;
                        }
                    }
                }
            }

            if (!senderFound || !recipientFound) {
                con.rollback();
                sendJson(exchange, 404,
                        "{\"error\":\"Account not found\"}");
                return;
            }

            if (!"ACTIVE".equals(senderStatus)
                    || !"ACTIVE".equals(recipientStatus)) {
                con.rollback();
                sendJson(exchange, 409,
                        "{\"error\":\"Both accounts must be active\"}");
                return;
            }

            if (senderBalance.compareTo(amount) < 0) {
                con.rollback();
                sendJson(exchange, 409,
                        "{\"error\":\"Insufficient balance\"}");
                return;
            }

            String debitSql =
                    "UPDATE bank_accounts "
                    + "SET balance = balance - ? "
                    + "WHERE account_id = ? "
                    + "AND account_status = 'ACTIVE' "
                    + "AND balance >= ?";

            try (PreparedStatement ps =
                         con.prepareStatement(debitSql)) {
                ps.setBigDecimal(1, amount);
                ps.setInt(2, senderId);
                ps.setBigDecimal(3, amount);

                if (ps.executeUpdate() != 1) {
                    throw new SQLException("Sender debit failed");
                }
            }

            String creditSql =
                    "UPDATE bank_accounts "
                    + "SET balance = balance + ? "
                    + "WHERE account_id = ? "
                    + "AND account_status = 'ACTIVE'";

            try (PreparedStatement ps =
                         con.prepareStatement(creditSql)) {
                ps.setBigDecimal(1, amount);
                ps.setInt(2, recipientId);

                if (ps.executeUpdate() != 1) {
                    throw new SQLException("Recipient credit failed");
                }
            }

            String reference = java.util.UUID.randomUUID().toString();

        
String insertSql =
        "INSERT INTO transactions "
        + "(sender_account_id, receiver_account_id, "
        + "amount, transaction_source, payment_status, "
        + "transfer_reference) "
        + "VALUES (?, ?, ?, 'MANUAL', 'COMPLETED', ?)";

int newTransactionId;

try (PreparedStatement ps = con.prepareStatement(
        insertSql, java.sql.Statement.RETURN_GENERATED_KEYS)) {

    ps.setInt(1, senderId);
    ps.setInt(2, recipientId);
    ps.setBigDecimal(3, amount);
    ps.setString(4, reference);

    if (ps.executeUpdate() != 1) {
        throw new SQLException(
                "Transaction record insertion failed");
    }

    try (ResultSet keys = ps.getGeneratedKeys()) {
        if (!keys.next()) {
            throw new SQLException(
                    "Could not retrieve the new transaction ID");
        }
        newTransactionId = keys.getInt(1);
    }
}


con.commit();

// Scan only the transaction that was just committed.
// Use a separate connection so scanning cannot roll back the transfer.
try (Connection scanConnection =
        DriverManager.getConnection(dbUrl, dbUser, dbPassword)) {

    int alertsCreated = FraudDetectionEngine.scanTransaction(
            scanConnection, newTransactionId);

    System.out.println(
        "Automatic fraud scan completed for transaction "
        + newTransactionId + ". New alerts created: " + alertsCreated);

} catch (Exception scanEx) {
    System.err.println(
        "Automatic fraud scan failed for transaction "
        + newTransactionId + ": "
        + scanEx.getClass().getSimpleName());
}

sendJson(exchange, 200,
        "{\"success\":true,"
        + "\"amount\":" + jsonString(amount.toPlainString()) + ","
        + "\"reference\":" + jsonString(reference) + ","
        + "\"message\":\"Transfer completed\"}");


        } catch (Exception ex) {
            try {
                con.rollback();
            } catch (SQLException rollbackEx) {
                System.err.println(
                        "Transfer rollback failed: "
                        + rollbackEx.getClass().getSimpleName());
            }

            throw ex;
        }

    } catch (Exception ex) {
        System.err.println("Transfer endpoint failed: "
                + ex.getClass().getSimpleName());

        try {
            sendJson(exchange, 500,
                    "{\"error\":\"Transfer could not be completed\"}");
        } catch (IOException responseEx) {
            System.err.println("Transfer response failed");
        }

    } finally {
        if (con != null) {
            try {
                con.close();
            } catch (SQLException ex) {
                System.err.println("Transfer connection close failed");
            }
        }

        exchange.close();
    }
}

    private static WebAuth.Session requireCustomer(
        HttpExchange exchange) throws IOException {

    WebAuth.Session session = getSession(exchange);

    if (session == null) {
        sendJson(exchange, 401, "{\"error\":\"Authentication required\"}");
        return null;
    }

    if (!"CUSTOMER".equalsIgnoreCase(session.getRole())) {
        sendJson(exchange, 403, "{\"error\":\"Customer access required\"}");
        return null;
    }

    return session;
}


private static WebAuth.Session requireInvestigator(
        HttpExchange exchange) throws IOException {

    WebAuth.Session session = getSession(exchange);

    if (session == null) {
        sendJson(exchange, 401,
                "{\"error\":\"Authentication required\"}");
        return null;
    }

    if (!"INVESTIGATOR".equalsIgnoreCase(session.getRole())) {
        sendJson(exchange, 403,
                "{\"error\":\"Investigator access required\"}");
        return null;
    }

    return session;
}



    private static WebAuth.Session getSession(HttpExchange exchange) {
        return WebAuth.getSession(readSessionToken(exchange));
    }

    private static String readSessionToken(HttpExchange exchange) {
        String cookieHeader =
                exchange.getRequestHeaders().getFirst("Cookie");

        if (cookieHeader == null) {
            return null;
        }

        for (String part : cookieHeader.split(";")) {
            String item = part.trim();
            int equals = item.indexOf('=');

            if (equals > 0
                    && COOKIE_NAME.equals(item.substring(0, equals))) {
                String token = item.substring(equals + 1);

                if (token.matches("[A-Za-z0-9_-]{43}")) {
                    return token;
                }
            }
        }

        return null;
    }

    private static boolean requireSameOrigin(HttpExchange exchange)
            throws IOException {
        String origin = exchange.getRequestHeaders().getFirst("Origin");

        if (EXPECTED_ORIGIN.equals(origin)) {
            return true;
        }

        return false;
    }

    private static boolean requireMethod(
            HttpExchange exchange, String method) throws IOException {

        if (method.equalsIgnoreCase(exchange.getRequestMethod())) {
            return true;
        }

        exchange.getResponseHeaders().set("Allow", method);
        sendJson(exchange, 405, "{\"error\":\"Method not allowed\"}");
        return false;
    }

    private static String mediaType(String contentType) {
        if (contentType == null) {
            return "";
        }

        int semicolon = contentType.indexOf(';');
        return (semicolon >= 0
                ? contentType.substring(0, semicolon)
                : contentType).trim();
    }

    private static byte[] readLimitedBody(HttpExchange exchange)
            throws IOException {

        try (InputStream in = exchange.getRequestBody();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            byte[] buffer = new byte[1024];
            int total = 0;
            int count;

            while ((count = in.read(buffer)) != -1) {
                total += count;

                if (total > MAX_BODY_BYTES) {
                    return null;
                }

                out.write(buffer, 0, count);
            }

            return out.toByteArray();
        }
    }

    private static Map<String, String> parseForm(String body) {
        Map<String, String> result = new HashMap<>();

        if (body.isEmpty()) {
            return result;
        }

        for (String pair : body.split("&", -1)) {
            int equals = pair.indexOf('=');

            String key = URLDecoder.decode(
                    equals < 0 ? pair : pair.substring(0, equals),
                    StandardCharsets.UTF_8);

            String value = URLDecoder.decode(
                    equals < 0 ? "" : pair.substring(equals + 1),
                    StandardCharsets.UTF_8);

            if (result.putIfAbsent(key, value) != null) {
                throw new IllegalArgumentException("Duplicate form field");
            }
        }

        return result;
    }

    private static boolean isLockedOut(String client) {
        LoginLimit limit = LOGIN_LIMITS.get(client);

        if (limit == null) {
            return false;
        }

        synchronized (limit) {
            long now = System.currentTimeMillis();

            if (limit.lockedUntil > now) {
                return true;
            }

            if (limit.lockedUntil != 0) {
                limit.failures = 0;
                limit.lockedUntil = 0;
            }

            return false;
        }
    }

    private static void recordFailure(String client) {
        LoginLimit limit = LOGIN_LIMITS.computeIfAbsent(
                client, key -> new LoginLimit());

        synchronized (limit) {
            limit.failures++;

            if (limit.failures >= MAX_LOGIN_ATTEMPTS) {
                limit.lockedUntil =
                        System.currentTimeMillis() + LOCKOUT_MS;
            }
        }
    }

    private static void clearFailures(String client) {
        LOGIN_LIMITS.remove(client);
    }

   private static String jsonString(String value) {
    if (value == null) {
        return "null";
    }

    StringBuilder out = new StringBuilder("\"");

    for (char c : value.toCharArray()) {
        switch (c) {
            case '"': out.append("\\\""); break;
            case '\\': out.append("\\\\"); break;
            case '\b': out.append("\\b"); break;
            case '\f': out.append("\\f"); break;
            case '\n': out.append("\\n"); break;
            case '\r': out.append("\\r"); break;
            case '\t': out.append("\\t"); break;
            default:
                if (c < 0x20) {
                    out.append(String.format("\\u%04x", (int) c));
                } else {
                    out.append(c);
                }
        }
    }

    return out.append('"').toString();
}
    private static void dashboard(HttpExchange exchange) throws IOException {
        try {
            if (!requireMethod(exchange, "GET")) {
                return;
            }

            String path = exchange.getRequestURI().getPath();


String page;

if ("/".equals(path) || "/index.html".equals(path)) {
    page = "index.html";
} else if ("/investigator.html".equals(path)) {
    page = "investigator.html";
} else {
    sendText(exchange, 404, "Page not found");
    return;
}

Path pagePath = WEB_ROOT.resolve(page).normalize();

if (!pagePath.startsWith(WEB_ROOT)
        || !Files.isRegularFile(pagePath)) {
    sendText(exchange, 404, "Page not found");
    return;
}

byte[] html = Files.readAllBytes(pagePath);


            exchange.getResponseHeaders().set(
                    "Content-Type", "text/html; charset=UTF-8");
            exchange.getResponseHeaders().set("Cache-Control", "no-store");

            exchange.sendResponseHeaders(200, html.length);

            try (var out = exchange.getResponseBody()) {
                out.write(html);
            }
        } finally {
            exchange.close();
        }
    }

    private static void sendJson(
            HttpExchange exchange, int status, String json)
            throws IOException {

        byte[] response = json.getBytes(StandardCharsets.UTF_8);

        Headers headers = exchange.getResponseHeaders();
        headers.set("Content-Type", "application/json; charset=UTF-8");
        headers.set("Cache-Control", "no-store");
        headers.set("X-Content-Type-Options", "nosniff");

        exchange.sendResponseHeaders(status, response.length);

        try (var out = exchange.getResponseBody()) {
            out.write(response);
        }
    }

    private static void sendText(
            HttpExchange exchange, int status, String message)
            throws IOException {

        byte[] response = message.getBytes(StandardCharsets.UTF_8);

        exchange.getResponseHeaders().set(
                "Content-Type", "text/plain; charset=UTF-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");

        exchange.sendResponseHeaders(status, response.length);

        try (var out = exchange.getResponseBody()) {
            out.write(response);
        }
    }
}
