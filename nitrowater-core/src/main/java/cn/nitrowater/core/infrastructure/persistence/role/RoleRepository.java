package cn.nitrowater.core.infrastructure.persistence.role;

import org.springframework.data.jpa.repository.JpaRepository;
import cn.nitrowater.core.entity.role.Role;

import java.util.Optional;

public interface RoleRepository extends JpaRepository<Role, Long> {

    Optional<Role> findByCode(String code);
}
