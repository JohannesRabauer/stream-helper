package com.streamhelper.app.service;

import com.streamhelper.app.model.GenerationCategory;
import com.streamhelper.app.model.ValidationIssue;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class OutputValidationService {

    public List<ValidationIssue> validate(GenerationCategory category, String content) {
        List<ValidationIssue> issues = new ArrayList<>();
        if (category == GenerationCategory.YOUTUBE_TAGS) {
            validateYouTubeTags(content, issues);
        }
        if (category == GenerationCategory.HASHTAGS) {
            validateHashtags(content, issues);
        }
        if (category == GenerationCategory.SOCIAL_POST) {
            validateSocialPost(content, issues);
        }
        if (category == GenerationCategory.CHAPTERS) {
            validateChapters(content, issues);
        }
        return issues;
    }

    public String normalize(GenerationCategory category, String content) {
        if (category == GenerationCategory.YOUTUBE_TAGS) {
            return normalizeYouTubeTags(content);
        }
        if (category == GenerationCategory.HASHTAGS) {
            return normalizeHashtags(content);
        }
        if (category == GenerationCategory.CHAPTERS) {
            return normalizeChapters(content);
        }
        return content == null ? "" : content.trim();
    }

    private String normalizeYouTubeTags(String content) {
        if (content == null) {
            return "";
        }
        String[] rawTokens = content.split(",");
        List<String> tags = new ArrayList<>();
        for (String token : rawTokens) {
            String cleaned = token.trim();
            if (!cleaned.isBlank()) {
                tags.add(cleaned);
            }
        }
        StringBuilder builder = new StringBuilder();
        for (String tag : tags) {
            String candidate = builder.isEmpty() ? tag : builder + ", " + tag;
            if (candidate.length() > 500) {
                break;
            }
            if (!builder.isEmpty()) {
                builder.append(", ");
            }
            builder.append(tag);
        }
        return builder.toString();
    }

    private void validateYouTubeTags(String content, List<ValidationIssue> issues) {
        if (content == null || content.isBlank()) {
            issues.add(new ValidationIssue("YT_TAGS_EMPTY", "YouTube tags are empty."));
            return;
        }
        if (content.length() > 500) {
            issues.add(new ValidationIssue("YT_TAGS_TOO_LONG", "YouTube tags exceed 500 characters."));
        }
    }

    private String normalizeHashtags(String content) {
        if (content == null) {
            return "";
        }
        String[] tokens = content.split("\\s+|,");
        Set<String> unique = new HashSet<>();
        List<String> result = new ArrayList<>();
        for (String token : tokens) {
            String normalized = token.trim();
            if (normalized.isBlank()) {
                continue;
            }
            if (!normalized.startsWith("#")) {
                normalized = "#" + normalized;
            }
            String key = normalized.toLowerCase(Locale.ROOT);
            if (unique.add(key)) {
                result.add(normalized);
            }
        }
        return String.join(" ", result);
    }

    private void validateHashtags(String content, List<ValidationIssue> issues) {
        if (content == null || content.isBlank()) {
            issues.add(new ValidationIssue("HASHTAGS_EMPTY", "Hashtags are empty."));
            return;
        }
        Set<String> unique = new HashSet<>();
        for (String token : content.split("\\s+|,")) {
            String normalized = token.trim().toLowerCase(Locale.ROOT);
            if (!normalized.isBlank() && !unique.add(normalized)) {
                issues.add(new ValidationIssue("HASHTAG_DUPLICATE", "Duplicate hashtag found: " + token));
                break;
            }
        }
    }

    private void validateSocialPost(String content, List<ValidationIssue> issues) {
        if (content == null || content.isBlank()) {
            issues.add(new ValidationIssue("SOCIAL_EMPTY", "Social post is empty."));
            return;
        }
        if (content.length() > 280) {
            issues.add(new ValidationIssue("SOCIAL_TOO_LONG", "Social post exceeds 280 characters."));
        }
    }

    private String normalizeChapters(String content) {
        if (content == null || content.isBlank()) {
            return "00:00 Introduction";
        }
        String normalized = content.trim();
        if (!normalized.startsWith("00:00")) {
            return "00:00 Introduction\n" + normalized;
        }
        return normalized;
    }

    private void validateChapters(String content, List<ValidationIssue> issues) {
        if (content == null || content.isBlank()) {
            issues.add(new ValidationIssue("CHAPTERS_EMPTY", "Chapter list is empty."));
            return;
        }
        String[] lines = content.split("\\R");
        int previous = -1;
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isBlank()) {
                continue;
            }
            String[] parts = trimmed.split("\\s+", 2);
            if (parts.length < 2 || !parts[0].matches("\\d{2}:\\d{2}(:\\d{2})?")) {
                issues.add(new ValidationIssue("CHAPTER_FORMAT", "Invalid chapter line: " + trimmed));
                continue;
            }
            int second = parseTimestamp(parts[0]);
            if (second < previous) {
                issues.add(new ValidationIssue("CHAPTER_ORDER", "Chapter timestamps are not ascending."));
                break;
            }
            previous = second;
        }
        if (!lines[0].trim().startsWith("00:00")) {
            issues.add(new ValidationIssue("CHAPTER_START", "First chapter should start at 00:00."));
        }
    }

    private int parseTimestamp(String value) {
        String[] parts = value.split(":");
        if (parts.length == 2) {
            return Integer.parseInt(parts[0]) * 60 + Integer.parseInt(parts[1]);
        }
        return Integer.parseInt(parts[0]) * 3600 + Integer.parseInt(parts[1]) * 60 + Integer.parseInt(parts[2]);
    }
}
