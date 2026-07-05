package com.streamhelper.app.web;

import com.streamhelper.app.project.ProjectStorageService;
import com.streamhelper.app.service.InstructionComposer;
import com.streamhelper.app.service.MarkdownService;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class PageController {

    private final ProjectStorageService storageService;
    private final MarkdownService markdownService;

    public PageController(ProjectStorageService storageService, MarkdownService markdownService) {
        this.storageService = storageService;
        this.markdownService = markdownService;
    }

    @GetMapping("/")
    public String index() {
        return "redirect:/projects";
    }

    @GetMapping("/projects")
    public String projects(Model model) {
        model.addAttribute("projects", storageService.listProjects());
        model.addAttribute("globalConfig", storageService.readGlobalConfig());
        return "projects";
    }

    @PostMapping("/projects")
    public String createProject(@RequestParam("name") String name, Model model) {
        if (name == null || name.isBlank()) {
            model.addAttribute("projects", storageService.listProjects());
            model.addAttribute("globalConfig", storageService.readGlobalConfig());
            model.addAttribute("error", "Project name is required");
            return "projects";
        }
        var project = storageService.createProject(name);
        return "redirect:/projects/%s".formatted(project.id());
    }

    @GetMapping("/projects/{projectId}")
    public String project(@PathVariable String projectId, @RequestParam(value = "noteId", required = false) String noteId, Model model) {
        var project = storageService.getProject(projectId);
        String projectNotes = readProjectNotes(projectId);
        model.addAttribute("project", project);
        model.addAttribute("projectConfig", storageService.readProjectConfig(projectId));
        model.addAttribute("globalConfig", storageService.readGlobalConfig());
        model.addAttribute("projectNoteMarkdown", projectNotes);
        model.addAttribute("projectNoteHtml", markdownService.render(projectNotes));
        model.addAttribute("imageProviderAvailable", isImageProviderAvailable());
        return "project";
    }

    private boolean isImageProviderAvailable() {
        String openaiKey = System.getenv("OPENAI_API_KEY");
        return openaiKey != null && !openaiKey.isBlank();
    }

    @PostMapping("/projects/{projectId}/notes")
    public String saveNote(
            @PathVariable String projectId,
            @RequestParam(value = "noteId", required = false) String noteId,
            @RequestParam("markdown") String markdown) {
        String effectiveNoteId = storageService.saveNote(projectId, noteId, markdown);
        return "redirect:/projects/%s?noteId=%s".formatted(projectId, effectiveNoteId);
    }

    private String readProjectNotes(String projectId) {
        if (storageService.listNoteIds(projectId).contains(InstructionComposer.PROJECT_NOTE_ID)) {
            return storageService.readNoteOrEmpty(projectId, InstructionComposer.PROJECT_NOTE_ID);
        }
        String projectNotes = storageService.readNoteOrEmpty(projectId, InstructionComposer.PROJECT_NOTE_ID);
        if (!projectNotes.isBlank()) {
            return projectNotes;
        }

        List<String> blocks = new ArrayList<>();
        addBlock(blocks, "## Pre-stream planning", storageService.readNoteOrEmpty(projectId, InstructionComposer.PRE_STREAM_NOTE_ID));

        String description = storageService.readNoteOrEmpty(projectId, InstructionComposer.DESCRIPTION_NOTE_ID);
        String thumbnail = storageService.readNoteOrEmpty(projectId, InstructionComposer.THUMBNAIL_NOTE_ID);
        String social = storageService.readNoteOrEmpty(projectId, InstructionComposer.SOCIAL_ANNOUNCEMENTS_NOTE_ID);
        String legacyPromotion = storageService.readNoteOrEmpty(projectId, InstructionComposer.LEGACY_PROMOTION_NOTE_ID);
        if (description.isBlank() && thumbnail.isBlank() && social.isBlank() && !legacyPromotion.isBlank()) {
            addBlock(blocks, "## Promotion", legacyPromotion);
        } else {
            addBlock(blocks, "## Description", description);
            addBlock(blocks, "## Thumbnail", thumbnail);
            addBlock(blocks, "## Social media", social);
        }

        addBlock(blocks, "## Transcription", storageService.readNoteOrEmpty(projectId, InstructionComposer.TRANSCRIPTION_NOTE_ID));
        addBlock(blocks, "## Post-stream wrap-up", storageService.readNoteOrEmpty(projectId, InstructionComposer.POST_STREAM_NOTE_ID));
        return String.join("\n\n", blocks);
    }

    private void addBlock(List<String> blocks, String heading, String content) {
        if (content != null && !content.isBlank()) {
            blocks.add(heading + "\n\n" + content.trim());
        }
    }
}
