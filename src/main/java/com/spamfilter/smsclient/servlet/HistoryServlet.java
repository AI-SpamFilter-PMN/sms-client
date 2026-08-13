package com.spamfilter.smsclient.servlet;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spamfilter.smsclient.auth.SessionUtil;
import com.spamfilter.smsclient.db.UserRepository;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.PrintWriter;

/**
 * GET /history
 * Server-rendered page that shows the full message history for the logged-in
 * user. The page fetches the JSON API and renders a detailed table.
 */
public class HistoryServlet extends HttpServlet {

    private final UserRepository userRepository;
    private final ObjectMapper mapper = new ObjectMapper();

    public HistoryServlet(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String userId = WebPage.requireLogin(req, resp);
        if (userId == null) {
            return;
        }

        String body = """
                <div class="card">
                  <h1>Message history</h1>
                  <p class="subtitle">All classified messages (detailed view).</p>
                  <div id="historyStatus" class="muted">Loading...</div>
                  <div id="historyList" style="display: none; margin-top: 1rem;"></div>
                </div>

                <script>
                  async function loadAllHistory() {
                    const status = document.getElementById('historyStatus');
                    const list = document.getElementById('historyList');
                    try {
                      const res = await fetch('/api/sms/history?limit=1000');
                      const data = await res.json();
                      if (!res.ok) {
                        status.textContent = data.error || 'Could not load history';
                        status.style.display = 'block';
                        list.style.display = 'none';
                        return;
                      }
                      if (data.length === 0) {
                        status.textContent = 'No classified messages yet.';
                        status.style.display = 'block';
                        list.style.display = 'none';
                        return;
                      }

                      const rows = data.map(m => `
                        <div class="card" style="margin-top:0.8rem;">
                          <div style="display:flex; justify-content:space-between; gap:1rem;">
                            <div>
                              <div class="msg-route">${escapeHtml(m.source)} <span class="arrow">→</span> ${escapeHtml(m.destination)}</div>
                              <div style="margin-top:0.5rem; color:var(--text-faint);">${escapeHtml(m.body || '')}</div>
                            </div>
                            <div style="text-align:right; min-width:220px;">
                              <div class="badge ${m.classificationLabel === 'spam' ? 'spam' : 'ham'}">${escapeHtml(m.classificationLabel)} · ${Number(m.classificationScore).toFixed(2)}</div>
                              <div class="msg-time">${escapeHtml(m.status)} · ${new Date(m.receivedAt).toLocaleString()}</div>
                              <div style="margin-top:0.5rem; color:var(--text-faint); font-size:0.82rem; word-break:break-all;">SMSServer ID: ${escapeHtml(m.smppMessageId || '')}</div>
                            </div>
                          </div>
                        </div>
                      `).join('');

                      list.innerHTML = rows;
                      status.style.display = 'none';
                      list.style.display = 'block';
                    } catch (err) {
                      status.textContent = 'Could not load history: ' + err;
                      status.style.display = 'block';
                      list.style.display = 'none';
                    }
                  }

                  function escapeHtml(s) {
                    return String(s).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
                  }

                  loadAllHistory();
                </script>
                """;

        resp.setContentType("text/html; charset=UTF-8");
        try (PrintWriter out = resp.getWriter()) {
            out.write(WebPage.shell("SpamGuard — History", SessionUtil.currentUserEmail(req),
                    SessionUtil.currentUserRole(req), body));
        }
    }
}
