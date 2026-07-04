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
import com.streamhelper.app.transcription.TranscriptionProgressListener;
import com.streamhelper.app.transcription.TranscriptionProgressService;
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
                transcriptionService,
                new TranscriptionProgressService());

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
        when(transcriptionService.transcribeUpload(any(), any(), anyBoolean(), any(TranscriptionProgressListener.class)))
                .thenReturn(List.of(new TranscriptEntry(0, 12, "Host", "Welcome to the stream")));
        when(transcriptionService.toPlainTranscript(any())).thenReturn("[00:00 - 00:12] Host: Welcome to the stream");

        AssistantService service = new AssistantService(
                aiClient,
                new InstructionComposer(storage),
                storage,
                new OutputValidationService(),
                transcriptionService,
                new TranscriptionProgressService());

        MockMultipartFile file = new MockMultipartFile("file", "video.mp4", "video/mp4", "fake".getBytes());
        var result = service.transcribeFile(project.id(), file, "en", true);
        assertThat(result.variants()).hasSize(1);
        assertThat(result.variants().getFirst().getContent()).contains("Welcome to the stream");
    }

    @Test
    void usesStoredTranscriptWhenPostStreamInputIsEmpty() {
        StreamHelperProperties properties = new StreamHelperProperties();
        properties.getStorage().setDataDir(tempDir);
        ProjectStorageService storage = new ProjectStorageService(properties, new ObjectMapper().findAndRegisterModules());
        var project = storage.createProject("Stored Transcript Project");
        storage.saveArtifact(project.id(), GenerationCategory.TRANSCRIPT, "youtube-transcription", "Stored transcript text", true, false);

        AiClient aiClient = mock(AiClient.class);
        when(aiClient.generateText(any(), any())).thenAnswer(invocation -> invocation.getArgument(1, String.class));

        AssistantService service = new AssistantService(
                aiClient,
                new InstructionComposer(storage),
                storage,
                new OutputValidationService(),
                mock(TranscriptionService.class),
                new TranscriptionProgressService());

        var result = service.generateChapters(project.id(), "");

        assertThat(result.variants()).hasSize(1);
        assertThat(result.variants().getFirst().getContent()).contains("Stored transcript text");
    }

    @Test
    void summaryPromptIsFactualAndBlogReady() {
        StreamHelperProperties properties = new StreamHelperProperties();
        properties.getStorage().setDataDir(tempDir);
        ProjectStorageService storage = new ProjectStorageService(properties, new ObjectMapper().findAndRegisterModules());
        var project = storage.createProject("Summary Prompt Project");
        storage.saveArtifact(project.id(), GenerationCategory.TRANSCRIPT, "youtube-transcription", "Stored transcript text", true, false);

        AiClient aiClient = mock(AiClient.class);
        when(aiClient.generateText(any(), any())).thenAnswer(invocation -> invocation.getArgument(1, String.class));

        AssistantService service = new AssistantService(
                aiClient,
                new InstructionComposer(storage),
                storage,
                new OutputValidationService(),
                mock(TranscriptionService.class),
                new TranscriptionProgressService());

        var result = service.generateSummary(project.id(), "");

        assertThat(result.variants()).hasSize(1);
        String content = result.variants().getFirst().getContent();
        assertThat(content).contains("factual, blog-ready summary");
        assertThat(content).contains("Where speakers agreed");
        assertThat(content).contains("Where speakers disagreed");
        assertThat(content).contains("What the audience asked");
        assertThat(content).contains("Do not add advice, speculation");
    }

    @Test
    void titleSuggestionsPromptAsksForFifteenDistinctOptionsWithOneRecommendation() {
        StreamHelperProperties properties = new StreamHelperProperties();
        properties.getStorage().setDataDir(tempDir);
        ProjectStorageService storage = new ProjectStorageService(properties, new ObjectMapper().findAndRegisterModules());
        var project = storage.createProject("Title Suggestions Project");

        AiClient aiClient = mock(AiClient.class);
        when(aiClient.generateText(any(), any())).thenAnswer(invocation -> invocation.getArgument(1, String.class));

        AssistantService service = new AssistantService(
                aiClient,
                new InstructionComposer(storage),
                storage,
                new OutputValidationService(),
                mock(TranscriptionService.class),
                new TranscriptionProgressService());

        var result = service.generateYouTubeTitles(project.id(), "AI coding stream");

        assertThat(result.category()).isEqualTo(GenerationCategory.YOUTUBE_TITLES);
        assertThat(result.variants()).hasSize(1);
        String content = result.variants().getFirst().getContent();
        assertThat(content).contains("Suggest exactly 15 YouTube titles");
        assertThat(content).contains("very different areas");
        assertThat(content).contains("⭐ RECOMMENDED:");
    }

    @Test
    void renamesDiarizedSpeakersUsingConfiguredParticipantNames() {
        StreamHelperProperties properties = new StreamHelperProperties();
        properties.getStorage().setDataDir(tempDir);
        ProjectStorageService storage = new ProjectStorageService(properties, new ObjectMapper().findAndRegisterModules());
        var project = storage.createProject("Speaker Mapping Project");

        var config = storage.readProjectConfig(project.id());
        config.setHostDisplayName("Johannes");
        config.setGuestDisplayName("Ada");
        storage.saveProjectConfig(project.id(), config);

        AiClient aiClient = mock(AiClient.class);
        TranscriptionService transcriptionService = mock(TranscriptionService.class);
        when(transcriptionService.transcribeUpload(any(), any(), anyBoolean(), any(TranscriptionProgressListener.class)))
                .thenReturn(List.of(
                        new TranscriptEntry(0, 6, "Speaker 0", "Welcome back"),
                        new TranscriptEntry(6, 11, "Speaker 1", "Thanks for having me")));
        when(transcriptionService.toPlainTranscript(any())).thenCallRealMethod();

        AssistantService service = new AssistantService(
                aiClient,
                new InstructionComposer(storage),
                storage,
                new OutputValidationService(),
                transcriptionService,
                new TranscriptionProgressService());

        MockMultipartFile file = new MockMultipartFile("file", "video.mp4", "video/mp4", "fake".getBytes());
        var result = service.transcribeFile(project.id(), file, "en", true);

        assertThat(result.variants().getFirst().getContent()).contains("Johannes: Welcome back");
        assertThat(result.variants().getFirst().getContent()).contains("Ada: Thanks for having me");
    }

    @Test
    void exposesCompletedTranscriptionProgressSnapshot() {
        StreamHelperProperties properties = new StreamHelperProperties();
        properties.getStorage().setDataDir(tempDir);
        ProjectStorageService storage = new ProjectStorageService(properties, new ObjectMapper().findAndRegisterModules());
        var project = storage.createProject("Progress Snapshot Project");

        AiClient aiClient = mock(AiClient.class);
        TranscriptionService transcriptionService = mock(TranscriptionService.class);
        when(transcriptionService.transcribeUpload(any(), any(), anyBoolean(), any(TranscriptionProgressListener.class)))
                .thenReturn(List.of(new TranscriptEntry(0, 5, "Host", "Quick intro")));
        when(transcriptionService.toPlainTranscript(any())).thenReturn("[00:00 - 00:05] Host: Quick intro");
        TranscriptionProgressService progressService = new TranscriptionProgressService();

        AssistantService service = new AssistantService(
                aiClient, new InstructionComposer(storage), storage, new OutputValidationService(), transcriptionService, progressService);

        MockMultipartFile file = new MockMultipartFile("file", "video.mp4", "video/mp4", "fake".getBytes());
        service.transcribeFile(project.id(), file, "en", true);

        var snapshot = service.getTranscriptionProgress(project.id());
        assertThat(snapshot.active()).isFalse();
        assertThat(snapshot.failed()).isFalse();
        assertThat(snapshot.percent()).isEqualTo(100);
        assertThat(snapshot.stage()).isEqualTo("completed");
    }
}
