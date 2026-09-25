package vn.edu.phenikaa.ams.academic.infrastructure.phenikaa;

final class AcademicSourceFixtures {
    static final String PROGRAMS = """
            [{"QLSV_NGUOIHOC_ID":"synthetic-learner","DAOTAO_TOCHUCCHUONGTRINH_ID":"synthetic-program",
              "DAOTAO_CHUONGTRINH_TEN":"Chương trình giả định"}]
            """;
    static final String PERIODS = """
            [{"ID":"synthetic-period-1","THOIGIAN":"Đợt giả định A"},
             {"ID":"synthetic-period-2","THOIGIAN":"Đợt giả định B"}]
            """;
    static String registration(int attempt) {
        return """
                {"ID":"synthetic-registration-%1$d","QLSV_NGUOIHOC_ID":"synthetic-learner",
                 "DAOTAO_TOCHUCCHUONGTRINH_ID":"synthetic-program","DANGKY_LOPHOCPHAN_ID":"synthetic-section-%1$d",
                 "DAOTAO_HOCPHAN_ID":"synthetic-course","DAOTAO_HOCPHAN_MA":"TEST101",
                 "DAOTAO_HOCPHAN_TEN":"Môn kiểm thử giả định","DAOTAO_HOCPHAN_HOCTRINH":3.0,
                 "DAOTAO_THOIGIANDAOTAO_ID":"synthetic-period-%1$d"}
                """.formatted(attempt);
    }
    static String component(int attempt) {
        return """
                {"ID":"synthetic-component-%1$d","NAMHOC":2026.0,"HOCKY":1.0,"LANHOC":%1$d.0,"LANTHI":2.0,
                 "DIEM_DANHSACHHOC_ID":"synthetic-section-%1$d","DAOTAO_HOCPHAN_ID":"synthetic-course",
                 "DAOTAO_HOCPHAN_MA":"TEST101","DAOTAO_HOCPHAN_HOCTRINH":3.0,
                 "DAOTAO_THOIGIANDAOTAO_ID":"synthetic-period-%1$d","DIEM":6.25,
                 "DIEM_THANHPHANDIEM_ID":"synthetic-component-type","DIEM_THANHPHANDIEM_MA":"TEST_COMPONENT",
                 "DIEM_THANHPHANDIEM_TEN":"Điểm thành phần giả định"}
                """.formatted(attempt);
    }
    static String result(int attempt) {
        return """
                {"ID":"synthetic-final-%1$d","NAMHOC":"2026_2027","HOCKY":1.0,"LANHOC":%1$d.0,"LANTHI":2.0,
                 "DIEM_DANHSACHHOC_ID":"synthetic-section-%1$d","DAOTAO_HOCPHAN_MA":"TEST101",
                 "DAOTAO_HOCPHAN_TEN":"Môn kiểm thử giả định","DAOTAO_HOCPHAN_HOCTRINH":3.0,
                 "DAOTAO_THOIGIANDAOTAO_ID":"synthetic-period-%1$d","DIEM":7.25,"DIEMQUYDOI":2.5,
                 "DIEMQUYDOI_TEN":"TEST-B","DANHGIA_MA":"DAT","DANHGIA_TEN":"Đạt"}
                """.formatted(attempt);
    }
    static String records(String components, String results) {
        return "{\"rsThongTinNguoiHoc\":[{\"QLSV_NGUOIHOC_ID\":\"synthetic-learner\"}],"
                + "\"rsDiemThanhPhan\":[" + components + "],\"rsDiemKetThucHocPhan\":[" + results + "]}";
    }
    static String records() { return records(component(1) + "," + component(2), result(1) + "," + result(2)); }
    static String registrations() { return "{\"rsKetQuaDangKy\":[" + registration(1) + "," + registration(2) + "]}"; }
}
