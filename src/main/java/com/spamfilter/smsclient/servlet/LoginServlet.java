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
 * GET/POST /login
 */
public class LoginServlet extends HttpServlet {

    private final UserRepository userRepository;

    public LoginServlet(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        render(resp, req.getParameter("error"));
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String email = req.getParameter("email");
        String password = req.getParameter("password");

        try {
            User user = userRepository.authenticate(email == null ? "" : email.trim(), password == null ? "" : password);
            if (user == null) {
                render(resp, "Invalid email or password");
                return;
            }
            SessionUtil.login(req, user.getId(), user.getEmail());
            resp.sendRedirect("/");
        } catch (IllegalStateException e) {
            render(resp, "Login unavailable: " + e.getMessage());
        }
    }

    private void render(HttpServletResponse resp, String error) throws IOException {
        String body = """
                <div class="card">
                  <h1>Log in</h1>
                  %s
                  <form method="post">
                    <label for="email">Email</label>
                    <input id="email" name="email" type="email" required>

                    <label for="password">Password</label>
                    <input id="password" name="password" type="password" required>

                    <button type="submit">Log in</button>
                  </form>
                  <p class="muted">No account yet? <a class="link" href="/register">Register</a></p>
                </div>
                """.formatted(error != null ? "<div class=\"banner error\">" + escape(error) + "</div>" : "");

        resp.setContentType("text/html; charset=UTF-8");
        try (PrintWriter out = resp.getWriter()) {
            out.write(WebPage.shell("Log in", null, body));
        }
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
