package vn.edu.phenikaa.ams.academic.infrastructure.phenikaa;

import static org.assertj.core.api.Assertions.*;
import static vn.edu.phenikaa.ams.academic.infrastructure.phenikaa.PhenikaaClientException.Code.*;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.json.JsonMapper;
import vn.edu.phenikaa.ams.academic.application.port.ScheduleObservation;

class PhenikaaHttpTransportTest {
    private static final String KEY = "synthetic-key";
    private static final LocalDate DAY = LocalDate.of(2026, 9, 21);
    private static final String ITEM = """
            {"ID":"synthetic-mutable-id","IDLICHHOC":"synthetic-schedule",
             "IDLOPHOCPHAN":"synthetic-section","DANGKY_LOPHOCPHAN_ID":"synthetic-enrollment",
             "TENHOCPHAN":"Môn học giả định","NGAYHOC":"21/09/2026",
             "GIOBATDAU":7.0,"PHUTBATDAU":0.0,"GIOKETTHUC":9.0,"PHUTKETTHUC":30.0,
             "TENPHONGHOC":"Phòng giả định","GIANGVIEN":"Giảng viên mẫu","PHANLOAI":"LICHHOC"}
            """;
    private final JsonMapper json = JsonMapper.builder().build();
    private final PhenikaaPayloadCodec codec = new PhenikaaPayloadCodec(8192);
    private final PhenikaaSession session = new PhenikaaSession("Bearer synthetic-token", "fixture=synthetic-cookie", KEY,
            "synthetic-learner", "synthetic-function");
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private HttpServer server;
    private PhenikaaHttpTransport transport;
    private PhenikaaHttpClient client;
    private volatile HttpHandler handler;

    @BeforeEach
    void startLoopbackServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(executor);
        handler = exchange -> reply(exchange, 200, envelope("[" + ITEM + "]"));
        server.createContext(PhenikaaHttpTransport.SCHEDULE_PATH, exchange -> {
            try (exchange) { handler.handle(exchange); }
        });
        server.start();
        configure(Duration.ofSeconds(3), 8192);
    }

    private URI address() { return URI.create("http://127.0.0.1:" + server.getAddress().getPort()); }

    private void configure(Duration timeout, int maxBytes) {
        if (transport != null) transport.close();
        transport = new PhenikaaHttpTransport(address(), Duration.ofSeconds(1), timeout, maxBytes);
        client = new PhenikaaHttpClient(transport, codec);
    }

    @AfterEach
    void stop() {
        transport.close();
        session.close();
        server.stop(0);
        executor.shutdownNow();
    }

    @Test
    void sendsObservedProtocolAndNormalizesWithoutClaimingPersistenceIdentity() {
        var requestChecked = new CompletableFuture<Boolean>();
        handler = exchange -> {
            try {
                assertThat(exchange.getRequestMethod()).isEqualTo("POST");
                assertThat(exchange.getRequestHeaders().getFirst("Authorization")).isEqualTo("Bearer synthetic-token");
                assertThat(exchange.getRequestHeaders().getFirst("Cookie")).isEqualTo("fixture=synthetic-cookie");
                assertThat(exchange.getRequestHeaders().getFirst("Origin")).isEqualTo(PhenikaaHttpTransport.PORTAL.toString());
                String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                assertThat(body).startsWith("A=");
                var parameters = json.readTree(codec.decodeResponse(URLDecoder.decode(body.substring(2), StandardCharsets.UTF_8), "DSA4BRINKCIpAiAPKSAv"));
                assertThat(parameters.path("action").asString()).isEqualTo("SV_ThongTin_MH/DSA4BRINKCIpAiAPKSAv");
                assertThat(parameters.path("func").asString()).isEqualTo("pkg_congthongtin_hssv_thongtin.LayDSLichCaNhan");
                assertThat(parameters.path("iM").asString()).isEqualTo(KEY);
                assertThat(parameters.path("strQLSV_NguoiHoc_Id").asString()).isEqualTo("synthetic-learner");
                assertThat(parameters.path("strNguoiThucHien_Id").asString()).isEqualTo("synthetic-learner");
                assertThat(parameters.path("strChucNang_Id").asString()).isEqualTo("synthetic-function");
                assertThat(parameters.path("strNgayBatDau").asString()).isEqualTo("21/09/2026");
                assertThat(parameters.path("strNgayKetThuc").asString()).isEqualTo("21/09/2026");
                requestChecked.complete(true);
            } catch (Throwable failure) { requestChecked.completeExceptionally(failure); }
            reply(exchange, 200, envelope("[" + ITEM + "]"));
        };
        var observation = fetch();
        assertThat(requestChecked.join()).isTrue();
        assertThat(observation.zone()).isEqualTo(ZoneId.of("Asia/Ho_Chi_Minh"));
        assertThat(observation.completeness()).isEqualTo(ScheduleObservation.Completeness.UNKNOWN);
        var item = observation.entries().getFirst();
        assertThat(item.date()).isEqualTo(DAY);
        assertThat(item.startsAt()).isEqualTo(LocalTime.of(7, 0));
        assertThat(item.endsAt()).isEqualTo(LocalTime.of(9, 30));
        assertThat(item.kind()).isEqualTo(ScheduleObservation.Kind.CLASS);
        assertThat(item.identity().scheduleId()).isEqualTo("synthetic-schedule");
        assertThat(item.identity().sectionId()).isEqualTo("synthetic-section");
        assertThat(item.identity().enrollmentSectionId()).isEqualTo("synthetic-enrollment");
        assertThat(item.identity().scope()).isEqualTo(ScheduleObservation.IdentityScope.UNVERIFIED);
        assertThat(observation.toString() + item + item.identity()).doesNotContain("synthetic", "Môn học", "Phòng");
        assertThatThrownBy(() -> observation.entries().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "{\"Success\":false,\"Message\":\"synthetic-private-message\"}|BUSINESS_FAILURE",
            "{\"Success\":\"true\"}|UNEXPECTED_SCHEMA",
            "{\"Success\":true,\"Data\":null}|UNEXPECTED_SCHEMA",
            "{\"Success\":true,\"Data\":{\"B\":42}}|UNEXPECTED_SCHEMA",
            "{\"Success\":true,\"Data\":{\"B\":\"!\"}}|DECODE_ERROR",
            "{\"Success\":true,\"Success\":false}|UNEXPECTED_SCHEMA",
            "not-json|UNEXPECTED_SCHEMA",
            "null|UNEXPECTED_SCHEMA"
    })
    void distinguishesOuterSchemaAndBusinessErrors(String response, String code) {
        handler = exchange -> reply(exchange, 200, response);
        assertFailure(PhenikaaClientException.Code.valueOf(code));
    }

    @Test
    void distinguishesDecodedSyntaxFromDecodedSchema() {
        handler = exchange -> reply(exchange, 200, envelope("not-json"));
        assertFailure(DECODE_ERROR);
        handler = exchange -> reply(exchange, 200, envelope("{}"));
        assertFailure(UNEXPECTED_SCHEMA);
        handler = exchange -> reply(exchange, 200, envelope("[] {}"));
        assertFailure(DECODE_ERROR);
    }

    @ParameterizedTest
    @CsvSource({"401,SESSION_EXPIRED", "403,HTTP_ERROR", "500,HTTP_ERROR", "204,UNEXPECTED_SCHEMA"})
    void classifiesHttpStatus(int status, String code) {
        handler = exchange -> reply(exchange, status, "{}");
        assertFailure(PhenikaaClientException.Code.valueOf(code));
    }

    @Test
    void detectsLoginRedirectButNeverFollowsOtherRedirectsOrForwardsCredentials() throws IOException {
        handler = exchange -> {
            exchange.getResponseHeaders().set("Location", "/conggiangvien/login.aspx");
            reply(exchange, 302, "{}");
        };
        assertFailure(SESSION_EXPIRED);
        var received = new AtomicInteger();
        var other = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        other.createContext("/", exchange -> { received.incrementAndGet(); exchange.close(); });
        other.start();
        try {
            handler = exchange -> {
                exchange.getResponseHeaders().set("Location", "http://127.0.0.1:" + other.getAddress().getPort() + "/private");
                reply(exchange, 307, "{}");
            };
            assertFailure(HTTP_ERROR);
            assertThat(received).hasValue(0);
        } finally { other.stop(0); }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void timesOutBeforeHeadersAndDuringBody(boolean sendHeaders) {
        configure(Duration.ofMillis(200), 8192);
        handler = exchange -> {
            if (sendHeaders) {
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, 0);
                exchange.getResponseBody().write('{');
                exchange.getResponseBody().flush();
            }
            try { Thread.sleep(1500); } catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
        };
        assertFailure(TIMEOUT);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void boundsBothFixedAndChunkedBodies(boolean chunked) {
        configure(Duration.ofSeconds(3), 1024);
        handler = exchange -> {
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            byte[] bytes = "x".repeat(2048).getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, chunked ? 0 : bytes.length);
            exchange.getResponseBody().write(bytes);
        };
        assertFailure(RESPONSE_TOO_LARGE);
    }

    @Test
    void rejectsUnexpectedMediaAndNetworkFailure() {
        handler = exchange -> {
            exchange.getResponseHeaders().set("Content-Type", "text/html");
            exchange.sendResponseHeaders(200, 2);
            exchange.getResponseBody().write("{}".getBytes(StandardCharsets.UTF_8));
        };
        assertFailure(UNEXPECTED_SCHEMA);
        server.stop(0);
        assertFailure(NETWORK_ERROR);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "21/09/2026|31/02/2026", "21/09/2026|09/21/2026", "21/09/2026|22/09/2026",
            "\"GIOBATDAU\":7.0|\"GIOBATDAU\":7.5", "\"GIOBATDAU\":7.0|\"GIOBATDAU\":null",
            "\"GIOBATDAU\":7.0|\"GIOBATDAU\":24", "\"GIOKETTHUC\":9.0|\"GIOKETTHUC\":6",
            "\"PHUTKETTHUC\":30.0|\"PHUTKETTHUC\":60", "\"GIOBATDAU\":7.0|\"GIOBATDAU\":\"7\"",
            "\"GIOBATDAU\":7.0|\"GIOBATDAU\":7.00000000000000000001",
            "\"NGAYHOC\":\"21/09/2026\"|\"NGAYHOC\":null"
    })
    void rejectsInvalidDateTimeAndUnprovenCoercions(String original, String replacement) {
        handler = exchange -> reply(exchange, 200, envelope("[" + ITEM.replace(original, replacement) + "]"));
        assertFailure(UNEXPECTED_SCHEMA);
    }

    @Test
    void acceptsLeapDayNullTimesAndUnknownClassificationWithoutUsingSystemLocale() {
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.US);
            handler = exchange -> reply(exchange, 200, envelope("[{\"NGAYHOC\":\"29/02/2024\",\"PHANLOAI\":\"UNRECOGNIZED\"}]"));
            var day = LocalDate.of(2024, 2, 29);
            var entry = client.fetchSchedule(session, day, day).entries().getFirst();
            assertThat(entry.date()).isEqualTo(day);
            assertThat(entry.startsAt()).isNull();
            assertThat(entry.endsAt()).isNull();
            assertThat(entry.identity().scheduleId()).isNull();
            assertThat(entry.kind()).isEqualTo(ScheduleObservation.Kind.UNKNOWN);
        } finally { Locale.setDefault(previous); }
    }

    @Test
    void emptyResultsAreNotDeclaredCompleteAndDateRangeIsBounded() {
        handler = exchange -> reply(exchange, 200, envelope("[]"));
        assertThat(fetch().entries()).isEmpty();
        assertThat(fetch().completeness()).isEqualTo(ScheduleObservation.Completeness.UNKNOWN);
        assertThatThrownBy(() -> client.fetchSchedule(session, DAY, DAY.plusDays(31))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> client.fetchSchedule(session, DAY, DAY.minusDays(1))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void preservesSeparateDatesWhenSourceScheduleIdIsReused() {
        handler = exchange -> reply(exchange, 200, envelope("[" + ITEM + ","
                + ITEM.replace("21/09/2026", "28/09/2026") + "]"));
        var result = client.fetchSchedule(session, DAY, DAY.plusDays(7));
        assertThat(result.entries()).hasSize(2);
        assertThat(result.entries().getFirst().identity()).isEqualTo(result.entries().getLast().identity());
        assertThat(result.entries().getFirst().date()).isNotEqualTo(result.entries().getLast().date());
        assertThat(result.entries().getFirst().identity().scope()).isEqualTo(ScheduleObservation.IdentityScope.UNVERIFIED);
    }

    @Test
    void forbidsUntrustedAddressesAndWireLogging() {
        for (String uri : new String[]{"https://example.invalid", "http://qldtbeta.phenikaa-uni.edu.vn", "http://user@127.0.0.1", "http://127.0.0.1/path"}) {
            assertThatThrownBy(() -> new PhenikaaHttpTransport(URI.create(uri), Duration.ofSeconds(1), Duration.ofSeconds(1), 1024))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        String previous = System.getProperty("jdk.httpclient.HttpClient.log");
        try {
            System.setProperty("jdk.httpclient.HttpClient.log", "headers,content");
            assertThatThrownBy(() -> new PhenikaaHttpTransport(Duration.ofSeconds(1), Duration.ofSeconds(1), 1024))
                    .isInstanceOf(IllegalStateException.class).hasMessageContaining("wire logging must be disabled");
        } finally {
            if (previous == null) System.clearProperty("jdk.httpclient.HttpClient.log");
            else System.setProperty("jdk.httpclient.HttpClient.log", previous);
        }
    }

    private ScheduleObservation fetch() { return client.fetchSchedule(session, DAY, DAY); }

    private void assertFailure(PhenikaaClientException.Code code) {
        assertThatThrownBy(this::fetch).isInstanceOf(PhenikaaClientException.class).hasMessage(code.name()).hasNoCause();
    }

    private String envelope(String data) {
        return json.writeValueAsString(Map.of("Success", true, "Data", Map.of("B", codec.encodeRequest(data, KEY))));
    }

    private static void reply(HttpExchange exchange, int status, String body) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        if (status == 204) { exchange.sendResponseHeaders(status, -1); return; }
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
    }
}
