package vn.edu.phenikaa.ams.academic.infrastructure.phenikaa;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Objects;
import java.util.List;
import java.util.HashSet;
import java.util.function.BiFunction;
import tools.jackson.core.JacksonException;
import tools.jackson.core.StreamReadConstraints;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import vn.edu.phenikaa.ams.academic.application.port.ProfileObservation;
import vn.edu.phenikaa.ams.academic.application.port.ScheduleObservation;
import vn.edu.phenikaa.ams.academic.application.port.ExamObservation;
import vn.edu.phenikaa.ams.academic.application.port.ExamPeriod;
import static vn.edu.phenikaa.ams.academic.infrastructure.phenikaa.PhenikaaClientException.Code.*;

public final class PhenikaaHttpClient {

    private static final String ACTION = "SV_ThongTin_MH/DSA4BRINKCIpAiAPKSAv";
    private static final String PROFILE_ACTION = "SV_Custom/DSA4FSkuLyYVKC8CKSgVKCQ1CS4SLgPP";
    private static final String EXAM_PERIODS_ACTION = "SV_ThongTin_MH/DSA4BRIVKS4oBiggLw0oIikVKSgP";
    private static final String EXAMS_ACTION = "SV_ThongTin_MH/DSA4BRINKCIpFSkoHgokCS4gIikVKSgP";
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/uuuu", Locale.ROOT);
    private static final ZoneId PORTAL_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private final PhenikaaHttpTransport transport;
    private final PhenikaaPayloadCodec codec;
    private final JsonMapper json = JsonMapper.builder(JsonFactory.builder()
            .streamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(32).maxStringLength(4 * 1024 * 1024).build())
            .disable(StreamReadFeature.INCLUDE_SOURCE_IN_LOCATION).enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build())
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS, DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS).build();

    public PhenikaaHttpClient(PhenikaaHttpTransport transport, PhenikaaPayloadCodec codec) {
        this.transport = Objects.requireNonNull(transport);
        this.codec = Objects.requireNonNull(codec);
    }

    public ScheduleObservation fetchSchedule(PhenikaaSession session, LocalDate from, LocalDate through) {
        Objects.requireNonNull(session);
        Objects.requireNonNull(from);
        Objects.requireNonNull(through);
        if (through.isBefore(from) || through.isAfter(from.plusDays(30)))
            throw new IllegalArgumentException("Schedule range must contain 1 to 31 days");
        var parameters = new LinkedHashMap<String, Object>();
        parameters.put("action", ACTION);
        parameters.put("func", "pkg_congthongtin_hssv_thongtin.LayDSLichCaNhan");
        parameters.put("iM", session.responseKey());
        parameters.put("strQLSV_NguoiHoc_Id", session.learnerId());
        parameters.put("strNgayBatDau", from.format(DATE));
        parameters.put("strNgayKetThuc", through.format(DATE));
        parameters.put("strChucNang_Id", session.functionId());
        parameters.put("strNguoiThucHien_Id", session.learnerId());
        byte[] response = null;
        try {
            String encoded = codec.encodeRequest(json.writeValueAsString(parameters), ACTION.substring(ACTION.indexOf('/') + 1));
            response = transport.readSchedule(session, encoded);
            var envelope = PhenikaaEnvelope.from(json.readTree(response));
            if (!envelope.success()) throw new PhenikaaClientException(BUSINESS_FAILURE);
            JsonNode data;
            try {
                data = json.readTree(codec.decodeResponse(envelope.encodedData(), session.responseKey()));
            } catch (JacksonException ex) {
                throw new PhenikaaClientException(DECODE_ERROR);
            }
            if (data == null || !data.isArray()) throw new PhenikaaClientException(UNEXPECTED_SCHEMA);
            if (data.size() > 10000) throw new PhenikaaClientException(RESPONSE_TOO_LARGE);
            var entries = new ArrayList<ScheduleObservation.Entry>();
            for (var item : data) {
                var entry = PhenikaaScheduleItem.from(item).normalize();
                if (entry.date().isBefore(from) || entry.date().isAfter(through)) throw new PhenikaaClientException(UNEXPECTED_SCHEMA);
                entries.add(entry);
            }
            return new ScheduleObservation(from, through, PORTAL_ZONE, entries);
        } catch (JacksonException ex) {
            throw new PhenikaaClientException(UNEXPECTED_SCHEMA);
        } finally {
            parameters.clear();
            if (response != null) Arrays.fill(response, (byte) 0);
        }
    }

    public ProfileObservation fetchProfile(PhenikaaSession session) {
        Objects.requireNonNull(session);
        var parameters = new LinkedHashMap<String, Object>();
        parameters.put("action", PROFILE_ACTION);
        parameters.put("func", "pkg_hosohocvien.LayThongTinChiTietHoSo");
        parameters.put("iM", session.responseKey());
        parameters.put("strId", session.learnerId());
        parameters.put("strChucNang_Id", session.functionId());
        parameters.put("strNguoiThucHien_Id", session.learnerId());
        byte[] response = null;
        try {
            String encoded = codec.encodeRequest(json.writeValueAsString(parameters),
                    PROFILE_ACTION.substring(PROFILE_ACTION.indexOf('/') + 1));
            response = transport.readProfile(session, encoded);
            var envelope = PhenikaaEnvelope.from(json.readTree(response));
            if (!envelope.success()) throw new PhenikaaClientException(BUSINESS_FAILURE);
            JsonNode data;
            try { data = json.readTree(codec.decodeResponse(envelope.encodedData(), session.responseKey())); }
            catch (JacksonException ex) { throw new PhenikaaClientException(DECODE_ERROR); }
            if (data == null || !data.isArray() || data.size() != 1)
                throw new PhenikaaClientException(UNEXPECTED_SCHEMA);
            return PhenikaaProfileItem.from(data.get(0), session.learnerId()).normalize();
        } catch (JacksonException ex) {
            throw new PhenikaaClientException(UNEXPECTED_SCHEMA);
        } finally {
            parameters.clear();
            if (response != null) Arrays.fill(response, (byte) 0);
        }
    }

    public List<ExamPeriod> fetchExamPeriods(PhenikaaSession session) {
        var parameters = examParameters(session, EXAM_PERIODS_ACTION, "LayDSThoiGianLichThi");
        var data = readExamData(session, parameters, transport::readExamPeriods);
        if (!data.isArray()) throw new PhenikaaClientException(UNEXPECTED_SCHEMA);
        if (data.size() > 256) throw new PhenikaaClientException(RESPONSE_TOO_LARGE);
        var periods = new ArrayList<ExamPeriod>();
        var ids = new HashSet<String>();
        for (var item : data) {
            if (!item.isObject()) throw new PhenikaaClientException(UNEXPECTED_SCHEMA);
            try {
                var period = new ExamPeriod(PhenikaaExamItem.text(item, "ID", true),
                        PhenikaaExamItem.text(item, "THOIGIAN", true));
                if (!ids.add(period.sourceId())) throw new PhenikaaClientException(UNEXPECTED_SCHEMA);
                periods.add(period);
            } catch (IllegalArgumentException ex) { throw new PhenikaaClientException(UNEXPECTED_SCHEMA); }
        }
        return List.copyOf(periods);
    }

    public ExamObservation fetchExams(PhenikaaSession session, ExamPeriod requestedPeriod) {
        Objects.requireNonNull(requestedPeriod);
        // Revalidate the filter against this account instead of trusting a caller-supplied source ID or label.
        var period = fetchExamPeriods(session).stream()
                .filter(candidate -> candidate.sourceId().equals(requestedPeriod.sourceId())).findFirst()
                .orElseThrow(() -> new PhenikaaClientException(UNEXPECTED_SCHEMA));
        var parameters = examParameters(session, EXAMS_ACTION, "LayDSLichThi_KeHoachThi");
        parameters.put("strQLSV_NguoiHoc_Id", session.learnerId());
        parameters.put("strDaoTao_ThoiGianDaoTao_Id", period.sourceId());
        parameters.put("strDaoTao_HocPhan_Id", "");
        var data = readExamData(session, parameters, transport::readExams);
        if (!data.isObject()) throw new PhenikaaClientException(UNEXPECTED_SCHEMA);
        var personal = data.path("rsLichThiCaNhan");
        var common = data.path("rsKeHoachThiChung");
        if (!personal.isArray() || !common.isArray()) throw new PhenikaaClientException(UNEXPECTED_SCHEMA);
        if (personal.size() > 10000 || common.size() > 10000) throw new PhenikaaClientException(RESPONSE_TOO_LARGE);
        // Only an empty common-plan table has been observed. Do not silently discard an unknown nonempty schema.
        if (!common.isEmpty()) throw new PhenikaaClientException(UNEXPECTED_SCHEMA);
        var entries = new ArrayList<ExamObservation.Entry>();
        for (var item : personal) entries.add(PhenikaaExamItem.from(item, session.learnerId()).normalize());
        return new ExamObservation(period, PORTAL_ZONE, entries);
    }

    private LinkedHashMap<String, Object> examParameters(PhenikaaSession session, String action, String function) {
        Objects.requireNonNull(session);
        var parameters = new LinkedHashMap<String, Object>();
        parameters.put("action", action);
        parameters.put("func", "pkg_congthongtin_hssv_thongtin." + function);
        parameters.put("iM", session.responseKey());
        parameters.put("strNguoiThucHien_Id", session.learnerId());
        parameters.put("strChucNang_Id", session.functionId());
        return parameters;
    }

    private JsonNode readExamData(PhenikaaSession session, LinkedHashMap<String, Object> parameters,
                                 BiFunction<PhenikaaSession, String, byte[]> read) {
        byte[] response = null;
        try {
            String action = (String) parameters.get("action");
            String encoded = codec.encodeRequest(json.writeValueAsString(parameters), action.substring(action.indexOf('/') + 1));
            response = read.apply(session, encoded);
            var envelope = PhenikaaEnvelope.from(json.readTree(response));
            if (!envelope.success()) throw new PhenikaaClientException(BUSINESS_FAILURE);
            try {
                var data = json.readTree(codec.decodeResponse(envelope.encodedData(), session.responseKey()));
                if (data == null || data.isNull()) throw new PhenikaaClientException(UNEXPECTED_SCHEMA);
                return data;
            } catch (JacksonException ex) { throw new PhenikaaClientException(DECODE_ERROR); }
        } catch (JacksonException ex) { throw new PhenikaaClientException(UNEXPECTED_SCHEMA); }
        finally {
            parameters.clear();
            if (response != null) Arrays.fill(response, (byte) 0);
        }
    }
}
