package com.spamfilter.smsclient.servlet;

import com.spamfilter.smsclient.auth.SessionUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/**
 * Shared HTML shell (nav bar + design system) so every page looks like one
 * coherent product, plus a login guard reused by every page that requires
 * an account. Visual language: dark "Signal Guard" theme - glassmorphic
 * cards, indigo/violet gradient accents, glowing spam/ham badges.
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

    

    /** Full page: nav + content, for logged-in app pages. */
    static String shell(String title, String activeEmail, String role, String bodyHtml) {
        return page(title, navLoggedIn(activeEmail, role) + "<main class=\"container wide\">" + bodyHtml + "</main>");
    }

    /** Centered auth page (login/register): no nav links, just the brand + a hero card. */
    static String authShell(String title, String heroTitle, String heroSubtitle, String cardHtml) {
        String hero = """
                <div class="hero">
                  <div class="logo-badge">%s</div>
                  <h1>%s</h1>
                  <p>%s</p>
                </div>
                """.formatted(LOGO_SVG, heroTitle, heroSubtitle);
        return page(title, navLoggedOut() + "<main class=\"container\">" + hero + cardHtml + "</main>");
    }

    private static String page(String title, String content) {
        return """
                <!doctype html>
                <html lang="en">
                <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <title>%s</title>
                <style>%s</style>
                </head>
                <body>
                %s
                </body>
                </html>
                """.formatted(title, CSS, content);
    }

    private static String navLoggedIn(String email, String role) {
        return """
                <nav class="nav">
                  <a class="brand" href="/">%s SpamGuard</a>
                  <div class="nav-links">
                    <a href="/">Send</a>
                    <a href="/history">History</a>
                    <a href="/numbers">My Numbers</a>
                    %s
                    <span class="nav-user">%s</span>
                    <a href="/logout">Logout</a>
                  </div>
                </nav>
                """.formatted(LOGO_SVG_SMALL,
                "", email);
    }

    private static String navLoggedOut() {
        return """
                <nav class="nav nav-center">
                  <span class="brand">%s SpamGuard</span>
                </nav>
                """.formatted(LOGO_SVG_SMALL);
    }

    private static final String LOGO_SVG = """
            <svg width="30" height="30" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
              <defs><linearGradient id="lg1" x1="2" y1="2" x2="22" y2="22" gradientUnits="userSpaceOnUse">
                <stop stop-color="#818cf8"/><stop offset="1" stop-color="#c084fc"/>
              </linearGradient></defs>
              <path d="M12 2.5l7 2.6v5.4c0 4.8-3 8.9-7 10.5-4-1.6-7-5.7-7-10.5V5.1l7-2.6z" fill="url(#lg1)" fill-opacity="0.18" stroke="url(#lg1)" stroke-width="1.5" stroke-linejoin="round"/>
              <path d="M8.5 12.2l2.4 2.4 4.6-5.2" stroke="url(#lg1)" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"/>
            </svg>
            """;

    private static final String LOGO_SVG_SMALL = """
            <svg width="22" height="22" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg" style="vertical-align:-5px">
              <defs><linearGradient id="lg2" x1="2" y1="2" x2="22" y2="22" gradientUnits="userSpaceOnUse">
                <stop stop-color="#818cf8"/><stop offset="1" stop-color="#c084fc"/>
              </linearGradient></defs>
              <path d="M12 2.5l7 2.6v5.4c0 4.8-3 8.9-7 10.5-4-1.6-7-5.7-7-10.5V5.1l7-2.6z" fill="url(#lg2)" fill-opacity="0.18" stroke="url(#lg2)" stroke-width="1.5" stroke-linejoin="round"/>
              <path d="M8.5 12.2l2.4 2.4 4.6-5.2" stroke="url(#lg2)" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"/>
            </svg>
            """;

    static final String ICON_CHECK = """
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none"><circle cx="12" cy="12" r="9.25" stroke="currentColor" stroke-width="1.5"/><path d="M8 12.3l2.6 2.6L16.2 9" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round"/></svg>
            """;

    static final String ICON_WARN = """
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none"><circle cx="12" cy="12" r="9.25" stroke="currentColor" stroke-width="1.5"/><path d="M12 7.5v5.5" stroke="currentColor" stroke-width="1.7" stroke-linecap="round"/><circle cx="12" cy="16.3" r="1" fill="currentColor"/></svg>
            """;

    static final String ICON_TRASH = """
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none"><path d="M5 7h14M9.5 7V5.2c0-.66.54-1.2 1.2-1.2h2.6c.66 0 1.2.54 1.2 1.2V7M7 7l.8 12c.05.9.8 1.6 1.7 1.6h5c.9 0 1.65-.7 1.7-1.6L17 7" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"/></svg>
            """;

    private static final String CSS = """
            :root {
              --bg-1: #0b0e17;
              --glass: rgba(255,255,255,0.045);
              --glass-border: rgba(255,255,255,0.09);
              --text: #f1f5f9;
              --text-muted: #8b95ac;
              --text-faint: #5b6479;
              --accent-1: #818cf8;
              --accent-2: #c084fc;
              --accent-grad: linear-gradient(135deg, #6366f1, #a855f7);
              --danger: #f87171;
              --danger-bg: rgba(239,68,68,0.12);
              --danger-border: rgba(248,113,113,0.35);
              --success: #4ade80;
              --success-bg: rgba(34,197,94,0.12);
              --success-border: rgba(74,222,128,0.35);
              --radius: 16px;
              --radius-sm: 10px;
            }
            * { box-sizing: border-box; }
            html { color-scheme: dark; }
            body {
              margin: 0; min-height: 100vh;
              font-family: -apple-system, "Segoe UI", Roboto, sans-serif;
              background: radial-gradient(circle at 20% 0%, #16203a 0%, var(--bg-1) 55%), var(--bg-1);
              color: var(--text); position: relative; overflow-x: hidden;
            }
            body::before, body::after {
              content: ''; position: fixed; border-radius: 50%; filter: blur(110px); z-index: 0; pointer-events: none;
            }
            body::before { width: 480px; height: 480px; background: var(--accent-1); opacity: 0.16; top: -180px; left: -120px; }
            body::after { width: 420px; height: 420px; background: var(--accent-2); opacity: 0.14; bottom: -160px; right: -100px; }
            ::-webkit-scrollbar { width: 10px; }
            ::-webkit-scrollbar-track { background: transparent; }
            ::-webkit-scrollbar-thumb { background: rgba(255,255,255,0.12); border-radius: 8px; }
            ::-webkit-scrollbar-thumb:hover { background: rgba(255,255,255,0.2); }

            .nav {
              position: sticky; top: 0; z-index: 10;
              display: flex; align-items: center; justify-content: space-between;
              padding: 1rem 2rem; background: rgba(11,14,23,0.7);
              backdrop-filter: blur(16px); -webkit-backdrop-filter: blur(16px);
              border-bottom: 1px solid var(--glass-border);
            }
            .nav-center { justify-content: center; }
            .brand {
              display: inline-flex; align-items: center; gap: 0.55rem; font-weight: 700; font-size: 1.1rem;
              background: var(--accent-grad); -webkit-background-clip: text; background-clip: text; color: transparent;
              text-decoration: none; letter-spacing: -0.02em;
            }
            .nav-links { display: flex; align-items: center; gap: 1.5rem; }
            .nav-links a { color: var(--text-muted); text-decoration: none; font-size: 0.92rem; font-weight: 500; transition: color 0.15s; }
            .nav-links a:hover { color: var(--text); }
            .nav-user {
              color: var(--text-faint); font-size: 0.82rem; padding: 0.3rem 0.7rem;
              background: var(--glass); border-radius: 999px; border: 1px solid var(--glass-border);
              font-family: ui-monospace, "SF Mono", Consolas, monospace;
            }

            .container { max-width: 480px; margin: 0 auto; padding: 3rem 1.5rem; position: relative; z-index: 1; }
            .container.wide { max-width: 980px; }

            .hero { text-align: center; margin-bottom: 2rem; }
            .hero .logo-badge {
              width: 60px; height: 60px; border-radius: 18px; background: var(--glass); border: 1px solid var(--glass-border);
              display: flex; align-items: center; justify-content: center; margin: 0 auto 1.25rem;
              box-shadow: 0 0 40px rgba(129,140,248,0.28);
            }
            .hero h1 { font-size: 1.5rem; margin: 0 0 0.4rem; background: var(--accent-grad); -webkit-background-clip: text; background-clip: text; color: transparent; letter-spacing: -0.01em; }
            .hero p { color: var(--text-muted); margin: 0; font-size: 0.92rem; }

            .card {
              background: var(--glass); backdrop-filter: blur(20px); -webkit-backdrop-filter: blur(20px);
              border: 1px solid var(--glass-border); border-radius: var(--radius);
              padding: 1.75rem 2rem; box-shadow: 0 8px 32px rgba(0,0,0,0.25);
            }
            .card + .card { margin-top: 1.5rem; }

            h1 { font-size: 1.2rem; margin: 0 0 0.25rem; letter-spacing: -0.01em; }
            h2 { font-size: 1.02rem; margin: 0 0 1rem; }
            .subtitle { color: var(--text-muted); font-size: 0.87rem; margin: 0 0 1.5rem; }

            label { display: block; margin-top: 1.1rem; font-size: 0.78rem; font-weight: 700; color: var(--text-muted); text-transform: uppercase; letter-spacing: 0.05em; }
            input, textarea, select {
              width: 100%; padding: 0.7rem 0.85rem; margin-top: 0.4rem;
              background: rgba(255,255,255,0.04); border: 1px solid var(--glass-border);
              border-radius: var(--radius-sm); color: var(--text); font: inherit; font-size: 0.95rem;
              transition: border-color 0.15s, box-shadow 0.15s;
            }
            input::placeholder, textarea::placeholder { color: var(--text-faint); }
            input:focus, textarea:focus, select:focus { outline: none; border-color: var(--accent-1); box-shadow: 0 0 0 3px rgba(129,140,248,0.18); }
            select { cursor: pointer; }
            option { background: #131a2b; color: var(--text); }

            button, .btn {
              margin-top: 1.5rem; padding: 0.75rem 1.4rem; border: none; border-radius: var(--radius-sm);
              font: inherit; font-weight: 600; font-size: 0.92rem; cursor: pointer;
              background: var(--accent-grad); color: #fff; box-shadow: 0 4px 20px rgba(129,140,248,0.3);
              transition: transform 0.15s, box-shadow 0.15s; display: inline-flex; align-items: center; gap: 0.4rem;
            }
            button:hover, .btn:hover { transform: translateY(-1px); box-shadow: 0 6px 26px rgba(129,140,248,0.42); }
            button:active, .btn:active { transform: translateY(0); }
            button.secondary, .btn.secondary { background: transparent; color: var(--danger); border: 1px solid var(--danger-border); box-shadow: none; }
            button.secondary:hover { background: var(--danger-bg); box-shadow: none; }
            button.icon-btn { margin-top: 0; padding: 0.55rem; background: var(--glass); border: 1px solid var(--glass-border); color: var(--text-muted); box-shadow: none; }
            button.icon-btn:hover { background: var(--danger-bg); color: var(--danger); border-color: var(--danger-border); }

            .banner { margin-top: 1.1rem; padding: 0.85rem 1rem; border-radius: var(--radius-sm); font-size: 0.88rem; display: flex; align-items: center; gap: 0.6rem; }
            .banner.error { background: var(--danger-bg); color: var(--danger); border: 1px solid var(--danger-border); }
            .banner.ok { background: var(--success-bg); color: var(--success); border: 1px solid var(--success-border); }
            .banner svg { flex-shrink: 0; }

            .muted { color: var(--text-muted); font-size: 0.9rem; }
            .link { color: var(--accent-1); text-decoration: none; font-weight: 600; }
            .link:hover { text-decoration: underline; }

            .badge {
              display: inline-flex; align-items: center; gap: 0.35rem; padding: 0.28rem 0.7rem; border-radius: 999px;
              font-size: 0.76rem; font-weight: 700; text-transform: uppercase; letter-spacing: 0.03em;
            }
            .badge.spam { background: var(--danger-bg); color: var(--danger); border: 1px solid var(--danger-border); box-shadow: 0 0 16px rgba(248,113,113,0.18); }
            .badge.ham { background: var(--success-bg); color: var(--success); border: 1px solid var(--success-border); box-shadow: 0 0 16px rgba(74,222,128,0.18); }
            .badge::before { content: ''; width: 6px; height: 6px; border-radius: 50%; background: currentColor; }

            .dashboard-grid { display: grid; grid-template-columns: 380px 1fr; gap: 1.75rem; align-items: start; }
            @media (max-width: 820px) { .dashboard-grid { grid-template-columns: 1fr; } }

            .msg-row { display: flex; align-items: center; justify-content: space-between; gap: 1rem; padding: 0.95rem 0; border-bottom: 1px solid var(--glass-border); }
            .msg-row:last-child { border-bottom: none; }
            .msg-route { font-family: ui-monospace, "SF Mono", Consolas, monospace; font-size: 0.9rem; color: var(--text); display: flex; align-items: center; gap: 0.5rem; }
            .msg-route .arrow { color: var(--text-faint); }
            .msg-meta { text-align: right; }
            .msg-time { color: var(--text-faint); font-size: 0.76rem; margin-top: 0.35rem; }

            .numbers-row { display: flex; align-items: center; justify-content: space-between; padding: 0.85rem 1.1rem; margin-bottom: 0.6rem; background: rgba(255,255,255,0.03); border: 1px solid var(--glass-border); border-radius: var(--radius-sm); }
            .numbers-row:last-child { margin-bottom: 0; }
            .numbers-row .msisdn { font-family: ui-monospace, "SF Mono", Consolas, monospace; font-weight: 600; }
            .numbers-row .label-tag { color: var(--text-faint); font-size: 0.82rem; margin-left: 0.6rem; }

            #result { display: none; }
            """;
}
