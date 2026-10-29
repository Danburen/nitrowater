package cn.nitrowater.server.infra.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/** Singleton row for administrator-controlled AI runtime settings. */
@Getter
@Setter
@Entity
@Table(name = "ai_admin_setting")
public class AiAdminSetting {

    public static final int SINGLETON_ID = 1;

    @Id
    @Column(name = "id", nullable = false, columnDefinition = "tinyint UNSIGNED")
    private Integer id = SINGLETON_ID;

    @Column(name = "daily_token_limit", nullable = false, columnDefinition = "bigint UNSIGNED")
    private Long dailyTokenLimit;

    @Column(name = "ai_enabled", nullable = false, columnDefinition = "tinyint(1)")
    private Boolean aiEnabled = true;

    @Column(name = "chat_enabled", nullable = false, columnDefinition = "tinyint(1)")
    private Boolean chatEnabled = true;

    @Column(name = "agent_enabled", nullable = false, columnDefinition = "tinyint(1)")
    private Boolean agentEnabled = true;

    @Column(name = "document_search_enabled", nullable = false, columnDefinition = "tinyint(1)")
    private Boolean documentSearchEnabled = true;

    @Column(name = "web_search_enabled", nullable = false, columnDefinition = "tinyint(1)")
    private Boolean webSearchEnabled = true;

    @Column(name = "code_runner_enabled", nullable = false, columnDefinition = "tinyint(1)")
    private Boolean codeRunnerEnabled = true;

    @Column(name = "skills_enabled", nullable = false, columnDefinition = "tinyint(1)")
    private Boolean skillsEnabled = true;

    @Column(name = "updated_by_uid")
    private Long updatedByUid;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @PrePersist
    @PreUpdate
    private void touch() {
        updatedAt = Instant.now();
    }
}
