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
 * GET/POST /numbers - add or remove the phone numbers a user can send from.
 */
public class NumbersServlet extends HttpServlet {

    private final UserRepository userRepository;

    public NumbersServlet(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String userId = WebPage.requireLogin(req, resp);
        if (userId == null) {
            return;
        }
        render(req, resp, userId, req.getParameter("error"));
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String userId = WebPage.requireLogin(req, resp);
        if (userId == null) {
            return;
        }

        String action = req.getParameter("action");
        try {
            if ("remove".equals(action)) {
                userRepository.removeNumber(userId, req.getParameter("id"));
            } else {
                String msisdn = trim(req.getParameter("msisdn"));
                String label = trim(req.getParameter("label"));
                if (isBlank(msisdn)) {
                    render(req, resp, userId, "Enter a phone number");
                    return;
                }
                userRepository.addNumber(userId, msisdn, isBlank(label) ? null : label);
            }
            resp.sendRedirect("/numbers");
        } catch (IllegalArgumentException e) {
            render(req, resp, userId, e.getMessage());
        } catch (IllegalStateException e) {
            render(req, resp, userId, "Unavailable: " + e.getMessage());
        }
    }

    private void render(HttpServletRequest req, HttpServletResponse resp, String userId, String error) throws IOException {
        List<PhoneNumber> numbers = userRepository.listNumbers(userId);

        String rows = numbers.isEmpty()
                ? "<p class=\"muted\">You haven't added any numbers yet — add one below.</p>"
                : numbers.stream().map(n -> """
                        <div class="numbers-row">
                          <span><span class="msisdn">%s</span>%s</span>
                          <form method="post" style="margin:0;">
                            <input type="hidden" name="action" value="remove">
                            <input type="hidden" name="id" value="%s">
                            <button type="submit" class="icon-btn" title="Remove">%s</button>
                          </form>
                        </div>
                        """.formatted(escape(n.getMsisdn()),
                                n.getLabel() != null ? " <span class=\"label-tag\">" + escape(n.getLabel()) + "</span>" : "",
                                n.getId(), WebPage.ICON_TRASH))
                        .collect(Collectors.joining());

        String body = """
                <div class="dashboard-grid">
                  <div class="card">
                    <h1>My Numbers</h1>
                    <p class="subtitle">Numbers you can send SMS from.</p>
                    %s
                    %s
                  </div>
                  <div class="card">
                    <h2>Add a number</h2>
                    <form method="post">
                      <input type="hidden" name="action" value="add">
                      <label for="msisdn">Phone number</label>
                      <input id="msisdn" name="msisdn" required placeholder="1000">

                      <label for="label">Label (optional)</label>
                      <input id="label" name="label" placeholder="Personal">

                      <button type="submit">Add number</button>
                    </form>
                  </div>
                </div>
                """.formatted(error != null ? "<div class=\"banner error\">" + WebPage.ICON_WARN + escape(error) + "</div>" : "", rows);

        resp.setContentType("text/html; charset=UTF-8");
        try (PrintWriter out = resp.getWriter()) {
            out.write(WebPage.shell("SpamGuard — My Numbers", SessionUtil.currentUserEmail(req),
                    SessionUtil.currentUserRole(req), body));
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
