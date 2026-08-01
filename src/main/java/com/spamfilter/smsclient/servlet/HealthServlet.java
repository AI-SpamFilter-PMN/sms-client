package com.spamfilter.smsclient.servlet;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spamfilter.smsclient.smpp.SmppService;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.Map;

/**
 * GET /api/health
 */
public class HealthServlet extends HttpServlet {

    private final SmppService smppService;
    private final ObjectMapper mapper = new ObjectMapper();

    public HealthServlet(SmppService smppService) {
        this.smppService = smppService;
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json");
        mapper.writeValue(resp.getOutputStream(), Map.of(
                "status", "UP",
                "smppBound", smppService.isBound()));
    }
}
