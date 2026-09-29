package vn.edu.phenikaa.ams.academic.infrastructure.phenikaa;

import com.sun.net.httpserver.*;
import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.IntStream;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import vn.edu.phenikaa.ams.academic.application.port.*;
import static org.assertj.core.api.Assertions.*;
import static vn.edu.phenikaa.ams.academic.infrastructure.phenikaa.CurriculumSourceFixtures.*;
import static vn.edu.phenikaa.ams.academic.infrastructure.phenikaa.PhenikaaHttpTransport.*;

class PhenikaaCurriculumHttpTest {
    private final PhenikaaPayloadCodec codec = new PhenikaaPayloadCodec(524288);
    private final PhenikaaSession session = new PhenikaaSession("Bearer synthetic-secret", "", "synthetic-key", "synthetic-learner", "synthetic-curriculum-function");
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final Map<String, String> data = new ConcurrentHashMap<>(responses());
    private final List<String> paths = new CopyOnWriteArrayList<>();
    private final CompletableFuture<Throwable> requestError = new CompletableFuture<>();
    private final CurriculumOption option = PhenikaaCurriculumMapper.options(rows(OPTIONS), "synthetic-learner").getFirst();
    private HttpServer server;
    private PhenikaaHttpTransport transport;
    private PhenikaaHttpClient client;
    private volatile HttpHandler override;
    private volatile boolean duplicatePage, inconsistentTotal, emptySecondPage;

    @BeforeEach void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0); server.setExecutor(executor);
        server.createContext("/", exchange -> {
            try (exchange) {
                String path = exchange.getRequestURI().getPath(); paths.add(path);
                var params = JSON.readTree(codec.decodeResponse(URLDecoder.decode(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8).substring(2), StandardCharsets.UTF_8), path.substring(path.lastIndexOf('/') + 1)));
                try {
                    assertThat(exchange.getRequestMethod()).isEqualTo("POST");
                    assertThat(exchange.getRequestHeaders().getFirst("Authorization")).isEqualTo("Bearer synthetic-secret");
                    assertThat(params.path("strNguoiThucHien_Id").asString()).isEqualTo("synthetic-learner");
                    assertThat(params.path("strChucNang_Id").asString()).isEqualTo("synthetic-curriculum-function");
                    assertThat(params.path("action").asString()).isEqualTo(path.substring(path.indexOf("/api/") + 5));
                    String function = switch (path) {
                        case CURRICULA_PATH -> "pkg_dangkyhoc_chung.LayDSChuongTrinh";
                        case CURRICULUM_COURSES_PATH -> "pkg_kehoach_thongtin.LayDSKS_DaoTao_HocPhan_CT";
                        case REQUIRED_GROUPS_PATH -> "pkg_kehoach_thongtin.LayDSKS_DaoTao_KhoiBatBuoc";
                        case ELECTIVE_GROUPS_PATH -> "pkg_kehoach_thongtin.LayDSKS_DaoTao_KhoiTuChon_Don";
                        case REQUIRED_MEMBERS_PATH -> "pkg_kehoach_thongtin.LayDSKS_DaoTao_HP_KhoiBatBuoc";
                        case ELECTIVE_MEMBERS_PATH -> "pkg_kehoach_thongtin.LayDSKS_DaoTao_HP_KTuChon_Don";
                        case COURSE_RELATIONS_PATH -> "pkg_kehoach_thongtin.LayDSKS_DaoTao_QuanHeHocPhan";
                        default -> throw new AssertionError("Unexpected endpoint");
                    };
                    assertThat(params.path("func").asString()).isEqualTo(function);
                    assertThat(params.path("iM").asString()).isEqualTo("synthetic-key");
                    if (path.equals(CURRICULA_PATH)) assertThat(params.path("strQLSV_NguoiHoc_Id").asString()).isEqualTo("synthetic-learner");
                    else {
                        assertThat(params.path(path.equals(CURRICULUM_COURSES_PATH) ? "strDaoTao_ChuongTrinh_Id" : "strDaoTao_ToChucCT_Id").asString()).isEqualTo("synthetic-curriculum");
                        assertThat(params.path("pageSize").asInt()).isEqualTo(100);
                        assertThat(params.path("pageIndex").asInt()).isPositive();
                        assertThat(params.path("strTuKhoa").asString()).isEmpty();
                    }
                    if (path.equals(REQUIRED_MEMBERS_PATH)) assertThat(params.path("strDaoTao_KhoiBatBuoc_Id").asString()).isEqualTo("synthetic-group-R");
                    if (path.equals(ELECTIVE_MEMBERS_PATH)) assertThat(params.path("strDaoTao_KTuChon_Don_Id").asString()).isEqualTo("synthetic-group-E");
                    if (path.equals(COURSE_RELATIONS_PATH)) assertThat(params.path("strDaoTao_HocPhan_Id").asString()).isEqualTo("synthetic-course-2");
                } catch (Throwable ex) { requestError.complete(ex); }
                if (override != null && path.equals(CURRICULUM_COURSES_PATH)) { override.handle(exchange); return; }
                String source = data.get(path);
                var parsed = JSON.readTree(source);
                Object pager = null;
                if (!path.equals(CURRICULA_PATH) && parsed.isArray()) {
                    int index = params.path("pageIndex").asInt();
                    pager = Integer.toString(parsed.size() + (inconsistentTotal && index > 1 ? 1 : 0));
                    var all = rows(source); int start = (index - 1) * 100;
                    if (duplicatePage && index > 1) start = 0;
                    source = JSON.writeValueAsString(emptySecondPage && index > 1 ? List.of() : all.subList(Math.min(start, all.size()), Math.min(start + 100, all.size())));
                }
                var envelope = new LinkedHashMap<String, Object>(); envelope.put("Success", true);
                envelope.put("Data", Map.of("B", codec.encodeRequest(source, "synthetic-key"))); envelope.put("Pager", pager);
                reply(exchange, 200, JSON.writeValueAsString(envelope));
            }
        });
        server.start();
        transport = new PhenikaaHttpTransport(URI.create("http://127.0.0.1:" + server.getAddress().getPort()), Duration.ofSeconds(1), Duration.ofMillis(900), 524288);
        client = new PhenikaaHttpClient(transport, codec);
    }
    @AfterEach void close() { transport.close(); session.close(); server.stop(0); executor.shutdownNow(); assertThat(requestError.getNow(null)).isNull(); }

    @Test void readsSelectorCatalogGroupsAndDescriptiveRelationsWithoutLoggingData() {
        assertThat(client.fetchCurricula(session)).hasSize(1);
        var result = client.fetchCurriculum(session, option);
        assertThat(result.courses()).hasSize(3); assertThat(result.groups()).hasSize(2);
        assertThat(result.completeness()).isEqualTo(CurriculumObservation.Completeness.UNKNOWN);
        assertThat(client.fetchCourseRelations(session, option, "synthetic-course-2").conditions()).hasSize(1);
    }
    @Test void emptyUnknownCurriculumRemainsEmptyWithoutInventedRows() {
        for (String path : List.of(CURRICULUM_COURSES_PATH, REQUIRED_GROUPS_PATH, ELECTIVE_GROUPS_PATH)) data.put(path, "[]");
        assertThat(client.fetchCurriculum(session, option).courses()).isEmpty();
        assertThat(client.fetchCurriculum(session, option).groups()).isEmpty();
    }
    @Test void rejectsUnownedCurriculumBeforeCatalogRequest() {
        data.put(CURRICULA_PATH, "[]"); failure("UNEXPECTED_SCHEMA"); assertThat(paths).containsExactly(CURRICULA_PATH);
    }
    @Test void doesNotReadRelationsForCourseOutsideOwnedCurriculum() {
        assertThatThrownBy(() -> client.fetchCourseRelations(session, option, "other-course")).hasMessage("UNEXPECTED_SCHEMA");
        assertThat(paths).doesNotContain(COURSE_RELATIONS_PATH);
    }
    @Test void boundedPaginationCollectsPagesButDoesNotPromiseGlobalCompleteness() {
        manyCourses(); assertThat(client.fetchCurriculum(session, option).courses()).hasSize(101);
        assertThat(paths.stream().filter(CURRICULUM_COURSES_PATH::equals)).hasSize(2);
    }
    @Test void rejectsDuplicatePage() { manyCourses(); duplicatePage = true; failure("UNEXPECTED_SCHEMA"); }
    @Test void rejectsChangingTotal() { manyCourses(); inconsistentTotal = true; failure("UNEXPECTED_SCHEMA"); }
    @Test void rejectsPrematureEmptyPage() { manyCourses(); emptySecondPage = true; failure("UNEXPECTED_SCHEMA"); }
    @Test void rejectsTotalsOverLimitBeforeContinuing() {
        override = e -> reply(e, 200, "{\"Success\":true,\"Data\":{\"B\":\"" + codec.encodeRequest("[]", "synthetic-key") + "\"},\"Pager\":\"2001\"}"); failure("RESPONSE_TOO_LARGE");
    }
    @ParameterizedTest @ValueSource(strings = {"{}", "null", "[null]"})
    void rejectsMalformedSourceSchema(String source) { data.put(CURRICULUM_COURSES_PATH, source); failure("UNEXPECTED_SCHEMA"); }
    @Test void rejectsBusinessFailureWithoutSourceMessage() {
        override = e -> reply(e, 200, "{\"Success\":false,\"Message\":\"synthetic-private-detail\"}"); failure("BUSINESS_FAILURE");
    }
    @Test void rejectsMalformedEnvelope() { override = e -> reply(e, 200, "{}"); failure("UNEXPECTED_SCHEMA"); }
    @Test void rejectsDecodeFailure() {
        override = e -> reply(e, 200, "{\"Success\":true,\"Data\":{\"B\":\"invalid!\"}}"); failure("DECODE_ERROR");
    }
    @Test void detectsUnauthorized() { override = e -> reply(e, 401, "{}"); failure("SESSION_EXPIRED"); }
    @Test void doesNotForwardCredentialsOnRedirect() {
        override = e -> { e.getResponseHeaders().add("Location", "/conggiangvien/login.aspx"); reply(e, 302, "{}"); };
        failure("SESSION_EXPIRED"); assertThat(paths).hasSize(2);
    }
    @Test void boundsResponseBytes() { override = e -> reply(e, 200, "x".repeat(524289)); failure("RESPONSE_TOO_LARGE"); }
    @Test void boundsResponseTime() {
        override = e -> { e.getResponseHeaders().add("Content-Type", "application/json"); e.sendResponseHeaders(200, 0); e.getResponseBody().write('{'); e.getResponseBody().flush();
            try { Thread.sleep(1800); } catch (InterruptedException ex) { Thread.currentThread().interrupt(); } };
        failure("TIMEOUT");
    }
    @Test void rejectsUnknownRelationSchema() {
        data.put(COURSE_RELATIONS_PATH, "[{}]");
        assertThatThrownBy(() -> client.fetchCourseRelations(session, option, "synthetic-course-2")).hasMessage("UNEXPECTED_SCHEMA");
    }
    private void manyCourses() { data.put(CURRICULUM_COURSES_PATH, "[" + String.join(",", IntStream.rangeClosed(1, 101).mapToObj(CurriculumSourceFixtures::course).toList()) + "]"); }
    private void failure(String code) { assertThatThrownBy(() -> client.fetchCurriculum(session, option)).isInstanceOf(PhenikaaClientException.class).hasMessage(code).hasNoCause(); }
    private static void reply(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8); exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length); exchange.getResponseBody().write(bytes);
    }
}
