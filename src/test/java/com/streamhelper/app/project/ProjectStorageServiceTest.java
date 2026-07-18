package com.streamhelper.app.project;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.streamhelper.app.config.StreamHelperProperties;
import com.streamhelper.app.model.GenerationCategory;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProjectStorageServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void createsProjectAndPersistsNoteAndArtifacts() {
        StreamHelperProperties properties = new StreamHelperProperties();
        properties.getStorage().setDataDir(tempDir);
        ProjectStorageService storage = new ProjectStorageService(properties, new ObjectMapper().findAndRegisterModules());

        var project = storage.createProject("My stream");
        assertThat(storage.listProjects()).hasSize(1);

        String noteId = storage.saveNote(project.id(), "", "# Hello");
        assertThat(storage.readNote(project.id(), noteId)).contains("Hello");

        var draftA = storage.saveArtifact(project.id(), GenerationCategory.SUMMARY, "v1", "content1", true, false);
        var draftB = storage.saveArtifact(project.id(), GenerationCategory.SUMMARY, "v2", "content2", false, false);
        storage.markFinal(project.id(), GenerationCategory.SUMMARY, draftB.getId());

        assertThat(storage.listArtifacts(project.id(), GenerationCategory.SUMMARY)).hasSize(2);
        assertThat(storage.getFinalArtifact(project.id(), GenerationCategory.SUMMARY)).isPresent();
        assertThat(storage.getFinalArtifact(project.id(), GenerationCategory.SUMMARY).get().getId()).isEqualTo(draftB.getId());
        assertThat(storage.readProjectConfig(project.id()).getSchemaVersion()).isEqualTo("1");
    }

    @Test
    void savesManualArtifactAndMarksItFinal() {
        StreamHelperProperties properties = new StreamHelperProperties();
        properties.getStorage().setDataDir(tempDir);
        ProjectStorageService storage = new ProjectStorageService(properties, new ObjectMapper().findAndRegisterModules());

        var project = storage.createProject("Manual Artifact Project");
        storage.saveArtifact(project.id(), GenerationCategory.SUMMARY, "ai-generated", "Generated summary", true, false);

        var manual = storage.saveManualArtifact(project.id(), GenerationCategory.SUMMARY, "Manually pasted summary");

        assertThat(manual.getStrategy()).isEqualTo("manual-entry");
        assertThat(manual.isFinalVersion()).isTrue();
        assertThat(storage.getFinalArtifact(project.id(), GenerationCategory.SUMMARY)).isPresent();
        assertThat(storage.getFinalArtifact(project.id(), GenerationCategory.SUMMARY).get().getId()).isEqualTo(manual.getId());
    }
}
