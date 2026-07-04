package com.streamhelper.app.web.dto;

import jakarta.validation.constraints.NotBlank;

public record TextInputRequest(@NotBlank String text) {}
