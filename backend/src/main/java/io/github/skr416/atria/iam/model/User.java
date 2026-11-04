package io.github.skr416.atria.iam.model;

import io.github.skr416.atria.common.audit.BaseAuditEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;

import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

@Getter
@Setter
@Entity
@Table(name = "iam_user")
@SQLRestriction("is_deleted = false")
public class User extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "username", nullable = false, length = 64)
    private String username;

    @Column(name = "email", length = 255)
    private String email;

    /** DelegatingPasswordEncoder format ({bcrypt}...). A value without the bcrypt prefix triggers DBA emergency recovery. */
    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "display_name", nullable = false, length = 100)
    private String displayName;

    @Column(name = "department_id")
    private Long departmentId;

    @Column(name = "hire_date")
    private LocalDate hireDate;

    @Column(name = "locale", nullable = false, length = 10)
    private String locale = "zh-TW";

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    @Column(name = "failed_login_attempts", nullable = false)
    private int failedLoginAttempts = 0;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "must_change_password", nullable = false)
    private boolean mustChangePassword = false;

    /** Bumped on password change / logout to invalidate every outstanding JWT of this user. */
    @Column(name = "token_version", nullable = false)
    private long tokenVersion = 0L;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @Column(name = "password_changed_at")
    private Instant passwordChangedAt;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "iam_user_role",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id"))
    private Set<Role> roles = new HashSet<>();

    public boolean isLocked(Instant now) {
        return lockedUntil != null && lockedUntil.isAfter(now);
    }
}
