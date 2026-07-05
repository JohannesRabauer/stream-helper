package com.streamhelper.app.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.streamhelper.app.project.ProjectStorageService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class PageControllerIntegrationTest {

    private static final Path DATA_DIR = createTempDir();

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("stream-helper.storage.data-dir", DATA_DIR::toString);
    }

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ProjectStorageService storageService;

    @Test
    void projectsPageLoads() throws Exception {
        mockMvc.perform(get("/projects"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Stream Helper")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Show global LLM definitions")));
    }

    @Test
    void projectPageShowsProjectNotesDrawer() throws Exception {
        var project = storageService.createProject("Notes Sidebar");

        mockMvc.perform(get("/projects/" + project.id()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Project notes")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Show notes")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Markdown notes")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Suggest title")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Latest stage result")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Show LLM definitions")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Edit project name")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("1. Decide the stream"))));
    }

    @Test
    void projectsPageOpensProjectByClickingCard() throws Exception {
        var project = storageService.createProject("Card Link Project");

        mockMvc.perform(get("/projects"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/projects/" + project.id())))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Open workspace"))));
    }

    @Test
    void projectPageContainsInlineResultSectionsForEveryStage() throws Exception {
        var project = storageService.createProject("Inline Results Project");

        mockMvc.perform(get("/projects/" + project.id()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("inline-result-pre-stream")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("inline-result-description")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("inline-result-thumbnail")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("inline-result-social-announcements")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("inline-result-transcription")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("inline-result-post-stream")));
    }

    @Test
    void projectPageDoesNotContainLegacyLatestResultDrawerMarkup() throws Exception {
        var project = storageService.createProject("No Result Drawer Project");

        mockMvc.perform(get("/projects/" + project.id()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("projectResultToggle"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("projectResultDrawer"))));
    }

    @Test
    void stylesResourceUsesScopedResultDisclosureSelector() throws Exception {
        mockMvc.perform(get("/styles.css"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(".result-raw[open] > summary::before")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("details[open] .result-raw summary::before"))));
    }

    @Test
    void projectScriptIncludesRefinementChatAndLocaleTimestampSupport() throws Exception {
        mockMvc.perform(get("/project.js"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Refinement chat")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/refine")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("createTimestampFormatter")));
    }

    private static Path createTempDir() {
        try {
            return Files.createTempDirectory("stream-helper-page-test-");
        } catch (IOException exception) {
            throw new RuntimeException(exception);
        }
    }
}
