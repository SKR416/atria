package io.github.skr416.atria.common.storage;

import java.io.InputStream;
import java.nio.file.Path;

/**
 * Pluggable storage abstraction for workflow attachments and STT audio uploads.
 * Default implementation stores files on local disk; swappable with S3/MinIO in cloud deployments.
 */
public interface FileStorageService {

    /**
     * Stores an uploaded stream under a relative category directory and returns the relative storage key.
     */
    String store(String category, String originalFilename, InputStream contentStream);

    /**
     * Resolves a stored key into a readable local path.
     */
    Path resolvePath(String storageKey);

    /**
     * Deletes a stored file if present.
     */
    boolean delete(String storageKey);
}
