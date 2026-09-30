package vn.edu.phenikaa.ams.calendar.google;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import vn.edu.phenikaa.ams.TestcontainersConfiguration;
import vn.edu.phenikaa.ams.auth.application.AccountPrincipal;
import vn.edu.phenikaa.ams.user.domain.AppUser;
import vn.edu.phenikaa.ams.user.infrastructure.UserRepository;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = {"spring.data.redis.password=", "ams.google-calendar.enabled=true",
        "ams.google-calendar.client-id=synthetic-client-id",
        "ams.google-calendar.client-secret=synthetic-client-secret",
        "ams.google-calendar.redirect-uri=http://localhost:3000/api/integrations/google-calendar/callback"})
@AutoConfigureMockMvc
class GoogleCalendarIT {
    private static final String BASE = "/api/me/connections/google-calendar";
    private static final String CALLBACK = "/api/integrations/google-calendar/callback";
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository users;
    @Autowired GoogleOAuthStateStore states;
    @Autowired GoogleCalendarConnectionStore store;
    @Autowired GoogleAccessTokenProvider tokens;
    @Autowired org.springframework.data.redis.core.StringRedisTemplate redis;
    @MockitoBean GoogleOAuthGateway oauth;
    @MockitoBean GoogleCalendarGateway calendar;
    private AppUser owner;
    private AppUser other;

    @DynamicPropertySource static void key(DynamicPropertyRegistry properties) {
        byte[] value = new byte[32];
        new SecureRandom().nextBytes(value);
        properties.add("ams.google-calendar.token-key", () -> Base64.getEncoder().encodeToString(value));
    }

    @BeforeEach void setup() {
        owner = users.save(new AppUser(UUID.randomUUID() + "@example.test", "!synthetic-hash", null));
        other = users.save(new AppUser(UUID.randomUUID() + "@example.test", "!synthetic-hash", null));
        when(oauth.authorizationUrl(anyString(), anyString(), anyBoolean())).thenAnswer(call ->
                "https://accounts.google.com/o/oauth2/v2/auth?state=" + call.getArgument(0));
        when(oauth.exchange(anyString(), anyString(), anyString())).thenAnswer(call -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return new GoogleOAuthGateway.Tokens("synthetic-access", "synthetic-refresh", Instant.now().plusSeconds(3600));
        });
        when(calendar.create(anyString())).thenAnswer(call -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return "synthetic-calendar-id";
        });
    }

    @Test void firstConnectionIsOwnedEncryptedAndCallbackIsOneTime() throws Exception {
        mvc.perform(get(BASE)).andExpect(status().isUnauthorized());
        mvc.perform(post(BASE + "/authorize").with(user(new AccountPrincipal(owner))))
                .andExpect(status().isForbidden());
        mvc.perform(get(BASE).with(user(new AccountPrincipal(owner))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DISCONNECTED"));
        String state = authorize(owner);
        mvc.perform(get(CALLBACK).param("state", state).param("code", "synthetic-code")
                        .with(user(new AccountPrincipal(owner))))
                .andExpect(status().isSeeOther()).andExpect(header().string("Location", "/settings?google=connected"))
                .andExpect(header().string("Referrer-Policy", "no-referrer"));
        var connection = store.find(owner.getId()).orElseThrow();
        assertThat(connection.status()).isEqualTo(GoogleCalendarConnectionStore.Status.CONNECTED);
        assertThat(connection.calendarId()).isEqualTo("synthetic-calendar-id");
        assertThat(new String(connection.encryptedTokens(), java.nio.charset.StandardCharsets.ISO_8859_1))
                .doesNotContain("synthetic-access", "synthetic-refresh");
        mvc.perform(get(BASE).with(user(new AccountPrincipal(owner))))
                .andExpect(jsonPath("$.calendarReady").value(true))
                .andExpect(jsonPath("$.encryptedTokens").doesNotExist())
                .andExpect(jsonPath("$.calendarId").doesNotExist());
        mvc.perform(get(BASE).with(user(new AccountPrincipal(other))))
                .andExpect(jsonPath("$.status").value("DISCONNECTED"));
        mvc.perform(get(CALLBACK).param("state", state).param("code", "synthetic-code")
                        .with(user(new AccountPrincipal(owner))))
                .andExpect(header().string("Location", "/settings?google=error"));
        mvc.perform(get(CALLBACK).param("state", state).param("code", "synthetic-code"))
                .andExpect(status().isUnauthorized());
        verify(oauth, times(1)).exchange(anyString(), anyString(), anyString());
        assertThat(jdbc.queryForObject("select count(*) from google_calendar_connection where user_id = ?",
                Integer.class, owner.getId())).isEqualTo(1);
    }

    @Test void wrongUserExpiredStateAndDeniedConsentNeverStoreTokens() throws Exception {
        var started = states.start(owner.getId(), -1);
        assertThat(started.state()).hasSize(43);
        assertThat(started.verifier()).hasSize(43);
        assertThat(started.challenge()).isEqualTo(GoogleOAuthStateStore.challenge(started.verifier()));
        assertThatThrownBy(() -> states.consume(started.state(), other.getId())).hasMessage("GOOGLE_STATE_INVALID");
        assertThatThrownBy(() -> states.consume(started.state(), owner.getId())).hasMessage("GOOGLE_STATE_INVALID");
        var expired = states.start(owner.getId(), -1);
        redis.delete("ams:oauth:google-calendar:" + expired.state());
        assertThatThrownBy(() -> states.consume(expired.state(), owner.getId())).hasMessage("GOOGLE_STATE_INVALID");
        String denied = authorize(owner);
        mvc.perform(get(CALLBACK).param("state", denied).param("error", "access_denied")
                        .with(user(new AccountPrincipal(owner))))
                .andExpect(header().string("Location", "/settings?google=cancelled"));
        mvc.perform(get(CALLBACK).param("state", "bad").param("code", "synthetic-code")
                        .with(user(new AccountPrincipal(owner))))
                .andExpect(header().string("Location", "/settings?google=error"));
        assertThat(store.find(owner.getId())).isEmpty();
        verify(oauth, never()).exchange(anyString(), anyString(), anyString());
    }

    @Test void missingRefreshTokenNeverClaimsConnectionAndReconnectReusesCalendar() throws Exception {
        when(oauth.exchange(anyString(), anyString(), anyString())).thenReturn(
                new GoogleOAuthGateway.Tokens("synthetic-access", null, Instant.now().plusSeconds(3600)));
        String first = authorize(owner);
        mvc.perform(get(CALLBACK).param("state", first).param("code", "synthetic-code")
                .with(user(new AccountPrincipal(owner)))).andExpect(header().string("Location", "/settings?google=error"));
        assertThat(store.find(owner.getId()).orElseThrow().status())
                .isEqualTo(GoogleCalendarConnectionStore.Status.RECONNECTION_REQUIRED);
        String second = authorize(owner);
        verify(oauth).authorizationUrl(eq(second), anyString(), eq(true));
        when(oauth.exchange(anyString(), anyString(), anyString())).thenReturn(
                new GoogleOAuthGateway.Tokens("synthetic-access", "synthetic-refresh", Instant.now().plusSeconds(3600)));
        mvc.perform(get(CALLBACK).param("state", second).param("code", "synthetic-code")
                .with(user(new AccountPrincipal(owner)))).andExpect(header().string("Location", "/settings?google=connected"));
        var firstId = store.find(owner.getId()).orElseThrow().id();
        when(oauth.revoke("synthetic-refresh")).thenReturn(false);
        mvc.perform(post(BASE + "/disconnect").with(user(new AccountPrincipal(owner))))
                .andExpect(status().isForbidden());
        mvc.perform(post(BASE + "/disconnect").with(user(new AccountPrincipal(owner))).with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.remoteRevocationConfirmed").value(false));
        assertThat(store.find(owner.getId()).orElseThrow().encryptedTokens()).isNull();
        assertThat(store.find(owner.getId()).orElseThrow().calendarId()).isEqualTo("synthetic-calendar-id");
        String reconnect = authorize(owner);
        when(calendar.exists("synthetic-access", "synthetic-calendar-id")).thenReturn(true);
        mvc.perform(get(CALLBACK).param("state", reconnect).param("code", "synthetic-code")
                .with(user(new AccountPrincipal(owner)))).andExpect(header().string("Location", "/settings?google=connected"));
        assertThat(store.find(owner.getId()).orElseThrow().id()).isEqualTo(firstId);
        verify(calendar, times(1)).create(anyString());
    }

    @Test void deletedCalendarIsRecreatedAfterReconnect() throws Exception {
        connect(owner);
        mvc.perform(post(BASE + "/disconnect").with(user(new AccountPrincipal(owner))).with(csrf()))
                .andExpect(status().isOk());
        String state = authorize(owner);
        when(calendar.exists("synthetic-access", "synthetic-calendar-id")).thenReturn(false);
        when(calendar.create("synthetic-access")).thenReturn("replacement-calendar-id");
        mvc.perform(get(CALLBACK).param("state", state).param("code", "synthetic-code")
                .with(user(new AccountPrincipal(owner))))
                .andExpect(header().string("Location", "/settings?google=connected"));
        assertThat(store.find(owner.getId()).orElseThrow().calendarId()).isEqualTo("replacement-calendar-id");
        verify(calendar, times(2)).create(anyString());
    }

    @Test void localDisconnectWorksWhenOldTokenKeyIsUnavailable() throws Exception {
        connect(owner);
        jdbc.update("update google_calendar_connection set encrypted_tokens = ? where user_id = ?",
                new byte[32], owner.getId());
        mvc.perform(post(BASE + "/disconnect").with(user(new AccountPrincipal(owner))).with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.remoteRevocationConfirmed").value(false));
        assertThat(store.find(owner.getId()).orElseThrow().encryptedTokens()).isNull();
        verify(oauth, never()).revoke(anyString());
    }

    @Test void setupFailureKeepsEncryptedTokenAndExplicitRetryCompletes() throws Exception {
        when(calendar.create(anyString())).thenThrow(new GoogleCalendarException(
                GoogleCalendarException.Code.GOOGLE_CALENDAR_SETUP_FAILED)).thenReturn("synthetic-calendar-id");
        String state = authorize(owner);
        mvc.perform(get(CALLBACK).param("state", state).param("code", "synthetic-code")
                .with(user(new AccountPrincipal(owner)))).andExpect(header().string("Location", "/settings?google=setup"));
        assertThat(store.find(owner.getId()).orElseThrow().status())
                .isEqualTo(GoogleCalendarConnectionStore.Status.SETUP_REQUIRED);
        assertThat(store.find(owner.getId()).orElseThrow().encryptedTokens()).isNotNull();
        mvc.perform(post(BASE + "/setup").with(user(new AccountPrincipal(owner))))
                .andExpect(status().isForbidden());
        mvc.perform(post(BASE + "/setup").with(user(new AccountPrincipal(owner))).with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CONNECTED"));
        verify(calendar, times(2)).create(anyString());
    }

    @Test void concurrentRefreshUsesOneHttpCallAndRotatesEncryptedTokens() throws Exception {
        connect(owner);
        jdbc.update("update google_calendar_connection set token_expires_at = now() - interval '1 second' where user_id = ?", owner.getId());
        var started = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        when(oauth.refresh(anyString(), anyString())).thenAnswer(call -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            started.countDown();
            assertThat(release.await(3, TimeUnit.SECONDS)).isTrue();
            return new GoogleOAuthGateway.Tokens("renewed-access", "renewed-refresh", Instant.now().plusSeconds(3600));
        });
        try (var pool = Executors.newFixedThreadPool(2)) {
            var first = pool.submit(() -> tokens.accessToken(owner.getId()));
            assertThat(started.await(3, TimeUnit.SECONDS)).isTrue();
            var second = pool.submit(() -> tokens.accessToken(owner.getId()));
            release.countDown();
            assertThat(first.get(5, TimeUnit.SECONDS)).isEqualTo("renewed-access");
            assertThat(second.get(5, TimeUnit.SECONDS)).isEqualTo("renewed-access");
        }
        verify(oauth, times(1)).refresh(anyString(), anyString());
        assertThat(new String(store.find(owner.getId()).orElseThrow().encryptedTokens(),
                java.nio.charset.StandardCharsets.ISO_8859_1)).doesNotContain("renewed-access", "renewed-refresh");
    }

    @Test void revokedRefreshRequiresReconnectionAndNoAcademicOrEventRowsAreWritten() throws Exception {
        connect(owner);
        jdbc.update("update google_calendar_connection set token_expires_at = now() - interval '1 second' where user_id = ?", owner.getId());
        when(oauth.refresh(anyString(), anyString())).thenThrow(new GoogleCalendarException(
                GoogleCalendarException.Code.GOOGLE_RECONNECTION_REQUIRED));
        assertThatThrownBy(() -> tokens.accessToken(owner.getId())).hasMessage("GOOGLE_RECONNECTION_REQUIRED");
        assertThat(store.find(owner.getId()).orElseThrow().status())
                .isEqualTo(GoogleCalendarConnectionStore.Status.RECONNECTION_REQUIRED);
        assertThat(store.find(owner.getId()).orElseThrow().encryptedTokens()).isNull();
        for (String table : new String[]{"student_course", "academic_result", "class_session", "exam"})
            assertThat(jdbc.queryForObject("select count(*) from " + table, Integer.class)).isZero();
    }

    private void connect(AppUser user) throws Exception {
        String state = authorize(user);
        mvc.perform(get(CALLBACK).param("state", state).param("code", "synthetic-code")
                .with(user(new AccountPrincipal(user)))).andExpect(header().string("Location", "/settings?google=connected"));
    }

    private String authorize(AppUser user) throws Exception {
        AtomicReference<String> state = new AtomicReference<>();
        when(oauth.authorizationUrl(anyString(), anyString(), anyBoolean())).thenAnswer(call -> {
            state.set(call.getArgument(0));
            return "https://accounts.google.com/o/oauth2/v2/auth?state=" + state.get();
        });
        mvc.perform(post(BASE + "/authorize").with(user(new AccountPrincipal(user))).with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.authorizationUrl").exists());
        return state.get();
    }
}
