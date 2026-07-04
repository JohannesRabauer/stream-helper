package com.streamhelper.app.project;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.springframework.stereotype.Service;

@Service
public class ProjectExportService {

    private final ProjectStorageService storageService;
    private final ObjectMapper objectMapper;

    public ProjectExportService(ProjectStorageService storageService, ObjectMapper objectMapper) {
        this.storageService = storageService;
        this.objectMapper = objectMapper;
    }

    public Path exportProjectZip(String projectId) {
        Path projectDir = storageService.getProjectDir(projectId);
        if (Files.notExists(projectDir)) {
            throw new NotFoundException("Project %s not found".formatted(projectId));
        }
        Path exportsDir = projectDir.resolve("exports");
        try {
            Files.createDirectories(exportsDir);
            Path zipPath = exportsDir.resolve("export-%d.zip".formatted(System.currentTimeMillis()));
            try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(zipPath))) {
                Path manifestTemp = Files.createTempFile("manifest-", ".json");
                objectMapper.writerWithDefaultPrettyPrinter()
                        .writeValue(manifestTemp.toFile(), storageService.buildProjectManifest(projectId));
                addFileToZip(zip, manifestTemp, Path.of("manifest.json"));
                try (var walk = Files.walk(projectDir)) {
                    walk.filter(Files::isRegularFile)
                            .filter(path -> !path.startsWith(exportsDir))
                            .sorted(Comparator.naturalOrder())
                            .forEach(path -> addFileToZip(zip, path, projectDir.relativize(path)));
                }
                Files.deleteIfExists(manifestTemp);
            }
            return zipPath;
        } catch (IOException exception) {
            throw new StorageException("Failed to export project %s".formatted(projectId), exception);
        }
    }

    private void addFileToZip(ZipOutputStream zip, Path source, Path pathInZip) {
        try {
            ZipEntry entry = new ZipEntry(pathInZip.toString().replace("\\", "/"));
            zip.putNextEntry(entry);
            Files.copy(source, zip);
            zip.closeEntry();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }
}
