package vn.edu.phenikaa.ams.auth.application;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import vn.edu.phenikaa.ams.user.domain.AppUser;
import vn.edu.phenikaa.ams.user.infrastructure.UserRepository;

public class AccountSessionGuard extends OncePerRequestFilter {
    private final UserRepository users;

    public AccountSessionGuard(UserRepository users) {
        this.users = users;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AccountPrincipal principal)) {
            chain.doFilter(request, response);
            return;
        }

        HttpSession session = request.getSession(false);
        var user = users.findById(principal.getUserId()).orElse(null);
        if (user == null || user.getAccountStatus() != AppUser.Status.ACTIVE) {
            reject(session, response, 403, "Tài khoản không khả dụng.");
            return;
        }
        boolean currentGeneration = principal.isVersionedSession()
                && principal.getSessionGeneration() == user.getSessionGeneration();
        boolean preservedCurrent = principal.isVersionedSession() && session != null
                && SessionReference.fromId(session.getId()).equals(user.getPreservedSessionRef());
        if (!currentGeneration && !preservedCurrent) {
            reject(session, response, 401, "Phiên đăng nhập không còn hiệu lực.");
            return;
        }
        chain.doFilter(request, response);
    }

    private void reject(HttpSession session, HttpServletResponse response, int status, String detail)
            throws IOException {
        if (session != null) session.invalidate();
        SecurityContextHolder.clearContext();
        response.setStatus(status);
        response.setContentType("application/problem+json");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Cache-Control", "no-store");
        response.getWriter().write("{\"status\":" + status + ",\"detail\":\"" + detail + "\"}");
    }
}
