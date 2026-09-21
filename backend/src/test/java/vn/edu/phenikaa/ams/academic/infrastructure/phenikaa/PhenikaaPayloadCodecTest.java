package vn.edu.phenikaa.ams.academic.infrastructure.phenikaa;

import static org.assertj.core.api.Assertions.*;
import static vn.edu.phenikaa.ams.academic.infrastructure.phenikaa.PhenikaaClientException.Code.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class PhenikaaPayloadCodecTest {
    private final PhenikaaPayloadCodec codec = new PhenikaaPayloadCodec(4096);

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "{}|CAQ=",
            "{\"name\":\"Sinh viên mẫu\",\"enabled\":true,\"items\":[1,null]}|CFsAFQUAVlNBfgILEVMPB8KeBkUZ4buCFg9HRxwdGAwYDQFWUxdfHgBVURAaEQUWVlM4HEcLDB8VMwk=",
            "{\"label\":\"📚\",\"date\":\"21/09/2026\"}|CFsCFQoAGEtZD/Clor9bX1sKFRwAVlNBH1pKSUpWXERaU1YU"
    })
    void matchesSyntheticVectorsComparedWithPortalFunctions(String plain, String encoded) {
        assertThat(codec.encodeRequest(plain, "synthetic-key")).isEqualTo(encoded);
        assertThat(codec.decodeResponse(encoded, "synthetic-key")).isEqualTo(plain);
    }

    @ParameterizedTest
    @ValueSource(strings = {"!", "CAQ", "CAQ=\n", "/w==", ""})
    void rejectsMalformedBase64OrUtf8(String encoded) {
        assertThatThrownBy(() -> codec.decodeResponse(encoded, "synthetic-key"))
                .isInstanceOf(PhenikaaClientException.class).hasMessage(DECODE_ERROR.name()).hasNoCause();
    }

    @Test
    void boundsInputAndRejectsInvalidKeyAndUnicode() {
        var small = new PhenikaaPayloadCodec(1);
        assertThatThrownBy(() -> small.encodeRequest("{}", "key")).hasMessage(RESPONSE_TOO_LARGE.name());
        assertThatThrownBy(() -> small.decodeResponse("CAQ=", "key")).hasMessage(RESPONSE_TOO_LARGE.name());
        assertThatThrownBy(() -> codec.encodeRequest("{}", "")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> codec.decodeResponse("CAQ=", null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> codec.encodeRequest("\uD800", "a")).hasMessage(DECODE_ERROR.name());
        assertThatThrownBy(() -> new PhenikaaPayloadCodec(0)).isInstanceOf(IllegalArgumentException.class);
    }
}
