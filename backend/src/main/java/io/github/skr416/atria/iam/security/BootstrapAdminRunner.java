package io.github.skr416.atria.iam.security;

import io.github.skr416.atria.iam.model.User;
import io.github.skr416.atria.iam.repository.RoleRepository;
import io.github.skr416.atria.iam.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Automatically creates the initial system administrator account upon application startup
 * if a bootstrap password is configured and the user does not yet exist.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BootstrapAdminRunner implements ApplicationRunner {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${atria.security.bootstrap-admin.username:admin}")
    private String adminUsername;

    @Value("${atria.security.bootstrap-admin.password:}")
    private String adminPassword;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (adminPassword == null || adminPassword.isBlank()) {
            log.info("Bootstrap admin password is not configured; skipping bootstrap administrator initialization.");
            return;
        }

        if (userRepository.existsByUsername(adminUsername)) {
            log.debug("Bootstrap admin user '{}' already exists; skipping initialization.", adminUsername);
            return;
        }

        log.info("Bootstrap admin user '{}' not found; initializing initial super administrator...", adminUsername);

        User admin = new User();
        admin.setUsername(adminUsername);
        admin.setEmail("admin@atria.local");
        admin.setDisplayName("System Administrator");
        admin.setPasswordHash(passwordEncoder.encode(adminPassword));
        admin.setDepartmentId(1L);
        admin.setLocale("zh-TW");
        admin.setEnabled(true);
        admin.setFailedLoginAttempts(0);
        admin.setMustChangePassword(false);
        admin.setTokenVersion(0L);

        roleRepository.findByCode("SUPER_ADMIN").ifPresent(role -> {
            admin.getRoles().add(role);
            log.debug("Assigned SUPER_ADMIN role to bootstrap user '{}'", adminUsername);
        });

        userRepository.save(admin);
        log.info("Bootstrap admin user '{}' successfully created in root department (ID=1).", adminUsername);
    }
}
