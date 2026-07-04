package com.streamhelper.app.transcription;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TranscriptionProgressServiceTest {

    @Test
    void returnsIdleSnapshotWhenNoProgressExists() {
        TranscriptionProgressService service = new TranscriptionProgressService();

        TranscriptionProgressSnapshot snapshot = service.get("missing-project");

        assertThat(snapshot.active()).isFalse();
        assertThat(snapshot.failed()).isFalse();
        assertThat(snapshot.percent()).isEqualTo(0);
        assertThat(snapshot.stage()).isEqualTo("idle");
    }

    @Test
    void tracksRunningAndCompletedProgress() {
        TranscriptionProgressService service = new TranscriptionProgressService();

        service.start("project-a", "local file");
        service.update("project-a", 45, "transcribe", "Chunk 1 done");
        service.complete("project-a", "Done");

        TranscriptionProgressSnapshot snapshot = service.get("project-a");
        assertThat(snapshot.active()).isFalse();
        assertThat(snapshot.failed()).isFalse();
        assertThat(snapshot.percent()).isEqualTo(100);
        assertThat(snapshot.stage()).isEqualTo("completed");
    }
}
