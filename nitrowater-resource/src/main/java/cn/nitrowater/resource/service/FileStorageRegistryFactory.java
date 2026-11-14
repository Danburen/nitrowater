package cn.nitrowater.resource.service;

import cn.nitrowater.resource.infrastructure.domain.StorageBackend;
import cn.nitrowater.resource.infrastructure.exception.StorageException;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Resolves the {@link FileStorageService} implementation for a given {@link StorageBackend}. */
@Component
public class FileStorageRegistryFactory {

    private final Map<StorageBackend, FileStorageService> storages = new EnumMap<>(StorageBackend.class);

    public FileStorageRegistryFactory(List<FileStorageService> implementations) {
        for (FileStorageService storage : implementations) {
            storages.put(storage.backend(), storage);
        }
    }

    /**
     * @param backend desired backend
     * @return whether an implementation is registered for the backend
     */
    public boolean supports(StorageBackend backend) {
        return storages.containsKey(backend);
    }

    /**
     * @param backend desired backend
     * @return the registered implementation
     * @throws StorageException when no implementation is registered for the backend
     */
    public FileStorageService get(StorageBackend backend) {
        FileStorageService storage = storages.get(backend);
        if (storage == null) {
            throw new StorageException("No storage backend registered for " + backend, null);
        }
        return storage;
    }
}
