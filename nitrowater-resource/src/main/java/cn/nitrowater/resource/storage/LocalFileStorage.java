package cn.nitrowater.resource.storage;

import cn.nitrowater.resource.config.ResourceProperties;
import cn.nitrowater.resource.domain.StorageBackend;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;

/**
 * Local-filesystem storage.
 *
 * <p>No presigning: {@link #presignedPutUrl}/{@link #presignedGetUrl} return {@code null} and
 * callers proxy bytes through this service. Object keys are resolved under a fixed base directory
 * with path-traversal protection.</p>
 */
@Component
public class LocalFileStorage implements FileStorage {

    private final Path basePath;

    public LocalFileStorage(ResourceProperties properties) {
        this.basePath = Paths.get(properties.local().basePath()).toAbsolutePath().normalize();
    }

    @Override
    public StorageBackend backend() {
        return StorageBackend.LOCAL;
    }

    @Override
    public String presignedPutUrl(String objectKey, String contentType, Duration expiry) {
        return null;
    }

    @Override
    public String presignedGetUrl(String objectKey, Duration expiry) {
        return null;
    }

    @Override
    public void put(String objectKey, InputStream in, long size, String contentType) {
        Path target = resolve(objectKey);
        try {
            Files.createDirectories(target.getParent());
            Files.copy(in, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            throw new StorageException("Failed to store local object " + objectKey, ex);
        }
    }

    @Override
    public InputStream get(String objectKey) {
        try {
            return Files.newInputStream(resolve(objectKey));
        } catch (IOException ex) {
            throw new StorageException("Failed to read local object " + objectKey, ex);
        }
    }

    @Override
    public boolean exists(String objectKey) {
        return Files.exists(resolve(objectKey));
    }

    @Override
    public long size(String objectKey) {
        try {
            return Files.size(resolve(objectKey));
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    @Override
    public void remove(String objectKey) {
        try {
            Files.deleteIfExists(resolve(objectKey));
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    /** Resolves an object key under the base directory, rejecting traversal outside it. */
    private Path resolve(String objectKey) {
        Path resolved = basePath.resolve(objectKey).normalize();
        if (!resolved.startsWith(basePath)) {
            throw new StorageException("Illegal object key: " + objectKey, null);
        }
        return resolved;
    }
}
