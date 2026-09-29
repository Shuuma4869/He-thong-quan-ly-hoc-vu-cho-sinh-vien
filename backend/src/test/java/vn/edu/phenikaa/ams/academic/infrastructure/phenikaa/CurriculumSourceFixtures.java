package vn.edu.phenikaa.ams.academic.infrastructure.phenikaa;

import java.util.*;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import static vn.edu.phenikaa.ams.academic.infrastructure.phenikaa.PhenikaaHttpTransport.*;

final class CurriculumSourceFixtures {
    static final JsonMapper JSON = JsonMapper.builder().build();
    static final String OPTIONS = """
        [{"ID":"synthetic-association","QLSV_NGUOIHOC_ID":"synthetic-learner",
          "DAOTAO_TOCHUCCHUONGTRINH_ID":"synthetic-curriculum","DAOTAO_TOCHUCCHUONGTRINH_MA":"TEST-CT",
          "DAOTAO_TOCHUCCHUONGTRINH_TEN":"Chương trình giả định","DAOTAO_KHOADAOTAO_MA":"TEST-COHORT","TONGSOTINCHIQUYDINH":6.0}]
        """;
    static String course(int number) {
        return """
            {"ID":"synthetic-row-%1$d","DAOTAO_TOCHUCCHUONGTRINH_ID":"synthetic-curriculum",
             "DAOTAO_HOCPHAN_ID":"synthetic-course-%1$d","DAOTAO_HOCPHAN_MA":"TEST%1$d",
             "DAOTAO_HOCPHAN_TEN":"Môn giả định %1$d","HOCTRINHAPDUNGHOCTAP":3.0,"THONGTINQUANHEHOCPHAN":null}
            """.formatted(number);
    }
    static String group(boolean elective) {
        return """
            [{"ID":"synthetic-group-%1$s","KYHIEU":"TEST-%1$s","TEN":"Nhóm giả định",
              "DAOTAO_TOCHUCCHUONGTRINH_ID":"synthetic-curriculum","TONGSOTINCHI":3.0,"TONGSOHOCPHAN":1.0,
              "TONGSOTC":3.0,"TONGSOHP":1.0,"SOTINCHIQUYDINH":3.0,"SOHOCPHANQUYDINH":1.0}]
            """.formatted(elective ? "E" : "R");
    }
    static String member(boolean elective) {
        return "[" + course(elective ? 2 : 1).strip().replace("}", ",\"" + (elective ? "DAOTAO_KHOITUCHON_DON_ID" : "DAOTAO_KHOIBATBUOC_ID")
                + "\":\"synthetic-group-" + (elective ? "E" : "R") + "\",\"LAHOCPHANBATBUOC\":null}") + "]";
    }
    static final String RELATIONS = """
        [{"ID":"synthetic-relation","DAOTAO_TOCHUCCHUONGTRINH_ID":"synthetic-curriculum",
          "DAOTAO_HOCPHAN_ID":"synthetic-course-2","DAOTAO_HOCPHAN_QUANHE_ID":"synthetic-course-1",
          "LOAIQUANHE_TEN":"Tiên quyết giả định","MUCDIEUKIEN_TEN":"Điểm giả định","TOANTU_TEN":">=","GIATRIDIEUKIEN":"12"}]
        """;
    static Map<String, String> responses() {
        return new HashMap<>(Map.of(CURRICULA_PATH, OPTIONS, CURRICULUM_COURSES_PATH, "[" + course(1) + "," + course(2) + "," + course(3) + "]",
                REQUIRED_GROUPS_PATH, group(false), ELECTIVE_GROUPS_PATH, group(true), REQUIRED_MEMBERS_PATH, member(false),
                ELECTIVE_MEMBERS_PATH, member(true), COURSE_RELATIONS_PATH, RELATIONS));
    }
    static List<JsonNode> rows(String source) {
        var rows = new ArrayList<JsonNode>(); JSON.readTree(source).forEach(rows::add); return rows;
    }
}
