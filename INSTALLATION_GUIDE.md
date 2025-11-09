# LuxBack - Installation Guide

## Project Status

✅ **Complete Implementation** - All files in place, fully tested, ready to run

This guide covers getting LuxBack up and running on your local machine or deploying to Google Cloud Run.

## Prerequisites

- **Java 21** or higher
- **Maven 3.8+** for building
- **(Optional) Docker** for containerized deployment
- **(Optional) Google Cloud SDK** for GCP deployment
- **(Optional) Azure AD** configured for production authentication

## Quick Start - Local Development

### 1. Clone the Repository

```bash
git clone <repository-url>
cd luxback
```

### 2. Verify Project Structure

Your project should have this structure:

```
luxback/
├── pom.xml
├── README.md
├── DEVELOPER_GUIDE.md
├── AZURE_AD_SETUP.md
├── DEPLOYMENT.md
├── Dockerfile
├── src/
│   ├── main/
│   │   ├── java/com/lbg/markets/luxback/
│   │   │   ├── config/
│   │   │   ├── controller/
│   │   │   ├── exception/
│   │   │   ├── model/
│   │   │   ├── security/
│   │   │   ├── service/
│   │   │   └── LuxbackApplication.java
│   │   └── resources/
│   │       ├── templates/
│   │       ├── application.yaml
│   │       ├── application-dev-local.yaml
│   │       ├── application-int-gcp.yaml
│   │       ├── application-pre-prod-gcp.yaml
│   │       └── application-prod-gcp.yaml
│   └── test/
│       └── java/com/lbg/markets/luxback/
└── target/
```

### 3. Build the Application

```bash
# Clean build with tests
mvn clean package

# Or skip tests for faster build
mvn clean package -DskipTests
```

This will:
- Compile all Java sources
- Run all unit and integration tests
- Create executable JAR: `target/luxback-0.0.1-SNAPSHOT.jar`

### 4. Run Locally

**Option A - Using Maven:**
```bash
mvn spring-boot:run -Dspring-boot.run.profiles=dev-local
```

**Option B - Using JAR:**
```bash
java -jar target/luxback-0.0.1-SNAPSHOT.jar --spring.profiles.active=dev-local
```

### 5. Access the Application

**URLs:**
- Application: http://localhost:8080
- Health check: http://localhost:8080/actuator/health
- Metrics (dev only): http://localhost:8080/actuator/metrics

**Test Credentials:**
- Regular user: `user` / `userpass`
- Administrator: `admin` / `adminpass`

**Storage Locations:**
- Files: `/tmp/luxback/backups/`
- Audit logs: `/tmp/luxback/audit-indexes/`

### 6. Test the Application

**Upload a file:**
1. Log in as `user` / `userpass`
2. Drag and drop a PDF file onto the upload zone
3. Verify success message
4. Check `/tmp/luxback/backups/user/` for the uploaded file
5. Check `/tmp/luxback/audit-indexes/user.csv` for audit entry

**Browse files (admin):**
1. Log out and log in as `admin` / `adminpass`
2. Click "Files" in the navbar
3. View all uploaded files
4. Test search and filters
5. Download a file

## Docker Installation

### Build Docker Image

```bash
# Build image
docker build -t luxback:latest .

# Verify image
docker images | grep luxback
```

### Run with Docker

**Development mode (local storage, basic auth):**
```bash
docker run -p 8080:8080 \
  -v /tmp/luxback:/tmp/luxback \
  -e SPRING_PROFILES_ACTIVE=dev-local \
  luxback:latest
```

**Note:** The `-v` flag mounts a local directory so your uploads persist.

**Access:** http://localhost:8080

**Credentials:** `user`/`userpass` or `admin`/`adminpass`

### Stop the Container

```bash
# Find container ID
docker ps

# Stop container
docker stop <container-id>

# Remove container
docker rm <container-id>
```

## Google Cloud Run Deployment

### Prerequisites

1. **Google Cloud Project** with billing enabled
2. **gcloud CLI** installed and authenticated
3. **GCS buckets** created for each environment
4. **Azure AD** configured (see AZURE_AD_SETUP.md)

### Deploy to Integration Environment

```bash
# Set variables
export PROJECT_ID=your-gcp-project-id
export REGION=us-central1

# Authenticate
gcloud auth login
gcloud config set project $PROJECT_ID

# Build and deploy
gcloud run deploy luxback-int \
  --source . \
  --platform managed \
  --region $REGION \
  --allow-unauthenticated \
  --memory 512Mi \
  --cpu 1 \
  --max-instances 10 \
  --timeout 300 \
  --set-env-vars SPRING_PROFILES_ACTIVE=int-gcp \
  --set-env-vars AZURE_CLIENT_ID=$AZURE_CLIENT_ID \
  --set-env-vars AZURE_CLIENT_SECRET=$AZURE_CLIENT_SECRET \
  --set-env-vars AZURE_TENANT_ID=$AZURE_TENANT_ID \
  --set-env-vars AZURE_ADMIN_GROUP_ID=$AZURE_ADMIN_GROUP_ID \
  --set-env-vars AZURE_USER_GROUP_ID=$AZURE_USER_GROUP_ID
```

### Deploy to Production

```bash
gcloud run deploy luxback-prod \
  --source . \
  --platform managed \
  --region $REGION \
  --allow-unauthenticated \
  --memory 512Mi \
  --cpu 1 \
  --max-instances 10 \
  --timeout 300 \
  --set-env-vars SPRING_PROFILES_ACTIVE=prod-gcp \
  --set-env-vars AZURE_CLIENT_ID=$AZURE_CLIENT_ID_PROD \
  --set-env-vars AZURE_CLIENT_SECRET=$AZURE_CLIENT_SECRET_PROD \
  --set-env-vars AZURE_TENANT_ID=$AZURE_TENANT_ID \
  --set-env-vars AZURE_ADMIN_GROUP_ID=$AZURE_ADMIN_GROUP_ID_PROD \
  --set-env-vars AZURE_USER_GROUP_ID=$AZURE_USER_GROUP_ID_PROD
```

**Note:** Use separate Azure AD app registrations for each environment.

### Verify Deployment

```bash
# Get service URL
SERVICE_URL=$(gcloud run services describe luxback-prod \
  --platform managed \
  --region $REGION \
  --format 'value(status.url)')

echo "Application URL: $SERVICE_URL"

# Test health endpoint
curl $SERVICE_URL/actuator/health
```

## Configuration Verification

### Check Configuration Files

**1. Verify base configuration:**
```bash
cat src/main/resources/application.yaml
```

Should show:
```yaml
spring:
  application:
    name: luxback
  profiles:
    active: dev-local
```

**2. Verify dev configuration:**
```bash
cat src/main/resources/application-dev-local.yaml
```

Should include:
- `storage-path: /tmp/luxback/backups`
- `audit-index-path: /tmp/luxback/audit-indexes`
- Basic auth credentials
- Allowed content types

**3. Verify production configurations exist:**
```bash
ls -l src/main/resources/application-*-gcp.yaml
```

Should show:
- `application-int-gcp.yaml`
- `application-pre-prod-gcp.yaml`
- `application-prod-gcp.yaml`

### Validate Application Properties

**Run validation:**
```bash
mvn validate
```

**Check for configuration issues:**
```bash
# Try to start with invalid profile (should fail gracefully)
mvn spring-boot:run -Dspring-boot.run.profiles=invalid-profile

# Should show clear error about missing profile
```

## Testing the Installation

### Automated Tests

**Run all tests:**
```bash
mvn test
```

**Expected output:**
- All unit tests pass
- All integration tests pass
- Test coverage report generated

**Run specific test suites:**
```bash
# Controller tests only
mvn test -Dtest=*ControllerTest

# Service tests only
mvn test -Dtest=*ServiceTest

# Integration tests only
mvn test -Dtest=*IntegrationTest
```

### Manual Testing Checklist

**As Regular User:**
- [ ] Can access login page
- [ ] Can log in with `user` / `userpass`
- [ ] Can access upload page
- [ ] Can upload a PDF file
- [ ] Can upload an Excel file
- [ ] Can see upload success message
- [ ] Cannot access `/files` page (gets 403)
- [ ] Cannot access download links (gets 403)

**As Administrator:**
- [ ] Can log in with `admin` / `adminpass`
- [ ] Can access upload page
- [ ] Can upload files
- [ ] Can access `/files` page
- [ ] Can see all uploaded files
- [ ] Can filter by filename
- [ ] Can filter by username
- [ ] Can filter by date range
- [ ] Can download files
- [ ] Can see pagination (if >50 files)

**Validation:**
- [ ] Files uploaded as `user` appear in `/tmp/luxback/backups/user/`
- [ ] Files uploaded as `admin` appear in `/tmp/luxback/backups/admin/`
- [ ] Upload events recorded in `/tmp/luxback/audit-indexes/user.csv`
- [ ] Download events recorded in audit CSV files
- [ ] Uploaded files have timestamped filenames
- [ ] Original filenames preserved in audit log
- [ ] Large files (>100MB) rejected with error
- [ ] Invalid file types rejected with error

## Troubleshooting Installation

### Build Fails

**Problem:** Maven build errors

**Solutions:**
```bash
# Clean and rebuild
mvn clean install

# Skip tests if they're failing
mvn clean install -DskipTests

# Update dependencies
mvn dependency:resolve

# Check Java version
java -version  # Should be 21+
```

### Application Won't Start

**Problem:** Application fails during startup

**Common causes:**
1. **Port already in use:**
```bash
# Check what's using port 8080
lsof -i :8080

# Kill the process or use different port
java -jar target/luxback-0.0.1-SNAPSHOT.jar --server.port=8081
```

2. **Invalid configuration:**
```bash
# Check logs
tail -f logs/application.log

# Verify configuration files exist
ls -l src/main/resources/application*.yaml
```

3. **Missing dependencies:**
```bash
# Clean rebuild
mvn clean install
```

### Cannot Upload Files

**Problem:** File upload fails with error

**Possible causes:**

1. **File too large:**
    - Default limit is 100MB
    - Check `max-file-size` in configuration

2. **Invalid file type:**
    - Check `allowed-content-types` in configuration
    - Add your file type if needed

3. **Storage directory not writable:**
```bash
# Check permissions
ls -ld /tmp/luxback/backups

# Create if missing
mkdir -p /tmp/luxback/backups
chmod 755 /tmp/luxback/backups
```

4. **CSRF token missing:**
    - Make sure JavaScript is enabled
    - Check browser console for errors

### Cannot Access Admin Pages

**Problem:** Get 403 Forbidden on `/files`

**Solution:**
```bash
# Make sure you're logged in as admin
# Credentials: admin / adminpass

# Check application logs
tail -f logs/application.log

# Should see: "Granted ADMIN role to user: admin"
```

### Docker Container Issues

**Problem:** Container fails to start

**Solutions:**
```bash
# Check container logs
docker logs <container-id>

# Inspect container
docker inspect <container-id>

# Try running interactively
docker run -it luxback:latest /bin/sh

# Check environment variables
docker run -it luxback:latest env
```

### GCP Deployment Issues

**Problem:** Cloud Run deployment fails

**Common issues:**

1. **Insufficient permissions:**
```bash
# Grant required roles
gcloud projects add-iam-policy-binding $PROJECT_ID \
  --member="user:your-email@example.com" \
  --role="roles/run.admin"
```

2. **Missing GCS bucket:**
```bash
# Create bucket
gsutil mb -l $REGION gs://luxback-prod

# Verify
gsutil ls gs://luxback-prod
```

3. **Service account permissions:**
```bash
# Get service account
gcloud run services describe luxback-prod --format='value(spec.template.spec.serviceAccount)'

# Grant storage permissions
gsutil iam ch serviceAccount:SERVICE_ACCOUNT:roles/storage.objectAdmin gs://luxback-prod
```

## Next Steps

### For Developers

1. **Read the documentation:**
    - [README.md](README.md) - Architecture and design
    - [DEVELOPER_GUIDE.md](DEVELOPER_GUIDE.md) - Development workflows

2. **Explore the code:**
    - Start with `LuxbackApplication.java`
    - Review controller implementations
    - Understand the service layer

3. **Run the tests:**
    - Review test coverage
    - Add tests for new features

4. **Experiment:**
    - Try different file types
    - Test with large files
    - Explore the admin interface

### For Operations

1. **Set up environments:**
    - Create GCS buckets for int/pre-prod/prod
    - Configure Azure AD for each environment
    - Deploy to integration first

2. **Configure monitoring:**
    - Set up Cloud Logging alerts
    - Monitor health endpoints
    - Review audit logs regularly

3. **Security:**
    - Rotate credentials regularly
    - Review access logs
    - Keep dependencies updated

### For Production Deployment

1. **Review [AZURE_AD_SETUP.md](AZURE_AD_SETUP.md)**
    - Configure app registrations
    - Set up security groups
    - Test authentication flow

2. **Review [DEPLOYMENT.md](DEPLOYMENT.md)**
    - Complete deployment checklist
    - Configure environment variables
    - Set up monitoring

3. **Test thoroughly:**
    - Run integration environment first
    - Test with real Azure AD users
    - Verify file upload/download
    - Check audit logging

## Getting Help

### Resources

- **Documentation:** README.md, DEVELOPER_GUIDE.md, AZURE_AD_SETUP.md, DEPLOYMENT.md
- **Application Logs:** Check for detailed error messages
- **Audit Logs:** Review CSV files for user activity
- **Health Check:** `/actuator/health` endpoint

### Common Questions

**Q: Where are files stored?**
A: Dev mode: `/tmp/luxback/backups/{username}/`. Production: GCS buckets.

**Q: Where are audit logs?**
A: Dev mode: `/tmp/luxback/audit-indexes/{username}.csv`. Production: GCS buckets.

**Q: How do I change the file size limit?**
A: Edit `max-file-size` in application.yaml configuration.

**Q: How do I add new file types?**
A: Add MIME types to `allowed-content-types` in configuration.

**Q: Can regular users download their own files?**
A: No, only admins can download files. This is by design for audit purposes.

**Q: How do I add a new admin?**
A: Dev: Use admin credentials. Production: Add user to Azure AD admin group.

## Summary

LuxBack is now ready to use! The application includes:

✅ Complete source code  
✅ Comprehensive tests  
✅ Multiple deployment profiles  
✅ Full documentation  
✅ Docker support  
✅ Cloud Run ready

**Quick Start:**
```bash
# Build
mvn clean package

# Run
mvn spring-boot:run

# Access
http://localhost:8080

# Login
user/userpass or admin/adminpass
```

For detailed usage and development workflows, see [DEVELOPER_GUIDE.md](DEVELOPER_GUIDE.md).

For production deployment, review [DEPLOYMENT.md](DEPLOYMENT.md) and [AZURE_AD_SETUP.md](AZURE_AD_SETUP.md).

---

**Status:** Production-ready  
**Version:** 0.0.1-SNAPSHOT  
**Last Updated:** November 2024