package com.spamfilter.smsclient.servlet;

import com.spamfilter.smsclient.auth.SessionUtil;
import com.spamfilter.smsclient.db.UserRepository;
import com.spamfilter.smsclient.model.PhoneNumber;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;
import java.util.stream.Collectors;

/**
 * GET / - requires login. Lets the user compose and send an SMS from one of
 * their own numbers (server-rendered dropdown, not free text), and browse
 * their own message history read from Neon via GET /api/sms/history.
 */
public class IndexServlet extends HttpServlet {

    private final UserRepository userRepository;

    public IndexServlet(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String userId = WebPage.requireLogin(req, resp);
        if (userId == null) {
            return;
        }

        List<PhoneNumber> numbers = userRepository.listNumbers(userId);

        String sourceField = numbers.isEmpty()
                ? "<p class=\"muted\">You have no numbers yet. <a class=\"link\" href=\"/numbers\">Add one</a> before sending.</p>"
                : """
                  <label for="source">Source</label>
                  <select id="source" name="source" required>
                    %s
                  </select>
                  """.formatted(numbers.stream()
                        .map(n -> "<option value=\"%s\">%s%s</option>".formatted(
                                escape(n.getMsisdn()), escape(n.getMsisdn()),
                                n.getLabel() != null ? " (" + escape(n.getLabel()) + ")" : ""))
                        .collect(Collectors.joining()));

        String body = """
                <div class="dashboard-grid">
                  <div class="card">
                    <h1>Send an SMS</h1>
                    <p class="subtitle">Every message is screened by the AI classifier before delivery.</p>
                    <form id="sendForm">
                      %s
                      <label for="destination">Destination</label>
                      <input id="destination" name="destination" required placeholder="2000">

                      <label for="body">Message</label>
                      <textarea id="body" name="body" rows="4" required placeholder="Type your message..."></textarea>

                      <button type="submit">Send message</button>
                    </form>
                    <div id="result"></div>
                  </div>

                  <div class="card">
                    <h2>Recent messages</h2>
                    <p id="historyStatus" class="muted">Loading...</p>
                    <div id="historyList" style="display: none;"></div>
                  </div>
                </div>

                <script>
                  const form = document.getElementById('sendForm');
                  const result = document.getElementById('result');

                  if (form) {
                    form.addEventListener('submit', async (e) => {
                      e.preventDefault();
                      result.className = '';
                      result.style.display = 'flex';
                      result.innerHTML = 'Sending...';

                      const payload = {
                        source: document.getElementById('source').value,
                        destination: document.getElementById('destination').value,
                        body: document.getElementById('body').value
                      };

                      try {
                        const res = await fetch('/api/sms/send', {
                          method: 'POST',
                          headers: { 'Content-Type': 'application/json' },
                          body: JSON.stringify(payload)
                        });
                        const data = await res.json();

                        if (res.ok) {
                          result.className = 'banner ok';
                          result.innerHTML = ICON_CHECK + `Message sent from <strong>&nbsp;${escapeHtml(data.source)}&nbsp;</strong> to <strong>&nbsp;${escapeHtml(data.destination)}</strong>.`;
                          document.getElementById('body').value = '';
                        } else {
                          result.className = 'banner error';
                          result.innerHTML = ICON_WARN + `Could not send message: ${escapeHtml(data.error || 'unknown error')}`;
                        }
                        loadHistory();
                      } catch (err) {
                        result.className = 'banner error';
                        result.innerHTML = ICON_WARN + 'Request failed: ' + escapeHtml(String(err));
                      }
                    });
                  }

                  const ICON_CHECK = '%s';
                  const ICON_WARN = '%s';

                  function escapeHtml(s) {
                    return String(s).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
                  }

                  async function loadHistory() {
                    const status = document.getElementById('historyStatus');
                    const list = document.getElementById('historyList');

                    try {
                      const res = await fetch('/api/sms/history?limit=5');
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

                      list.innerHTML = data.map(m => {
                        const badgeClass = m.classificationLabel === 'spam' ? 'spam' : 'ham';
                        return `
                          <div class="msg-row">
                            <div class="msg-route">${escapeHtml(m.source)} <span class="arrow">&#8594;</span> ${escapeHtml(m.destination)}</div>
                            <div class="msg-meta">
                              <span class="badge ${badgeClass}">${escapeHtml(m.classificationLabel)} &middot; ${m.classificationScore.toFixed(2)}</span>
                              <div class="msg-time">${escapeHtml(m.status)} &middot; ${new Date(m.receivedAt).toLocaleString()}</div>
                            </div>
                          </div>
                        `;
                      }).join('');

                      status.style.display = 'none';
                      list.style.display = 'block';
                    } catch (err) {
                      status.textContent = 'Could not load history: ' + err;
                      status.style.display = 'block';
                      list.style.display = 'none';
                    }
                  }

                  loadHistory();
                </script>
                """.formatted(sourceField, oneLine(WebPage.ICON_CHECK), oneLine(WebPage.ICON_WARN));

        resp.setContentType("text/html; charset=UTF-8");
        try (PrintWriter out = resp.getWriter()) {
            out.write(WebPage.shell("SpamGuard — Send", SessionUtil.currentUserEmail(req),
                    SessionUtil.currentUserRole(req), body));
        }
    }

    private static String oneLine(String svg) {
        return svg.replace("\n", "").replace("'", "\\'");
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
