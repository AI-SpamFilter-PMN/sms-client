package com.spamfilter.smsclient.servlet;

import com.spamfilter.smsclient.auth.SessionUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/**
 * Shared HTML shell (nav bar + styling) so every page looks consistent,
 * plus a login guard reused by every page that requires an account.
 */
final class WebPage {

    private WebPage() {
    }

    static String requireLogin(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String userId = SessionUtil.currentUserId(req);
        if (userId == null) {
            resp.sendRedirect("/login");
            return null;
        }
        return userId;
    }

    static String shell(String title, String activeEmail, String bodyHtml) {
        String nav = activeEmail != null ? navLoggedIn(activeEmail) : navLoggedOut();
        return """
                <!doctype html>
                <html lang="en">
                <head>
                <meta charset="UTF-8">
                <title>%s</title>
                <style>%s</style>
                </head>
                <body>
                %s
                <main class="container">
                %s
                </main>
                </body>
                </html>
                """.formatted(title, CSS, nav, bodyHtml);
    }

    private static String navLoggedIn(String email) {
        return """
                <nav class="nav">
                  <a class="brand" href="/">SMS Client</a>
                  <div class="nav-links">
                    <a href="/">Send</a>
                    <a href="/numbers">My Numbers</a>
                    <span class="nav-user">%s</span>
                    <a href="/logout">Logout</a>
                  </div>
                </nav>
                """.formatted(email);
    }

    private static String navLoggedOut() {
        return """
                <nav class="nav">
                  <span class="brand">SMS Client</span>
                  <div class="nav-links">
                    <a href="/login">Login</a>
                    <a href="/register">Register</a>
                  </div>
                </nav>
                """;
    }

    private static final String CSS = """
            :root {
              --accent: #4f46e5;
              --accent-dark: #4338ca;
              --bg: #f4f5f9;
              --card: #ffffff;
              --border: #e2e4ea;
              --text: #1f2430;
              --muted: #6b7280;
              --spam: #b3261e;
              --ham: #1e7a34;
            }
            * { box-sizing: border-box; }
            body {
              font-family: -apple-system, "Segoe UI", Roboto, sans-serif;
              margin: 0; background: var(--bg); color: var(--text);
            }
            .nav {
              display: flex; align-items: center; justify-content: space-between;
              padding: 0.9rem 1.5rem; background: var(--card); border-bottom: 1px solid var(--border);
            }
            .brand { font-weight: 700; color: var(--accent); text-decoration: none; font-size: 1.1rem; }
            .nav-links { display: flex; align-items: center; gap: 1.25rem; }
            .nav-links a { color: var(--text); text-decoration: none; font-size: 0.95rem; }
            .nav-links a:hover { color: var(--accent); }
            .nav-user { color: var(--muted); font-size: 0.9rem; }
            .container { max-width: 640px; margin: 2.5rem auto; padding: 0 1rem; }
            .card {
              background: var(--card); border: 1px solid var(--border); border-radius: 12px;
              padding: 1.75rem; box-shadow: 0 1px 3px rgba(0,0,0,0.04);
            }
            .card + .card { margin-top: 1.75rem; }
            h1 { font-size: 1.3rem; margin: 0 0 1.25rem; }
            h2 { font-size: 1.05rem; margin: 0 0 1rem; }
            label { display: block; margin-top: 1rem; font-weight: 600; font-size: 0.9rem; }
            input, textarea, select {
              width: 100%; padding: 0.6rem 0.7rem; margin-top: 0.3rem; box-sizing: border-box;
              font: inherit; border: 1px solid var(--border); border-radius: 8px; background: #fff;
            }
            input:focus, textarea:focus, select:focus { outline: 2px solid var(--accent); border-color: var(--accent); }
            button, .btn {
              margin-top: 1.5rem; padding: 0.65rem 1.3rem; font: inherit; font-weight: 600;
              cursor: pointer; background: var(--accent); color: #fff; border: none; border-radius: 8px;
            }
            button:hover, .btn:hover { background: var(--accent-dark); }
            button.secondary, .btn.secondary { background: transparent; color: var(--spam); border: 1px solid var(--border); }
            button.secondary:hover { background: #fef2f2; }
            .banner { margin-top: 1rem; padding: 0.75rem 1rem; border-radius: 8px; font-size: 0.9rem; }
            .banner.error { background: #fce8e6; color: var(--spam); }
            .banner.ok { background: #e6f4ea; color: var(--ham); }
            .muted { color: var(--muted); font-size: 0.9rem; }
            .link { color: var(--accent); text-decoration: none; }
            table { width: 100%; border-collapse: collapse; margin-top: 0.5rem; font-size: 0.88rem; }
            th, td { text-align: left; padding: 0.5rem 0.4rem; border-bottom: 1px solid var(--border); }
            .spam { color: var(--spam); font-weight: 600; }
            .ham { color: var(--ham); }
            .row { display: flex; align-items: center; justify-content: space-between; padding: 0.5rem 0; border-bottom: 1px solid var(--border); }
            .row:last-child { border-bottom: none; }
            #result { margin-top: 1.5rem; padding: 0.75rem; border-radius: 8px; white-space: pre-wrap; font-family: monospace; font-size: 0.85rem; display: none; }
            #result.ok { display: block; background: #e6f4ea; color: var(--ham); }
            #result.error { display: block; background: #fce8e6; color: var(--spam); }
            """;
}
