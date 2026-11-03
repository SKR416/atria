package io.github.skr416.atria.iam.security;

import io.github.skr416.atria.iam.model.DataScopeType;
import lombok.Builder;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Authenticated principal stored in Spring SecurityContext for stateless JWT requests.
 * Holds effective {@link DataScopeType}, department context, role codes, and permission codes.
 */
@Getter
@Builder
public class AtriaUserPrincipal implements UserDetails {

    private final Long userId;
    private final String username;
    private final String displayName;
    private final Long departmentId;
    private final String departmentTreePath;
    private final String locale;
    private final boolean enabled;
    private final boolean mustChangePassword;
    private final long tokenVersion;
    private final DataScopeType dataScope;
    private final Set<String> roleCodes;
    private final Set<String> permissionCodes;

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        Set<GrantedAuthority> authorities = new LinkedHashSet<>();
        if (roleCodes != null) {
            for (String role : roleCodes) {
                authorities.add(new SimpleGrantedAuthority("ROLE_" + role));
            }
        }
        if (permissionCodes != null) {
            for (String perm : permissionCodes) {
                authorities.add(new SimpleGrantedAuthority(perm));
            }
        }
        return Collections.unmodifiableSet(authorities);
    }

    @Override
    public String getPassword() {
        return null;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }
}
