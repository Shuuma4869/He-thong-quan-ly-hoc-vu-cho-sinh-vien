package vn.edu.phenikaa.ams.academic.infrastructure.phenikaa;

import java.util.*;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import vn.edu.phenikaa.ams.academic.application.port.*;
import static vn.edu.phenikaa.ams.academic.infrastructure.phenikaa.PhenikaaHttpTransport.*;
import static vn.edu.phenikaa.ams.academic.infrastructure.phenikaa.PhenikaaClientException.Code.*;

final class PhenikaaCurriculumReader {
    private static final int PAGE_SIZE = 100, MAX_PAGES = 20, MAX_REQUESTS = 128;
    private final PhenikaaHttpTransport transport;
    private final PhenikaaPayloadCodec codec;
    private final JsonMapper json;
    private int requests;

    PhenikaaCurriculumReader(PhenikaaHttpTransport transport, PhenikaaPayloadCodec codec, JsonMapper json) {
        this.transport = transport; this.codec = codec; this.json = json;
    }

    List<CurriculumOption> options(PhenikaaSession session) {
        var parameters = parameters(session, CURRICULA_PATH, "pkg_dangkyhoc_chung.LayDSChuongTrinh");
        parameters.put("strQLSV_NguoiHoc_Id", session.learnerId());
        try {
            var page = request(session, CURRICULA_PATH, parameters);
            return PhenikaaCurriculumMapper.options(page.rows(), session.learnerId());
        } finally { parameters.clear(); }
    }

    private CurriculumOption owned(PhenikaaSession session, CurriculumOption requested) {
        Objects.requireNonNull(requested);
        return options(session).stream().filter(c -> c.sourceId().equals(requested.sourceId())).findFirst()
                .orElseThrow(() -> new PhenikaaClientException(UNEXPECTED_SCHEMA));
    }

    CurriculumObservation read(PhenikaaSession session, CurriculumOption requested) {
        var option = owned(session, requested);
        var courses = catalog(session, option);
        var groups = new ArrayList<PhenikaaCurriculumMapper.GroupData>();
        for (boolean elective : new boolean[]{false, true}) {
            String path = elective ? ELECTIVE_GROUPS_PATH : REQUIRED_GROUPS_PATH;
            var params = parameters(session, path, "pkg_kehoach_thongtin.LayDSKS_DaoTao_" + (elective ? "KhoiTuChon_Don" : "KhoiBatBuoc"));
            params.put("strTuKhoa", "");
            params.put(elective ? "strDaoTao_KTuChon_Don_Cha_Id" : "strDaoTao_KhoiBatBuoc_Cha_Id", "");
            params.put("strDaoTao_ToChucCT_Id", option.sourceId());
            if (elective) params.put("strLoaiLuaChon_Id", "");
            for (var group : pages(session, path, params, 64)) {
                if (groups.size() >= 64) throw new PhenikaaClientException(RESPONSE_TOO_LARGE);
                PhenikaaCurriculumMapper.requireProgram(group, option);
                String id = PhenikaaAcademicRecords.text(group, "ID", 128);
                String memberPath = elective ? ELECTIVE_MEMBERS_PATH : REQUIRED_MEMBERS_PATH;
                var members = parameters(session, memberPath, "pkg_kehoach_thongtin.LayDSKS_DaoTao_HP_" + (elective ? "KTuChon_Don" : "KhoiBatBuoc"));
                members.put("strTuKhoa", ""); members.put("strDaoTao_HocPhan_Id", "");
                members.put("strDaoTao_ToChucCT_Id", option.sourceId());
                members.put(elective ? "strDaoTao_KTuChon_Don_Id" : "strDaoTao_KhoiBatBuoc_Id", id);
                groups.add(new PhenikaaCurriculumMapper.GroupData(group, elective, pages(session, memberPath, members, 2000)));
            }
        }
        return PhenikaaCurriculumMapper.normalize(option, courses, groups);
    }

    CourseRelationObservation relations(PhenikaaSession session, CurriculumOption requested, String courseId) {
        var option = owned(session, requested);
        var catalog = PhenikaaCurriculumMapper.courses(option, catalog(session, option));
        if (catalog.stream().noneMatch(c -> c.sourceId().equals(courseId))) throw new PhenikaaClientException(UNEXPECTED_SCHEMA);
        var parameters = parameters(session, COURSE_RELATIONS_PATH, "pkg_kehoach_thongtin.LayDSKS_DaoTao_QuanHeHocPhan");
        parameters.put("strTuKhoa", ""); parameters.put("strLoaiQuanHe_Id", "");
        parameters.put("strDaoTao_ToChucCT_Id", option.sourceId());
        parameters.put("strDaoTao_HocPhan_QuanHe_Id", ""); parameters.put("strDaoTao_HocPhan_Id", courseId);
        return PhenikaaCurriculumMapper.relations(option, courseId, pages(session, COURSE_RELATIONS_PATH, parameters, 256));
    }

    private List<JsonNode> catalog(PhenikaaSession session, CurriculumOption option) {
        var params = parameters(session, CURRICULUM_COURSES_PATH, "pkg_kehoach_thongtin.LayDSKS_DaoTao_HocPhan_CT");
        for (String field : List.of("strTuKhoa", "strDaoTao_ThoiGian_KH_Id", "strDaoTao_ThoiGian_TT_Id",
                "strThuocTinhHocPhan_Id", "strPhanCongPhamViDamNhiem_Id", "strDaoTao_HocPhan_Id")) params.put(field, "");
        params.put("strDaoTao_ChuongTrinh_Id", option.sourceId());
        return pages(session, CURRICULUM_COURSES_PATH, params, 2000);
    }

    private List<JsonNode> pages(PhenikaaSession session, String path, LinkedHashMap<String, Object> parameters, int limit) {
        var rows = new ArrayList<JsonNode>();
        var ids = new HashSet<String>();
        Integer total = null;
        try {
            parameters.put("pageSize", PAGE_SIZE);
            for (int index = 1; index <= MAX_PAGES; index++) {
                parameters.put("pageIndex", index);
                var page = request(session, path, parameters);
                if (page.total() == null || page.total() > limit || page.rows().size() > PAGE_SIZE)
                    throw new PhenikaaClientException(page.total() != null && page.total() > limit ? RESPONSE_TOO_LARGE : UNEXPECTED_SCHEMA);
                if (total != null && !total.equals(page.total())) throw new PhenikaaClientException(UNEXPECTED_SCHEMA);
                total = page.total();
                for (var row : page.rows()) {
                    if (!ids.add(PhenikaaAcademicRecords.text(row, "ID", 128))) throw new PhenikaaClientException(UNEXPECTED_SCHEMA);
                    rows.add(row);
                }
                if (rows.size() > total) throw new PhenikaaClientException(UNEXPECTED_SCHEMA);
                if (rows.size() == total) return List.copyOf(rows);
                if (page.rows().isEmpty()) throw new PhenikaaClientException(UNEXPECTED_SCHEMA);
            }
            throw new PhenikaaClientException(RESPONSE_TOO_LARGE);
        } finally { parameters.clear(); }
    }

    private LinkedHashMap<String, Object> parameters(PhenikaaSession session, String path, String function) {
        var params = new LinkedHashMap<String, Object>();
        params.put("action", path.substring(path.indexOf("/api/") + 5)); params.put("func", function);
        params.put("iM", session.responseKey()); params.put("strNguoiThucHien_Id", session.learnerId());
        params.put("strChucNang_Id", session.functionId());
        return params;
    }

    private Page request(PhenikaaSession session, String path, Map<String, Object> parameters) {
        if (++requests > MAX_REQUESTS) throw new PhenikaaClientException(RESPONSE_TOO_LARGE);
        byte[] response = null;
        try {
            String encoded = codec.encodeRequest(json.writeValueAsString(parameters), path.substring(path.lastIndexOf('/') + 1));
            response = transport.readCurriculum(session, encoded, path);
            var root = json.readTree(response);
            var envelope = PhenikaaEnvelope.from(root);
            if (!envelope.success()) throw new PhenikaaClientException(BUSINESS_FAILURE);
            JsonNode data;
            try { data = json.readTree(codec.decodeResponse(envelope.encodedData(), session.responseKey())); }
            catch (JacksonException ex) { throw new PhenikaaClientException(DECODE_ERROR); }
            var array = PhenikaaAcademicRecords.boundedArray(data, 2000);
            var rows = new ArrayList<JsonNode>();
            array.forEach(rows::add);
            Integer total = null;
            var pager = root.path("Pager");
            if (!pager.isNull() && !pager.isMissingNode()) {
                if (!pager.isString() || !pager.stringValue().matches("[0-9]{1,8}")) throw new PhenikaaClientException(UNEXPECTED_SCHEMA);
                total = Integer.parseInt(pager.stringValue());
            }
            return new Page(rows, total);
        } catch (JacksonException ex) { throw new PhenikaaClientException(UNEXPECTED_SCHEMA); }
        finally { if (response != null) Arrays.fill(response, (byte) 0); }
    }

    private record Page(List<JsonNode> rows, Integer total) {
        @Override public String toString() { return "CurriculumSourcePage[redacted]"; }
    }
}
