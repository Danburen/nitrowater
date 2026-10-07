package cn.nitrowater.core.entity.user;

import jakarta.persistence.CascadeType;
import jakarta.persistence.*;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.*;
import org.hibernate.proxy.HibernateProxy;
import cn.nitrowater.core.api.VO.OptionVO;

import java.time.Instant;
import java.util.Objects;

@Getter
@Setter
@RequiredArgsConstructor
@Entity
@Table(name = "user")
public class User {
    @Id
    @Column(name = "uid", nullable = false)
    private Long uid;

    @Column(name = "username", nullable = false, length = 32)
    private String username;

    @Column(name = "password_hash")
    private String passwordHash;

    @ColumnDefault("'0'")
    @Column(name = "account_status", columnDefinition = "tinyint UNSIGNED")
    private AccountStatus accountStatus = AccountStatus.ACTIVE;

    @Column(name = "status_changed_at")
    private Instant statusChangedAt;

    @Column(name = "updated_at")
    @UpdateTimestamp
    private Instant updatedAt;

    @ColumnDefault("CURRENT_TIMESTAMP")
    @Column(name = "created_at")
    @CreationTimestamp
    private Instant createdAt;

    @Size(max = 12)
    @Column(name = "nickname", length = 12)
    private String nickname;

    @Column(name = "last_active_at")
    private Instant lastActiveAt;

    @ColumnDefault("'0'")
    @Column(name = "user_type", columnDefinition = "tinyint UNSIGNED")
    private UserType userType = UserType.COMMON;

    @ColumnDefault("'1'")
    @Column(name = "level", columnDefinition = "tinyint UNSIGNED")
    private Short level = 1;

    @ColumnDefault("'0'")
    @Column(name = "exp", columnDefinition = "int UNSIGNED")
    private Integer exp = 0;

    @Override
    public final boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;
        Class<?> oEffectiveClass = o instanceof HibernateProxy ? ((HibernateProxy) o).getHibernateLazyInitializer().getPersistentClass() : o.getClass();
        Class<?> thisEffectiveClass = this instanceof HibernateProxy ? ((HibernateProxy) this).getHibernateLazyInitializer().getPersistentClass() : this.getClass();
        if (thisEffectiveClass != oEffectiveClass) return false;
        User user = (User) o;
        return getUid() != null && Objects.equals(getUid(), user.getUid());
    }

    @Override
    public final int hashCode() {
        return this instanceof HibernateProxy ? ((HibernateProxy) this).getHibernateLazyInitializer().getPersistentClass().hashCode() : getClass().hashCode();
    }

    @OneToOne(mappedBy = "user", cascade = CascadeType.ALL)
    private UserDatum userDatum;

    public OptionVO<Long> toOptionVO(){
        return OptionVO.of(this.uid, this.nickname, this.nickname, ! this.accountStatus.equals(AccountStatus.ACTIVE));
    }

    public String getDisplayName(){
        return this.nickname != null ? this.nickname : this.username;
    }
}


