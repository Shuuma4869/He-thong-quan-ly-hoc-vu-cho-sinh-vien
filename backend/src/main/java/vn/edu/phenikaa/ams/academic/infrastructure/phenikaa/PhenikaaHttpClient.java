package vn.edu.phenikaa.ams.academic.infrastructure.phenikaa;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Objects;
import tools.jackson.core.JacksonException;
import tools.jackson.core.StreamReadConstraints;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import vn.edu.phenikaa.ams.academic.application.port.ProfileObservation;
import vn.edu.phenikaa.ams.academic.application.port.ScheduleObservation;
import static vn.edu.phenikaa.ams.academic.infrastructure.phenikaa.PhenikaaClientException.Code.*;

public final class PhenikaaHttpClient {

    private static final String ACTION = "SV_ThongTin_MH/DSA4BRINKCIpAiAPKSAv";
    private static final String PROFILE_ACTION = "SV_Custom/DSA4FSkuLyYVKC8CKSgVKCQ1CS4SLgPP";
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
}
