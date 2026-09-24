package vn.edu.phenikaa.ams.academic.infrastructure.phenikaa;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.Objects;
import java.util.UUID;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public final class PhenikaaSessionCipher implements AutoCloseable {
    private static final int NONCE_BYTES = 12;
    private static final int MAX_PLAINTEXT_BYTES = 200_000;
    // The authenticated envelope stays v1 even when the encrypted payload gains a capability.
    private static final int ENVELOPE_VERSION = 1;
    private final byte[] key;
    private final int keyVersion;
    private final SecureRandom random = new SecureRandom();
    private boolean closed;

    public PhenikaaSessionCipher(String base64Key, int keyVersion) {
        byte[] decoded = null;
        try {
            decoded = Base64.getDecoder().decode(Objects.requireNonNullElse(base64Key, ""));
            if (decoded.length != 32 || keyVersion < 1) throw new IllegalArgumentException();
            this.key = decoded.clone();
            this.keyVersion = keyVersion;
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("Phenikaa persistence requires a Base64-encoded 32-byte key and a positive key version");
        } finally {
            if (decoded != null) Arrays.fill(decoded, (byte) 0);
        }
    }

    public int keyVersion() { return keyVersion; }

    public byte[] encrypt(PhenikaaSessionMaterial material, UUID connectionId, UUID userId) {
        byte[] plain = material.encode();
        try { return seal(plain, connectionId, userId, "session"); }
        finally { Arrays.fill(plain, (byte) 0); }
    }

    public PhenikaaSessionMaterial decrypt(byte[] encrypted, int storedKeyVersion, UUID connectionId, UUID userId) {
        byte[] plain = open(encrypted, storedKeyVersion, connectionId, userId, "session");
        try { return PhenikaaSessionMaterial.decode(plain); }
        finally { Arrays.fill(plain, (byte) 0); }
    }

    byte[] encryptSubject(PhenikaaSessionMaterial material, UUID connectionId, UUID userId) {
        byte[] plain = material.profile().learnerId().getBytes(StandardCharsets.UTF_8);
        try { return seal(plain, connectionId, userId, "subject"); }
        finally { Arrays.fill(plain, (byte) 0); }
    }

    boolean matchesSubject(byte[] encrypted, int storedKeyVersion, PhenikaaSessionMaterial material,
                           UUID connectionId, UUID userId) {
        byte[] existing = open(encrypted, storedKeyVersion, connectionId, userId, "subject");
        byte[] candidate = material.profile().learnerId().getBytes(StandardCharsets.UTF_8);
        try { return java.security.MessageDigest.isEqual(existing, candidate); }
        finally { Arrays.fill(existing, (byte) 0); Arrays.fill(candidate, (byte) 0); }
    }

    private synchronized byte[] seal(byte[] plain, UUID connectionId, UUID userId, String purpose) {
        ensureOpen();
        if (plain.length > MAX_PLAINTEXT_BYTES) throw failure();
        byte[] nonce = new byte[NONCE_BYTES];
        random.nextBytes(nonce);
        try {
            Cipher cipher = cipher(Cipher.ENCRYPT_MODE, nonce, connectionId, userId, purpose);
            byte[] encrypted = cipher.doFinal(plain);
            return ByteBuffer.allocate(nonce.length + encrypted.length).put(nonce).put(encrypted).array();
        } catch (GeneralSecurityException ex) { throw failure(); }
    }

    private synchronized byte[] open(byte[] encrypted, int storedKeyVersion, UUID connectionId, UUID userId, String purpose) {
        ensureOpen();
        if (storedKeyVersion != keyVersion || encrypted == null || encrypted.length < NONCE_BYTES + 16
                || encrypted.length > MAX_PLAINTEXT_BYTES + NONCE_BYTES + 16) throw failure();
        try {
            Cipher cipher = cipher(Cipher.DECRYPT_MODE, Arrays.copyOf(encrypted, NONCE_BYTES), connectionId, userId, purpose);
            return cipher.doFinal(encrypted, NONCE_BYTES, encrypted.length - NONCE_BYTES);
        } catch (GeneralSecurityException ex) { throw failure(); }
    }

    private Cipher cipher(int mode, byte[] nonce, UUID connectionId, UUID userId, String purpose) throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(mode, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, nonce));
        String context = "AMS:phenikaa:" + purpose + ":" + ENVELOPE_VERSION
                + ":" + keyVersion + ":" + Objects.requireNonNull(connectionId) + ":" + Objects.requireNonNull(userId);
        cipher.updateAAD(context.getBytes(StandardCharsets.UTF_8));
        return cipher;
    }

    private void ensureOpen() { if (closed) throw failure(); }
    private static IllegalStateException failure() { return new IllegalStateException("Session encryption or integrity check failed"); }
    @Override public String toString() { return "PhenikaaSessionCipher[redacted]"; }
    @Override public synchronized void close() { Arrays.fill(key, (byte) 0); closed = true; }
}
