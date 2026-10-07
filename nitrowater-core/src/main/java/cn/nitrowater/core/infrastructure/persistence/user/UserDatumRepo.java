package cn.nitrowater.core.infrastructure.persistence.user;

import org.springframework.data.jpa.repository.JpaRepository;
import cn.nitrowater.core.entity.user.UserDatum;

import java.util.List;
import java.util.Optional;

public interface UserDatumRepo extends JpaRepository<UserDatum, Long> {
    Optional<UserDatum> findByEmailHash(String emailHash);
    Optional<UserDatum> findByPhoneHash(String phoneHash);

    Optional<UserDatum> findUserDatumByUserUid(Long userUid);

    List<UserDatum> findUserDatumByEmailVerifiedFalse();

    void deleteByUserUid(long attr0);
}
