package com.streamhelper.app.model;

import java.util.EnumMap;
import java.util.Map;

public class PromptDirectives {

    private String projectInstruction = "";
    private final Map<GenerationCategory, String> categoryInstructions = new EnumMap<>(GenerationCategory.class);

    public String getProjectInstruction() {
        return projectInstruction;
    }

    public void setProjectInstruction(String projectInstruction) {
        this.projectInstruction = projectInstruction == null ? "" : projectInstruction;
    }

    public Map<GenerationCategory, String> getCategoryInstructions() {
        return categoryInstructions;
    }
}
