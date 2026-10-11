package cn.nitrowater.server.infra.entity;

import cn.nitrowater.server.infra.enums.ProblemStatus;
import cn.nitrowater.server.infra.enums.Verdict;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/** Per-user aggregate status for one problem (solved flag / progress / first-AC idempotency). */
@Getter
@Setter
@Entity
@Table(name = "user_problem_status")
public class UserProblemStatus {

    @EmbeddedId
    private UserProblemStatusId id;

    @Column(name = "status", nullable = false, columnDefinition = "tinyint UNSIGNED")
    private ProblemStatus status = ProblemStatus.ATTEMPTED;

    @Column(name = "best_verdict", columnDefinition = "tinyint UNSIGNED")
    private Verdict bestVerdict;

    @Column(name = "best_score", nullable = false)
    private Integer bestScore = 0;

    @Column(name = "attempts", nullable = false)
    private Integer attempts = 0;

    @Column(name = "first_solved_at")
    private Instant firstSolvedAt;

    @Column(name = "last_submission_at")
    private Instant lastSubmissionAt;
}
