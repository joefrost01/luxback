package com.lbg.markets.luxback.service;

import com.lbg.markets.luxback.exception.StorageException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for LocalStorageService.
 * Tests file operations with temporary filesystem.
 */
class LocalStorageServiceTest {

    @TempDir
    Path tempDir;

    private LocalStorageService storageService;

    @BeforeEach
    void setUp() {
        storageService = new LocalStorageService();
    }

    @Test
    void writeFile_shouldCreateFileWithContent() throws IOException {
        // Arrange
        String path = tempDir.resolve("test.txt").toString();
        String content = "Test file content";
        InputStream inputStream = new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));

        // Act
        storageService.writeFile(path, inputStream, content.length());

        // Assert
        assertThat(Files.exists(Path.of(path))).isTrue();
        String fileContent = Files.readString(Path.of(path), StandardCharsets.UTF_8);
        assertThat(fileContent).isEqualTo(content);
    }

    @Test
    void writeFile_shouldCreateParentDirectories() throws IOException {
        // Arrange
        String path = tempDir.resolve("subdir1/subdir2/test.txt").toString();
        String content = "Test content";
        InputStream inputStream = new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));

        // Act
        storageService.writeFile(path, inputStream, content.length());

        // Assert
        assertThat(Files.exists(Path.of(path))).isTrue();
        assertThat(Files.exists(tempDir.resolve("subdir1/subdir2"))).isTrue();
    }

    @Test
    void writeFile_shouldReplaceExistingFile() throws IOException {
        // Arrange
        String path = tempDir.resolve("test.txt").toString();
        Files.writeString(Path.of(path), "Old content", StandardCharsets.UTF_8);

        String newContent = "New content";
        InputStream inputStream = new ByteArrayInputStream(newContent.getBytes(StandardCharsets.UTF_8));

        // Act
        storageService.writeFile(path, inputStream, newContent.length());

        // Assert
        String fileContent = Files.readString(Path.of(path), StandardCharsets.UTF_8);
        assertThat(fileContent).isEqualTo(newContent);
    }

    @Test
    void readFile_shouldReturnInputStream() throws IOException {
        // Arrange
        String path = tempDir.resolve("test.txt").toString();
        String content = "Test file content";
        Files.writeString(Path.of(path), content, StandardCharsets.UTF_8);

        // Act
        InputStream inputStream = storageService.readFile(path);
        String readContent = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);

        // Assert
        assertThat(readContent).isEqualTo(content);
    }

    @Test
    void readFile_shouldThrowWhenFileNotFound() {
        // Arrange
        String path = tempDir.resolve("nonexistent.txt").toString();

        // Act & Assert
        assertThatThrownBy(() -> storageService.readFile(path))
                .isInstanceOf(StorageException.class)
                .hasMessageContaining("File not found");
    }

    @Test
    void writeString_shouldCreateFileWithStringContent() throws IOException {
        // Arrange
        String path = tempDir.resolve("test.txt").toString();
        String content = "String content";

        // Act
        storageService.writeString(path, content);

        // Assert
        String fileContent = Files.readString(Path.of(path), StandardCharsets.UTF_8);
        assertThat(fileContent).isEqualTo(content);
    }

    @Test
    void writeString_shouldCreateParentDirectories() {
        // Arrange
        String path = tempDir.resolve("subdir/test.txt").toString();
        String content = "Test content";

        // Act
        storageService.writeString(path, content);

        // Assert
        assertThat(Files.exists(Path.of(path))).isTrue();
    }

    @Test
    void readString_shouldReturnFileContent() throws IOException {
        // Arrange
        String path = tempDir.resolve("test.txt").toString();
        String content = "Test content with unicode: 文档";
        Files.writeString(Path.of(path), content, StandardCharsets.UTF_8);

        // Act
        String readContent = storageService.readString(path);

        // Assert
        assertThat(readContent).isEqualTo(content);
    }

    @Test
    void readString_shouldThrowWhenFileNotFound() {
        // Arrange
        String path = tempDir.resolve("nonexistent.txt").toString();

        // Act & Assert
        assertThatThrownBy(() -> storageService.readString(path))
                .isInstanceOf(StorageException.class)
                .hasMessageContaining("File not found");
    }

    @Test
    void append_shouldAppendToExistingFile() throws IOException {
        // Arrange
        String path = tempDir.resolve("test.txt").toString();
        Files.writeString(Path.of(path), "First line\n", StandardCharsets.UTF_8);

        // Act
        storageService.append(path, "Second line\n");
        storageService.append(path, "Third line\n");

        // Assert
        String content = Files.readString(Path.of(path), StandardCharsets.UTF_8);
        assertThat(content).isEqualTo("First line\nSecond line\nThird line\n");
    }

    @Test
    void append_shouldCreateFileIfNotExists() throws IOException {
        // Arrange
        String path = tempDir.resolve("test.txt").toString();

        // Act
        storageService.append(path, "First line\n");

        // Assert
        String content = Files.readString(Path.of(path), StandardCharsets.UTF_8);
        assertThat(content).isEqualTo("First line\n");
    }

    @Test
    void append_shouldCreateParentDirectories() {
        // Arrange
        String path = tempDir.resolve("subdir/test.txt").toString();

        // Act
        storageService.append(path, "Content");

        // Assert
        assertThat(Files.exists(Path.of(path))).isTrue();
    }

    @Test
    void exists_shouldReturnTrueForExistingFile() throws IOException {
        // Arrange
        String path = tempDir.resolve("test.txt").toString();
        Files.writeString(Path.of(path), "content", StandardCharsets.UTF_8);

        // Act
        boolean exists = storageService.exists(path);

        // Assert
        assertThat(exists).isTrue();
    }

    @Test
    void exists_shouldReturnFalseForNonexistentFile() {
        // Arrange
        String path = tempDir.resolve("nonexistent.txt").toString();

        // Act
        boolean exists = storageService.exists(path);

        // Assert
        assertThat(exists).isFalse();
    }

    @Test
    void exists_shouldReturnTrueForDirectory() throws IOException {
        // Arrange
        Path dir = tempDir.resolve("subdir");
        Files.createDirectory(dir);

        // Act
        boolean exists = storageService.exists(dir.toString());

        // Assert
        assertThat(exists).isTrue();
    }

    @Test
    void listFiles_shouldReturnAllFilesInDirectory() throws IOException {
        // Arrange
        String prefix = tempDir.toString();
        Files.writeString(tempDir.resolve("file1.txt"), "content1", StandardCharsets.UTF_8);
        Files.writeString(tempDir.resolve("file2.txt"), "content2", StandardCharsets.UTF_8);

        Path subdir = tempDir.resolve("subdir");
        Files.createDirectory(subdir);
        Files.writeString(subdir.resolve("file3.txt"), "content3", StandardCharsets.UTF_8);

        // Act
        List<String> files = storageService.listFiles(prefix);

        // Assert
        assertThat(files).hasSize(3);
        assertThat(files).anyMatch(path -> path.endsWith("file1.txt"));
        assertThat(files).anyMatch(path -> path.endsWith("file2.txt"));
        assertThat(files).anyMatch(path -> path.endsWith("file3.txt"));
    }

    @Test
    void listFiles_shouldReturnEmptyListForNonexistentDirectory() {
        // Arrange
        String prefix = tempDir.resolve("nonexistent").toString();

        // Act
        List<String> files = storageService.listFiles(prefix);

        // Assert
        assertThat(files).isEmpty();
    }

    @Test
    void listFiles_shouldReturnEmptyListForFile() throws IOException {
        // Arrange
        String path = tempDir.resolve("test.txt").toString();
        Files.writeString(Path.of(path), "content", StandardCharsets.UTF_8);

        // Act
        List<String> files = storageService.listFiles(path);

        // Assert
        assertThat(files).isEmpty();
    }

    @Test
    void listFiles_shouldOnlyReturnFiles() throws IOException {
        // Arrange
        String prefix = tempDir.toString();
        Files.writeString(tempDir.resolve("file.txt"), "content", StandardCharsets.UTF_8);

        Path subdir = tempDir.resolve("subdir");
        Files.createDirectory(subdir);

        // Act
        List<String> files = storageService.listFiles(prefix);

        // Assert - should only include files, not directories
        assertThat(files).hasSize(1);
        assertThat(files.get(0)).endsWith("file.txt");
    }

    @Test
    void writeFile_shouldHandleLargeFile() throws IOException {
        // Arrange
        String path = tempDir.resolve("large.bin").toString();
        byte[] largeContent = new byte[10 * 1024 * 1024]; // 10MB
        // Fill with pattern
        for (int i = 0; i < largeContent.length; i++) {
            largeContent[i] = (byte) (i % 256);
        }
        InputStream inputStream = new ByteArrayInputStream(largeContent);

        // Act
        storageService.writeFile(path, inputStream, largeContent.length);

        // Assert
        assertThat(Files.size(Path.of(path))).isEqualTo(largeContent.length);
    }

    @Test
    void writeString_shouldHandleMultilineContent() throws IOException {
        // Arrange
        String path = tempDir.resolve("multiline.txt").toString();
        String content = "Line 1\nLine 2\nLine 3\n";

        // Act
        storageService.writeString(path, content);

        // Assert
        String readContent = Files.readString(Path.of(path), StandardCharsets.UTF_8);
        assertThat(readContent).isEqualTo(content);
    }

    @Test
    void append_shouldHandleConcurrentAppends() throws IOException, InterruptedException {
        // Arrange
        String path = tempDir.resolve("concurrent.txt").toString();

        // Act - simulate multiple appends
        Thread t1 = new Thread(() -> {
            for (int i = 0; i < 10; i++) {
                storageService.append(path, "Thread1-" + i + "\n");
            }
        });

        Thread t2 = new Thread(() -> {
            for (int i = 0; i < 10; i++) {
                storageService.append(path, "Thread2-" + i + "\n");
            }
        });

        t1.start();
        t2.start();
        t1.join();
        t2.join();

        // Assert - verify file has 20 lines
        String content = Files.readString(Path.of(path), StandardCharsets.UTF_8);
        long lineCount = content.lines().count();
        assertThat(lineCount).isEqualTo(20);
    }
}