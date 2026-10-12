package cn.nitrowater.server.infra.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

/** Composite key for {@link ProblemTag}: (problem, tag). */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
@Embeddable
public class ProblemTagId implements Serializable {

    @Column(name = "problem_id", nullable = false)
    private Long problemId;

    @Column(name = "tag_id", nullable = false)
    private Long tagId;
}
