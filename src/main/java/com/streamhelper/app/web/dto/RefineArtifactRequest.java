package com.streamhelper.app.web.dto;

import jakarta.validation.constraints.NotBlank;

public record RefineArtifactRequest(@NotBlank String prompt) {}
