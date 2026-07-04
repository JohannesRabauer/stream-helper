package com.streamhelper.app.web.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateProjectRequest(@NotBlank String name) {}
