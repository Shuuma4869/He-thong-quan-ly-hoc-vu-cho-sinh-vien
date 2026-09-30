package vn.edu.phenikaa.ams.calendar.google;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.http.converter.FormHttpMessageConverter;
import org.springframework.security.oauth2.client.endpoint.OAuth2AuthorizationCodeGrantRequest;
import org.springframework.security.oauth2.client.endpoint.OAuth2RefreshTokenGrantRequest;
import org.springframework.security.oauth2.client.endpoint.RestClientAuthorizationCodeTokenResponseClient;
import org.springframework.security.oauth2.client.endpoint.RestClientRefreshTokenTokenResponseClient;
import org.springframework.security.oauth2.client.http.OAuth2ErrorResponseErrorHandler;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2AuthorizationException;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.core.endpoint.OAuth2AccessTokenResponse;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationExchange;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationResponse;
import org.springframework.security.oauth2.core.http.converter.OAuth2AccessTokenResponseHttpMessageConverter;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

final class SpringGoogleOAuthGateway implements GoogleOAuthGateway {
    static final String SCOPE = "https://www.googleapis.com/auth/calendar.app.created";
    private static final URI AUTH_URI = URI.create("https://accounts.google.com/o/oauth2/v2/auth");
    private static final URI TOKEN_URI = URI.create("https://oauth2.googleapis.com/token");
    private static final URI REVOKE_URI = URI.create("https://oauth2.googleapis.com/revoke");

    private final ClientRegistration registration;
    private final RestClientAuthorizationCodeTokenResponseClient codeClient;
    private final RestClientRefreshTokenTokenResponseClient refreshClient;
    private final RestClient revocationClient;
    private final URI revokeUri;

    SpringGoogleOAuthGateway(String clientId, String clientSecret, String redirectUri) {
        this(clientId, clientSecret, redirectUri, AUTH_URI, TOKEN_URI, REVOKE_URI);
    }

    SpringGoogleOAuthGateway(String clientId, String clientSecret, String redirectUri,
                             URI authorizationUri, URI tokenUri, URI revokeUri) {
        this(clientId, clientSecret, redirectUri, authorizationUri, tokenUri, revokeUri,
                Duration.ofSeconds(5), Duration.ofSeconds(8));
    }

    SpringGoogleOAuthGateway(String clientId, String clientSecret, String redirectUri,
                             URI authorizationUri, URI tokenUri, URI revokeUri,
                             Duration connectTimeout, Duration readTimeout) {
        this.registration = ClientRegistration.withRegistrationId("ams-google-calendar")
                .clientId(clientId).clientSecret(clientSecret)
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .authorizationUri(authorizationUri.toString()).tokenUri(tokenUri.toString())
                .redirectUri(redirectUri).scope(SCOPE).clientName("AMS Calendar").build();
        this.revokeUri = revokeUri;
        var requestFactory = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
                .connectTimeout(connectTimeout).followRedirects(HttpClient.Redirect.NEVER).build());
        requestFactory.setReadTimeout(readTimeout);
        var tokenRestClient = RestClient.builder().requestFactory(requestFactory)
                .messageConverters(converters -> {
                    converters.clear();
                    converters.add(new FormHttpMessageConverter());
                    converters.add(new OAuth2AccessTokenResponseHttpMessageConverter());
                })
                .defaultStatusHandler(new OAuth2ErrorResponseErrorHandler()).build();
        this.codeClient = new RestClientAuthorizationCodeTokenResponseClient();
        this.codeClient.setRestClient(tokenRestClient);
        this.refreshClient = new RestClientRefreshTokenTokenResponseClient();
        this.refreshClient.setRestClient(tokenRestClient);
        this.revocationClient = RestClient.builder().requestFactory(requestFactory).build();
    }

    @Override public String authorizationUrl(String state, String challenge, boolean renewConsent) {
        return request(state, challenge, null, renewConsent).getAuthorizationRequestUri();
    }

    @Override public Tokens exchange(String code, String state, String verifier) {
        try {
            var authorization = request(state, GoogleOAuthStateStore.challenge(verifier), verifier, false);
            var response = OAuth2AuthorizationResponse.success(code).redirectUri(registration.getRedirectUri())
                    .state(state).build();
            var grant = new OAuth2AuthorizationCodeGrantRequest(registration,
                    new OAuth2AuthorizationExchange(authorization, response));
            return tokens(codeClient.getTokenResponse(grant), true);
        } catch (OAuth2AuthorizationException | RestClientException ex) {
            throw new GoogleCalendarException(GoogleCalendarException.Code.GOOGLE_TOKEN_EXCHANGE_FAILED);
        }
    }

    @Override public Tokens refresh(String accessToken, String refreshToken) {
        try {
            var previous = new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, accessToken,
                    Instant.EPOCH, Instant.EPOCH.plusSeconds(1), Set.of(SCOPE));
            var grant = new OAuth2RefreshTokenGrantRequest(registration, previous,
                    new OAuth2RefreshToken(refreshToken, Instant.EPOCH));
            return tokens(refreshClient.getTokenResponse(grant), false);
        } catch (OAuth2AuthorizationException ex) {
            if ("invalid_grant".equals(ex.getError().getErrorCode()))
                throw new GoogleCalendarException(GoogleCalendarException.Code.GOOGLE_RECONNECTION_REQUIRED);
            throw new GoogleCalendarException(GoogleCalendarException.Code.GOOGLE_UNAVAILABLE);
        } catch (RestClientException ex) {
            throw new GoogleCalendarException(GoogleCalendarException.Code.GOOGLE_UNAVAILABLE);
        }
    }

    @Override public boolean revoke(String refreshToken) {
        try {
            var form = new LinkedMultiValueMap<String, String>();
            form.add("token", refreshToken);
            return revocationClient.post().uri(revokeUri).contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form).exchange((request, response) -> response.getStatusCode().is2xxSuccessful());
        } catch (RestClientException ex) { return false; }
    }

    private OAuth2AuthorizationRequest request(String state, String challenge, String verifier, boolean renewConsent) {
        return OAuth2AuthorizationRequest.authorizationCode()
                .authorizationUri(registration.getProviderDetails().getAuthorizationUri())
                .clientId(registration.getClientId()).redirectUri(registration.getRedirectUri())
                .scopes(Set.of(SCOPE)).state(state)
                .additionalParameters(parameters -> {
                    parameters.put("access_type", "offline");
                    parameters.put("code_challenge", challenge);
                    parameters.put("code_challenge_method", "S256");
                    if (renewConsent) parameters.put("prompt", "consent");
                })
                .attributes(attributes -> {
                    if (verifier != null) attributes.put("code_verifier", verifier);
                }).build();
    }

    private static Tokens tokens(OAuth2AccessTokenResponse response, boolean requireScope) {
        var access = response.getAccessToken();
        if (access == null || access.getTokenValue().isBlank() || access.getExpiresAt() == null
                || (requireScope && !access.getScopes().contains(SCOPE))
                || (!access.getScopes().isEmpty() && !access.getScopes().contains(SCOPE)))
            throw new GoogleCalendarException(GoogleCalendarException.Code.GOOGLE_TOKEN_EXCHANGE_FAILED);
        var refresh = response.getRefreshToken();
        return new Tokens(access.getTokenValue(), refresh == null ? null : refresh.getTokenValue(),
                access.getExpiresAt());
    }
}
