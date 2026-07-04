package com.streamhelper.app.web.dto;

import jakarta.validation.constraints.NotBlank;

public record ThumbnailCreateRequest(@NotBlank String prompt, boolean builtIn) {}
