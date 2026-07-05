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
    void acceptsBlankBriefForGenerationEndpoints() throws Exception {
        String projectId = createProject("Validation API");

        mockMvc.perform(post("/api/projects/" + projectId + "/topic-ideas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"brief\":\"   \"}"))
                .andExpect(status().isOk());
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
    void generatesTenThumbnailIdeas() throws Exception {
        String projectId = createProject("Thumbnail Ideas API");
        when(aiClient.generateText(anyString(), anyString()))
                .thenReturn("""
                        IDEA 01: Hook
                        Composition: host points at error stack
                        Background: dark IDE scene
                        Color palette: red, black, white
                        Text overlay: Bug fixed in 5 min
                        Mood: urgent
                        IDEA 02: Clean
                        Composition: close-up host, centered title
                        Background: blurred code editor
                        Color palette: blue, cyan, white
                        Text overlay: Ship faster with AI
                        Mood: confident
                        IDEA 03: Face-off
                        Composition: host vs guest split-screen
                        Background: lightning effect
                        Color palette: purple, yellow, black
                        Text overlay: Java vs AI myths
                        Mood: intense
                        IDEA 04: Minimal
                        Composition: host profile with big text block
                        Background: solid gradient
                        Color palette: teal, navy, white
                        Text overlay: Spring AI, real code
                        Mood: focused
                        IDEA 05: Reaction
                        Composition: host surprised expression, code snippet card
                        Background: radial burst
                        Color palette: orange, black, white
                        Text overlay: It finally works
                        Mood: excited
                        IDEA 06: Checklist
                        Composition: host left, checklist right
                        Background: notebook texture
                        Color palette: green, white, charcoal
                        Text overlay: 3 setup rules
                        Mood: practical
                        IDEA 07: Retro
                        Composition: pixel-art frame with host avatar
                        Background: neon grid
                        Color palette: magenta, cyan, black
                        Text overlay: Debugging boss fight
                        Mood: playful
                        IDEA 08: Authority
                        Composition: host holding architecture diagram
                        Background: soft spotlight
                        Color palette: gold, navy, white
                        Text overlay: Architecture that scales
                        Mood: professional
                        IDEA 09: Story
                        Composition: before/after timeline cards
                        Background: clean studio
                        Color palette: gray, blue, lime
                        Text overlay: From bug to release
                        Mood: optimistic
                        IDEA 10: Meme
                        Composition: host + bold emoji callouts
                        Background: high-contrast collage
                        Color palette: pink, yellow, black
                        Text overlay: This code fought back
                        Mood: chaotic
                        """);

        mockMvc.perform(post("/api/projects/" + projectId + "/thumbnail-ideas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"brief\":\"Focus on the final YouTube description angle\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.category").value("THUMBNAIL_IDEA"))
                .andExpect(jsonPath("$.variants.length()").value(10))
                .andExpect(jsonPath("$.variants[0].strategy").value("idea-01"));
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

    @Test
    void generatesLinkedInPosts() throws Exception {
        String projectId = createProject("LinkedIn Posts API");

        mockMvc.perform(post("/api/projects/" + projectId + "/linkedin-post")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"brief\":\"Spring AI coding session\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.category").value("LINKEDIN_POST"))
                .andExpect(jsonPath("$.variants.length()").value(3));
    }

    @Test
    void generatesSocialPosts() throws Exception {
        String projectId = createProject("Social Posts API");

        mockMvc.perform(post("/api/projects/" + projectId + "/social-posts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"brief\":\"Announcing new Spring AI stream\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.category").value("SOCIAL_POST"))
                .andExpect(jsonPath("$.variants.length()").value(3));
    }

    @Test
    void generatesHashtags() throws Exception {
        String projectId = createProject("Hashtags API");

        mockMvc.perform(post("/api/projects/" + projectId + "/hashtags")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"brief\":\"Spring Boot AI Java\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.category").value("HASHTAGS"))
                .andExpect(jsonPath("$.variants.length()").value(1));
    }

    @Test
    void generatesGuestIdeas() throws Exception {
        String projectId = createProject("Guest Ideas API");

        mockMvc.perform(post("/api/projects/" + projectId + "/guest-ideas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"brief\":\"Spring AI experts\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.category").value("GUEST_IDEA"))
                .andExpect(jsonPath("$.variants.length()").value(3));
    }

    @Test
    void finalizingNonExistentArtifactReturns404() throws Exception {
        String projectId = createProject("Non-existent Artifact API");

        mockMvc.perform(post("/api/projects/" + projectId + "/artifacts/YOUTUBE_DESCRIPTION/non-existent-id/finalize"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("NOT_FOUND"));
    }

    @Test
    void refiningNonExistentArtifactReturns404() throws Exception {
        String projectId = createProject("Non-existent Refine API");

        mockMvc.perform(post("/api/projects/" + projectId + "/artifacts/YOUTUBE_DESCRIPTION/non-existent-id/refine")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"prompt\":\"Make it shorter\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("NOT_FOUND"));
    }

    @Test
    void allGeneratedVariantsHaveNonBlankContent() throws Exception {
        String projectId = createProject("Non-blank Content API");

        String response = mockMvc.perform(post("/api/projects/" + projectId + "/topic-ideas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"brief\":\"Spring Boot and AI\"}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();
        com.fasterxml.jackson.databind.JsonNode root = objectMapper.readTree(response);
        com.fasterxml.jackson.databind.JsonNode variants = root.get("variants");
        org.assertj.core.api.Assertions.assertThat(variants).isNotNull();
        variants.forEach(v -> org.assertj.core.api.Assertions.assertThat(v.get("content").asText()).isNotBlank());
    }

    @Test
    void generatedArtifactsHaveUniqueIds() throws Exception {
        String projectId = createProject("Unique IDs API");

        String response = mockMvc.perform(post("/api/projects/" + projectId + "/topic-ideas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"brief\":\"Testing uniqueness\"}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();
        com.fasterxml.jackson.databind.JsonNode root = objectMapper.readTree(response);
        com.fasterxml.jackson.databind.JsonNode variants = root.get("variants");
        java.util.Set<String> ids = new java.util.HashSet<>();
        variants.forEach(v -> ids.add(v.get("id").asText()));
        org.assertj.core.api.Assertions.assertThat(ids).hasSameSizeAs(new java.util.ArrayList<>(ids));
    }

    @Test
    void generatedArtifactsArePersisted() throws Exception {
        String projectId = createProject("Persistence Check API");

        mockMvc.perform(post("/api/projects/" + projectId + "/youtube-description")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"brief\":\"Testing persistence\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/projects/" + projectId + "/artifacts/YOUTUBE_DESCRIPTION"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3));
    }

    @Test
    void socialPostsAreTruncatedTo280Chars() throws Exception {
        String projectId = createProject("Social Post Truncation API");
        String longContent = "A".repeat(500);
        when(aiClient.generateText(anyString(), anyString())).thenReturn(longContent);

        String response = mockMvc.perform(post("/api/projects/" + projectId + "/social-posts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"brief\":\"Test truncation\"}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();
        com.fasterxml.jackson.databind.JsonNode root = objectMapper.readTree(response);
        root.get("variants").forEach(v -> {
            String content = v.get("content").asText();
            org.assertj.core.api.Assertions.assertThat(content.length()).isLessThanOrEqualTo(280);
        });
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
