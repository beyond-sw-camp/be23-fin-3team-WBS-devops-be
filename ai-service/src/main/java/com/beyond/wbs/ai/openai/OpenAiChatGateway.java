package com.beyond.wbs.ai.openai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class OpenAiChatGateway {

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String model;
    private final double temperature;
    private final int maxTokens;

    public OpenAiChatGateway(
            ObjectMapper objectMapper,
            @Value("${spring.ai.openai.api-key}") String apiKey,
            @Value("${spring.ai.openai.chat.options.model:gpt-4o-mini}") String model,
            @Value("${spring.ai.openai.chat.options.temperature:0.1}") double temperature,
            @Value("${spring.ai.openai.chat.options.max-tokens:220}") int maxTokens) {
        this.objectMapper = objectMapper;
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.model = model;
        this.temperature = temperature;
        this.maxTokens = maxTokens;
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public String complete(String userPrompt) {
        return complete(null, userPrompt);
    }

    public String complete(String systemPrompt, String userPrompt) {
        return complete(systemPrompt, userPrompt, null);
    }

    public String completeJson(String systemPrompt, String userPrompt) {
        return complete(systemPrompt, userPrompt, Map.of("type", "json_object"));
    }

    private String complete(String systemPrompt, String userPrompt, Map<String, Object> responseFormat) {
        try {
            List<Map<String, String>> messages = systemPrompt == null || systemPrompt.isBlank()
                    ? List.of(Map.of("role", "user", "content", userPrompt))
                    : List.of(
                    Map.of("role", "system", "content", systemPrompt),
                    Map.of("role", "user", "content", userPrompt)
            );

            Map<String, Object> request = new java.util.LinkedHashMap<>();
            request.put("model", model);
            request.put("temperature", temperature);
            request.put("max_tokens", maxTokens);
            request.put("messages", messages);
            if (responseFormat != null && !responseFormat.isEmpty()) {
                request.put("response_format", responseFormat);
            }

            String requestBody = objectMapper.writeValueAsString(request);
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.openai.com/v1/chat/completions"))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .header("Accept-Encoding", "identity")
                    .timeout(Duration.ofSeconds(30))
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            String raw = response.body();
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("OpenAI HTTP " + response.statusCode() + " - " + preview(raw));
            }

            JsonNode root;
            try {
                root = objectMapper.readTree(raw);
            } catch (Exception e) {
                throw new IllegalStateException("OpenAI JSON parse failed. status="
                        + response.statusCode() + ", bodyLength=" + (raw == null ? 0 : raw.length())
                        + ", bodyPreview=" + preview(raw), e);
            }
            JsonNode content = root.path("choices").path(0).path("message").path("content");
            if (content.isMissingNode() || content.isNull()) {
                throw new IllegalStateException("OpenAI 응답에 message.content가 없습니다.");
            }
            return content.asText();
        } catch (Exception e) {
            log.warn("[OPENAI_CHAT_FAILED] model={}, reason={}", model, e.getMessage());
            throw new IllegalStateException("OpenAI chat completion failed: " + e.getMessage(), e);
        }
    }

    private String preview(String raw) {
        if (raw == null) {
            return "";
        }
        String normalized = raw.replaceAll("\\s+", " ").trim();
        return normalized.length() <= 300 ? normalized : normalized.substring(0, 300) + "...";
    }
}
