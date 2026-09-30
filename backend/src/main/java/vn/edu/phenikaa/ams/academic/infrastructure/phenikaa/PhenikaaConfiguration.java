package vn.edu.phenikaa.ams.academic.infrastructure.phenikaa;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import vn.edu.phenikaa.ams.academic.application.ProfileImportService;
import vn.edu.phenikaa.ams.academic.application.AcademicSourceQueryService;
import vn.edu.phenikaa.ams.academic.application.port.AcademicReadGate;
import vn.edu.phenikaa.ams.academic.infrastructure.StudentProfileRepository;
import vn.edu.phenikaa.ams.user.infrastructure.UserRepository;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "ams.phenikaa.enabled", havingValue = "true")
public class PhenikaaConfiguration {
    @Bean
    AcademicReadGate academicReadGate(org.springframework.data.redis.core.StringRedisTemplate redis,
                                      @Value("${ams.phenikaa.read-cooldown:5s}") Duration cooldown) {
        return new RedisAcademicReadGate(redis, cooldown);
    }

    @Bean
    AcademicSourceQueryService academicSourceQueryService(PhenikaaAcademicPortalClient portal, AcademicReadGate gate) {
        return new AcademicSourceQueryService(portal, gate);
    }

    @Bean
    PhenikaaCurriculumStore phenikaaCurriculumStore(jakarta.persistence.EntityManager entities, org.springframework.jdbc.core.JdbcTemplate jdbc) {
        return new PhenikaaCurriculumStore(entities, jdbc);
    }

    @Bean
    vn.edu.phenikaa.ams.academic.application.CurriculumImportService curriculumImportService(
            PhenikaaAcademicPortalClient portal, StudentProfileRepository profiles, PhenikaaCurriculumStore store,
            org.springframework.transaction.PlatformTransactionManager transactions) {
        return new vn.edu.phenikaa.ams.academic.application.CurriculumImportService(portal, profiles, store, transactions);
    }
    @Bean(destroyMethod = "close")
    PhenikaaSessionCipher phenikaaSessionCipher(@Value("${ams.phenikaa.session-key:}") String key,
                                               @Value("${ams.phenikaa.key-version:1}") int keyVersion) {
        return new PhenikaaSessionCipher(key, keyVersion);
    }

    @Bean(destroyMethod = "close")
    PhenikaaHttpTransport phenikaaHttpTransport(@Value("${ams.phenikaa.connect-timeout:8s}") Duration connect,
                                               @Value("${ams.phenikaa.response-timeout:15s}") Duration response,
                                               @Value("${ams.phenikaa.max-response-bytes:2097152}") int maxBytes) {
        return new PhenikaaHttpTransport(connect, response, maxBytes);
    }

    @Bean
    PhenikaaHttpClient phenikaaHttpClient(PhenikaaHttpTransport transport,
                                        @Value("${ams.phenikaa.max-response-bytes:2097152}") int maxBytes) {
        return new PhenikaaHttpClient(transport, new PhenikaaPayloadCodec(maxBytes));
    }

    @Bean
    PhenikaaAcademicPortalClient phenikaaAcademicPortalClient(PhenikaaConnectionRepository connections, UserRepository users,
                                                              PhenikaaSessionCipher cipher, PhenikaaHttpClient http,
                                                              org.springframework.transaction.PlatformTransactionManager transactions) {
        return new PhenikaaAcademicPortalClient(connections, users, cipher, http, transactions);
    }

    @Bean
    ProfileImportService profileImportService(PhenikaaAcademicPortalClient portal, StudentProfileRepository profiles,
                                             org.springframework.transaction.PlatformTransactionManager transactions) {
        return new ProfileImportService(portal, profiles, transactions);
    }
}
