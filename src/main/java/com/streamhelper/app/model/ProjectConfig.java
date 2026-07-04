package com.streamhelper.app.model;

public class ProjectConfig {
    private String schemaVersion = "1";
    private String defaultLanguage = "en";
    private PromptDirectives directives = new PromptDirectives();
    private BrandProfile brandProfile = new BrandProfile();

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
}
