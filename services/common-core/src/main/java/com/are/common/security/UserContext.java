package com.are.common.security;

public class UserContext {
    private static final ThreadLocal<Long> userId = new ThreadLocal<>();
    private static final ThreadLocal<String> userRole = new ThreadLocal<>();

    public static void setUserId(Long id) {
        userId.set(id);
    }

    public static Long getUserId() {
        return userId.get();
    }

    public static void setUserRole(String role) {
        userRole.set(role);
    }

    public static String getUserRole() {
        return userRole.get();
    }

    public static boolean isAdmin() {
        return "ADMIN".equalsIgnoreCase(userRole.get());
    }

    public static void clear() {
        userId.remove();
        userRole.remove();
    }
}
