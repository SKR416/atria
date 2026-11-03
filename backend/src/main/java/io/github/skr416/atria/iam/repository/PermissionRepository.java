package io.github.skr416.atria.iam.repository;

import io.github.skr416.atria.iam.model.Permission;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PermissionRepository extends JpaRepository<Permission, Long> {

    List<Permission> findAllByOrderByModuleAscCodeAsc();
}
