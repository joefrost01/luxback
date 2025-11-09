# Docker Deployment Guide

This guide covers building and deploying LuxBack using Docker and Google Cloud Run.

## Prerequisites

- Docker installed locally (for testing)
- Google Cloud SDK (`gcloud`) installed and authenticated
- GCS buckets created for each environment
- Azure AD configured (see AZURE_AD_SETUP.md)

## Local Docker Build and Test

### Build the Image

```bash
# Build the Docker image
docker build -t luxback:latest .

# Or with a specific tag
docker build -t luxback:0.0.1 .
```

### Run Locally with Docker

```bash
# Run in dev-local mode (no Azure AD)
docker run -p 8080:8080 \
  -e SPRING_PROFILES_ACTIVE=dev-local \
  luxback:latest

# Access at http://localhost:8080
# Login with: user/userpass or admin/adminpass
```

### Test with Azure AD Locally

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

## Build Options

### Standard Build (for deployment)
```bash
docker build -t luxback:latest .
```

### Development Build (includes tests)
```bash
# Modify Dockerfile to include tests
docker build \
  --build-arg MAVEN_OPTS="-Dmaven.test.skip=false" \
  -t luxback:dev .
```

### Multi-architecture Build (for Apple Silicon)
```bash
docker buildx build \
  --platform linux/amd64,linux/arm64 \
  -t luxback:latest .
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

```bash
# Integration environment
gsutil mb -l $REGION gs://luxback-int

# Pre-production environment  
gsutil mb -l $REGION gs://luxback-preprod

# Production environment
gsutil mb -l $REGION gs://luxback-prod
```

### 3. Enable Required APIs

```bash
gcloud services enable \
  run.googleapis.com \
  cloudbuild.googleapis.com \
  containerregistry.googleapis.com
```

### 4. Build and Push to Google Container Registry

```bash
# Tag for GCR
export IMAGE_NAME=gcr.io/$PROJECT_ID/luxback

# Build and push (method 1: using Cloud Build)
gcloud builds submit --tag $IMAGE_NAME:latest

# Or build locally and push (method 2)
docker build -t $IMAGE_NAME:latest .
docker push $IMAGE_NAME:latest
```

### 5. Deploy to Cloud Run

#### Integration Environment

```bash
gcloud run deploy luxback-int \
  --image $IMAGE_NAME:latest \
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

#### Pre-Production Environment

```bash
gcloud run deploy luxback-preprod \
  --image $IMAGE_NAME:latest \
  --platform managed \
  --region $REGION \
  --allow-unauthenticated \
  --memory 512Mi \
  --cpu 1 \
  --max-instances 10 \
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
  --image $IMAGE_NAME:latest \
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

### 6. Configure Service Account Permissions

```bash
# Get the service account email
export SERVICE_ACCOUNT=$(gcloud run services describe luxback-prod \
  --platform managed \
  --region $REGION \
  --format 'value(spec.template.spec.serviceAccountName)')

# Grant GCS permissions
gsutil iam ch serviceAccount:$SERVICE_ACCOUNT:roles/storage.objectAdmin \
  gs://luxback-prod
```

## Environment-Specific Configurations

### Integration (int-gcp)
- **Purpose**: Development testing with Azure AD
- **Logging**: DEBUG level
- **Actuator**: All endpoints exposed
- **Bucket**: `gs://luxback-int`

### Pre-Production (pre-prod-gcp)
- **Purpose**: Staging environment for final testing
- **Logging**: INFO level
- **Actuator**: Health and info only
- **Bucket**: `gs://luxback-preprod`

### Production (prod-gcp)
- **Purpose**: Live production system
- **Logging**: WARN level
- **Actuator**: Health only (no details)
- **Bucket**: `gs://luxback-prod`

## Health Checks

Cloud Run automatically uses the health check configured in the Dockerfile:

```
GET /actuator/health
Expected: 200 OK
Interval: 30s
Timeout: 3s
```

## Monitoring

### View Logs

```bash
# Recent logs
gcloud run services logs read luxback-prod \
  --platform managed \
  --region $REGION \
  --limit 100

# Follow logs (tail)
gcloud run services logs tail luxback-prod \
  --platform managed \
  --region $REGION
```

### View Metrics

```bash
# Get service URL
gcloud run services describe luxback-prod \
  --platform managed \
  --region $REGION \
  --format 'value(status.url)'

# Check health
curl https://your-service-url.run.app/actuator/health
```

## Troubleshooting

### Container Fails to Start

```bash
# Check logs
gcloud run services logs read luxback-prod --limit 50

# Common issues:
# - Missing environment variables
# - Invalid Azure AD configuration
# - GCS bucket permissions
```

### Out of Memory Errors

```bash
# Increase memory allocation
gcloud run services update luxback-prod \
  --memory 1Gi \
  --region $REGION
```

### Slow Cold Starts

```bash
# Set minimum instances
gcloud run services update luxback-prod \
  --min-instances 1 \
  --region $REGION
```

### GCS Access Denied

```bash
# Verify service account has storage.objectAdmin role
# Or grant specific permissions:
gsutil iam ch \
  serviceAccount:$SERVICE_ACCOUNT:roles/storage.objectCreator \
  gs://luxback-prod

gsutil iam ch \
  serviceAccount:$SERVICE_ACCOUNT:roles/storage.objectViewer \
  gs://luxback-prod
```

## CI/CD Integration

### Example GitHub Actions Workflow

```yaml
name: Deploy to Cloud Run

on:
  push:
    branches: [main]

jobs:
  deploy:
    runs-on: ubuntu-latest
    
    steps:
    - uses: actions/checkout@v3
    
    - name: Set up Cloud SDK
      uses: google-github-actions/setup-gcloud@v1
      with:
        project_id: ${{ secrets.GCP_PROJECT_ID }}
        service_account_key: ${{ secrets.GCP_SA_KEY }}
    
    - name: Build and push
      run: |
        gcloud builds submit --tag gcr.io/${{ secrets.GCP_PROJECT_ID }}/luxback:$GITHUB_SHA
    
    - name: Deploy to Cloud Run
      run: |
        gcloud run deploy luxback-prod \
          --image gcr.io/${{ secrets.GCP_PROJECT_ID }}/luxback:$GITHUB_SHA \
          --platform managed \
          --region us-central1
```

## Rollback

```bash
# List revisions
gcloud run revisions list \
  --service luxback-prod \
  --region $REGION

# Rollback to previous revision
gcloud run services update-traffic luxback-prod \
  --to-revisions REVISION_NAME=100 \
  --region $REGION
```

## Clean Up

```bash
# Delete Cloud Run service
gcloud run services delete luxback-prod \
  --platform managed \
  --region $REGION

# Delete Docker images
gcloud container images delete gcr.io/$PROJECT_ID/luxback:latest

# Delete GCS buckets (careful!)
gsutil -m rm -r gs://luxback-prod
```

## Performance Tuning

### JVM Options (already configured in Dockerfile)

```bash
-XX:+UseContainerSupport     # Auto-detect container limits
-XX:MaxRAMPercentage=75.0    # Use 75% of container memory
-XX:+UseG1GC                 # G1 garbage collector
```

### Concurrency Settings

```bash
# Adjust concurrent requests per instance
gcloud run services update luxback-prod \
  --concurrency 80 \
  --region $REGION
```

## Security Considerations

1. **Never commit secrets**: Use environment variables or Secret Manager
2. **Least privilege**: Service account should only have necessary permissions
3. **Regular updates**: Keep base images and dependencies updated
4. **Audit logs**: Monitor Cloud Run audit logs for suspicious activity
5. **VPC connector**: Consider using VPC connector for enhanced security

## Cost Optimization

```bash
# Set maximum instances to control costs
gcloud run services update luxback-prod \
  --max-instances 10 \
  --region $REGION

# Use smaller instance types for non-prod
gcloud run services update luxback-int \
  --memory 256Mi \
  --cpu 1 \
  --region $REGION
```

## References

- [Cloud Run Documentation](https://cloud.google.com/run/docs)
- [Docker Best Practices](https://docs.docker.com/develop/dev-best-practices/)
- [GCS IAM Permissions](https://cloud.google.com/storage/docs/access-control/iam-permissions)
