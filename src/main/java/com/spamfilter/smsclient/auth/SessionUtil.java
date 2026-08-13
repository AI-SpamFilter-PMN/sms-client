package com.spamfilter.smsclient.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

/**
 * Reads/writes the logged-in user's identity on the servlet session.
 */
public final class SessionUtil {

    private static final String USER_ID = "userId";
    private static final String USER_EMAIL = "userEmail";
    private static final String USER_ROLE = "userRole";

    private SessionUtil() {
    }

    public static void login(HttpServletRequest req, String userId, String email, String role) {
        HttpSession session = req.getSession(true);
        session.setAttribute(USER_ID, userId);
        session.setAttribute(USER_EMAIL, email);
        session.setAttribute(USER_ROLE, role);
    }

    public static String currentUserId(HttpServletRequest req) {
        HttpSession session = req.getSession(false);
        return session == null ? null : (String) session.getAttribute(USER_ID);
    }

    public static String currentUserEmail(HttpServletRequest req) {
        HttpSession session = req.getSession(false);
        return session == null ? null : (String) session.getAttribute(USER_EMAIL);
    }

    public static String currentUserRole(HttpServletRequest req) {
        HttpSession session = req.getSession(false);
        return session == null ? null : (String) session.getAttribute(USER_ROLE);
    }

    public static void logout(HttpServletRequest req) {
        HttpSession session = req.getSession(false);
        if (session != null) {
            session.invalidate();
        }
    }
}
