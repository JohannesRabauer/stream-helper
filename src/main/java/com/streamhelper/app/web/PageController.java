package com.streamhelper.app.web;

import com.streamhelper.app.project.ProjectStorageService;
import com.streamhelper.app.service.MarkdownService;
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
        var notes = storageService.listNoteIds(projectId);
        String selectedNote = noteId == null && !notes.isEmpty() ? notes.get(0) : noteId;
        String markdown = selectedNote == null ? "" : storageService.readNote(projectId, selectedNote);
        model.addAttribute("project", project);
        model.addAttribute("notes", notes);
        model.addAttribute("selectedNoteId", selectedNote);
        model.addAttribute("markdown", markdown);
        model.addAttribute("renderedMarkdown", markdownService.render(markdown));
        model.addAttribute("projectConfig", storageService.readProjectConfig(projectId));
        model.addAttribute("globalConfig", storageService.readGlobalConfig());
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
}
