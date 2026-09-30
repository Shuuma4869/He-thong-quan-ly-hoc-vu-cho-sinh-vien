package vn.edu.phenikaa.ams.calendar.google;

import java.time.Instant;

interface GoogleOAuthGateway {
    String authorizationUrl(String state, String challenge, boolean renewConsent);
    Tokens exchange(String code, String state, String verifier);
    Tokens refresh(String accessToken, String refreshToken);
    boolean revoke(String refreshToken);

    record Tokens(String accessToken, String refreshToken, Instant expiresAt) {
        @Override public String toString() { return "GoogleOAuthTokens[redacted]"; }
    }
}
