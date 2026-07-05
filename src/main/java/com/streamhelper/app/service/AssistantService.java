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
import com.streamhelper.app.transcription.TranscriptionProgressListener;
import com.streamhelper.app.transcription.TranscriptionProgressService;
import com.streamhelper.app.transcription.TranscriptionProgressSnapshot;
import com.streamhelper.app.transcription.TranscriptionService;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
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
    private final TranscriptionProgressService transcriptionProgressService;

    public AssistantService(
            AiClient aiClient,
            InstructionComposer instructionComposer,
            ProjectStorageService storageService,
            OutputValidationService validationService,
            TranscriptionService transcriptionService,
            TranscriptionProgressService transcriptionProgressService) {
        this.aiClient = aiClient;
        this.instructionComposer = instructionComposer;
        this.storageService = storageService;
        this.validationService = validationService;
        this.transcriptionService = transcriptionService;
        this.transcriptionProgressService = transcriptionProgressService;
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

    public VariantResult generateYouTubeTitles(String projectId, String brief) {
        String effective = instructionComposer.compose(projectId, GenerationCategory.YOUTUBE_TITLES);
        String userPrompt = """
                Category: %s
                Input:
                %s

                Task:
                Suggest exactly 15 YouTube titles for the same video.
                Every title must use a clearly different angle so the set spans very different areas.
                Include a spread such as: contrarian take, practical tutorial, beginner framing, advanced deep dive,
                case study, myth-busting, trend/prediction, comparison, mistakes to avoid, and storytelling.

                Output format (strict):
                - one title per line
                - mark exactly one strongest choice as: ⭐ RECOMMENDED: <title>
                - all other 14 lines as: - <title>
                - do not add explanations or extra lines
                """
                .formatted(GenerationCategory.YOUTUBE_TITLES.name(), brief == null ? "" : brief);
        String content = aiClient.generateText(effective, userPrompt).trim();
        content = validationService.normalize(GenerationCategory.YOUTUBE_TITLES, content);
        List<ValidationIssue> issues = validationService.validate(GenerationCategory.YOUTUBE_TITLES, content);

        List<ArtifactVersion> artifacts = saveYouTubeTitleOptions(projectId, content);
        return new VariantResult(GenerationCategory.YOUTUBE_TITLES, effective, artifacts, issues);
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
        transcriptionProgressService.start(projectId, "local file");
        transcriptionProgressService.update(projectId, 4, "prepare", "Validating uploaded file...");
        logger.info(
                "Transcribe file requested: projectId={}, filename={}, sizeBytes={}, language={}, diarize={}",
                projectId,
                file.getOriginalFilename(),
                file.getSize(),
                language,
                diarize);
        try {
            var entries = applyParticipantLabels(
                    projectId, transcriptionService.transcribeUpload(file, language, diarize, progressReporter(projectId)));
            String transcript = transcriptionService.toPlainTranscript(entries);
            String normalized = validationService.normalize(GenerationCategory.TRANSCRIPT, transcript);
            transcriptionProgressService.update(projectId, 96, "saving", "Saving transcript into project history...");
            ArtifactVersion artifact = storageService.saveArtifact(
                    projectId, GenerationCategory.TRANSCRIPT, "transcription", normalized, true, false);
            transcriptionProgressService.complete(projectId, "Transcription completed.");
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
        } catch (RuntimeException exception) {
            transcriptionProgressService.fail(projectId, exception.getMessage());
            throw exception;
        }
    }

    public VariantResult transcribeYoutube(String projectId, String youtubeUrl, String language, boolean diarize) {
        transcriptionProgressService.start(projectId, "YouTube URL");
        transcriptionProgressService.update(projectId, 4, "prepare", "Validating YouTube URL...");
        logger.info(
                "Transcribe YouTube requested: projectId={}, url={}, language={}, diarize={}",
                projectId,
                youtubeUrl,
                language,
                diarize);
        try {
            var entries = applyParticipantLabels(
                    projectId,
                    transcriptionService.transcribeYoutube(youtubeUrl, language, diarize, progressReporter(projectId)));
            String transcript = transcriptionService.toPlainTranscript(entries);
            String normalized = validationService.normalize(GenerationCategory.TRANSCRIPT, transcript);
            transcriptionProgressService.update(projectId, 96, "saving", "Saving transcript into project history...");
            ArtifactVersion artifact = storageService.saveArtifact(
                    projectId, GenerationCategory.TRANSCRIPT, "youtube-transcription", normalized, true, false);
            transcriptionProgressService.complete(projectId, "Transcription completed.");
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
        } catch (RuntimeException exception) {
            transcriptionProgressService.fail(projectId, exception.getMessage());
            throw exception;
        }
    }

    public TranscriptionProgressSnapshot getTranscriptionProgress(String projectId) {
        return transcriptionProgressService.get(projectId);
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
                Produce a factual, blog-ready summary of the full video.
                Focus on what was actually said, not interpretation.

                Include:
                - Main points discussed, with clear speaker attribution where available.
                - What each speaker argued, proposed, or explained.
                - Where speakers agreed.
                - Where speakers disagreed (and about what).
                - What the audience asked, suggested, or challenged.
                - Concrete outcomes, decisions, and unresolved questions.

                Constraints:
                - Stay neutral and evidence-based.
                - Do not add advice, speculation, or extra conclusions beyond the source.
                - If attribution is unclear, state that it is unclear instead of guessing.
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

    public VariantResult refineArtifact(String projectId, GenerationCategory category, String artifactId, String prompt) {
        ArtifactVersion source = storageService.getArtifact(projectId, category, artifactId);
        String effective = instructionComposer.compose(projectId, category);
        String userPrompt = """
                Category: %s
                Base strategy: %s

                Existing version:
                %s

                Refinement request:
                %s

                Task:
                Rewrite the existing version so it follows the refinement request while preserving the strongest useful parts.
                Return only the refined output content.
                """
                .formatted(
                        category.name(),
                        source.getStrategy() == null ? "version" : source.getStrategy(),
                        source.getContent() == null ? "" : source.getContent(),
                        prompt == null ? "" : prompt.trim());
        String content = aiClient.generateText(effective, userPrompt).trim();
        content = validationService.normalize(category, content);
        List<ValidationIssue> issues = validationService.validate(category, content);
        ArtifactVersion saved = storageService.saveArtifact(
                projectId,
                category,
                refinedStrategy(source.getStrategy()),
                content,
                source.isRecommended(),
                false,
                source.getId(),
                source.getThreadId(),
                prompt == null ? "" : prompt.trim());
        return new VariantResult(category, effective, List.of(saved), issues);
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

    private TranscriptionProgressListener progressReporter(String projectId) {
        return (percent, stage, message) -> transcriptionProgressService.update(projectId, percent, stage, message);
    }

    private String refinedStrategy(String sourceStrategy) {
        if (sourceStrategy == null || sourceStrategy.isBlank()) {
            return "refined";
        }
        if (sourceStrategy.endsWith("-refined")) {
            return sourceStrategy;
        }
        return sourceStrategy + "-refined";
    }

    private List<ArtifactVersion> saveYouTubeTitleOptions(String projectId, String rawTitles) {
        List<TitleOption> options = extractYouTubeTitleOptions(rawTitles);
        if (options.isEmpty()) {
            return List.of(storageService.saveArtifact(
                    projectId,
                    GenerationCategory.YOUTUBE_TITLES,
                    "option-01",
                    rawTitles,
                    true,
                    false));
        }
        List<ArtifactVersion> artifacts = new ArrayList<>();
        for (int index = 0; index < options.size(); index++) {
            TitleOption option = options.get(index);
            artifacts.add(storageService.saveArtifact(
                    projectId,
                    GenerationCategory.YOUTUBE_TITLES,
                    "option-%02d".formatted(index + 1),
                    option.title(),
                    option.recommended(),
                    false));
        }
        return artifacts;
    }

    private List<TitleOption> extractYouTubeTitleOptions(String rawTitles) {
        if (rawTitles == null || rawTitles.isBlank()) {
            return List.of();
        }
        List<TitleOption> parsed = new ArrayList<>();
        Set<String> dedupe = new HashSet<>();
        for (String line : rawTitles.split("\\R")) {
            if (line == null) {
                continue;
            }
            String trimmed = line.trim();
            if (trimmed.isBlank()) {
                continue;
            }
            boolean recommended = false;
            if (trimmed.startsWith("⭐ RECOMMENDED:")) {
                recommended = true;
                trimmed = trimmed.substring("⭐ RECOMMENDED:".length()).trim();
            } else if (trimmed.toUpperCase(Locale.ROOT).startsWith("RECOMMENDED:")) {
                recommended = true;
                trimmed = trimmed.substring("RECOMMENDED:".length()).trim();
            } else if (trimmed.startsWith("-")) {
                trimmed = trimmed.substring(1).trim();
            } else if (trimmed.startsWith("•")) {
                trimmed = trimmed.substring(1).trim();
            } else {
                trimmed = trimmed.replaceFirst("^\\d+[.)]\\s*", "").trim();
            }
            if (trimmed.isBlank()) {
                continue;
            }
            String key = trimmed.toLowerCase(Locale.ROOT);
            if (dedupe.add(key)) {
                parsed.add(new TitleOption(trimmed, recommended));
            }
        }
        if (parsed.isEmpty()) {
            return parsed;
        }
        List<TitleOption> normalized = new ArrayList<>();
        boolean hasRecommended = false;
        for (TitleOption option : parsed) {
            if (option.recommended() && !hasRecommended) {
                normalized.add(option);
                hasRecommended = true;
            } else {
                normalized.add(new TitleOption(option.title(), false));
            }
        }
        if (!hasRecommended) {
            TitleOption first = normalized.getFirst();
            normalized.set(0, new TitleOption(first.title(), true));
        }
        return normalized;
    }

    private record TitleOption(String title, boolean recommended) {}
}
