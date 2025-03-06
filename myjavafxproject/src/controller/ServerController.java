package controller;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import database.DatabaseConnection;
import utils.SessionManager;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ServerController {

    private static final Logger logger = Logger.getLogger(ServerController.class.getName());

    @FXML private TextField serverNameField;
    @FXML private TextField channelNameField;
    @FXML private TextField messageField;
    @FXML private TextField inviteUserField;
    @FXML private VBox channelContainer;
    @FXML private VBox messageContainer;
    @FXML private ListView<String> userListView;
    @FXML private ListView<String> serverListView;
    @FXML private ListView<String> channelListView;

    // Maps to store IDs for servers, channels, and users
    private final Map<String, Integer> serverIdMap = new HashMap<>();
    private final Map<String, Integer> channelIdMap = new HashMap<>();
    private final Map<String, Integer> userIdMap = new HashMap<>();

    @FXML
    public void initialize() {
        // Validate database schema
        validateDatabaseSchema();

        // Load servers, users, and channels when the controller is initialized
        loadServers();
        loadUsers();
        loadChannels();

        // Add listeners to handle selection changes
        serverListView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                loadChannels(); // Refresh channels when a new server is selected
            }
        });

        channelListView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                loadMessages(); // Refresh messages when a new channel is selected
            }
        });
    }

    // Validate the existence of required tables in the database
    private void validateDatabaseSchema() {
        String[] tables = {"users", "servers", "channels", "channel_messages", "server_members"};
        for (String table : tables) {
            if (!isTableExists(table)) {
                showAlert("Database Error", "Table '" + table + "' does not exist. Please check your database schema.");
                logger.severe("Table '" + table + "' does not exist.");
            }
        }
    }

    // Check if a table exists in the database
    private boolean isTableExists(String tableName) {
        String query = "SELECT 1 FROM " + tableName + " LIMIT 1";
        try (Connection conn = DatabaseConnection.connect();
             PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.executeQuery();
            return true;
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Table '" + tableName + "' does not exist.", e);
            return false;
        }
    }

    @FXML
    public void createServer() {
        String serverName = serverNameField.getText().trim();

        if (serverName.isEmpty()) {
            showAlert("Error", "Server name cannot be empty.");
            return;
        }

        String query = "INSERT INTO servers (name, owner_id) VALUES (?, ?)";
        executeUpdate(query, serverName, SessionManager.getCurrentUserId());
        showAlert("Success", "Server '" + serverName + "' created successfully.");
        serverNameField.clear();
        loadServers(); // Refresh the server list after creating a new server
    }

    @FXML
    public void createChannel() {
        String channelName = channelNameField.getText().trim();

        if (channelName.isEmpty()) {
            showAlert("Error", "Channel name cannot be empty.");
            return;
        }

        int serverId = getSelectedServerId();
        if (serverId == -1) {
            showAlert("Error", "No server selected. Please select a server.");
            return;
        }

        String query = "INSERT INTO channels (name, server_id) VALUES (?, ?)";
        executeUpdate(query, channelName, serverId);
        showAlert("Success", "Channel '" + channelName + "' created successfully.");
        channelNameField.clear();
        loadChannels(); // Refresh the channel list after creating a new channel
    }

    @FXML
    public void sendMessage() {
        String message = messageField.getText().trim();

        if (message.isEmpty()) {
            showAlert("Error", "Message cannot be empty.");
            return;
        }

        int channelId = getSelectedChannelId();
        if (channelId == -1) {
            showAlert("Error", "No channel selected. Please select a channel.");
            return;
        }

        String query = "INSERT INTO channel_messages (channel_id, sender_id, content) VALUES (?, ?, ?)";
        try {
            executeUpdate(query, channelId, SessionManager.getCurrentUserId(), message);
            showAlert("Success", "Message sent successfully.");
            messageField.clear();
            loadMessages(); // Refresh the message list after sending a new message
        } catch (Exception e) {
            showAlert("Error", "Failed to send message: " + e.getMessage());
        }
    }

    @FXML
    public void inviteUserToServer() {
        String username = inviteUserField.getText().trim();

        if (username.isEmpty()) {
            showAlert("Error", "Username cannot be empty.");
            return;
        }

        int serverId = getSelectedServerId();
        if (serverId == -1) {
            showAlert("Error", "No server selected. Please select a server.");
            return;
        }

        int userId = getUserIdByUsername(username);
        if (userId == -1) {
            showAlert("Error", "User '" + username + "' not found.");
            return;
        }

        String query = "INSERT INTO server_members (server_id, user_id) VALUES (?, ?)";
        executeUpdate(query, serverId, userId);
        showAlert("Success", "User '" + username + "' invited to the server.");
        inviteUserField.clear();
        loadUsers(); // Refresh the user list after inviting a new user
    }

    @FXML
    public void loadServers() {
        String query = "SELECT id, name FROM servers WHERE owner_id = ?";
        executeQuery(query, SessionManager.getCurrentUserId(), rs -> {
            serverListView.getItems().clear();
            serverIdMap.clear();
            while (rs.next()) {
                String serverName = rs.getString("name");
                int serverId = rs.getInt("id");
                Platform.runLater(() -> serverListView.getItems().add(serverName));
                serverIdMap.put(serverName, serverId);
            }
        }, "Failed to load servers.");
    }

    @FXML
    public void loadUsers() {
        String query = "SELECT id, username FROM users";
        executeQuery(query, rs -> {
            userListView.getItems().clear();
            userIdMap.clear();
            while (rs.next()) {
                String username = rs.getString("username");
                int userId = rs.getInt("id");
                Platform.runLater(() -> userListView.getItems().add(username));
                userIdMap.put(username, userId);
            }
        }, "Failed to load users.");
    }

    @FXML
    public void loadChannels() {
        int serverId = getSelectedServerId();
        if (serverId == -1) {
            Platform.runLater(() -> channelListView.getItems().clear());
            return;
        }

        String query = "SELECT id, name FROM channels WHERE server_id = ?";
        executeQuery(query, serverId, rs -> {
            channelListView.getItems().clear();
            channelIdMap.clear();
            while (rs.next()) {
                String channelName = rs.getString("name");
                int channelId = rs.getInt("id");
                Platform.runLater(() -> channelListView.getItems().add(channelName));
                channelIdMap.put(channelName, channelId);
            }
        }, "Failed to load channels.");
    }

    @FXML
    public void loadMessages() {
        int channelId = getSelectedChannelId();
        if (channelId == -1) {
            Platform.runLater(() -> messageContainer.getChildren().clear());
            return;
        }

        String query = "SELECT sender_id, content, sent_at FROM channel_messages WHERE channel_id = ? ORDER BY sent_at ASC";
        executeQuery(query, channelId, rs -> {
            Platform.runLater(() -> messageContainer.getChildren().clear());
            while (rs.next()) {
                int senderId = rs.getInt("sender_id");
                String message = rs.getString("content");
                String sentAt = rs.getString("sent_at");

                String senderUsername = getUsernameById(senderId);
                if (senderUsername == null) {
                    senderUsername = "Unknown User";
                }

                String finalMessage = senderUsername + " (" + sentAt + "): " + message;
                Platform.runLater(() -> messageContainer.getChildren().add(new Label(finalMessage)));
            }
        }, "Failed to load messages.");
    }

    @FXML
    public void goToHome() {
        navigateTo("/fxml/Home.fxml", "Failed to go to Home.");
    }

    @FXML
    public void goToDirectMessage() {
        navigateTo("/fxml/DirectMessage.fxml", "Failed to go to Direct Messages.");
    }

    @FXML
    public void logout() {
        try {
            // Clear session data
            SessionManager.logout();

            // Load the login page
            navigateTo("/fxml/LoginPage.fxml", "Failed to log out.");
        } catch (Exception e) {
            showAlert("Error", "Failed to log out.");
            logger.log(Level.SEVERE, "Logout failed.", e);
        }
    }

    // Helper method to get the selected server ID
    private int getSelectedServerId() {
        String selectedServer = serverListView.getSelectionModel().getSelectedItem();
        return selectedServer != null ? serverIdMap.getOrDefault(selectedServer, -1) : -1;
    }

    // Helper method to get the selected channel ID
    private int getSelectedChannelId() {
        String selectedChannel = channelListView.getSelectionModel().getSelectedItem();
        return selectedChannel != null ? channelIdMap.getOrDefault(selectedChannel, -1) : -1;
    }

    // Helper method to get the user ID by username
    private int getUserIdByUsername(String username) {
        return userIdMap.getOrDefault(username, -1);
    }

    // Helper method to get the username by user ID
    private String getUsernameById(int userId) {
        String query = "SELECT username FROM users WHERE id = ?";
        try (Connection conn = DatabaseConnection.connect();
             PreparedStatement stmt = conn.prepareStatement(query)) {

            stmt.setInt(1, userId);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                return rs.getString("username");
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to fetch username by ID.", e);
        }
        return null;
    }

    // Helper method to execute an update query
    private void executeUpdate(String query, Object... params) {
        try (Connection conn = DatabaseConnection.connect();
             PreparedStatement stmt = conn.prepareStatement(query)) {

            // Set parameters
            for (int i = 0; i < params.length; i++) {
                stmt.setObject(i + 1, params[i]);
            }

            // Execute the update
            int rowsAffected = stmt.executeUpdate();
            if (rowsAffected > 0) {
                logger.info("Update successful: " + query);
            } else {
                logger.warning("No rows affected: " + query);
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Database update failed: " + query, e);
            showAlert("Database Error", "Failed to execute update: " + e.getMessage());
        }
    }

    // Helper method to execute a query and process the result set
    private void executeQuery(String query, ResultSetProcessor processor, String errorMessage) {
        try (Connection conn = DatabaseConnection.connect();
             PreparedStatement stmt = conn.prepareStatement(query);
             ResultSet rs = stmt.executeQuery()) {

            processor.process(rs);
        } catch (SQLException e) {
            logger.log(Level.SEVERE, errorMessage, e);
            showAlert("Database Error", errorMessage);
        }
    }

    // Helper method to execute a query with parameters and process the result set
    private void executeQuery(String query, int param, ResultSetProcessor processor, String errorMessage) {
        try (Connection conn = DatabaseConnection.connect();
             PreparedStatement stmt = conn.prepareStatement(query)) {

            stmt.setInt(1, param);
            try (ResultSet rs = stmt.executeQuery()) {
                processor.process(rs);
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, errorMessage, e);
            showAlert("Database Error", errorMessage);
        }
    }

    // Helper method to navigate to a different FXML view
    private void navigateTo(String fxmlPath, String errorMessage) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlPath));
            Parent root = loader.load();

            // Use a non-null field to get the current stage
            Stage stage = (Stage) messageField.getScene().getWindow(); // Use messageField or another non-null field
            if (stage == null) {
                logger.severe("Stage is null. Cannot navigate.");
                return;
            }

            stage.setScene(new Scene(root));
            stage.show();
        } catch (IOException e) {
            logger.log(Level.SEVERE, errorMessage, e);
            showAlert("Error", errorMessage + ": " + e.getMessage());
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Unexpected error during navigation.", e);
            showAlert("Error", "An unexpected error occurred: " + e.getMessage());
        }
    }

    // Helper method to show an alert
    private void showAlert(String title, String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.showAndWait();
        });
    }

    // Functional interface for processing ResultSet
    @FunctionalInterface
    private interface ResultSetProcessor {
        void process(ResultSet rs) throws SQLException;
    }
}