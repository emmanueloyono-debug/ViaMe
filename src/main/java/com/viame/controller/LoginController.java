package com.viame.controller;

import com.viame.MainApp;
import com.viame.exception.AuthenticationException;
import com.viame.exception.DatabaseException;
import com.viame.model.User;
import com.viame.service.AuthenticationService;
import com.viame.util.InputValidator;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;

/**
 * JavaFX Controller for {@code LoginView.fxml} (UC01: Authenticate & Role-Based Routing).
 * Performs client-side syntax validation, delegates BCrypt authentication to
 * {@link AuthenticationService}, and routes authenticated users to their role dashboard.
 */
public class LoginController {

    private static final String DEMO_PASSWORD = "Password123!";

    @FXML
    protected TextField emailField;

    @FXML
    protected PasswordField passwordField;

    @FXML
    protected Label feedbackLabel;

    @FXML
    protected Button loginButton;

    private final AuthenticationService authenticationService;

    public LoginController() {
        this(new AuthenticationService());
    }

    public LoginController(AuthenticationService authenticationService) {
        this.authenticationService = authenticationService;
    }

    @FXML
    public void initialize() {
        hideFeedback();
    }

    @FXML
    public void handleLogin(ActionEvent event) {
        hideFeedback();
        String email = emailField != null ? emailField.getText() : "";
        String password = passwordField != null ? passwordField.getText() : "";

        if (email == null || email.isBlank() || password == null || password.isBlank()) {
            showError("Please enter both your email address and password.");
            return;
        }
        if (!InputValidator.isValidEmail(email.trim())) {
            showError("Please enter a valid email address (e.g. user@viame.cm).");
            return;
        }

        try {
            User user = authenticationService.login(email.trim(), password);
            MainApp.routeByRole(user);
        } catch (AuthenticationException e) {
            showError(e.getMessage());
        } catch (DatabaseException e) {
            showError("Database unavailable: " + e.getMessage());
        } catch (Exception e) {
            showError("Unexpected error during sign-in: " + e.getMessage());
        }
    }

    @FXML
    public void handleNavigateToRegister(ActionEvent event) {
        MainApp.showRegisterView();
    }

    @FXML
    public void handleFillPassengerDemo(ActionEvent event) {
        fillCredentials("passenger.alice@viame.cm", DEMO_PASSWORD);
    }

    @FXML
    public void handleFillDriverDemo(ActionEvent event) {
        fillCredentials("driver.samuel@viame.cm", DEMO_PASSWORD);
    }

    @FXML
    public void handleFillAdminDemo(ActionEvent event) {
        fillCredentials("admin@viame.cm", DEMO_PASSWORD);
    }

    private void fillCredentials(String email, String password) {
        hideFeedback();
        if (emailField != null) emailField.setText(email);
        if (passwordField != null) passwordField.setText(password);
    }

    protected void showError(String message) {
        if (feedbackLabel != null) {
            feedbackLabel.getStyleClass().removeAll("success-label");
            if (!feedbackLabel.getStyleClass().contains("error-label")) {
                feedbackLabel.getStyleClass().add("error-label");
            }
            feedbackLabel.setText(message);
            feedbackLabel.setVisible(true);
            feedbackLabel.setManaged(true);
        }
    }

    protected void hideFeedback() {
        if (feedbackLabel != null) {
            feedbackLabel.setText("");
            feedbackLabel.setVisible(false);
            feedbackLabel.setManaged(false);
        }
    }
}

