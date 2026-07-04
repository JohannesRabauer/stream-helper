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
}
