package com.streamhelper.app.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.streamhelper.app.config.StreamHelperProperties;
import com.streamhelper.app.model.GenerationCategory;
import com.streamhelper.app.project.ProjectStorageService;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class InstructionComposerTest {

    @TempDir
    Path tempDir;

    @Test
    void composesInstructionLayersWithExpectedPrecedenceText() {
        StreamHelperProperties properties = new StreamHelperProperties();
        properties.getStorage().setDataDir(tempDir);
        ProjectStorageService storageService = new ProjectStorageService(properties, new ObjectMapper().findAndRegisterModules());
        var project = storageService.createProject("Demo");

        var global = storageService.readGlobalConfig();
        global.setGlobalInstruction("Global instruction");
        global.getCategoryInstructions().put(GenerationCategory.YOUTUBE_DESCRIPTION, "Global category instruction");
        storageService.saveGlobalConfig(global);

        var projectConfig = storageService.readProjectConfig(project.id());
        projectConfig.getDirectives().setProjectInstruction("Project instruction");
        projectConfig.getDirectives().getCategoryInstructions().put(GenerationCategory.YOUTUBE_DESCRIPTION, "Project category instruction");
        storageService.saveProjectConfig(project.id(), projectConfig);

        InstructionComposer composer = new InstructionComposer(storageService);
        String result = composer.compose(project.id(), GenerationCategory.YOUTUBE_DESCRIPTION);

        assertThat(result).contains("Global instruction");
        assertThat(result).contains("Project instruction");
        assertThat(result).contains("Project category instruction");
        assertThat(result).contains("Priority rules");
    }

    @Test
    void includesSavedAreaContextButExcludesStoredTranscript() {
        StreamHelperProperties properties = new StreamHelperProperties();
        properties.getStorage().setDataDir(tempDir);
        ProjectStorageService storageService = new ProjectStorageService(properties, new ObjectMapper().findAndRegisterModules());
        var project = storageService.createProject("Context Demo");

        storageService.saveNote(project.id(), InstructionComposer.PROJECT_NOTE_ID, "Remember to keep the stream practical.");
        storageService.saveArtifact(project.id(), GenerationCategory.TOPIC_IDEA, "default", "Build an MCP debugging stream", true, true);
        storageService.saveArtifact(project.id(), GenerationCategory.TRANSCRIPT, "transcription", "Huge transcript body should stay out of shared context", true, false);

        var projectConfig = storageService.readProjectConfig(project.id());
        projectConfig.setHostDisplayName("Johannes");
        projectConfig.setGuestDisplayName("Guest Expert");
        storageService.saveProjectConfig(project.id(), projectConfig);

        InstructionComposer composer = new InstructionComposer(storageService);
        String result = composer.compose(project.id(), GenerationCategory.YOUTUBE_DESCRIPTION);

        assertThat(result).contains("Remember to keep the stream practical.");
        assertThat(result).contains("Build an MCP debugging stream");
        assertThat(result).contains("Johannes");
        assertThat(result).doesNotContain("Huge transcript body should stay out of shared context");
    }

    @Test
    void thumbnailIdeasUseDescriptionContextAndThumbnailPromptsReuseSavedIdeas() {
        StreamHelperProperties properties = new StreamHelperProperties();
        properties.getStorage().setDataDir(tempDir);
        ProjectStorageService storageService = new ProjectStorageService(properties, new ObjectMapper().findAndRegisterModules());
        var project = storageService.createProject("Thumbnail Context Demo");

        storageService.saveArtifact(
                project.id(), GenerationCategory.YOUTUBE_DESCRIPTION, "default", "Created YouTube description context", true, true);
        storageService.saveArtifact(
                project.id(), GenerationCategory.THUMBNAIL_IDEA, "idea-01", "IDEA 01: Dramatic split-screen visual", true, false);
        storageService.saveArtifact(
                project.id(), GenerationCategory.THUMBNAIL_PROMPT, "high-contrast", "Legacy thumbnail prompt context", true, false);

        InstructionComposer composer = new InstructionComposer(storageService);

        String ideasContext = composer.compose(project.id(), GenerationCategory.THUMBNAIL_IDEA);
        assertThat(ideasContext).contains("Created YouTube description context");
        assertThat(ideasContext).doesNotContain("Legacy thumbnail prompt context");

        String promptContext = composer.compose(project.id(), GenerationCategory.THUMBNAIL_PROMPT);
        assertThat(promptContext).contains("Created YouTube description context");
        assertThat(promptContext).contains("IDEA 01: Dramatic split-screen visual");
        assertThat(promptContext).contains("Legacy thumbnail prompt context");
    }
}
