package pl.zai.backend.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import pl.zai.backend.common.ApiErrors;

/**
 * Enforces {@link RequiresAdmin}. Runs before the request body is read, so an unauthenticated
 * request gets 401 even if its body is also invalid.
 */
@Component
public class AdminAuthInterceptor implements HandlerInterceptor {

    public static final String ADMIN_ATTRIBUTE = "authenticatedAdmin";
    private static final String BEARER_PREFIX = "Bearer ";

    private final AuthService authService;

    public AdminAuthInterceptor(AuthService authService) {
        this.authService = authService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod method) || !method.hasMethodAnnotation(RequiresAdmin.class)) {
            return true;
        }
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            throw ApiErrors.unauthorized("Missing bearer token");
        }
        String token = header.substring(BEARER_PREFIX.length()).trim();
        AuthService.AuthenticatedAdmin admin = authService.authenticate(token)
                .orElseThrow(() -> ApiErrors.unauthorized("Invalid or expired token"));
        request.setAttribute(ADMIN_ATTRIBUTE, admin);
        return true;
    }
}
