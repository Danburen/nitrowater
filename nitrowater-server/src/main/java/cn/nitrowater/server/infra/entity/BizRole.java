package cn.nitrowater.server.infra.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/** Application-local role vocabulary (distinct from account's global roles). */
@Getter
@Setter
@Entity
@Table(name = "biz_role")
public class BizRole {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    /** e.g. {@code PLUGIN_ADMIN} / {@code MEMBER}. */
    @Column(name = "code", nullable = false, length = 64)
    private String code;

    @Column(name = "name", nullable = false, length = 64)
    private String name;

    @Column(name = "description", length = 255)
    private String description;

    @Column(name = "builtin", nullable = false, columnDefinition = "tinyint(1)")
    private Boolean builtin = false;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
}
