package vn.edu.phenikaa.ams.academic.infrastructure.phenikaa;

import java.io.*;
import java.util.Arrays;

/** Integration-only session context; never a request or response DTO. */
public final class PhenikaaSessionMaterial implements AutoCloseable {
    static final int FORMAT_VERSION = 1;
    private final PhenikaaSession profile;
    private final PhenikaaSession schedule;

    public PhenikaaSessionMaterial(String authorization, String cookie, String responseKey, String learnerId,
                                  String profileFunctionId, String scheduleFunctionId) {
        if (authorization == null || !authorization.startsWith("Bearer ") || authorization.substring(7).isBlank())
            throw new IllegalArgumentException("Missing portal authorization");
        profile = new PhenikaaSession(authorization, cookie, responseKey, learnerId, profileFunctionId);
        try {
            schedule = new PhenikaaSession(authorization, cookie, responseKey, learnerId, scheduleFunctionId);
        } catch (RuntimeException ex) {
            profile.close();
            throw ex;
        }
    }

    PhenikaaSession profile() { return profile; }
    PhenikaaSession schedule() { return schedule; }

    byte[] encode() {
        try (var buffer = new WipingBuffer(); var output = new DataOutputStream(buffer)) {
            output.writeInt(FORMAT_VERSION);
            for (String value : new String[]{profile.authorization(), profile.cookie(), profile.responseKey(),
                    profile.learnerId(), profile.functionId(), schedule.functionId()}) output.writeUTF(value);
            return buffer.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("Cannot encode session material");
        }
    }

    static PhenikaaSessionMaterial decode(byte[] bytes) {
        try (var input = new DataInputStream(new ByteArrayInputStream(bytes))) {
            if (input.readInt() != FORMAT_VERSION) throw new IOException();
            var result = new PhenikaaSessionMaterial(input.readUTF(), input.readUTF(), input.readUTF(),
                    input.readUTF(), input.readUTF(), input.readUTF());
            if (input.available() != 0) {
                result.close();
                throw new IOException();
            }
            return result;
        } catch (IOException | IllegalArgumentException ex) {
            throw new IllegalStateException("Invalid encrypted session format");
        }
    }

    @Override public String toString() { return "PhenikaaSessionMaterial[redacted]"; }
    @Override public void close() { profile.close(); schedule.close(); }

    private static final class WipingBuffer extends ByteArrayOutputStream {
        @Override public void close() { Arrays.fill(buf, (byte) 0); reset(); }
    }
}
