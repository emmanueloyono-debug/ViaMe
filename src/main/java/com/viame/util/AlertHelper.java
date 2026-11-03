package com.viame.util;

import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.stage.Window;

import java.util.Optional;

/**
 * Standardized JavaFX modal alert dialog helper for consistent user feedback
 * across all controllers.
 */
public final class AlertHelper {

    private AlertHelper() {
    }

    public static void showInfo(String title, String message) {
        showAlert(Alert.AlertType.INFORMATION, title, message, null);
    }

    public static void showWarning(String title, String message) {
        showAlert(Alert.AlertType.WARNING, title, message, null);
    }

    public static void showError(String title, String message) {
        showAlert(Alert.AlertType.ERROR, title, message, null);
    }

    public static void showError(String title, String message, Window owner) {
        showAlert(Alert.AlertType.ERROR, title, message, owner);
    }

    public static boolean showConfirmation(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        Optional<ButtonType> result = alert.showAndWait();
        return result.isPresent() && result.get() == ButtonType.OK;
    }

    private static void showAlert(Alert.AlertType type, String title, String message, Window owner) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        if (owner != null) {
            alert.initOwner(owner);
        }
        alert.showAndWait();
    }
}

