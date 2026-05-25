package vip.mate.license;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Rejects all {@code /api/**} requests (except whitelisted paths) when the
 * trial license is not valid.
 * <p>
 * Runs BEFORE Spring Security's filter chain ({@code Order(1)}) so that
 * expired-license responses are consistent regardless of auth state.
 */
@Component
@RequiredArgsConstructor
@Order(1)
public class LicenseFilter extends OncePerRequestFilter {

    private final LicenseService licenseService;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        // Allow license status check, login, and non-API requests through
        return !path.startsWith("/api/")
                || path.equals("/api/v1/license/status")
                || path.equals("/api/v1/auth/login")
                || path.startsWith("/api/v1/setup/")
                || path.equals("/api/v1/settings/language");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (licenseService.isValid()) {
            filterChain.doFilter(request, response);
            return;
        }

        // License not valid — block with 403
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json;charset=UTF-8");
        LicenseStatusDTO status = licenseService.getCachedStatus();
        String msg = status != null ? status.getMessage() : "试用授权无效";
        response.getWriter().write(
                "{\"code\":403,\"msg\":\"LICENSE_EXPIRED\",\"data\":\"" + msg + "\"}"
        );
    }
}
