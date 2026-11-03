package com.viame;

import com.viame.model.User;
import com.viame.util.AlertHelper;
import com.viame.util.SessionManager;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;
import java.net.URL;

/**
 * Primary JavaFX Application Entry Point & Scene Switcher for ViaMe.
 * Supports role-based routing (Passenger, Driver, Admin) and concurrent simulation windows.
 */
public class MainApp extends Application {

    public static final String LOGIN_FXML = "/com/viame/views/LoginView.fxml";
    public static final String REGISTER_FXML = "/com/viame/views/RegisterView.fxml";
    public static final String PASSENGER_DASHBOARD_FXML = "/com/viame/views/PassengerDashboard.fxml";
    public static final String DRIVER_DASHBOARD_FXML = "/com/viame/views/DriverDashboard.fxml";
    public static final String ADMIN_CONSOLE_FXML = "/com/viame/views/AdminConsole.fxml";
    public static final String STYLESHEET = "/com/viame/css/styles.css";

    private static Stage primaryStage;

    @Override
    public void start(Stage stage) {
        primaryStage = stage;
        primaryStage.setMinWidth(960);
        primaryStage.setMinHeight(640);
        showLoginView();
        primaryStage.show();
    }

    public static Stage getPrimaryStage() {
        return primaryStage;
    }

    /**
     * Navigates the primary stage to the Login screen.
     */
    public static void showLoginView() {
        switchScene(LOGIN_FXML, "ViaMe — Sign In", 1020, 660);
    }

    /**
     * Navigates the primary stage to the Registration screen.
     */
    public static void showRegisterView() {
        switchScene(REGISTER_FXML, "ViaMe — Create Account", 1060, 720);
    }

    /**
     * Routes the authenticated user to their role-specific dashboard.
     */
    public static void routeByRole(User user) {
        if (user == null) {
            showLoginView();
            return;
        }
        switch (user.getRole()) {
            case PASSENGER -> switchScene(PASSENGER_DASHBOARD_FXML,
                    "ViaMe — Passenger Console (" + user.getEmail() + ")", 1180, 760);
            case DRIVER -> switchScene(DRIVER_DASHBOARD_FXML,
                    "ViaMe — Driver Portal (" + user.getEmail() + ")", 1180, 760);
            case ADMIN -> switchScene(ADMIN_CONSOLE_FXML,
                    "ViaMe — Dispatcher & Admin Console (" + user.getEmail() + ")", 1240, 800);
        }
    }

    /**
     * Loads an FXML view into the primary stage and attaches the global stylesheet.
     */
    public static void switchScene(String fxmlPath, String title, int width, int height) {
        try {
            Parent root = loadFXML(fxmlPath);
            Scene scene = new Scene(root, width, height);
            attachStylesheet(scene);
            if (primaryStage != null) {
                primaryStage.setTitle(title);
                primaryStage.setScene(scene);
                primaryStage.centerOnScreen();
            }
        } catch (IOException e) {
            AlertHelper.showError("Navigation Error", "Could not load view: " + fxmlPath + "\n" + e.getMessage());
        }
    }

    /**
     * Opens an independent secondary window for multi-role concurrent desktop simulation.
     */
    public static Stage openSimulationWindow(String fxmlPath, String title, int width, int height) {
        try {
            Parent root = loadFXML(fxmlPath);
            Scene scene = new Scene(root, width, height);
            attachStylesheet(scene);
            Stage stage = new Stage();
            stage.setTitle(title);
            stage.setMinWidth(900);
            stage.setMinHeight(620);
            stage.setScene(scene);
            stage.show();
            return stage;
        } catch (IOException e) {
            AlertHelper.showError("Simulation Window Error", "Failed to open window: " + fxmlPath + "\n" + e.getMessage());
            return null;
        }
    }

    public static Parent loadFXML(String fxmlPath) throws IOException {
        URL resource = MainApp.class.getResource(fxmlPath);
        if (resource == null) {
            throw new IOException("FXML resource not found on classpath: " + fxmlPath);
        }
        FXMLLoader loader = new FXMLLoader(resource);
        return loader.load();
    }

    private static void attachStylesheet(Scene scene) {
        URL cssUrl = MainApp.class.getResource(STYLESHEET);
        if (cssUrl != null) {
            scene.getStylesheets().add(cssUrl.toExternalForm());
        }
    }

    @Override
    public void stop() {
        SessionManager.getInstance().logout();
    }

    public static void main(String[] args) {
        launch(args);
    }
}

