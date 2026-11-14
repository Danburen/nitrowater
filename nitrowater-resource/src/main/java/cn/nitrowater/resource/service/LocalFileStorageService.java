package cn.nitrowater.resource.service;

import cn.nitrowater.resource.config.ResourceProperties;
import cn.nitrowater.resource.infrastructure.domain.StorageBackend;
import cn.nitrowater.resource.infrastructure.exception.StorageException;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;

/**
 * Local-filesystem storage.
 *
 * <p>No presigning: {@link #presignedPutUrl}/{@link #presignedGetUrl} return {@code null} and
 * callers proxy bytes through this service. Object keys are resolved under a fixed base directory
 * with path-traversal protection.</p>
 */
@Component
public class LocalFileStorageService implements FileStorageService {

    private final Path basePath;

    public LocalFileStorageService(ResourceProperties properties) {
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
        Path staged = null;
        try {
            Files.createDirectories(target.getParent());
            // Stage next to the target, then rename. An interrupted upload (or a re-publish that
            // overwrites an existing key) must never leave a truncated object behind: readers see
            // either the old bytes or the complete new ones, never half of either.
            staged = Files.createTempFile(target.getParent(), ".upload-", ".part");
            Files.copy(in, staged, StandardCopyOption.REPLACE_EXISTING);
            // createTempFile is 0600; restore a normal umask-like mode so host-side backups and
            // tools running as the deploying user can read the tree (reads by the service itself
            // are unaffected either way). No-op on non-POSIX filesystems.
            relaxPermissionsQuietly(staged);
            Files.move(staged, target, StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException ex) {
            removeQuietly(staged);
            throw new StorageException("Failed to store local object " + objectKey, ex);
        }
    }

    /** Makes a staged object world-readable, ignoring filesystems without POSIX permissions. */
    private static void relaxPermissionsQuietly(Path path) {
        try {
            Files.setPosixFilePermissions(path,
                    PosixFilePermissions.fromString("rw-r--r--"));
        } catch (UnsupportedOperationException | IOException ignored) {
            // Windows / FAT: the default ACLs already allow the service (and backups) to read.
        }
    }

    /** Deletes a staged file after a failed write; never masks the original failure. */
    private static void removeQuietly(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // Best effort: the temp file is inside the managed tree and is cleaned by operators.
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
            throw new StorageException("Failed to stat local object " + objectKey, ex);
        }
    }

    @Override
    public void remove(String objectKey) {
        try {
            Files.deleteIfExists(resolve(objectKey));
        } catch (IOException ex) {
            throw new StorageException("Failed to remove local object " + objectKey, ex);
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
