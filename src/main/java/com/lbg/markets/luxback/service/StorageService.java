package com.lbg.markets.luxback.service;

import java.io.InputStream;
import java.util.List;

/**
 * Abstraction for storage operations.
 * Allows profile-specific implementations (local filesystem vs GCS).
 */
public interface StorageService {

    /**
     * Write a file from an input stream
     *
     * @param path        the storage path
     * @param inputStream the file content
     * @param size        the file size in bytes
     */
    void writeFile(String path, InputStream inputStream, long size);

    /**
     * Read a file as an input stream
     *
     * @param path the storage path
     * @return input stream of file contents
     */
    InputStream readFile(String path);

    /**
     * Write a string to a file
     *
     * @param path    the storage path
     * @param content the string content
     */
    void writeString(String path, String content);

    /**
     * Read a file as a string
     *
     * @param path the storage path
     * @return file contents as string
     */
    String readString(String path);

    /**
     * Append content to a file
     *
     * @param path    the storage path
     * @param content the content to append
     */
    void append(String path, String content);

    /**
     * Check if a file exists
     *
     * @param path the storage path
     * @return true if file exists
     */
    boolean exists(String path);

    /**
     * List files with a given prefix
     *
     * @param prefix the path prefix
     * @return list of file paths
     */
    List<String> listFiles(String prefix);
}
