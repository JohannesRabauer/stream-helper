package com.streamhelper.app.web.dto;

import jakarta.validation.constraints.NotBlank;

public record YouTubeTranscriptionRequest(@NotBlank String youtubeUrl, String language, boolean diarize) {}
