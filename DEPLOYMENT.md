# LuxBack - Docker and Cloud Run Deployment Guide

This guide covers building Docker images and deploying LuxBack to Google Cloud Run across multiple environments.

## Prerequisites

- Docker installed locally (for testing)
- Google Cloud SDK (`gcloud`) installed and authenticated
- GCS buckets created for each environment
- Azure AD configured (see AZURE_AD_SETUP.md)
- Application built successfully (`mvn clean package`)

## Overview

**Deployment Environments:**
- **Integration (int-gcp):** Development testing with Azure AD
- **Pre-Production (pre-prod-gcp):** Staging for final testing
- **Production (prod-gcp):** Live production system

**Deployment Strategy:**
1. Build application JAR with Maven
2. Build Docker image
3. Push to Google Container Registry (GCR)
4. Deploy to Cloud Run
5. Configure service account permissions
6. Test deployment

## Local Docker Build and Test

### Build the Application

First, build the JAR file:

```bash
# Clean build with tests
mvn clean package

# Or skip tests for faster build
mvn clean package -DskipTests
```

**Output:** `target/luxback-0.0.1-SNAPSHOT.jar`

### Build the Docker Image

```bash
# Build the Docker image
docker build -t luxback:latest .

# Or with a specific tag
docker build -t luxback:0.0.1 .

# Verify image was created
docker images | grep luxback
```

### Test Locally with Docker

**Development mode (basic auth, local storage):**
```bash
docker run -p 8080:8080 \
  -v /tmp/luxback:/tmp/luxback \
  -e SPRING_PROFILES_ACTIVE=dev-local \
  luxback:latest
```

**Access:** http://localhost:8080  
**Login:** `user/userpass` or `admin/adminpass`

**Integration mode (Azure AD, needs credentials):**
```bash
docker run -p 8080:8080 \
  -e SPRING_PROFILES_ACTIVE=int-gcp \
  -e AZURE_CLIENT_ID=your-client-id \
  -e AZURE_CLIENT_SECRET=your-client-secret \
  -e AZURE_TENANT_ID=your-tenant-id \
  -e AZURE_ADMIN_GROUP_ID=your-admin-group-id \
  -e AZURE_USER_GROUP_ID=your-user-group-id \
  luxback:latest
```

**Stop the container:**
```bash
# Find container ID
docker ps

# Stop container
docker stop <container-id>

# Remove container
docker rm <container-id>
```

## Google Cloud Run Deployment

### 1. Set Up Environment

```bash
# Set your project ID
export PROJECT_ID=your-gcp-project-id
export REGION=us-central1  # or your preferred region

# Authenticate
gcloud auth login
gcloud config set project $PROJECT_ID
```

### 2. Create GCS Buckets (if not exists)

Create separate buckets for each environment:

```bash
# Integration environment
gsutil mb -l $REGION gs://luxback-int

# Pre-production environment  
gsutil mb -l $REGION gs://luxback-preprod

# Production environment
gsutil mb -l $REGION gs://luxback-prod

# Verify buckets
gsutil ls | grep luxback
```

**Important:** Use separate buckets for each environment to prevent accidental data mixing.

### 3. Enable Required APIs

```bash
gcloud services enable \
  run.googleapis.com \
  cloudbuild.googleapis.com \
  containerregistry.googleapis.com \
  storage.googleapis.com
```

### 4. Build and Push to Google Container Registry

**Option 1: Using Cloud Build (recommended)**
```bash
# Tag for GCR
export IMAGE_NAME=gcr.io/$PROJECT_ID/luxback

# Build and push using Cloud Build
gcloud builds submit --tag $IMAGE_NAME:latest

# Tag with version number
gcloud builds submit --tag $IMAGE_NAME:0.0.1
```

**Option 2: Build locally and push**
```bash
# Build locally
docker build -t gcr.io/$PROJECT_ID/luxback:latest .

# Configure Docker auth for GCR
gcloud auth configure-docker

# Push to GCR
docker push gcr.io/$PROJECT_ID/luxback:latest
```

### 5. Deploy to Cloud Run

#### Integration Environment

```bash
gcloud run deploy luxback-int \
  --image gcr.io/$PROJECT_ID/luxback:latest \
  --platform managed \
  --region $REGION \
  --allow-unauthenticated \
  --memory 512Mi \
  --cpu 1 \
  --max-instances 10 \
  --min-instances 0 \
  --timeout 300 \
  --set-env-vars SPRING_PROFILES_ACTIVE=int-gcp \
  --set-env-vars AZURE_CLIENT_ID=$AZURE_CLIENT_ID \
  --set-env-vars AZURE_CLIENT_SECRET=$AZURE_CLIENT_SECRET \
  --set-env-vars AZURE_TENANT_ID=$AZURE_TENANT_ID \
  --set-env-vars AZURE_ADMIN_GROUP_ID=$AZURE_ADMIN_GROUP_ID \
  --set-env-vars AZURE_USER_GROUP_ID=$AZURE_USER_GROUP_ID
```

#### Pre-Production Environment

```bash
gcloud run deploy luxback-preprod \
  --image gcr.io/$PROJECT_ID/luxback:latest \
  --platform managed \
  --region $REGION \
  --allow-unauthenticated \
  --memory 512Mi \
  --cpu 1 \
  --max-instances 10 \
  --min-instances 0 \
  --timeout 300 \
  --set-env-vars SPRING_PROFILES_ACTIVE=pre-prod-gcp \
  --set-env-vars AZURE_CLIENT_ID=$AZURE_CLIENT_ID \
  --set-env-vars AZURE_CLIENT_SECRET=$AZURE_CLIENT_SECRET \
  --set-env-vars AZURE_TENANT_ID=$AZURE_TENANT_ID \
  --set-env-vars AZURE_ADMIN_GROUP_ID=$AZURE_ADMIN_GROUP_ID \
  --set-env-vars AZURE_USER_GROUP_ID=$AZURE_USER_GROUP_ID
```

#### Production Environment

```bash
gcloud run deploy luxback-prod \
  --image gcr.io/$PROJECT_ID/luxback:latest \
  --platform managed \
  --region $REGION \
  --allow-unauthenticated \
  --memory 512Mi \
  --cpu 1 \
  --max-instances 10 \
  --min-instances 0 \
  --timeout 300 \
  --set-env-vars SPRING_PROFILES_ACTIVE=prod-gcp \
  --set-env-vars AZURE_CLIENT_ID=$AZURE_CLIENT_ID_PROD \
  --set-env-vars AZURE_CLIENT_SECRET=$AZURE_CLIENT_SECRET_PROD \
  --set-env-vars AZURE_TENANT_ID=$AZURE_TENANT_ID \
  --set-env-vars AZURE_ADMIN_GROUP_ID=$AZURE_ADMIN_GROUP_ID_PROD \
  --set-env-vars AZURE_USER_GROUP_ID=$AZURE_USER_GROUP_ID_PROD
```

**Important:** Use separate Azure AD app registrations and credentials for production.

### 6. Configure Service Account Permissions

Grant the Cloud Run service account access to GCS buckets:

```bash
# Get the service account email
export SERVICE_ACCOUNT=$(gcloud run services describe luxback-prod \
  --platform managed \
  --region $REGION \
  --format 'value(spec.template.spec.serviceAccountName)')

echo "Service Account: $SERVICE_ACCOUNT"

# Grant GCS permissions
gsutil iam ch serviceAccount:$SERVICE_ACCOUNT:roles/storage.objectAdmin \
  gs://luxback-prod

# Verify permissions
gsutil iam get gs://luxback-prod
```

**Repeat for each environment:**
```bash
# Integration
gsutil iam ch serviceAccount:$SERVICE_ACCOUNT:roles/storage.objectAdmin gs://luxback-int

# Pre-production
gsutil iam ch serviceAccount:$SERVICE_ACCOUNT:roles/storage.objectAdmin gs://luxback-preprod
```

### 7. Verify Deployment

```bash
# Get service URL
export SERVICE_URL=$(gcloud run services describe luxback-prod \
  --platform managed \
  --region $REGION \
  --format 'value(status.url)')

echo "Application URL: $SERVICE_URL"

# Test health endpoint
curl $SERVICE_URL/actuator/health

# Expected output:
# {"status":"UP"}
```

**Manual testing:**
1. Open service URL in browser
2. Should redirect to Azure AD login
3. Log in with valid Azure AD credentials
4. Verify you can access the application
5. Test file upload
6. Test admin features (if you're in admin group)

## Environment-Specific Configurations

### Integration (int-gcp)
**Purpose:** Development testing with Azure AD  
**Logging:** INFO level  
**Actuator:** Health, info, and metrics endpoints  
**Bucket:** `gs://luxback-int`  
**Users:** Development team, QA testers

**Configuration highlights:**
```yaml
logging:
  level:
    com.lbg.markets.luxback: INFO
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics
```

### Pre-Production (pre-prod-gcp)
**Purpose:** Staging environment for final testing  
**Logging:** INFO level  
**Actuator:** Health and info only  
**Bucket:** `gs://luxback-preprod`  
**Users:** QA team, stakeholders

**Configuration highlights:**
```yaml
logging:
  level:
    com.lbg.markets.luxback: INFO
management:
  endpoints:
    web:
      exposure:
        include: health,info
```

### Production (prod-gcp)
**Purpose:** Live production system  
**Logging:** WARN level  
**Actuator:** Health only (minimal details)  
**Bucket:** `gs://luxback-prod`  
**Users:** End users

**Configuration highlights:**
```yaml
logging:
  level:
    com.lbg.markets.luxback: WARN
management:
  endpoints:
    web:
      exposure:
        include: health
  endpoint:
    health:
      show-details: never
```

## Monitoring and Logging

### View Logs

**Recent logs:**
```bash
gcloud run services logs read luxback-prod \
  --platform managed \
  --region $REGION \
  --limit 100
```

**Follow logs (tail):**
```bash
gcloud run services logs tail luxback-prod \
  --platform managed \
  --region $REGION
```

**Filter logs:**
```bash
# Only errors
gcloud run services logs read luxback-prod \
  --platform managed \
  --region $REGION \
  --log-filter='severity>=ERROR'

# Specific user activity
gcloud run services logs read luxback-prod \
  --platform managed \
  --region $REGION \
  --log-filter='textPayload:"user@example.com"'
```

### View Metrics

**In Cloud Console:**
1. Navigate to Cloud Run
2. Select your service
3. Click "Metrics" tab
4. View request count, latency, memory usage

**Using gcloud:**
```bash
# Get service URL for testing
gcloud run services describe luxback-prod \
  --platform managed \
  --region $REGION \
  --format 'value(status.url)'
```

### Health Checks

Cloud Run automatically uses the configured health check:

```bash
# Health endpoint
curl https://your-service-url.run.app/actuator/health

# Expected response:
# {"status":"UP"}
```

**Health check configuration:**
- Endpoint: `/actuator/health`
- Expected: 200 OK
- Interval: 30s
- Timeout: 3s
- Startup period: 60s

## Advanced Deployment Scenarios

### Blue-Green Deployment

Deploy new version without downtime:

```bash
# Deploy new version with tag
gcloud run deploy luxback-prod \
  --image gcr.io/$PROJECT_ID/luxback:v2 \
  --tag blue \
  --no-traffic \
  --region $REGION

# Test the new version at: https://blue---luxback-prod-xyz.run.app

# If tests pass, migrate traffic
gcloud run services update-traffic luxback-prod \
  --to-tags blue=100 \
  --region $REGION

# Rollback if needed
gcloud run services update-traffic luxback-prod \
  --to-revisions PREVIOUS_REVISION=100 \
  --region $REGION
```

### Canary Deployment

Gradually roll out new version:

```bash
# Deploy new version
gcloud run deploy luxback-prod \
  --image gcr.io/$PROJECT_ID/luxback:v2 \
  --tag canary \
  --no-traffic \
  --region $REGION

# Route 10% traffic to canary
gcloud run services update-traffic luxback-prod \
  --to-tags canary=10 \
  --region $REGION

# Monitor metrics, then increase gradually
gcloud run services update-traffic luxback-prod \
  --to-tags canary=50 \
  --region $REGION

# Final cutover
gcloud run services update-traffic luxback-prod \
  --to-tags canary=100 \
  --region $REGION
```

### Using Secret Manager

Store sensitive configuration in Secret Manager:

```bash
# Create secret
echo -n "your-azure-client-secret" | \
  gcloud secrets create azure-client-secret --data-file=-

# Grant service account access
gcloud secrets add-iam-policy-binding azure-client-secret \
  --member=serviceAccount:$SERVICE_ACCOUNT \
  --role=roles/secretmanager.secretAccessor

# Deploy with secret
gcloud run deploy luxback-prod \
  --image gcr.io/$PROJECT_ID/luxback:latest \
  --update-secrets AZURE_CLIENT_SECRET=azure-client-secret:latest \
  --region $REGION
```

## Troubleshooting

### Container Fails to Start

**Check logs:**
```bash
gcloud run services logs read luxback-prod --limit 50
```

**Common issues:**
1. **Missing environment variables**
    - Verify all required env vars are set
    - Check AZURE_CLIENT_ID, AZURE_CLIENT_SECRET, etc.

2. **Invalid Azure AD configuration**
    - Verify redirect URI matches
    - Check app registration permissions

3. **GCS bucket permissions**
    - Verify service account has storage.objectAdmin role
    - Check bucket exists and is accessible

### Out of Memory Errors

**Symptom:** Container restarts frequently, OOM errors in logs

**Solution:** Increase memory allocation
```bash
gcloud run services update luxback-prod \
  --memory 1Gi \
  --region $REGION
```

**Monitor memory usage:**
```bash
# View metrics in Cloud Console
# Or check logs for memory warnings
gcloud run services logs read luxback-prod \
  --log-filter='textPayload:"OutOfMemoryError"'
```

### Slow Cold Starts

**Symptom:** First request after idle period is very slow

**Solution:** Set minimum instances
```bash
gcloud run services update luxback-prod \
  --min-instances 1 \
  --region $REGION
```

**Note:** This increases costs but eliminates cold starts.

### GCS Access Denied

**Symptom:** StorageException when uploading/downloading files

**Check permissions:**
```bash
# Verify service account
gcloud run services describe luxback-prod \
  --format='value(spec.template.spec.serviceAccountName)'

# Check bucket IAM
gsutil iam get gs://luxback-prod

# Grant access if needed
gsutil iam ch serviceAccount:$SERVICE_ACCOUNT:roles/storage.objectAdmin \
  gs://luxback-prod
```

### Deployment Timeout

**Symptom:** Deployment hangs or times out

**Solutions:**
```bash
# Increase timeout
gcloud run deploy luxback-prod \
  --timeout 300 \
  --region $REGION

# Check Cloud Build logs
gcloud builds list --limit 5

# View specific build
gcloud builds log <BUILD_ID>
```

## CI/CD Integration

### GitHub Actions Example

```yaml
# .github/workflows/deploy.yml
name: Deploy to Cloud Run

on:
  push:
    branches: [main]

jobs:
  deploy:
    runs-on: ubuntu-latest
    
    steps:
    - uses: actions/checkout@v3
    
    - name: Set up JDK 21
      uses: actions/setup-java@v3
      with:
        java-version: '21'
        distribution: 'temurin'
    
    - name: Build with Maven
      run: mvn clean package -DskipTests
    
    - name: Set up Cloud SDK
      uses: google-github-actions/setup-gcloud@v1
      with:
        project_id: ${{ secrets.GCP_PROJECT_ID }}
        service_account_key: ${{ secrets.GCP_SA_KEY }}
    
    - name: Build and push Docker image
      run: |
        gcloud builds submit --tag gcr.io/${{ secrets.GCP_PROJECT_ID }}/luxback:$GITHUB_SHA
    
    - name: Deploy to Cloud Run
      run: |
        gcloud run deploy luxback-prod \
          --image gcr.io/${{ secrets.GCP_PROJECT_ID }}/luxback:$GITHUB_SHA \
          --platform managed \
          --region us-central1 \
          --set-env-vars SPRING_PROFILES_ACTIVE=prod-gcp \
          --set-env-vars AZURE_CLIENT_ID=${{ secrets.AZURE_CLIENT_ID }} \
          --set-env-vars AZURE_CLIENT_SECRET=${{ secrets.AZURE_CLIENT_SECRET }} \
          --set-env-vars AZURE_TENANT_ID=${{ secrets.AZURE_TENANT_ID }} \
          --set-env-vars AZURE_ADMIN_GROUP_ID=${{ secrets.AZURE_ADMIN_GROUP_ID }} \
          --set-env-vars AZURE_USER_GROUP_ID=${{ secrets.AZURE_USER_GROUP_ID }}
```

### GitLab CI Example

```yaml
# .gitlab-ci.yml
stages:
  - build
  - deploy

build:
  stage: build
  image: maven:3.9-eclipse-temurin-21
  script:
    - mvn clean package -DskipTests
  artifacts:
    paths:
      - target/*.jar

deploy:
  stage: deploy
  image: google/cloud-sdk:alpine
  script:
    - echo $GCP_SA_KEY | gcloud auth activate-service-account --key-file=-
    - gcloud config set project $GCP_PROJECT_ID
    - gcloud builds submit --tag gcr.io/$GCP_PROJECT_ID/luxback:latest
    - gcloud run deploy luxback-prod
        --image gcr.io/$GCP_PROJECT_ID/luxback:latest
        --platform managed
        --region us-central1
        --set-env-vars SPRING_PROFILES_ACTIVE=prod-gcp
  only:
    - main
```

## Rollback Procedures

### List Revisions

```bash
# List all revisions
gcloud run revisions list \
  --service luxback-prod \
  --region $REGION

# View specific revision details
gcloud run revisions describe REVISION_NAME \
  --region $REGION
```

### Rollback to Previous Revision

```bash
# Get previous revision name
PREVIOUS_REVISION=$(gcloud run revisions list \
  --service luxback-prod \
  --region $REGION \
  --sort-by=~ACTIVE \
  --limit 2 \
  --format='value(REVISION)' | tail -1)

# Rollback
gcloud run services update-traffic luxback-prod \
  --to-revisions $PREVIOUS_REVISION=100 \
  --region $REGION
```

### Rollback to Specific Version

```bash
# Rollback to specific revision
gcloud run services update-traffic luxback-prod \
  --to-revisions luxback-prod-00042-xyz=100 \
  --region $REGION
```

## Clean Up

### Delete Cloud Run Service

```bash
# Delete service
gcloud run services delete luxback-prod \
  --platform managed \
  --region $REGION

# Confirm deletion
gcloud run services list
```

### Delete Docker Images

```bash
# List images
gcloud container images list --repository=gcr.io/$PROJECT_ID

# Delete specific image
gcloud container images delete gcr.io/$PROJECT_ID/luxback:latest

# Delete all versions of an image
gcloud container images delete gcr.io/$PROJECT_ID/luxback --force-delete-tags
```

### Delete GCS Buckets

**⚠️ WARNING:** This will delete all uploaded files and audit logs!

```bash
# Delete bucket and all contents
gsutil -m rm -r gs://luxback-prod

# Verify deletion
gsutil ls | grep luxback
```

## Performance Tuning

### JVM Options

The Dockerfile already includes optimized JVM settings:

```dockerfile
ENV JAVA_OPTS="-XX:+UseContainerSupport \
               -XX:MaxRAMPercentage=75.0 \
               -XX:+UseG1GC \
               -XX:+HeapDumpOnOutOfMemoryError \
               -XX:HeapDumpPath=/tmp/heap-dump.hprof \
               -Djava.security.egd=file:/dev/./urandom"
```

### Concurrency Settings

Adjust concurrent requests per instance:

```bash
gcloud run services update luxback-prod \
  --concurrency 80 \
  --region $REGION
```

**Guidelines:**
- Default: 80 concurrent requests
- Memory-intensive workloads: Lower (40-60)
- I/O-intensive workloads: Higher (100-150)

### Scaling Configuration

```bash
# Set auto-scaling parameters
gcloud run services update luxback-prod \
  --min-instances 1 \
  --max-instances 10 \
  --concurrency 80 \
  --cpu-throttling \
  --region $REGION
```

## Security Best Practices

### Environment Variables

**Never commit secrets:**
- Use Secret Manager for sensitive data
- Use environment variables for configuration
- Rotate secrets regularly

### Network Security

**Consider VPC connector:**
```bash
# Create VPC connector
gcloud compute networks vpc-access connectors create luxback-connector \
  --network default \
  --region $REGION \
  --range 10.8.0.0/28

# Use connector in deployment
gcloud run deploy luxback-prod \
  --vpc-connector luxback-connector \
  --vpc-egress all-traffic \
  --region $REGION
```

### IAM Best Practices

**Principle of least privilege:**
```bash
# Service account should only have necessary permissions
# - storage.objectAdmin for GCS bucket
# - No other permissions needed
```

### Audit Logging

**Enable Cloud Audit Logs:**
```bash
# Enable admin activity logs (automatic)
# Enable data access logs (manual)
gcloud logging sinks create luxback-audit \
  storage.googleapis.com/luxback-audit-logs \
  --log-filter='resource.type="cloud_run_revision"
                resource.labels.service_name="luxback-prod"'
```

## Cost Optimization

### Reduce Costs

**For development environments:**
```bash
# Reduce min instances
gcloud run services update luxback-int \
  --min-instances 0 \
  --region $REGION

# Use smaller instance
gcloud run services update luxback-int \
  --memory 256Mi \
  --cpu 1 \
  --region $REGION
```

### Monitor Costs

**View cost breakdown:**
1. Navigate to Cloud Console → Billing
2. Filter by Cloud Run and Storage
3. Review usage by service

**Set budget alerts:**
```bash
# Create budget (via console or API)
# Set alerts at 50%, 90%, 100% of budget
```

## References

- [Cloud Run Documentation](https://cloud.google.com/run/docs)
- [Docker Best Practices](https://docs.docker.com/develop/dev-best-practices/)
- [GCS IAM Permissions](https://cloud.google.com/storage/docs/access-control/iam-permissions)
- [Spring Boot on Cloud Run](https://cloud.google.com/run/docs/quickstarts/build-and-deploy/deploy-java-service)

---

**Last Updated:** November 2024  
**Status:** Production-ready  
**Version:** 0.0.1-SNAPSHOT