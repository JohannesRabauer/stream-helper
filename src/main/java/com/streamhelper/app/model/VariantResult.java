package com.streamhelper.app.model;

import java.util.List;

public record VariantResult(
        GenerationCategory category,
        String effectivePromptPreview,
        List<ArtifactVersion> variants,
        List<ValidationIssue> validationIssues) {}
