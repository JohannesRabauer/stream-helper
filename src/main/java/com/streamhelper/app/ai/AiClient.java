package com.streamhelper.app.ai;

import java.util.Optional;

public interface AiClient {
    String generateText(String systemPrompt, String userPrompt);

    Optional<byte[]> generateImagePng(String prompt);
}
