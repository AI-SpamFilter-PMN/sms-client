package com.spamfilter.smsclient.servlet;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spamfilter.smsclient.model.Direction;
import com.spamfilter.smsclient.model.SmsMessage;
import com.spamfilter.smsclient.store.MessageStore;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

/**
 * GET /api/sms/messages?direction=SENT|RECEIVED&limit=100
 */
public class MessagesServlet extends HttpServlet {

    private final MessageStore store;
    private final ObjectMapper mapper = new ObjectMapper();

    public MessagesServlet(MessageStore store) {
        this.store = store;
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        Direction direction = parseDirection(req.getParameter("direction"));
        int limit = parseLimit(req.getParameter("limit"));

        List<SmsMessage> messages = store.list(direction, limit);
        List<Object> json = messages.stream().map(SendSmsServlet::toJson).collect(Collectors.toList());

        resp.setContentType("application/json");
        mapper.writeValue(resp.getOutputStream(), json);
    }

    private static Direction parseDirection(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return Direction.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static int parseLimit(String value) {
        if (value == null || value.isBlank()) return 100;
        try {
            return Math.max(1, Integer.parseInt(value));
        } catch (NumberFormatException e) {
            return 100;
        }
    }
}
