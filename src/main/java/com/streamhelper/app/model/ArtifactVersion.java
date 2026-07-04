package com.streamhelper.app.model;

import java.time.OffsetDateTime;

public class ArtifactVersion {
    private String id;
    private GenerationCategory category;
    private String strategy;
    private String content;
    private String parentArtifactId;
    private String threadId;
    private String refinementPrompt;
    private boolean recommended;
    private boolean finalVersion;
    private OffsetDateTime createdAt;

    public static ArtifactVersion create(
            String id,
            GenerationCategory category,
            String strategy,
            String content,
            String parentArtifactId,
            String threadId,
            String refinementPrompt,
            boolean recommended,
            boolean finalVersion,
            OffsetDateTime createdAt) {
        ArtifactVersion version = new ArtifactVersion();
        version.id = id;
        version.category = category;
        version.strategy = strategy;
        version.content = content;
        version.parentArtifactId = parentArtifactId;
        version.threadId = threadId;
        version.refinementPrompt = refinementPrompt;
        version.recommended = recommended;
        version.finalVersion = finalVersion;
        version.createdAt = createdAt;
        return version;
    }

    public String getId() {
        return id;
    }

    public GenerationCategory getCategory() {
        return category;
    }

    public String getStrategy() {
        return strategy;
    }

    public String getContent() {
        return content;
    }

    public String getParentArtifactId() {
        return parentArtifactId;
    }

    public String getThreadId() {
        return threadId;
    }

    public String getRefinementPrompt() {
        return refinementPrompt;
    }

    public boolean isRecommended() {
        return recommended;
    }

    public boolean isFinalVersion() {
        return finalVersion;
    }

    public void setFinalVersion(boolean finalVersion) {
        this.finalVersion = finalVersion;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
