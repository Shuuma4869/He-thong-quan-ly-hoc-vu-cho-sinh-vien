package vn.edu.phenikaa.ams.academic.infrastructure.phenikaa;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import static vn.edu.phenikaa.ams.academic.infrastructure.phenikaa.PhenikaaClientException.Code.*;

public final class PhenikaaPayloadCodec {
    private final int maxBytes;

    public PhenikaaPayloadCodec(int maxBytes) {
        if (maxBytes < 1 || maxBytes > 16 * 1024 * 1024) throw new IllegalArgumentException("Invalid codec limit");
        this.maxBytes = maxBytes;
    }

    public String encodeRequest(String json, String actionKey) {
        validateKey(actionKey);
        if (json == null || json.length() > maxBytes) throw new PhenikaaClientException(RESPONSE_TOO_LARGE);
        try {
            var bytes = StandardCharsets.UTF_8.newEncoder()
                    .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
                    .encode(CharBuffer.wrap(xor(json, actionKey)));
            if (bytes.remaining() > maxBytes) throw new PhenikaaClientException(RESPONSE_TOO_LARGE);
            byte[] data = new byte[bytes.remaining()];
            bytes.get(data);
            try { return Base64.getEncoder().encodeToString(data); }
            finally { java.util.Arrays.fill(data, (byte) 0); }
        } catch (CharacterCodingException ex) {
            throw new PhenikaaClientException(DECODE_ERROR);
        }
    }

    public String decodeResponse(String encoded, String responseKey) {
        validateKey(responseKey);
        if (encoded == null || encoded.isEmpty()) throw new PhenikaaClientException(DECODE_ERROR);
        if (encoded.length() > 4L * ((maxBytes + 2L) / 3L)) throw new PhenikaaClientException(RESPONSE_TOO_LARGE);
        byte[] bytes = null;
        try {
            bytes = Base64.getDecoder().decode(encoded);
            if (bytes.length > maxBytes) throw new PhenikaaClientException(RESPONSE_TOO_LARGE);
            if (!Base64.getEncoder().encodeToString(bytes).equals(encoded)) throw new PhenikaaClientException(DECODE_ERROR);
            String transformed = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString();
            return xor(transformed, responseKey);
        } catch (IllegalArgumentException | CharacterCodingException ex) {
            throw new PhenikaaClientException(DECODE_ERROR);
        } finally {
            if (bytes != null) java.util.Arrays.fill(bytes, (byte) 0);
        }
    }

    private static String xor(String text, String key) {
        char[] result = new char[text.length()];
        for (int i = 0; i < text.length(); i++) result[i] = (char) (text.charAt(i) ^ key.charAt(i % key.length()));
        try { return new String(result); }
        finally { java.util.Arrays.fill(result, '\0'); }
    }

    private static void validateKey(String key) {
        if (key == null || key.isEmpty() || key.length() > 1024) throw new IllegalArgumentException("Invalid codec key");
    }
}
