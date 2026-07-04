package com.streamhelper.app.model;

import java.util.EnumMap;
import java.util.Map;

public class GlobalConfig {
    private String schemaVersion = "1";
    private String globalInstruction = "";
    private final Map<GenerationCategory, String> categoryInstructions = new EnumMap<>(GenerationCategory.class);
    private BrandProfile brandProfile = new BrandProfile();

    public String getSchemaVersion() {
        return schemaVersion;
    }

    public void setSchemaVersion(String schemaVersion) {
        this.schemaVersion = schemaVersion;
    }

    public String getGlobalInstruction() {
        return globalInstruction;
    }

    public void setGlobalInstruction(String globalInstruction) {
        this.globalInstruction = globalInstruction == null ? "" : globalInstruction;
    }

    public Map<GenerationCategory, String> getCategoryInstructions() {
        return categoryInstructions;
    }

    public BrandProfile getBrandProfile() {
        return brandProfile;
    }

    public void setBrandProfile(BrandProfile brandProfile) {
        this.brandProfile = brandProfile;
    }
}
