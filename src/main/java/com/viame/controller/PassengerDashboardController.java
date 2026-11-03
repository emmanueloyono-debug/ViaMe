package com.viame.controller;

import com.viame.MainApp;
import com.viame.model.Passenger;
import com.viame.model.User;
import com.viame.service.AuthenticationService;
import com.viame.util.SessionManager;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Label;

/**
 * Controller for {@code PassengerDashboard.fxml}.
 * Provides session header, wallet summary, and navigation shell (full booking simulation in Sprint 3).
 */
public class PassengerDashboardController {

    @FXML
    protected Label userHeaderLabel;

    @FXML
    protected Label walletBalanceLabel;

    private final AuthenticationService authenticationService = new AuthenticationService();

    @FXML
    public void initialize() {
        SessionManager.getInstance().getCurrentUser().ifPresent(this::bindUser);
    }

    protected void bindUser(User user) {
        if (user instanceof Passenger passenger) {
            if (userHeaderLabel != null) {
                userHeaderLabel.setText(passenger.getFullName() + " (" + passenger.getEmail() + ")");
            }
            if (walletBalanceLabel != null) {
                walletBalanceLabel.setText(passenger.getWalletBalance().toPlainString() + " XAF");
            }
        } else if (userHeaderLabel != null) {
            userHeaderLabel.setText(user.getEmail());
        }
    }

    @FXML
    public void handleOpenDriverWindow(ActionEvent event) {
        MainApp.openSimulationWindow(MainApp.DRIVER_DASHBOARD_FXML, "ViaMe — Concurrent Driver Portal", 1100, 720);
    }

    @FXML
    public void handleLogout(ActionEvent event) {
        authenticationService.logout();
        MainApp.showLoginView();
    }
}

