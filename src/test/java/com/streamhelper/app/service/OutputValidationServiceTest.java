package com.streamhelper.app.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.streamhelper.app.model.GenerationCategory;
import org.junit.jupiter.api.Test;

class OutputValidationServiceTest {

    private final OutputValidationService service = new OutputValidationService();

    @Test
    void normalizesYouTubeTagsTo500Characters() {
        String longTags = "java,spring,thymeleaf," + "x".repeat(600);
        String normalized = service.normalize(GenerationCategory.YOUTUBE_TAGS, longTags);
        assertThat(normalized.length()).isLessThanOrEqualTo(500);
    }

    @Test
    void removesDuplicateHashtags() {
        String normalized = service.normalize(GenerationCategory.HASHTAGS, "#java java #Spring #java");
        assertThat(normalized).isEqualTo("#java #Spring");
    }

    @Test
    void prependsZeroChapterWhenMissing() {
        String normalized = service.normalize(GenerationCategory.CHAPTERS, "01:23 Intro");
        assertThat(normalized).startsWith("00:00");
    }

    @Test
    void flagsSocialLengthViolations() {
        var issues = service.validate(GenerationCategory.SOCIAL_POST, "a".repeat(281));
        assertThat(issues).extracting("code").contains("SOCIAL_TOO_LONG");
    }
}
