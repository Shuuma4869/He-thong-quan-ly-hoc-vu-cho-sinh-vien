package vn.edu.phenikaa.ams.academic.infrastructure.phenikaa;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import vn.edu.phenikaa.ams.academic.application.ProfileImportService;
import vn.edu.phenikaa.ams.academic.infrastructure.StudentProfileRepository;
import vn.edu.phenikaa.ams.user.infrastructure.UserRepository;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "ams.phenikaa.enabled", havingValue = "true")
public class PhenikaaConfiguration {
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
                                                              PhenikaaSessionCipher cipher, PhenikaaHttpClient http) {
        return new PhenikaaAcademicPortalClient(connections, users, cipher, http);
    }

    @Bean
    ProfileImportService profileImportService(PhenikaaAcademicPortalClient portal, StudentProfileRepository profiles) {
        return new ProfileImportService(portal, profiles);
    }
}
