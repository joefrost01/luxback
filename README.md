# LuxBack - File Backup Service Design Document

## Overview

LuxBack is a Spring Boot 3 file backup service that allows users to securely upload files to Google Cloud Storage (or local disk in development) with comprehensive audit logging. The system supports approximately 50 users with low traffic patterns.

**Artifact:** `com.lbg.markets.luxback`  
**Deployment:** CloudRun (containerized)  
**UI Framework:** Thymeleaf with Bootstrap (Bootswatch Flatly theme)  
**Upload Component:** Dropzone.js or similar file upload helper

## Core Design Philosophy

The design prioritizes **simplicity and pragmatism** over complexity:

- **No database** - With 50 users and low traffic, CSV files and in-memory caching are sufficient
- **Clean separation** - Interface-based abstractions allow profile-specific implementations
- **Flat data model** - Simple CSV schema with append-only operations
- **Natural partitioning** - Per-user files eliminate cross-user contention
- **Efficient caching** - Entire audit history loads into memory (~2.5MB for a year)

As the original requirement stated: "the number one feature is simplicity."

## Key Design Decisions

### 1. CSV-Based Audit System

**Decision:** Use per-user CSV files for audit logging instead of a database.

**Rationale:**
- **Scale appropriate** - 50 users × 100 uploads/month × 500 bytes/entry = ~2.5MB/year
- **Performance** - Load all 50 files in ~50ms, filter in-memory with Java Streams
- **No contention** - Per-user files eliminate locking between users
- **Append efficiency** - CSV files support simple append operations
- **Debuggable** - Open files in Excel/spreadsheet for inspection
- **Schema evolution** - Adding columns at the end preserves backward compatibility
- **No infrastructure** - No database connection pools, migrations, or operational overhead

**Alternative considered:** JSON with nested structure - rejected because:
- Requires parsing entire file to append new events
- More complex than needed for flat event structure
- Slower parsing and larger file sizes

### 2. Flat Event Model

**Decision:** Store all audit events (uploads, downloads) as flat records, not nested structures.

**Schema:**
```csv
event_id,event_type,timestamp,username,filename,stored_as,file_size,content_type,ip_address,user_agent,session_id,actor_username
uuid1,UPLOAD,2024-11-09T14:30:00Z,joe.bloggs,document.pdf,2024-11-09T14-30-00_document.pdf,1024000,application/pdf,10.0.0.1,Mozilla/5.0...,abc123,joe.bloggs
uuid2,DOWNLOAD,2024-11-10T09:15:00Z,joe.bloggs,document.pdf,2024-11-09T14-30-00_document.pdf,,,10.0.0.2,Mozilla/5.0...,def456,admin.user
```

**Fields:**
- `username` - File owner
- `actor_username` - Who performed the action (uploader or downloader)
- Empty values for fields not applicable to event type (e.g., file_size for downloads)

**Rationale:**
- Simpler to process than nested JSON structures
- Natural fit for CSV format
- Easy to filter and sort with standard tools
- Schema changes handled by adding columns at end

### 3. In-Memory Audit Cache

**Decision:** Load entire audit history into memory on first admin search or at startup.

**Implementation strategies:**

**Option A - Lazy load with full cache invalidation:**
```java
private volatile List<AuditEvent> auditCache = null;

public void recordUpload(...) {
    appendToUserCSV(...);
    auditCache = null; // Invalidate entire cache
}

public List<AuditEvent> searchAll(...) {
    if (auditCache == null) {
        refreshCache(); // Load all 50 CSV files
    }
    return filterInMemory(auditCache, criteria);
}
```

**Option B - Per-user caching (recommended):**
```java
private final ConcurrentHashMap<String, List<AuditEvent>> perUserCache = new ConcurrentHashMap<>();

public void recordUpload(String username, ...) {
    appendToUserCSV(username, ...);
    perUserCache.remove(username); // Invalidate only this user
}

public List<AuditEvent> searchAll(...) {
    List<AuditEvent> allEvents = getAllUsernames().stream()
        .flatMap(u -> getUserEvents(u).stream())
        .collect(Collectors.toList());
    return filterInMemory(allEvents, criteria);
}
```

**Rationale:**
- 2.5MB fits comfortably in memory with room for growth
- Admin searches become instant after initial load
- Complex filtering with full power of Java Streams
- Per-user caching minimizes reload overhead

### 4. Profile-Based Storage Abstraction

**Decision:** Use interface-based storage with profile-specific implementations.

```java
public interface StorageService {
    void writeFile(String path, InputStream inputStream, long size);
    InputStream readFile(String path);
    void writeString(String path, String content);
    String readString(String path);
    void append(String path, String content);
    boolean exists(String path);
    List<String> listFiles(String prefix);
}

@Service
@Profile("dev-local")
public class LocalStorageService implements StorageService {
    // Writes to local filesystem
}

@Service
@Profile({"int-gcp", "pre-prod-gcp", "prod-gcp"})
public class GcsStorageService implements StorageService {
    // Writes to Google Cloud Storage
}
```

**Profiles:**
- `dev-local` - Local filesystem storage, basic auth
- `int-gcp` - GCS storage, Azure AD OAuth2
- `pre-prod-gcp` - GCS storage, Azure AD OAuth2
- `prod-gcp` - GCS storage, Azure AD OAuth2

**Rationale:**
- Clean separation of concerns
- Easy to test without GCS dependencies
- No conditional logic scattered through code
- Simple to add new storage backends if needed

### 5. Centralized Configuration

**Decision:** Use single configuration class bound to YAML root instead of scattered `@Value` annotations.

```java
@Configuration
@ConfigurationProperties(prefix = "luxback")
public class LuxBackConfig {
    private String storagePath;
    private String auditIndexPath;
    private long maxFileSize;
    private List<String> allowedContentTypes;
    private Security security;
    
    public static class Security {
        private String devUsername;
        private String devPassword;
        private String adminUsername;
        private String adminPassword;
    }
    
    // Getters and setters
}
```

**application.yml structure:**
```yaml
# application.yml (defaults for all profiles)
luxback:
  storage-path: /backups
  audit-index-path: /audit-indexes
  max-file-size: 104857600 # 100MB
  allowed-content-types:
    - application/pdf
    - application/vnd.ms-excel
    - text/plain

# application-dev-local.yml
luxback:
  storage-path: /tmp/luxback/backups
  audit-index-path: /tmp/luxback/audit-indexes
  security:
    dev-username: user
    dev-password: userpass
    admin-username: admin
    admin-password: adminpass

# application-int-gcp.yml
luxback:
  storage-path: gs://luxback-int/backups
  audit-index-path: gs://luxback-int/audit-indexes

# application-pre-prod-gcp.yml
luxback:
  storage-path: gs://luxback-preprod/backups
  audit-index-path: gs://luxback-preprod/audit-indexes

# application-prod-gcp.yml
luxback:
  storage-path: gs://luxback-prod/backups
  audit-index-path: gs://luxback-prod/audit-indexes
```

**Rationale:**
- Single injection point - inject `LuxBackConfig` where needed
- Type-safe configuration access
- IDE autocomplete support
- Easy to see all configuration in one place
- No magic strings scattered through codebase

## Architecture

### Module Structure

```
com.lbg.markets.luxback
├── config
│   ├── SecurityConfig            # Profile-based security (BasicAuth vs OAuth2)
│   ├── StorageConfig            # Profile-based storage bean configuration
│   ├── WebConfig                # Dropzone, CSRF, file upload settings
│   └── LuxBackConfig            # Application configuration properties
├── controller
│   ├── LoginController          # Login page
│   ├── UploadController         # File upload (streaming)
│   ├── FileListingController    # Admin-only file browser
│   ├── DownloadController       # Admin-only file download
│   └── ErrorController          # Error page
├── service
│   ├── StorageService           # Interface for storage operations
│   ├── LocalStorageService      # @Profile("dev-local")
│   ├── GcsStorageService        # @Profile({"int-gcp", "pre-prod-gcp", "prod-gcp"})
│   ├── AuditService             # CSV audit management & caching
│   └── FileMetadataService      # File naming, validation
├── security
│   ├── Role                     # Enum: USER, ADMIN
│   ├── SecurityUtils            # Get current user, check roles
│   └── CustomAccessDeniedHandler
├── model
│   ├── AuditEvent               # Single audit record
│   ├── FileMetadata             # Upload metadata
│   └── SearchCriteria           # Filter parameters
└── exception
    ├── StorageException
    ├── FileSizeExceededException
    └── InvalidFileTypeException
```

### File Organization

```
Storage (local or GCS):
/backups
  /{username}
    /2024-11-09T14-30-00_document.pdf
    /2024-11-10T08-45-12_report.xlsx

/audit-indexes
  /joe.bloggs.csv
  /jane.smith.csv
  /admin.user.csv
```

**File naming convention:** `{ISO-8601-timestamp}_{original-filename}`
- Example: `2024-11-09T14-30-00_quarterly-report.pdf`
- Prevents name collisions
- Sortable by time
- Preserves original filename for user recognition

### Security Model

**Two authentication modes based on profile:**

#### Development Mode (`dev-local` profile)
```java
@Configuration
@Profile("dev-local")
public class DevSecurityConfig {
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/", "/upload").hasAnyRole("USER", "ADMIN")
                .requestMatchers("/files/**", "/download/**").hasRole("ADMIN")
                .anyRequest().authenticated()
            )
            .httpBasic();
        return http.build();
    }
    
    @Bean
    public UserDetailsService userDetailsService(LuxBackConfig config) {
        UserDetails user = User.builder()
            .username(config.getSecurity().getDevUsername())
            .password("{noop}" + config.getSecurity().getDevPassword())
            .roles("USER")
            .build();
            
        UserDetails admin = User.builder()
            .username(config.getSecurity().getAdminUsername())
            .password("{noop}" + config.getSecurity().getAdminPassword())
            .roles("ADMIN")
            .build();
            
        return new InMemoryUserDetailsManager(user, admin);
    }
}
```

#### Production Mode (`*-gcp` profiles)
```java
@Configuration
@Profile({"int-gcp", "pre-prod-gcp", "prod-gcp"})
public class ProdSecurityConfig {
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/", "/upload").hasAnyRole("USER", "ADMIN")
                .requestMatchers("/files/**", "/download/**").hasRole("ADMIN")
                .anyRequest().authenticated()
            )
            .oauth2Login()
            .oauth2Client();
        return http.build();
    }
}
```

**Azure AD configuration (application-*-gcp.yml):**
```yaml
spring:
  security:
    oauth2:
      client:
        registration:
          azure:
            client-id: ${AZURE_CLIENT_ID}
            client-secret: ${AZURE_CLIENT_SECRET}
            scope: openid, profile, email
        provider:
          azure:
            issuer-uri: https://login.microsoftonline.com/${AZURE_TENANT_ID}/v2.0
```

**Role mapping:** Extract roles from Azure AD groups claim and map to `USER` or `ADMIN` role.

### Pages and Navigation

**Pages:**
1. **Login** (`/login`) - Dev: basic auth form, Prod: Azure AD redirect
2. **Upload** (`/` or `/upload`) - Default landing page for all authenticated users
3. **File Listing** (`/files`) - Admin only, searchable file browser
4. **Error** (`/error`) - Friendly error messages

**Navbar (visible on all pages after login):**
- Logo/App name: "LuxBack"
- Upload (visible to USER and ADMIN)
- Files (visible to ADMIN only)
- Logout

**Access control:**
- Users attempting to access `/files` or `/download/*` get 403 Forbidden
- All pages require authentication
- Spring Security handles enforcement at controller level

### File Upload Flow

```
1. User selects file(s) in Dropzone UI
2. JavaScript initiates multipart upload to POST /upload
3. UploadController receives MultipartFile
4. Validate file size, type, filename
5. Generate storage filename: {timestamp}_{originalName}
6. Stream to StorageService.writeFile()
   - LocalStorageService: writes to /tmp/luxback/backups/{username}/
   - GcsStorageService: streams to gs://bucket/backups/{username}/
7. On success, append audit event to /audit-indexes/{username}.csv
8. Return success response to Dropzone
9. Dropzone shows success indicator
```

**Key implementation detail - Streaming:**
```java
@PostMapping("/upload")
public ResponseEntity<UploadResponse> handleFileUpload(
        @RequestParam("file") MultipartFile file,
        HttpServletRequest request) {
    
    String username = SecurityUtils.getCurrentUsername();
    
    // Validate without loading into memory
    if (file.getSize() > config.getMaxFileSize()) {
        throw new FileSizeExceededException();
    }
    
    // Generate storage path
    String storedFilename = fileMetadataService.generateStorageFilename(file.getOriginalFilename());
    String path = config.getStoragePath() + "/" + username + "/" + storedFilename;
    
    // Stream directly to storage - no intermediate buffering
    try (InputStream inputStream = file.getInputStream()) {
        storageService.writeFile(path, inputStream, file.getSize());
    }
    
    // Record audit event
    FileMetadata metadata = FileMetadata.builder()
        .originalFilename(file.getOriginalFilename())
        .storedFilename(storedFilename)
        .size(file.getSize())
        .contentType(file.getContentType())
        .build();
        
    auditService.recordUpload(username, metadata, request);
    
    return ResponseEntity.ok(new UploadResponse("success"));
}
```

This approach allows files larger than available RAM since we never load the entire file into memory.

### File Download Flow (Admin Only)

```
1. Admin searches/browses files in /files page
2. Clicks download link: /download/{username}/{storedFilename}
3. DownloadController checks ADMIN role (Spring Security)
4. Retrieve file from StorageService as InputStream
5. Stream to response with appropriate Content-Disposition header
6. Record download audit event in {username}.csv
7. File downloads in browser
```

**Implementation:**
```java
@GetMapping("/download/{username}/{filename}")
@PreAuthorize("hasRole('ADMIN')")
public ResponseEntity<StreamingResponseBody> downloadFile(
        @PathVariable String username,
        @PathVariable String filename,
        HttpServletRequest request) {
    
    String downloaderUsername = SecurityUtils.getCurrentUsername();
    String path = config.getStoragePath() + "/" + username + "/" + filename;
    
    // Get original filename from audit records
    String originalFilename = auditService.getOriginalFilename(username, filename);
    
    StreamingResponseBody stream = outputStream -> {
        try (InputStream inputStream = storageService.readFile(path)) {
            inputStream.transferTo(outputStream);
        }
    };
    
    // Record download after successful streaming
    auditService.recordDownload(username, originalFilename, filename, downloaderUsername, request);
    
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + originalFilename + "\"")
        .body(stream);
}
```

### File Listing and Search

**UI Features:**
- Table view: Filename (original), Uploaded by, Upload date, File size, Actions (Download)
- Search filters: Date range, filename (contains), uploaded by
- Pagination: 50 results per page
- Sorting: By upload date (default desc), filename, size

**Implementation:**
```java
@GetMapping("/files")
@PreAuthorize("hasRole('ADMIN')")
public String listFiles(
        @RequestParam(required = false) String filename,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
        @RequestParam(required = false) String username,
        @RequestParam(defaultValue = "0") int page,
        Model model) {
    
    SearchCriteria criteria = SearchCriteria.builder()
        .filename(filename)
        .startDate(startDate)
        .endDate(endDate)
        .username(username)
        .build();
    
    // Fast in-memory search across all cached audit events
    List<AuditEvent> results = auditService.searchAllAudit(criteria);
    
    // Paginate results
    int pageSize = 50;
    int start = page * pageSize;
    int end = Math.min(start + pageSize, results.size());
    List<AuditEvent> pageResults = results.subList(start, end);
    
    model.addAttribute("files", pageResults);
    model.addAttribute("totalPages", (results.size() + pageSize - 1) / pageSize);
    model.addAttribute("currentPage", page);
    model.addAttribute("criteria", criteria);
    
    return "file-listing";
}
```

**Performance characteristics:**
- Initial cache load: ~50ms (all 50 CSV files)
- Subsequent searches: <1ms (in-memory filtering)
- Cache invalidation: Per-user, on upload/download
- Memory footprint: ~2.5MB for year of audit data

## Audit Service Implementation

### Core Service

```java
@Service
public class AuditService {
    private static final String[] CSV_HEADERS = {
        "event_id", "event_type", "timestamp", "username", "filename", 
        "stored_as", "file_size", "content_type", "ip_address", 
        "user_agent", "session_id", "actor_username"
    };
    
    private final StorageService storage;
    private final LuxBackConfig config;
    private final ConcurrentHashMap<String, ReentrantLock> userLocks = new ConcurrentHashMap<>();
    
    // Per-user caching
    private final ConcurrentHashMap<String, List<AuditEvent>> perUserCache = new ConcurrentHashMap<>();
    
    public void recordUpload(String username, FileMetadata metadata, HttpServletRequest request) {
        ReentrantLock lock = userLocks.computeIfAbsent(username, k -> new ReentrantLock());
        lock.lock();
        try {
            appendAuditEvent(username, 
                UUID.randomUUID().toString(),
                "UPLOAD",
                Instant.now(),
                username,
                metadata.getOriginalFilename(),
                metadata.getStoredFilename(),
                metadata.getSize(),
                metadata.getContentType(),
                getClientIp(request),
                request.getHeader("User-Agent"),
                request.getSession().getId(),
                username // actor is uploader
            );
            perUserCache.remove(username); // Invalidate this user's cache
        } finally {
            lock.unlock();
        }
    }
    
    public void recordDownload(String fileOwner, String originalFilename, String storedFilename,
                              String downloaderUsername, HttpServletRequest request) {
        ReentrantLock lock = userLocks.computeIfAbsent(fileOwner, k -> new ReentrantLock());
        lock.lock();
        try {
            appendAuditEvent(fileOwner,
                UUID.randomUUID().toString(),
                "DOWNLOAD",
                Instant.now(),
                fileOwner,
                originalFilename,
                storedFilename,
                null, // file_size not needed for download
                null, // content_type not needed for download
                getClientIp(request),
                request.getHeader("User-Agent"),
                request.getSession().getId(),
                downloaderUsername // actor is downloader
            );
            perUserCache.remove(fileOwner); // Invalidate file owner's cache
        } finally {
            lock.unlock();
        }
    }
    
    private void appendAuditEvent(String username, Object... values) {
        String path = config.getAuditIndexPath() + "/" + username + ".csv";
        
        boolean fileExists = storage.exists(path);
        
        StringWriter sw = new StringWriter();
        try (CSVPrinter printer = new CSVPrinter(sw, CSVFormat.DEFAULT)) {
            if (!fileExists) {
                printer.printRecord((Object[]) CSV_HEADERS);
            }
            printer.printRecord(values);
        }
        
        if (fileExists) {
            storage.append(path, sw.toString());
        } else {
            storage.writeString(path, sw.toString());
        }
    }
    
    public List<AuditEvent> searchAllAudit(SearchCriteria criteria) {
        List<AuditEvent> allEvents = getAllUsernames().stream()
            .flatMap(username -> getUserEvents(username).stream())
            .collect(Collectors.toList());
        
        return allEvents.stream()
            .filter(event -> matchesCriteria(event, criteria))
            .sorted(Comparator.comparing(AuditEvent::getTimestamp).reversed())
            .collect(Collectors.toList());
    }
    
    private List<AuditEvent> getUserEvents(String username) {
        return perUserCache.computeIfAbsent(username, this::loadUserAudit);
    }
    
    private List<AuditEvent> loadUserAudit(String username) {
        String path = config.getAuditIndexPath() + "/" + username + ".csv";
        try {
            String csv = storage.readString(path);
            CSVParser parser = CSVParser.parse(csv, CSVFormat.DEFAULT.withFirstRecordAsHeader());
            return parser.stream()
                .map(this::recordToAuditEvent)
                .collect(Collectors.toList());
        } catch (IOException e) {
            log.error("Failed to read audit file for user: " + username, e);
            return Collections.emptyList();
        }
    }
    
    private AuditEvent recordToAuditEvent(CSVRecord record) {
        return AuditEvent.builder()
            .eventId(record.get("event_id"))
            .eventType(record.get("event_type"))
            .timestamp(Instant.parse(record.get("timestamp")))
            .username(record.get("username"))
            .filename(record.get("filename"))
            .storedAs(record.get("stored_as"))
            .fileSize(parseOptionalLong(record, "file_size"))
            .contentType(getOptional(record, "content_type"))
            .ipAddress(record.get("ip_address"))
            .userAgent(getOptional(record, "user_agent"))
            .sessionId(getOptional(record, "session_id"))
            .actorUsername(record.get("actor_username"))
            .build();
    }
    
    private String getOptional(CSVRecord record, String column) {
        try {
            return record.isMapped(column) ? record.get(column) : null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
    
    private Long parseOptionalLong(CSVRecord record, String column) {
        String value = getOptional(record, column);
        return (value == null || value.isEmpty()) ? null : Long.parseLong(value);
    }
    
    private boolean matchesCriteria(AuditEvent event, SearchCriteria criteria) {
        if (criteria.getFilename() != null && 
            !event.getFilename().toLowerCase().contains(criteria.getFilename().toLowerCase())) {
            return false;
        }
        if (criteria.getUsername() != null && 
            !event.getUsername().equals(criteria.getUsername())) {
            return false;
        }
        if (criteria.getStartDate() != null) {
            LocalDate eventDate = event.getTimestamp().atZone(ZoneId.systemDefault()).toLocalDate();
            if (eventDate.isBefore(criteria.getStartDate())) {
                return false;
            }
        }
        if (criteria.getEndDate() != null) {
            LocalDate eventDate = event.getTimestamp().atZone(ZoneId.systemDefault()).toLocalDate();
            if (eventDate.isAfter(criteria.getEndDate())) {
                return false;
            }
        }
        return true;
    }
    
    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty()) {
            ip = request.getRemoteAddr();
        }
        return ip;
    }
    
    private List<String> getAllUsernames() {
        return storage.listFiles(config.getAuditIndexPath())
            .stream()
            .filter(path -> path.endsWith(".csv"))
            .map(path -> path.substring(path.lastIndexOf("/") + 1, path.length() - 4))
            .collect(Collectors.toList());
    }
}
```

### Schema Evolution

CSV schema can evolve by **adding columns at the end**:

```csv
# Original schema
event_id,event_type,timestamp,username,filename,stored_as,file_size,content_type,ip_address,user_agent,session_id,actor_username

# After adding file_hash and correlation_id columns
event_id,event_type,timestamp,username,filename,stored_as,file_size,content_type,ip_address,user_agent,session_id,actor_username,file_hash,correlation_id
```

Old records will have empty values for new columns. Apache Commons CSV with header-based parsing handles this gracefully:

```java
private AuditEvent recordToAuditEvent(CSVRecord record) {
    return AuditEvent.builder()
        .eventId(record.get("event_id"))
        // ... existing fields ...
        .actorUsername(record.get("actor_username"))
        // New fields - gracefully handle missing columns
        .fileHash(getOptional(record, "file_hash"))
        .correlationId(getOptional(record, "correlation_id"))
        .build();
}
```

**Rules for safe schema evolution:**
- ✅ Add columns at the end
- ✅ Make new columns nullable/optional
- ✅ Handle missing/empty values in code
- ❌ Never rename existing columns
- ❌ Never remove existing columns
- ❌ Never change data type of existing columns

## Testing Strategy

### Unit Tests
```java
@SpringBootTest
class AuditServiceTest {
    @Autowired
    private AuditService auditService;
    
    @MockBean
    private StorageService storageService;
    
    @Test
    void testRecordUpload() {
        // Given
        MockHttpServletRequest request = new MockHttpServletRequest();
        FileMetadata metadata = FileMetadata.builder()
            .originalFilename("test.pdf")
            .storedFilename("2024-11-09T14-30-00_test.pdf")
            .size(1024L)
            .contentType("application/pdf")
            .build();
        
        // When
        auditService.recordUpload("joe.bloggs", metadata, request);
        
        // Then
        verify(storageService).append(contains("joe.bloggs.csv"), anyString());
    }
}
```

### Controller Tests (MockMVC)
```java
@WebMvcTest(UploadController.class)
@Import(SecurityConfig.class)
class UploadControllerTest {
    @Autowired
    private MockMvc mockMvc;
    
    @MockBean
    private StorageService storageService;
    
    @MockBean
    private AuditService auditService;
    
    @Test
    @WithMockUser(username = "user", roles = "USER")
    void testFileUpload() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
            "file",
            "test.pdf",
            "application/pdf",
            "PDF content".getBytes()
        );
        
        mockMvc.perform(multipart("/upload")
                .file(file)
                .with(csrf()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("success"));
        
        verify(storageService).writeFile(anyString(), any(InputStream.class), anyLong());
        verify(auditService).recordUpload(eq("user"), any(FileMetadata.class), any());
    }
    
    @Test
    @WithMockUser(username = "user", roles = "USER")
    void testFileTooLarge() throws Exception {
        byte[] largeContent = new byte[101 * 1024 * 1024]; // 101MB
        MockMultipartFile file = new MockMultipartFile(
            "file",
            "large.pdf",
            "application/pdf",
            largeContent
        );
        
        mockMvc.perform(multipart("/upload")
                .file(file)
                .with(csrf()))
            .andExpect(status().isBadRequest());
    }
    
    @Test
    @WithMockUser(username = "user", roles = "USER")
    void testUserCannotAccessFileList() throws Exception {
        mockMvc.perform(get("/files"))
            .andExpect(status().isForbidden());
    }
    
    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testAdminCanAccessFileList() throws Exception {
        mockMvc.perform(get("/files"))
            .andExpect(status().isOk())
            .andExpect(view().name("file-listing"));
    }
}
```

### Integration Tests
```java
@SpringBootTest
@ActiveProfiles("dev-local")
@AutoConfigureMockMvc
class LuxBackIntegrationTest {
    @Autowired
    private MockMvc mockMvc;
    
    @Autowired
    private StorageService storageService;
    
    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testFullUploadAndDownloadFlow() throws Exception {
        // Upload
        MockMultipartFile file = new MockMultipartFile(
            "file", "document.pdf", "application/pdf", "content".getBytes()
        );
        
        mockMvc.perform(multipart("/upload")
                .file(file)
                .with(csrf()))
            .andExpect(status().isOk());
        
        // Verify audit entry created
        String auditPath = "/tmp/luxback/audit-indexes/admin.csv";
        String auditContent = storageService.readString(auditPath);
        assertThat(auditContent).contains("UPLOAD");
        assertThat(auditContent).contains("document.pdf");
        
        // Search for file
        mockMvc.perform(get("/files")
                .param("filename", "document"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("document.pdf")));
        
        // Download file
        mockMvc.perform(get("/download/admin/2024-11-09T14-30-00_document.pdf"))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Disposition", 
                containsString("document.pdf")));
        
        // Verify download audit entry
        auditContent = storageService.readString(auditPath);
        assertThat(auditContent).contains("DOWNLOAD");
    }
}
```

### Test Coverage Targets
- Unit tests: 80%+ coverage of service logic
- Controller tests: All endpoints, all roles, all error cases
- Integration tests: Critical user journeys (upload, search, download)
- Security tests: Access control for USER and ADMIN roles

## Dependencies

```xml
<dependencies>
    <!-- Spring Boot -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-security</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-oauth2-client</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-thymeleaf</artifactId>
    </dependency>
    <dependency>
        <groupId>org.thymeleaf.extras</groupId>
        <artifactId>thymeleaf-extras-springsecurity6</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-actuator</artifactId>
    </dependency>
    
    <!-- GCS -->
    <dependency>
        <groupId>com.google.cloud</groupId>
        <artifactId>spring-cloud-gcp-starter-storage</artifactId>
    </dependency>
    
    <!-- CSV -->
    <dependency>
        <groupId>org.apache.commons</groupId>
        <artifactId>commons-csv</artifactId>
        <version>1.10.0</version>
    </dependency>
    
    <!-- WebJars for UI -->
    <dependency>
        <groupId>org.webjars</groupId>
        <artifactId>bootstrap</artifactId>
        <version>5.3.0</version>
    </dependency>
    <dependency>
        <groupId>org.webjars.npm</groupId>
        <artifactId>dropzone</artifactId>
        <version>6.0.0-beta.2</version>
    </dependency>
    
    <!-- Testing -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-test</artifactId>
        <scope>test</scope>
    </dependency>
    <dependency>
        <groupId>org.springframework.security</groupId>
        <artifactId>spring-security-test</artifactId>
        <scope>test</scope>
    </dependency>
</dependencies>
```

## Performance Characteristics

### Storage
- **File upload**: Streaming - supports files larger than RAM
- **File download**: Streaming - no memory buffering
- **Throughput**: Limited by network and GCS, not application

### Audit System
- **Append operation**: O(1) - append to CSV file
- **Per-user locking**: Only blocks concurrent uploads by same user
- **Cache load**: 50 files × ~1ms = ~50ms total
- **Search**: O(n) where n = total events, but n is small (~5000 events/year for 50 users)
- **Memory**: ~2.5MB for full year of audit data

### Scalability Limits
With current architecture:
- **Users**: 50 (designed for)
- **Growth headroom**: Could handle 200-300 users before needing architecture changes
- **Events**: Millions (CSV parsing is fast enough)
- **Files**: Unlimited (storage-limited, not app-limited)

**When to evolve:**
- \>300 users: Consider database for audit (Postgres, BigQuery)
- \>10GB audit data: Consider columnar format (Parquet) or database
- High concurrency: Current per-user locking is fine; shared database would introduce different bottlenecks

## Security Considerations

### Authentication
- **Dev**: Basic auth with test credentials (not for production data)
- **Prod**: Azure AD OAuth2 with MFA (handled by Azure)

### Authorization
- **Role-based access control** via Spring Security
- **Method-level security** with `@PreAuthorize` annotations
- **URL-based rules** in SecurityConfig
- **Roles extracted from Azure AD groups**

### Data Protection
- **In transit**: HTTPS enforced by CloudRun
- **At rest**: GCS encryption (Google-managed keys)
- **Audit trail**: Immutable append-only logs

### Input Validation
- **File size limits**: Configurable max size (default 100MB)
- **File type whitelist**: Configurable allowed MIME types
- **Filename sanitization**: Remove path traversal characters
- **CSRF protection**: Enabled by default in Spring Security

### Additional Hardening
- **Rate limiting**: Consider adding Spring rate limiting if needed
- **Session management**: Configure session timeout in application.yml
- **Security headers**: Add via Spring Security (CSP, X-Frame-Options, etc.)
- **Audit log protection**: Read-only access, no deletion capability

## Error Handling

### User-Facing Errors
- **File too large**: "File exceeds maximum size of 100MB"
- **Invalid file type**: "File type not allowed. Supported types: PDF, Excel, Text"
- **Upload failed**: "Upload failed. Please try again or contact support."
- **Access denied**: "You do not have permission to access this page."
- **File not found**: "The requested file could not be found."

### Technical Errors
- **Storage failures**: Log error, show generic message to user
- **Audit write failures**: Implement compensation (delete uploaded file if audit fails)
- **Authentication failures**: Redirect to login with error message
- **Unexpected exceptions**: Show error page with correlation ID for support

### Error Page (error.html)
```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org">
<head>
    <title>Error - LuxBack</title>
    <link rel="stylesheet" th:href="@{/webjars/bootstrap/5.3.0/css/bootstrap.min.css}"/>
</head>
<body>
<div class="container mt-5">
    <h1>Oops! Something went wrong</h1>
    <p th:text="${message}">An unexpected error occurred.</p>
    <p><a th:href="@{/}" class="btn btn-primary">Return to Upload</a></p>
</div>
</body>
</html>
```

## Monitoring and Observability

### Spring Boot Actuator
Enable health and info endpoints:

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics
  endpoint:
    health:
      show-details: when-authorized
```

### Custom Metrics
- Upload success/failure count
- Upload duration histogram
- File size distribution
- Search query count and duration
- Cache hit/miss ratio

### Logging
```yaml
logging:
  level:
    com.lbg.markets.luxback: INFO
    org.springframework.security: DEBUG # In dev only
  pattern:
    console: "%d{ISO8601} [%thread] %-5level %logger{36} - %msg%n"
```

### CloudRun Integration
- **Stdout/stderr**: Captured by Cloud Logging
- **Structured logging**: Use JSON formatter for production
- **Correlation IDs**: Add to MDC for request tracing
- **Error reporting**: Integrate with Cloud Error Reporting

## Deployment Considerations

### Containerization (Handled by other team)
Expected Dockerfile structure:
```dockerfile
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY target/luxback-*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

### Environment Variables
```bash
# Profile selection
SPRING_PROFILES_ACTIVE=prod-gcp

# Azure AD (prod only)
AZURE_CLIENT_ID=xxx
AZURE_CLIENT_SECRET=xxx
AZURE_TENANT_ID=xxx

# GCS (handled by CloudRun service account)
# No explicit credentials needed - uses default credentials
```

### CloudRun Configuration
- **Memory**: 512MB (sufficient for caching)
- **CPU**: 1 vCPU
- **Max instances**: 10 (for 50 users, likely 1-2 active)
- **Timeout**: 300s (for large file uploads)
- **Concurrency**: 80 (default)

### Health Checks
- **Startup probe**: `/actuator/health` (first 60s)
- **Liveness probe**: `/actuator/health` (every 30s)
- **Readiness probe**: `/actuator/health` (every 10s)

## Future Enhancements (Not in Scope)

Potential improvements if requirements change:
- **Virus scanning**: Integrate ClamAV or cloud scanner before writing to storage
- **File preview**: Generate thumbnails for images/PDFs
- **Bulk download**: Zip multiple files for download
- **File expiration**: Auto-delete files after retention period
- **Email notifications**: Alert on upload completion for large files
- **File versioning**: Keep multiple versions of same filename
- **Collaboration**: Share files with specific users
- **Advanced search**: Full-text search in file contents
- **Analytics dashboard**: Upload trends, storage usage charts
- **API access**: REST API for programmatic uploads

## Open Questions / To Be Decided

1. **File retention policy**: Keep forever or auto-delete after X days/months?
2. **Max file size**: Current suggestion 100MB, adjust based on use case?
3. **Allowed file types**: Which MIME types to whitelist?
4. **Azure AD group mapping**: Which AD groups map to USER vs ADMIN roles?
5. **File naming conflicts**: Current strategy is timestamp prefix - acceptable?
6. **Download links**: Should they be time-limited signed URLs or direct?
7. **Audit log retention**: Keep audit CSVs forever or archive old ones?

## Summary

LuxBack implements a pragmatic, simple file backup solution perfectly suited to its scale (50 users, low traffic). Key design principles:

- **No database overhead** - CSV files + in-memory caching handles the load
- **Clean abstractions** - Interface-based storage allows easy testing and deployment flexibility
- **Centralized configuration** - Single config class eliminates scattered @Value annotations
- **Profile-based behavior** - Dev and prod modes use appropriate auth and storage
- **Audit by design** - Comprehensive logging with efficient per-user partitioning
- **Performance appropriate** - In-memory caching makes admin searches instant
- **Simple to maintain** - Flat CSV structure, standard tools, no complex infrastructure

The architecture is designed to be understood and maintained years from now, following the principle that "the number one feature is simplicity."