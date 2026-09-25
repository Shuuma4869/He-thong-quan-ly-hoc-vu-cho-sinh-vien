package vn.edu.phenikaa.ams.academic.infrastructure.phenikaa;

import java.io.*;
import java.util.Arrays;

/** Integration-only session context; never a request or response DTO. */
public final class PhenikaaSessionMaterial implements AutoCloseable {
    static final int FORMAT_VERSION = 3;
    private final PhenikaaSession profile;
    private final PhenikaaSession schedule;
    private final PhenikaaSession exam;
    private final PhenikaaSession academic;

    public PhenikaaSessionMaterial(String authorization, String cookie, String responseKey, String learnerId,
                                  String profileFunctionId, String scheduleFunctionId) {
        this(authorization, cookie, responseKey, learnerId, profileFunctionId, scheduleFunctionId, null);
    }

    public PhenikaaSessionMaterial(String authorization, String cookie, String responseKey, String learnerId,
                                  String profileFunctionId, String scheduleFunctionId, String examFunctionId) {
        this(authorization, cookie, responseKey, learnerId, profileFunctionId, scheduleFunctionId, examFunctionId, null);
    }

    public PhenikaaSessionMaterial(String authorization, String cookie, String responseKey, String learnerId,
                                  String profileFunctionId, String scheduleFunctionId, String examFunctionId,
                                  String academicFunctionId) {
        if (authorization == null || !authorization.startsWith("Bearer ") || authorization.substring(7).isBlank())
            throw new IllegalArgumentException("Missing portal authorization");
        profile = new PhenikaaSession(authorization, cookie, responseKey, learnerId, profileFunctionId);
        try {
            schedule = new PhenikaaSession(authorization, cookie, responseKey, learnerId, scheduleFunctionId);
        } catch (RuntimeException ex) {
            profile.close();
            throw ex;
        }
        try {
            exam = examFunctionId == null ? null : new PhenikaaSession(authorization, cookie, responseKey, learnerId, examFunctionId);
        } catch (RuntimeException ex) {
            profile.close();
            schedule.close();
            throw ex;
        }
        try {
            academic = academicFunctionId == null ? null : new PhenikaaSession(authorization, cookie, responseKey, learnerId, academicFunctionId);
        } catch (RuntimeException ex) {
            profile.close();
            schedule.close();
            if (exam != null) exam.close();
            throw ex;
        }
    }

    PhenikaaSession profile() { return profile; }
    PhenikaaSession schedule() { return schedule; }
    PhenikaaSession exam() { return exam; }
    PhenikaaSession academic() { return academic; }

    byte[] encode() {
        try (var buffer = new WipingBuffer(); var output = new DataOutputStream(buffer)) {
            output.writeInt(FORMAT_VERSION);
            for (String value : new String[]{profile.authorization(), profile.cookie(), profile.responseKey(),
                    profile.learnerId(), profile.functionId(), schedule.functionId()}) output.writeUTF(value);
            output.writeBoolean(exam != null);
            if (exam != null) output.writeUTF(exam.functionId());
            output.writeBoolean(academic != null);
            if (academic != null) output.writeUTF(academic.functionId());
            return buffer.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("Cannot encode session material");
        }
    }

    static PhenikaaSessionMaterial decode(byte[] bytes) {
        try (var input = new DataInputStream(new ByteArrayInputStream(bytes))) {
            int version = input.readInt();
            if (version < 1 || version > FORMAT_VERSION) throw new IOException();
            String authorization = input.readUTF(), cookie = input.readUTF(), responseKey = input.readUTF();
            String learner = input.readUTF(), profileFunction = input.readUTF(), scheduleFunction = input.readUTF();
            String examFunction = version >= 2 && input.readBoolean() ? input.readUTF() : null;
            String academicFunction = version >= 3 && input.readBoolean() ? input.readUTF() : null;
            var result = new PhenikaaSessionMaterial(authorization, cookie, responseKey, learner,
                    profileFunction, scheduleFunction, examFunction, academicFunction);
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
    @Override public void close() {
        profile.close(); schedule.close();
        if (exam != null) exam.close();
        if (academic != null) academic.close();
    }

    private static final class WipingBuffer extends ByteArrayOutputStream {
        @Override public void close() { Arrays.fill(buf, (byte) 0); reset(); }
    }
}
