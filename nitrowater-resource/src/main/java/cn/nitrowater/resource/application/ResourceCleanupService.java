package cn.nitrowater.resource.application;

import cn.nitrowater.resource.domain.Resource;
import cn.nitrowater.resource.domain.ResourceRepository;
import cn.nitrowater.resource.domain.ResourceStatus;
import cn.nitrowater.resource.infrastructure.FileStorage;
import cn.nitrowater.resource.infrastructure.FileStorageRegistry;
import cn.nitrowater.resource.infrastructure.PresignedUrlCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Reaps abandoned pending uploads.
 *
 * <p>{@code createUploadPolicy} inserts a {@code PENDING} row before the client uploads its bytes.
 * If the client never calls {@code confirmUpload} (closed tab, failed PUT, crashed client), that row
 * would stay pending forever and keep an empty/partial object around. After a grace period this job
 * removes any bytes and flips the row to {@link ResourceStatus#ORPHAN}, which is excluded from every
 * read/list path.</p>
 *
 * <p>The job is deliberately best-effort: when storage removal fails the row stays {@code PENDING}
 * so the next run retries instead of hiding a still-existing object. Runs are batched (200 rows) to
 * keep a single transaction bounded.</p>
 */
@Service
public class ResourceCleanupService {

    private static final Logger log = LoggerFactory.getLogger(ResourceCleanupService.class);

    private final ResourceRepository repository;
    private final FileStorageRegistry storageRegistry;
    private final PresignedUrlCache readUrlCache;
    private final int orphanPendingMinutes;

    /**
     * @param repository resource metadata repository
     * @param storageRegistry backend registry used to remove abandoned bytes
     * @param readUrlCache cache invalidated when a row is reaped
     * @param orphanPendingMinutes grace period for a pending upload; {@code <= 0} disables the job
     */
    public ResourceCleanupService(
            ResourceRepository repository,
            FileStorageRegistry storageRegistry,
            PresignedUrlCache readUrlCache,
            @Value("${app.resource.orphan-pending-minutes:60}") int orphanPendingMinutes) {
        this.repository = repository;
        this.storageRegistry = storageRegistry;
        this.readUrlCache = readUrlCache;
        this.orphanPendingMinutes = orphanPendingMinutes;
    }

    /**
     * Scheduled entry point. The interval and initial delay are configuration-driven so deployments
     * can tune them without a code change.
     */
    @Scheduled(
            fixedDelayString = "${app.resource.orphan-cleanup-interval-ms:3600000}",
            initialDelayString = "${app.resource.orphan-cleanup-initial-delay-ms:60000}")
    @Transactional
    public void reapPending() {
        int reaped = reapPendingAt(Instant.now());
        if (reaped > 0) {
            log.info("Reaped {} abandoned PENDING resource(s)", reaped);
        }
    }

    /**
     * Reaps pending rows last updated before the configured grace period.
     *
     * @param now clock instant used for the cutoff (injected in tests)
     * @return number of rows moved to {@link ResourceStatus#ORPHAN}
     */
    @Transactional
    public int reapPendingAt(Instant now) {
        if (orphanPendingMinutes <= 0) {
            return 0;
        }
        Instant cutoff = now.minus(Duration.ofMinutes(orphanPendingMinutes));
        List<Resource> stale = repository.findTop200ByStatusAndUpdatedAtBeforeOrderByUpdatedAtAsc(
                ResourceStatus.PENDING, cutoff);
        int reaped = 0;
        for (Resource resource : stale) {
            if (!removeBytes(resource)) {
                continue;
            }
            resource.setStatus(ResourceStatus.ORPHAN);
            resource.setUpdatedAt(now);
            repository.save(resource);
            readUrlCache.evict(resource.getUuid());
            reaped++;
        }
        return reaped;
    }

    /**
     * Removes the abandoned object if it exists.
     *
     * @return {@code true} when it is safe to mark the row orphaned; {@code false} when storage
     *         failed and the row should stay pending for a later retry
     */
    private boolean removeBytes(Resource resource) {
        try {
            FileStorage storage = storageRegistry.get(resource.getBackend());
            if (storage.exists(resource.getBucketKey())) {
                storage.remove(resource.getBucketKey());
            }
            return true;
        } catch (RuntimeException ex) {
            log.warn("Failed to remove abandoned object {} for resource {}; will retry",
                    resource.getBucketKey(), resource.getUuid(), ex);
            return false;
        }
    }
}
