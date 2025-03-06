package controller;


public class Friend {
    private String username;
    private int userId;
    private String profilePicturePath;

    public Friend(String username, int userId, String profilePicturePath) {
        this.username = username;
        this.userId = userId;
        this.profilePicturePath = profilePicturePath;
    }

    // Getters
    public String getUsername() {
        return username;
    }

    public int getUserId() {
        return userId;
    }

    public String getProfilePicturePath() {
        return profilePicturePath;
    }

    @Override
    public String toString() {
        return username; // Display username in the ListView
    }
}