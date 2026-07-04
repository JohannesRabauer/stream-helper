package com.streamhelper.app.service;

import com.streamhelper.app.model.BrandProfile;
import com.streamhelper.app.model.GenerationCategory;
import com.streamhelper.app.model.GlobalConfig;
import com.streamhelper.app.model.ProjectConfig;
import com.streamhelper.app.project.ProjectStorageService;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class InstructionComposer {

    public static final String PRE_STREAM_NOTE_ID = "pre-stream-notes";
    public static final String PROMOTION_NOTE_ID = "promotion-notes";
    public static final String POST_STREAM_NOTE_ID = "post-stream-notes";
    public static final String TRANSCRIPTION_NOTE_ID = "transcription-notes";

    private final ProjectStorageService storageService;

    public InstructionComposer(ProjectStorageService storageService) {
        this.storageService = storageService;
    }

    public String compose(String projectId, GenerationCategory category) {
        GlobalConfig globalConfig = storageService.readGlobalConfig();
        ProjectConfig projectConfig = storageService.readProjectConfig(projectId);
        List<String> blocks = new ArrayList<>();

        add(blocks, "Global instruction", globalConfig.getGlobalInstruction());
        add(blocks, "Project instruction", projectConfig.getDirectives().getProjectInstruction());
        add(blocks, "Global category instruction", globalConfig.getCategoryInstructions().get(category));
        add(blocks, "Project category instruction", projectConfig.getDirectives().getCategoryInstructions().get(category));
        add(blocks, "Workflow notes and saved context", buildWorkflowContext(projectId, projectConfig, category));

        BrandProfile merged = merge(globalConfig.getBrandProfile(), projectConfig.getBrandProfile());
        blocks.add(renderBrandProfile(merged));
        add(blocks, "Participants", renderParticipants(projectConfig));

        blocks.add(
                """
                Priority rules: if instructions conflict, follow the most specific one.
                Precedence order: Project category > Global category > Project > Global.
                """);
        return String.join("\n\n", blocks);
    }

    public String effectivePreview(String projectId, GenerationCategory category) {
        return compose(projectId, category);
    }

    private void add(List<String> blocks, String title, String content) {
        if (content != null && !content.isBlank()) {
            blocks.add(title + ":\n" + content.trim());
        }
    }

    private BrandProfile merge(BrandProfile global, BrandProfile project) {
        BrandProfile merged = new BrandProfile();
        merged.setPreferredColors(new ArrayList<>(global.getPreferredColors()));
        merged.getPreferredColors().addAll(project.getPreferredColors());
        merged.setRequiredWords(new ArrayList<>(global.getRequiredWords()));
        merged.getRequiredWords().addAll(project.getRequiredWords());
        merged.setBannedWords(new ArrayList<>(global.getBannedWords()));
        merged.getBannedWords().addAll(project.getBannedWords());
        merged.setThumbnailMaxWords(
                Objects.requireNonNullElse(project.getThumbnailMaxWords(), global.getThumbnailMaxWords()));
        return merged;
    }

    private String renderBrandProfile(BrandProfile profile) {
        return """
                Structured brand profile:
                - preferred colors: %s
                - required words: %s
                - banned words: %s
                - thumbnail max words: %s
                """
                .formatted(
                        profile.getPreferredColors(),
                        profile.getRequiredWords(),
                        profile.getBannedWords(),
                        profile.getThumbnailMaxWords());
    }

    private String buildWorkflowContext(String projectId, ProjectConfig projectConfig, GenerationCategory targetCategory) {
        List<String> blocks = new ArrayList<>();
        add(blocks, "Pre-stream notes", storageService.readNoteOrEmpty(projectId, PRE_STREAM_NOTE_ID));
        if (usesPromotionContext(targetCategory)) {
            add(blocks, "Promotion notes", storageService.readNoteOrEmpty(projectId, PROMOTION_NOTE_ID));
        }
        if (usesPostStreamContext(targetCategory)) {
            add(blocks, "Post-stream notes", storageService.readNoteOrEmpty(projectId, POST_STREAM_NOTE_ID));
        }
        if (targetCategory == GenerationCategory.TRANSCRIPT) {
            add(blocks, "Transcription notes", storageService.readNoteOrEmpty(projectId, TRANSCRIPTION_NOTE_ID));
        }

        for (GenerationCategory category : relevantContextCategories(targetCategory)) {
            Optional<com.streamhelper.app.model.ArtifactVersion> artifact = storageService.getFinalArtifact(projectId, category);
            if (artifact.isEmpty()) {
                artifact = storageService.getLatestArtifact(projectId, category);
            }
            artifact.ifPresent(version -> add(
                    blocks,
                    humanize(category) + " (" + version.getCreatedAt() + ")",
                    version.getContent()));
        }

        return String.join("\n\n", blocks);
    }

    private boolean usesPromotionContext(GenerationCategory targetCategory) {
        return switch (targetCategory) {
            case YOUTUBE_DESCRIPTION, LINKEDIN_POST, SOCIAL_POST, HASHTAGS, YOUTUBE_TAGS, THUMBNAIL_PROMPT, THUMBNAIL_ASSET,
                            CHAPTERS, SUMMARY, TRANSCRIPT -> true;
            default -> false;
        };
    }

    private boolean usesPostStreamContext(GenerationCategory targetCategory) {
        return switch (targetCategory) {
            case CHAPTERS, SUMMARY -> true;
            default -> false;
        };
    }

    private EnumSet<GenerationCategory> relevantContextCategories(GenerationCategory targetCategory) {
        return switch (targetCategory) {
            case TOPIC_IDEA, GUEST_IDEA -> EnumSet.of(GenerationCategory.TOPIC_IDEA, GenerationCategory.GUEST_IDEA);
            case YOUTUBE_DESCRIPTION, LINKEDIN_POST, SOCIAL_POST, HASHTAGS, YOUTUBE_TAGS, THUMBNAIL_PROMPT, THUMBNAIL_ASSET -> EnumSet.of(
                    GenerationCategory.TOPIC_IDEA,
                    GenerationCategory.GUEST_IDEA,
                    GenerationCategory.YOUTUBE_DESCRIPTION,
                    GenerationCategory.LINKEDIN_POST,
                    GenerationCategory.SOCIAL_POST,
                    GenerationCategory.HASHTAGS,
                    GenerationCategory.YOUTUBE_TAGS,
                    GenerationCategory.THUMBNAIL_PROMPT);
            case CHAPTERS, SUMMARY -> EnumSet.of(
                    GenerationCategory.TOPIC_IDEA,
                    GenerationCategory.GUEST_IDEA,
                    GenerationCategory.YOUTUBE_DESCRIPTION,
                    GenerationCategory.LINKEDIN_POST,
                    GenerationCategory.SOCIAL_POST,
                    GenerationCategory.HASHTAGS,
                    GenerationCategory.YOUTUBE_TAGS,
                    GenerationCategory.THUMBNAIL_PROMPT,
                    GenerationCategory.CHAPTERS,
                    GenerationCategory.SUMMARY);
            case TRANSCRIPT -> EnumSet.of(GenerationCategory.TOPIC_IDEA, GenerationCategory.GUEST_IDEA);
        };
    }

    private String renderParticipants(ProjectConfig projectConfig) {
        String host = projectConfig.getHostDisplayName() == null ? "" : projectConfig.getHostDisplayName().trim();
        String guest = projectConfig.getGuestDisplayName() == null ? "" : projectConfig.getGuestDisplayName().trim();
        if (host.isBlank() && guest.isBlank()) {
            return "";
        }
        return """
                Conversation participants:
                - host: %s
                - guest: %s
                When speaker attribution is ambiguous, say so instead of guessing.
                """
                .formatted(host.isBlank() ? "Host" : host, guest.isBlank() ? "Guest" : guest);
    }

    private String humanize(GenerationCategory category) {
        return switch (category) {
            case TOPIC_IDEA -> "Topic ideas";
            case GUEST_IDEA -> "Guest ideas";
            case YOUTUBE_DESCRIPTION -> "YouTube descriptions";
            case LINKEDIN_POST -> "LinkedIn posts";
            case SOCIAL_POST -> "Social posts";
            case HASHTAGS -> "Hashtags";
            case YOUTUBE_TAGS -> "YouTube tags";
            case TRANSCRIPT -> "Transcript";
            case CHAPTERS -> "Chapters";
            case SUMMARY -> "Summary";
            case THUMBNAIL_PROMPT -> "Thumbnail prompts";
            case THUMBNAIL_ASSET -> "Thumbnail assets";
        };
    }
}
