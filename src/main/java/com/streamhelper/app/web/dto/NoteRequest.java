package com.streamhelper.app.web.dto;

import jakarta.validation.constraints.NotBlank;

public record NoteRequest(String noteId, @NotBlank String markdown) {}
