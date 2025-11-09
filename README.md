# LuxBack - File Backup Service

## Project Status

✅ **Implementation Complete** - All core features implemented and tested

**Current Version:** 0.0.1-SNAPSHOT  
**Last Updated:** November 2024  
**Status:** Ready for deployment and testing

### What's Implemented

- ✅ File upload with drag-and-drop UI (Dropzone.js)
- ✅ Admin file browser with search and pagination
- ✅ File download with streaming (no memory buffering)
- ✅ CSV-based audit logging with per-user caching
- ✅ Profile-based configuration (dev-local, int-gcp, pre-prod-gcp, prod-gcp)
- ✅ Role-based access control (USER, ADMIN)
- ✅ Local filesystem storage (dev mode)
- ✅ Google Cloud Storage integration (production modes)
- ✅ Azure AD OAuth2 authentication (production modes)
- ✅ Basic authentication (dev mode)
- ✅ Comprehensive unit and integration tests
- ✅ Bootstrap Flatly theme UI
- ✅ Custom error handling
- ✅ CSRF protection
- ✅ Audit trail with IP tracking

### Documentation

- **[README.md](README.md)** - This file: Architecture and design decisions (you are here)
- **[DEVELOPER_GUIDE.md](DEVELOPER_GUIDE.md)** - Developer setup, API documentation, and usage
- **[AZURE_AD_SETUP.md](AZURE_AD_SETUP.md)** - Step-by-step Azure AD configuration
- **[DEPLOYMENT.md](DEPLOYMENT.md)** - Docker and CloudRun deployment guide
- **[INSTALLATION_GUIDE.md](INSTALLATION_GUIDE.md)** - File placement instructions

### Quick Start

```bash
# Run locally with dev profile
mvn spring-boot:run -Dspring-boot.run.profiles=dev-local

# Access at http://localhost:8080
# User login: user / userpass
# Admin login: admin / adminpass
```

---

## Overview

LuxBack is a Spring Boot 3 file backup service that allows users to securely upload files to Google Cloud Storage (or local disk in development) with comprehensive audit logging. The system supports approximately 50 users with low traffic patterns.

**Artifact:** `com.lbg.markets.luxback`  
**Deployment:** CloudRun (containerized)  
**UI Framework:** Thymeleaf with Bootstrap (Bootswatch Flatly theme)  
**Upload Component:** Dropzone.js  
**Java Version:** 21  
**Spring Boot Version:** 3.5.7

## Core Design Philosophy

The design prioritizes **simplicity and pragmatism** over complexity:

- **No database** - With 50 users and low traffic, CSV files and in-memory caching are sufficient
- **Clean separation** - Interface-based abstractions allow profile-specific implementations
- **Flat data model** - Simple CSV schema with append-only operations
- **Natural partitioning** - Per-user files eliminate cross-user contention
- **Efficient caching** - Entire audit history loads into memory (~2.5MB for a year)

As the original requirement stated: **"the number one feature is simplicity."**

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

**Implementation:** `AuditService.java` with concurrent per-user caching

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

**Decision:** Load entire audit history into memory with per-user caching and invalidation.

**Implementation:**
```java
// Per-user cache with automatic invalidation on write
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
- Per-user caching minimizes reload overhead on writes

### 4. Profile-Based Storage Abstraction

**Decision:** Use interface-based storage with profile-specific implementations.

**Implementations:**
- `LocalStorageService` - Active in `dev-local` profile
- `GcsStorageService` - Active in `int-gcp`, `pre-prod-gcp`, `prod-gcp` profiles

**Interface:**
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

**Implementation:**
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
}
```

**Configuration hierarchy:**
```
application.yml              # Base configuration
├── application-dev-local.yml    # Dev overrides
├── application-int-gcp.yml      # Integration overrides
├── application-pre-prod-gcp.yml # Pre-prod overrides
└── application-prod-gcp.yml     # Production overrides
```

**Rationale:**
- Single injection point - inject `LuxBackConfig` where needed
- Type-safe configuration access with IDE autocomplete
- Easy to see all configuration in one place
- No magic strings scattered through codebase

## Architecture

### Module Structure

```
com.lbg.markets.luxback
├── config
│   ├── DevSecurityConfig           # Basic auth (dev mode)
│   ├── ProdSecurityConfig          # Azure AD OAuth2 (prod modes)
│   ├── OAuth2AuthoritiesMapper     # Map Azure AD groups to roles
│   ├── WebConfig                   # Multipart & static resources
│   └── LuxBackConfig               # Application properties
├── controller
│   ├── LoginController             # Login page
│   ├── UploadController            # File upload (streaming)
│   ├── FileListingController       # Admin file browser
│   ├── DownloadController          # Admin file download
│   └── CustomErrorController       # Error handling
├── service
│   ├── StorageService              # Storage interface
│   ├── LocalStorageService         # @Profile("dev-local")
│   ├── GcsStorageService           # @Profile({"int-gcp", "pre-prod-gcp", "prod-gcp"})
│   ├── AuditService                # CSV audit with caching
│   └── FileMetadataService         # File naming & validation
├── security
│   ├── Role                        # Enum: USER, ADMIN
│   ├── SecurityUtils               # Get current user
│   └── CustomAccessDeniedHandler   # 403 handler
├── model
│   ├── AuditEvent                  # Single audit record
│   ├── FileMetadata                # Upload metadata
│   └── SearchCriteria              # Filter parameters
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

## Security Model

### Two Authentication Modes

#### Development Mode (`dev-local` profile)
- HTTP Basic Authentication
- In-memory user store
- Test credentials from configuration
- Form-based login page

**Configuration:**
```yaml
luxback:
  security:
    dev-username: user
    dev-password: userpass
    admin-username: admin
    admin-password: adminpass
```

#### Production Mode (`*-gcp` profiles)
- Azure AD OAuth2 with OpenID Connect
- Group-based role mapping
- MFA handled by Azure AD
- Single sign-on support

**Environment variables required:**
```bash
AZURE_CLIENT_ID=...
AZURE_CLIENT_SECRET=...
AZURE_TENANT_ID=...
AZURE_ADMIN_GROUP_ID=...
AZURE_USER_GROUP_ID=...
```

See [AZURE_AD_SETUP.md](AZURE_AD_SETUP.md) for detailed setup instructions.

### Role-Based Access Control

**USER Role:**
- Access upload page (`/`, `/upload`)
- Upload files to their own storage area
- Cannot view or download others' files

**ADMIN Role:**
- All USER permissions
- Access file browser (`/files`)
- Search and filter all files
- Download any user's files
- All actions are audited

**Enforcement:**
- Method-level: `@PreAuthorize("hasRole('ADMIN')")`
- URL-level: Security filter chain configuration
- Template-level: Thymeleaf security expressions

## Pages and User Interface

### Pages

1. **Login** (`/login`)
   - Dev: Basic auth form with username/password
   - Prod: Azure AD OAuth2 redirect

2. **Upload** (`/`, `/upload`)
   - Drag-and-drop file upload (Dropzone.js)
   - File size and type validation
   - Upload progress indicators
   - Accessible to USER and ADMIN

3. **File Browser** (`/files`)
   - Searchable table of all files
   - Filter by filename, username, date range
   - Pagination (50 results per page)
   - Download links for each file
   - Admin only

4. **Error** (`/error`)
   - User-friendly error messages
   - Custom handling for 403, 404, 500 errors
   - Navigation back to upload page

### UI Theme

- **Framework:** Bootstrap 5.3.0
- **Theme:** Bootswatch Flatly
- **Icons:** Bootstrap Icons
- **Colors:** Primary #18bc9c, Secondary #2c3e50
- **Responsive:** Mobile-friendly design

## File Upload Flow

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

The implementation never loads entire files into memory. Files are streamed directly from the multipart request to storage using `MultipartFile.getInputStream()` and `StorageService.writeFile()`. This allows files larger than available RAM to be uploaded successfully.

```java
// Stream directly to storage - no intermediate buffering
try (InputStream inputStream = file.getInputStream()) {
    storageService.writeFile(path, inputStream, file.getSize());
}
```

## File Download Flow (Admin Only)

```
1. Admin searches/browses files in /files page
2. Clicks download link: /download/{username}/{storedFilename}
3. DownloadController checks ADMIN role (Spring Security)
4. Retrieve file from StorageService as InputStream
5. Stream to response with Content-Disposition header
6. Record download audit event in {username}.csv
7. File downloads in browser
```

**Streaming implementation:**

Downloads also use streaming to avoid memory issues:

```java
StreamingResponseBody stream = outputStream -> {
    try (InputStream inputStream = storageService.readFile(path)) {
        inputStream.transferTo(outputStream);
    }
};

return ResponseEntity.ok()
    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + originalFilename + "\"")
    .body(stream);
```

## Audit Service Implementation

### Core Features

- **Per-user CSV files** - One file per user for natural partitioning
- **Append-only operations** - New events appended without reading entire file
- **Concurrent per-user locks** - Prevent write conflicts, allow cross-user parallelism
- **Lazy loading with caching** - Load on first access, cache until invalidation
- **Cache invalidation** - Per-user invalidation on write operations

### Search Performance

- **Initial load:** ~50ms to load all 50 CSV files
- **Subsequent searches:** <1ms using in-memory filtering
- **Memory footprint:** ~2.5MB for year of audit data (50 users)
- **Filtering:** Full power of Java Streams (date ranges, text contains, etc.)

### Schema Evolution

CSV schema can evolve by **adding columns at the end**:

```csv
# Original schema
event_id,event_type,timestamp,username,filename,stored_as,file_size,content_type,ip_address,user_agent,session_id,actor_username

# After adding new columns
event_id,event_type,timestamp,username,filename,stored_as,file_size,content_type,ip_address,user_agent,session_id,actor_username,file_hash,correlation_id
```

Old records will have empty values for new columns. Apache Commons CSV with header-based parsing handles this gracefully.

**Rules for safe schema evolution:**
- ✅ Add columns at the end
- ✅ Make new columns nullable/optional
- ✅ Handle missing/empty values in code
- ❌ Never rename existing columns
- ❌ Never remove existing columns
- ❌ Never change data type of existing columns

## Testing

### Test Coverage

The application includes comprehensive testing:

**Unit Tests:**
- `AuditServiceTest` - CSV operations, caching, search
- `FileMetadataServiceTest` - Filename generation, validation
- `LocalStorageServiceTest` - File operations with temp directories

**Controller Tests (MockMVC):**
- `UploadControllerTest` - File upload, validation, CSRF
- `FileListingControllerTest` - Search, pagination, access control
- `DownloadControllerTest` - Streaming download, admin access

**Integration Tests:**
- `LuxbackIntegrationTest` - End-to-end user journeys
- Multi-user scenarios
- Full upload → search → download flow
- Security and access control
- CSRF protection
- Validation (file size, file type)

### Running Tests

```bash
# Run all tests
mvn test

# Run with coverage
mvn test jacoco:report

# Run integration tests only
mvn verify -Dtest=*IntegrationTest

# Run specific test class
mvn test -Dtest=AuditServiceTest
```

## Dependencies

### Core Dependencies

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
    
    <!-- GCS -->
    <dependency>
        <groupId>com.google.cloud</groupId>
        <artifactId>google-cloud-storage</artifactId>
        <version>2.59.0</version>
    </dependency>
    
    <!-- CSV -->
    <dependency>
        <groupId>org.apache.commons</groupId>
        <artifactId>commons-csv</artifactId>
        <version>1.10.0</version>
    </dependency>
    
    <!-- WebJars -->
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
</dependencies>
```

## Performance Characteristics

### Storage
- **File upload:** Streaming - supports files larger than RAM
- **File download:** Streaming - no memory buffering
- **Throughput:** Limited by network and GCS, not application

### Audit System
- **Append operation:** O(1) - append to CSV file
- **Per-user locking:** Only blocks concurrent uploads by same user
- **Cache load:** 50 files × ~1ms = ~50ms total
- **Search:** O(n) where n = total events, but n is small (~5000 events/year)
- **Memory:** ~2.5MB for full year of audit data

### Scalability Limits

With current architecture:
- **Users:** 50 (designed for)
- **Growth headroom:** Could handle 200-300 users before architecture changes needed
- **Events:** Millions (CSV parsing is fast enough)
- **Files:** Unlimited (storage-limited, not app-limited)

**When to evolve:**
- \>300 users: Consider database for audit (Postgres, BigQuery)
- \>10GB audit data: Consider columnar format (Parquet) or database
- High concurrency: Current per-user locking is fine

## Security Considerations

### Authentication
- **Dev:** Basic auth with test credentials (not for production data)
- **Prod:** Azure AD OAuth2 with MFA (handled by Azure)

### Authorization
- **Role-based access control** via Spring Security
- **Method-level security** with `@PreAuthorize` annotations
- **URL-based rules** in SecurityConfig
- **Roles extracted from Azure AD groups**

### Data Protection
- **In transit:** HTTPS enforced by CloudRun
- **At rest:** GCS encryption (Google-managed keys)
- **Audit trail:** Immutable append-only logs

### Input Validation
- **File size limits:** Configurable max size (default 100MB)
- **File type whitelist:** Configurable allowed MIME types
- **Filename sanitization:** Remove path traversal characters
- **CSRF protection:** Enabled by default in Spring Security

### Additional Hardening
- **Session management:** Secure session cookies
- **Security headers:** CSP, X-Frame-Options, etc.
- **Audit log protection:** Read-only access, no deletion capability
- **IP tracking:** X-Forwarded-For support for CloudRun

## Error Handling

### User-Facing Errors
- **File too large:** "File exceeds maximum size of 100MB"
- **Invalid file type:** "File type not allowed. Supported types: PDF, Excel, Text"
- **Upload failed:** "Upload failed. Please try again or contact support."
- **Access denied:** "You do not have permission to access this page."
- **File not found:** "The requested file could not be found."

### Technical Errors
- **Storage failures:** Log error, show generic message to user
- **Audit write failures:** Log error, ensure file was written successfully
- **Authentication failures:** Redirect to login with error message
- **Unexpected exceptions:** Show error page with HTTP status

### Custom Error Controller

The `CustomErrorController` provides user-friendly error pages for common HTTP status codes while maintaining security by not exposing internal error details.

## Monitoring and Observability

### Spring Boot Actuator

Enabled endpoints:
- `/actuator/health` - Health check (all environments)
- `/actuator/info` - Application info (non-prod)
- `/actuator/metrics` - Metrics (non-prod)

**Configuration by environment:**
- **dev-local:** All endpoints exposed, detailed health
- **int-gcp:** Health, info, metrics (authorized details)
- **pre-prod-gcp:** Health, info (authorized details)
- **prod-gcp:** Health only (minimal details)

### Logging

**Log levels by environment:**
- **dev-local:** DEBUG for app, DEBUG for security
- **int-gcp:** INFO for app, INFO for security
- **pre-prod-gcp:** INFO for app, WARN for security
- **prod-gcp:** WARN for app, WARN for security

**Structured logging:**
- ISO8601 timestamps
- Thread information
- Logger name
- Log level
- Message

### CloudRun Integration
- **Stdout/stderr:** Captured by Cloud Logging
- **Health checks:** Automatic via `/actuator/health`
- **Metrics:** Exported to Cloud Monitoring

## Deployment

### Environments

| Environment | Profile | Storage | Authentication | Purpose |
|-------------|---------|---------|----------------|---------|
| Local Dev | `dev-local` | Local filesystem | Basic auth | Development and testing |
| Integration | `int-gcp` | GCS (luxback-int) | Azure AD | Integration testing |
| Pre-Production | `pre-prod-gcp` | GCS (luxback-preprod) | Azure AD | Staging and UAT |
| Production | `prod-gcp` | GCS (luxback-prod) | Azure AD | Live production |

### Quick Deployment

**Local:**
```bash
mvn spring-boot:run -Dspring-boot.run.profiles=dev-local
```

**Docker:**
```bash
docker build -t luxback:latest .
docker run -p 8080:8080 -e SPRING_PROFILES_ACTIVE=dev-local luxback:latest
```

**CloudRun:**
```bash
gcloud run deploy luxback-prod \
  --source . \
  --platform managed \
  --region us-central1 \
  --set-env-vars SPRING_PROFILES_ACTIVE=prod-gcp
```

See [DEPLOYMENT.md](DEPLOYMENT.md) for complete deployment instructions.

## Configuration

### Environment Variables

**Production (GCP) - Required:**
```bash
SPRING_PROFILES_ACTIVE=prod-gcp
AZURE_CLIENT_ID=<azure-app-client-id>
AZURE_CLIENT_SECRET=<azure-app-client-secret>
AZURE_TENANT_ID=<azure-tenant-id>
AZURE_ADMIN_GROUP_ID=<admin-group-object-id>
AZURE_USER_GROUP_ID=<user-group-object-id>
```

**Development - Built into configuration:**
- User: `user` / `userpass`
- Admin: `admin` / `adminpass`

### Application Properties

**File size limit:**
```yaml
luxback:
  max-file-size: 104857600  # 100MB in bytes
```

**Allowed file types:**
```yaml
luxback:
  allowed-content-types:
    - application/pdf
    - application/vnd.ms-excel
    - application/vnd.openxmlformats-officedocument.spreadsheetml.sheet
    - text/plain
    - text/csv
    - image/png
    - image/jpeg
```

**Storage paths:**
```yaml
# Dev
luxback:
  storage-path: /tmp/luxback/backups
  audit-index-path: /tmp/luxback/audit-indexes

# Production
luxback:
  storage-path: gs://luxback-prod/backups
  audit-index-path: gs://luxback-prod/audit-indexes
```

## Future Enhancements (Not in Current Scope)

Potential improvements if requirements change:
- **Virus scanning:** Integrate ClamAV or cloud scanner
- **File preview:** Generate thumbnails for images/PDFs
- **Bulk download:** Zip multiple files
- **File expiration:** Auto-delete after retention period
- **Email notifications:** Alert on upload completion
- **File versioning:** Keep multiple versions of same filename
- **Collaboration:** Share files with specific users
- **Advanced search:** Full-text search in file contents
- **Analytics dashboard:** Upload trends, storage usage
- **API access:** REST API for programmatic uploads
- **File encryption:** Client-side encryption before upload

## Known Limitations

1. **User management:** Users must be managed in Azure AD (no self-registration)
2. **File retention:** No automatic deletion or archiving
3. **Search scope:** Search limited to audit metadata (not file contents)
4. **Download history:** Can see who downloaded, but no download frequency metrics
5. **Bulk operations:** No bulk delete or bulk download
6. **File sharing:** No mechanism to share files between users

## Open Questions / Decisions Needed

1. **File retention policy:** Keep forever or auto-delete after X days/months?
2. **Max file size:** Current limit 100MB - adjust based on use case?
3. **Allowed file types:** Current whitelist sufficient or needs expansion?
4. **Azure AD group mapping:** Confirmed group Object IDs for USER and ADMIN roles?
5. **Audit log retention:** Keep audit CSVs forever or archive old ones?
6. **Backup strategy:** How to backup GCS buckets and audit logs?
7. **Monitoring alerts:** What thresholds for alerting (errors, storage usage, etc.)?

## Support and Troubleshooting

### Common Issues

**"File exceeds maximum size"**
- Check `luxback.max-file-size` in configuration
- Current limit is 100MB (104857600 bytes)
- Increase if needed for your use case

**"Access Denied" Error**
- Verify user role (USER vs ADMIN)
- Admin-only endpoints: `/files`, `/download/*`
- Check Azure AD group membership in production

**Azure AD Login Fails**
- Verify environment variables are set correctly
- Check redirect URI matches exactly
- Ensure app registration has correct permissions
- Verify user is member of configured groups

**Files Not Appearing in Browser**
- Only UPLOAD events shown (not downloads)
- Check audit CSV file exists for that user
- Verify cache is loading correctly (check logs)

### Getting Help

1. Check [DEVELOPER_GUIDE.md](DEVELOPER_GUIDE.md) for detailed usage
2. Check [AZURE_AD_SETUP.md](AZURE_AD_SETUP.md) for auth issues
3. Review application logs for errors
4. Check audit CSV files directly for data issues

## License

Proprietary - Internal use only

## Summary

LuxBack implements a pragmatic, simple file backup solution perfectly suited to its scale (50 users, low traffic). Key principles:

- **No database overhead** - CSV files + in-memory caching handles the load
- **Clean abstractions** - Interface-based storage allows easy testing and deployment flexibility
- **Centralized configuration** - Single config class eliminates scattered @Value annotations
- **Profile-based behavior** - Dev and prod modes use appropriate auth and storage
- **Audit by design** - Comprehensive logging with efficient per-user partitioning
- **Performance appropriate** - In-memory caching makes admin searches instant
- **Simple to maintain** - Flat CSV structure, standard tools, no complex infrastructure

The architecture is designed to be understood and maintained years from now, following the principle that **"the number one feature is simplicity."**

---

**Built with:** Spring Boot 3.5.7 | Java 21 | Bootstrap 5 | Thymeleaf | Google Cloud Storage
