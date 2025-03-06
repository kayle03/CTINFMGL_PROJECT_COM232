package controller;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import database.DatabaseConnection;
import utils.PasswordUtils;
import utils.SessionManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class LoginController {

    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;

    @FXML
    private void handleLogin() {
        String username = usernameField.getText().trim();
        String password = passwordField.getText().trim();

        if (username.isEmpty() || password.isEmpty()) {
            showAlert("Error", "Please enter both username and password.");
            return;
        }

        String query = "SELECT id, password FROM users WHERE username = ?";
        try (Connection conn = DatabaseConnection.connect();
             PreparedStatement stmt = conn.prepareStatement(query)) {

            stmt.setString(1, username);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                String storedPassword = rs.getString("password");
                int userId = rs.getInt("id");

                if (PasswordUtils.verifyPassword(password, storedPassword)) {
                    SessionManager.setCurrentUserId(userId);
                    goToHome(username, userId); // Pass both username and user ID
                } else {
                    showAlert("Error", "Invalid password.");
                }
            } else {
                showAlert("Error", "User not found.");
            }
        } catch (SQLException e) {
            showAlert("Database Error", "Failed to connect to the database.");
            e.printStackTrace();
        }
    }

    private void goToHome(String username, int userId) {
        try {
            // Load the Home FXML
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/Home.fxml"));
            Parent root = loader.load();

            // Get the HomeController and pass the logged-in user's data
            HomeController homeController = loader.getController();
            homeController.setUsername(username, userId); // Pass both username and user ID
            homeController.loadFriendsList(); // Load the user's friends list

            // Set the new scene
            Stage stage = (Stage) usernameField.getScene().getWindow();
            stage.setScene(new Scene(root));
            stage.show();
        } catch (Exception e) {
            e.printStackTrace();
            showAlert("Navigation Error", "Failed to load the home screen.");
        }
    }

    @FXML
    private void goToRegister() {
        navigateTo("/fxml/RegPage.fxml");
    }

    private void navigateTo(String fxmlPath) {
        try {
            Parent root = FXMLLoader.load(getClass().getResource(fxmlPath));
            Stage stage = (Stage) usernameField.getScene().getWindow();
            stage.setScene(new Scene(root));
            stage.show();
        } catch (Exception e) {
            showAlert("Navigation Error", "Failed to load the page.");
            e.printStackTrace();
        }
    }

    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}