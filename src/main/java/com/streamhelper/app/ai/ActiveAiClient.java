package com.streamhelper.app.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.streamhelper.app.config.Provider;
import com.streamhelper.app.config.StreamHelperProperties;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Service
public class ActiveAiClient implements AiClient {

    private final StreamHelperProperties properties;
    private final RestClient defaultClient;

    public ActiveAiClient(StreamHelperProperties properties) {
        this.properties = properties;
        this.defaultClient = RestClient.builder()
                .requestFactory(new org.springframework.http.client.SimpleClientHttpRequestFactory() {
                    {
                        int timeout = (int) Duration.ofSeconds(properties.getAi().getTimeoutSeconds())
                                .toMillis();
                        setConnectTimeout(timeout);
                        setReadTimeout(timeout);
                    }
                })
                .build();
    }

    @Override
    public String generateText(String systemPrompt, String userPrompt) {
        return switch (properties.getAi().getProvider()) {
            case OLLAMA -> callOllama(systemPrompt, userPrompt);
            case OPENAI -> callOpenAi(systemPrompt, userPrompt);
        };
    }

    @Override
    public Optional<byte[]> generateImagePng(String prompt) {
        if (properties.getAi().getProvider() == Provider.OPENAI) {
            return Optional.of(callOpenAiImage(prompt));
        }
        return Optional.empty();
    }

    private String callOllama(String systemPrompt, String userPrompt) {
        try {
            Map<String, Object> requestBody = Map.of(
                    "model",
                    properties.getAi().getOllama().getModel(),
                    "system",
                    systemPrompt,
                    "prompt",
                    userPrompt,
                    "stream",
                    false);
            JsonNode node = defaultClient.post()
                    .uri(properties.getAi().getOllama().getBaseUrl() + "/api/generate")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(JsonNode.class);
            if (node == null || node.path("response").isMissingNode()) {
                throw new AiClientException("Ollama returned an unexpected response");
            }
            return node.path("response").asText();
        } catch (RestClientException exception) {
            throw new AiClientException("Failed to call Ollama", exception);
        }
    }

    private String callOpenAi(String systemPrompt, String userPrompt) {
        String apiKey = properties.getAi().getOpenai().getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            throw new AiClientException("OPENAI_API_KEY is missing");
        }
        try {
            Map<String, Object> body = Map.of(
                    "model",
                    properties.getAi().getOpenai().getChatModel(),
                    "messages",
                    List.of(
                            Map.of("role", "system", "content", systemPrompt),
                            Map.of("role", "user", "content", userPrompt)));
            JsonNode node = defaultClient.post()
                    .uri(properties.getAi().getOpenai().getBaseUrl() + "/v1/chat/completions")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
            if (node == null) {
                throw new AiClientException("OpenAI returned empty response");
            }
            JsonNode contentNode =
                    node.path("choices").path(0).path("message").path("content");
            if (contentNode.isMissingNode() || contentNode.asText().isBlank()) {
                throw new AiClientException("OpenAI did not return message content");
            }
            return contentNode.asText();
        } catch (RestClientException exception) {
            throw new AiClientException("Failed to call OpenAI", exception);
        }
    }

    private byte[] callOpenAiImage(String prompt) {
        String apiKey = properties.getAi().getOpenai().getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            throw new AiClientException("OPENAI_API_KEY is missing");
        }
        try {
            Map<String, Object> body = Map.of(
                    "model",
                    properties.getAi().getOpenai().getImageModel(),
                    "prompt",
                    prompt,
                    "size",
                    "1536x1024",
                    "response_format",
                    "b64_json");
            JsonNode node = defaultClient.post()
                    .uri(properties.getAi().getOpenai().getBaseUrl() + "/v1/images/generations")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
            if (node == null) {
                throw new AiClientException("OpenAI image response is empty");
            }
            String b64 = node.path("data").path(0).path("b64_json").asText();
            if (b64.isBlank()) {
                throw new AiClientException("OpenAI did not return image data");
            }
            return Base64.getDecoder().decode(b64);
        } catch (RestClientException exception) {
            throw new AiClientException("Failed to generate image with OpenAI", exception);
        }
    }
}
