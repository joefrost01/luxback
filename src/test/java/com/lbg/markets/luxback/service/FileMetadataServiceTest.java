package com.lbg.markets.luxback.service;

import com.lbg.markets.luxback.config.LuxBackConfig;
import com.lbg.markets.luxback.exception.FileSizeExceededException;
import com.lbg.markets.luxback.exception.InvalidFileTypeException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for FileMetadataService.
 * Tests filename generation, sanitization, and validation logic.
 */
class FileMetadataServiceTest {

    private FileMetadataService fileMetadataService;
    private LuxBackConfig config;

    @BeforeEach
    void setUp() {
        config = new LuxBackConfig();
        config.setMaxFileSize(104857600L); // 100MB
        config.setAllowedContentTypes(List.of(
                "application/pdf",
                "application/vnd.ms-excel",
                "text/plain",
                "image/png"
        ));

        fileMetadataService = new FileMetadataService(config);
    }

    @Test
    void generateStorageFilename_shouldPrefixWithTimestamp() {
        // Arrange
        String originalFilename = "document.pdf";

        // Act
        String storageFilename = fileMetadataService.generateStorageFilename(originalFilename);

        // Assert - Note: dots are replaced with underscores by sanitization
        assertThat(storageFilename)
                .matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}-\\d{2}-\\d{2}_document_pdf");
    }

    @Test
    void generateStorageFilename_shouldSanitizeDangerousCharacters() {
        // Arrange
        String originalFilename = "../../../etc/passwd";

        // Act
        String storageFilename = fileMetadataService.generateStorageFilename(originalFilename);

        // Assert
        assertThat(storageFilename).doesNotContain("..");
        assertThat(storageFilename).doesNotContain("/");
        assertThat(storageFilename).matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}-\\d{2}-\\d{2}_.*");
    }

    @Test
    void generateStorageFilename_shouldHandleSpecialCharacters() {
        // Arrange
        String originalFilename = "My Document (Draft) [v2].pdf";

        // Act
        String storageFilename = fileMetadataService.generateStorageFilename(originalFilename);

        // Assert - Special chars and dots replaced with underscores
        assertThat(storageFilename).doesNotContain("(");
        assertThat(storageFilename).doesNotContain(")");
        assertThat(storageFilename).doesNotContain("[");
        assertThat(storageFilename).doesNotContain("]");
        assertThat(storageFilename).matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}-\\d{2}-\\d{2}_.*_pdf");
    }

    @Test
    void generateStorageFilename_shouldHandleNullFilename() {
        // Act
        String storageFilename = fileMetadataService.generateStorageFilename(null);

        // Assert
        assertThat(storageFilename).contains("unnamed_file");
    }

    @Test
    void generateStorageFilename_shouldHandleEmptyFilename() {
        // Act
        String storageFilename = fileMetadataService.generateStorageFilename("");

        // Assert
        assertThat(storageFilename).contains("unnamed_file");
    }

    @Test
    void generateStorageFilename_shouldPreserveSafeCharacters() {
        // Arrange
        String originalFilename = "report-2024_final.xlsx";

        // Act
        String storageFilename = fileMetadataService.generateStorageFilename(originalFilename);

        // Assert - Hyphens and underscores preserved, dots replaced
        assertThat(storageFilename).contains("report-2024_final");
        assertThat(storageFilename).endsWith("_xlsx"); // Dot replaced with underscore
    }

    @Test
    void generateStorageFilename_shouldHandleFilenameStartingWithDot() {
        // Arrange
        String originalFilename = ".hidden-file.txt";

        // Act
        String storageFilename = fileMetadataService.generateStorageFilename(originalFilename);

        // Assert
        // Should not start with underscore (which would indicate a hidden file)
        assertThat(storageFilename)
                .matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}-\\d{2}-\\d{2}_file.*");
    }

    @Test
    void validateFileSize_shouldPassForValidSize() {
        // Arrange
        long fileSize = 50 * 1024 * 1024; // 50MB

        // Act & Assert - should not throw
        fileMetadataService.validateFileSize(fileSize);
    }

    @Test
    void validateFileSize_shouldPassForMaxSize() {
        // Arrange
        long fileSize = 104857600L; // Exactly 100MB

        // Act & Assert - should not throw
        fileMetadataService.validateFileSize(fileSize);
    }

    @Test
    void validateFileSize_shouldThrowForOversizedFile() {
        // Arrange
        long fileSize = 105 * 1024 * 1024; // 105MB

        // Act & Assert
        assertThatThrownBy(() -> fileMetadataService.validateFileSize(fileSize))
                .isInstanceOf(FileSizeExceededException.class)
                .hasMessageContaining("100 MB");
    }

    @Test
    void validateFileSize_shouldThrowForVeryLargeFile() {
        // Arrange
        long fileSize = 1024L * 1024L * 1024L; // 1GB

        // Act & Assert
        assertThatThrownBy(() -> fileMetadataService.validateFileSize(fileSize))
                .isInstanceOf(FileSizeExceededException.class)
                .hasMessageContaining("exceeds maximum size");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "application/pdf",
            "application/vnd.ms-excel",
            "text/plain",
            "image/png"
    })
    void validateContentType_shouldPassForAllowedTypes(String contentType) {
        // Act & Assert - should not throw
        fileMetadataService.validateContentType(contentType);
    }

    @Test
    void validateContentType_shouldPassForSubtypes() {
        // Arrange - Excel with full MIME type
        String contentType = "application/vnd.ms-excel; charset=UTF-8";

        // Act & Assert - should not throw (starts with allowed type)
        fileMetadataService.validateContentType(contentType);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "application/zip",
            "video/mp4",
            "audio/mpeg",
            "application/x-executable"
    })
    void validateContentType_shouldThrowForDisallowedTypes(String contentType) {
        // Act & Assert
        assertThatThrownBy(() -> fileMetadataService.validateContentType(contentType))
                .isInstanceOf(InvalidFileTypeException.class)
                .hasMessageContaining("not allowed");
    }

    @Test
    void validateContentType_shouldThrowForNullContentType() {
        // Act & Assert
        assertThatThrownBy(() -> fileMetadataService.validateContentType(null))
                .isInstanceOf(InvalidFileTypeException.class)
                .hasMessageContaining("could not be determined");
    }

    @Test
    void validateContentType_shouldThrowForEmptyContentType() {
        // Act & Assert
        assertThatThrownBy(() -> fileMetadataService.validateContentType(""))
                .isInstanceOf(InvalidFileTypeException.class)
                .hasMessageContaining("could not be determined");
    }

    @Test
    void validateContentType_shouldThrowForBlankContentType() {
        // Act & Assert
        assertThatThrownBy(() -> fileMetadataService.validateContentType("   "))
                .isInstanceOf(InvalidFileTypeException.class)
                .hasMessageContaining("could not be determined");
    }

    @Test
    void validateContentType_shouldBeCaseInsensitive() {
        // Arrange
        String contentType = "APPLICATION/PDF";

        // Act & Assert - should not throw
        fileMetadataService.validateContentType(contentType);
    }

    @Test
    void validateContentType_shouldAllowWhenNoRestrictions() {
        // Arrange - config with no restrictions
        config.setAllowedContentTypes(null);
        fileMetadataService = new FileMetadataService(config);

        // Act & Assert - should allow any type
        fileMetadataService.validateContentType("application/zip");
        fileMetadataService.validateContentType("video/mp4");
    }

    @Test
    void validateContentType_shouldAllowWhenEmptyRestrictions() {
        // Arrange - config with empty list
        config.setAllowedContentTypes(List.of());
        fileMetadataService = new FileMetadataService(config);

        // Act & Assert - should allow any type
        fileMetadataService.validateContentType("application/zip");
        fileMetadataService.validateContentType("video/mp4");
    }

    @Test
    void generateStorageFilename_shouldHandleUnicodeCharacters() {
        // Arrange
        String originalFilename = "文档.pdf"; // Chinese characters

        // Act
        String storageFilename = fileMetadataService.generateStorageFilename(originalFilename);

        // Assert - Unicode replaced with underscores, dots also replaced
        assertThat(storageFilename).matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}-\\d{2}-\\d{2}_.*_pdf");
        // Unicode characters should be replaced with underscores
        assertThat(storageFilename).doesNotContain("文");
        assertThat(storageFilename).doesNotContain("档");
    }

    @Test
    void generateStorageFilename_shouldHandleVeryLongFilenames() {
        // Arrange
        String originalFilename = "a".repeat(300) + ".pdf";

        // Act
        String storageFilename = fileMetadataService.generateStorageFilename(originalFilename);

        // Assert - Dots replaced with underscores
        assertThat(storageFilename).matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}-\\d{2}-\\d{2}_.*_pdf");
        // Just verify it doesn't crash and produces a valid filename
    }

    @Test
    void generateStorageFilename_shouldProduceDifferentFilenamesForSameInput() {
        // Arrange
        String originalFilename = "document.pdf";

        // Act - generate two filenames with a small delay
        String filename1 = fileMetadataService.generateStorageFilename(originalFilename);

        try {
            Thread.sleep(1100); // Wait over 1 second to ensure different timestamp
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        String filename2 = fileMetadataService.generateStorageFilename(originalFilename);

        // Assert - timestamps should be different
        assertThat(filename1).isNotEqualTo(filename2);
    }
}
