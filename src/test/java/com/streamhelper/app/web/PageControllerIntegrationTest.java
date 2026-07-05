package com.streamhelper.app.web;

import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
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

    @Test
    void rootUrlRedirectsToProjectsPage() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/projects"));
    }

    @Test
    void nonExistentApiProjectReturns404() throws Exception {
        mockMvc.perform(get("/api/projects/does-not-exist-xyz-abc123"))
                .andExpect(status().isNotFound())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("NOT_FOUND")));
    }

    @Test
    void projectsPageShowsCreatedProjectTile() throws Exception {
        var project = storageService.createProject("Tile Test Project");

        mockMvc.perform(get("/projects"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Tile Test Project")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/projects/" + project.id())));
    }

    @Test
    void projectPageHasStatusBarHiddenByDefault() throws Exception {
        var project = storageService.createProject("Status Bar Project");

        String html = mockMvc.perform(get("/projects/" + project.id()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        org.assertj.core.api.Assertions.assertThat(html)
                .contains("id=\"statusBar\"")
                .contains("hidden");
    }

    @Test
    void projectPageDrawersHaveCorrectInitialAriaState() throws Exception {
        var project = storageService.createProject("Aria Project");

        mockMvc.perform(get("/projects/" + project.id()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("aria-hidden=\"true\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("aria-expanded=\"false\"")));
    }

    @Test
    void projectScriptHasModuleLevelStartTranscriptionProgressMonitor() throws Exception {
        mockMvc.perform(get("/project.js"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\nfunction startTranscriptionProgressMonitor(")))
                .andExpect(content().string(not(org.hamcrest.Matchers.containsString("  function startTranscriptionProgressMonitor("))));
    }

    @Test
    void projectScriptHasModuleLevelResolveRefinementTurnsForArtifact() throws Exception {
        mockMvc.perform(get("/project.js"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\nfunction resolveRefinementTurnsForArtifact(")))
                .andExpect(content().string(not(org.hamcrest.Matchers.containsString("  function resolveRefinementTurnsForArtifact("))));
    }

    @Test
    void projectScriptHasModuleLevelInitializeTranscriptionProgress() throws Exception {
        mockMvc.perform(get("/project.js"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\nasync function initializeTranscriptionProgress(")))
                .andExpect(content().string(not(org.hamcrest.Matchers.containsString("  async function initializeTranscriptionProgress("))));
    }

    @Test
    void projectScriptHasModuleLevelStopTranscriptionProgressPolling() throws Exception {
        mockMvc.perform(get("/project.js"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\nfunction stopTranscriptionProgressPolling(")))
                .andExpect(content().string(not(org.hamcrest.Matchers.containsString("  function stopTranscriptionProgressPolling("))));
    }

    @Test
    void projectPageContainsAllWorkflowTabButtons() throws Exception {
        var project = storageService.createProject("Tabs Project");

        mockMvc.perform(get("/projects/" + project.id()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("data-tab=\"pre-stream\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("data-tab=\"description\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("data-tab=\"thumbnail\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("data-tab=\"social-announcements\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("data-tab=\"transcription\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("data-tab=\"post-stream\"")));
    }

    @Test
    void thumbnailStagePlacesIdeaGenerationBeforePromptGeneration() throws Exception {
        var project = storageService.createProject("Thumbnail Order Project");

        String html = mockMvc.perform(get("/projects/" + project.id()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        int ideasButtonIndex = html.indexOf("runStageBriefAction('thumbnail', 'thumbnail-ideas'");
        int promptsButtonIndex = html.indexOf("runStageBriefAction('thumbnail', 'thumbnail-prompts'");

        org.assertj.core.api.Assertions.assertThat(ideasButtonIndex).isGreaterThan(-1);
        org.assertj.core.api.Assertions.assertThat(promptsButtonIndex).isGreaterThan(ideasButtonIndex);
    }

    @Test
    void projectPageContainsPreStreamTabPanelNotHidden() throws Exception {
        var project = storageService.createProject("Pre-stream Panel Project");

        String html = mockMvc.perform(get("/projects/" + project.id()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        org.assertj.core.api.Assertions.assertThat(html)
                .contains("data-tab-panel=\"pre-stream\"")
                .doesNotContain("data-tab-panel=\"pre-stream\" hidden");
    }

    @Test
    void projectPageTitleContainsProjectName() throws Exception {
        var project = storageService.createProject("Named Title Project");

        mockMvc.perform(get("/projects/" + project.id()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Named Title Project")));
    }

    @Test
    void projectPageContainsExportZipButton() throws Exception {
        var project = storageService.createProject("Export Button Project");

        mockMvc.perform(get("/projects/" + project.id()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Export ZIP")));
    }

    @Test
    void projectPageContainsBackToProjectsLink() throws Exception {
        var project = storageService.createProject("Back Link Project");

        mockMvc.perform(get("/projects/" + project.id()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("href=\"/projects\"")));
    }

    @Test
    void projectsPageContainsCreateProjectForm() throws Exception {
        mockMvc.perform(get("/projects"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("New project")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("action=\"/projects\"")));
    }

    @Test
    void projectsPageScriptIncludesGlobalConfig() throws Exception {
        mockMvc.perform(get("/projects"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("initialGlobalConfig")));
    }

    private static Path createTempDir() {
        try {
            return Files.createTempDirectory("stream-helper-page-test-");
        } catch (IOException exception) {
            throw new RuntimeException(exception);
        }
    }
}
