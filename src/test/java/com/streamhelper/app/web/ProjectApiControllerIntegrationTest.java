package com.streamhelper.app.web;

import static org.hamcrest.Matchers.containsString;
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

    private static Path createTempDir() {
        try {
            return Files.createTempDirectory("stream-helper-project-api-test-");
        } catch (IOException exception) {
            throw new RuntimeException(exception);
        }
    }
}
