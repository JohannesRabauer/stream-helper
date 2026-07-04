package com.streamhelper.app.web;

import com.streamhelper.app.project.ProjectStorageService;
import com.streamhelper.app.service.InstructionComposer;
import java.util.Map;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class PageController {

    private final ProjectStorageService storageService;

    public PageController(ProjectStorageService storageService) {
        this.storageService = storageService;
    }

    @GetMapping("/")
    public String index() {
        return "redirect:/projects";
    }

    @GetMapping("/projects")
    public String projects(Model model) {
        model.addAttribute("projects", storageService.listProjects());
        return "projects";
    }

    @PostMapping("/projects")
    public String createProject(@RequestParam("name") String name, Model model) {
        if (name == null || name.isBlank()) {
            model.addAttribute("projects", storageService.listProjects());
            model.addAttribute("error", "Project name is required");
            return "projects";
        }
        var project = storageService.createProject(name);
        return "redirect:/projects/%s".formatted(project.id());
    }

    @GetMapping("/projects/{projectId}")
    public String project(@PathVariable String projectId, @RequestParam(value = "noteId", required = false) String noteId, Model model) {
        var project = storageService.getProject(projectId);
        model.addAttribute("project", project);
        model.addAttribute("projectConfig", storageService.readProjectConfig(projectId));
        model.addAttribute("globalConfig", storageService.readGlobalConfig());
        model.addAttribute(
                "workflowNotes",
                Map.of(
                        "pre-stream",
                        storageService.readNoteOrEmpty(projectId, InstructionComposer.PRE_STREAM_NOTE_ID),
                        "description",
                        readWorkflowNote(projectId, InstructionComposer.DESCRIPTION_NOTE_ID),
                        "thumbnail",
                        readWorkflowNote(projectId, InstructionComposer.THUMBNAIL_NOTE_ID),
                        "social-announcements",
                        readWorkflowNote(projectId, InstructionComposer.SOCIAL_ANNOUNCEMENTS_NOTE_ID),
                        "post-stream",
                        storageService.readNoteOrEmpty(projectId, InstructionComposer.POST_STREAM_NOTE_ID),
                        "transcription",
                        storageService.readNoteOrEmpty(projectId, InstructionComposer.TRANSCRIPTION_NOTE_ID)));
        return "project";
    }

    @PostMapping("/projects/{projectId}/notes")
    public String saveNote(
            @PathVariable String projectId,
            @RequestParam(value = "noteId", required = false) String noteId,
            @RequestParam("markdown") String markdown) {
        String effectiveNoteId = storageService.saveNote(projectId, noteId, markdown);
        return "redirect:/projects/%s?noteId=%s".formatted(projectId, effectiveNoteId);
    }

    private String readWorkflowNote(String projectId, String noteId) {
        String note = storageService.readNoteOrEmpty(projectId, noteId);
        if (!note.isBlank()) {
            return note;
        }
        return storageService.readNoteOrEmpty(projectId, InstructionComposer.LEGACY_PROMOTION_NOTE_ID);
    }
}
