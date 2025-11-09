# Multi-stage Dockerfile for LuxBack
# Stage 1: Build with Maven
FROM maven:3.9-eclipse-temurin-21-alpine AS builder

WORKDIR /build

# Copy pom.xml first for better Docker layer caching
COPY pom.xml .

# Download dependencies (cached unless pom.xml changes)
RUN mvn dependency:go-offline -B

# Copy source code
COPY src ./src

# Build the application (skip tests for faster builds - run tests in CI)
RUN mvn clean package -DskipTests -B

# Stage 2: Runtime image
FROM eclipse-temurin:21-jre-alpine

# Add metadata
LABEL maintainer="LuxBack Team"
LABEL description="LuxBack - Simple File Backup Service"
LABEL version="0.0.1-SNAPSHOT"

# Create non-root user for security
RUN addgroup -S luxback && adduser -S luxback -G luxback

WORKDIR /app

# Copy the built jar from builder stage
COPY --from=builder /build/target/luxback-*.jar app.jar

# Change ownership to non-root user
RUN chown -R luxback:luxback /app

# Switch to non-root user
USER luxback

# Expose application port
EXPOSE 8080

# Health check (CloudRun will use this)
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health || exit 1

# JVM options for containerized environment
ENV JAVA_OPTS="-XX:+UseContainerSupport \
               -XX:MaxRAMPercentage=75.0 \
               -XX:+UseG1GC \
               -XX:+HeapDumpOnOutOfMemoryError \
               -XX:HeapDumpPath=/tmp/heap-dump.hprof \
               -Djava.security.egd=file:/dev/./urandom"

# Run the application
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
