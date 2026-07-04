package com.streamhelper.app.transcription;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.stereotype.Service;

@Service
public class TranscriptionProgressService {

    private final ConcurrentMap<String, TranscriptionProgressSnapshot> snapshots = new ConcurrentHashMap<>();

    public void start(String projectId, String source) {
        Instant now = Instant.now();
        snapshots.put(
                projectId,
                new TranscriptionProgressSnapshot(
                        true,
                        false,
                        0,
                        "starting",
                        "Starting transcription for %s.".formatted(source == null || source.isBlank() ? "request" : source),
                        now,
                        now,
                        null));
    }

    public void update(String projectId, int percent, String stage, String message) {
        Instant now = Instant.now();
        snapshots.compute(projectId, (ignored, current) -> {
            Instant startedAt = current == null ? now : current.startedAt();
            int normalized = clampPercent(percent);
            if (current != null && current.active()) {
                normalized = Math.max(normalized, current.percent());
            }
            return new TranscriptionProgressSnapshot(
                    true,
                    false,
                    normalized,
                    normalizeStage(stage),
                    normalizeMessage(message, "Transcription in progress."),
                    startedAt,
                    now,
                    null);
        });
    }

    public void complete(String projectId, String message) {
        Instant now = Instant.now();
        snapshots.compute(projectId, (ignored, current) -> new TranscriptionProgressSnapshot(
                false,
                false,
                100,
                "completed",
                normalizeMessage(message, "Transcription completed."),
                current == null ? now : current.startedAt(),
                now,
                now));
    }

    public void fail(String projectId, String message) {
        Instant now = Instant.now();
        snapshots.compute(projectId, (ignored, current) -> new TranscriptionProgressSnapshot(
                false,
                true,
                current == null ? 0 : current.percent(),
                "failed",
                normalizeMessage(message, "Transcription failed."),
                current == null ? now : current.startedAt(),
                now,
                now));
    }

    public TranscriptionProgressSnapshot get(String projectId) {
        TranscriptionProgressSnapshot snapshot = snapshots.get(projectId);
        if (snapshot != null) {
            return snapshot;
        }
        Instant now = Instant.now();
        return new TranscriptionProgressSnapshot(false, false, 0, "idle", "No active transcription.", now, now, null);
    }

    private static int clampPercent(int percent) {
        return Math.max(0, Math.min(100, percent));
    }

    private static String normalizeStage(String stage) {
        if (stage == null || stage.isBlank()) {
            return "running";
        }
        return stage.trim();
    }

    private static String normalizeMessage(String message, String fallback) {
        if (message == null || message.isBlank()) {
            return fallback;
        }
        return message.trim();
    }
}
