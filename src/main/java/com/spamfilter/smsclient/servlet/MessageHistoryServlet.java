package com.spamfilter.smsclient.servlet;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spamfilter.smsclient.auth.SessionUtil;
import com.spamfilter.smsclient.db.UserRepository;
import com.spamfilter.smsclient.model.MessageRecord;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;
import java.util.Map;

/**
 * GET /api/sms/history?limit=50
 * Read-only view of the caller's own messages - any message whose source or
 * destination matches one of the logged-in user's phone numbers - from the
 * messages table the SMPP server writes to in Neon after classifying each
 * submission. This app never writes to that table.
 */
public class MessageHistoryServlet extends HttpServlet {

    private static final Logger log = LoggerFactory.getLogger(MessageHistoryServlet.class);

    private final UserRepository userRepository;
    private final ObjectMapper mapper = new ObjectMapper();

    public MessageHistoryServlet(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String userId = SessionUtil.currentUserId(req);
        if (userId == null) {
            writeJson(resp, HttpServletResponse.SC_UNAUTHORIZED, Map.of("error", "Not logged in"));
            return;
        }

        int limit = parseLimit(req.getParameter("limit"));

        List<MessageRecord> records;
        try {
            records = userRepository.historyForUser(userId, limit);
        } catch (IllegalStateException e) {
            log.warn("Failed to query message history: {}", e.getMessage());
            writeJson(resp, HttpServletResponse.SC_SERVICE_UNAVAILABLE, Map.of("error", "Could not reach the database"));
            return;
        }

        writeJson(resp, HttpServletResponse.SC_OK, records);
    }

    /**
     * Serializes to a String first, then writes it in one shot - a failure
     * mid-serialization (or a dropped connection to Neon) must never leave a
     * half-written JSON body on the wire, which the client can't parse.
     */
    private void writeJson(HttpServletResponse resp, int status, Object payload) throws IOException {
        String json;
        try {
            json = mapper.writeValueAsString(payload);
        } catch (Exception e) {
            log.warn("Failed to serialize response: {}", e.getMessage());
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.setContentType("application/json");
            try (PrintWriter out = resp.getWriter()) {
                out.write("{\"error\":\"Internal error building response\"}");
            }
            return;
        }
        resp.setStatus(status);
        resp.setContentType("application/json");
        try (PrintWriter out = resp.getWriter()) {
            out.write(json);
        }
    }

    private static int parseLimit(String value) {
        if (value == null || value.isBlank()) {
            return 50;
        }
        try {
            return Math.max(1, Integer.parseInt(value));
        } catch (NumberFormatException e) {
            return 50;
        }
    }
}
