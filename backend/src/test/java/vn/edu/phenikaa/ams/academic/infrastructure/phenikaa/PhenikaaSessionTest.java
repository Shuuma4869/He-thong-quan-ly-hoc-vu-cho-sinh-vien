package vn.edu.phenikaa.ams.academic.infrastructure.phenikaa;

import static org.assertj.core.api.Assertions.*;

import java.io.PrintWriter;
import java.io.StringWriter;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class PhenikaaSessionTest {
    @Test
    void redactsDiagnosticOutputAndDiscardsOwnedMaterialOnClose() {
        var session = new PhenikaaSession("Bearer synthetic-secret", "fixture=synthetic-cookie",
                "synthetic-key", "synthetic-learner", "synthetic-function");
        assertThat(session.toString()).isEqualTo("PhenikaaSession[redacted]");
        assertThat(JsonMapper.builder().build().writeValueAsString(session)).doesNotContain("synthetic");
        assertThat(session.authorization()).isEqualTo("Bearer synthetic-secret");
        session.close();
        session.close();
        assertThatThrownBy(session::authorization).hasMessage("Session is closed");
        assertThatThrownBy(session::responseKey).hasMessage("Session is closed");
        assertThatThrownBy(session::learnerId).hasMessage("Session is closed");
    }

    @Test
    void rejectsHeaderInjectionWithoutEchoingInput() {
        assertThatThrownBy(() -> new PhenikaaSession("Bearer synthetic\r\nInjected: secret", "", "key", "learner", "function"))
                .hasMessage("Invalid session material").hasNoCause();
        assertThatThrownBy(() -> new PhenikaaSession("Basic synthetic", "", "key", "learner", "function"))
                .hasMessage("Invalid authorization scheme");
    }

    @Test
    void exceptionsContainOnlyStableCodes() {
        for (var code : PhenikaaClientException.Code.values()) {
            var failure = new PhenikaaClientException(code);
            var output = new StringWriter();
            failure.printStackTrace(new PrintWriter(output));
            assertThat(failure).hasMessage(code.name()).hasNoCause();
            assertThat(output.toString()).doesNotContain("Bearer", "Cookie", "Data.B", "synthetic-secret");
            assertThat(failure.reconnectionRequired()).isEqualTo(code == PhenikaaClientException.Code.SESSION_EXPIRED);
        }
    }
}
