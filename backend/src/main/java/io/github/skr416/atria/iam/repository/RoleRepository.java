package io.github.skr416.atria.iam.repository;

import io.github.skr416.atria.iam.model.Role;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RoleRepository extends JpaRepository<Role, Long> {

    @EntityGraph(attributePaths = {"permissions"})
    List<Role> findAllByOrderByIdAsc();

    @EntityGraph(attributePaths = {"permissions"})
    Optional<Role> findWithPermissionsById(Long id);

    boolean existsByCode(String code);

    Optional<Role> findByCode(String code);
}
