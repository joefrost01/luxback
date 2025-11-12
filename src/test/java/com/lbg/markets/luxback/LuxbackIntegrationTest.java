package com.lbg.markets.luxback;

import com.lbg.markets.luxback.config.LuxBackConfig;
import com.lbg.markets.luxback.service.AuditService;
import com.lbg.markets.luxback.service.StorageService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * End-to-end integration tests for LuxBack application.
 * Tests full user journeys across multiple controllers.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev-local")
@Import({com.lbg.markets.luxback.config.DevSecurityConfig.class})
class LuxbackIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StorageService storageService;

    @Autowired
    private LuxBackConfig config;

    @BeforeEach
    void setUp() throws IOException {
        // Ensure directories exist before each test
        Path storagePath = Paths.get(config.getStoragePath());
        Path auditPath = Paths.get(config.getAuditIndexPath());

        Files.createDirectories(storagePath);
        Files.createDirectories(auditPath);
    }

    @AfterEach
    void tearDown() throws IOException {
        // Optional: Clean up after each test for isolation
        Path storagePath = Paths.get(config.getStoragePath());
        Path auditPath = Paths.get(config.getAuditIndexPath());

        deleteDirectoryRecursively(storagePath);
        deleteDirectoryRecursively(auditPath);
    }

    private void deleteDirectoryRecursively(Path path) throws IOException {
        if (Files.exists(path)) {
            Files.walk(path)
                    .sorted(Comparator.reverseOrder())
                    .forEach(p -> {
                        try {
                            Files.delete(p);
                        } catch (IOException e) {
                            // Log but don't fail
                        }
                    });
        }
    }

    @Test
    @WithMockUser(username = "testuser", roles = "USER")
    void fullUploadFlow_asUser() throws Exception {
        // Arrange
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test-document.pdf",
                "application/pdf",
                "Test PDF content for integration test".getBytes()
        );

        // Act & Assert - Upload file
        mockMvc.perform(multipart("/upload")
                        .file(file)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.message").value("File uploaded successfully: test-document.pdf"));

        // Verify file was stored
        String auditCsv = storageService.readString(config.getAuditIndexPath()+"/testuser.csv");
        assertThat(auditCsv).contains("UPLOAD");
        assertThat(auditCsv).contains("test-document.pdf");
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void fullAdminFlow_uploadSearchDownload() throws Exception {
        // Step 1: Upload a file as admin
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "admin-report.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "Excel content for integration test".getBytes()
        );

        mockMvc.perform(multipart("/upload")
                        .file(file)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"));

        // Step 2: Search for the uploaded file
        mockMvc.perform(get("/files")
                        .param("filename", "admin-report"))
                .andExpect(status().isOk())
                .andExpect(view().name("file-listing"))
                .andExpect(content().string(containsString("admin-report.xlsx")));

        // Step 3: Get the stored filename from audit
        String auditCsv = storageService.readString(config.getAuditIndexPath()+"/admin.csv");
        String[] lines = auditCsv.split("\n");
        // Last line should be the upload event
        String lastLine = lines[lines.length - 1];
        String[] fields = lastLine.split(",");
        String storedFilename = fields[5]; // stored_as column

        // Step 4: Download the file
        mockMvc.perform(get("/download/admin/" + storedFilename))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition",
                        containsString("admin-report.xlsx")))
                .andExpect(content().bytes("Excel content for integration test".getBytes()));

        // Verify download was audited
        auditCsv = storageService.readString(config.getAuditIndexPath()+"/admin.csv");
        assertThat(auditCsv).contains("DOWNLOAD");
    }

    @Test
    @WithMockUser(username = "user1", roles = "USER")
    void multiUserScenario_separateFiles() throws Exception {
        // Upload as user1
        mockMvc.perform(multipart("/upload")
                        .file(new MockMultipartFile(
                                "file",
                                "user1-doc.pdf",
                                "application/pdf",
                                "User1 content".getBytes()
                        ))
                        .with(csrf())
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("user1").roles("USER")))
                .andExpect(status().isOk());

        // Upload as user2
        mockMvc.perform(multipart("/upload")
                        .file(new MockMultipartFile(
                                "file",
                                "user2-doc.pdf",
                                "application/pdf",
                                "User2 content".getBytes()
                        ))
                        .with(csrf())
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("user2").roles("USER")))
                .andExpect(status().isOk());

        // Verify separate audit files were created
        assertThat(storageService.exists(config.getAuditIndexPath()+"/user1.csv")).isTrue();
        assertThat(storageService.exists(config.getAuditIndexPath()+"/user2.csv")).isTrue();

        String user1Audit = storageService.readString(config.getAuditIndexPath()+"/user1.csv");
        String user2Audit = storageService.readString(config.getAuditIndexPath()+"/user2.csv");

        assertThat(user1Audit).contains("user1-doc.pdf");
        assertThat(user1Audit).doesNotContain("user2-doc.pdf");

        assertThat(user2Audit).contains("user2-doc.pdf");
        assertThat(user2Audit).doesNotContain("user1-doc.pdf");
    }

    @Test
    @WithMockUser(username = "user", roles = "USER")
    void userCannotAccessAdminEndpoints() throws Exception {
        // Upload a file first
        mockMvc.perform(multipart("/upload")
                        .file(new MockMultipartFile(
                                "file",
                                "doc.pdf",
                                "application/pdf",
                                "Content".getBytes()
                        ))
                        .with(csrf()))
                .andExpect(status().isOk());

        // Try to access file listing - should be forbidden
        mockMvc.perform(get("/files"))
                .andExpect(status().isForbidden());

        // Try to download own file - should be forbidden
        mockMvc.perform(get("/download/user/2024-11-09T14-30-00_doc.pdf"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void adminCanSearchAcrossAllUsers() throws Exception {
        // Upload files as different users
        mockMvc.perform(multipart("/upload")
                        .file(new MockMultipartFile("file", "alice-file.pdf",
                                "application/pdf", "Alice content".getBytes()))
                        .with(csrf())
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("alice").roles("USER")))
                .andExpect(status().isOk());

        mockMvc.perform(multipart("/upload")
                        .file(new MockMultipartFile("file", "bob-file.pdf",
                                "application/pdf", "Bob content".getBytes()))
                        .with(csrf())
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("bob").roles("USER")))
                .andExpect(status().isOk());

        // Admin searches for all files
        mockMvc.perform(get("/files"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("alice-file.pdf")))
                .andExpect(content().string(containsString("bob-file.pdf")));
    }

    @Test
    @WithMockUser(username = "user", roles = "USER")
    void uploadValidation_fileSize() throws Exception {
        // Try to upload file that's too large
        byte[] largeContent = new byte[101 * 1024 * 1024]; // 101MB

        mockMvc.perform(multipart("/upload")
                        .file(new MockMultipartFile(
                                "file",
                                "large.pdf",
                                "application/pdf",
                                largeContent
                        ))
                        .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.message").value(containsString("exceeds maximum size")));
    }

    @Test
    @WithMockUser(username = "user", roles = "USER")
    void uploadValidation_fileType() throws Exception {
        // Try to upload disallowed file type
        mockMvc.perform(multipart("/upload")
                        .file(new MockMultipartFile(
                                "file",
                                "script.exe",
                                "application/x-executable",
                                "Executable content".getBytes()
                        ))
                        .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.message").value(containsString("not allowed")));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void searchWithPagination() throws Exception {
        // Upload multiple files
        for (int i = 0; i < 75; i++) {
            mockMvc.perform(multipart("/upload")
                            .file(new MockMultipartFile(
                                    "file",
                                    "file" + i + ".pdf",
                                    "application/pdf",
                                    ("Content " + i).getBytes()
                            ))
                            .with(csrf()))
                    .andExpect(status().isOk());
        }

        // Check first page - note: dots are replaced with underscores by sanitization
        mockMvc.perform(get("/files")
                        .param("page", "0"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("file70.pdf")));

        // Check second page
        mockMvc.perform(get("/files")
                        .param("page", "1"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("file60.pdf")));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void auditTrail_recordsAllEvents() throws Exception {
        // Upload a file
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "audit-test.pdf",
                "application/pdf",
                "Audit test content".getBytes()
        );

        mockMvc.perform(multipart("/upload")
                        .file(file)
                        .with(csrf()))
                .andExpect(status().isOk());

        // Get stored filename from audit
        String auditCsv = storageService.readString(config.getAuditIndexPath()+"/admin.csv");
        String[] lines = auditCsv.split("\n");
        String lastLine = lines[lines.length - 1];
        String[] fields = lastLine.split(",");
        String storedFilename = fields[5];

        // Download the file
        mockMvc.perform(get("/download/admin/" + storedFilename))
                .andExpect(status().isOk());

        // Verify both events are in audit
        auditCsv = storageService.readString(config.getAuditIndexPath()+"/admin.csv");
        assertThat(auditCsv.split("\n")).hasSizeGreaterThan(2); // Header + UPLOAD + DOWNLOAD
        assertThat(auditCsv).contains("UPLOAD");
        assertThat(auditCsv).contains("DOWNLOAD");
    }

    @Test
    void unauthenticatedAccess_shouldRedirectToLogin() throws Exception {
        // Try to access upload page without authentication
        mockMvc.perform(get("/"))
                .andExpect(status().isUnauthorized());

        // Try to upload without authentication
        mockMvc.perform(multipart("/upload")
                        .file(new MockMultipartFile(
                                "file",
                                "doc.pdf",
                                "application/pdf",
                                "Content".getBytes()
                        ))
                        .with(csrf()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "user", roles = "USER")
    void csrfProtection_shouldBlockRequestsWithoutToken() throws Exception {
        // Try to upload without CSRF token
        mockMvc.perform(multipart("/upload")
                        .file(new MockMultipartFile(
                                "file",
                                "doc.pdf",
                                "application/pdf",
                                "Content".getBytes()
                        )))
                .andExpect(status().isForbidden());
    }
}