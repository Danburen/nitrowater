package cn.nitrowater.server.infra.entity;

import cn.nitrowater.server.infra.enums.Expertise;
import cn.nitrowater.server.infra.enums.ReplyStyle;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * Per-user NitroBot preferences.
 *
 * <p>{@code uid} is the primary key and logically references {@code nitrowater_account.user.uid}.
 * Structured choices (language, reply style, expertise) live in typed columns; anything free-form
 * (preferred stack, output format, banned words) stays in {@code preferencesJson}.</p>
 */
@Getter
@Setter
@Entity
@Table(name = "ai_user_profile")
public class AiUserProfile {

    @Id
    @Column(name = "uid", nullable = false)
    private Long uid;

    @Column(name = "language", nullable = false, length = 16)
    private String language = "zh-CN";

    @Column(name = "reply_style", nullable = false, columnDefinition = "tinyint UNSIGNED")
    private ReplyStyle replyStyle = ReplyStyle.BALANCED;

    @Column(name = "expertise", nullable = false, columnDefinition = "tinyint UNSIGNED")
    private Expertise expertise = Expertise.INTERMEDIATE;

    @Column(name = "timezone", length = 64)
    private String timezone;

    @Column(name = "preferences_json", columnDefinition = "json")
    private String preferencesJson;

    /** When false (default) the assistant never infers profile fields automatically. */
    @Column(name = "inference_opt_in", nullable = false, columnDefinition = "tinyint(1)")
    private Boolean inferenceOptIn = false;

    @Column(name = "version", nullable = false)
    private Integer version = 0;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();
}
