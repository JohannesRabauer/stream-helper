package com.streamhelper.app.model;

import java.util.ArrayList;
import java.util.List;

public class BrandProfile {
    private List<String> preferredColors = new ArrayList<>();
    private List<String> requiredWords = new ArrayList<>();
    private List<String> bannedWords = new ArrayList<>();
    private Integer thumbnailMaxWords = 4;

    public List<String> getPreferredColors() {
        return preferredColors;
    }

    public void setPreferredColors(List<String> preferredColors) {
        this.preferredColors = preferredColors == null ? new ArrayList<>() : preferredColors;
    }

    public List<String> getRequiredWords() {
        return requiredWords;
    }

    public void setRequiredWords(List<String> requiredWords) {
        this.requiredWords = requiredWords == null ? new ArrayList<>() : requiredWords;
    }

    public List<String> getBannedWords() {
        return bannedWords;
    }

    public void setBannedWords(List<String> bannedWords) {
        this.bannedWords = bannedWords == null ? new ArrayList<>() : bannedWords;
    }

    public Integer getThumbnailMaxWords() {
        return thumbnailMaxWords;
    }

    public void setThumbnailMaxWords(Integer thumbnailMaxWords) {
        this.thumbnailMaxWords = thumbnailMaxWords;
    }
}
