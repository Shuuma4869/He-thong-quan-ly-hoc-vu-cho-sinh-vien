package vn.edu.phenikaa.ams.academic.infrastructure.phenikaa;

import com.sun.net.httpserver.*;
import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.json.JsonMapper;
import vn.edu.phenikaa.ams.academic.application.port.*;
import static org.assertj.core.api.Assertions.*;
import static vn.edu.phenikaa.ams.academic.infrastructure.phenikaa.PhenikaaClientException.Code.*;

class PhenikaaExamHttpTest {
    private static final String PERIODS = "[{\"ID\":\"synthetic-period\",\"THOIGIAN\":\"Kỳ giả định\"}]";
    private static final String ITEM = """
            {"IDLICHHOC":"synthetic-exam","QLSV_NGUOIHOC_ID":"synthetic-learner",
             "MAHOCPHAN":"SYN-101","TENHOCPHAN":"Môn giả định","NGAYHOC":"24/09/2026",
             "GIOBATDAU":7.0,"PHUTBATDAU":30.0,"GIOKETTHUC":9.0,"PHUTKETTHUC":0.0,
             "LANTHI":2.0,"CATHI":"Ca giả định","PHONGHOC_TEN":"Phòng giả định"}
            """;
    private final JsonMapper json = JsonMapper.builder().build();
    private final PhenikaaPayloadCodec codec = new PhenikaaPayloadCodec(8192);
    private final PhenikaaSession session = new PhenikaaSession("Bearer synthetic-secret", "fixture=synthetic-cookie",
            "synthetic-key", "synthetic-learner", "synthetic-exam-function");
    private final ExamPeriod period = new ExamPeriod("synthetic-period", "Caller label is not trusted");
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final AtomicInteger examCalls = new AtomicInteger();
    private HttpServer server;
    private PhenikaaHttpTransport transport;
    private PhenikaaHttpClient client;
    private volatile HttpHandler examHandler;
    private volatile String periods = PERIODS;
    private final CompletableFuture<Boolean> periodRequestChecked = new CompletableFuture<>();

    @BeforeEach void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(executor);
        server.createContext(PhenikaaHttpTransport.EXAM_PERIODS_PATH, exchange -> {
            try (exchange) {
                try {
                    var params = parameters(exchange, "DSA4BRIVKS4oBiggLw0oIikVKSgP");
                    assertThat(params.size()).isEqualTo(5);
                    assertThat(params.path("func").asString()).endsWith(".LayDSThoiGianLichThi");
                    assertThat(params.path("strNguoiThucHien_Id").asString()).isEqualTo("synthetic-learner");
                    assertThat(params.path("strChucNang_Id").asString()).isEqualTo("synthetic-exam-function");
                    periodRequestChecked.complete(true);
                } catch (Throwable ex) { periodRequestChecked.completeExceptionally(ex); }
                reply(exchange, 200, envelope(periods));
            }
        });
        server.createContext(PhenikaaHttpTransport.EXAMS_PATH, exchange -> {
            examCalls.incrementAndGet();
            try (exchange) { examHandler.handle(exchange); }
        });
        server.start();
        transport = new PhenikaaHttpTransport(URI.create("http://127.0.0.1:" + server.getAddress().getPort()),
                Duration.ofSeconds(1), Duration.ofMillis(700), 8192);
        client = new PhenikaaHttpClient(transport, codec);
    }

    @AfterEach void stop() { transport.close(); session.close(); server.stop(0); executor.shutdownNow(); }

    @Test void sendsVerifiedScopeAndNormalizesPersonalExamsWithoutInventingEnrollment() throws Exception {
        var checked = new CompletableFuture<Boolean>();
        examHandler = exchange -> {
            try {
                assertThat(exchange.getRequestHeaders().getFirst("Authorization")).isEqualTo("Bearer synthetic-secret");
                var params = parameters(exchange, "DSA4BRINKCIpFSkoHgokCS4gIikVKSgP");
                assertThat(params.size()).isEqualTo(8);
                assertThat(params.path("func").asString()).endsWith(".LayDSLichThi_KeHoachThi");
                assertThat(params.path("strQLSV_NguoiHoc_Id").asString()).isEqualTo("synthetic-learner");
                assertThat(params.path("strNguoiThucHien_Id").asString()).isEqualTo("synthetic-learner");
                assertThat(params.path("strChucNang_Id").asString()).isEqualTo("synthetic-exam-function");
                assertThat(params.path("strDaoTao_ThoiGianDaoTao_Id").asString()).isEqualTo(period.sourceId());
                assertThat(params.path("strDaoTao_HocPhan_Id").asString()).isEmpty();
                checked.complete(true);
            } catch (Throwable ex) { checked.completeExceptionally(ex); }
            reply(exchange, 200, exams("[" + ITEM + "]"));
        };
        var result = client.fetchExams(session, period);
        assertThat(checked.get(1, TimeUnit.SECONDS)).isTrue();
        assertThat(periodRequestChecked.get(1, TimeUnit.SECONDS)).isTrue();
        assertThat(result.requestedPeriod().label()).isEqualTo("Kỳ giả định");
        assertThat(result.zone()).isEqualTo(ZoneId.of("Asia/Ho_Chi_Minh"));
        assertThat(result.completeness()).isEqualTo(ExamObservation.Completeness.UNKNOWN);
        var entry = result.entries().getFirst();
        assertThat(entry.startsAt()).isEqualTo(Instant.parse("2026-09-24T00:30:00Z"));
        assertThat(entry.endsAt()).isEqualTo(Instant.parse("2026-09-24T02:00:00Z"));
        assertThat(entry.examAttempt()).isEqualTo(2);
        assertThat(entry.courseCode()).isEqualTo("SYN-101");
        assertThat(entry.identity().scope()).isEqualTo(ExamObservation.IdentityScope.UNVERIFIED);
        assertThat(result.toString()).isEqualTo("ExamObservation[redacted]");
        assertThat(entry.toString()).isEqualTo("ExamEntry[redacted]");
        assertThat(entry.identity().toString()).isEqualTo("ExamCandidateIdentity[redacted]");
    }

    @Test void emptyResultStillHasUnknownCompleteness() {
        examHandler = e -> reply(e, 200, exams("[]"));
        var result = client.fetchExams(session, period);
        assertThat(result.entries()).isEmpty();
        assertThat(result.completeness()).isEqualTo(ExamObservation.Completeness.UNKNOWN);
    }

    @Test void absentEndTimeIsNotReplacedWithInventedDuration() {
        examHandler = e -> reply(e, 200, exams("[" + ITEM.replace("\"GIOKETTHUC\":9.0", "\"GIOKETTHUC\":null")
                .replace("\"PHUTKETTHUC\":0.0", "\"PHUTKETTHUC\":null") + "]"));
        assertThat(client.fetchExams(session, period).entries().getFirst().endsAt()).isNull();
    }

    @ParameterizedTest @ValueSource(strings = {"31/02/2026", "1/09/2026", "2026-09-24", "24/09/26"})
    void rejectsInvalidDate(String date) {
        examHandler = e -> reply(e, 200, exams("[" + ITEM.replace("24/09/2026", date) + "]"));
        failure(UNEXPECTED_SCHEMA);
    }

    @ParameterizedTest @ValueSource(strings = {"24", "-1", "7.5", "null", "\"7\""})
    void rejectsInvalidStartHour(String hour) {
        examHandler = e -> reply(e, 200, exams("[" + ITEM.replace("\"GIOBATDAU\":7.0", "\"GIOBATDAU\":" + hour) + "]"));
        failure(UNEXPECTED_SCHEMA);
    }

    @Test void rejectsWrongOwnerAndNeverReturnsPartialSuccess() {
        examHandler = e -> reply(e, 200, exams("[" + ITEM + "," + ITEM.replace("synthetic-learner", "other-learner") + "]"));
        failure(UNEXPECTED_SCHEMA);
    }

    @Test void rejectsIncompleteOrReversedTimeAndInvalidAttempt() {
        for (String item : new String[]{ITEM.replace("\"GIOKETTHUC\":9.0", "\"GIOKETTHUC\":null"),
                ITEM.replace("\"GIOKETTHUC\":9.0", "\"GIOKETTHUC\":6"),
                ITEM.replace("\"LANTHI\":2.0", "\"LANTHI\":0")}) {
            examHandler = e -> reply(e, 200, exams("[" + item + "]"));
            failure(UNEXPECTED_SCHEMA);
        }
    }

    @ParameterizedTest @ValueSource(strings = {"[]", "{}", "null", "{\"rsLichThiCaNhan\":[]}",
            "{\"rsLichThiCaNhan\":[],\"rsKeHoachThiChung\":[{}]}",
            "{\"rsLichThiCaNhan\":[null],\"rsKeHoachThiChung\":[]}"})
    void rejectsMalformedTablesOrUnverifiedCommonPlanSchema(String data) {
        examHandler = e -> reply(e, 200, envelope(data));
        failure(UNEXPECTED_SCHEMA);
    }

    @Test void refusesUnknownPeriodBeforeSendingExamRequest() {
        periods = "[]";
        failure(UNEXPECTED_SCHEMA);
        assertThat(examCalls).hasValue(0);
    }

    @Test void rejectsDuplicatePeriodIds() {
        periods = "[" + PERIODS.substring(1, PERIODS.length() - 1) + "," + PERIODS.substring(1, PERIODS.length() - 1) + "]";
        failure(UNEXPECTED_SCHEMA);
        assertThat(examCalls).hasValue(0);
    }

    @Test void rejectsMalformedPeriodWithoutEchoingSourceValues() {
        periods = "[{\"ID\":\"private-synthetic-value\",\"THOIGIAN\":null}]";
        failure(UNEXPECTED_SCHEMA);
    }

    @Test void boundsPeriodAndRecordCountsBeforeMapping() {
        transport.close();
        transport = new PhenikaaHttpTransport(URI.create("http://127.0.0.1:" + server.getAddress().getPort()),
                Duration.ofSeconds(1), Duration.ofSeconds(3), 1024 * 1024);
        client = new PhenikaaHttpClient(transport, new PhenikaaPayloadCodec(1024 * 1024));
        var largeCodec = new PhenikaaPayloadCodec(1024 * 1024);
        periods = "[" + String.join(",", java.util.Collections.nCopies(257, "{}")) + "]";
        failure(RESPONSE_TOO_LARGE);
        periods = PERIODS;
        examHandler = e -> reply(e, 200, json.writeValueAsString(Map.of("Success", true, "Data", Map.of("B",
                largeCodec.encodeRequest("{\"rsLichThiCaNhan\":["
                        + String.join(",", java.util.Collections.nCopies(10001, "null"))
                        + "],\"rsKeHoachThiChung\":[]}", "synthetic-key")))));
        failure(RESPONSE_TOO_LARGE);
    }

    @Test void rejectsMissingCourseIdentityInsteadOfFallingBackToName() {
        examHandler = e -> reply(e, 200, exams("[" + ITEM.replace("\"MAHOCPHAN\":\"SYN-101\"", "\"MAHOCPHAN\":null") + "]"));
        failure(UNEXPECTED_SCHEMA);
    }

    @Test void rejectsBusinessFailure() {
        examHandler = e -> reply(e, 200, "{\"Success\":false,\"Message\":\"synthetic-private-detail\"}");
        failure(BUSINESS_FAILURE);
    }

    @Test void recognizesUnauthorized() { examHandler = e -> reply(e, 401, "{}"); failure(SESSION_EXPIRED); }

    @Test void recognizesLoginRedirectWithoutFollowing() {
        examHandler = e -> { e.getResponseHeaders().add("Location", "/conggiangvien/login.aspx"); reply(e, 302, "{}"); };
        failure(SESSION_EXPIRED);
    }

    @Test void limitsBodySize() { examHandler = e -> reply(e, 200, "x".repeat(9000)); failure(RESPONSE_TOO_LARGE); }

    @Test void limitsWholeResponseTime() {
        examHandler = e -> {
            e.getResponseHeaders().add("Content-Type", "application/json");
            e.sendResponseHeaders(200, 0); e.getResponseBody().write('{'); e.getResponseBody().flush();
            try { Thread.sleep(1500); } catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
        };
        failure(TIMEOUT);
    }

    @Test void rejectsDecodingError() {
        examHandler = e -> reply(e, 200, "{\"Success\":true,\"Data\":{\"B\":\"invalid-base64!\"}}");
        failure(DECODE_ERROR);
    }

    private tools.jackson.databind.JsonNode parameters(HttpExchange exchange, String action) throws IOException {
        assertThat(exchange.getRequestMethod()).isEqualTo("POST");
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        return json.readTree(codec.decodeResponse(URLDecoder.decode(body.substring(2), StandardCharsets.UTF_8), action));
    }
    private void failure(PhenikaaClientException.Code code) {
        assertThatThrownBy(() -> client.fetchExams(session, period)).isInstanceOf(PhenikaaClientException.class)
                .hasMessage(code.name()).hasNoCause();
    }
    private String exams(String items) { return envelope("{\"rsLichThiCaNhan\":" + items + ",\"rsKeHoachThiChung\":[]}"); }
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
