    package controller;

    import javafx.fxml.FXML;
    import javafx.fxml.FXMLLoader;
    import javafx.scene.Parent;
    import javafx.scene.Scene;
    import javafx.stage.Stage;
    import javafx.scene.control.*;
    import javafx.scene.image.ImageView;
    import javafx.scene.layout.VBox;
    import database.DatabaseConnection;
    import utils.SessionManager;
    import java.io.IOException;
    import java.sql.Connection;
    import java.sql.PreparedStatement;
    import java.sql.ResultSet;
    import java.sql.SQLException;
    import javafx.collections.FXCollections;
    import javafx.collections.ObservableList;

    public class DirectMessageController {

        @FXML private Button homeButton;
        @FXML private Button directMessageButton;
        @FXML private Button serverButton;
        @FXML private Button logoutButton;
        @FXML private TextField searchUserField;
        @FXML private TextField messageField;
        @FXML private ListView<String> friendListView; // ListView to display friends
        @FXML private VBox messageContainer; // Container to display messages
        @FXML private Label userNameLabel; // Label to display the current user's name
        @FXML private Label userIdLabel; // Label to display the current user's ID
        @FXML private ImageView userProfilePicture; // ImageView to display the current user's profile picture

        @FXML
        public void initialize() {
            // Add a listener to handle friend selection
            friendListView.getSelectionModel().selectedItemProperty().addListener((obs, oldSelection, newSelection) -> {
                if (newSelection != null) {
                    // Handle friend selection (e.g., load messages for the selected friend)
                    String selectedFriend = newSelection;
                    System.out.println("Selected friend: " + selectedFriend);
                    loadMessagesForFriend(selectedFriend); // Load messages for the selected friend
                }
            });

            // Ensure the home button is properly initialized
            if (homeButton != null) {
                homeButton.sceneProperty().addListener((obs, oldScene, newScene) -> {
                    if (newScene != null && newScene.getWindow() instanceof Stage) {
                        Stage stage = (Stage) newScene.getWindow();
                        if (stage != null) {
                            stage.setResizable(true); // Allow resizing but do not force maximization
                        }
                    }
                });
            } else {
                System.out.println("homeButton is NULL! Check FXML.");
            }
        }

        /**
         * Initializes the friend list with the provided usernames.
         *
         * @param friendUsernames The list of friend usernames to display.
         */
        public void initializeFriendList(ObservableList<String> friendUsernames) {
            friendListView.getItems().clear(); // Clear existing items
            friendListView.getItems().addAll(friendUsernames); // Add all friend usernames
        }

        @FXML
        public void searchUser() {
            String username = searchUserField.getText().trim();

            if (username.isEmpty()) {
                showAlert("Error", "Please enter a username.");
                return;
            }

            String query = "SELECT id, username FROM users WHERE username = ?";
            try (Connection conn = DatabaseConnection.connect();
                PreparedStatement stmt = conn.prepareStatement(query)) {

                stmt.setString(1, username);
                ResultSet rs = stmt.executeQuery();

                if (rs.next()) {
                    String foundUsername = rs.getString("username");
                    // Add the found user to the friendListView if not already present
                    if (!friendListView.getItems().contains(foundUsername)) {
                        friendListView.getItems().add(foundUsername);
                    } else {
                        showAlert("Info", "User is already in your friend list.");
                    }
                } else {
                    showAlert("Error", "User not found.");
                }
            } catch (SQLException e) {
                showAlert("Database Error", "Failed to search for user.");
                e.printStackTrace();
            }
        }

        @FXML
        public void sendMessage() {
            String message = messageField.getText().trim();
        
            if (message.isEmpty()) {
                showAlert("Error", "Message cannot be empty.");
                return;
            }
        
            int receiverId = getSelectedUserId();
            if (receiverId == -1) {
                showAlert("Error", "No friend selected.");
                return;
            }
        
            String query = "INSERT INTO direct_messages (sender_id, receiver_id, content) VALUES (?, ?, ?)";
            try (Connection conn = DatabaseConnection.connect();
                 PreparedStatement stmt = conn.prepareStatement(query)) {
        
                stmt.setInt(1, SessionManager.getCurrentUserId());
                stmt.setInt(2, receiverId);
                stmt.setString(3, message);
                stmt.executeUpdate();
        
                // Display the sent message in the message container
                messageContainer.getChildren().add(new Label("You: " + message));
                messageField.clear();
            } catch (SQLException e) {
                showAlert("Database Error", "Failed to send message.");
                e.printStackTrace();
            }
        }
        

        @FXML
        public void goToHome() {
            navigateTo("/fxml/Home.fxml");
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

        /**
         * Navigates to the specified FXML file.
         *
         * @param fxmlFile The path to the FXML file.
         */
        private void navigateTo(String fxmlFile) {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlFile));
                Parent root = loader.load();
                Stage stage = (Stage) homeButton.getScene().getWindow();
                stage.setScene(new Scene(root));
                stage.show();
            } catch (IOException e) {
                showAlert("Error", "Failed to load page: " + fxmlFile);
                e.printStackTrace();
            }
        }

        /**
         * Retrieves the selected friend's user ID.
         *
         * @return The user ID of the selected friend, or -1 if no friend is selected.
         */
        private int getSelectedUserId() {
            String selectedFriend = friendListView.getSelectionModel().getSelectedItem();
            if (selectedFriend == null) {
                return -1; // No friend selected
            }
        
            // Extract the user ID from the selected friend's username (format: username#ID)
            String[] parts = selectedFriend.split("#");
            if (parts.length != 2) {
                showAlert("Error", "Invalid friend format. Expected username#ID.");
                return -1;
            }
        
            try {
                return Integer.parseInt(parts[1]); // Return the user ID
            } catch (NumberFormatException e) {
                showAlert("Error", "Invalid user ID in friend format.");
                return -1;
            }
        }

        /**
         * Loads and displays messages for the selected friend.
         *
         * @param friendUsername The username of the selected friend.
         */
        private void loadMessagesForFriend(String friendUsername) {
            int currentUserId = SessionManager.getCurrentUserId();
            int friendId = getSelectedUserId();
        
            if (friendId == -1) {
                showAlert("Error", "Friend not found.");
                return;
            }
        
            String query = "SELECT content, sender_id FROM direct_messages " +
                           "WHERE (sender_id = ? AND receiver_id = ?) OR (sender_id = ? AND receiver_id = ?) " +
                           "ORDER BY created_at ASC"; // Use `created_at` instead of `timestamp`
        
            try (Connection conn = DatabaseConnection.connect();
                 PreparedStatement stmt = conn.prepareStatement(query)) {
        
                stmt.setInt(1, currentUserId);
                stmt.setInt(2, friendId);
                stmt.setInt(3, friendId);
                stmt.setInt(4, currentUserId);
                ResultSet rs = stmt.executeQuery();
        
                messageContainer.getChildren().clear(); // Clear previous messages
        
                while (rs.next()) {
                    String content = rs.getString("content");
                    int senderId = rs.getInt("sender_id");
        
                    // Display the message with the sender's name
                    String senderName = (senderId == currentUserId) ? "You" : friendUsername.split("#")[0]; // Extract username
                    messageContainer.getChildren().add(new Label(senderName + ": " + content));
                }
            } catch (SQLException e) {
                showAlert("Database Error", "Failed to load messages.");
                e.printStackTrace();
            }
        }

        /**
         * Displays an alert with the specified title and message.
         *
         * @param title   The title of the alert.
         * @param message The message to display.
         */
        private void showAlert(String title, String message) {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.showAndWait();
        }
    }