package cn.nitrowater.server.infra.entity;

import cn.nitrowater.server.infra.enums.AvatarSource;
import cn.nitrowater.server.infra.enums.UserStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * Business-side mirror of an account user.
 *
 * <p>{@code uid} logically references {@code nitrowater_account.user.uid} (no cross-DB FK).
 * Profile fields are snapshots copied from account on first touch / login; {@code points} is a
 * balance cache whose source of truth is {@link UserPointLog}.</p>
 */
@Getter
@Setter
@Entity
@Table(name = "biz_user")
public class BizUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    /** Logical link to account uid (no cross-DB FK). */
    @Column(name = "uid", nullable = false)
    private Long uid;

    @Column(name = "username", length = 64)
    private String username;

    @Column(name = "nickname", length = 64)
    private String nickname;

    /** Avatar resource uuid (references {@code resource.uuid} in nitrowater-resource). */
    @Column(name = "avatar_resource_uuid", length = 36)
    private String avatarResourceUuid;

    @Column(name = "avatar_source", nullable = false, columnDefinition = "tinyint UNSIGNED")
    private AvatarSource avatarSource = AvatarSource.ACCOUNT;

    /** When the profile snapshot was last refreshed from account. */
    @Column(name = "profile_synced_at")
    private Instant profileSyncedAt;

    /** Points balance cache (source of truth = {@link UserPointLog}). */
    @Column(name = "points", nullable = false)
    private Integer points = 0;

    @Column(name = "status", nullable = false, columnDefinition = "tinyint UNSIGNED")
    private UserStatus status = UserStatus.ACTIVE;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();
}
