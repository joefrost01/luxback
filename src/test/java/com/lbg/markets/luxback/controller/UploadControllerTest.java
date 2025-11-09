package com.lbg.markets.luxback.controller;

import com.lbg.markets.luxback.config.LuxBackConfig;
import com.lbg.markets.luxback.service.AuditService;
import com.lbg.markets.luxback.service.FileMetadataService;
import com.lbg.markets.luxback.service.StorageService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for UploadController.
 * Tests file upload functionality, validation, and security.
 */
@WebMvcTest(UploadController.class)
@ActiveProfiles("dev-local")
@Import({com.lbg.markets.luxback.config.DevSecurityConfig.class,
        com.lbg.markets.luxback.config.LuxBackConfig.class})
class UploadControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private StorageService storageService;

    @MockBean
    private AuditService auditService;

    @MockBean
    private FileMetadataService fileMetadataService;

    @MockBean
    private LuxBackConfig config;

    @Test
    void uploadPage_shouldRequireAuthentication() throws Exception {
        // Act & Assert
        mockMvc.perform(get("/"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "user", roles = "USER")
    void uploadPage_shouldBeAccessibleByUser() throws Exception {
        // Arrange
        when(config.getMaxFileSize()).thenReturn(104857600L);

        // Act & Assert
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(view().name("upload"))
                .andExpect(model().attributeExists("username"))
                .andExpect(model().attribute("username", "user"));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void uploadPage_shouldBeAccessibleByAdmin() throws Exception {
        // Arrange
        when(config.getMaxFileSize()).thenReturn(104857600L);

        // Act & Assert
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(view().name("upload"))
                .andExpect(model().attributeExists("isAdmin"))
                .andExpect(model().attribute("isAdmin", true));
    }

    @Test
    @WithMockUser(username = "user", roles = "USER")
    void handleFileUpload_shouldAcceptValidFile() throws Exception {
        // Arrange
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "document.pdf",
                "application/pdf",
                "PDF content".getBytes()
        );

        when(config.getMaxFileSize()).thenReturn(104857600L);
        when(config.getAllowedContentTypes()).thenReturn(List.of("application/pdf"));
        when(config.getStoragePath()).thenReturn("/tmp/luxback/backups");
        when(fileMetadataService.generateStorageFilename("document.pdf"))
                .thenReturn("2024-11-09T14-30-00_document.pdf");

        // Mock validation methods - they should not throw
        doNothing().when(fileMetadataService).validateFileSize(anyLong());
        doNothing().when(fileMetadataService).validateContentType(anyString());

        // Act & Assert
        mockMvc.perform(multipart("/upload")
                        .file(file)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.message").value("File uploaded successfully: document.pdf"));

        // Verify storage and audit operations
        verify(storageService).writeFile(anyString(), any(), anyLong());
        verify(auditService).recordUpload(eq("user"), any(), any());
    }

    @Test
    @WithMockUser(username = "user", roles = "USER")
    void handleFileUpload_shouldRejectOversizedFile() throws Exception {
        // Arrange
        byte[] largeContent = new byte[101 * 1024 * 1024]; // 101MB
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "large.pdf",
                "application/pdf",
                largeContent
        );

        when(config.getMaxFileSize()).thenReturn(100 * 1024 * 1024L);
        doThrow(new com.lbg.markets.luxback.exception.FileSizeExceededException("File exceeds maximum size of 100 MB"))
                .when(fileMetadataService).validateFileSize(anyLong());

        // Act & Assert
        mockMvc.perform(multipart("/upload")
                        .file(file)
                        .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.message").value("File exceeds maximum size of 100 MB"));

        // Verify no storage or audit operations
        verify(storageService, never()).writeFile(anyString(), any(), anyLong());
        verify(auditService, never()).recordUpload(anyString(), any(), any());
    }

    @Test
    @WithMockUser(username = "user", roles = "USER")
    void handleFileUpload_shouldRejectInvalidFileType() throws Exception {
        // Arrange
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "malicious.exe",
                "application/x-executable",
                "Executable content".getBytes()
        );

        when(config.getMaxFileSize()).thenReturn(104857600L);
        doNothing().when(fileMetadataService).validateFileSize(anyLong());
        doThrow(new com.lbg.markets.luxback.exception.InvalidFileTypeException(
                "File type 'application/x-executable' is not allowed"))
                .when(fileMetadataService).validateContentType(anyString());

        // Act & Assert
        mockMvc.perform(multipart("/upload")
                        .file(file)
                        .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.message").value("File type 'application/x-executable' is not allowed"));

        // Verify no storage or audit operations
        verify(storageService, never()).writeFile(anyString(), any(), anyLong());
        verify(auditService, never()).recordUpload(anyString(), any(), any());
    }

    @Test
    @WithMockUser(username = "user", roles = "USER")
    void handleFileUpload_shouldRequireCsrfToken() throws Exception {
        // Arrange
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "document.pdf",
                "application/pdf",
                "PDF content".getBytes()
        );

        // Act & Assert - without CSRF token
        mockMvc.perform(multipart("/upload")
                        .file(file))
                .andExpect(status().isForbidden());

        // Verify no storage or audit operations
        verify(storageService, never()).writeFile(anyString(), any(), anyLong());
        verify(auditService, never()).recordUpload(anyString(), any(), any());
    }

    @Test
    void handleFileUpload_shouldRequireAuthentication() throws Exception {
        // Arrange
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "document.pdf",
                "application/pdf",
                "PDF content".getBytes()
        );

        // Act & Assert
        mockMvc.perform(multipart("/upload")
                        .file(file)
                        .with(csrf()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "user", roles = "USER")
    void handleFileUpload_shouldHandleStorageFailure() throws Exception {
        // Arrange
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "document.pdf",
                "application/pdf",
                "PDF content".getBytes()
        );

        when(config.getMaxFileSize()).thenReturn(104857600L);
        when(config.getAllowedContentTypes()).thenReturn(List.of("application/pdf"));
        when(config.getStoragePath()).thenReturn("/tmp/luxback/backups");
        when(fileMetadataService.generateStorageFilename("document.pdf"))
                .thenReturn("2024-11-09T14-30-00_document.pdf");

        doNothing().when(fileMetadataService).validateFileSize(anyLong());
        doNothing().when(fileMetadataService).validateContentType(anyString());

        // Simulate storage failure
        doThrow(new RuntimeException("Storage unavailable"))
                .when(storageService).writeFile(anyString(), any(), anyLong());

        // Act & Assert
        mockMvc.perform(multipart("/upload")
                        .file(file)
                        .with(csrf()))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.message").value("Upload failed. Please try again or contact support."));

        // Verify no audit operation (upload failed)
        verify(auditService, never()).recordUpload(anyString(), any(), any());
    }

    @Test
    @WithMockUser(username = "user", roles = "USER")
    void handleFileUpload_shouldAcceptMultipleFileTypes() throws Exception {
        // Arrange - test with Excel file
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "spreadsheet.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "Excel content".getBytes()
        );

        when(config.getMaxFileSize()).thenReturn(104857600L);
        when(config.getAllowedContentTypes()).thenReturn(List.of(
                "application/pdf",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        ));
        when(config.getStoragePath()).thenReturn("/tmp/luxback/backups");
        when(fileMetadataService.generateStorageFilename("spreadsheet.xlsx"))
                .thenReturn("2024-11-09T14-30-00_spreadsheet.xlsx");

        doNothing().when(fileMetadataService).validateFileSize(anyLong());
        doNothing().when(fileMetadataService).validateContentType(anyString());

        // Act & Assert
        mockMvc.perform(multipart("/upload")
                        .file(file)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"));

        verify(storageService).writeFile(anyString(), any(), anyLong());
        verify(auditService).recordUpload(eq("user"), any(), any());
    }

    @Test
    @WithMockUser(username = "user", roles = "USER")
    void handleFileUpload_shouldHandleEmptyFile() throws Exception {
        // Arrange
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "empty.txt",
                "text/plain",
                new byte[0]
        );

        when(config.getMaxFileSize()).thenReturn(104857600L);
        when(config.getAllowedContentTypes()).thenReturn(List.of("text/plain"));
        when(config.getStoragePath()).thenReturn("/tmp/luxback/backups");
        when(fileMetadataService.generateStorageFilename("empty.txt"))
                .thenReturn("2024-11-09T14-30-00_empty.txt");

        doNothing().when(fileMetadataService).validateFileSize(0L);
        doNothing().when(fileMetadataService).validateContentType("text/plain");

        // Act & Assert
        mockMvc.perform(multipart("/upload")
                        .file(file)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void handleFileUpload_shouldWorkForAdmin() throws Exception {
        // Arrange
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "admin-file.pdf",
                "application/pdf",
                "Admin PDF content".getBytes()
        );

        when(config.getMaxFileSize()).thenReturn(104857600L);
        when(config.getAllowedContentTypes()).thenReturn(List.of("application/pdf"));
        when(config.getStoragePath()).thenReturn("/tmp/luxback/backups");
        when(fileMetadataService.generateStorageFilename("admin-file.pdf"))
                .thenReturn("2024-11-09T14-30-00_admin-file.pdf");

        doNothing().when(fileMetadataService).validateFileSize(anyLong());
        doNothing().when(fileMetadataService).validateContentType(anyString());

        // Act & Assert
        mockMvc.perform(multipart("/upload")
                        .file(file)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"));

        // Verify admin username is used
        verify(auditService).recordUpload(eq("admin"), any(), any());
    }
}
