package io.github.skr416.atria.iam.repository;

import io.github.skr416.atria.iam.model.User;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long>, JpaSpecificationExecutor<User> {

    @EntityGraph(attributePaths = {"roles", "roles.permissions"})
    Optional<User> findWithAuthoritiesById(Long id);

    @EntityGraph(attributePaths = {"roles", "roles.permissions"})
    Optional<User> findWithAuthoritiesByUsername(String username);

    @EntityGraph(attributePaths = {"roles"})
    Optional<User> findWithRolesById(Long id);

    boolean existsByUsername(String username);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByDepartmentId(Long departmentId);

    @Query("select count(u) from User u")
    long countActive();
}
