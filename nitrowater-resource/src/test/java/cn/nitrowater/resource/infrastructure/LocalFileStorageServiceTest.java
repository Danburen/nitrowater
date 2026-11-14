package cn.nitrowater.resource.infrastructure;

import cn.nitrowater.resource.config.ResourceProperties;
import cn.nitrowater.resource.infrastructure.exception.StorageException;
import cn.nitrowater.resource.service.LocalFileStorageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies the local-filesystem backend: round-trip, atomic replace, traversal guard and the
 * "no partial file on failure" guarantee that the shared download path relies on.
 */
class LocalFileStorageServiceTest {

    private static final String KEY = "nitrowater/samples/system/2026/11/14/deadbeef.txt";

    @TempDir
    Path root;

    @Test
    void roundTripsAnObject() throws IOException {
        LocalFileStorageService storage = storage();

        storage.put(KEY, bytes("hello"), 5, "text/plain");

        assertThat(storage.exists(KEY)).isTrue();
        assertThat(storage.size(KEY)).isEqualTo(5);
        try (InputStream in = storage.get(KEY)) {
            assertThat(new String(in.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("hello");
        }

        storage.remove(KEY);
        assertThat(storage.exists(KEY)).isFalse();
        storage.remove(KEY);
    }

    @Test
    void replacesAnExistingObjectWithoutLeavingStagingFiles() throws IOException {
        LocalFileStorageService storage = storage();
        storage.put(KEY, bytes("first"), 5, "text/plain");

        // Publishing a new version reuses the key: the old bytes must be swapped, not appended to.
        storage.put(KEY, bytes("second"), 6, "text/plain");

        try (InputStream in = storage.get(KEY)) {
            assertThat(new String(in.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("second");
        }
        assertThat(stagingFiles(root)).isEmpty();
    }

    @Test
    void aFailedWriteLeavesNeitherAPartialObjectNorStagingFiles() {
        LocalFileStorageService storage = storage();

        assertThatThrownBy(() -> storage.put(KEY, brokenStream(), 100, "text/plain"))
                .isInstanceOf(StorageException.class);

        assertThat(storage.exists(KEY)).isFalse();
        assertThat(stagingFiles(root)).isEmpty();
    }

    @Test
    void rejectsKeysThatEscapeTheBaseDirectory() {
        LocalFileStorageService storage = storage();

        assertThatThrownBy(() -> storage.put("../escape.txt", bytes("x"), 1, "text/plain"))
                .isInstanceOf(StorageException.class);
        assertThat(root.resolveSibling("escape.txt")).doesNotExist();
    }

    @Test
    void reportsMissingObjects() {
        LocalFileStorageService storage = storage();

        assertThat(storage.exists(KEY)).isFalse();
        assertThatThrownBy(() -> storage.get(KEY)).isInstanceOf(StorageException.class);
    }

    private LocalFileStorageService storage() {
        return new LocalFileStorageService(new ResourceProperties("local", false, 600, 1024L, 1024L, null, null,
                new ResourceProperties.Local(root.toString()), null));
    }

    private static ByteArrayInputStream bytes(String value) {
        return new ByteArrayInputStream(value.getBytes(StandardCharsets.UTF_8));
    }

    private static List<Path> stagingFiles(Path root) {
        try (Stream<Path> files = Files.walk(root)) {
            return files.filter(path -> path.getFileName().toString().endsWith(".part")).toList();
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
    }

    /** Emits a few bytes, then fails: simulates a client that disconnects mid-upload. */
    private static InputStream brokenStream() {
        return new InputStream() {
            private int served;

            @Override
            public int read() throws IOException {
                if (served++ < 4) {
                    return 'x';
                }
                throw new IOException("client disconnected");
            }
        };
    }
}
