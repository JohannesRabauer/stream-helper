package com.streamhelper.app.web;

import com.streamhelper.app.model.GenerationCategory;
import com.streamhelper.app.model.GlobalConfig;
import com.streamhelper.app.model.ProjectConfig;
import com.streamhelper.app.project.ProjectExportService;
import com.streamhelper.app.project.ProjectStorageService;
import com.streamhelper.app.service.MarkdownService;
import com.streamhelper.app.web.dto.CreateProjectRequest;
import com.streamhelper.app.web.dto.NoteRequest;
import com.streamhelper.app.web.dto.RenameProjectRequest;
import jakarta.validation.Valid;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Map;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api")
public class ProjectApiController {

    private final ProjectStorageService storageService;
    private final ProjectExportService exportService;
    private final MarkdownService markdownService;

    public ProjectApiController(
            ProjectStorageService storageService, ProjectExportService exportService, MarkdownService markdownService) {
        this.storageService = storageService;
        this.exportService = exportService;
        this.markdownService = markdownService;
    }

    @GetMapping("/projects")
    public Object listProjects() {
        return storageService.listProjects();
    }

    @PostMapping("/projects")
    public Object createProject(@Valid @RequestBody CreateProjectRequest request) {
        return storageService.createProject(request.name());
    }

    @GetMapping("/projects/{projectId}")
    public Object getProject(@PathVariable String projectId) {
        return Map.of(
                "project",
                storageService.getProject(projectId),
                "config",
                storageService.readProjectConfig(projectId),
                "notes",
                storageService.listNoteIds(projectId));
    }

    @PutMapping("/projects/{projectId}")
    public Object renameProject(@PathVariable String projectId, @Valid @RequestBody RenameProjectRequest request) {
        return storageService.renameProject(projectId, request.name());
    }

    @DeleteMapping("/projects/{projectId}")
    public void deleteProject(@PathVariable String projectId) {
        storageService.deleteProject(projectId);
    }

    @GetMapping("/projects/{projectId}/config")
    public ProjectConfig getProjectConfig(@PathVariable String projectId) {
        return storageService.readProjectConfig(projectId);
    }

    @PutMapping("/projects/{projectId}/config")
    public ProjectConfig saveProjectConfig(@PathVariable String projectId, @RequestBody ProjectConfig config) {
        return storageService.saveProjectConfig(projectId, config);
    }

    @GetMapping("/config/global")
    public GlobalConfig getGlobalConfig() {
        return storageService.readGlobalConfig();
    }

    @PutMapping("/config/global")
    public GlobalConfig saveGlobalConfig(@RequestBody GlobalConfig config) {
        return storageService.saveGlobalConfig(config);
    }

    @GetMapping("/projects/{projectId}/notes")
    public Object listNotes(@PathVariable String projectId) {
        return storageService.listNoteIds(projectId);
    }

    @GetMapping("/projects/{projectId}/notes/{noteId}")
    public Object readNote(@PathVariable String projectId, @PathVariable String noteId) {
        String markdown = storageService.readNote(projectId, noteId);
        return Map.of("noteId", noteId, "markdown", markdown, "html", markdownService.render(markdown));
    }

    @PostMapping("/projects/{projectId}/notes")
    public Object saveNote(@PathVariable String projectId, @Valid @RequestBody NoteRequest request) {
        String noteId = storageService.saveNote(projectId, request.noteId(), request.markdown());
        return readNote(projectId, noteId);
    }

    @DeleteMapping("/projects/{projectId}/notes/{noteId}")
    public void deleteNote(@PathVariable String projectId, @PathVariable String noteId) {
        storageService.deleteNote(projectId, noteId);
    }

    @GetMapping("/projects/{projectId}/artifacts/{category}")
    public Object listArtifacts(@PathVariable String projectId, @PathVariable GenerationCategory category) {
        return storageService.listArtifacts(projectId, category);
    }

    @GetMapping("/projects/{projectId}/export")
    public ResponseEntity<Resource> exportProject(@PathVariable String projectId) throws IOException {
        var zip = exportService.exportProjectZip(projectId);
        Resource resource = new FileSystemResource(zip);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(Files.size(zip))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"%s\"".formatted(zip.getFileName()))
                .body(resource);
    }
}
