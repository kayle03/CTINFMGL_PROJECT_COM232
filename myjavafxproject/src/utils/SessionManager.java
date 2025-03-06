package utils;

public class SessionManager {
    private static int currentUserId = -1;

    public static int getCurrentUserId() {
        return currentUserId;
    }

    public static void setCurrentUserId(int userId) {
        currentUserId = userId;
    }

    public static void logout() {
        currentUserId = -1;
    }
}