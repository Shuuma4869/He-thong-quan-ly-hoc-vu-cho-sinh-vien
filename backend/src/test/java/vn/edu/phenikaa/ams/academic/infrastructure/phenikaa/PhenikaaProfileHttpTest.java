package vn.edu.phenikaa.ams.academic.infrastructure.phenikaa;

import com.sun.net.httpserver.*;
import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.*;
import static vn.edu.phenikaa.ams.academic.infrastructure.phenikaa.PhenikaaClientException.Code.*;

class PhenikaaProfileHttpTest {
    private final JsonMapper json = JsonMapper.builder().build();
    private final PhenikaaPayloadCodec codec = new PhenikaaPayloadCodec(8192);
    private final PhenikaaSessionMaterial material = PhenikaaSessionCipherTest.material();
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private HttpServer server;
    private PhenikaaHttpTransport transport;
    private PhenikaaHttpClient client;
    private volatile HttpHandler handler;

    @BeforeEach void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(executor);
        server.createContext(PhenikaaHttpTransport.PROFILE_PATH, exchange -> {
            try (exchange) { handler.handle(exchange); }
        });
        server.start();
        transport = new PhenikaaHttpTransport(URI.create("http://127.0.0.1:" + server.getAddress().getPort()),
                Duration.ofSeconds(1), Duration.ofMillis(500), 8192);
        client = new PhenikaaHttpClient(transport, codec);
    }

    @AfterEach void stop() { transport.close(); material.close(); server.stop(0); executor.shutdownNow(); }

    @Test void sendsObservedProfileParametersAndMapsOnlyNecessaryFields() throws Exception {
        var checked = new CompletableFuture<Boolean>();
        handler = exchange -> {
            try {
                assertThat(exchange.getRequestMethod()).isEqualTo("POST");
                String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                var params = json.readTree(codec.decodeResponse(URLDecoder.decode(body.substring(2), StandardCharsets.UTF_8),
                        "DSA4FSkuLyYVKC8CKSgVKCQ1CS4SLgPP"));
                assertThat(params.size()).isEqualTo(6);
                assertThat(params.path("action").asString()).isEqualTo("SV_Custom/DSA4FSkuLyYVKC8CKSgVKCQ1CS4SLgPP");
                assertThat(params.path("func").asString()).isEqualTo("pkg_hosohocvien.LayThongTinChiTietHoSo");
                assertThat(params.path("strId").asString()).isEqualTo("synthetic-learner");
                assertThat(params.path("strNguoiThucHien_Id").asString()).isEqualTo("synthetic-learner");
                assertThat(params.path("strChucNang_Id").asString()).isEqualTo("synthetic-profile-function");
                assertThat(params.path("iM").asString()).isEqualTo("synthetic-key");
                checked.complete(true);
            } catch (Throwable ex) { checked.completeExceptionally(ex); }
            reply(exchange, 200, envelope("""
                    [{"ID":"synthetic-learner","MASO":"SYNTHETIC-001","NGANH":"Ngành giả định",
                      "HOTEN":"Không nhập trường này","KHOADAOTAO":"Không suy cohort"}]
                    """));
        };
        var result = client.fetchProfile(material.profile());
        assertThat(checked.get(1, TimeUnit.SECONDS)).isTrue();
        assertThat(result.studentNumber()).isEqualTo("SYNTHETIC-001");
        assertThat(result.programName()).isEqualTo("Ngành giả định");
        assertThat(result.toString()).isEqualTo("ProfileObservation[redacted]");
    }

    @ParameterizedTest
    @ValueSource(strings = {"[]", "{}", "[null]", "[{\"ID\":\"other-learner\",\"MASO\":\"SYNTHETIC-001\"}]",
            "[{\"ID\":\"synthetic-learner\",\"MASO\":123}]",
            "[{\"ID\":\"synthetic-learner\",\"MASO\":\" \"}]",
            "[{\"ID\":\"synthetic-learner\",\"MASO\":\"SYNTHETIC-001\",\"NGANH\":[]}]"})
    void rejectsMalformedOrWrongSubjectProfiles(String data) {
        handler = exchange -> reply(exchange, 200, envelope(data));
        failure(UNEXPECTED_SCHEMA);
    }

    @Test void preservesUnknownProgramAsNull() {
        handler = exchange -> reply(exchange, 200, envelope("[{\"ID\":\"synthetic-learner\",\"MASO\":\"SYNTHETIC-001\"}]"));
        assertThat(client.fetchProfile(material.profile()).programName()).isNull();
    }

    @Test void rejectsBusinessFailureWithoutEchoingMessage() {
        handler = exchange -> reply(exchange, 200, "{\"Success\":false,\"Message\":\"synthetic-private-detail\"}");
        failure(BUSINESS_FAILURE);
    }

    @Test void identifiesUnauthorized() { handler = exchange -> reply(exchange, 401, "{}"); failure(SESSION_EXPIRED); }

    @Test void recognizesLoginRedirectWithoutFollowingIt() {
        handler = exchange -> {
            exchange.getResponseHeaders().add("Location", "/conggiangvien/login.aspx");
            reply(exchange, 302, "{}");
        };
        failure(SESSION_EXPIRED);
    }

    @Test void limitsWholeResponseTime() {
        handler = exchange -> {
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, 0);
            exchange.getResponseBody().write('{');
            exchange.getResponseBody().flush();
            try { Thread.sleep(1500); } catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
        };
        failure(TIMEOUT);
    }

    @Test void limitsBodySize() {
        handler = exchange -> reply(exchange, 200, "x".repeat(9000));
        failure(RESPONSE_TOO_LARGE);
    }

    @Test void rejectsInvalidEncodedPayload() {
        handler = exchange -> reply(exchange, 200, "{\"Success\":true,\"Data\":{\"B\":\"invalid-base64!\"}}");
        failure(DECODE_ERROR);
    }

    private void failure(PhenikaaClientException.Code code) {
        assertThatThrownBy(() -> client.fetchProfile(material.profile())).isInstanceOf(PhenikaaClientException.class)
                .hasMessage(code.name()).hasNoCause();
    }
    private String envelope(String decoded) {
        return json.writeValueAsString(Map.of("Success", true, "Data", Map.of("B", codec.encodeRequest(decoded, "synthetic-key"))));
    }
    private static void reply(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
    }
}
