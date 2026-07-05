package com.streamhelper.app.web;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
    void generatesYoutubeTitlesAsSeparateSelectableVariants() throws Exception {
        String projectId = createProject("Title API");
        when(aiClient.generateText(anyString(), anyString()))
                .thenReturn("""
                        ⭐ RECOMMENDED: Spring AI in 2026: What Actually Works
                        - 10 Spring AI Mistakes to Avoid in Production
                        - Build an AI Feature in Spring Boot in 30 Minutes
                        - Beginner Guide: Your First Spring AI Service
                        - Advanced Spring AI Patterns for Real Systems
                        - From Prompt to Product: Shipping with Spring AI
                        - Spring AI vs LangChain: Practical Comparison
                        - Is Spring AI Overhyped? Honest Developer Take
                        - Debugging Spring AI Apps Without Losing Hours
                        - Cost-Optimized Spring AI Architectures
                        - Designing Safer AI APIs with Spring Security
                        - Real-world RAG with Spring AI and PostgreSQL
                        - How We Scaled a Spring AI Feature to 1M Requests
                        - Event-Driven AI Workflows in Spring
                        - What to Learn Next After Spring AI Basics
                        """);

        mockMvc.perform(post("/api/projects/" + projectId + "/youtube-titles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"brief\":\"Spring + AI livestream\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.category").value("YOUTUBE_TITLES"))
                .andExpect(jsonPath("$.variants.length()").value(15))
                .andExpect(jsonPath("$.variants[0].recommended").value(true))
                .andExpect(jsonPath("$.variants[0].content").value("Spring AI in 2026: What Actually Works"));
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

    @Test
    void refinesArtifactAndKeepsThreadLineage() throws Exception {
        String projectId = createProject("Refine API");
        String generateResponse = mockMvc.perform(post("/api/projects/" + projectId + "/youtube-description")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"brief\":\"Test\"}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String artifactId = generateResponse.replaceAll(".*\"id\":\"([^\"]+)\".*", "$1");

        mockMvc.perform(post("/api/projects/" + projectId + "/artifacts/YOUTUBE_DESCRIPTION/" + artifactId + "/refine")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"prompt\":\"Shorter and more direct\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.category").value("YOUTUBE_DESCRIPTION"))
                .andExpect(jsonPath("$.variants.length()").value(1))
                .andExpect(jsonPath("$.variants[0].parentArtifactId").value(artifactId))
                .andExpect(jsonPath("$.variants[0].threadId").isNotEmpty())
                .andExpect(jsonPath("$.variants[0].refinementPrompt").value("Shorter and more direct"));
    }

    @Test
    void returnsIdleTranscriptionProgressWhenNoTaskHasRunYet() throws Exception {
        String projectId = createProject("Progress API");

        mockMvc.perform(get("/api/projects/" + projectId + "/transcripts/progress"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false))
                .andExpect(jsonPath("$.failed").value(false))
                .andExpect(jsonPath("$.percent").value(0))
                .andExpect(jsonPath("$.stage").value("idle"));
    }

    @Test
    void rejectsBlankBriefForGenerationEndpoints() throws Exception {
        String projectId = createProject("Validation API");

        mockMvc.perform(post("/api/projects/" + projectId + "/topic-ideas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"brief\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }

    @Test
    void rejectsBlankRefinementPrompt() throws Exception {
        String projectId = createProject("Refine Validation API");
        String generateResponse = mockMvc.perform(post("/api/projects/" + projectId + "/youtube-description")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"brief\":\"Test\"}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String artifactId = generateResponse.replaceAll(".*\"id\":\"([^\"]+)\".*", "$1");

        mockMvc.perform(post("/api/projects/" + projectId + "/artifacts/YOUTUBE_DESCRIPTION/" + artifactId + "/refine")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"prompt\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }

    @Test
    void returnsEffectivePromptPreview() throws Exception {
        String projectId = createProject("Prompt Preview API");

        mockMvc.perform(post("/api/projects/" + projectId + "/effective-prompt/YOUTUBE_DESCRIPTION"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.category").value("YOUTUBE_DESCRIPTION"))
                .andExpect(jsonPath("$.effectivePrompt").isString());
    }

    @Test
    void generatesThumbnailPromptVariants() throws Exception {
        String projectId = createProject("Thumbnail Prompts API");

        mockMvc.perform(post("/api/projects/" + projectId + "/thumbnail-prompts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"brief\":\"Bold contrast and clean composition\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.category").value("THUMBNAIL_PROMPT"))
                .andExpect(jsonPath("$.variants.length()").value(3));
    }

    @Test
    void createsExternalThumbnailPromptPackage() throws Exception {
        String projectId = createProject("Thumbnail External API");

        mockMvc.perform(post("/api/projects/" + projectId + "/thumbnails/create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"prompt\":\"Use high contrast red and blue\",\"builtIn\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.category").value("THUMBNAIL_ASSET"))
                .andExpect(jsonPath("$.variants.length()").value(1))
                .andExpect(jsonPath("$.variants[0].strategy").value("external-prompt-package"));
    }

    @Test
    void createsBuiltInThumbnailAssetMarker() throws Exception {
        String projectId = createProject("Thumbnail Builtin API");

        mockMvc.perform(post("/api/projects/" + projectId + "/thumbnails/create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"prompt\":\"Studio portrait lighting\",\"builtIn\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.category").value("THUMBNAIL_ASSET"))
                .andExpect(jsonPath("$.variants.length()").value(1))
                .andExpect(jsonPath("$.variants[0].strategy").value("built-in-image"));
    }

    @Test
    void returnsBadRequestForPostStreamWithoutStoredTranscript() throws Exception {
        String projectId = createProject("Missing Transcript API");

        mockMvc.perform(post("/api/projects/" + projectId + "/summary")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("REQUEST_FAILED"));
    }

    @Test
    void generatesYouTubeTagsVariant() throws Exception {
        String projectId = createProject("YouTube Tags API");

        mockMvc.perform(post("/api/projects/" + projectId + "/youtube-tags")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"brief\":\"Spring AI, coding, architecture\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.category").value("YOUTUBE_TAGS"))
                .andExpect(jsonPath("$.variants.length()").value(1))
                .andExpect(jsonPath("$.variants[0].content").isString());
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
