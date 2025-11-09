package com.lbg.markets.luxback.service;

import com.lbg.markets.luxback.exception.StorageException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Local filesystem implementation of StorageService.
 * Active in dev-local profile for development and testing.
 */
@Service
@Profile("dev-local")
@Slf4j
public class LocalStorageService implements StorageService {

    @Override
    public void writeFile(String path, InputStream inputStream, long size) {
        try {
            Path filePath = Paths.get(path);
            Files.createDirectories(filePath.getParent());
            Files.copy(inputStream, filePath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            log.debug("Wrote file to local storage: {}", path);
        } catch (IOException e) {
            throw new StorageException("Failed to write file: " + path, e);
        }
    }

    @Override
    public InputStream readFile(String path) {
        try {
            Path filePath = Paths.get(path);
            if (!Files.exists(filePath)) {
                throw new StorageException("File not found: " + path);
            }
            return Files.newInputStream(filePath);
        } catch (IOException e) {
            throw new StorageException("Failed to read file: " + path, e);
        }
    }

    @Override
    public void writeString(String path, String content) {
        try {
            Path filePath = Paths.get(path);
            Files.createDirectories(filePath.getParent());
            Files.writeString(filePath, content, StandardCharsets.UTF_8);
            log.debug("Wrote string to local storage: {}", path);
        } catch (IOException e) {
            throw new StorageException("Failed to write string: " + path, e);
        }
    }

    @Override
    public String readString(String path) {
        try {
            Path filePath = Paths.get(path);
            if (!Files.exists(filePath)) {
                throw new StorageException("File not found: " + path);
            }
            return Files.readString(filePath, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new StorageException("Failed to read string: " + path, e);
        }
    }

    @Override
    public void append(String path, String content) {
        try {
            Path filePath = Paths.get(path);
            Files.createDirectories(filePath.getParent());
            Files.writeString(filePath, content, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            log.debug("Appended to file: {}", path);
        } catch (IOException e) {
            throw new StorageException("Failed to append to file: " + path, e);
        }
    }

    @Override
    public boolean exists(String path) {
        return Files.exists(Paths.get(path));
    }

    @Override
    public List<String> listFiles(String prefix) {
        try {
            Path dirPath = Paths.get(prefix);
            if (!Files.exists(dirPath) || !Files.isDirectory(dirPath)) {
                return new ArrayList<>();
            }

            try (Stream<Path> paths = Files.walk(dirPath)) {
                return paths
                        .filter(Files::isRegularFile)
                        .map(Path::toString)
                        .toList();
            }
        } catch (IOException e) {
            throw new StorageException("Failed to list files with prefix: " + prefix, e);
        }
    }
}
