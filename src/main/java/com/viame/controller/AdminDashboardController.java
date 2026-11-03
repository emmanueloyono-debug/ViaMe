package com.viame.controller;

import com.viame.MainApp;
import com.viame.model.User;
import com.viame.service.AuthenticationService;
import com.viame.util.SessionManager;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Label;

/**
 * Controller for {@code AdminConsole.fxml}.
 * Provides administrative header and multi-role window launchers (fleet, tariffs & BI charts in Sprint 4).
 */
public class AdminDashboardController {

    @FXML
    protected Label adminHeaderLabel;

    private final AuthenticationService authenticationService = new AuthenticationService();

    @FXML
    public void initialize() {
        SessionManager.getInstance().getCurrentUser().ifPresent(this::bindAdmin);
    }

    protected void bindAdmin(User user) {
        if (adminHeaderLabel != null) {
            adminHeaderLabel.setText("Administrator • " + user.getEmail());
        }
    }

    @FXML
    public void handleOpenPassengerWindow(ActionEvent event) {
        MainApp.openSimulationWindow(MainApp.PASSENGER_DASHBOARD_FXML, "ViaMe — Concurrent Passenger Console", 1100, 720);
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

