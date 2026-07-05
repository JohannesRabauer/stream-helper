package com.streamhelper.app.web;

import com.streamhelper.app.model.ArtifactVersion;
import com.streamhelper.app.model.GenerationCategory;
import com.streamhelper.app.service.AssistantService;
import com.streamhelper.app.service.InstructionComposer;
import com.streamhelper.app.transcription.TranscriptionProgressSnapshot;
import com.streamhelper.app.web.dto.GenerationRequest;
import com.streamhelper.app.web.dto.RefineArtifactRequest;
import com.streamhelper.app.web.dto.TextInputRequest;
import com.streamhelper.app.web.dto.ThumbnailCreateRequest;
import com.streamhelper.app.web.dto.YouTubeTranscriptionRequest;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Validated
@RestController
@RequestMapping("/api/projects/{projectId}")
public class AssistantApiController {

    private final AssistantService assistantService;
    private final InstructionComposer instructionComposer;

    public AssistantApiController(AssistantService assistantService, InstructionComposer instructionComposer) {
        this.assistantService = assistantService;
        this.instructionComposer = instructionComposer;
    }

    @PostMapping("/topic-ideas")
    public Object topicIdeas(@PathVariable String projectId, @Valid @RequestBody GenerationRequest request) {
        return assistantService.generateTopicIdeas(projectId, request.brief());
    }

    @PostMapping("/guest-ideas")
    public Object guestIdeas(@PathVariable String projectId, @Valid @RequestBody GenerationRequest request) {
        return assistantService.generateGuestIdeas(projectId, request.brief());
    }

    @PostMapping("/youtube-description")
    public Object youtubeDescription(@PathVariable String projectId, @Valid @RequestBody GenerationRequest request) {
        return assistantService.generateYouTubeDescriptions(projectId, request.brief());
    }

    @PostMapping("/youtube-titles")
    public Object youtubeTitles(@PathVariable String projectId, @Valid @RequestBody GenerationRequest request) {
        return assistantService.generateYouTubeTitles(projectId, request.brief());
    }

    @PostMapping("/linkedin-post")
    public Object linkedinPost(@PathVariable String projectId, @Valid @RequestBody GenerationRequest request) {
        return assistantService.generateLinkedInPosts(projectId, request.brief());
    }

    @PostMapping("/social-posts")
    public Object socialPosts(@PathVariable String projectId, @Valid @RequestBody GenerationRequest request) {
        return assistantService.generateSocialPosts(projectId, request.brief());
    }

    @PostMapping("/hashtags")
    public Object hashtags(@PathVariable String projectId, @Valid @RequestBody GenerationRequest request) {
        return assistantService.generateHashtags(projectId, request.brief());
    }

    @PostMapping("/youtube-tags")
    public Object youtubeTags(@PathVariable String projectId, @Valid @RequestBody GenerationRequest request) {
        return assistantService.generateYoutubeTags(projectId, request.brief());
    }

    @PostMapping(value = "/transcripts/file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Object transcribeFile(
            @PathVariable String projectId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "language", required = false) String language,
            @RequestParam(value = "diarize", defaultValue = "true") boolean diarize) {
        return assistantService.transcribeFile(projectId, file, language, diarize);
    }

    @PostMapping("/transcripts/youtube")
    public Object transcribeYoutube(@PathVariable String projectId, @Valid @RequestBody YouTubeTranscriptionRequest request) {
        return assistantService.transcribeYoutube(projectId, request.youtubeUrl(), request.language(), request.diarize());
    }

    @GetMapping("/transcripts/progress")
    public TranscriptionProgressSnapshot transcriptionProgress(@PathVariable String projectId) {
        return assistantService.getTranscriptionProgress(projectId);
    }

    @PostMapping("/chapters")
    public Object chapters(@PathVariable String projectId, @Valid @RequestBody TextInputRequest request) {
        return assistantService.generateChapters(projectId, request.text());
    }

    @PostMapping("/summary")
    public Object summary(@PathVariable String projectId, @Valid @RequestBody TextInputRequest request) {
        return assistantService.generateSummary(projectId, request.text());
    }

    @PostMapping("/thumbnail-prompts")
    public Object thumbnailPrompts(@PathVariable String projectId, @Valid @RequestBody GenerationRequest request) {
        return assistantService.generateThumbnailPrompts(projectId, request.brief());
    }

    @PostMapping("/thumbnail-ideas")
    public Object thumbnailIdeas(@PathVariable String projectId, @Valid @RequestBody GenerationRequest request) {
        return assistantService.generateThumbnailIdeas(projectId, request.brief());
    }

    @PostMapping("/thumbnails/create")
    public Object createThumbnail(@PathVariable String projectId, @Valid @RequestBody ThumbnailCreateRequest request) {
        return assistantService.createThumbnail(projectId, request.prompt(), request.builtIn());
    }

    @PostMapping("/artifacts/{category}/{artifactId}/finalize")
    public ArtifactVersion markFinal(
            @PathVariable String projectId, @PathVariable GenerationCategory category, @PathVariable String artifactId) {
        return assistantService.markFinal(projectId, category, artifactId);
    }

    @PostMapping("/artifacts/{category}/{artifactId}/refine")
    public Object refineArtifact(
            @PathVariable String projectId,
            @PathVariable GenerationCategory category,
            @PathVariable String artifactId,
            @Valid @RequestBody RefineArtifactRequest request) {
        return assistantService.refineArtifact(projectId, category, artifactId, request.prompt());
    }

    @PostMapping("/effective-prompt/{category}")
    public Object effectivePrompt(@PathVariable String projectId, @PathVariable GenerationCategory category) {
        return Map.of("category", category, "effectivePrompt", instructionComposer.effectivePreview(projectId, category));
    }
}
