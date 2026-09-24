package vn.edu.phenikaa.ams.academic.infrastructure.phenikaa;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class PhenikaaSessionCipherTest {
    private final UUID connectionId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();

    static String randomKey() {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        try { return Base64.getEncoder().encodeToString(key); }
        finally { java.util.Arrays.fill(key, (byte) 0); }
    }

    static PhenikaaSessionMaterial material() {
        return new PhenikaaSessionMaterial("Bearer synthetic-secret", "fixture=synthetic-cookie", "synthetic-key",
                "synthetic-learner", "synthetic-profile-function", "synthetic-schedule-function");
    }

    @Test void readsLegacyEncryptedPayloadWithoutRewritingItOrGrantingExamCapability() throws Exception {
        String key = randomKey();
        byte[] legacy;
        try (var buffer = new java.io.ByteArrayOutputStream(); var output = new java.io.DataOutputStream(buffer)) {
            output.writeInt(1);
            for (String value : new String[]{"Bearer synthetic-secret", "", "synthetic-key", "synthetic-learner",
                    "synthetic-profile-function", "synthetic-schedule-function"}) output.writeUTF(value);
            legacy = buffer.toByteArray();
        }
        byte[] nonce = new byte[12];
        new SecureRandom().nextBytes(nonce);
        var oldCipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding");
        oldCipher.init(javax.crypto.Cipher.ENCRYPT_MODE, new javax.crypto.spec.SecretKeySpec(Base64.getDecoder().decode(key), "AES"),
                new javax.crypto.spec.GCMParameterSpec(128, nonce));
        oldCipher.updateAAD(("AMS:phenikaa:session:1:1:" + connectionId + ":" + userId)
                .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        byte[] ciphertext = oldCipher.doFinal(legacy);
        byte[] stored = java.nio.ByteBuffer.allocate(nonce.length + ciphertext.length).put(nonce).put(ciphertext).array();
        try (var cipher = new PhenikaaSessionCipher(key, 1); var result = cipher.decrypt(stored, 1, connectionId, userId)) {
            assertThat(result.profile().learnerId()).isEqualTo("synthetic-learner");
            assertThat(result.schedule().functionId()).isEqualTo("synthetic-schedule-function");
            assertThat(result.exam()).isNull();
        }
        java.util.Arrays.fill(legacy, (byte) 0);
    }

    @Test void keepsExamContextSeparateAndClosesIt() {
        try (var cipher = new PhenikaaSessionCipher(randomKey(), 1);
             var original = new PhenikaaSessionMaterial("Bearer synthetic-secret", "", "synthetic-key", "synthetic-learner",
                     "synthetic-profile-function", "synthetic-schedule-function", "synthetic-exam-function")) {
            var restored = cipher.decrypt(cipher.encrypt(original, connectionId, userId), 1, connectionId, userId);
            assertThat(restored.exam().functionId()).isEqualTo("synthetic-exam-function");
            assertThat(restored.schedule().functionId()).isEqualTo("synthetic-schedule-function");
            restored.close();
            assertThatThrownBy(() -> restored.exam().authorization()).isInstanceOf(IllegalStateException.class);
        }
    }

    @Test void readsVersionTwoWithoutGrantingAcademicCapability() throws Exception {
        byte[] encoded;
        try (var buffer = new java.io.ByteArrayOutputStream(); var output = new java.io.DataOutputStream(buffer)) {
            output.writeInt(2);
            for (String value : new String[]{"Bearer synthetic-secret", "", "synthetic-key", "synthetic-learner",
                    "synthetic-profile-function", "synthetic-schedule-function"}) output.writeUTF(value);
            output.writeBoolean(true);
            output.writeUTF("synthetic-exam-function");
            encoded = buffer.toByteArray();
        }
        try (var decoded = PhenikaaSessionMaterial.decode(encoded)) {
            assertThat(decoded.exam().functionId()).isEqualTo("synthetic-exam-function");
            assertThat(decoded.academic()).isNull();
        } finally { java.util.Arrays.fill(encoded, (byte) 0); }
    }

    @Test void encryptedVersionThreeKeepsAcademicContextDistinctAndClosesIt() {
        try (var cipher = new PhenikaaSessionCipher(randomKey(), 1);
             var original = new PhenikaaSessionMaterial("Bearer synthetic-secret", "", "synthetic-key", "synthetic-learner",
                     "synthetic-profile-function", "synthetic-schedule-function", "synthetic-exam-function", "synthetic-academic-function")) {
            var restored = cipher.decrypt(cipher.encrypt(original, connectionId, userId), 1, connectionId, userId);
            assertThat(restored.academic().functionId()).isEqualTo("synthetic-academic-function");
            assertThat(restored.exam().functionId()).isEqualTo("synthetic-exam-function");
            restored.close();
            assertThatThrownBy(() -> restored.academic().authorization()).isInstanceOf(IllegalStateException.class);
        }
    }

    @Test void roundTripKeepsSeparateCapabilityContextAndWipesOwnedArraysOnClose() throws Exception {
        try (var cipher = new PhenikaaSessionCipher(randomKey(), 1); var original = material()) {
            var encrypted = cipher.encrypt(original, connectionId, userId);
            PhenikaaSessionMaterial decoded;
            try (var result = cipher.decrypt(encrypted, 1, connectionId, userId)) {
                decoded = result;
                assertThat(result.profile().authorization()).isEqualTo("Bearer synthetic-secret");
                assertThat(result.profile().cookie()).isEqualTo("fixture=synthetic-cookie");
                assertThat(result.profile().learnerId()).isEqualTo("synthetic-learner");
                assertThat(result.profile().functionId()).isEqualTo("synthetic-profile-function");
                assertThat(result.schedule().functionId()).isEqualTo("synthetic-schedule-function");
                assertThat(result.toString()).isEqualTo("PhenikaaSessionMaterial[redacted]");
                assertThat(cipher.toString()).isEqualTo("PhenikaaSessionCipher[redacted]");
            }
            assertThatThrownBy(() -> decoded.profile().authorization()).isInstanceOf(IllegalStateException.class);
            var field = PhenikaaSession.class.getDeclaredField("authorization");
            field.setAccessible(true);
            assertThat((char[]) field.get(decoded.profile())).containsOnly('\0');
        }
    }

    @Test void randomNonceProducesDifferentCiphertextsAndNoPlaintext() {
        try (var cipher = new PhenikaaSessionCipher(randomKey(), 1); var session = material()) {
            byte[] first = cipher.encrypt(session, connectionId, userId);
            byte[] second = cipher.encrypt(session, connectionId, userId);
            assertThat(first).isNotEqualTo(second);
            assertThat(new String(first, java.nio.charset.StandardCharsets.ISO_8859_1))
                    .doesNotContain("synthetic-secret", "synthetic-cookie", "synthetic-key", "synthetic-learner");
        }
    }

    @Test void rejectsTamperingWrongUserWrongConnectionWrongVersionAndWrongKey() {
        try (var cipher = new PhenikaaSessionCipher(randomKey(), 1);
             var other = new PhenikaaSessionCipher(randomKey(), 1); var session = material()) {
            byte[] encrypted = cipher.encrypt(session, connectionId, userId);
            assertIntegrityFailure(() -> cipher.decrypt(encrypted, 1, connectionId, UUID.randomUUID()));
            assertIntegrityFailure(() -> cipher.decrypt(encrypted, 1, UUID.randomUUID(), userId));
            assertIntegrityFailure(() -> cipher.decrypt(encrypted, 2, connectionId, userId));
            assertIntegrityFailure(() -> other.decrypt(encrypted, 1, connectionId, userId));
            encrypted[encrypted.length - 1] ^= 1;
            assertIntegrityFailure(() -> cipher.decrypt(encrypted, 1, connectionId, userId));
        }
    }

    @Test void keepsSourceBindingDistinctFromSessionPayload() {
        try (var cipher = new PhenikaaSessionCipher(randomKey(), 1); var session = material();
             var other = new PhenikaaSessionMaterial("Bearer synthetic-secret", "", "synthetic-key", "other-learner",
                     "synthetic-profile-function", "synthetic-schedule-function")) {
            byte[] subject = cipher.encryptSubject(session, connectionId, userId);
            assertThat(cipher.matchesSubject(subject, 1, session, connectionId, userId)).isTrue();
            assertThat(cipher.matchesSubject(subject, 1, other, connectionId, userId)).isFalse();
            assertIntegrityFailure(() -> cipher.decrypt(subject, 1, connectionId, userId));
        }
    }

    @Test void rejectsMissingMalformedOrShortKeysWithoutEchoingConfiguration() {
        for (String key : new String[]{"", "not-base64!", Base64.getEncoder().encodeToString(new byte[16])})
            assertThatThrownBy(() -> new PhenikaaSessionCipher(key, 1)).isInstanceOf(IllegalStateException.class)
                    .hasMessage("Phenikaa persistence requires a Base64-encoded 32-byte key and a positive key version")
                    .hasNoCause();
        assertThatThrownBy(() -> new PhenikaaSessionCipher(randomKey(), 0)).isInstanceOf(IllegalStateException.class);
    }

    @Test void rejectsMalformedPayloadAndUseAfterClose() {
        assertThatThrownBy(() -> PhenikaaSessionMaterial.decode(new byte[]{1, 2, 3})).hasNoCause();
        var cipher = new PhenikaaSessionCipher(randomKey(), 1);
        cipher.close();
        try (var session = material()) { assertIntegrityFailure(() -> cipher.encrypt(session, connectionId, userId)); }
    }

    private static void assertIntegrityFailure(org.assertj.core.api.ThrowableAssert.ThrowingCallable action) {
        assertThatThrownBy(action).isInstanceOf(IllegalStateException.class)
                .hasMessage("Session encryption or integrity check failed").hasNoCause();
    }
}
