package io.github.skr416.atria.iam.model;

import io.github.skr416.atria.common.audit.BaseAuditEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.type.SqlTypes;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Getter
@Setter
@Entity
@Table(name = "iam_role")
@SQLRestriction("is_deleted = false")
public class Role extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "code", nullable = false, length = 64)
    private String code;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "name", nullable = false)
    private Map<String, String> name;

    @Enumerated(EnumType.STRING)
    @Column(name = "data_scope", nullable = false, length = 20)
    private DataScopeType dataScope = DataScopeType.SELF_ONLY;

    @Column(name = "built_in", nullable = false)
    private boolean builtIn = false;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "iam_role_permission",
            joinColumns = @JoinColumn(name = "role_id"),
            inverseJoinColumns = @JoinColumn(name = "permission_id"))
    private Set<Permission> permissions = new HashSet<>();
}
