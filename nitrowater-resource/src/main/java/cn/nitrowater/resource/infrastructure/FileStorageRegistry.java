package cn.nitrowater.resource.infrastructure;

import cn.nitrowater.resource.domain.StorageBackend;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Resolves the {@link FileStorage} implementation for a given {@link StorageBackend}. */
@Component
public class FileStorageRegistry {

    private final Map<StorageBackend, FileStorage> storages = new EnumMap<>(StorageBackend.class);

    public FileStorageRegistry(List<FileStorage> implementations) {
        for (FileStorage storage : implementations) {
            storages.put(storage.backend(), storage);
        }
    }

    /**
     * @param backend desired backend
     * @return the registered implementation
     * @throws StorageException when no implementation is registered for the backend
     */
    public FileStorage get(StorageBackend backend) {
        FileStorage storage = storages.get(backend);
        if (storage == null) {
            throw new StorageException("No storage backend registered for " + backend, null);
        }
        return storage;
    }
}
