package cn.nitrowater.resource.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/** Persistence for {@link Resource} metadata rows. */
public interface ResourceRepository extends JpaRepository<Resource, Long> {

    /**
     * @param uuid opaque resource identifier
     * @return the resource, or empty when none matches
     */
    Optional<Resource> findByUuid(String uuid);
}
