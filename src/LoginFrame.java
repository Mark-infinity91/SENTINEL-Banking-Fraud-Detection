
import javax.swing.*;
import java.awt.*;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.security.MessageDigest;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

public class LoginFrame extends JFrame {

    private static final long serialVersionUID = 1L;


    public LoginFrame() {
        // Window settings
        setTitle("SENTINEL | Secure Banking Intelligence");
        setSize(820, 560);
        setMinimumSize(new Dimension(760, 520));
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setResizable(true);

        // SENTINEL color palette
        Color navy = new Color(8, 20, 38);
        Color cardColor = new Color(16, 32, 54);
        Color inputColor = new Color(10, 25, 44);
        Color accentBlue = new Color(22, 119, 210);
        Color cyan = new Color(80, 210, 255);
        Color primaryText = new Color(240, 246, 255);
        Color secondaryText = new Color(151, 174, 204);
        Color borderColor = new Color(43, 67, 96);

        // Main background
        JPanel mainPanel = new JPanel(new GridBagLayout());
        mainPanel.setBackground(navy);
        mainPanel.setBorder(
                BorderFactory.createEmptyBorder(25, 25, 25, 25)
        );

        // Login card
        JPanel card = new JPanel(new GridBagLayout());
        card.setBackground(cardColor);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(borderColor, 1),
                BorderFactory.createEmptyBorder(28, 38, 25, 38)
        ));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 2;
        gbc.weightx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(5, 0, 5, 0);

        // Shield icon
        JLabel shield = new JLabel("\u25C6");
        shield.setFont(new Font("Arial", Font.BOLD, 32));
        shield.setForeground(cyan);
        shield.setHorizontalAlignment(SwingConstants.CENTER);
        gbc.insets = new Insets(0, 0, 2, 0);
        card.add(shield, gbc);

        // Main title
        JLabel title = new JLabel("SENTINEL");
        title.setFont(new Font("Arial", Font.BOLD, 32));
        title.setForeground(primaryText);
        title.setHorizontalAlignment(SwingConstants.CENTER);

        gbc.gridy++;
        gbc.insets = new Insets(0, 0, 4, 0);
        card.add(title, gbc);

        // Subtitle
        JLabel subtitle = new JLabel(
                "FRAUD DETECTION & INTELLIGENCE PLATFORM"
        );
        subtitle.setFont(new Font("Arial", Font.BOLD, 10));
        subtitle.setForeground(cyan);
        subtitle.setHorizontalAlignment(SwingConstants.CENTER);

        gbc.gridy++;
        gbc.insets = new Insets(0, 0, 22, 0);
        card.add(subtitle, gbc);

        // Section heading
        JLabel signInTitle = new JLabel("Secure Access");
        signInTitle.setFont(new Font("Arial", Font.BOLD, 21));
        signInTitle.setForeground(primaryText);

        gbc.gridy++;
        gbc.insets = new Insets(0, 0, 2, 0);
        card.add(signInTitle, gbc);

        JLabel description = new JLabel(
                "Sign in to access your authorized workspace."
        );
        description.setFont(new Font("Arial", Font.PLAIN, 12));
        description.setForeground(secondaryText);

        gbc.gridy++;
        gbc.insets = new Insets(0, 0, 18, 0);
        card.add(description, gbc);

        // Username label
        JLabel usernameLabel = new JLabel("USERNAME");
        usernameLabel.setFont(new Font("Arial", Font.BOLD, 11));
        usernameLabel.setForeground(primaryText);

        gbc.gridy++;
        gbc.insets = new Insets(0, 0, 7, 0);
        card.add(usernameLabel, gbc);

        // Username field
        JTextField usernameField = new JTextField(22);
        usernameField.setFont(new Font("Arial", Font.PLAIN, 14));
        usernameField.setForeground(primaryText);
        usernameField.setBackground(inputColor);
        usernameField.setCaretColor(cyan);
        usernameField.setSelectionColor(accentBlue);
        usernameField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(borderColor, 1),
                BorderFactory.createEmptyBorder(10, 12, 10, 12)
        ));

        gbc.gridy++;
        gbc.insets = new Insets(0, 0, 15, 0);
        card.add(usernameField, gbc);

        // Password label
        JLabel passwordLabel = new JLabel("PASSWORD");
        passwordLabel.setFont(new Font("Arial", Font.BOLD, 11));
        passwordLabel.setForeground(primaryText);

        gbc.gridy++;
        gbc.insets = new Insets(0, 0, 7, 0);
        card.add(passwordLabel, gbc);

        // Password field
        JPasswordField passwordField = new JPasswordField(22);
        passwordField.setFont(new Font("Arial", Font.PLAIN, 14));
        passwordField.setForeground(primaryText);
        passwordField.setBackground(inputColor);
        passwordField.setCaretColor(cyan);
        passwordField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(borderColor, 1),
                BorderFactory.createEmptyBorder(10, 12, 10, 12)
        ));

        gbc.gridy++;
        gbc.insets = new Insets(0, 0, 20, 0);
        card.add(passwordField, gbc);

        // Login button
        JButton loginButton = new JButton("SIGN IN  \u2192");
        loginButton.setFont(new Font("Arial", Font.BOLD, 14));
        loginButton.setForeground(Color.WHITE);
        loginButton.setBackground(accentBlue);
        loginButton.setFocusPainted(false);
        loginButton.setCursor(new Cursor(Cursor.HAND_CURSOR));
        loginButton.setBorder(BorderFactory.createEmptyBorder(13, 15, 13, 15));
        loginButton.setOpaque(true);

        gbc.gridy++;
        gbc.insets = new Insets(0, 0, 18, 0);
        card.add(loginButton, gbc);

        // Footer
        JLabel footer = new JLabel(
                "AUTHORIZED ACCESS ONLY  |  NOVA BANK DEMO"
        );
        footer.setFont(new Font("Arial", Font.PLAIN, 10));
        footer.setForeground(secondaryText);
        footer.setHorizontalAlignment(SwingConstants.CENTER);

        gbc.gridy++;
        gbc.insets = new Insets(0, 0, 0, 0);
        card.add(footer, gbc);

        // Preserve existing login authentication and role routing
        loginButton.addActionListener(e -> {
            String username = usernameField.getText().trim();
            char[] password = passwordField.getPassword();

            if (username.isEmpty() || password.length == 0
                    || new String(password).isBlank()) {
                JOptionPane.showMessageDialog(
                        this,
                        "Please enter your username and password.",
                        "Login Required",
                        JOptionPane.WARNING_MESSAGE
                );
                java.util.Arrays.fill(password, '\0');
                return;
            }

            loginButton.setEnabled(false);
            loginButton.setText("AUTHENTICATING...");
            try {
                String url = System.getenv("SENTINEL_DB_URL");
                String dbUsername = System.getenv("SENTINEL_DB_USER");
                String dbPassword = System.getenv("SENTINEL_DB_PASSWORD");

                if (url == null || dbUsername == null || dbPassword == null) {
                    throw new SQLException(
                            "Database environment variables are not configured.");
                }

                if (url == null || dbUsername == null || dbPassword == null
                        || !url.matches(
                            "(?i)^jdbc:mysql://localhost:3306/sentinel_test_db(?:\\?.*)?$")) {
                    System.out.println(
                        "LOGIN BLOCKED: Only the approved local test database is allowed."
                    );
                    return;
                }

                try (Connection connection =
                            DriverManager.getConnection(url, dbUsername, dbPassword);
                    PreparedStatement statement = connection.prepareStatement(
                            "SELECT password_hash, role, account_status " +
                            "FROM users WHERE username = ?")) {

                    statement.setString(1, username);

                    try (ResultSet result = statement.executeQuery()) {
                        if (!result.next()) {
                            JOptionPane.showMessageDialog(
                                    this, "Invalid username or password.");
                        } else if (!"ACTIVE".equalsIgnoreCase(
                                result.getString("account_status"))) {
                            JOptionPane.showMessageDialog(
                                    this, "This account is disabled.");
                        } else if (verifyPassword(
                                password, result.getString("password_hash"))) {

                            String role = result.getString("role");

                            if ("CUSTOMER".equalsIgnoreCase(role)) {
                                new CustomerDashboard(username).setVisible(true);
                                dispose();
                            } else if ("INVESTIGATOR".equalsIgnoreCase(role)
                                    || "ADMIN".equalsIgnoreCase(role)) {
                                new InvestigatorDashboard(username).setVisible(true);
                                dispose();
                            } else {
                                JOptionPane.showMessageDialog(
                                        this, "Unrecognized account role.");
                            }

                        } else {
                            JOptionPane.showMessageDialog(
                                    this, "Invalid username or password.");
                        }
                    }
                }


            } catch (Exception ex) {
                ex.printStackTrace();

                JOptionPane.showMessageDialog(
                        this,
                        "Login failed. Please check the database configuration and try again.",
                        "Login Error",
                        JOptionPane.ERROR_MESSAGE
                );
            } finally {
                java.util.Arrays.fill(password, '\0');

                if (loginButton.isDisplayable()) {
                    loginButton.setEnabled(true);
                    loginButton.setText("SIGN IN  \u2192");
                }
            }
        });

        // Pressing Enter submits the login form
        getRootPane().setDefaultButton(loginButton);

        // Assemble window
        mainPanel.add(card);
        setContentPane(mainPanel);
    }




    private static boolean verifyPassword(char[] password, String storedHash) {
        if (storedHash == null || password == null) {
            return false;
        }

        try {
            // Accept either "salt:hash" or "iterations:salt:hash" (Base64 fields).
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
		byte[] expectedHash = Base64.getDecoder().decode(parts[saltIndex + 1]);

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

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            LoginFrame frame = new LoginFrame();
            frame.setVisible(true);
        });
    }
}
