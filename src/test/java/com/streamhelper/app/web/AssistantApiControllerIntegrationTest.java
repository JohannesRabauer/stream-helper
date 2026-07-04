package com.streamhelper.app.web;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.streamhelper.app.ai.AiClient;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class AssistantApiControllerIntegrationTest {

    private static final Path DATA_DIR = createTempDir();

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("stream-helper.storage.data-dir", DATA_DIR::toString);
    }

    @Autowired
    MockMvc mockMvc;

    @MockBean
    AiClient aiClient;

    @BeforeEach
    void setUp() {
        when(aiClient.generateText(anyString(), anyString())).thenReturn("Generated content");
        when(aiClient.generateImagePng(anyString())).thenReturn(Optional.of(new byte[] {1, 2, 3}));
    }

    @Test
    void generatesTopicIdeasAndReturnsThreeVariants() throws Exception {
        String projectId = createProject("Assistant API");

        mockMvc.perform(post("/api/projects/" + projectId + "/topic-ideas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"brief\":\"Spring + AI livestream\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.category").value("TOPIC_IDEA"))
                .andExpect(jsonPath("$.variants.length()").value(3));
    }

    @Test
    void finalizesArtifact() throws Exception {
        String projectId = createProject("Finalize API");
        String response = mockMvc.perform(post("/api/projects/" + projectId + "/youtube-description")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"brief\":\"Test\"}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String artifactId = response.replaceAll(".*\"id\":\"([^\"]+)\".*", "$1");

        mockMvc.perform(post("/api/projects/" + projectId + "/artifacts/YOUTUBE_DESCRIPTION/" + artifactId + "/finalize"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(artifactId));
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
            return Files.createTempDirectory("stream-helper-assistant-api-test-");
        } catch (IOException exception) {
            throw new RuntimeException(exception);
        }
    }
}
