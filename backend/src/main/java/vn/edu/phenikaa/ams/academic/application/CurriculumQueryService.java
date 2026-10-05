package vn.edu.phenikaa.ams.academic.application;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import vn.edu.phenikaa.ams.academic.domain.CurriculumCourse.Requirement;
import vn.edu.phenikaa.ams.academic.infrastructure.CurriculumReadRepository;
import vn.edu.phenikaa.ams.academic.infrastructure.StudentProfileRepository;
import vn.edu.phenikaa.ams.user.domain.AppUser;
import vn.edu.phenikaa.ams.user.infrastructure.UserRepository;

@Service
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class CurriculumQueryService {
    public record Page<T>(List<T> items, String nextCursor) {}
    public record CurriculumView(UUID id, String code, String name, String cohort, String revision,
                                 BigDecimal minimumCredits, long courseCount, long groupCount) {}
    public record GroupView(UUID id, String code, String name, Requirement requirement,
                            BigDecimal minimumCredits, Integer minimumCourseCount) {}
    public record CurriculumDetail(CurriculumView curriculum, Page<GroupView> groups) {}
    public record CurriculumCourseView(UUID id, UUID courseId, String code, String name,
                                       BigDecimal credits, Requirement requirement, UUID groupId, String groupName,
                                       Integer recommendedTerm) {}
    public record CatalogCourseView(UUID id, String code, String name, BigDecimal credits, boolean curriculumLinked) {}

    private final StudentProfileRepository profiles;
    private final CurriculumReadRepository reads;
    private final UserRepository users;

    public CurriculumQueryService(StudentProfileRepository profiles, CurriculumReadRepository reads, UserRepository users) {
        this.profiles = profiles;
        this.reads = reads;
        this.users = users;
    }

    public Page<CurriculumView> curricula(UUID userId, CatalogPageRequest page) {
        UUID profile = profile(userId);
        return profile == null ? empty() : slice(reads.curricula(profile, page), page,
                CurriculumView::code, CurriculumView::id);
    }

    public CurriculumDetail detail(UUID userId, UUID curriculumId, CatalogPageRequest page) {
        var curriculum = owned(userId, curriculumId);
        return new CurriculumDetail(curriculum.view(), slice(reads.groups(curriculum.profile(), curriculumId, page),
                page, GroupView::code, GroupView::id));
    }

    public Page<CurriculumCourseView> courses(UUID userId, UUID curriculumId, CatalogPageRequest page) {
        var curriculum = owned(userId, curriculumId);
        return slice(reads.courses(curriculum.profile(), curriculumId, page), page,
                CurriculumCourseView::code, CurriculumCourseView::courseId);
    }

    public Page<CatalogCourseView> catalog(UUID userId, CatalogPageRequest page) {
        UUID profile = profile(userId);
        return profile == null ? empty() : slice(reads.catalog(profile, page), page,
                CatalogCourseView::code, CatalogCourseView::id);
    }

    private UUID profile(UUID userId) {
        users.findById(userId).filter(user -> user.getAccountStatus() == AppUser.Status.ACTIVE)
                .orElseThrow(() -> new AccessDeniedException("Account unavailable"));
        return profiles.findByUserId(userId).map(p -> p.getId()).orElse(null);
    }

    private record OwnedCurriculum(UUID profile, CurriculumView view) {}
    private OwnedCurriculum owned(UUID userId, UUID curriculumId) {
        UUID profile = profile(userId);
        if (profile == null) throw notFound();
        return new OwnedCurriculum(profile, reads.curriculum(profile, curriculumId).orElseThrow(CurriculumQueryService::notFound));
    }

    private static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy chương trình đào tạo.");
    }

    private static <T> Page<T> empty() { return new Page<>(List.of(), null); }
    private static <T> Page<T> slice(List<T> rows, CatalogPageRequest page, Function<T, String> code, Function<T, UUID> id) {
        boolean more = rows.size() > page.limit();
        List<T> items = List.copyOf(rows.subList(0, Math.min(rows.size(), page.limit())));
        T last = items.isEmpty() ? null : items.getLast();
        return new Page<>(items, more ? CatalogPageRequest.encode(code.apply(last), id.apply(last)) : null);
    }
}
