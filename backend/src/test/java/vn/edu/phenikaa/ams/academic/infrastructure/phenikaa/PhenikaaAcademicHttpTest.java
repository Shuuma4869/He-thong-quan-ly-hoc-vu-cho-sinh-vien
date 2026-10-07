package vn.edu.phenikaa.ams.academic.infrastructure.phenikaa;

import com.sun.net.httpserver.*;
import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.json.JsonMapper;
import vn.edu.phenikaa.ams.academic.application.port.*;
import static org.assertj.core.api.Assertions.*;
import static vn.edu.phenikaa.ams.academic.infrastructure.phenikaa.PhenikaaClientException.Code.*;

class PhenikaaAcademicHttpTest {
    private final JsonMapper json = JsonMapper.builder().build();
    private final PhenikaaPayloadCodec codec = new PhenikaaPayloadCodec(32768);
    private final PhenikaaSession session = new PhenikaaSession("Bearer synthetic-secret", "", "synthetic-key",
            "synthetic-learner", "synthetic-academic-function");
    private final AcademicProgram program = new AcademicProgram("synthetic-program", "Caller label");
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final List<String> paths = new CopyOnWriteArrayList<>();
    private final CompletableFuture<Throwable> requestFailure = new CompletableFuture<>();
    private HttpServer server;
    private PhenikaaHttpTransport transport;
    private PhenikaaHttpClient client;
    private volatile String programs = AcademicSourceFixtures.PROGRAMS;
    private volatile String periods = AcademicSourceFixtures.PERIODS;
    private volatile String records = AcademicSourceFixtures.records();
    private volatile String registrations = AcademicSourceFixtures.registrations();
    private volatile HttpHandler override;
    private volatile HttpHandler detailOverride;
    private volatile String detail = "[" + AcademicSourceFixtures.component(1) + "]";

    @BeforeEach void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(executor);
        server.createContext("/sinhvienapi3/api/", exchange -> {
            try (exchange) {
                String path = exchange.getRequestURI().getPath();
                paths.add(path);
                checkRequest(exchange, path);
                if (override != null && path.equals(PhenikaaHttpTransport.ACADEMIC_RECORDS_PATH)) {
                    override.handle(exchange);
                    return;
                }
                if (detailOverride != null && path.equals(PhenikaaHttpTransport.ACADEMIC_RESULT_DETAIL_PATH)) {
                    detailOverride.handle(exchange);
                    return;
                }
                String data = switch (path) {
                    case PhenikaaHttpTransport.ACADEMIC_PROGRAMS_PATH -> programs;
                    case PhenikaaHttpTransport.ACADEMIC_PERIODS_PATH -> periods;
                    case PhenikaaHttpTransport.ACADEMIC_RECORDS_PATH -> records;
                    case PhenikaaHttpTransport.ACADEMIC_REGISTRATIONS_PATH -> registrations;
                    case PhenikaaHttpTransport.ACADEMIC_RESULT_DETAIL_PATH -> detail;
                    default -> throw new AssertionError("Unexpected test path");
                };
                reply(exchange, 200, envelope(data));
            }
        });
        server.start();
        transport = new PhenikaaHttpTransport(URI.create("http://127.0.0.1:" + server.getAddress().getPort()),
                Duration.ofSeconds(1), Duration.ofMillis(900), 32768);
        client = new PhenikaaHttpClient(transport, codec);
    }
    @AfterEach void stop() {
        transport.close(); session.close(); server.stop(0); executor.shutdownNow();
        assertThat(requestFailure.getNow(null)).isNull();
    }
    private void checkRequest(HttpExchange exchange, String path) {
        try {
            assertThat(exchange.getRequestMethod()).isEqualTo("POST");
            assertThat(exchange.getRequestHeaders().getFirst("Authorization")).isEqualTo("Bearer synthetic-secret");
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            var params = json.readTree(codec.decodeResponse(URLDecoder.decode(body.substring(2), StandardCharsets.UTF_8),
                    path.substring(path.lastIndexOf('/') + 1)));
            if (!path.equals(PhenikaaHttpTransport.ACADEMIC_RESULT_DETAIL_PATH))
                assertThat(params.path("strQLSV_NguoiHoc_Id").asString()).isEqualTo("synthetic-learner");
            assertThat(params.path("strNguoiThucHien_Id").asString()).isEqualTo("synthetic-learner");
            assertThat(params.path("strChucNang_Id").asString()).isEqualTo("synthetic-academic-function");
            assertThat(params.path("action").asString()).isEqualTo(path.substring("/sinhvienapi3/api/".length()));
            String function = switch (path) {
                case PhenikaaHttpTransport.ACADEMIC_PROGRAMS_PATH -> "LayThongTinChuongTrinhHoc";
                case PhenikaaHttpTransport.ACADEMIC_PERIODS_PATH -> "LayDSThoiGianLichHoc";
                case PhenikaaHttpTransport.ACADEMIC_RECORDS_PATH -> "KetQuaHocTapCaNhan";
                case PhenikaaHttpTransport.ACADEMIC_REGISTRATIONS_PATH -> "LayKetQuaDangKyHocCaNhan";
                case PhenikaaHttpTransport.ACADEMIC_RESULT_DETAIL_PATH -> "LayDSDiemThanhPhanTheoTKHP";
                default -> throw new AssertionError();
            };
            assertThat(params.path("func").asString()).isEqualTo("pkg_congthongtin_hssv_thongtin." + function);
            if (path.equals(PhenikaaHttpTransport.ACADEMIC_RESULT_DETAIL_PATH)) {
                assertThat(params.path("strDiem_NguoiHoc_TongKet_Id").asString()).isEqualTo("synthetic-final-1");
                assertThat(params.size()).isEqualTo(6);
                assertThat(params.has("strQLSV_NguoiHoc_Id")).isFalse();
            } else if (path.equals(PhenikaaHttpTransport.ACADEMIC_RECORDS_PATH)) {
                assertThat(params.path("strDaoTao_ChuongTrinh_Id").asString()).isEqualTo("synthetic-program");
                assertThat(params.size()).isEqualTo(7);
            } else if (path.equals(PhenikaaHttpTransport.ACADEMIC_REGISTRATIONS_PATH)) {
                assertThat(params.path("strDaoTao_ThoiGianDaoTao_Id").asString()).isEmpty();
                assertThat(params.size()).isEqualTo(7);
            } else assertThat(params.size()).isEqualTo(6);
        } catch (Throwable failure) { requestFailure.complete(failure); }
    }
    @Test void readsOwnedProgramsAndOpaquePeriodsAndJoinsMultipleTrainingPeriods() {
        assertThat(client.fetchAcademicPrograms(session)).hasSize(1);
        assertThat(client.fetchAcademicPeriods(session)).hasSize(2);
        var result = client.fetchAcademicRecords(session, program);
        assertThat(result.program().label()).isEqualTo("Chương trình giả định");
        assertThat(result.entries()).hasSize(2);
        assertThat(result.entries()).extracting(AcademicRecordObservation.Entry::semesterNumber).containsOnly(1);
        assertThat(result.entries()).extracting(AcademicRecordObservation.Entry::sourcePeriodId).doesNotHaveDuplicates();
        assertThat(paths).hasSize(5);
    }
    @Test void readsOnlyProgressSummaryFromExistingRecordsEndpoint() {
        records = """
                {"rsDiemTrungBinhChung":[
                  {"DAOTAO_THOIGIANDAOTAO_ID":null,"LOAIDIEMTRUNGBINH_MA":"TRUNGBINHTICHLUY",
                   "THUOCTINHLANTINH":0,"THANGDIEM_MA":"4","DIEMTRUNGBINH":3.25},
                  {"DAOTAO_THOIGIANDAOTAO_ID":null,"LOAIDIEMTRUNGBINH_MA":"TRUNGBINHTICHLUY",
                   "THUOCTINHLANTINH":0,"THANGDIEM_MA":"10","DIEMTRUNGBINH":8.10,"TONGSOTINCHI":72}]}
                """;
        var summary = client.fetchAcademicProgressSummary(session, program);
        assertThat(summary.cumulativeAverageScale4()).isEqualByComparingTo("3.25");
        assertThat(summary.sourceAccumulatedCredits()).isEqualByComparingTo("72");
        assertThat(paths).containsExactly(PhenikaaHttpTransport.ACADEMIC_PROGRAMS_PATH,
                PhenikaaHttpTransport.ACADEMIC_RECORDS_PATH);
        assertThat(paths).doesNotContain(PhenikaaHttpTransport.ACADEMIC_REGISTRATIONS_PATH);
    }
    @Test void summaryRejectsProgramNotInCurrentSourceList() {
        assertThatThrownBy(() -> client.fetchAcademicProgressSummary(session,
                new AcademicProgram("foreign-program", program.label()))).hasMessage("UNEXPECTED_SCHEMA");
        assertThat(paths).containsExactly(PhenikaaHttpTransport.ACADEMIC_PROGRAMS_PATH);
    }
    @Test void emptyResultDoesNotInventEntriesOrCompleteness() {
        records = AcademicSourceFixtures.records("", "");
        registrations = "{\"rsKetQuaDangKy\":[]}";
        var result = client.fetchAcademicRecords(session, program);
        assertThat(result.entries()).isEmpty();
        assertThat(result.completeness()).isEqualTo(AcademicRecordObservation.Completeness.UNKNOWN);
    }
    @Test void rejectsUnownedProgramBeforeRecordsRequest() {
        programs = "[]";
        failure(UNEXPECTED_SCHEMA);
        assertThat(paths).doesNotContain(PhenikaaHttpTransport.ACADEMIC_RECORDS_PATH);
    }
    @Test void rejectsDuplicateProgramsAndPeriodsAndWrongProgramOwner() {
        programs = "[" + AcademicSourceFixtures.PROGRAMS.strip().substring(1, AcademicSourceFixtures.PROGRAMS.strip().length() - 1)
                + "," + AcademicSourceFixtures.PROGRAMS.strip().substring(1, AcademicSourceFixtures.PROGRAMS.strip().length() - 1) + "]";
        failure(UNEXPECTED_SCHEMA);
        programs = AcademicSourceFixtures.PROGRAMS.replace("synthetic-learner", "other-learner");
        failure(UNEXPECTED_SCHEMA);
        periods = AcademicSourceFixtures.PERIODS.replace("synthetic-period-2", "synthetic-period-1");
        assertThatThrownBy(() -> client.fetchAcademicPeriods(session)).hasMessage("UNEXPECTED_SCHEMA");
    }
    @ParameterizedTest @ValueSource(strings = {"[]", "{}", "null", "{\"rsDiemThanhPhan\":[]}"})
    void rejectsIncompleteRecordSchema(String data) { records = data; failure(UNEXPECTED_SCHEMA); }
    @Test void rejectsOneMalformedRowAndMissingRegistrationIdentity() {
        records = AcademicSourceFixtures.records().replace("\"LANHOC\":2.0", "\"LANHOC\":null");
        failure(UNEXPECTED_SCHEMA);
        records = AcademicSourceFixtures.records();
        registrations = AcademicSourceFixtures.registrations().replace("\"ID\":\"synthetic-registration-2\"", "\"ID\":null");
        failure(UNEXPECTED_SCHEMA);
    }
    @Test void rejectsBusinessFailure() {
        override = e -> reply(e, 200, "{\"Success\":false,\"Message\":\"synthetic-private-detail\"}");
        failure(BUSINESS_FAILURE);
    }
    @Test void rejectsMalformedEnvelope() { override = e -> reply(e, 200, "{}"); failure(UNEXPECTED_SCHEMA); }
    @Test void rejectsDecodeFailure() {
        override = e -> reply(e, 200, "{\"Success\":true,\"Data\":{\"B\":\"invalid!\"}}");
        failure(DECODE_ERROR);
    }
    @Test void recognizesSessionExpiry() { override = e -> reply(e, 401, "{}"); failure(SESSION_EXPIRED); }
    @Test void doesNotFollowLoginRedirect() {
        override = e -> { e.getResponseHeaders().add("Location", "/conggiangvien/login.aspx"); reply(e, 302, "{}"); };
        failure(SESSION_EXPIRED);
        assertThat(paths).hasSize(2);
    }
    @Test void rejectsOversizedBody() { override = e -> reply(e, 200, "x".repeat(33000)); failure(RESPONSE_TOO_LARGE); }
    @Test void boundsWholeResponseTime() {
        override = e -> {
            e.getResponseHeaders().add("Content-Type", "application/json"); e.sendResponseHeaders(200, 0);
            e.getResponseBody().write('{'); e.getResponseBody().flush();
            try { Thread.sleep(1800); } catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
        };
        failure(TIMEOUT);
    }
    @Test void boundsListCountsBeforeMapping() {
        periods = "[" + String.join(",", Collections.nCopies(257, "{}")) + "]";
        assertThatThrownBy(() -> client.fetchAcademicPeriods(session)).hasMessage("RESPONSE_TOO_LARGE");
    }
    private void failure(PhenikaaClientException.Code code) {
        assertThatThrownBy(() -> client.fetchAcademicRecords(session, program)).isInstanceOf(PhenikaaClientException.class)
                .hasMessage(code.name()).hasNoCause();
    }
    @Test void readsResultDetailOnlyAfterRevalidatingProgramRecordsAndRegistrations() {
        var result = client.fetchAcademicResultDetail(session, program, "synthetic-final-1");
        assertThat(result.components()).containsExactly(new AcademicResultDetail.ComponentLink(
                "synthetic-registration-1", "synthetic-component-1", "TEST_COMPONENT", "Điểm thành phần giả định", 2,
                new java.math.BigDecimal("6.25")));
        assertThat(paths).containsExactly(PhenikaaHttpTransport.ACADEMIC_PROGRAMS_PATH, PhenikaaHttpTransport.ACADEMIC_RECORDS_PATH,
                PhenikaaHttpTransport.ACADEMIC_REGISTRATIONS_PATH, PhenikaaHttpTransport.ACADEMIC_RESULT_DETAIL_PATH);
    }
    @Test void rejectsUnownedResultBeforeDetailRequest() {
        assertThatThrownBy(() -> client.fetchAcademicResultDetail(session, program, "other-result")).hasMessage("UNEXPECTED_SCHEMA");
        assertThat(paths).doesNotContain(PhenikaaHttpTransport.ACADEMIC_RESULT_DETAIL_PATH);
    }
    @Test void emptyDetailDoesNotInventAnyLinks() {
        detail = "[]";
        assertThat(client.fetchAcademicResultDetail(session, program, "synthetic-final-1").components()).isEmpty();
    }
    @ParameterizedTest @ValueSource(strings = {"{}", "null", "[{}]"})
    void rejectsMalformedDetail(String input) { detail = input; detailFailure(UNEXPECTED_SCHEMA); }
    @Test void rejectsDuplicateDetailComponents() {
        detail = "[" + AcademicSourceFixtures.component(1) + "," + AcademicSourceFixtures.component(1) + "]";
        detailFailure(UNEXPECTED_SCHEMA);
    }
    @Test void detailBusinessFailureDoesNotExposeSourceMessage() {
        detailOverride = e -> reply(e, 200, "{\"Success\":false,\"Message\":\"synthetic-private-detail\"}"); detailFailure(BUSINESS_FAILURE);
    }
    @Test void detailUnauthorizedRequiresReconnect() { detailOverride = e -> reply(e, 401, "{}"); detailFailure(SESSION_EXPIRED); }
    @Test void detailRedirectIsNotFollowed() {
        detailOverride = e -> { e.getResponseHeaders().add("Location", "/conggiangvien/login.aspx"); reply(e, 302, "{}"); };
        detailFailure(SESSION_EXPIRED); assertThat(paths).hasSize(4);
    }
    @Test void detailDecodeFailureIsSafe() {
        detailOverride = e -> reply(e, 200, "{\"Success\":true,\"Data\":{\"B\":\"invalid!\"}}"); detailFailure(DECODE_ERROR);
    }
    @Test void detailResponseHasByteLimit() { detailOverride = e -> reply(e, 200, "x".repeat(33000)); detailFailure(RESPONSE_TOO_LARGE); }
    @Test void detailResponseHasTimeLimit() {
        detailOverride = e -> {
            e.getResponseHeaders().add("Content-Type", "application/json"); e.sendResponseHeaders(200, 0);
            e.getResponseBody().write('{'); e.getResponseBody().flush();
            try { Thread.sleep(1800); } catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
        };
        detailFailure(TIMEOUT);
    }
    private void detailFailure(PhenikaaClientException.Code code) {
        assertThatThrownBy(() -> client.fetchAcademicResultDetail(session, program, "synthetic-final-1"))
                .isInstanceOf(PhenikaaClientException.class).hasMessage(code.name()).hasNoCause();
    }
    private String envelope(String data) {
        return json.writeValueAsString(Map.of("Success", true, "Data", Map.of("B", codec.encodeRequest(data, "synthetic-key"))));
    }
    private static void reply(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length); exchange.getResponseBody().write(bytes);
    }
}
