package com.streamhelper.app.transcription;

import java.time.Instant;

public record TranscriptionProgressSnapshot(
        boolean active,
        boolean failed,
        int percent,
        String stage,
        String message,
        Instant startedAt,
        Instant updatedAt,
        Instant completedAt) {}
