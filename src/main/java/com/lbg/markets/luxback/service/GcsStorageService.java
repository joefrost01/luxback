package com.lbg.markets.luxback.service;

import com.google.cloud.storage.*;
import com.lbg.markets.luxback.exception.StorageException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.Channels;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Google Cloud Storage implementation of StorageService.
 * Active in GCP profiles (int-gcp, pre-prod-gcp, prod-gcp).
 */
@Service
@Profile({"int-gcp", "pre-prod-gcp", "prod-gcp"})
@RequiredArgsConstructor
@Slf4j
public class GcsStorageService implements StorageService {

    private final Storage storage = StorageOptions.getDefaultInstance().getService();

    /**
     * Parse GCS path (gs://bucket/path) into bucket and blob name
     */
    private BlobId parsePath(String path) {
        if (!path.startsWith("gs://")) {
            throw new IllegalArgumentException("GCS path must start with gs://");
        }

        String withoutPrefix = path.substring(5);
        int slashIndex = withoutPrefix.indexOf('/');

        if (slashIndex == -1) {
            throw new IllegalArgumentException("Invalid GCS path, missing blob name: " + path);
        }

        String bucket = withoutPrefix.substring(0, slashIndex);
        String blobName = withoutPrefix.substring(slashIndex + 1);

        return BlobId.of(bucket, blobName);
    }

    @Override
    public void writeFile(String path, InputStream inputStream, long size) {
        try {
            BlobId blobId = parsePath(path);
            BlobInfo blobInfo = BlobInfo.newBuilder(blobId).build();

            // Stream upload to GCS
            try (var writer = storage.writer(blobInfo)) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    writer.write(java.nio.ByteBuffer.wrap(buffer, 0, bytesRead));
                }
            }

            log.debug("Wrote file to GCS: {}", path);
        } catch (IOException e) {
            throw new StorageException("Failed to write file to GCS: " + path, e);
        }
    }

    @Override
    public InputStream readFile(String path) {
        BlobId blobId = parsePath(path);
        Blob blob = storage.get(blobId);

        if (blob == null || !blob.exists()) {
            throw new StorageException("File not found in GCS: " + path);
        }

        return Channels.newInputStream(blob.reader());
    }

    @Override
    public void writeString(String path, String content) {
        BlobId blobId = parsePath(path);
        BlobInfo blobInfo = BlobInfo.newBuilder(blobId).build();

        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        storage.create(blobInfo, bytes);

        log.debug("Wrote string to GCS: {}", path);
    }

    @Override
    public String readString(String path) {
        BlobId blobId = parsePath(path);
        Blob blob = storage.get(blobId);

        if (blob == null || !blob.exists()) {
            throw new StorageException("File not found in GCS: " + path);
        }

        byte[] content = blob.getContent();
        return new String(content, StandardCharsets.UTF_8);
    }

    @Override
    public void append(String path, String content) {
        // GCS doesn't support true append - read, modify, write
        String existing = "";
        if (exists(path)) {
            existing = readString(path);
        }
        writeString(path, existing + content);
    }

    @Override
    public boolean exists(String path) {
        BlobId blobId = parsePath(path);
        Blob blob = storage.get(blobId);
        return blob != null && blob.exists();
    }

    @Override
    public List<String> listFiles(String prefix) {
        if (!prefix.startsWith("gs://")) {
            throw new IllegalArgumentException("GCS path must start with gs://");
        }

        String withoutPrefix = prefix.substring(5);
        int slashIndex = withoutPrefix.indexOf('/');

        String bucket;
        String blobPrefix;

        if (slashIndex == -1) {
            bucket = withoutPrefix;
            blobPrefix = "";
        } else {
            bucket = withoutPrefix.substring(0, slashIndex);
            blobPrefix = withoutPrefix.substring(slashIndex + 1);
        }

        List<String> files = new ArrayList<>();

        for (Blob blob : storage.list(bucket, Storage.BlobListOption.prefix(blobPrefix)).iterateAll()) {
            files.add("gs://" + bucket + "/" + blob.getName());
        }

        return files;
    }
}
