package com.streamhelper.app.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.streamhelper.app.model.GenerationCategory;
import com.streamhelper.app.project.ProjectStorageService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class ProjectApiControllerIntegrationTest {

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
    void canCreateAndReadProject() throws Exception {
        String body = """
                {"name":"API Project"}
                """;
        String response = mockMvc.perform(post("/api/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("API Project"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String projectId = response.replaceAll(".*\"id\":\"([^\"]+)\".*", "$1");

        mockMvc.perform(get("/api/projects/" + projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.project.id").value(projectId));
    }

    @Test
    void canSaveAndReadNote() throws Exception {
        String projectResponse = mockMvc.perform(post("/api/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Notes Project\"}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String projectId = projectResponse.replaceAll(".*\"id\":\"([^\"]+)\".*", "$1");

        mockMvc.perform(post("/api/projects/" + projectId + "/notes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"noteId\":\"planning\",\"markdown\":\"# Hello\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.markdown").value("# Hello"))
                .andExpect(jsonPath("$.html", containsString("<h1")));
    }

    @Test
    void canSaveEmptyAutosaveNote() throws Exception {
        String projectResponse = mockMvc.perform(post("/api/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Empty Notes Project\"}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String projectId = projectResponse.replaceAll(".*\"id\":\"([^\"]+)\".*", "$1");

        mockMvc.perform(post("/api/projects/" + projectId + "/notes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"noteId\":\"pre-stream-notes\",\"markdown\":\"\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.markdown").value(""));
    }

    @Test
    void canExportProjectZip() throws Exception {
        String projectResponse = mockMvc.perform(post("/api/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Export Project\"}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String projectId = projectResponse.replaceAll(".*\"id\":\"([^\"]+)\".*", "$1");

        mockMvc.perform(get("/api/projects/" + projectId + "/export"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_OCTET_STREAM));
    }

    @Test
    void canSaveEditedArtifactAsNewVersion() throws Exception {
        String projectResponse = mockMvc.perform(post("/api/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Artifact Edit Project\"}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String projectId = projectResponse.replaceAll(".*\"id\":\"([^\"]+)\".*", "$1");

        var original = storageService.saveArtifact(projectId, GenerationCategory.SUMMARY, "default", "Original summary", true, false);

        mockMvc.perform(put("/api/projects/" + projectId + "/artifacts/SUMMARY/" + original.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"Edited summary\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("Edited summary"))
                .andExpect(jsonPath("$.strategy").value("default-edited"))
                .andExpect(jsonPath("$.finalVersion").value(true));
    }

    @Test
    void canSaveManualArtifactVersion() throws Exception {
        String projectId = createProject("Manual Artifact API Project");

        mockMvc.perform(post("/api/projects/" + projectId + "/artifacts/SUMMARY/manual")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"Imported summary from outside\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("Imported summary from outside"))
                .andExpect(jsonPath("$.strategy").value("manual-entry"))
                .andExpect(jsonPath("$.finalVersion").value(true));

        mockMvc.perform(get("/api/projects/" + projectId + "/artifacts/SUMMARY"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].strategy").value("manual-entry"));
    }

    @Test
    void canRenameProjectViaApi() throws Exception {
        String projectId = createProject("Rename Source");

        mockMvc.perform(put("/api/projects/" + projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Renamed Project\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(projectId))
                .andExpect(jsonPath("$.name").value("Renamed Project"));
    }

    @Test
    void deletingProjectMakesItUnavailable() throws Exception {
        String projectId = createProject("Delete Project");

        mockMvc.perform(delete("/api/projects/" + projectId))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/projects/" + projectId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("NOT_FOUND"));
    }

    @Test
    void canPersistAndReadProjectConfigFields() throws Exception {
        String projectId = createProject("Config Project");

        mockMvc.perform(put("/api/projects/" + projectId + "/config")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                {
                                  "defaultLanguage":"de",
                                  "hostDisplayName":"Johannes",
                                  "guestDisplayName":"Ada",
                                  "currentWorkflowStage":"description",
                                  "workspaceDrafts":{"description":"Draft text"},
                                  "directives":{"projectInstruction":"Always concise","categoryInstructions":{"YOUTUBE_DESCRIPTION":"Use bullet points"}},
                                  "brandProfile":{"preferredColors":["purple"],"requiredWords":["stream"],"bannedWords":["hype"],"thumbnailMaxWords":3}
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.defaultLanguage").value("de"))
                .andExpect(jsonPath("$.hostDisplayName").value("Johannes"))
                .andExpect(jsonPath("$.guestDisplayName").value("Ada"))
                .andExpect(jsonPath("$.currentWorkflowStage").value("description"));

        mockMvc.perform(get("/api/projects/" + projectId + "/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.defaultLanguage").value("de"))
                .andExpect(jsonPath("$.workspaceDrafts.description").value("Draft text"))
                .andExpect(jsonPath("$.directives.projectInstruction").value("Always concise"))
                .andExpect(jsonPath("$.directives.categoryInstructions.YOUTUBE_DESCRIPTION").value("Use bullet points"));
    }

    @Test
    void listsSavedArtifactsByCategory() throws Exception {
        String projectId = createProject("Artifact List Project");
        storageService.saveArtifact(projectId, GenerationCategory.SUMMARY, "first", "First version", false, false);
        storageService.saveArtifact(projectId, GenerationCategory.SUMMARY, "second", "Second version", true, false);

        mockMvc.perform(get("/api/projects/" + projectId + "/artifacts/SUMMARY"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].content").value("Second version"));
    }

    @Test
    void canDeleteNoteAndThenReadReturnsNotFound() throws Exception {
        String projectId = createProject("Delete Note Project");
        mockMvc.perform(post("/api/projects/" + projectId + "/notes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"noteId\":\"temp-note\",\"markdown\":\"Draft\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/projects/" + projectId + "/notes/temp-note"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/projects/" + projectId + "/notes/temp-note"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("NOT_FOUND"));
    }

    @Test
    void rejectsBlankProjectNameViaApi() throws Exception {
        mockMvc.perform(post("/api/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }

    @Test
    void rejectsBlankRenameProjectNameViaApi() throws Exception {
        String projectId = createProject("Valid Name");

        mockMvc.perform(put("/api/projects/" + projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }

    @Test
    void globalConfigSavesAndReadsAcrossRequests() throws Exception {
        mockMvc.perform(put("/api/config/global")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"globalInstruction\":\"Never use dashes\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.globalInstruction").value("Never use dashes"));

        mockMvc.perform(get("/api/config/global"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.globalInstruction").value("Never use dashes"));
    }

    @Test
    void listProjectsReturnsAllCreatedProjects() throws Exception {
        createProject("List First Project");
        createProject("List Second Project");
        createProject("List Third Project");

        mockMvc.perform(get("/api/projects"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(greaterThanOrEqualTo(3)));
    }

    @Test
    void savedNoteAppearsInNoteIdList() throws Exception {
        String projectId = createProject("Note List Project");

        mockMvc.perform(post("/api/projects/" + projectId + "/notes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"noteId\":\"my-note\",\"markdown\":\"# Hello\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/projects/" + projectId + "/notes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(content().string(containsString("my-note")));
    }

    @Test
    void noteMissingNoteIdGetsGeneratedId() throws Exception {
        String projectId = createProject("Auto Note ID Project");

        mockMvc.perform(post("/api/projects/" + projectId + "/notes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"markdown\":\"# Auto note\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.noteId").isString())
                .andExpect(jsonPath("$.markdown").value("# Auto note"));
    }

    @Test
    void artifactListIsEmptyForFreshProject() throws Exception {
        String projectId = createProject("Fresh Artifacts Project");

        mockMvc.perform(get("/api/projects/" + projectId + "/artifacts/YOUTUBE_DESCRIPTION"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void projectConfigDefaultsAreReturnedForNewProject() throws Exception {
        String projectId = createProject("Default Config Project");

        mockMvc.perform(get("/api/projects/" + projectId + "/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.defaultLanguage").value("en"));
    }

    @Test
    void projectConfigDefaultLanguageCanBeUpdated() throws Exception {
        String projectId = createProject("Language Config Project");

        mockMvc.perform(put("/api/projects/" + projectId + "/config")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"defaultLanguage\":\"fr\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.defaultLanguage").value("fr"));
    }

    @Test
    void multipleSavedArtifactsAreReturnedInDescendingOrder() throws Exception {
        String projectId = createProject("Artifact Order Project");

        storageService.saveArtifact(projectId, GenerationCategory.SUMMARY, "first", "First version", false, false);
        Thread.sleep(10);
        storageService.saveArtifact(projectId, GenerationCategory.SUMMARY, "second", "Second version", true, false);

        mockMvc.perform(get("/api/projects/" + projectId + "/artifacts/SUMMARY"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].strategy").value("second"))
                .andExpect(jsonPath("$[1].strategy").value("first"));
    }

    @Test
    void readingNonExistentNoteReturns404() throws Exception {
        String projectId = createProject("Missing Note Project");

        mockMvc.perform(get("/api/projects/" + projectId + "/notes/does-not-exist"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("NOT_FOUND"));
    }

    private String createProject(String name) throws Exception {
        String response = mockMvc.perform(post("/api/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\"}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return response.replaceAll(".*\"id\":\"([^\"]+)\".*", "$1");
    }

    private static Path createTempDir() {
        try {
            return Files.createTempDirectory("stream-helper-project-api-test-");
        } catch (IOException exception) {
            throw new RuntimeException(exception);
        }
    }
}
