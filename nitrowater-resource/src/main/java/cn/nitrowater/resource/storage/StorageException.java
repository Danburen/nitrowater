package cn.nitrowater.resource.storage;

/** Signals a storage-layer failure (backend error, illegal key). */
public class StorageException extends RuntimeException {

    /**
     * @param message description
     * @param cause underlying cause, or {@code null}
     */
    public StorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
