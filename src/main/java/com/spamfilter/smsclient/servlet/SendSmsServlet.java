package com.spamfilter.smsclient.servlet;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.spamfilter.smsclient.auth.SessionUtil;
import com.spamfilter.smsclient.db.UserRepository;
import com.spamfilter.smsclient.model.SmsMessage;
import com.spamfilter.smsclient.smpp.SmppService;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * POST /api/sms/send  { "source": "...", "destination": "...", "body": "..." }
 *
 * Requires a logged-in session. Submits the message via SMPP to the
 * teammate's SMPP server, which does the spam classification, forwards to
 * Osmocom or blocks it, and records the result in Neon. This servlet just
 * validates input, checks the source number actually belongs to the caller,
 * and reports the outcome of the SMPP handoff itself.
 */
public class SendSmsServlet extends HttpServlet {

    private final SmppService smppService;
    private final UserRepository userRepository;
    private final ObjectMapper mapper = new ObjectMapper();

    public SendSmsServlet(SmppService smppService, UserRepository userRepository) {
        this.smppService = smppService;
        this.userRepository = userRepository;
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String userId = SessionUtil.currentUserId(req);
        if (userId == null) {
            writeError(resp, HttpServletResponse.SC_UNAUTHORIZED, "Not logged in");
            return;
        }

        JsonNode json;
        try {
            json = mapper.readTree(req.getInputStream());
        } catch (Exception e) {
            writeError(resp, HttpServletResponse.SC_BAD_REQUEST, "Malformed request body");
            return;
        }
        if (json == null) {
            writeError(resp, HttpServletResponse.SC_BAD_REQUEST, "Malformed request body");
            return;
        }

        String source = json.path("source").asText(null);
        String destination = json.path("destination").asText(null);
        String body = json.path("body").asText(null);

        if (isBlank(source) || isBlank(destination) || isBlank(body)) {
            writeError(resp, HttpServletResponse.SC_BAD_REQUEST, "source, destination and body are required");
            return;
        }

        if (!userRepository.userOwnsNumber(userId, source)) {
            writeError(resp, HttpServletResponse.SC_FORBIDDEN, "That source number is not one of your numbers");
            return;
        }

        SmsMessage message = new SmsMessage(source, destination, body);

        try {
            String smppMessageId = smppService.submit(source, destination, body);
            message.setSmppMessageId(smppMessageId);
            message.setStatus("SUBMITTED");
        } catch (RuntimeException e) {
            message.setStatus("FAILED");
            writeError(resp, HttpServletResponse.SC_SERVICE_UNAVAILABLE, e.getMessage());
            return;
        }

        writeJson(resp, HttpServletResponse.SC_OK, toJson(message));
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private void writeError(HttpServletResponse resp, int status, String message) throws IOException {
        writeJson(resp, status, Map.of("error", message));
    }

    /**
     * Serializes to a String first, then writes it in one shot - a failure
     * mid-serialization must never leave a half-written JSON body on the
     * wire, which the client can't parse.
     */
    private void writeJson(HttpServletResponse resp, int status, Object payload) throws IOException {
        String json;
        try {
            json = mapper.writeValueAsString(payload);
        } catch (Exception e) {
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

    static Map<String, Object> toJson(SmsMessage m) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", m.getId());
        map.put("source", m.getSource());
        map.put("destination", m.getDestination());
        map.put("body", m.getBody());
        map.put("status", m.getStatus());
        map.put("smppMessageId", m.getSmppMessageId());
        map.put("timestamp", m.getTimestamp().toString());
        return map;
    }
}
