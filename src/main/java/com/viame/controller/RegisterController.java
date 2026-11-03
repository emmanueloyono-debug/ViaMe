package com.viame.controller;

import com.viame.MainApp;
import com.viame.exception.DatabaseException;
import com.viame.exception.ValidationException;
import com.viame.model.Driver;
import com.viame.model.Passenger;
import com.viame.model.enums.PaymentMethod;
import com.viame.model.enums.UserRole;
import com.viame.service.AuthenticationService;
import com.viame.util.AlertHelper;
import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;

/**
 * JavaFX Controller for {@code RegisterView.fxml} (UC01 & AD-2 Driver Onboarding).
 * Dynamically toggles between Passenger profile fields and Driver + Vehicle onboarding fields.
 */
public class RegisterController {

    @FXML
    private ComboBox<UserRole> roleComboBox;

    @FXML
    private TextField fullNameField;

    @FXML
    private TextField emailField;

    @FXML
    private TextField phoneField;

    @FXML
    private PasswordField passwordField;

    @FXML
    private PasswordField confirmPasswordField;

    @FXML
    private VBox passengerSection;

    @FXML
    private ComboBox<PaymentMethod> paymentMethodComboBox;

    @FXML
    private VBox driverSection;

    @FXML
    private TextField licenceNumberField;

    @FXML
    private TextField plateNumberField;

    @FXML
    private TextField makeField;

    @FXML
    private TextField modelField;

    @FXML
    private Spinner<Integer> seatsSpinner;

    @FXML
    private Label feedbackLabel;

    private final AuthenticationService authenticationService;

    public RegisterController() {
        this(new AuthenticationService());
    }

    public RegisterController(AuthenticationService authenticationService) {
        this.authenticationService = authenticationService;
    }

    @FXML
    public void initialize() {
        hideFeedback();

        if (roleComboBox != null) {
            roleComboBox.setItems(FXCollections.observableArrayList(UserRole.PASSENGER, UserRole.DRIVER));
            roleComboBox.setValue(UserRole.PASSENGER);
            roleComboBox.valueProperty().addListener((obs, oldVal, newVal) -> updateRoleSections(newVal));
        }

        if (paymentMethodComboBox != null) {
            paymentMethodComboBox.setItems(FXCollections.observableArrayList(
                    PaymentMethod.WALLET, PaymentMethod.CASH, PaymentMethod.MOBILE_MONEY, PaymentMethod.CARD));
            paymentMethodComboBox.setValue(PaymentMethod.WALLET);
        }

        if (seatsSpinner != null) {
            seatsSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 15, 4));
        }

        updateRoleSections(UserRole.PASSENGER);
    }

    private void updateRoleSections(UserRole selectedRole) {
        boolean isDriver = selectedRole == UserRole.DRIVER;
        if (passengerSection != null) {
            passengerSection.setVisible(!isDriver);
            passengerSection.setManaged(!isDriver);
        }
        if (driverSection != null) {
            driverSection.setVisible(isDriver);
            driverSection.setManaged(isDriver);
        }
    }

    @FXML
    public void handleRegister(ActionEvent event) {
        hideFeedback();

        String fullName = text(fullNameField);
        String email = text(emailField);
        String phone = text(phoneField);
        String password = text(passwordField);
        String confirmPassword = text(confirmPasswordField);
        UserRole role = roleComboBox != null && roleComboBox.getValue() != null
                ? roleComboBox.getValue() : UserRole.PASSENGER;

        if (!password.equals(confirmPassword)) {
            showError("Password and confirmation password do not match.");
            return;
        }

        try {
            if (role == UserRole.PASSENGER) {
                PaymentMethod method = paymentMethodComboBox != null && paymentMethodComboBox.getValue() != null
                        ? paymentMethodComboBox.getValue() : PaymentMethod.CASH;
                Passenger passenger = authenticationService.registerPassenger(
                        email, phone, password, fullName, method);
                AlertHelper.showInfo("Account Created",
                        "Welcome to ViaMe, " + passenger.getFullName() + "! You can now sign in.");
                MainApp.showLoginView();
            } else {
                String licence = text(licenceNumberField);
                String plate = text(plateNumberField);
                String make = text(makeField);
                String model = text(modelField);
                int seats = seatsSpinner != null && seatsSpinner.getValue() != null ? seatsSpinner.getValue() : 4;

                Driver driver = authenticationService.registerDriver(
                        email, phone, password, fullName, licence, plate, make, model, seats);
                AlertHelper.showInfo("Driver Application Submitted",
                        "Driver profile for " + driver.getFullName() + " created (Status: PENDING).\n"
                        + "An administrator will verify your documents to enable ONLINE dispatching.");
                MainApp.showLoginView();
            }
        } catch (ValidationException e) {
            showError(e.getMessage());
        } catch (DatabaseException e) {
            showError("Database error: " + e.getMessage());
        } catch (Exception e) {
            showError("Registration failed: " + e.getMessage());
        }
    }

    @FXML
    public void handleBackToLogin(ActionEvent event) {
        MainApp.showLoginView();
    }

    private String text(TextField field) {
        return field != null && field.getText() != null ? field.getText().trim() : "";
    }

    private void showError(String message) {
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

    private void hideFeedback() {
        if (feedbackLabel != null) {
            feedbackLabel.setText("");
            feedbackLabel.setVisible(false);
            feedbackLabel.setManaged(false);
        }
    }
}

