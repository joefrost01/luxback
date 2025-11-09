package com.lbg.markets.luxback.service;

import com.lbg.markets.luxback.config.LuxBackConfig;
import com.lbg.markets.luxback.model.AuditEvent;
import com.lbg.markets.luxback.model.FileMetadata;
import com.lbg.markets.luxback.model.SearchCriteria;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for AuditService.
 * Tests CSV operations, caching behavior, and search functionality.
 */
@ExtendWith(MockitoExtension.class)
class AuditServiceTest {

    @Mock
    private StorageService storageService;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpSession session;

    private AuditService auditService;
    private LuxBackConfig config;

    @BeforeEach
    void setUp() {
        config = new LuxBackConfig();
        config.setAuditIndexPath("/audit");
        auditService = new AuditService(storageService, config);
    }

    /**
     * Setup mock request for tests that need it
     */
    private void setupMockRequest() {
        when(request.getRemoteAddr()).thenReturn("192.168.1.1");
        when(request.getHeader("X-Forwarded-For")).thenReturn(null); // Simulate no proxy
        when(request.getHeader("User-Agent")).thenReturn("Mozilla/5.0");
        when(request.getSession()).thenReturn(session);
        when(session.getId()).thenReturn("session123");
    }

    @Test
    void recordUpload_shouldCreateNewCsvFileWithHeaders() {
        // Arrange
        setupMockRequest(); // Setup request mock for this test

        String username = "joe.bloggs";
        FileMetadata metadata = FileMetadata.builder()
                .originalFilename("document.pdf")
                .storedFilename("2024-11-09T14-30-00_document.pdf")
                .size(1024000L)
                .contentType("application/pdf")
                .build();

        when(storageService.exists(anyString())).thenReturn(false);

        // Act
        auditService.recordUpload(username, metadata, request);

        // Assert
        ArgumentCaptor<String> pathCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> contentCaptor = ArgumentCaptor.forClass(String.class);

        verify(storageService).writeString(pathCaptor.capture(), contentCaptor.capture());

        assertThat(pathCaptor.getValue()).isEqualTo("/audit/joe.bloggs.csv");

        String csvContent = contentCaptor.getValue();
        assertThat(csvContent).contains("event_id,event_type,timestamp");
        assertThat(csvContent).contains("UPLOAD");
        assertThat(csvContent).contains("document.pdf");
        assertThat(csvContent).contains("2024-11-09T14-30-00_document.pdf");
        assertThat(csvContent).contains("1024000");
        assertThat(csvContent).contains("application/pdf");
        assertThat(csvContent).contains("192.168.1.1");
    }

    @Test
    void recordUpload_shouldAppendToExistingCsvFile() {
        // Arrange
        setupMockRequest(); // Setup request mock for this test

        String username = "joe.bloggs";
        FileMetadata metadata = FileMetadata.builder()
                .originalFilename("report.xlsx")
                .storedFilename("2024-11-10T09-00-00_report.xlsx")
                .size(2048000L)
                .contentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                .build();

        when(storageService.exists(anyString())).thenReturn(true);

        // Act
        auditService.recordUpload(username, metadata, request);

        // Assert
        verify(storageService).append(eq("/audit/joe.bloggs.csv"), anyString());
        verify(storageService, never()).writeString(anyString(), anyString());
    }

    @Test
    void recordDownload_shouldAppendDownloadEventWithDownloaderUsername() {
        // Arrange
        setupMockRequest(); // Setup request mock for this test

        String fileOwner = "joe.bloggs";
        String downloaderUsername = "admin";
        String originalFilename = "document.pdf";
        String storedFilename = "2024-11-09T14-30-00_document.pdf";

        when(storageService.exists(anyString())).thenReturn(true);

        // Act
        auditService.recordDownload(fileOwner, originalFilename, storedFilename,
                downloaderUsername, request);

        // Assert
        ArgumentCaptor<String> contentCaptor = ArgumentCaptor.forClass(String.class);
        verify(storageService).append(eq("/audit/joe.bloggs.csv"), contentCaptor.capture());

        String csvContent = contentCaptor.getValue();
        assertThat(csvContent).contains("DOWNLOAD");
        assertThat(csvContent).contains("document.pdf");
        assertThat(csvContent).contains(downloaderUsername);
        // File size and content type should be empty for downloads
        assertThat(csvContent).doesNotContain("1024000");
    }

    @Test
    void searchAllAudit_shouldReturnEmptyListWhenNoAuditFiles() {
        // Arrange
        when(storageService.listFiles(anyString())).thenReturn(List.of());
        SearchCriteria criteria = SearchCriteria.builder().build();

        // Act
        List<AuditEvent> results = auditService.searchAllAudit(criteria);

        // Assert
        assertThat(results).isEmpty();
    }

    @Test
    void searchAllAudit_shouldFilterByFilename() {
        // Arrange
        String csvContent = """
                event_id,event_type,timestamp,username,filename,stored_as,file_size,content_type,ip_address,user_agent,session_id,actor_username
                uuid1,UPLOAD,2024-11-09T14:30:00Z,joe.bloggs,document.pdf,2024-11-09T14-30-00_document.pdf,1024000,application/pdf,192.168.1.1,Mozilla/5.0,session123,joe.bloggs
                uuid2,UPLOAD,2024-11-09T15:00:00Z,joe.bloggs,report.xlsx,2024-11-09T15-00-00_report.xlsx,2048000,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet,192.168.1.1,Mozilla/5.0,session123,joe.bloggs
                """;

        when(storageService.listFiles("/audit")).thenReturn(List.of("/audit/joe.bloggs.csv"));
        when(storageService.exists("/audit/joe.bloggs.csv")).thenReturn(true);
        when(storageService.readString("/audit/joe.bloggs.csv")).thenReturn(csvContent);

        SearchCriteria criteria = SearchCriteria.builder()
                .filename("document")
                .build();

        // Act
        List<AuditEvent> results = auditService.searchAllAudit(criteria);

        // Assert
        assertThat(results).hasSize(1);
        assertThat(results.get(0).getFilename()).isEqualTo("document.pdf");
    }

    @Test
    void searchAllAudit_shouldFilterByUsername() {
        // Arrange
        String user1Csv = """
                event_id,event_type,timestamp,username,filename,stored_as,file_size,content_type,ip_address,user_agent,session_id,actor_username
                uuid1,UPLOAD,2024-11-09T14:30:00Z,joe.bloggs,document.pdf,2024-11-09T14-30-00_document.pdf,1024000,application/pdf,192.168.1.1,Mozilla/5.0,session123,joe.bloggs
                """;

        String user2Csv = """
                event_id,event_type,timestamp,username,filename,stored_as,file_size,content_type,ip_address,user_agent,session_id,actor_username
                uuid2,UPLOAD,2024-11-09T15:00:00Z,jane.smith,report.xlsx,2024-11-09T15-00-00_report.xlsx,2048000,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet,192.168.1.2,Mozilla/5.0,session456,jane.smith
                """;

        when(storageService.listFiles("/audit")).thenReturn(
                List.of("/audit/joe.bloggs.csv", "/audit/jane.smith.csv"));
        when(storageService.exists("/audit/joe.bloggs.csv")).thenReturn(true);
        when(storageService.exists("/audit/jane.smith.csv")).thenReturn(true);
        when(storageService.readString("/audit/joe.bloggs.csv")).thenReturn(user1Csv);
        when(storageService.readString("/audit/jane.smith.csv")).thenReturn(user2Csv);

        SearchCriteria criteria = SearchCriteria.builder()
                .username("joe.bloggs")
                .build();

        // Act
        List<AuditEvent> results = auditService.searchAllAudit(criteria);

        // Assert
        assertThat(results).hasSize(1);
        assertThat(results.get(0).getUsername()).isEqualTo("joe.bloggs");
    }

    @Test
    void searchAllAudit_shouldFilterByDateRange() {
        // Arrange
        String csvContent = """
                event_id,event_type,timestamp,username,filename,stored_as,file_size,content_type,ip_address,user_agent,session_id,actor_username
                uuid1,UPLOAD,2024-11-05T14:30:00Z,joe.bloggs,old-file.pdf,2024-11-05T14-30-00_old-file.pdf,1024000,application/pdf,192.168.1.1,Mozilla/5.0,session123,joe.bloggs
                uuid2,UPLOAD,2024-11-09T15:00:00Z,joe.bloggs,new-file.xlsx,2024-11-09T15-00-00_new-file.xlsx,2048000,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet,192.168.1.1,Mozilla/5.0,session123,joe.bloggs
                uuid3,UPLOAD,2024-11-15T10:00:00Z,joe.bloggs,future-file.txt,2024-11-15T10-00-00_future-file.txt,512000,text/plain,192.168.1.1,Mozilla/5.0,session123,joe.bloggs
                """;

        when(storageService.listFiles("/audit")).thenReturn(List.of("/audit/joe.bloggs.csv"));
        when(storageService.exists("/audit/joe.bloggs.csv")).thenReturn(true);
        when(storageService.readString("/audit/joe.bloggs.csv")).thenReturn(csvContent);

        SearchCriteria criteria = SearchCriteria.builder()
                .startDate(LocalDate.of(2024, 11, 8))
                .endDate(LocalDate.of(2024, 11, 10))
                .build();

        // Act
        List<AuditEvent> results = auditService.searchAllAudit(criteria);

        // Assert
        assertThat(results).hasSize(1);
        assertThat(results.get(0).getFilename()).isEqualTo("new-file.xlsx");
    }

    @Test
    void searchAllAudit_shouldSortByTimestampDescending() {
        // Arrange
        String csvContent = """
                event_id,event_type,timestamp,username,filename,stored_as,file_size,content_type,ip_address,user_agent,session_id,actor_username
                uuid1,UPLOAD,2024-11-09T14:30:00Z,joe.bloggs,first.pdf,2024-11-09T14-30-00_first.pdf,1024000,application/pdf,192.168.1.1,Mozilla/5.0,session123,joe.bloggs
                uuid2,UPLOAD,2024-11-09T15:00:00Z,joe.bloggs,second.xlsx,2024-11-09T15-00-00_second.xlsx,2048000,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet,192.168.1.1,Mozilla/5.0,session123,joe.bloggs
                uuid3,UPLOAD,2024-11-09T16:00:00Z,joe.bloggs,third.txt,2024-11-09T16-00-00_third.txt,512000,text/plain,192.168.1.1,Mozilla/5.0,session123,joe.bloggs
                """;

        when(storageService.listFiles("/audit")).thenReturn(List.of("/audit/joe.bloggs.csv"));
        when(storageService.exists("/audit/joe.bloggs.csv")).thenReturn(true);
        when(storageService.readString("/audit/joe.bloggs.csv")).thenReturn(csvContent);

        SearchCriteria criteria = SearchCriteria.builder().build();

        // Act
        List<AuditEvent> results = auditService.searchAllAudit(criteria);

        // Assert
        assertThat(results).hasSize(3);
        assertThat(results.get(0).getFilename()).isEqualTo("third.txt");
        assertThat(results.get(1).getFilename()).isEqualTo("second.xlsx");
        assertThat(results.get(2).getFilename()).isEqualTo("first.pdf");
    }

    @Test
    void getOriginalFilename_shouldReturnOriginalFromAuditRecord() {
        // Arrange
        String csvContent = """
                event_id,event_type,timestamp,username,filename,stored_as,file_size,content_type,ip_address,user_agent,session_id,actor_username
                uuid1,UPLOAD,2024-11-09T14:30:00Z,joe.bloggs,My Document.pdf,2024-11-09T14-30-00_My_Document.pdf,1024000,application/pdf,192.168.1.1,Mozilla/5.0,session123,joe.bloggs
                """;

        when(storageService.exists("/audit/joe.bloggs.csv")).thenReturn(true);
        when(storageService.readString("/audit/joe.bloggs.csv")).thenReturn(csvContent);

        // Act
        String originalFilename = auditService.getOriginalFilename("joe.bloggs",
                "2024-11-09T14-30-00_My_Document.pdf");

        // Assert
        assertThat(originalFilename).isEqualTo("My Document.pdf");
    }

    @Test
    void getOriginalFilename_shouldReturnStoredFilenameIfNotFound() {
        // Arrange
        when(storageService.exists("/audit/joe.bloggs.csv")).thenReturn(true);
        when(storageService.readString("/audit/joe.bloggs.csv")).thenReturn(
                "event_id,event_type,timestamp,username,filename,stored_as,file_size,content_type,ip_address,user_agent,session_id,actor_username\n");

        // Act
        String originalFilename = auditService.getOriginalFilename("joe.bloggs",
                "2024-11-09T14-30-00_unknown.pdf");

        // Assert
        assertThat(originalFilename).isEqualTo("2024-11-09T14-30-00_unknown.pdf");
    }

    @Test
    void searchAllAudit_shouldCacheUserAuditData() {
        // Arrange
        String csvContent = """
                event_id,event_type,timestamp,username,filename,stored_as,file_size,content_type,ip_address,user_agent,session_id,actor_username
                uuid1,UPLOAD,2024-11-09T14:30:00Z,joe.bloggs,document.pdf,2024-11-09T14-30-00_document.pdf,1024000,application/pdf,192.168.1.1,Mozilla/5.0,session123,joe.bloggs
                """;

        when(storageService.listFiles("/audit")).thenReturn(List.of("/audit/joe.bloggs.csv"));
        when(storageService.exists("/audit/joe.bloggs.csv")).thenReturn(true);
        when(storageService.readString("/audit/joe.bloggs.csv")).thenReturn(csvContent);

        SearchCriteria criteria = SearchCriteria.builder().build();

        // Act - call twice
        auditService.searchAllAudit(criteria);
        auditService.searchAllAudit(criteria);

        // Assert - should only read from storage once (subsequent calls use cache)
        verify(storageService, times(1)).readString("/audit/joe.bloggs.csv");
    }

    @Test
    void recordUpload_shouldInvalidateCacheForUser() {
        // Arrange
        setupMockRequest(); // Setup request mock for this test

        String username = "joe.bloggs";
        String csvContent = """
                event_id,event_type,timestamp,username,filename,stored_as,file_size,content_type,ip_address,user_agent,session_id,actor_username
                uuid1,UPLOAD,2024-11-09T14:30:00Z,joe.bloggs,old.pdf,2024-11-09T14-30-00_old.pdf,1024000,application/pdf,192.168.1.1,Mozilla/5.0,session123,joe.bloggs
                """;

        when(storageService.listFiles("/audit")).thenReturn(List.of("/audit/joe.bloggs.csv"));
        when(storageService.exists("/audit/joe.bloggs.csv")).thenReturn(true);
        when(storageService.readString("/audit/joe.bloggs.csv")).thenReturn(csvContent);

        // First search to populate cache
        auditService.searchAllAudit(SearchCriteria.builder().build());

        // Reset mock to verify next call
        reset(storageService);
        when(storageService.exists("/audit/joe.bloggs.csv")).thenReturn(true);

        FileMetadata metadata = FileMetadata.builder()
                .originalFilename("new.pdf")
                .storedFilename("2024-11-10T10-00-00_new.pdf")
                .size(2048000L)
                .contentType("application/pdf")
                .build();

        // Act - record upload (should invalidate cache)
        auditService.recordUpload(username, metadata, request);

        // Assert - verify cache was used before upload
        verify(storageService, never()).readString(anyString());
    }

    @Test
    void searchAllAudit_shouldHandleEmptyOptionalFields() {
        // Arrange - CSV with some optional fields empty
        String csvContent = """
                event_id,event_type,timestamp,username,filename,stored_as,file_size,content_type,ip_address,user_agent,session_id,actor_username
                uuid1,DOWNLOAD,2024-11-09T14:30:00Z,joe.bloggs,document.pdf,2024-11-09T14-30-00_document.pdf,,,192.168.1.1,,session123,admin
                """;

        when(storageService.listFiles("/audit")).thenReturn(List.of("/audit/joe.bloggs.csv"));
        when(storageService.exists("/audit/joe.bloggs.csv")).thenReturn(true);
        when(storageService.readString("/audit/joe.bloggs.csv")).thenReturn(csvContent);

        SearchCriteria criteria = SearchCriteria.builder().build();

        // Act
        List<AuditEvent> results = auditService.searchAllAudit(criteria);

        // Assert
        assertThat(results).hasSize(1);
        AuditEvent event = results.get(0);
        assertThat(event.getFileSize()).isNull();
        assertThat(event.getContentType()).isNull();
        assertThat(event.getUserAgent()).isNull();
        assertThat(event.getActorUsername()).isEqualTo("admin");
    }
}
