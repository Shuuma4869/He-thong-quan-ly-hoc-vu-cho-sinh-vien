package vn.edu.phenikaa.ams.academic.infrastructure.phenikaa;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import vn.edu.phenikaa.ams.academic.infrastructure.StudentProfileRepository;
import vn.edu.phenikaa.ams.user.infrastructure.UserRepository;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.mock;

class PhenikaaConfigurationTest {
    private final ApplicationContextRunner context = new ApplicationContextRunner()
            .withInitializer(application -> application.getBeanFactory().setConversionService(
                    org.springframework.boot.convert.ApplicationConversionService.getSharedInstance()))
            .withInitializer(application -> application.getBeanFactory().setConversionService(
                    org.springframework.boot.convert.ApplicationConversionService.getSharedInstance()))
            .withUserConfiguration(PhenikaaConfiguration.class)
            .withBean(PhenikaaConnectionRepository.class, () -> mock(PhenikaaConnectionRepository.class))
            .withBean(UserRepository.class, () -> mock(UserRepository.class))
            .withBean(StudentProfileRepository.class, () -> mock(StudentProfileRepository.class));

    @Test void disabledFeatureDoesNotRequireAKeyOrCreateAClient() {
        context.run(result -> {
            assertThat(result).hasNotFailed();
            assertThat(result).doesNotHaveBean(PhenikaaSessionCipher.class);
            assertThat(result).doesNotHaveBean(PhenikaaAcademicPortalClient.class);
        });
    }

    @Test void enabledFeatureFailsStartupWithoutAValidKeyIncludingProduction() {
        context.withPropertyValues("ams.phenikaa.enabled=true", "spring.profiles.active=prod").run(result -> {
            assertThat(result).hasFailed();
            assertThat(result.getStartupFailure()).hasRootCauseMessage(
                    "Phenikaa persistence requires a Base64-encoded 32-byte key and a positive key version");
        });
    }

    @Test void enabledFeatureAcceptsExplicitRandomKey() {
        context.withPropertyValues("ams.phenikaa.enabled=true", "ams.phenikaa.session-key=" + PhenikaaSessionCipherTest.randomKey())
                .run(result -> assertThat(result).hasNotFailed().hasSingleBean(PhenikaaSessionCipher.class));
    }
}
