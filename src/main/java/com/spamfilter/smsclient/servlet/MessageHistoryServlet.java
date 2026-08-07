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
        resp.setContentType("application/json");

        String userId = SessionUtil.currentUserId(req);
        if (userId == null) {
            resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            mapper.writeValue(resp.getOutputStream(), Map.of("error", "Not logged in"));
            return;
        }

        int limit = parseLimit(req.getParameter("limit"));

        try {
            List<MessageRecord> records = userRepository.historyForUser(userId, limit);
            mapper.writeValue(resp.getOutputStream(), records);
        } catch (IllegalStateException e) {
            log.warn("Failed to query message history: {}", e.getMessage());
            resp.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            mapper.writeValue(resp.getOutputStream(), Map.of("error", "Could not reach the database"));
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
