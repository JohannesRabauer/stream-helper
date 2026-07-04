package com.streamhelper.app.service;

import com.streamhelper.app.ai.AiClient;
import com.streamhelper.app.ai.AiClientException;
import com.streamhelper.app.model.ArtifactVersion;
import com.streamhelper.app.model.GenerationCategory;
import com.streamhelper.app.model.ProjectConfig;
import com.streamhelper.app.model.TranscriptEntry;
import com.streamhelper.app.model.ValidationIssue;
import com.streamhelper.app.model.VariantResult;
import com.streamhelper.app.project.ProjectStorageService;
import com.streamhelper.app.transcription.TranscriptionService;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class AssistantService {

    private static final Logger logger = LoggerFactory.getLogger(AssistantService.class);

    private final AiClient aiClient;
    private final InstructionComposer instructionComposer;
    private final ProjectStorageService storageService;
    private final OutputValidationService validationService;
    private final TranscriptionService transcriptionService;

    public AssistantService(
            AiClient aiClient,
            InstructionComposer instructionComposer,
            ProjectStorageService storageService,
            OutputValidationService validationService,
            TranscriptionService transcriptionService) {
        this.aiClient = aiClient;
        this.instructionComposer = instructionComposer;
        this.storageService = storageService;
        this.validationService = validationService;
        this.transcriptionService = transcriptionService;
    }

    public VariantResult generateTopicIdeas(String projectId, String brief) {
        return generateVariants(
                projectId,
                GenerationCategory.TOPIC_IDEA,
                brief,
                List.of("pragmatic", "beginner-friendly", "hype"),
                """
                Generate one stream topic idea per strategy with:
                - title
                - why it matters now
                - expected audience
                - concrete coding deliverable
                """);
    }

    public VariantResult generateGuestIdeas(String projectId, String brief) {
        return generateVariants(
                projectId,
                GenerationCategory.GUEST_IDEA,
                brief,
                List.of("deep-technical", "community-builder", "pragmatic practitioner"),
                """
                Recommend one guest candidate profile per strategy using curated internal context.
                Include: guest archetype, fit reason, conversation angle, outreach hook.
                """);
    }

    public VariantResult generateYouTubeDescriptions(String projectId, String brief) {
        return generateVariants(
                projectId,
                GenerationCategory.YOUTUBE_DESCRIPTION,
                brief,
                List.of("technical", "beginner-friendly", "hype"),
                "Write a polished YouTube description with clear value, agenda, and call to action.");
    }

    public VariantResult generateLinkedInPosts(String projectId, String brief) {
        return generateVariants(
                projectId,
                GenerationCategory.LINKEDIN_POST,
                brief,
                List.of("thought-leadership", "story-driven", "technical insights"),
                "Write a LinkedIn post that feels professional and clear.");
    }

    public VariantResult generateSocialPosts(String projectId, String brief) {
        VariantResult result = generateVariants(
                projectId,
                GenerationCategory.SOCIAL_POST,
                brief,
                List.of("technical", "beginner-friendly", "hype"),
                "Write short social posts for X/Twitter style with max 280 characters.");
        List<ArtifactVersion> normalized = new ArrayList<>();
        for (ArtifactVersion artifact : result.variants()) {
            String trimmed = artifact.getContent();
            if (trimmed.length() > 280) {
                trimmed = trimmed.substring(0, 277) + "...";
            }
            normalized.add(storageService.saveArtifact(
                    projectId,
                    GenerationCategory.SOCIAL_POST,
                    artifact.getStrategy() + "-normalized",
                    trimmed,
                    false,
                    false));
        }
        return new VariantResult(result.category(), result.effectivePromptPreview(), normalized, result.validationIssues());
    }

    public VariantResult generateHashtags(String projectId, String brief) {
        return generateVariants(
                projectId,
                GenerationCategory.HASHTAGS,
                brief,
                List.of("default"),
                "Generate one line with relevant hashtags for YouTube and LinkedIn. Avoid duplicates.");
    }

    public VariantResult generateYoutubeTags(String projectId, String brief) {
        return generateVariants(
                projectId,
                GenerationCategory.YOUTUBE_TAGS,
                brief,
                List.of("default"),
                """
                Generate comma-separated YouTube tags. Hard requirement:
                - total length <= 500 chars
                - no duplicates
                """);
    }

    public VariantResult transcribeFile(String projectId, MultipartFile file, String language, boolean diarize) {
        logger.info(
                "Transcribe file requested: projectId={}, filename={}, sizeBytes={}, language={}, diarize={}",
                projectId,
                file.getOriginalFilename(),
                file.getSize(),
                language,
                diarize);
        var entries = applyParticipantLabels(projectId, transcriptionService.transcribeUpload(file, language, diarize));
        String transcript = transcriptionService.toPlainTranscript(entries);
        String normalized = validationService.normalize(GenerationCategory.TRANSCRIPT, transcript);
        ArtifactVersion artifact = storageService.saveArtifact(
                projectId, GenerationCategory.TRANSCRIPT, "transcription", normalized, true, false);
        logger.info(
                "Transcribe file completed: projectId={}, entries={}, transcriptChars={}",
                projectId,
                entries.size(),
                normalized.length());
        return new VariantResult(
                GenerationCategory.TRANSCRIPT,
                instructionComposer.effectivePreview(projectId, GenerationCategory.TRANSCRIPT),
                List.of(artifact),
                List.of());
    }

    public VariantResult transcribeYoutube(String projectId, String youtubeUrl, String language, boolean diarize) {
        logger.info(
                "Transcribe YouTube requested: projectId={}, url={}, language={}, diarize={}",
                projectId,
                youtubeUrl,
                language,
                diarize);
        var entries = applyParticipantLabels(projectId, transcriptionService.transcribeYoutube(youtubeUrl, language, diarize));
        String transcript = transcriptionService.toPlainTranscript(entries);
        String normalized = validationService.normalize(GenerationCategory.TRANSCRIPT, transcript);
        ArtifactVersion artifact = storageService.saveArtifact(
                projectId, GenerationCategory.TRANSCRIPT, "youtube-transcription", normalized, true, false);
        logger.info(
                "Transcribe YouTube completed: projectId={}, url={}, entries={}, transcriptChars={}",
                projectId,
                youtubeUrl,
                entries.size(),
                normalized.length());
        return new VariantResult(
                GenerationCategory.TRANSCRIPT,
                instructionComposer.effectivePreview(projectId, GenerationCategory.TRANSCRIPT),
                List.of(artifact),
                List.of());
    }

    public VariantResult generateChapters(String projectId, String transcript) {
        return generateVariants(
                projectId,
                GenerationCategory.CHAPTERS,
                resolvePostStreamInput(projectId, transcript),
                List.of("default"),
                """
                Create YouTube chapters from the transcript.
                Output format must be one chapter per line:
                MM:SS Chapter Title
                Ensure first chapter starts at 00:00 and timestamps ascend.
                """);
    }

    public VariantResult generateSummary(String projectId, String transcript) {
        return generateVariants(
                projectId,
                GenerationCategory.SUMMARY,
                resolvePostStreamInput(projectId, transcript),
                List.of("default"),
                """
                Produce a detailed summary with:
                - key technical topics
                - decisions and trade-offs
                - notable code/architecture points
                - follow-up ideas
                """);
    }

    public VariantResult generateThumbnailPrompts(String projectId, String brief) {
        return generateVariants(
                projectId,
                GenerationCategory.THUMBNAIL_PROMPT,
                brief,
                List.of("high-contrast", "clean-professional", "bold-hype"),
                """
                Produce a thumbnail design prompt including:
                composition, foreground subject, background style, text overlay guidance, color cues.
                Keep title words concise and high impact.
                """);
    }

    public VariantResult createThumbnail(String projectId, String selectedPrompt, boolean builtIn) {
        if (!builtIn) {
            ArtifactVersion artifact = storageService.saveArtifact(
                    projectId,
                    GenerationCategory.THUMBNAIL_ASSET,
                    "external-prompt-package",
                    selectedPrompt,
                    true,
                    false);
            return new VariantResult(
                    GenerationCategory.THUMBNAIL_ASSET,
                    instructionComposer.effectivePreview(projectId, GenerationCategory.THUMBNAIL_ASSET),
                    List.of(artifact),
                    List.of());
        }

        byte[] png = aiClient.generateImagePng(selectedPrompt)
                .orElseThrow(() -> new AiClientException("Built-in image generation is not available for current provider"));
        storageService.writeBinaryAsset(
                projectId, GenerationCategory.THUMBNAIL_ASSET, "png", new ByteArrayInputStream(png));
        ArtifactVersion artifact = storageService.saveArtifact(
                projectId,
                GenerationCategory.THUMBNAIL_ASSET,
                "built-in-image",
                "Generated PNG image stored in project outputs.",
                true,
                false);
        return new VariantResult(
                GenerationCategory.THUMBNAIL_ASSET,
                instructionComposer.effectivePreview(projectId, GenerationCategory.THUMBNAIL_ASSET),
                List.of(artifact),
                List.of());
    }

    public ArtifactVersion markFinal(String projectId, GenerationCategory category, String artifactId) {
        return storageService.markFinal(projectId, category, artifactId);
    }

    private VariantResult generateVariants(
            String projectId, GenerationCategory category, String brief, List<String> strategies, String taskPrompt) {
        String effective = instructionComposer.compose(projectId, category);
        List<ArtifactVersion> artifacts = new ArrayList<>();
        List<ValidationIssue> issues = new ArrayList<>();
        for (int index = 0; index < strategies.size(); index++) {
            String strategy = strategies.get(index);
            String userPrompt = """
                    Category: %s
                    Strategy: %s
                    Input:
                    %s

                    Task:
                    %s
                    """
                    .formatted(category.name(), strategy, brief == null ? "" : brief, taskPrompt);
            String content = aiClient.generateText(effective, userPrompt).trim();
            content = validationService.normalize(category, content);
            issues.addAll(validationService.validate(category, content));
            artifacts.add(storageService.saveArtifact(projectId, category, strategy, content, index == 0, false));
        }
        return new VariantResult(category, effective, artifacts, issues);
    }

    private String resolvePostStreamInput(String projectId, String additionalInstructions) {
        String transcript = storageService.getLatestArtifact(projectId, GenerationCategory.TRANSCRIPT)
                .map(ArtifactVersion::getContent)
                .orElseThrow(() -> new AiClientException("No stored transcript found. Transcribe a video first or paste transcript text."));
        if (additionalInstructions == null || additionalInstructions.isBlank()) {
            return transcript;
        }
        return """
                Transcript:
                %s

                Additional focus:
                %s
                """
                .formatted(transcript, additionalInstructions.trim());
    }

    private List<TranscriptEntry> applyParticipantLabels(String projectId, List<TranscriptEntry> entries) {
        ProjectConfig config = storageService.readProjectConfig(projectId);
        String host = trimToNull(config.getHostDisplayName());
        String guest = trimToNull(config.getGuestDisplayName());
        if (host == null && guest == null) {
            return entries;
        }

        Map<String, String> aliases = new LinkedHashMap<>();
        for (TranscriptEntry entry : entries) {
            String speaker = trimToNull(entry.speaker());
            if (speaker == null || "Unknown".equalsIgnoreCase(speaker) || aliases.containsKey(speaker)) {
                continue;
            }
            if (aliases.isEmpty() && host != null) {
                aliases.put(speaker, host);
                continue;
            }
            if (aliases.size() == 1 && guest != null) {
                aliases.put(speaker, guest);
            }
        }
        if (aliases.isEmpty()) {
            return entries;
        }

        List<TranscriptEntry> renamed = new ArrayList<>(entries.size());
        for (TranscriptEntry entry : entries) {
            renamed.add(new TranscriptEntry(
                    entry.startSeconds(),
                    entry.endSeconds(),
                    aliases.getOrDefault(entry.speaker(), entry.speaker()),
                    entry.text()));
        }
        return renamed;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isBlank() ? null : trimmed;
    }
}
