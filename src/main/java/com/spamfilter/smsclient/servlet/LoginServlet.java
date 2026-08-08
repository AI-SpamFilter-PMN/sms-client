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
        String card = """
                <div class="card">
                  %s
                  <form method="post">
                    <label for="email">Email</label>
                    <input id="email" name="email" type="email" required placeholder="you@example.com" autofocus>

                    <label for="password">Password</label>
                    <input id="password" name="password" type="password" required placeholder="&#8226;&#8226;&#8226;&#8226;&#8226;&#8226;&#8226;&#8226;">

                    <button type="submit">Log in</button>
                  </form>
                  <p class="muted" style="margin-top: 1.25rem;">No account yet? <a class="link" href="/register">Create one</a></p>
                </div>
                """.formatted(error != null ? "<div class=\"banner error\">" + WebPage.ICON_WARN + escape(error) + "</div>" : "");

        resp.setContentType("text/html; charset=UTF-8");
        try (PrintWriter out = resp.getWriter()) {
            out.write(WebPage.authShell("SpamGuard — Log in", "Welcome back",
                    "Log in to send and track your protected messages.", card));
        }
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
