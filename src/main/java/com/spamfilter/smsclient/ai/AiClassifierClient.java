package com.spamfilter.smsclient.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.spamfilter.smsclient.config.AppConfig;
import com.spamfilter.smsclient.model.Classification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/**
 * Talks to the AI team's spam classification service over plain REST/JSON.
 * If the service is unreachable (e.g. not started yet during dev), calls
 * degrade to Classification.UNKNOWN rather than failing the SMS pipeline.
 */
public class AiClassifierClient {

    private static final Logger log = LoggerFactory.getLogger(AiClassifierClient.class);

    private final AppConfig config;
    private final HttpClient httpClient;
    private final ObjectMapper mapper = new ObjectMapper();

    public AiClassifierClient(AppConfig config) {
        this.config = config;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(config.aiConnectTimeoutMs()))
                .build();
    }

    public Classification classify(String text) {
        try {
            String requestBody = mapper.writeValueAsString(Map.of("channel", "sms", "text", text));
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(config.aiClassifyUrl()))
                    .timeout(Duration.ofMillis(config.aiReadTimeoutMs()))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                log.warn("AI classifier returned HTTP {}: {}", response.statusCode(), response.body());
                return Classification.UNKNOWN;
            }

            JsonNode json = mapper.readTree(response.body());
            String label = json.path("label").asText("unknown");
            double score = json.path("score").asDouble(0.0);
            return new Classification(label, score);
        } catch (Exception e) {
            log.warn("Failed to reach AI classifier at {}: {}", config.aiClassifyUrl(), e.toString());
            return Classification.UNKNOWN;
        }
    }
}
