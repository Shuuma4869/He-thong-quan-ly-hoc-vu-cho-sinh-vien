package vn.edu.phenikaa.ams.auth.application;

import jakarta.servlet.http.HttpSession;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.session.Session;
import org.springframework.session.data.redis.RedisIndexedSessionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import vn.edu.phenikaa.ams.auth.api.SecurityDtos.PasswordRequest;
import vn.edu.phenikaa.ams.auth.api.SecurityDtos.SessionView;
import vn.edu.phenikaa.ams.user.domain.AppUser;
import vn.edu.phenikaa.ams.user.infrastructure.UserRepository;

@Service
public class AccountSecurityService {
    private static final Logger LOG = LoggerFactory.getLogger(AccountSecurityService.class);
    private final UserRepository users;
    private final RedisIndexedSessionRepository sessions;
    private final PasswordEncoder encoder;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;

    public AccountSecurityService(UserRepository users, RedisIndexedSessionRepository sessions,
            PasswordEncoder encoder, JdbcTemplate jdbc, PlatformTransactionManager transactionManager) {
        this.users = users;
        this.sessions = sessions;
        this.encoder = encoder;
        this.jdbc = jdbc;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    public List<SessionView> list(AccountPrincipal principal, HttpSession current) {
        AppUser account = active(principal.getUserId());
        String currentId = currentId(current);
        verifyCurrent(principal, account, SessionReference.fromId(currentId));
        var owned = sessions.findByPrincipalName(account.getEmail());
        if (!owned.containsKey(currentId)) throw inconsistentIndex();
        return owned.values().stream()
                .filter(session -> usable(session, account))
                .map(session -> new SessionView(SessionReference.fromId(session.getId()),
                        session.getId().equals(currentId), session.getCreationTime(),
                        session.getLastAccessedTime(),
                        session.getLastAccessedTime().plus(session.getMaxInactiveInterval())))
                .sorted(Comparator.comparing(SessionView::current).reversed()
                        .thenComparing(SessionView::createdAt, Comparator.reverseOrder())
                        .thenComparing(SessionView::sessionRef))
                .toList();
    }

    public void revokeOne(AccountPrincipal principal, HttpSession current, String sessionRef) {
        if (!SessionReference.isValid(sessionRef)) throw notFound();
        AppUser account = active(principal.getUserId());
        String currentId = currentId(current);
        verifyCurrent(principal, account, SessionReference.fromId(currentId));
        var owned = sessions.findByPrincipalName(account.getEmail());
        if (!owned.containsKey(currentId)) throw inconsistentIndex();
        for (Session session : owned.values()) {
            if (SessionReference.fromId(session.getId()).equals(sessionRef) && usable(session, account)) {
                if (session.getId().equals(currentId)) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Không thể đăng xuất phiên hiện tại tại đây.");
                }
                deleteAndVerify(session.getId());
                audit(principal.getUserId(), "SESSION_REVOKED", "{}");
                return;
            }
        }
        throw notFound();
    }

    public void revokeOthers(AccountPrincipal principal, HttpSession current) {
        String currentId = currentId(current);
        String currentRef = SessionReference.fromId(currentId);
        AppUser before = active(principal.getUserId());
        var owned = sessions.findByPrincipalName(before.getEmail());
        if (!owned.containsKey(currentId)) throw inconsistentIndex();
        long count = owned.values().stream()
                .filter(session -> !session.getId().equals(currentId) && usable(session, before)).count();
        transactions.executeWithoutResult(status -> {
            AppUser locked = lockActive(principal.getUserId());
            verifyCurrent(principal, locked, currentRef);
            locked.revokeOtherSessions(currentRef);
            audit(principal.getUserId(), "OTHER_SESSIONS_REVOKED", "{\"affectedSessions\":" + count + "}");
        });
        removeOldIndexedSessions(principal.getUserId(), currentId);
    }

    public void changePassword(AccountPrincipal principal, HttpSession current, PasswordRequest request) {
        if (!AccountPasswordEncoder.hasValidLength(request.newPassword())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Mật khẩu cần 12–64 ký tự và không quá 72 byte UTF-8.");
        }
        String currentId = currentId(current);
        String currentRef = SessionReference.fromId(currentId);
        AppUser before = active(principal.getUserId());
        if (!sessions.findByPrincipalName(before.getEmail()).containsKey(currentId)) throw inconsistentIndex();
        transactions.executeWithoutResult(status -> {
            AppUser locked = lockActive(principal.getUserId());
            verifyCurrent(principal, locked, currentRef);
            if (!encoder.matches(request.currentPassword(), locked.getPasswordHash())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mật khẩu hiện tại không đúng.");
            }
            if (encoder.matches(request.newPassword(), locked.getPasswordHash())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mật khẩu mới phải khác mật khẩu hiện tại.");
            }
            locked.changePassword(encoder.encode(request.newPassword()), currentRef);
            audit(principal.getUserId(), "PASSWORD_CHANGED", "{}");
        });
        removeOldIndexedSessions(principal.getUserId(), currentId);
    }

    private void removeOldIndexedSessions(UUID userId, String currentId) {
        AppUser account = active(userId);
        try {
            var indexed = sessions.findByPrincipalName(account.getEmail());
            if (!indexed.containsKey(currentId)) throw inconsistentIndex();
            for (Session session : indexed.values()) {
                if (!session.getId().equals(currentId) && !usable(session, account)) {
                    sessions.deleteById(session.getId());
                    if (sessions.findById(session.getId()) != null) {
                        throw new IllegalStateException("Session deletion not confirmed");
                    }
                }
            }
        } catch (RuntimeException exception) {
            LOG.warn("Session cleanup could not be confirmed: {}", exception.getClass().getSimpleName());
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Thay đổi bảo mật đã được lưu nhưng chưa xác nhận được việc dọn phiên cũ. Vui lòng tải lại trạng thái.");
        }
    }

    private void deleteAndVerify(String sessionId) {
        try {
            sessions.deleteById(sessionId);
            if (sessions.findById(sessionId) != null) throw new IllegalStateException("Session deletion not confirmed");
        } catch (RuntimeException exception) {
            LOG.warn("Session revocation could not be confirmed: {}", exception.getClass().getSimpleName());
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Không thể xác nhận phiên đã được đăng xuất. Vui lòng tải lại trạng thái.");
        }
    }

    private boolean usable(Session session, AppUser account) {
        SecurityContext context = session.getAttribute("SPRING_SECURITY_CONTEXT");
        if (context == null || !(context.getAuthentication().getPrincipal() instanceof AccountPrincipal principal)) {
            return false;
        }
        if (!principal.getUserId().equals(account.getId()) || !principal.isVersionedSession()) return false;
        return principal.getSessionGeneration() == account.getSessionGeneration()
                || SessionReference.fromId(session.getId()).equals(account.getPreservedSessionRef());
    }

    private void verifyCurrent(AccountPrincipal principal, AppUser account, String currentRef) {
        if (!principal.isVersionedSession() ||
                (principal.getSessionGeneration() != account.getSessionGeneration()
                        && !currentRef.equals(account.getPreservedSessionRef()))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Phiên đăng nhập đã thay đổi. Vui lòng tải lại trang.");
        }
    }

    private AppUser active(UUID userId) {
        return users.findById(userId).filter(user -> user.getAccountStatus() == AppUser.Status.ACTIVE)
                .orElseThrow(() -> new AccessDeniedException("Account unavailable"));
    }

    private AppUser lockActive(UUID userId) {
        return users.lockById(userId).filter(user -> user.getAccountStatus() == AppUser.Status.ACTIVE)
                .orElseThrow(() -> new AccessDeniedException("Account unavailable"));
    }

    private void audit(UUID userId, String eventType, String metadata) {
        jdbc.update("insert into security_audit_event (user_id, event_type, metadata) values (?, ?, ?::jsonb)",
                userId, eventType, metadata);
    }

    private static String currentId(HttpSession current) {
        if (current == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        return current.getId();
    }

    private static ResponseStatusException inconsistentIndex() {
        return new ResponseStatusException(HttpStatus.CONFLICT,
                "Chưa thể xác nhận danh sách phiên đăng nhập. Vui lòng đăng nhập lại.");
    }

    private static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy phiên cần đăng xuất.");
    }
}
