package com.streamhelper.app.model;

import java.util.LinkedHashMap;
import java.util.Map;

public class ProjectConfig {
    private String schemaVersion = "1";
    private String defaultLanguage = "en";
    private PromptDirectives directives = new PromptDirectives();
    private BrandProfile brandProfile = new BrandProfile();
    private String currentWorkflowStage = "pre-stream";
    private String hostDisplayName = "";
    private String guestDisplayName = "";
    private final Map<String, String> workspaceDrafts = new LinkedHashMap<>();

    public String getSchemaVersion() {
        return schemaVersion;
    }

    public void setSchemaVersion(String schemaVersion) {
        this.schemaVersion = schemaVersion;
    }

    public String getDefaultLanguage() {
        return defaultLanguage;
    }

    public void setDefaultLanguage(String defaultLanguage) {
        this.defaultLanguage = defaultLanguage;
    }

    public PromptDirectives getDirectives() {
        return directives;
    }

    public void setDirectives(PromptDirectives directives) {
        this.directives = directives;
    }

    public BrandProfile getBrandProfile() {
        return brandProfile;
    }

    public void setBrandProfile(BrandProfile brandProfile) {
        this.brandProfile = brandProfile;
    }

    public String getCurrentWorkflowStage() {
        return currentWorkflowStage;
    }

    public void setCurrentWorkflowStage(String currentWorkflowStage) {
        this.currentWorkflowStage = currentWorkflowStage == null || currentWorkflowStage.isBlank()
                ? "pre-stream"
                : currentWorkflowStage;
    }

    public String getHostDisplayName() {
        return hostDisplayName;
    }

    public void setHostDisplayName(String hostDisplayName) {
        this.hostDisplayName = hostDisplayName == null ? "" : hostDisplayName;
    }

    public String getGuestDisplayName() {
        return guestDisplayName;
    }

    public void setGuestDisplayName(String guestDisplayName) {
        this.guestDisplayName = guestDisplayName == null ? "" : guestDisplayName;
    }

    public Map<String, String> getWorkspaceDrafts() {
        return workspaceDrafts;
    }
}
