package io.github.skr416.atria.common.storage;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Local filesystem implementation of {@link FileStorageService}.
 */
@Slf4j
@Service
public class LocalFileStorageService implements FileStorageService {

    private final Path rootDir;

    public LocalFileStorageService(@Value("${atria.storage.local-dir:./uploads}") String localDir) {
        this.rootDir = Paths.get(localDir).toAbsolutePath().normalize();
    }

    @Override
    public String store(String category, String originalFilename, InputStream contentStream) {
        try {
            String safeCategory = category.replaceAll("[^a-zA-Z0-9_-]", "general");
            String ext = "";
            if (originalFilename != null && originalFilename.contains(".")) {
                ext = originalFilename.substring(originalFilename.lastIndexOf('.')).replaceAll("[^a-zA-Z0-9.]", "");
            }
            String dateSegment = LocalDate.now().toString();
            String relativeKey = safeCategory + "/" + dateSegment + "/" + UUID.randomUUID() + ext;
            Path targetPath = resolvePath(relativeKey);
            Files.createDirectories(targetPath.getParent());
            Files.copy(contentStream, targetPath, StandardCopyOption.REPLACE_EXISTING);
            return relativeKey;
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to store file on local storage", ex);
        }
    }

    @Override
    public Path resolvePath(String storageKey) {
        Path resolved = rootDir.resolve(storageKey).normalize();
        if (!resolved.startsWith(rootDir)) {
            throw new SecurityException("Invalid storage key outside root directory");
        }
        return resolved;
    }

    @Override
    public boolean delete(String storageKey) {
        try {
            return Files.deleteIfExists(resolvePath(storageKey));
        } catch (IOException ex) {
            log.warn("Failed to delete storage key: {}", storageKey, ex);
            return false;
        }
    }
}
