package com.spamfilter.smsclient.servlet;

import com.spamfilter.smsclient.auth.SessionUtil;
import com.spamfilter.smsclient.db.SubscriberRepository;
import com.spamfilter.smsclient.model.Subscriber;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** GET/POST /subscribers - list, add, and remove network subscribers. */
public class SubscribersServlet extends HttpServlet {

    private static final Set<String> VALID_STATUSES = Set.of("ACTIVE", "SUSPENDED", "BLOCKED");
    private final SubscriberRepository subscriberRepository;

    public SubscribersServlet(SubscriberRepository subscriberRepository) {
        this.subscriberRepository = subscriberRepository;
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        if (!WebPage.requireSubscriberManagementAccess(req, resp)) {
            return;
        }
        render(req, resp, req.getParameter("error"));
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        if (!WebPage.requireSubscriberManagementAccess(req, resp)) {
            return;
        }

        try {
            if ("remove".equals(req.getParameter("action"))) {
                subscriberRepository.remove(req.getParameter("id"));
            } else {
                String msisdn = trim(req.getParameter("msisdn"));
                String imsi = trim(req.getParameter("imsi"));
                String displayName = trim(req.getParameter("displayName"));
                String status = trim(req.getParameter("status"));
                if (isBlank(msisdn)) {
                    render(req, resp, "Enter a phone number (MSISDN)");
                    return;
                }
                if (!VALID_STATUSES.contains(status)) {
                    render(req, resp, "Choose a valid subscriber status");
                    return;
                }
                subscriberRepository.add(msisdn, blankToNull(imsi), blankToNull(displayName), status);
            }
            resp.sendRedirect("/subscribers");
        } catch (IllegalArgumentException e) {
            render(req, resp, e.getMessage());
        } catch (IllegalStateException e) {
            render(req, resp, "Unavailable: " + e.getMessage());
        }
    }

    private void render(HttpServletRequest req, HttpServletResponse resp, String error) throws IOException {
        List<Subscriber> subscribers = subscriberRepository.list();
        String rows = subscribers.isEmpty()
                ? "<p class=\"muted\">No subscribers have been added yet.</p>"
                : subscribers.stream().map(s -> """
                        <div class="numbers-row">
                          <span><span class="msisdn">%s</span>%s%s</span>
                          <form method="post" style="margin:0;">
                            <input type="hidden" name="action" value="remove">
                            <input type="hidden" name="id" value="%s">
                            <button type="submit" class="icon-btn" title="Remove subscriber">%s</button>
                          </form>
                        </div>
                        """.formatted(
                        escape(s.getMsisdn()),
                        s.getDisplayName() == null ? "" : " <span class=\"label-tag\">" + escape(s.getDisplayName()) + "</span>",
                        " <span class=\"label-tag\">" + escape(s.getStatus())
                                + (s.getImsi() == null ? "" : " · IMSI " + escape(s.getImsi())) + "</span>",
                        escape(s.getId()), WebPage.ICON_TRASH)).collect(Collectors.joining());

        String body = """
                <div class="dashboard-grid">
                  <div class="card">
                    <h1>Subscribers</h1>
                    <p class="subtitle">Numbers registered on the private mobile network.</p>
                    %s
                    %s
                  </div>
                  <div class="card">
                    <h2>Add subscriber</h2>
                    <form method="post">
                      <input type="hidden" name="action" value="add">
                      <label for="msisdn">Phone number (MSISDN)</label>
                      <input id="msisdn" name="msisdn" required maxlength="20" placeholder="201000000000">

                      <label for="imsi">IMSI (optional)</label>
                      <input id="imsi" name="imsi" maxlength="20" placeholder="602010123456789">

                      <label for="displayName">Display name (optional)</label>
                      <input id="displayName" name="displayName" maxlength="100" placeholder="Test phone">

                      <label for="status">Status</label>
                      <select id="status" name="status">
                        <option value="ACTIVE">Active</option>
                        <option value="SUSPENDED">Suspended</option>
                        <option value="BLOCKED">Blocked</option>
                      </select>

                      <button type="submit">Add subscriber</button>
                    </form>
                  </div>
                </div>
                """.formatted(error == null ? "" : "<div class=\"banner error\">" + WebPage.ICON_WARN + escape(error) + "</div>", rows);

        resp.setContentType("text/html; charset=UTF-8");
        try (PrintWriter out = resp.getWriter()) {
            out.write(WebPage.shell("SpamGuard — Subscribers", SessionUtil.currentUserEmail(req),
                    SessionUtil.currentUserRole(req), body));
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String blankToNull(String value) {
        return isBlank(value) ? null : value;
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }

    private static String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
