package vn.edu.phenikaa.ams.academic.application;

import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import vn.edu.phenikaa.ams.academic.domain.Curriculum;
import vn.edu.phenikaa.ams.academic.infrastructure.CurriculumReadRepository;
import vn.edu.phenikaa.ams.academic.infrastructure.CurriculumRepository;
import vn.edu.phenikaa.ams.academic.infrastructure.StudentProfileRepository;
import vn.edu.phenikaa.ams.user.domain.AppUser;
import vn.edu.phenikaa.ams.user.infrastructure.UserRepository;

@Service
public class CurriculumSelectionService {
    public record SelectedCurriculumView(UUID id, String code, String name, String cohort, String revision,
                                         BigDecimal minimumCredits) {
        static SelectedCurriculumView from(Curriculum curriculum) {
            return new SelectedCurriculumView(curriculum.getId(), curriculum.getCode(), curriculum.getName(),
                    curriculum.getCohort(), curriculum.getRevision(), curriculum.getMinimumCredits());
        }
    }
    public record SelectionView(String selectionMode, SelectedCurriculumView curriculum) {
        static SelectionView of(SelectedCurriculumView curriculum) {
            return new SelectionView("USER_SELECTED_AMS", curriculum);
        }
    }

    private final UserRepository users;
    private final StudentProfileRepository profiles;
    private final CurriculumRepository curricula;
    private final CurriculumReadRepository reads;

    public CurriculumSelectionService(UserRepository users, StudentProfileRepository profiles,
                                      CurriculumRepository curricula, CurriculumReadRepository reads) {
        this.users = users;
        this.profiles = profiles;
        this.curricula = curricula;
        this.reads = reads;
    }

    @Transactional(readOnly = true)
    public SelectionView current(UUID userId) {
        active(userId);
        return SelectionView.of(reads.selectedCurriculum(userId).orElse(null));
    }

    @Transactional
    public SelectionView select(UUID userId, UUID curriculumId) {
        active(userId);
        var profile = profiles.findByUserId(userId).orElseThrow(CurriculumSelectionService::notFound);
        var curriculum = curricula.findByIdAndProfileId(curriculumId, profile.getId())
                .orElseThrow(CurriculumSelectionService::notFound);
        profile.selectCurriculum(curriculum);
        return SelectionView.of(SelectedCurriculumView.from(curriculum));
    }

    @Transactional
    public SelectionView clear(UUID userId) {
        active(userId);
        profiles.findByUserId(userId).ifPresent(profile -> profile.clearCurriculumSelection());
        return SelectionView.of(null);
    }

    private void active(UUID userId) {
        users.findById(userId).filter(user -> user.getAccountStatus() == AppUser.Status.ACTIVE)
                .orElseThrow(() -> new AccessDeniedException("Account unavailable"));
    }

    private static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Curriculum not found");
    }
}
