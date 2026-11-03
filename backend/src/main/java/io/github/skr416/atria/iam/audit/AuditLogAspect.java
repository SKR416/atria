package io.github.skr416.atria.iam.audit;

import io.github.skr416.atria.common.api.ApiResponse;
import io.github.skr416.atria.common.util.ClientIpResolver;
import io.github.skr416.atria.iam.model.AuditLogEntry;
import io.github.skr416.atria.iam.repository.AuditLogRepository;
import io.github.skr416.atria.iam.security.AtriaUserPrincipal;
import io.github.skr416.atria.iam.security.SecurityUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.time.Instant;

/**
 * AOP Aspect recording administrative mutations into {@code iam_audit_log} (ADR-0004).
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class AuditLogAspect {

    private final AuditLogRepository auditLogRepository;

    @AfterReturning(pointcut = "@annotation(auditLog)", returning = "result")
    public void recordAuditLog(JoinPoint joinPoint, AuditLog auditLog, Object result) {
        try {
            AtriaUserPrincipal principal = SecurityUtils.currentPrincipal().orElse(null);
            Long actorId = principal != null ? principal.getUserId() : null;
            String actorUsername = principal != null ? principal.getUsername() : "anonymous";

            String clientIp = resolveClientIp();
            String targetId = resolveTargetId(joinPoint, result);
            String detail = summarizeArgumentsSafely(joinPoint);

            AuditLogEntry entry = new AuditLogEntry();
            entry.setActorId(actorId);
            entry.setActorUsername(actorUsername);
            entry.setAction(auditLog.action());
            entry.setTargetType(auditLog.targetType().isBlank() ? null : auditLog.targetType());
            entry.setTargetId(targetId);
            entry.setDetail(detail);
            entry.setClientIp(clientIp);
            entry.setCreatedAt(Instant.now());

            auditLogRepository.save(entry);
        } catch (Exception e) {
            log.error("Failed to record audit log for action: {}", auditLog.action(), e);
        }
    }

    private String resolveClientIp() {
        RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
        if (attrs instanceof ServletRequestAttributes servletAttrs) {
            HttpServletRequest request = servletAttrs.getRequest();
            return ClientIpResolver.resolve(request);
        }
        return "0.0.0.0";
    }

    private String resolveTargetId(JoinPoint joinPoint, Object result) {
        Object[] args = joinPoint.getArgs();
        if (args != null && args.length > 0 && args[0] != null) {
            Object firstArg = args[0];
            if (firstArg instanceof Number || firstArg instanceof String) {
                return String.valueOf(firstArg);
            }
        }

        if (result != null) {
            Object targetObj = result;
            if (result instanceof ApiResponse<?> apiResponse && apiResponse.getData() != null) {
                targetObj = apiResponse.getData();
            }
            String extractedId = invokeIdGetter(targetObj);
            if (extractedId != null) {
                return extractedId;
            }
        }

        // Secondary fallback: check other arguments for an ID getter
        if (args != null) {
            for (Object arg : args) {
                if (arg != null && !(arg instanceof Number) && !(arg instanceof String)) {
                    String extractedId = invokeIdGetter(arg);
                    if (extractedId != null) {
                        return extractedId;
                    }
                }
            }
        }

        return null;
    }

    private String invokeIdGetter(Object target) {
        if (target == null) {
            return null;
        }
        Class<?> clazz = target.getClass();
        for (String methodName : new String[]{"getId", "id"}) {
            try {
                Method method = clazz.getMethod(methodName);
                Object val = method.invoke(target);
                if (val != null) {
                    return String.valueOf(val);
                }
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private String summarizeArgumentsSafely(JoinPoint joinPoint) {
        Object[] args = joinPoint.getArgs();
        if (args == null || args.length == 0) {
            return joinPoint.getSignature().getName() + "()";
        }
        StringBuilder sb = new StringBuilder(joinPoint.getSignature().getName()).append("(");
        for (int i = 0; i < args.length; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            Object arg = args[i];
            if (arg == null) {
                sb.append("null");
            } else {
                String className = arg.getClass().getSimpleName();
                String stringVal = maskSensitiveInfo(String.valueOf(arg));
                if (stringVal.length() > 200) {
                    stringVal = stringVal.substring(0, 197) + "...";
                }
                sb.append(className).append("=").append(stringVal);
            }
        }
        sb.append(")");
        String summary = sb.toString();
        if (summary.length() > 1000) {
            return summary.substring(0, 997) + "...";
        }
        return summary;
    }

    private String maskSensitiveInfo(String input) {
        if (input == null) {
            return null;
        }
        return input.replaceAll("(?i)(password|secret|token|credential|rawPassword)=[^,}\\)]+", "$1=***");
    }
}
