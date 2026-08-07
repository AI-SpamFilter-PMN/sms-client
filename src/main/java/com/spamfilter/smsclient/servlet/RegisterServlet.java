package com.spamfilter.smsclient.servlet;

import com.spamfilter.smsclient.auth.SessionUtil;
import com.spamfilter.smsclient.db.UserRepository;
import com.spamfilter.smsclient.model.User;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.PrintWriter;

/**
 * GET/POST /register - create an account (email + password + optional display name).
 */
public class RegisterServlet extends HttpServlet {

    private final UserRepository userRepository;

    public RegisterServlet(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        render(resp, req.getParameter("error"));
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String email = trim(req.getParameter("email"));
        String password = req.getParameter("password");
        String confirm = req.getParameter("confirm");
        String displayName = trim(req.getParameter("displayName"));

        if (isBlank(email) || !email.contains("@") || isBlank(password)) {
            render(resp, "Enter a valid email and password");
            return;
        }
        if (!password.equals(confirm)) {
            render(resp, "Passwords do not match");
            return;
        }

        try {
            User user = userRepository.register(email, password, isBlank(displayName) ? null : displayName);
            SessionUtil.login(req, user.getId(), user.getEmail());
            resp.sendRedirect("/");
        } catch (IllegalArgumentException e) {
            render(resp, e.getMessage());
        } catch (IllegalStateException e) {
            render(resp, "Registration unavailable: " + e.getMessage());
        }
    }

    private void render(HttpServletResponse resp, String error) throws IOException {
        String body = """
                <div class="card">
                  <h1>Create an account</h1>
                  %s
                  <form method="post">
                    <label for="email">Email</label>
                    <input id="email" name="email" type="email" required>

                    <label for="displayName">Display name (optional)</label>
                    <input id="displayName" name="displayName">

                    <label for="password">Password</label>
                    <input id="password" name="password" type="password" required>

                    <label for="confirm">Confirm password</label>
                    <input id="confirm" name="confirm" type="password" required>

                    <button type="submit">Register</button>
                  </form>
                  <p class="muted">Already have an account? <a class="link" href="/login">Log in</a></p>
                </div>
                """.formatted(error != null ? "<div class=\"banner error\">" + escape(error) + "</div>" : "");

        resp.setContentType("text/html; charset=UTF-8");
        try (PrintWriter out = resp.getWriter()) {
            out.write(WebPage.shell("Register", null, body));
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static String trim(String s) {
        return s == null ? null : s.trim();
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
