package io.github.skr416.atria.workflow.repository;

import io.github.skr416.atria.workflow.model.FormTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FormTemplateRepository extends JpaRepository<FormTemplate, Long> {

    Optional<FormTemplate> findByCode(String code);

    boolean existsByCode(String code);

    List<FormTemplate> findByEnabledTrueOrderByIdAsc();

    List<FormTemplate> findAllByOrderByIdAsc();
}
