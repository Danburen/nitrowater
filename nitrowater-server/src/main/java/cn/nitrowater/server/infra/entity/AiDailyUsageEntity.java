package cn.nitrowater.server.infra.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Formula;

import java.time.Instant;

/** JPA row holding one user's UTC-day AI token counters. */
@Getter
@Setter
@Entity
@Table(name = "ai_daily_usage")
public class AiDailyUsageEntity {

    @EmbeddedId
    private AiDailyUsageId id;

    @Column(name = "input_tokens", nullable = false, columnDefinition = "bigint UNSIGNED")
    private Long inputTokens = 0L;

    @Column(name = "output_tokens", nullable = false, columnDefinition = "bigint UNSIGNED")
    private Long outputTokens = 0L;

    @Column(name = "reserved_tokens", nullable = false, columnDefinition = "bigint UNSIGNED")
    private Long reservedTokens = 0L;

    @Column(name = "request_count", nullable = false)
    private Integer requestCount = 0;

    @Column(name = "estimated_requests", nullable = false)
    private Integer estimatedRequests = 0;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    /**
     * Derived total, mapped as a SQL expression so the admin listing can sort and filter on it
     * without loading every row into memory.
     */
    @Formula("input_tokens + output_tokens")
    private Long totalTokens;

    public AiDailyUsageEntity() {
    }

    public AiDailyUsageEntity(AiDailyUsageId id) {
        this.id = id;
    }

    @PrePersist
    @PreUpdate
    private void touch() {
        updatedAt = Instant.now();
    }
}
