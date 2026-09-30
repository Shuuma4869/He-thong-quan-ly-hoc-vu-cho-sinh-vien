package vn.edu.phenikaa.ams.calendar.google;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.UUID;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

final class GoogleTokenCipher implements AutoCloseable {
    static final int MAX_PLAIN_BYTES = 16_000;
    private static final int NONCE_BYTES = 12;
    private final byte[] key;
    private final int keyVersion;
    private final SecureRandom random = new SecureRandom();

    GoogleTokenCipher(String encodedKey, int keyVersion) {
        byte[] decoded = null;
        try {
            decoded = Base64.getDecoder().decode(encodedKey);
            if (decoded.length != 32 || keyVersion < 1) throw new IllegalArgumentException();
            this.key = decoded.clone();
            this.keyVersion = keyVersion;
        } catch (RuntimeException ex) {
            throw new IllegalStateException("Google Calendar requires a Base64-encoded 32-byte token key and positive key version");
        } finally {
            if (decoded != null) Arrays.fill(decoded, (byte) 0);
        }
    }

    int keyVersion() { return keyVersion; }

    byte[] encrypt(TokenMaterial tokens, UUID connectionId, UUID userId) {
        byte[] plain = encode(tokens);
        byte[] nonce = new byte[NONCE_BYTES];
        random.nextBytes(nonce);
        try {
            var cipher = cipher(Cipher.ENCRYPT_MODE, nonce, connectionId, userId);
            byte[] ciphertext = cipher.doFinal(plain);
            return ByteBuffer.allocate(nonce.length + ciphertext.length).put(nonce).put(ciphertext).array();
        } catch (GeneralSecurityException ex) {
            throw failure();
        } finally {
            Arrays.fill(plain, (byte) 0);
        }
    }

    TokenMaterial decrypt(byte[] encrypted, int storedKeyVersion, UUID connectionId, UUID userId) {
        if (storedKeyVersion != keyVersion || encrypted == null
                || encrypted.length < NONCE_BYTES + 16 || encrypted.length > MAX_PLAIN_BYTES + NONCE_BYTES + 16)
            throw failure();
        byte[] plain = null;
        try {
            var cipher = cipher(Cipher.DECRYPT_MODE, Arrays.copyOf(encrypted, NONCE_BYTES), connectionId, userId);
            plain = cipher.doFinal(encrypted, NONCE_BYTES, encrypted.length - NONCE_BYTES);
            return decode(plain);
        } catch (GeneralSecurityException | IOException ex) {
            throw failure();
        } finally {
            if (plain != null) Arrays.fill(plain, (byte) 0);
        }
    }

    private Cipher cipher(int mode, byte[] nonce, UUID connectionId, UUID userId) throws GeneralSecurityException {
        var cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(mode, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, nonce));
        String aad = "AMS:google-calendar:tokens:v1:" + keyVersion + ":" + connectionId + ":" + userId;
        cipher.updateAAD(aad.getBytes(StandardCharsets.UTF_8));
        return cipher;
    }

    private static byte[] encode(TokenMaterial tokens) {
        try {
            var buffer = new ByteArrayOutputStream();
            var data = new DataOutputStream(buffer);
            data.writeByte(1);
            data.writeUTF(tokens.accessToken());
            data.writeUTF(tokens.refreshToken());
            data.flush();
            byte[] plain = buffer.toByteArray();
            if (plain.length > MAX_PLAIN_BYTES) throw failure();
            return plain;
        } catch (IOException ex) { throw failure(); }
    }

    private static TokenMaterial decode(byte[] plain) throws IOException {
        var data = new DataInputStream(new ByteArrayInputStream(plain));
        if (data.readUnsignedByte() != 1) throw failure();
        var tokens = new TokenMaterial(data.readUTF(), data.readUTF());
        if (data.available() != 0) throw failure();
        return tokens;
    }

    private static IllegalStateException failure() { return new IllegalStateException("Google token integrity check failed"); }

    @Override public String toString() { return "GoogleTokenCipher[redacted]"; }
    @Override public void close() { Arrays.fill(key, (byte) 0); }

    record TokenMaterial(String accessToken, String refreshToken) {
        TokenMaterial {
            if (accessToken == null || accessToken.isBlank() || refreshToken == null || refreshToken.isBlank())
                throw failure();
        }
        @Override public String toString() { return "GoogleTokenMaterial[redacted]"; }
    }
}
