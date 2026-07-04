package com.streamhelper.app.web.dto;

import jakarta.validation.constraints.NotBlank;

public record GenerationRequest(@NotBlank String brief) {}
