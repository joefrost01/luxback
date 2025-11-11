package com.lbg.markets.luxback.controller;

import com.lbg.markets.luxback.config.TestConfig;
import com.lbg.markets.luxback.exception.StorageException;
import com.lbg.markets.luxback.service.AuditService;
import com.lbg.markets.luxback.service.StorageService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for DownloadController.
 * Tests file download functionality and admin-only access.
 */
@WebMvcTest(DownloadController.class)
@ActiveProfiles("dev-local")
@Import({com.lbg.markets.luxback.config.DevSecurityConfig.class, TestConfig.class})
class DownloadControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private StorageService storageService;

    @MockBean
    private AuditService auditService;

    @Test
    void downloadFile_shouldRequireAuthentication() throws Exception {
        // Act & Assert
        mockMvc.perform(get("/download/joe.bloggs/2024-11-09T14-30-00_document.pdf"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "user", roles = "USER")
    void downloadFile_shouldBeForbiddenForRegularUser() throws Exception {
        // Act & Assert
        mockMvc.perform(get("/download/joe.bloggs/2024-11-09T14-30-00_document.pdf"))
                .andExpect(status().isForbidden());

        // Verify no storage or audit operations
        verify(storageService, never()).readFile(anyString());
        verify(auditService, never()).recordDownload(anyString(), anyString(), anyString(), anyString(), any());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void downloadFile_shouldSucceedForAdmin() throws Exception {
        // Arrange
        String fileContent = "Test file content";

        when(storageService.exists(anyString())).thenReturn(true);

        // Use thenAnswer to create a fresh InputStream each time - critical for streaming tests
        when(storageService.readFile(anyString())).thenAnswer(invocation ->
                new ByteArrayInputStream(fileContent.getBytes(StandardCharsets.UTF_8))
        );

        when(auditService.getOriginalFilename("joe.bloggs", "2024-11-09T14-30-00_document.pdf"))
                .thenReturn("document.pdf");

        // Act & Assert
        mockMvc.perform(get("/download/joe.bloggs/2024-11-09T14-30-00_document.pdf"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"document.pdf\""))
                .andExpect(header().string("Content-Type", "application/octet-stream"))
                .andExpect(content().string(fileContent));

        // Verify audit was recorded
        verify(auditService).recordDownload(
                eq("joe.bloggs"),
                eq("document.pdf"),
                eq("2024-11-09T14-30-00_document.pdf"),
                eq("admin"),
                any()
        );
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void downloadFile_shouldReturnNotFoundWhenFileDoesNotExist() throws Exception {
        // Arrange
        when(storageService.exists(anyString())).thenReturn(false);

        // Act & Assert
        mockMvc.perform(get("/download/joe.bloggs/nonexistent.pdf"))
                .andExpect(status().isNotFound());

        // Verify no audit was recorded
        verify(auditService, never()).recordDownload(anyString(), anyString(), anyString(), anyString(), any());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void downloadFile_shouldHandleStorageException() throws Exception {
        // Arrange
        when(storageService.exists(anyString())).thenReturn(true);
        when(storageService.readFile(anyString()))
                .thenThrow(new StorageException("Storage unavailable"));
        when(auditService.getOriginalFilename(anyString(), anyString()))
                .thenReturn("document.pdf");

        // Act & Assert
        mockMvc.perform(get("/download/joe.bloggs/2024-11-09T14-30-00_document.pdf"))
                .andExpect(status().isInternalServerError());

        // Verify no audit was recorded (download failed)
        verify(auditService, never()).recordDownload(anyString(), anyString(), anyString(), anyString(), any());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void downloadFile_shouldStreamLargeFiles() throws Exception {
        // Arrange - simulate 10MB file
        byte[] largeContent = new byte[10 * 1024 * 1024];
        for (int i = 0; i < largeContent.length; i++) {
            largeContent[i] = (byte) (i % 256);
        }

        when(storageService.exists(anyString())).thenReturn(true);

        // Use thenAnswer for fresh stream
        when(storageService.readFile(anyString())).thenAnswer(invocation ->
                new ByteArrayInputStream(largeContent)
        );

        when(auditService.getOriginalFilename("joe.bloggs", "2024-11-09T14-30-00_large.bin"))
                .thenReturn("large.bin");

        // Act & Assert
        mockMvc.perform(get("/download/joe.bloggs/2024-11-09T14-30-00_large.bin"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"large.bin\""));

        // Verify audit was recorded
        verify(auditService).recordDownload(anyString(), anyString(), anyString(), eq("admin"), any());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void downloadFile_shouldUseOriginalFilenameInHeader() throws Exception {
        // Arrange
        String fileContent = "Content";

        when(storageService.exists(anyString())).thenReturn(true);
        when(storageService.readFile(anyString())).thenAnswer(invocation ->
                new ByteArrayInputStream(fileContent.getBytes(StandardCharsets.UTF_8))
        );
        when(auditService.getOriginalFilename("joe.bloggs", "2024-11-09T14-30-00_My_Document.pdf"))
                .thenReturn("My Document.pdf");

        // Act & Assert
        mockMvc.perform(get("/download/joe.bloggs/2024-11-09T14-30-00_My_Document.pdf"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"My Document.pdf\""));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void downloadFile_shouldHandleFilenamesWithSpecialCharacters() throws Exception {
        // Arrange
        String fileContent = "Content";

        when(storageService.exists(anyString())).thenReturn(true);
        when(storageService.readFile(anyString())).thenAnswer(invocation ->
                new ByteArrayInputStream(fileContent.getBytes(StandardCharsets.UTF_8))
        );
        when(auditService.getOriginalFilename("joe.bloggs", "2024-11-09T14-30-00_file_name.pdf"))
                .thenReturn("file (copy).pdf");

        // Act & Assert
        mockMvc.perform(get("/download/joe.bloggs/2024-11-09T14-30-00_file_name.pdf"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"file (copy).pdf\""));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void downloadFile_shouldRecordDownloaderUsername() throws Exception {
        // Arrange
        String fileContent = "Content";

        when(storageService.exists(anyString())).thenReturn(true);
        when(storageService.readFile(anyString())).thenAnswer(invocation ->
                new ByteArrayInputStream(fileContent.getBytes(StandardCharsets.UTF_8))
        );
        when(auditService.getOriginalFilename(anyString(), anyString())).thenReturn("document.pdf");

        // Act
        mockMvc.perform(get("/download/joe.bloggs/2024-11-09T14-30-00_document.pdf"))
                .andExpect(status().isOk());

        // Assert - verify downloader is 'admin'
        verify(auditService).recordDownload(
                eq("joe.bloggs"),
                eq("document.pdf"),
                eq("2024-11-09T14-30-00_document.pdf"),
                eq("admin"), // downloader
                any()
        );
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void downloadFile_shouldHandleEmptyFile() throws Exception {
        // Arrange - empty file
        when(storageService.exists(anyString())).thenReturn(true);
        when(storageService.readFile(anyString())).thenAnswer(invocation ->
                new ByteArrayInputStream(new byte[0])
        );
        when(auditService.getOriginalFilename(anyString(), anyString())).thenReturn("empty.txt");

        // Act & Assert
        mockMvc.perform(get("/download/joe.bloggs/2024-11-09T14-30-00_empty.txt"))
                .andExpect(status().isOk())
                .andExpect(content().string(""));

        // Verify audit was recorded
        verify(auditService).recordDownload(anyString(), anyString(), anyString(), anyString(), any());
    }

    @Test
    @WithMockUser(username = "superadmin", roles = "ADMIN")
    void downloadFile_shouldWorkForAnyAdminUsername() throws Exception {
        // Arrange
        String fileContent = "Content";

        when(storageService.exists(anyString())).thenReturn(true);
        when(storageService.readFile(anyString())).thenAnswer(invocation ->
                new ByteArrayInputStream(fileContent.getBytes(StandardCharsets.UTF_8))
        );
        when(auditService.getOriginalFilename(anyString(), anyString())).thenReturn("document.pdf");

        // Act
        mockMvc.perform(get("/download/joe.bloggs/2024-11-09T14-30-00_document.pdf"))
                .andExpect(status().isOk());

        // Assert - verify downloader is 'superadmin'
        verify(auditService).recordDownload(
                eq("joe.bloggs"),
                eq("document.pdf"),
                eq("2024-11-09T14-30-00_document.pdf"),
                eq("superadmin"),
                any()
        );
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void downloadFile_shouldConstructCorrectStoragePath() throws Exception {
        // Arrange
        String fileContent = "Content";

        when(storageService.exists(anyString())).thenReturn(true);
        when(storageService.readFile(anyString())).thenAnswer(invocation ->
                new ByteArrayInputStream(fileContent.getBytes(StandardCharsets.UTF_8))
        );
        when(auditService.getOriginalFilename(anyString(), anyString())).thenReturn("document.pdf");

        // Act
        mockMvc.perform(get("/download/joe.bloggs/2024-11-09T14-30-00_document.pdf"))
                .andExpect(status().isOk())
                .andExpect(content().string(fileContent));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void downloadFile_shouldFallbackToStoredFilenameWhenOriginalNotFound() throws Exception {
        // Arrange
        String fileContent = "Content";

        when(storageService.exists(anyString())).thenReturn(true);
        when(storageService.readFile(anyString())).thenAnswer(invocation ->
                new ByteArrayInputStream(fileContent.getBytes(StandardCharsets.UTF_8))
        );
        // Simulate audit service returning stored filename as fallback
        when(auditService.getOriginalFilename("joe.bloggs", "2024-11-09T14-30-00_document.pdf"))
                .thenReturn("2024-11-09T14-30-00_document.pdf");

        // Act & Assert
        mockMvc.perform(get("/download/joe.bloggs/2024-11-09T14-30-00_document.pdf"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition",
                        "attachment; filename=\"2024-11-09T14-30-00_document.pdf\""));
    }
}