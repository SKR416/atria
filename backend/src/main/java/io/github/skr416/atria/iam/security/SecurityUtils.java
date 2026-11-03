package io.github.skr416.atria.iam.security;

import io.github.skr416.atria.common.api.ErrorCode;
import io.github.skr416.atria.common.exception.BusinessException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

/**
 * Helper utility to retrieve the current authenticated {@link AtriaUserPrincipal}.
 */
public final class SecurityUtils {

    private SecurityUtils() {
    }

    public static Optional<AtriaUserPrincipal> currentPrincipal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AtriaUserPrincipal principal) {
            return Optional.of(principal);
        }
        return Optional.empty();
    }

    public static AtriaUserPrincipal requirePrincipal() {
        return currentPrincipal()
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
    }

    public static Long currentUserIdOrNull() {
        return currentPrincipal().map(AtriaUserPrincipal::getUserId).orElse(null);
    }
}
