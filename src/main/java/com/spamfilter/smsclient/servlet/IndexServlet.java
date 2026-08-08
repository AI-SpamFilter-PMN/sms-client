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
                <div class="card">
                  <h1>Send an SMS</h1>
                  <form id="sendForm">
                    %s
                    <label for="destination">Destination</label>
                    <input id="destination" name="destination" required placeholder="2000">

                    <label for="body">Message</label>
                    <textarea id="body" name="body" rows="4" required></textarea>

                    <button type="submit">Send</button>
                  </form>
                  <div id="result"></div>
                </div>

                <div class="card">
                  <h2>My recent messages</h2>
                  <p id="historyStatus" class="muted">Loading...</p>
                  <table id="historyTable" style="display: none;">
                    <thead>
                      <tr><th>Source</th><th>Destination</th><th>Verdict</th><th>Status</th><th>Received</th></tr>
                    </thead>
                    <tbody id="historyBody"></tbody>
                  </table>
                </div>

                <script>
                  const form = document.getElementById('sendForm');
                  const result = document.getElementById('result');

                  if (form) {
                    form.addEventListener('submit', async (e) => {
                      e.preventDefault();
                      result.className = '';
                      result.textContent = 'Sending...';
                      result.style.display = 'block';

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
                          result.className = 'ok';
                          result.innerHTML = `Message sent from <strong>${escapeHtml(data.source)}</strong> to <strong>${escapeHtml(data.destination)}</strong>.`;
                          document.getElementById('body').value = '';
                        } else {
                          result.className = 'error';
                          result.innerHTML = `Could not send message: ${escapeHtml(data.error || 'unknown error')}`;
                        }
                        loadHistory();
                      } catch (err) {
                        result.className = 'error';
                        result.textContent = 'Request failed: ' + err;
                      }
                    });
                  }

                  function escapeHtml(s) {
                    return String(s).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
                  }

                  async function loadHistory() {
                    const status = document.getElementById('historyStatus');
                    const table = document.getElementById('historyTable');
                    const body = document.getElementById('historyBody');

                    try {
                      const res = await fetch('/api/sms/history?limit=25');
                      const data = await res.json();

                      if (!res.ok) {
                        status.textContent = data.error || 'Could not load history';
                        table.style.display = 'none';
                        return;
                      }

                      if (data.length === 0) {
                        status.textContent = 'No classified messages yet.';
                        table.style.display = 'none';
                        return;
                      }

                      body.innerHTML = data.map(m => `
                        <tr>
                          <td>${m.source}</td>
                          <td>${m.destination}</td>
                          <td class="${m.classificationLabel}">${m.classificationLabel} (${m.classificationScore.toFixed(2)})</td>
                          <td>${m.status}</td>
                          <td>${new Date(m.receivedAt).toLocaleString()}</td>
                        </tr>
                      `).join('');

                      status.style.display = 'none';
                      table.style.display = 'table';
                    } catch (err) {
                      status.textContent = 'Could not load history: ' + err;
                      table.style.display = 'none';
                    }
                  }

                  loadHistory();
                </script>
                """.formatted(sourceField);

        resp.setContentType("text/html; charset=UTF-8");
        try (PrintWriter out = resp.getWriter()) {
            out.write(WebPage.shell("SMS Client", SessionUtil.currentUserEmail(req), body));
        }
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
