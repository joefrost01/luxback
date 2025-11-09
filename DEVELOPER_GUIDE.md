# LuxBack - Developer Guide

## Quick Start

### Prerequisites
- Java 21 or higher
- Maven 3.8+
- (Optional) Google Cloud SDK for GCP deployment

### Running Locally

1. **Clone and build:**
```bash
mvn clean package
```

2. **Run with dev profile:**
```bash
mvn spring-boot:run -Dspring-boot.run.profiles=dev-local
```

3. **Access the application:**
- URL: http://localhost:8080
- User login: `user` / `userpass`
- Admin login: `admin` / `adminpass`

The application will create local directories at:
- `/tmp/luxback/backups/` - File storage
- `/tmp/luxback/audit-indexes/` - Audit logs (CSV files)

## Application Structure

### Architecture Overview

```
┌─────────────────┐
│   Controllers   │  ← HTTP endpoints, Thymeleaf views
├─────────────────┤
│    Services     │  ← Business logic, audit, storage
├─────────────────┤
│  Storage Layer  │  ← Profile-based: Local or GCS
└─────────────────┘
```

### Key Design Patterns

1. **Interface-based Storage** - `StorageService` interface with profile-specific implementations
2. **Profile-based Configuration** - Different behavior for dev vs prod
3. **Streaming I/O** - Files never loaded entirely into memory
4. **Per-user Caching** - Audit data cached per user for performance
5. **Append-only Audit** - CSV files with per-user locking

## Profiles

### dev-local (Development)
- Local filesystem storage (`/tmp/luxback/`)
- Basic authentication (username/password)
- Debug logging enabled
- Actuator endpoints fully exposed

**Activate:** `--spring.profiles.active=dev-local`

### int-gcp (Integration)
- GCS storage (`gs://luxback-int/`)
- Azure AD OAuth2 authentication
- Info-level logging
- Health and metrics endpoints exposed

**Required environment variables:**
```bash
AZURE_CLIENT_ID=your-client-id
AZURE_CLIENT_SECRET=your-secret
AZURE_TENANT_ID=your-tenant-id
```

**Activate:** `--spring.profiles.active=int-gcp`

### pre-prod-gcp (Pre-Production)
- GCS storage (`gs://luxback-preprod/`)
- Azure AD OAuth2 authentication
- Info-level logging
- Health endpoint only

**Activate:** `--spring.profiles.active=pre-prod-gcp`

### prod-gcp (Production)
- GCS storage (`gs://luxback-prod/`)
- Azure AD OAuth2 authentication
- Warning-level logging
- Health endpoint only (minimal details)

**Activate:** `--spring.profiles.active=prod-gcp`

## Configuration

### Application Properties

All configuration is centralized in `LuxBackConfig.java` and bound from YAML files.

**Key settings:**
```yaml
luxback:
  storage-path: /tmp/luxback/backups        # Base path for files
  audit-index-path: /tmp/luxback/audit-indexes  # Base path for audit CSVs
  max-file-size: 104857600                  # 100MB in bytes
  allowed-content-types:                    # MIME type whitelist
    - application/pdf
    - application/vnd.ms-excel
    - text/plain
    - image/png
  security:
    dev-username: user                      # Dev mode username
    dev-password: userpass                  # Dev mode password
    admin-username: admin                   # Admin username
    admin-password: adminpass               # Admin password
```

### Customizing File Size Limits

Edit in `application-dev-local.yaml` (or other profile YAML):
```yaml
luxback:
  max-file-size: 524288000  # 500MB
```

### Adding Allowed File Types

Add MIME types to the whitelist:
```yaml
luxback:
  allowed-content-types:
    - application/pdf
    - application/zip
    - video/mp4
```

## User Roles

### USER Role
- Can access upload page (`/`, `/upload`)
- Can upload files to their own storage area
- Cannot view or download others' files

### ADMIN Role
- Has all USER permissions
- Can access file browser (`/files`)
- Can search and filter all files
- Can download any user's files
- All downloads are audited

## File Organization

### Storage Layout
```
/tmp/luxback/backups/           # Root storage path
  /joe.bloggs/                  # Per-user directory
    /2024-11-09T14-30-00_document.pdf
    /2024-11-09T15-45-12_report.xlsx
  /jane.smith/
    /2024-11-10T09-00-00_data.csv
```

### Audit Layout
```
/tmp/luxback/audit-indexes/     # Root audit path
  /joe.bloggs.csv               # Per-user audit file
  /jane.smith.csv
  /admin.csv
```

### File Naming Convention

Uploaded files are automatically prefixed with ISO-8601 timestamp:
- Original: `quarterly-report.pdf`
- Stored as: `2024-11-09T14-30-00_quarterly-report.pdf`

This ensures:
- No name collisions
- Sortable by upload time
- Original filename preserved for downloads

## Audit System

### Audit Event Schema

CSV format with headers:
```csv
event_id,event_type,timestamp,username,filename,stored_as,file_size,content_type,ip_address,user_agent,session_id,actor_username
```

**Event types:**
- `UPLOAD` - File was uploaded
- `DOWNLOAD` - File was downloaded (admin only)

**Key fields:**
- `username` - File owner
- `actor_username` - Who performed the action (uploader or downloader)
- `stored_as` - Timestamped storage filename
- `filename` - Original filename

### Viewing Audit Logs

Audit logs are plain CSV files that can be:
1. Opened in Excel/Google Sheets
2. Queried with SQL (after import to database)
3. Processed with CSV tools
4. Searched through the admin interface

Example:
```bash
cat /tmp/luxback/audit-indexes/joe.bloggs.csv
```

## API Endpoints

### Upload File
```http
POST /upload
Content-Type: multipart/form-data

file: <binary data>
```

**Response:**
```json
{
  "status": "success",
  "message": "File uploaded successfully: document.pdf"
}
```

**Errors:**
- 400 - File too large or invalid type
- 401 - Not authenticated
- 500 - Upload failed

### List Files (Admin)
```http
GET /files?filename=report&username=joe.bloggs&startDate=2024-11-01&page=0
```

**Query parameters:**
- `filename` - Filter by filename (contains, case-insensitive)
- `username` - Filter by file owner
- `startDate` - Filter by upload date (from)
- `endDate` - Filter by upload date (to)
- `page` - Page number (0-indexed, 50 results per page)

### Download File (Admin)
```http
GET /download/{username}/{filename}
```

Example:
```
GET /download/joe.bloggs/2024-11-09T14-30-00_document.pdf
```

File is streamed with `Content-Disposition: attachment` header.

## Development Workflow

### Adding New File Type Support

1. **Update configuration:**
```yaml
# application-dev-local.yaml
luxback:
  allowed-content-types:
    - video/mp4  # Add new type
```

2. **Test upload:**
```bash
curl -u user:userpass \
  -F "file=@test.mp4" \
  http://localhost:8080/upload
```

### Testing Different Roles

**As regular user:**
```bash
curl -u user:userpass http://localhost:8080/
curl -u user:userpass http://localhost:8080/files  # Should return 403
```

**As admin:**
```bash
curl -u admin:adminpass http://localhost:8080/files  # Should work
```

### Debugging Tips

1. **Enable debug logging:**
```yaml
logging:
  level:
    com.lbg.markets.luxback: DEBUG
```

2. **Check audit logs:**
```bash
tail -f /tmp/luxback/audit-indexes/*.csv
```

3. **View uploaded files:**
```bash
ls -lh /tmp/luxback/backups/*/
```

4. **Check application health:**
```bash
curl http://localhost:8080/actuator/health
```

## Testing

### Manual Testing Checklist

- [ ] Upload file as user
- [ ] Verify file appears in filesystem
- [ ] Verify audit entry created
- [ ] Try uploading oversized file (should fail)
- [ ] Try uploading invalid file type (should fail)
- [ ] Login as admin
- [ ] Search for uploaded files
- [ ] Download file as admin
- [ ] Verify download audit entry created
- [ ] Try accessing `/files` as regular user (should fail)

### Running Unit Tests

```bash
mvn test
```

### Running Integration Tests

```bash
mvn verify
```

## Performance Characteristics

### File Upload
- **Throughput:** Limited by network/storage, not application
- **Memory:** Constant - files are streamed, not buffered
- **Concurrent uploads:** Per-user locking allows multiple users simultaneously

### Admin Search
- **Initial load:** ~50ms (loads all audit CSVs)
- **Subsequent searches:** <1ms (in-memory filtering)
- **Memory usage:** ~2.5MB for year of audit data (50 users)

### Scalability
- **Current design:** Optimized for 50 users
- **Headroom:** Can handle 200-300 users before architecture changes needed
- **When to evolve:** Consider database when >300 users or >10GB audit data

## Troubleshooting

### Upload Fails with "File Too Large"
- Check `max-file-size` in configuration
- Default is 100MB
- Increase if needed for your use case

### "Access Denied" Error
- Check user role (USER vs ADMIN)
- Admin-only endpoints: `/files`, `/download/*`
- Use admin credentials for testing

### GCS Connection Issues (Production)
- Verify GCS bucket exists and is accessible
- Check service account has Storage Object Admin role
- Verify GOOGLE_APPLICATION_CREDENTIALS environment variable

### Azure AD Login Fails (Production)
- Verify AZURE_CLIENT_ID, AZURE_CLIENT_SECRET, AZURE_TENANT_ID
- Check redirect URI matches configuration
- Ensure app registration has correct permissions

### Audit CSV Corruption
- Check filesystem permissions
- Verify no manual edits to CSV files
- Restart application to reload cache

## Security Best Practices

### Development
- Never commit credentials to git
- Use `.env` files for local development (excluded from git)
- Rotate test credentials periodically

### Production
- Use environment variables for all secrets
- Enable GCS bucket versioning
- Set up monitoring for failed auth attempts
- Regular audit log reviews
- Restrict network access to CloudRun instance

## Deployment

### Building for Production

```bash
mvn clean package -DskipTests
```

This creates: `target/luxback-0.0.1-SNAPSHOT.jar`

### Docker Build (Example)

```dockerfile
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY target/luxback-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

### CloudRun Deployment

```bash
# Build and deploy
gcloud run deploy luxback \
  --source . \
  --platform managed \
  --region us-central1 \
  --allow-unauthenticated \
  --set-env-vars SPRING_PROFILES_ACTIVE=prod-gcp \
  --set-env-vars AZURE_CLIENT_ID=xxx \
  --set-env-vars AZURE_CLIENT_SECRET=xxx \
  --set-env-vars AZURE_TENANT_ID=xxx
```

## Support

### Getting Help
1. Check this developer guide
2. Review design document (README.md)
3. Check application logs
4. Review audit logs for access patterns

### Common Issues
- See Troubleshooting section above
- Check GitHub issues (if applicable)
- Review Spring Boot documentation for framework issues

## License

Proprietary - Internal use only