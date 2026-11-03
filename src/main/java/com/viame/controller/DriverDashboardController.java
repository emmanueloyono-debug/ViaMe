package com.viame.controller;

import com.viame.MainApp;
import com.viame.model.Driver;
import com.viame.model.User;
import com.viame.service.AuthenticationService;
import com.viame.util.SessionManager;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Label;

/**
 * Controller for {@code DriverDashboard.fxml}.
 * Displays authenticated driver metadata, availability status, and earnings summary.
 */
public class DriverDashboardController {

    @FXML
    protected Label driverHeaderLabel;

    @FXML
    protected Label availabilityBadge;

    @FXML
    protected Label earningsLabel;

    @FXML
    protected Label ratingLabel;

    private final AuthenticationService authenticationService = new AuthenticationService();

    @FXML
    public void initialize() {
        SessionManager.getInstance().getCurrentUser().ifPresent(this::bindDriver);
    }

    protected void bindDriver(User user) {
        if (user instanceof Driver driver) {
            if (driverHeaderLabel != null) {
                driverHeaderLabel.setText(driver.getFullName() + " • Licence: " + driver.getLicenceNumber());
            }
            if (availabilityBadge != null) {
                availabilityBadge.setText(driver.getAvailability().name());
            }
            if (earningsLabel != null) {
                earningsLabel.setText(driver.getTotalEarnings().toPlainString() + " XAF");
            }
            if (ratingLabel != null) {
                ratingLabel.setText(driver.getRatingAvg().toPlainString() + " ★");
            }
        } else if (driverHeaderLabel != null) {
            driverHeaderLabel.setText(user.getEmail());
        }
    }

    @FXML
    public void handleLogout(ActionEvent event) {
        authenticationService.logout();
        MainApp.showLoginView();
    }
}

