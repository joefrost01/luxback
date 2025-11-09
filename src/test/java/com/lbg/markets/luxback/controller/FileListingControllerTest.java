package com.lbg.markets.luxback.controller;

import com.lbg.markets.luxback.config.TestConfig;
import com.lbg.markets.luxback.model.AuditEvent;
import com.lbg.markets.luxback.model.SearchCriteria;
import com.lbg.markets.luxback.service.AuditService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for FileListingController.
 * Tests file browser, search, pagination, and admin-only access.
 */
@WebMvcTest(FileListingController.class)
@ActiveProfiles("dev-local")
@Import({com.lbg.markets.luxback.config.DevSecurityConfig.class, TestConfig.class})
class FileListingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AuditService auditService;

    @Test
    void listFiles_shouldRequireAuthentication() throws Exception {
        // Act & Assert
        mockMvc.perform(get("/files"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "user", roles = "USER")
    void listFiles_shouldBeForbiddenForRegularUser() throws Exception {
        // Act & Assert
        mockMvc.perform(get("/files"))
                .andExpect(status().isForbidden());

        // Verify service was never called
        verify(auditService, never()).searchAllAudit(any());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void listFiles_shouldBeAccessibleByAdmin() throws Exception {
        // Arrange
        List<AuditEvent> mockEvents = List.of(
                createUploadEvent("uuid1", "joe.bloggs", "document.pdf", "2024-11-09T14:30:00Z"),
                createUploadEvent("uuid2", "jane.smith", "report.xlsx", "2024-11-10T09:00:00Z")
        );
        when(auditService.searchAllAudit(any(SearchCriteria.class))).thenReturn(mockEvents);

        // Act & Assert
        mockMvc.perform(get("/files"))
                .andExpect(status().isOk())
                .andExpect(view().name("file-listing"))
                .andExpect(model().attributeExists("files"))
                .andExpect(model().attribute("files", hasSize(2)))
                .andExpect(model().attributeExists("totalResults"))
                .andExpect(model().attribute("totalResults", 2));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void listFiles_shouldFilterByFilename() throws Exception {
        // Arrange
        List<AuditEvent> mockEvents = List.of(
                createUploadEvent("uuid1", "joe.bloggs", "document.pdf", "2024-11-09T14:30:00Z")
        );
        when(auditService.searchAllAudit(any(SearchCriteria.class))).thenReturn(mockEvents);

        // Act & Assert
        mockMvc.perform(get("/files")
                        .param("filename", "document"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("files", hasSize(1)))
                .andExpect(model().attributeExists("criteria"));

        // Verify search criteria was passed correctly
        verify(auditService).searchAllAudit(argThat(criteria ->
                criteria.getFilename() != null && criteria.getFilename().equals("document")
        ));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void listFiles_shouldFilterByUsername() throws Exception {
        // Arrange
        List<AuditEvent> mockEvents = List.of(
                createUploadEvent("uuid1", "joe.bloggs", "document.pdf", "2024-11-09T14:30:00Z")
        );
        when(auditService.searchAllAudit(any(SearchCriteria.class))).thenReturn(mockEvents);

        // Act & Assert
        mockMvc.perform(get("/files")
                        .param("username", "joe.bloggs"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("files", hasSize(1)));

        verify(auditService).searchAllAudit(argThat(criteria ->
                criteria.getUsername() != null && criteria.getUsername().equals("joe.bloggs")
        ));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void listFiles_shouldFilterByDateRange() throws Exception {
        // Arrange
        List<AuditEvent> mockEvents = List.of(
                createUploadEvent("uuid1", "joe.bloggs", "document.pdf", "2024-11-09T14:30:00Z")
        );
        when(auditService.searchAllAudit(any(SearchCriteria.class))).thenReturn(mockEvents);

        // Act & Assert
        mockMvc.perform(get("/files")
                        .param("startDate", "2024-11-01")
                        .param("endDate", "2024-11-30"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("files", hasSize(1)));

        verify(auditService).searchAllAudit(argThat(criteria ->
                criteria.getStartDate() != null &&
                        criteria.getStartDate().equals(LocalDate.of(2024, 11, 1)) &&
                        criteria.getEndDate() != null &&
                        criteria.getEndDate().equals(LocalDate.of(2024, 11, 30))
        ));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void listFiles_shouldPaginateResults() throws Exception {
        // Arrange - create 75 events (should be 2 pages at 50 per page)
        List<AuditEvent> mockEvents = new ArrayList<>();
        for (int i = 0; i < 75; i++) {
            mockEvents.add(createUploadEvent(
                    "uuid" + i,
                    "joe.bloggs",
                    "file" + i + ".pdf",
                    "2024-11-09T14:30:00Z"
            ));
        }
        when(auditService.searchAllAudit(any(SearchCriteria.class))).thenReturn(mockEvents);

        // Act & Assert - first page
        mockMvc.perform(get("/files")
                        .param("page", "0"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("files", hasSize(50)))
                .andExpect(model().attribute("currentPage", 0))
                .andExpect(model().attribute("totalPages", 2))
                .andExpect(model().attribute("totalResults", 75));

        // Act & Assert - second page
        mockMvc.perform(get("/files")
                        .param("page", "1"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("files", hasSize(25)))
                .andExpect(model().attribute("currentPage", 1));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void listFiles_shouldShowEmptyListWhenNoResults() throws Exception {
        // Arrange
        when(auditService.searchAllAudit(any(SearchCriteria.class))).thenReturn(List.of());

        // Act & Assert
        mockMvc.perform(get("/files"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("files", hasSize(0)))
                .andExpect(model().attribute("totalResults", 0))
                .andExpect(model().attribute("totalPages", 0));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void listFiles_shouldOnlyShowUploadEvents() throws Exception {
        // Arrange - mix of upload and download events
        List<AuditEvent> mockEvents = List.of(
                createUploadEvent("uuid1", "joe.bloggs", "document.pdf", "2024-11-09T14:30:00Z"),
                createDownloadEvent("uuid2", "joe.bloggs", "document.pdf", "2024-11-09T15:00:00Z", "admin"),
                createUploadEvent("uuid3", "jane.smith", "report.xlsx", "2024-11-10T09:00:00Z")
        );
        when(auditService.searchAllAudit(any(SearchCriteria.class))).thenReturn(mockEvents);

        // Act & Assert
        mockMvc.perform(get("/files"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("files", hasSize(2))); // Only uploads
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void listFiles_shouldHandleCombinedFilters() throws Exception {
        // Arrange
        List<AuditEvent> mockEvents = List.of(
                createUploadEvent("uuid1", "joe.bloggs", "document.pdf", "2024-11-09T14:30:00Z")
        );
        when(auditService.searchAllAudit(any(SearchCriteria.class))).thenReturn(mockEvents);

        // Act & Assert - combined filters
        mockMvc.perform(get("/files")
                        .param("filename", "document")
                        .param("username", "joe.bloggs")
                        .param("startDate", "2024-11-01")
                        .param("endDate", "2024-11-30"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("files", hasSize(1)));

        verify(auditService).searchAllAudit(argThat(criteria ->
                "document".equals(criteria.getFilename()) &&
                        "joe.bloggs".equals(criteria.getUsername()) &&
                        criteria.getStartDate() != null &&
                        criteria.getEndDate() != null
        ));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void listFiles_shouldDefaultToPageZero() throws Exception {
        // Arrange
        List<AuditEvent> mockEvents = List.of(
                createUploadEvent("uuid1", "joe.bloggs", "document.pdf", "2024-11-09T14:30:00Z")
        );
        when(auditService.searchAllAudit(any(SearchCriteria.class))).thenReturn(mockEvents);

        // Act & Assert - no page parameter
        mockMvc.perform(get("/files"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("currentPage", 0));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void listFiles_shouldHandlePageBeyondResults() throws Exception {
        // Arrange - only 10 events (1 page)
        List<AuditEvent> mockEvents = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            mockEvents.add(createUploadEvent(
                    "uuid" + i,
                    "joe.bloggs",
                    "file" + i + ".pdf",
                    "2024-11-09T14:30:00Z"
            ));
        }
        when(auditService.searchAllAudit(any(SearchCriteria.class))).thenReturn(mockEvents);

        // Act & Assert - request page 5 (beyond available data)
        mockMvc.perform(get("/files")
                        .param("page", "5"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("files", hasSize(0))) // Empty page
                .andExpect(model().attribute("currentPage", 5))
                .andExpect(model().attribute("totalResults", 10));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void listFiles_shouldPreserveCriteriaInModel() throws Exception {
        // Arrange
        List<AuditEvent> mockEvents = List.of(
                createUploadEvent("uuid1", "joe.bloggs", "document.pdf", "2024-11-09T14:30:00Z")
        );
        when(auditService.searchAllAudit(any(SearchCriteria.class))).thenReturn(mockEvents);

        // Act & Assert
        mockMvc.perform(get("/files")
                        .param("filename", "test")
                        .param("username", "joe.bloggs")
                        .param("startDate", "2024-11-01"))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("criteria"));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void listFiles_shouldHandleExactly50Results() throws Exception {
        // Arrange - exactly 50 events (should be 1 page)
        List<AuditEvent> mockEvents = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            mockEvents.add(createUploadEvent(
                    "uuid" + i,
                    "joe.bloggs",
                    "file" + i + ".pdf",
                    "2024-11-09T14:30:00Z"
            ));
        }
        when(auditService.searchAllAudit(any(SearchCriteria.class))).thenReturn(mockEvents);

        // Act & Assert
        mockMvc.perform(get("/files"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("files", hasSize(50)))
                .andExpect(model().attribute("totalPages", 1));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void listFiles_shouldHandleExactly51Results() throws Exception {
        // Arrange - 51 events (should be 2 pages)
        List<AuditEvent> mockEvents = new ArrayList<>();
        for (int i = 0; i < 51; i++) {
            mockEvents.add(createUploadEvent(
                    "uuid" + i,
                    "joe.bloggs",
                    "file" + i + ".pdf",
                    "2024-11-09T14:30:00Z"
            ));
        }
        when(auditService.searchAllAudit(any(SearchCriteria.class))).thenReturn(mockEvents);

        // Act & Assert - page 1
        mockMvc.perform(get("/files")
                        .param("page", "1"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("files", hasSize(1)))
                .andExpect(model().attribute("totalPages", 2));
    }

    // Helper methods
    private AuditEvent createUploadEvent(String eventId, String username, String filename, String timestamp) {
        return AuditEvent.builder()
                .eventId(eventId)
                .eventType("UPLOAD")
                .timestamp(Instant.parse(timestamp))
                .username(username)
                .filename(filename)
                .storedAs("2024-11-09T14-30-00_" + filename)
                .fileSize(1024000L)
                .contentType("application/pdf")
                .ipAddress("192.168.1.1")
                .userAgent("Mozilla/5.0")
                .sessionId("session123")
                .actorUsername(username)
                .build();
    }

    private AuditEvent createDownloadEvent(String eventId, String username, String filename,
                                           String timestamp, String downloader) {
        return AuditEvent.builder()
                .eventId(eventId)
                .eventType("DOWNLOAD")
                .timestamp(Instant.parse(timestamp))
                .username(username)
                .filename(filename)
                .storedAs("2024-11-09T14-30-00_" + filename)
                .ipAddress("192.168.1.1")
                .userAgent("Mozilla/5.0")
                .sessionId("session456")
                .actorUsername(downloader)
                .build();
    }
}
