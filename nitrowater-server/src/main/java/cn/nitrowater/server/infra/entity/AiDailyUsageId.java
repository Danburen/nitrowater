package cn.nitrowater.server.infra.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.time.LocalDate;

/** Composite key for one user's UTC-day AI usage row. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
@Embeddable
public class AiDailyUsageId implements Serializable {

    @Column(name = "uid", nullable = false)
    private Long uid;

    @Column(name = "usage_date", nullable = false)
    private LocalDate usageDate;
}
