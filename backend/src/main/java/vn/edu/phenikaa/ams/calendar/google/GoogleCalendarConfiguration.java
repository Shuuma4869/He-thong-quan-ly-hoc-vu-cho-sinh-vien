package vn.edu.phenikaa.ams.calendar.google;

import java.net.URI;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import vn.edu.phenikaa.ams.user.infrastructure.UserRepository;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "ams.google-calendar.enabled", havingValue = "true")
class GoogleCalendarConfiguration {
    @Bean(destroyMethod = "close")
    GoogleTokenCipher googleTokenCipher(@Value("${ams.google-calendar.token-key:}") String key,
                                        @Value("${ams.google-calendar.key-version:1}") int version) {
        return new GoogleTokenCipher(key, version);
    }

    @Bean GoogleCalendarConnectionStore googleCalendarConnectionStore(JdbcTemplate jdbc) {
        return new GoogleCalendarConnectionStore(jdbc);
    }

    @Bean GoogleOAuthStateStore googleOAuthStateStore(StringRedisTemplate redis) {
        return new GoogleOAuthStateStore(redis);
    }

    @Bean GoogleOperationLock googleOperationLock(StringRedisTemplate redis) {
        return new GoogleOperationLock(redis);
    }

    @Bean GoogleOAuthGateway googleOAuthGateway(@Value("${ams.google-calendar.client-id:}") String clientId,
                                                 @Value("${ams.google-calendar.client-secret:}") String clientSecret,
                                                 @Value("${ams.google-calendar.redirect-uri:}") String redirectUri) {
        if (clientId.isBlank() || clientSecret.isBlank() || !validRedirect(redirectUri))
            throw new IllegalStateException("Google Calendar requires OAuth client credentials and a fixed callback URI");
        return new SpringGoogleOAuthGateway(clientId, clientSecret, redirectUri);
    }

    @Bean GoogleCalendarGateway googleCalendarGateway() { return new GoogleCalendarHttpGateway(); }

    @Bean GoogleAccessTokenProvider googleAccessTokenProvider(GoogleCalendarConnectionStore store,
            GoogleTokenCipher cipher, GoogleOAuthGateway oauth, GoogleOperationLock locks, Clock clock) {
        return new GoogleAccessTokenProvider(store, cipher, oauth, locks, clock);
    }

    @Bean GoogleCalendarService googleCalendarService(GoogleCalendarConnectionStore store, GoogleTokenCipher cipher,
            GoogleOAuthStateStore states, GoogleOAuthGateway oauth, GoogleCalendarGateway calendar,
            GoogleAccessTokenProvider tokens, GoogleOperationLock locks, UserRepository users, Clock clock) {
        return new GoogleCalendarService(store, cipher, states, oauth, calendar, tokens, locks, users, clock);
    }

    private static boolean validRedirect(String value) {
        try {
            URI uri = URI.create(value);
            return uri.getHost() != null && uri.getRawUserInfo() == null && uri.getRawQuery() == null
                    && uri.getRawFragment() == null
                    && "/api/integrations/google-calendar/callback".equals(uri.getPath())
                    && ("https".equals(uri.getScheme()) || ("http".equals(uri.getScheme())
                    && "localhost".equals(uri.getHost())));
        } catch (IllegalArgumentException ex) { return false; }
    }
}
