package com.streamhelper.app.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.streamhelper.app.project.ProjectStorageService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class PageControllerIntegrationTest {

    private static final Path DATA_DIR = createTempDir();

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("stream-helper.storage.data-dir", DATA_DIR::toString);
    }

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ProjectStorageService storageService;

    @Test
    void projectsPageLoads() throws Exception {
        mockMvc.perform(get("/projects"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Stream Helper")));
    }

    @Test
    void projectPageShowsProjectNotesDrawer() throws Exception {
        var project = storageService.createProject("Notes Sidebar");

        mockMvc.perform(get("/projects/" + project.id()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Project notes")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Show notes")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Markdown notes")));
    }

    private static Path createTempDir() {
        try {
            return Files.createTempDirectory("stream-helper-page-test-");
        } catch (IOException exception) {
            throw new RuntimeException(exception);
        }
    }
}
