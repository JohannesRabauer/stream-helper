package com.streamhelper.app.service;

import com.streamhelper.app.model.BrandProfile;
import com.streamhelper.app.model.GenerationCategory;
import com.streamhelper.app.model.GlobalConfig;
import com.streamhelper.app.model.ProjectConfig;
import com.streamhelper.app.project.ProjectStorageService;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;

@Service
public class InstructionComposer {

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

        BrandProfile merged = merge(globalConfig.getBrandProfile(), projectConfig.getBrandProfile());
        blocks.add(renderBrandProfile(merged));

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
}
