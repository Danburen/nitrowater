package cn.nitrowater.server.infra.entity;

import cn.nitrowater.server.infra.enums.IoMode;
import cn.nitrowater.server.infra.enums.Language;
import cn.nitrowater.server.infra.enums.SubmissionStatus;
import cn.nitrowater.server.infra.enums.Verdict;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/** A code submission and its judging result. */
@Getter
@Setter
@Entity
@Table(name = "submission")
public class Submission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    /** FK to {@code biz_user.id}. */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** Redundant uid for join-free audit/queries. */
    @Column(name = "uid", nullable = false)
    private Long uid;

    @Column(name = "problem_id", nullable = false)
    private Long problemId;

    @Column(name = "language", nullable = false, columnDefinition = "tinyint UNSIGNED")
    private Language language;

    @Column(name = "io_mode", nullable = false, columnDefinition = "tinyint UNSIGNED")
    private IoMode ioMode;

    @Column(name = "code", nullable = false, columnDefinition = "mediumtext")
    private String code;

    @Column(name = "status", nullable = false, columnDefinition = "tinyint UNSIGNED")
    private SubmissionStatus status = SubmissionStatus.QUEUED;

    /** {@code null} until judged (distinct from AC). */
    @Column(name = "verdict", columnDefinition = "tinyint UNSIGNED")
    private Verdict verdict;

    @Column(name = "passed", nullable = false)
    private Integer passed = 0;

    @Column(name = "total", nullable = false)
    private Integer total = 0;

    @Column(name = "score", nullable = false)
    private Integer score = 0;

    @Column(name = "time_ms")
    private Integer timeMs;

    @Column(name = "memory_kb")
    private Integer memoryKb;

    /** Per-case results as JSON text: {index,verdict,timeMs,memoryKb,message}. */
    @Column(name = "case_results", columnDefinition = "json")
    private String caseResults;

    /** Compile-error summary (truncated/escaped). */
    @Column(name = "compile_message", columnDefinition = "text")
    private String compileMessage;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "finished_at")
    private Instant finishedAt;
}
