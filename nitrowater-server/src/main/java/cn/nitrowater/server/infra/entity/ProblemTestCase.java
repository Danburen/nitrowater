package cn.nitrowater.server.infra.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** One test case of a {@link Problem} (unique per {@code (problem_id, idx)}). */
@Getter
@Setter
@Entity
@Table(name = "problem_test_case")
public class ProblemTestCase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "problem_id", nullable = false)
    private Long problemId;

    /** Zero-based case index. */
    @Column(name = "idx", nullable = false)
    private Integer idx;

    @Column(name = "stdin", columnDefinition = "mediumtext")
    private String stdin;

    @Column(name = "expected_output", columnDefinition = "mediumtext")
    private String expectedOutput;

    /** Whether this case is shown to users (sample). */
    @Column(name = "is_sample", nullable = false, columnDefinition = "tinyint(1)")
    private Boolean isSample = false;

    /** Optional partial score. */
    @Column(name = "score", nullable = false)
    private Integer score = 0;
}
