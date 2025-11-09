# LuxBack - File Installation Guide

This guide shows where to place each of the generated files in your project structure.

## File Placement Instructions

### Configuration Files

Copy these files to `src/main/java/com/lbg/markets/luxback/config/`:
- ✅ `WebConfig.java` - Multipart and static resource configuration

### Controller Files

Copy these files to `src/main/java/com/lbg/markets/luxback/controller/`:
- ✅ `UploadController.java` - File upload endpoint
- ✅ `FileListingController.java` - Admin file browser
- ✅ `DownloadController.java` - File download endpoint
- ✅ `LoginController.java` - Login page
- ✅ `CustomErrorController.java` - Error handling

### Security Files

Copy this file to `src/main/java/com/lbg/markets/luxback/security/`:
- ✅ `CustomAccessDeniedHandler.java` - 403 error handler

### Thymeleaf Template Files

Copy these files to `src/main/resources/templates/`:
- ✅ `layout.html` - Base layout template (not used yet, but available for refactoring)
- ✅ `upload.html` - File upload page
- ✅ `file-listing.html` - Admin file browser page
- ✅ `login.html` - Login page
- ✅ `error.html` - Error page

### Application Configuration Files

Copy these files to `src/main/resources/`:
- ✅ `application-int-gcp.yaml` - Integration GCP environment config
- ✅ `application-pre-prod-gcp.yaml` - Pre-production GCP environment config
- ✅ `application-prod-gcp.yaml` - Production GCP environment config

Note: The existing `application-dev-local.yaml` already exists in your project.

### Documentation Files

Copy these files to the project root directory:
- ✅ `PROGRESS_UPDATED.md` - Updated progress tracking (replace existing PROGRESS.md)
- ✅ `DEVELOPER_GUIDE.md` - Comprehensive developer documentation

## Quick Installation Script

You can use this script to copy all files to their correct locations:

```bash
#!/bin/bash

# Set the output directory where files were generated
OUTPUT_DIR="./outputs"

# Configuration
cp "$OUTPUT_DIR/WebConfig.java" \
   "src/main/java/com/lbg/markets/luxback/config/"

# Controllers
cp "$OUTPUT_DIR/UploadController.java" \
   "src/main/java/com/lbg/markets/luxback/controller/"
cp "$OUTPUT_DIR/FileListingController.java" \
   "src/main/java/com/lbg/markets/luxback/controller/"
cp "$OUTPUT_DIR/DownloadController.java" \
   "src/main/java/com/lbg/markets/luxback/controller/"
cp "$OUTPUT_DIR/LoginController.java" \
   "src/main/java/com/lbg/markets/luxback/controller/"
cp "$OUTPUT_DIR/CustomErrorController.java" \
   "src/main/java/com/lbg/markets/luxback/controller/"

# Security
cp "$OUTPUT_DIR/CustomAccessDeniedHandler.java" \
   "src/main/java/com/lbg/markets/luxback/security/"

# Templates
cp "$OUTPUT_DIR/layout.html" \
   "src/main/resources/templates/"
cp "$OUTPUT_DIR/upload.html" \
   "src/main/resources/templates/"
cp "$OUTPUT_DIR/file-listing.html" \
   "src/main/resources/templates/"
cp "$OUTPUT_DIR/login.html" \
   "src/main/resources/templates/"
cp "$OUTPUT_DIR/error.html" \
   "src/main/resources/templates/"

# Application configs
cp "$OUTPUT_DIR/application-int-gcp.yaml" \
   "src/main/resources/"
cp "$OUTPUT_DIR/application-pre-prod-gcp.yaml" \
   "src/main/resources/"
cp "$OUTPUT_DIR/application-prod-gcp.yaml" \
   "src/main/resources/"

# Documentation
cp "$OUTPUT_DIR/PROGRESS_UPDATED.md" \
   "PROGRESS.md"
cp "$OUTPUT_DIR/DEVELOPER_GUIDE.md" \
   "DEVELOPER_GUIDE.md"

echo "✅ All files copied successfully!"
```

## Verification

After copying files, verify your project structure looks like this:

```
luxback/
├── pom.xml
├── README.md
├── PROGRESS.md (updated)
├── DEVELOPER_GUIDE.md (new)
├── src/
│   ├── main/
│   │   ├── java/com/lbg/markets/luxback/
│   │   │   ├── config/
│   │   │   │   ├── DevSecurityConfig.java
│   │   │   │   ├── LuxBackConfig.java
│   │   │   │   ├── ProdSecurityConfig.java
│   │   │   │   └── WebConfig.java ⭐ NEW
│   │   │   ├── controller/
│   │   │   │   ├── CustomErrorController.java ⭐ NEW
│   │   │   │   ├── DownloadController.java ⭐ NEW
│   │   │   │   ├── FileListingController.java ⭐ NEW
│   │   │   │   ├── LoginController.java ⭐ NEW
│   │   │   │   └── UploadController.java ⭐ NEW
│   │   │   ├── exception/
│   │   │   │   ├── FileSizeExceededException.java
│   │   │   │   ├── InvalidFileTypeException.java
│   │   │   │   └── StorageException.java
│   │   │   ├── model/
│   │   │   │   ├── AuditEvent.java
│   │   │   │   ├── FileMetadata.java
│   │   │   │   └── SearchCriteria.java
│   │   │   ├── security/
│   │   │   │   ├── CustomAccessDeniedHandler.java ⭐ NEW
│   │   │   │   ├── Role.java
│   │   │   │   └── SecurityUtils.java
│   │   │   ├── service/
│   │   │   │   ├── AuditService.java
│   │   │   │   ├── FileMetadataService.java
│   │   │   │   ├── GcsStorageService.java
│   │   │   │   ├── LocalStorageService.java
│   │   │   │   └── StorageService.java
│   │   │   └── LuxbackApplication.java
│   │   └── resources/
│   │       ├── templates/
│   │       │   ├── error.html ⭐ NEW
│   │       │   ├── file-listing.html ⭐ NEW
│   │       │   ├── layout.html ⭐ NEW
│   │       │   ├── login.html ⭐ NEW
│   │       │   └── upload.html ⭐ NEW
│   │       ├── application.yaml
│   │       ├── application-dev-local.yaml
│   │       ├── application-int-gcp.yaml ⭐ NEW
│   │       ├── application-pre-prod-gcp.yaml ⭐ NEW
│   │       └── application-prod-gcp.yaml ⭐ NEW
│   └── test/
│       └── java/com/lbg/markets/luxback/
│           └── LuxbackApplicationTests.java
└── target/
```

## Build and Test

After copying all files:

1. **Clean build:**
```bash
mvn clean compile
```

2. **Run tests:**
```bash
mvn test
```

3. **Start application:**
```bash
mvn spring-boot:run -Dspring-boot.run.profiles=dev-local
```

4. **Access application:**
- URL: http://localhost:8080
- User: `user` / `userpass`
- Admin: `admin` / `adminpass`

## What's Included

### Completed Features
- ✅ File upload with drag-and-drop (Dropzone.js)
- ✅ Admin file browser with search and pagination
- ✅ File download with audit logging
- ✅ Role-based access control (USER, ADMIN)
- ✅ Profile-based configuration (dev, int, pre-prod, prod)
- ✅ CSV-based audit system with per-user caching
- ✅ Streaming file I/O (no memory buffering)
- ✅ Bootstrap Flatly theme UI
- ✅ Custom error handling
- ✅ CSRF protection
- ✅ Comprehensive logging

### Ready for Testing
The application is feature-complete and ready for:
- Manual testing
- Unit test implementation
- Integration test implementation
- Security testing
- Deployment to GCP

## Next Steps

1. **Copy all files** to their correct locations
2. **Build the project** to verify compilation
3. **Run the application** locally
4. **Test the features** manually
5. **Implement unit tests** for services
6. **Implement integration tests** for controllers
7. **Deploy to integration environment** for user acceptance testing

## Troubleshooting

### Compilation Errors
- Ensure all files are in correct packages
- Run `mvn clean compile` to rebuild
- Check for missing dependencies in pom.xml

### Runtime Errors
- Check profile is set correctly
- Verify configuration in application-*.yaml files
- Check logs for detailed error messages
- Ensure /tmp/luxback directories are writable

### Template Not Found
- Verify templates are in src/main/resources/templates/
- Check Thymeleaf is in dependencies
- Restart application after adding templates

## Support

Refer to:
- **README.md** - Architecture and design decisions
- **DEVELOPER_GUIDE.md** - Detailed usage and API documentation
- **PROGRESS.md** - Implementation status

For issues, check:
1. Application logs
2. Audit CSV files
3. File system permissions
4. Configuration files