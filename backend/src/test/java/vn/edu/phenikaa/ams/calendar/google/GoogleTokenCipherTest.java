package vn.edu.phenikaa.ams.calendar.google;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import vn.edu.phenikaa.ams.calendar.google.GoogleTokenCipher.TokenMaterial;
import static org.assertj.core.api.Assertions.*;

class GoogleTokenCipherTest {
    @Test void protectsTokensWithRandomNonceAndBoundContext() {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        String encoded = Base64.getEncoder().encodeToString(key);
        UUID connection = UUID.randomUUID();
        UUID user = UUID.randomUUID();
        var material = new TokenMaterial("synthetic-access", "synthetic-refresh");
        try (var cipher = new GoogleTokenCipher(encoded, 2)) {
            byte[] first = cipher.encrypt(material, connection, user);
            byte[] second = cipher.encrypt(material, connection, user);
            assertThat(first).isNotEqualTo(second);
            assertThat(cipher.decrypt(first, 2, connection, user)).isEqualTo(material);
            assertThat(new String(first, java.nio.charset.StandardCharsets.ISO_8859_1)).doesNotContain("synthetic-access");
            assertThatThrownBy(() -> cipher.decrypt(first, 2, UUID.randomUUID(), user)).hasMessageContaining("integrity");
            assertThatThrownBy(() -> cipher.decrypt(first, 2, connection, UUID.randomUUID())).hasMessageContaining("integrity");
            assertThatThrownBy(() -> cipher.decrypt(first, 1, connection, user)).hasMessageContaining("integrity");
            first[first.length - 1] ^= 1;
            assertThatThrownBy(() -> cipher.decrypt(first, 2, connection, user)).hasMessageContaining("integrity");
            assertThat(cipher.toString()).doesNotContain(encoded);
            assertThat(material.toString()).doesNotContain("synthetic-access", "synthetic-refresh");
        }
        try (var wrong = new GoogleTokenCipher(Base64.getEncoder().encodeToString(new byte[32]), 2);
             var original = new GoogleTokenCipher(encoded, 2)) {
            byte[] encrypted = original.encrypt(material, connection, user);
            assertThatThrownBy(() -> wrong.decrypt(encrypted, 2, connection, user)).hasMessageContaining("integrity");
        }
        assertThatThrownBy(() -> new GoogleTokenCipher("bad", 1)).isInstanceOf(IllegalStateException.class);
    }
}
