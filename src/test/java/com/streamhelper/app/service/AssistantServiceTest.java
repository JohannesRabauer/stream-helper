package com.streamhelper.app.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.streamhelper.app.ai.AiClient;
import com.streamhelper.app.config.StreamHelperProperties;
import com.streamhelper.app.model.GenerationCategory;
import com.streamhelper.app.model.TranscriptEntry;
import com.streamhelper.app.project.ProjectStorageService;
import com.streamhelper.app.transcription.TranscriptionService;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

class AssistantServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void generatesThreeTopicVariantsAndStoresArtifacts() {
        StreamHelperProperties properties = new StreamHelperProperties();
        properties.getStorage().setDataDir(tempDir);
        ProjectStorageService storage = new ProjectStorageService(properties, new ObjectMapper().findAndRegisterModules());
        var project = storage.createProject("Test Project");

        AiClient aiClient = mock(AiClient.class);
        when(aiClient.generateText(any(), any())).thenReturn("Generated output");

        TranscriptionService transcriptionService = mock(TranscriptionService.class);

        AssistantService service = new AssistantService(
                aiClient,
                new InstructionComposer(storage),
                storage,
                new OutputValidationService(),
                transcriptionService);

        var result = service.generateTopicIdeas(project.id(), "Java + Spring");
        assertThat(result.variants()).hasSize(3);
        assertThat(storage.listArtifacts(project.id(), GenerationCategory.TOPIC_IDEA)).hasSize(3);
    }

    @Test
    void storesTranscriptFromUpload() {
        StreamHelperProperties properties = new StreamHelperProperties();
        properties.getStorage().setDataDir(tempDir);
        ProjectStorageService storage = new ProjectStorageService(properties, new ObjectMapper().findAndRegisterModules());
        var project = storage.createProject("Transcript Project");

        AiClient aiClient = mock(AiClient.class);
        TranscriptionService transcriptionService = mock(TranscriptionService.class);
        when(transcriptionService.transcribeUpload(any(), any(), anyBoolean()))
                .thenReturn(List.of(new TranscriptEntry(0, 12, "Host", "Welcome to the stream")));
        when(transcriptionService.toPlainTranscript(any())).thenReturn("[00:00 - 00:12] Host: Welcome to the stream");

        AssistantService service = new AssistantService(
                aiClient,
                new InstructionComposer(storage),
                storage,
                new OutputValidationService(),
                transcriptionService);

        MockMultipartFile file = new MockMultipartFile("file", "video.mp4", "video/mp4", "fake".getBytes());
        var result = service.transcribeFile(project.id(), file, "en", true);
        assertThat(result.variants()).hasSize(1);
        assertThat(result.variants().getFirst().getContent()).contains("Welcome to the stream");
    }
}
