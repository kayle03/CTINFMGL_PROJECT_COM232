package controller;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import database.DatabaseConnection;
import utils.SessionManager;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class HomeController {

    @FXML private Label profileName;
    @FXML private Label profileId;
    @FXML private ImageView profilePicture;
    @FXML private ListView<HBox> friendListView;
    @FXML private TextField friendUsernameField;
    @FXML private ListView<HBox> friendRequestsList; // Added this line
    @FXML private ListView<HBox> serverInvitesList;

    @FXML
    public void initialize() {
        loadFriendsList(); // Load the friends list
        loadFriendRequests(); // Load friend requests
        loadServerInvites(); // Load server invites
    }

    // Method to set the username and user ID in the HomeController
    public void setUsername(String username, int userId) {
        profileName.setText(username);
        profileId.setText("#" + userId); // Set the user ID
    }

    public void initializeFriendList(ObservableList<String> friendUsernames) {
        friendListView.getItems().clear(); // Clear existing items
        friendListView.getItems().addAll(friendUsernames); // Add all friend usernames
    }

    // Method to load the friends list
    public void loadFriendsList() {
        int currentUserId = SessionManager.getCurrentUserId();
        if (currentUserId == -1) {
            showAlert("Error", "No user is logged in.");
            return;
        }

        System.out.println("Loading friends list for user ID: " + currentUserId);

        // Load the logged-in user's profile picture
        String userQuery = "SELECT profile_picture FROM users WHERE id = ?";
        try (Connection conn = DatabaseConnection.connect();
             PreparedStatement userStmt = conn.prepareStatement(userQuery)) {

            userStmt.setInt(1, currentUserId);
            ResultSet userRs = userStmt.executeQuery();

            if (userRs.next()) {
                String profilePicPath = userRs.getString("profile_picture");
                if (profilePicPath != null && !profilePicPath.isEmpty()) {
                    profilePicture.setImage(new javafx.scene.image.Image(profilePicPath));
                }
            }
        } catch (SQLException e) {
            showAlert("Database Error", "Failed to load user profile picture.");
            e.printStackTrace();
        }

        // Load the friends list
        String query = "SELECT u.username, u.id, u.profile_picture FROM friends f " +
                       "JOIN users u ON (f.friend_user_id = u.id OR f.user_id = u.id) " +
                       "WHERE (f.user_id = ? OR f.friend_user_id = ?) AND f.status = 'accepted' " +
                       "AND u.id != ?";

        try (Connection conn = DatabaseConnection.connect();
             PreparedStatement stmt = conn.prepareStatement(query)) {

            stmt.setInt(1, currentUserId);
            stmt.setInt(2, currentUserId);
            stmt.setInt(3, currentUserId);

            ResultSet rs = stmt.executeQuery();

            System.out.println("Friends found: " + rs.getRow()); // Debug: Check if any rows are returned

            friendListView.getItems().clear();

            while (rs.next()) {
                String friendUsername = rs.getString("username");
                int friendId = rs.getInt("id");
                String profilePicPath = rs.getString("profile_picture");

                System.out.println("Friend: " + friendUsername + " #" + friendId); // Debug: Print friend details

                // Create UI components
                ImageView friendProfilePic = new ImageView();
                friendProfilePic.setFitHeight(40);
                friendProfilePic.setFitWidth(40);

                if (profilePicPath != null && !profilePicPath.isEmpty()) {
                    friendProfilePic.setImage(new javafx.scene.image.Image(profilePicPath));
                }

                Label friendLabel = new Label(friendUsername + " #" + friendId);
                friendLabel.setStyle("-fx-text-fill: white; -fx-font-size: 14px;");

                HBox friendItem = new HBox(friendProfilePic, friendLabel);
                friendItem.setSpacing(10);

                // Add to ListView
                friendListView.getItems().add(friendItem);
            }
        } catch (SQLException e) {
            showAlert("Database Error", "Failed to load friends.");
            e.printStackTrace();
        }
    }

    @FXML
    public void sendFriendRequest() {
        String input = friendUsernameField.getText().trim();

        if (input.isEmpty() || !input.contains("#")) {
            showAlert("Error", "Please enter a valid username and ID in the format username#ID.");
            return;
        }

        String[] parts = input.split("#");
        if (parts.length != 2) {
            showAlert("Error", "Invalid format. Use username#ID (e.g., kay#2).");
            return;
        }

        String username = parts[0];
        int enteredUserId;
        try {
            enteredUserId = Integer.parseInt(parts[1]);
        } catch (NumberFormatException e) {
            showAlert("Error", "Invalid ID. Please enter a numeric user ID.");
            return;
        }

        int currentUserId = SessionManager.getCurrentUserId();

        String getUserIdQuery = "SELECT id FROM users WHERE username = ? AND id = ?";
        try (Connection conn = DatabaseConnection.connect();
             PreparedStatement getUserStmt = conn.prepareStatement(getUserIdQuery)) {

            getUserStmt.setString(1, username);
            getUserStmt.setInt(2, enteredUserId);
            ResultSet rs = getUserStmt.executeQuery();

            if (rs.next()) {
                int friendUserId = rs.getInt("id");

                if (friendUserId == currentUserId) {
                    showAlert("Error", "You cannot add yourself as a friend.");
                    return;
                }

                String checkExistingRequest = "SELECT * FROM friends WHERE (user_id = ? AND friend_user_id = ?) OR (user_id = ? AND friend_user_id = ?)";
                try (PreparedStatement checkStmt = conn.prepareStatement(checkExistingRequest)) {
                    checkStmt.setInt(1, currentUserId);
                    checkStmt.setInt(2, friendUserId);
                    checkStmt.setInt(3, friendUserId);
                    checkStmt.setInt(4, currentUserId);
                    ResultSet checkResult = checkStmt.executeQuery();

                    if (checkResult.next()) {
                        showAlert("Info", "You are already friends or have a pending request.");
                        return;
                    }
                }

                String insertFriendQuery = "INSERT INTO friends (user_id, friend_user_id, status) VALUES (?, ?, 'pending')";
                try (PreparedStatement insertStmt = conn.prepareStatement(insertFriendQuery)) {
                    insertStmt.setInt(1, currentUserId);
                    insertStmt.setInt(2, friendUserId);
                    insertStmt.executeUpdate();
                    showAlert("Success", "Friend request sent to " + username + " (#" + friendUserId + ").");
                    loadFriendsList(); // Refresh friend requests list
                }

            } else {
                showAlert("Error", "User not found or ID does not match the username.");
            }
        } catch (SQLException e) {
            showAlert("Database Error", "Failed to send friend request.");
            e.printStackTrace();
        }
    }

    @FXML
    public void loadFriendRequests() {
        int currentUserId = SessionManager.getCurrentUserId();
        System.out.println("Loading friend requests for user ID: " + currentUserId);

        String query = "SELECT u.username, f.user_id FROM friends f " +
                       "JOIN users u ON f.user_id = u.id " +
                       "WHERE f.friend_user_id = ? AND f.status = 'pending'";

        try (Connection conn = DatabaseConnection.connect();
             PreparedStatement stmt = conn.prepareStatement(query)) {

            stmt.setInt(1, currentUserId);
            ResultSet rs = stmt.executeQuery();

            System.out.println("Friend requests found: " + rs.getRow()); // Debug: Check if any rows are returned

            friendRequestsList.getItems().clear();

            while (rs.next()) {
                int senderId = rs.getInt("user_id");
                String friendRequest = rs.getString("username") + "#" + senderId;

                System.out.println("Friend Request: " + friendRequest); // Debug: Print friend request details

                // Buttons for Accept and Decline
                Button acceptButton = new Button("✅");
                Button declineButton = new Button("❌");

                acceptButton.setOnAction(event -> acceptFriendRequest(senderId));
                declineButton.setOnAction(event -> declineFriendRequest(senderId));

                HBox requestItem = new HBox(new Label(friendRequest), acceptButton, declineButton);
                requestItem.setSpacing(10);

                friendRequestsList.getItems().add(requestItem);
            }
        } catch (SQLException e) {
            showAlert("Database Error", "Failed to load friend requests.");
            e.printStackTrace();
        }
    }

    private void acceptFriendRequest(int senderId) {
        int currentUserId = SessionManager.getCurrentUserId();
        String query = "UPDATE friends SET status = 'accepted' WHERE user_id = ? AND friend_user_id = ?";

        try (Connection conn = DatabaseConnection.connect();
             PreparedStatement stmt = conn.prepareStatement(query)) {

            stmt.setInt(1, senderId);
            stmt.setInt(2, currentUserId);
            stmt.executeUpdate();
            showAlert("Success", "Friend request accepted.");
            loadFriendRequests(); // Refresh the friend requests list
        } catch (SQLException e) {
            showAlert("Database Error", "Failed to accept friend request.");
            e.printStackTrace();
        }
    }

    private void declineFriendRequest(int senderId) {
        int currentUserId = SessionManager.getCurrentUserId();
        String query = "DELETE FROM friends WHERE user_id = ? AND friend_user_id = ? AND status = 'pending'";

        try (Connection conn = DatabaseConnection.connect();
             PreparedStatement stmt = conn.prepareStatement(query)) {

            stmt.setInt(1, senderId);
            stmt.setInt(2, currentUserId);
            stmt.executeUpdate();
            showAlert("Info", "Friend request declined.");
            loadFriendRequests();
        } catch (SQLException e) {
            showAlert("Database Error", "Failed to decline friend request.");
            e.printStackTrace();
        }
    }

    @FXML
    public void loadServerInvites() {
        int currentUserId = SessionManager.getCurrentUserId();
        System.out.println("Loading server invites for user ID: " + currentUserId);

        String query = "SELECT s.name, i.server_id FROM server_invites i " +
                       "JOIN servers s ON i.server_id = s.id " +
                       "WHERE i.user_id = ? AND i.status = 'pending'";

        try (Connection conn = DatabaseConnection.connect();
             PreparedStatement stmt = conn.prepareStatement(query)) {

            stmt.setInt(1, currentUserId);
            ResultSet rs = stmt.executeQuery();

            System.out.println("Server invites found: " + rs.getRow()); // Debug: Check if any rows are returned

            serverInvitesList.getItems().clear();

            while (rs.next()) {
                int serverId = rs.getInt("server_id");
                String invite = rs.getString("name") + " (Server ID: " + serverId + ")";

                System.out.println("Server Invite: " + invite); // Debug: Print server invite details

                // Buttons for Accept and Decline
                Button acceptButton = new Button("✅");
                Button declineButton = new Button("❌");

                acceptButton.setOnAction(event -> acceptServerInvite(serverId));
                declineButton.setOnAction(event -> declineServerInvite(serverId));

                HBox inviteItem = new HBox(new Label(invite), acceptButton, declineButton);
                inviteItem.setSpacing(10);

                serverInvitesList.getItems().add(inviteItem);
            }
        } catch (SQLException e) {
            showAlert("Database Error", "Failed to load server invites.");
            e.printStackTrace();
        }
    }

    private void acceptServerInvite(int serverId) {
        int currentUserId = SessionManager.getCurrentUserId();
        String query = "UPDATE server_invites SET status = 'accepted' WHERE server_id = ? AND user_id = ?";

        try (Connection conn = DatabaseConnection.connect();
             PreparedStatement stmt = conn.prepareStatement(query)) {

            stmt.setInt(1, serverId);
            stmt.setInt(2, currentUserId);
            stmt.executeUpdate();
            showAlert("Success", "You have joined the server.");
            loadServerInvites();
        } catch (SQLException e) {
            showAlert("Database Error", "Failed to accept server invite.");
            e.printStackTrace();
        }
    }

    private void declineServerInvite(int serverId) {
        int currentUserId = SessionManager.getCurrentUserId();
        String query = "DELETE FROM server_invites WHERE server_id = ? AND user_id = ? AND status = 'pending'";

        try (Connection conn = DatabaseConnection.connect();
             PreparedStatement stmt = conn.prepareStatement(query)) {

            stmt.setInt(1, serverId);
            stmt.setInt(2, currentUserId);
            stmt.executeUpdate();
            showAlert("Info", "Server invite declined.");
            loadServerInvites();
        } catch (SQLException e) {
            showAlert("Database Error", "Failed to decline server invite.");
            e.printStackTrace();
        }
    }

    @FXML
    public void changeProfilePicture() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Choose Profile Picture");
        fileChooser.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("Image Files", "*.jpg", "*.jpeg", "*.png", "*.gif")
        );

        File selectedFile = fileChooser.showOpenDialog(new Stage());
        if (selectedFile != null) {
            String imagePath = selectedFile.toURI().toString(); // Convert to a string path

            int userId = SessionManager.getCurrentUserId();
            if (userId == -1) {
                showAlert("Error", "No user is logged in.");
                return;
            }

            // Update the profile picture in the database
            String updateQuery = "UPDATE users SET profile_picture = ? WHERE id = ?";
            try (Connection conn = DatabaseConnection.connect();
                 PreparedStatement stmt = conn.prepareStatement(updateQuery)) {

                stmt.setString(1, imagePath);
                stmt.setInt(2, userId);
                stmt.executeUpdate();

                // Update UI immediately
                profilePicture.setImage(new javafx.scene.image.Image(imagePath));
                showAlert("Success", "Profile picture updated successfully!");
            } catch (SQLException e) {
                showAlert("Database Error", "Failed to save profile picture.");
                e.printStackTrace();
            }
        } else {
            showAlert("Info", "No file selected.");
        }
    }

    @FXML
    public void goToDirectMessage() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/DirectMessage.fxml"));
            Parent root = loader.load();

            // Get the controller for the DirectMessage.fxml
            DirectMessageController directMessageController = loader.getController();

            // Extract usernames from the HBox elements
            ObservableList<String> friendUsernames = FXCollections.observableArrayList();
            for (HBox friendItem : friendListView.getItems()) {
                Label friendLabel = (Label) friendItem.getChildren().get(1); // Assuming the label is the second child
                friendUsernames.add(friendLabel.getText());
            }

            // Pass the friend list data to the DirectMessageController
            directMessageController.initializeFriendList(friendUsernames);

            // Set the scene
            Stage stage = (Stage) profileName.getScene().getWindow();
            stage.setScene(new Scene(root));
            stage.show();
        } catch (IOException e) {
            showAlert("Error", "Failed to load Direct Message page.");
            e.printStackTrace();
        }
    }

    @FXML
    public void goToServer() {
        navigateTo("/fxml/Server.fxml");
    }

    @FXML
    public void logout() {
        SessionManager.logout();
        navigateTo("/fxml/LoginPage.fxml");
    }

    private void navigateTo(String fxmlFile) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlFile));
            Parent root = loader.load();
            Stage stage = (Stage) profileName.getScene().getWindow();
            stage.setScene(new Scene(root));
            stage.show();
        } catch (IOException e) {
            showAlert("Error", "Failed to load page: " + fxmlFile);
            e.printStackTrace();
        }
    }

    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}