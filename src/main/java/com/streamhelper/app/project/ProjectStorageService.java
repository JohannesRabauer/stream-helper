package com.streamhelper.app.project;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.streamhelper.app.config.StreamHelperProperties;
import com.streamhelper.app.model.ArtifactVersion;
import com.streamhelper.app.model.GenerationCategory;
import com.streamhelper.app.model.GlobalConfig;
import com.streamhelper.app.model.ProjectConfig;
import com.streamhelper.app.model.ProjectMetadata;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;

@Service
public class ProjectStorageService {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final Path dataDir;
    private final Path projectsDir;
    private final Path globalDir;
    private final ObjectMapper objectMapper;

    public ProjectStorageService(StreamHelperProperties properties, ObjectMapper objectMapper) {
        this.dataDir = properties.getStorage().getDataDir().toAbsolutePath().normalize();
        this.projectsDir = dataDir.resolve("projects");
        this.globalDir = dataDir.resolve("global");
        this.objectMapper = objectMapper;
        init();
    }

    private void init() {
        try {
            Files.createDirectories(projectsDir);
            Files.createDirectories(globalDir);
            if (Files.notExists(globalConfigFile())) {
                saveGlobalConfig(new GlobalConfig());
            }
        } catch (IOException exception) {
            throw new StorageException("Failed to initialize storage directories", exception);
        }
    }

    public Path getProjectDir(String projectId) {
        return projectsDir.resolve(projectId);
    }

    public Path getDataDir() {
        return dataDir;
    }

    public List<ProjectMetadata> listProjects() {
        try (Stream<Path> stream = Files.list(projectsDir)) {
            return stream.filter(Files::isDirectory)
                    .map(this::readProjectMetadataUnchecked)
                    .sorted(Comparator.comparing(ProjectMetadata::updatedAt).reversed())
                    .toList();
        } catch (IOException exception) {
            throw new StorageException("Failed to list projects", exception);
        }
    }

    public ProjectMetadata createProject(String name) {
        String normalized = normalizeName(name);
        String id = "%s-%s".formatted(normalized, UUID.randomUUID().toString().substring(0, 8));
        Path projectDir = getProjectDir(id);
        try {
            Files.createDirectories(projectDir.resolve("notes"));
            Files.createDirectories(projectDir.resolve("sessions"));
            Files.createDirectories(projectDir.resolve("outputs"));
            Files.createDirectories(projectDir.resolve("transcripts"));
            Files.createDirectories(projectDir.resolve("thumbnails"));
            Files.createDirectories(projectDir.resolve("config"));

            OffsetDateTime now = OffsetDateTime.now();
            ProjectMetadata metadata = new ProjectMetadata(id, name.trim(), "1", now, now);
            objectMapper.writeValue(projectFile(id).toFile(), metadata);
            saveProjectConfig(id, new ProjectConfig());
            return metadata;
        } catch (IOException exception) {
            throw new StorageException("Failed to create project %s".formatted(name), exception);
        }
    }

    public ProjectMetadata getProject(String projectId) {
        Path file = projectFile(projectId);
        if (Files.notExists(file)) {
            throw new NotFoundException("Project %s not found".formatted(projectId));
        }
        try {
            return objectMapper.readValue(file.toFile(), ProjectMetadata.class);
        } catch (IOException exception) {
            throw new StorageException("Failed to read project %s".formatted(projectId), exception);
        }
    }

    public ProjectMetadata renameProject(String projectId, String newName) {
        ProjectMetadata existing = getProject(projectId);
        ProjectMetadata updated =
                new ProjectMetadata(
                        existing.id(),
                        newName.trim(),
                        existing.schemaVersion(),
                        existing.createdAt(),
                        OffsetDateTime.now());
        writeProjectMetadata(updated);
        return updated;
    }

    public void deleteProject(String projectId) {
        Path projectDir = getProjectDir(projectId);
        if (Files.notExists(projectDir)) {
            throw new NotFoundException("Project %s not found".formatted(projectId));
        }
        try (Stream<Path> stream = Files.walk(projectDir)) {
            stream.sorted(Comparator.reverseOrder()).forEach(this::deletePath);
        } catch (IOException exception) {
            throw new StorageException("Failed to delete project %s".formatted(projectId), exception);
        }
    }

    private void deletePath(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException exception) {
            throw new StorageException("Failed to delete %s".formatted(path), exception);
        }
    }

    public List<String> listNoteIds(String projectId) {
        Path notesDir = getProjectDir(projectId).resolve("notes");
        ensureProjectExists(projectId);
        try (Stream<Path> stream = Files.list(notesDir)) {
            return stream.filter(path -> path.getFileName().toString().endsWith(".md"))
                    .map(path -> path.getFileName().toString().replace(".md", ""))
                    .sorted()
                    .toList();
        } catch (IOException exception) {
            throw new StorageException("Failed to list notes for %s".formatted(projectId), exception);
        }
    }

    public String saveNote(String projectId, String noteId, String markdown) {
        ensureProjectExists(projectId);
        String effectiveNoteId = (noteId == null || noteId.isBlank())
                ? "note-%s".formatted(UUID.randomUUID().toString().substring(0, 8))
                : normalizeName(noteId);
        Path file = getProjectDir(projectId).resolve("notes").resolve("%s.md".formatted(effectiveNoteId));
        try {
            Files.writeString(
                    file,
                    markdown == null ? "" : markdown,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING);
            touchProject(projectId);
            return effectiveNoteId;
        } catch (IOException exception) {
            throw new StorageException("Failed to save note %s".formatted(effectiveNoteId), exception);
        }
    }

    public String readNote(String projectId, String noteId) {
        ensureProjectExists(projectId);
        Path file = getProjectDir(projectId).resolve("notes").resolve("%s.md".formatted(noteId));
        if (Files.notExists(file)) {
            throw new NotFoundException("Note %s not found".formatted(noteId));
        }
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new StorageException("Failed to read note %s".formatted(noteId), exception);
        }
    }

    public void deleteNote(String projectId, String noteId) {
        ensureProjectExists(projectId);
        Path file = getProjectDir(projectId).resolve("notes").resolve("%s.md".formatted(noteId));
        deletePath(file);
    }

    public ProjectConfig readProjectConfig(String projectId) {
        ensureProjectExists(projectId);
        Path file = projectConfigFile(projectId);
        if (Files.notExists(file)) {
            ProjectConfig config = new ProjectConfig();
            saveProjectConfig(projectId, config);
            return config;
        }
        try {
            return objectMapper.readValue(file.toFile(), ProjectConfig.class);
        } catch (IOException exception) {
            throw new StorageException("Failed to read project config for %s".formatted(projectId), exception);
        }
    }

    public ProjectConfig saveProjectConfig(String projectId, ProjectConfig config) {
        ensureProjectExists(projectId);
        try {
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(projectConfigFile(projectId).toFile(), config);
            touchProject(projectId);
            return config;
        } catch (IOException exception) {
            throw new StorageException("Failed to save project config for %s".formatted(projectId), exception);
        }
    }

    public GlobalConfig readGlobalConfig() {
        if (Files.notExists(globalConfigFile())) {
            GlobalConfig config = new GlobalConfig();
            saveGlobalConfig(config);
            return config;
        }
        try {
            return objectMapper.readValue(globalConfigFile().toFile(), GlobalConfig.class);
        } catch (IOException exception) {
            throw new StorageException("Failed to read global config", exception);
        }
    }

    public GlobalConfig saveGlobalConfig(GlobalConfig globalConfig) {
        try {
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(globalConfigFile().toFile(), globalConfig);
            return globalConfig;
        } catch (IOException exception) {
            throw new StorageException("Failed to save global config", exception);
        }
    }

    public ArtifactVersion saveArtifact(
            String projectId,
            GenerationCategory category,
            String strategy,
            String content,
            boolean recommended,
            boolean finalVersion) {
        ensureProjectExists(projectId);
        String artifactId = UUID.randomUUID().toString();
        ArtifactVersion version = ArtifactVersion.create(
                artifactId, category, strategy, content, recommended, finalVersion, OffsetDateTime.now());
        Path categoryDir = categoryDir(projectId, category);
        try {
            Files.createDirectories(categoryDir);
            objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValue(categoryDir.resolve("%s.json".formatted(artifactId)).toFile(), version);
            if (finalVersion) {
                Files.writeString(
                        categoryDir.resolve("final.txt"),
                        artifactId,
                        StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.TRUNCATE_EXISTING);
            }
            touchProject(projectId);
            return version;
        } catch (IOException exception) {
            throw new StorageException("Failed to save artifact for %s".formatted(projectId), exception);
        }
    }

    public List<ArtifactVersion> listArtifacts(String projectId, GenerationCategory category) {
        ensureProjectExists(projectId);
        Path categoryDir = categoryDir(projectId, category);
        if (Files.notExists(categoryDir)) {
            return List.of();
        }
        try (Stream<Path> stream = Files.list(categoryDir)) {
            List<ArtifactVersion> versions = new ArrayList<>();
            stream.filter(path -> path.getFileName().toString().endsWith(".json")).forEach(path -> {
                try {
                    versions.add(objectMapper.readValue(path.toFile(), ArtifactVersion.class));
                } catch (IOException exception) {
                    throw new StorageException("Failed to read artifact %s".formatted(path), exception);
                }
            });
            return versions.stream().sorted(Comparator.comparing(ArtifactVersion::getCreatedAt).reversed()).toList();
        } catch (IOException exception) {
            throw new StorageException("Failed to list artifacts for %s".formatted(projectId), exception);
        }
    }

    public ArtifactVersion markFinal(String projectId, GenerationCategory category, String artifactId) {
        ensureProjectExists(projectId);
        Path categoryDir = categoryDir(projectId, category);
        Path targetFile = categoryDir.resolve("%s.json".formatted(artifactId));
        if (Files.notExists(targetFile)) {
            throw new NotFoundException("Artifact %s not found".formatted(artifactId));
        }
        try {
            List<ArtifactVersion> versions = listArtifacts(projectId, category);
            for (ArtifactVersion version : versions) {
                version.setFinalVersion(version.getId().equals(artifactId));
                objectMapper.writerWithDefaultPrettyPrinter()
                        .writeValue(categoryDir.resolve("%s.json".formatted(version.getId())).toFile(), version);
            }
            Files.writeString(
                    categoryDir.resolve("final.txt"),
                    artifactId,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING);
            touchProject(projectId);
            return objectMapper.readValue(targetFile.toFile(), ArtifactVersion.class);
        } catch (IOException exception) {
            throw new StorageException("Failed to finalize artifact %s".formatted(artifactId), exception);
        }
    }

    public Optional<ArtifactVersion> getFinalArtifact(String projectId, GenerationCategory category) {
        ensureProjectExists(projectId);
        Path finalRef = categoryDir(projectId, category).resolve("final.txt");
        if (Files.notExists(finalRef)) {
            return Optional.empty();
        }
        try {
            String artifactId = Files.readString(finalRef, StandardCharsets.UTF_8).trim();
            Path file = categoryDir(projectId, category).resolve("%s.json".formatted(artifactId));
            if (Files.notExists(file)) {
                return Optional.empty();
            }
            return Optional.of(objectMapper.readValue(file.toFile(), ArtifactVersion.class));
        } catch (IOException exception) {
            throw new StorageException("Failed to read final artifact", exception);
        }
    }

    public Path writeBinaryAsset(String projectId, GenerationCategory category, String extension, InputStream content) {
        ensureProjectExists(projectId);
        String artifactId = UUID.randomUUID().toString();
        Path categoryDir = categoryDir(projectId, category);
        try {
            Files.createDirectories(categoryDir);
            Path target = categoryDir.resolve("%s.%s".formatted(artifactId, extension));
            Files.copy(content, target);
            touchProject(projectId);
            return target;
        } catch (IOException exception) {
            throw new StorageException("Failed to save binary asset", exception);
        }
    }

    public Map<String, Object> buildProjectManifest(String projectId) {
        ProjectMetadata metadata = getProject(projectId);
        ProjectConfig config = readProjectConfig(projectId);
        return Map.of(
                "schemaVersion",
                metadata.schemaVersion(),
                "projectId",
                metadata.id(),
                "name",
                metadata.name(),
                "createdAt",
                metadata.createdAt().toString(),
                "updatedAt",
                metadata.updatedAt().toString(),
                "defaultLanguage",
                config.getDefaultLanguage(),
                "categories",
                Stream.of(GenerationCategory.values()).map(Enum::name).toList());
    }

    public Map<String, Object> readJson(Path path) {
        try {
            return objectMapper.readValue(path.toFile(), MAP_TYPE);
        } catch (IOException exception) {
            throw new StorageException("Failed to parse JSON from %s".formatted(path), exception);
        }
    }

    private Path categoryDir(String projectId, GenerationCategory category) {
        return getProjectDir(projectId).resolve("outputs").resolve(category.name().toLowerCase(Locale.ROOT));
    }

    private Path globalConfigFile() {
        return globalDir.resolve("global-config.json");
    }

    private Path projectFile(String projectId) {
        return getProjectDir(projectId).resolve("project.json");
    }

    private Path projectConfigFile(String projectId) {
        return getProjectDir(projectId).resolve("config").resolve("project-config.json");
    }

    private void ensureProjectExists(String projectId) {
        if (Files.notExists(getProjectDir(projectId))) {
            throw new NotFoundException("Project %s not found".formatted(projectId));
        }
    }

    private void touchProject(String projectId) {
        ProjectMetadata metadata = getProject(projectId);
        writeProjectMetadata(
                new ProjectMetadata(
                        metadata.id(), metadata.name(), metadata.schemaVersion(), metadata.createdAt(), OffsetDateTime.now()));
    }

    private void writeProjectMetadata(ProjectMetadata metadata) {
        try {
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(projectFile(metadata.id()).toFile(), metadata);
        } catch (IOException exception) {
            throw new StorageException("Failed to write project metadata", exception);
        }
    }

    private ProjectMetadata readProjectMetadataUnchecked(Path projectDir) {
        try {
            return objectMapper.readValue(projectDir.resolve("project.json").toFile(), ProjectMetadata.class);
        } catch (IOException exception) {
            throw new StorageException("Failed to read project in %s".formatted(projectDir), exception);
        }
    }

    private String normalizeName(String name) {
        String normalized = name.trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-+|-+$)", "");
        return normalized.isBlank() ? "project" : normalized;
    }
}
