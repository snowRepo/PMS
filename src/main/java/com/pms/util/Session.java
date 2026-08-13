package com.pms.util;

import com.pms.model.User;

/**
 * Holds the currently logged-in user for the lifetime of the session.
 * All controllers can call Session.current() to check who is logged in
 * and what their role is.
 */
public class Session {

    private static User currentUser;

    private Session() {}

    public static void login(User user) {
        currentUser = user;
    }

    public static void logout() {
        currentUser = null;
    }

    public static User current() {
        return currentUser;
    }

    public static boolean isLoggedIn() {
        return currentUser != null;
    }

    public static boolean isAdmin() {
        return currentUser != null && "admin".equals(currentUser.getRole());
    }

    public static boolean isPharmacist() {
        return currentUser != null &&
               ("admin".equals(currentUser.getRole()) || "pharmacist".equals(currentUser.getRole()));
    }
}
