# Nghiên cứu kết nối Phenikaa QLĐT

## Phạm vi và kết luận Phase 3

Đợt nghiên cứu ngày 18–20/09/2026 đã xác định request thực tế cho hồ sơ, lịch học, lịch thi, điểm và chương trình đào tạo; kiểm tra phiên qua reload/chuyển màn hình, so sánh candidate ID và quan sát push/polling. Đây là kết quả research để review, **chưa phải connector hoạt động**. UNKNOWN là chưa kiểm chứng, không có nghĩa dịch vụ không hỗ trợ.

Baseline là Phase 2 tại `50fb103`, đã có trên main. `AMS-Solution` được fast-forward tới baseline này; không sửa lịch sử hoặc merge vào main. Phase 3 chỉ thêm tài liệu và liên kết README, không thay code, contract, migration hoặc workflow.

Phân biệt bằng chứng:

- **UI**: giao diện/hành vi đã quan sát.
- **STATIC**: mã frontend được trang tham chiếu; không đồng nghĩa request đã chạy.
- **NETWORK**: request/response thực tế do browser tạo qua UI hợp lệ.
- **RUNTIME**: cấu trúc trong memory sau khi portal xử lý response, không phải JSON nguyên bản trên đường truyền.

Lượt đầu dùng in-app browser xem UI và mã liên quan. Lượt Network dùng Chrome có sẵn, profile tạm ngoài repo, Playwright qua CDP loopback. Người dùng tự login; observer chỉ bật sau login. Không attach profile cá nhân, sao chép phiên từ browser khác, đọc mật khẩu, vượt MFA/CAPTCHA, dò endpoint hoặc truy cập người học khác. Chromium đóng gói gặp lỗi side-by-side nên dùng Chrome có sẵn.

Observer redact trong memory trước output: chỉ tên query/header/body field, schema/type, host/path và boolean. Không xuất token/hash token, cookie value, tên, mã sinh viên, điểm hoặc lịch cá nhân; không tạo HAR, storageState, screenshot, raw response hoặc storage dump. Script và profile tạm đã xóa sau khi đóng đúng Chrome nghiên cứu, không giữ bản sao trong project.

## Đăng nhập và vòng đời phiên

`GET https://qldtbeta.phenikaa-uni.edu.vn/` trả 200, HTML chuyển bằng JavaScript tới `/conggiangvien/login.aspx`, không phải HTTP 302. Login có form nội bộ và liên kết Microsoft. Markup có dấu hiệu ASP.NET Web Forms (`__VIEWSTATE`, `__EVENTVALIDATION`), nhưng endpoint xử lý local login vẫn UNKNOWN; không suy từ form bị chặn submit mặc định.

Liên kết Microsoft công khai trỏ tới `login.microsoftonline.com/.../oauth2/v2.0/authorize`, với `response_type=code`, `response_mode=form_post`, scope `openid profile` và redirect về login portal. Đây là cấu hình authorization code/OIDC; chưa capture callback/code exchange. Cookie có domain Microsoft và `sso.phenikaa-uni.edu.vn/adfs`, phù hợp SSO liên kết, chưa tái dựng toàn bộ redirect chain. Không thấy `offline_access` trong liên kết đã đọc không chứng minh server không có refresh. Không dùng client ID của trường để xin thêm scope hoặc thay redirect URI.

Sau login, UI cho phép vào Cổng sinh viên tại `conggiangvien/index.aspx` rồi đổi module bằng fragment. Tên thư mục không chứng minh quyền giảng viên.

| Kiểm tra | Kết quả và giới hạn |
| --- | --- |
| Auth trên năm nhóm API chính | NETWORK: đều có `Authorization: Bearer <redacted>` và `Cookie: present`, cùng host QLĐT |
| Bắt buộc cả Bearer và cookie? | UNKNOWN; browser gửi cả hai nhưng không thử bỏ credential hoặc replay API |
| Timetable → reload → timetable | HTTP 200, không cần login lại; token trước/sau bằng nhau qua so SHA-256 trong memory, chỉ xuất boolean |
| Chuyển module rồi quay lại timetable | HTTP 200; token bằng token trước reload |
| Refresh/silent renewal | Không thấy request token/refresh/authorize/renew/silent sau login trong cửa sổ quan sát; không chứng minh không có refresh |
| Expiry, idle/absolute timeout | UNKNOWN; không chờ hết phiên hoặc đọc JWT claims |
| Reuse ngoài browser/server-side refresh | UNKNOWN; chưa thử HTTP client độc lập |
| Logout | Bấm nút đăng xuất thật, chuyển tới `login.microsoftonline.com/common/oauth2/v2.0/logout`; chưa xác nhận quay lại login hoặc invalidation phía server, không replay phiên |

### Cookie metadata, không có value

Browser API trả cookie value cùng metadata thì projection bỏ value trước output. Chỉ ghi cookie liên quan, không suy chức năng xác thực chỉ từ tên. Session nghĩa là không có expiry cố định trong metadata browser, không phải server không có timeout.

| Tên | Domain / path | Expiry | HttpOnly | Secure | SameSite |
| --- | --- | --- | --- | --- | --- |
| `__AntiXsrfToken` | `qldtbeta.phenikaa-uni.edu.vn` / `/` | Session | Có | Không | Lax |
| `<dynamic-key>` — tên động đã ẩn | `qldtbeta.phenikaa-uni.edu.vn` / `/` | Session | Không | Không | Lax |
| `MSISAuth`, `MSISSignOut`, `MSISAuthenticated`, `MSISLoopDetectionCookie` | `sso.phenikaa-uni.edu.vn` / `/adfs` | Session | Có | Có | None |

Phía Microsoft identity còn thấy `ESTSAUTH` dạng session và `ESTSAUTHPERSISTENT` có expiry; cả hai có HttpOnly/Secure, SameSite=None. Không lưu thời điểm expiry cụ thể hoặc mở rộng đây thành kết luận về thời hạn phiên QLĐT.

Cookie portal thiếu Secure là rủi ro cấu hình quan sát được, không phải kết quả khai thác. Không làm yếu cookie `AMS_SESSION` để mô phỏng nguồn. Credential tích hợp, nếu sau này được phép lưu, phải tách khỏi session AMS, mã hóa authenticated encryption, giới hạn quyền và không đi qua domain DTO/log. Hết phiên thì dừng sync, yêu cầu reconnect tương tác; không lưu mật khẩu Phenikaa.

## Danh mục endpoint thực tế

Host chung: `https://qldtbeta.phenikaa-uni.edu.vn`. Các endpoint đã quan sát được phân loại **PORTAL_INTERNAL_API**: frontend đã gọi, chưa tìm thấy tài liệu chính thức trong tài nguyên đã đọc. Không gọi là official API hoặc HTML_RENDERED chỉ vì module tải HTML template.

### Giao thức chung

- Các POST trong bảng đều trả HTTP 200, không có query key, request `application/x-www-form-urlencoded; charset=UTF-8`, body chỉ thấy field **`A`** trên đường truyền.
- Header liên quan đã thấy: `accept`, `authorization`, `content-type`, `cookie`, `origin`, `referer`; chưa xác minh tập header tối thiểu bắt buộc.
- Response `application/json`; envelope có `Data: { B: string }`, `Message: string`, `Success: boolean`, `Pager: null` trong mẫu chính, `Id: null`. Không giữ giá trị chuỗi/nghiệp vụ. HTTP 200 không tự chứng minh `Success=true`, completeness hoặc danh sách rỗng hợp lệ.
- STATIC: client bọc tham số bằng `AE(JSON.stringify(...))` vào `A`, xử lý `Data.B` qua `AD` rồi parse JSON. Chưa reverse/bypass hay implement lớp này. Các tham số nghiệp vụ bên dưới là từ STATIC, **không phải plaintext body đã capture**.

### Năm nguồn chính

| Nhóm | Method / path thực tế | Phạm vi/filter từ STATIC; kiểm chứng UI |
| --- | --- | --- |
| Profile | POST `/sinhvienapi3/api/SV_Custom/DSA4FSkuLyYVKC8CKSgVKCQ1CS4SLgPP` | `LayThongTinChiTietHoSo`, `strId` từ current user; mở hồ sơ nhưng không lưu form |
| Timetable | POST `/sinhvienapi3/api/SV_ThongTin_MH/DSA4BRINKCIpAiAPKSAv` | `LayDSLichCaNhan`, `strQLSV_NguoiHoc_Id`, `strNgayBatDau`, `strNgayKetThuc`; có lịch UI/RUNTIME |
| Exam | POST `/sinhvienapi3/api/SV_ThongTin_MH/DSA4BRINKCIpFSkoHgokCS4gIikVKSgP` | `LayDSLichThi_KeHoachThi`, learner, `strDaoTao_ThoiGianDaoTao_Id`, `strDaoTao_HocPhan_Id`; có thể thay action qua `strHam` |
| Grades | POST `/sinhvienapi3/api/SV_ThongTin_MH/CiQ1EDQgCS4iFSAxAiAPKSAv` | `KetQuaHocTapCaNhan`, learner, `strDaoTao_ChuongTrinh_Id`, function/user context; có bảng điểm UI |
| Curriculum | POST `/kehoachchuongtrinhapi/api/KHCT_ThongTin_MH/DSA4BRIKEh4FIC4VIC4eCS4iESkgLx4CFQPP` | Chương trình, filter, `pageIndex/pageSize`; có danh sách môn và kiểu dữ liệu RUNTIME |

Exam được gọi sau khi chọn một học kỳ có trong danh sách và bấm xem. HTTP 200 nhưng hai bảng lịch thi không có hàng, không có thông báo rỗng rõ ràng. Chưa xác nhận semantics “không có kỳ thi”, không kết luận tài khoản không có lịch thi. Record thi dùng để so ID bên dưới đến từ lịch cá nhân, không phải endpoint exam riêng.

### Request phụ trợ đã quan sát

Các POST dưới đây cũng dùng giao thức chung trên, trả 200.

| Nhóm | Path | Vai trò |
| --- | --- | --- |
| Profile | `/sinhvienapi3/api/SV_KeHoach_MH/DSA4BRIKJAkuICIpDykgMQkuEi4P` | Kế hoạch tự nhập hồ sơ |
| Timetable | `/sinhvienapi3/api/SV_ThongTin_MH/DSA4FQoDDS4xCikuLyYCLg0oIikCKSgVKCQ1` | Lớp chưa có lịch chi tiết; filter learner/ngày |
| Exam | `/sinhvienapi3/api/SV_ThongTin_MH/DSA4BRIVKS4oBiggLw0oIikVKSgP` | Danh sách thời gian/học kỳ |
| Grades | `/sinhvienapi3/api/SV_ThongTin_MH/DSA4FSkuLyYVKC8CKTQuLyYVMygvKQkuIgPP` | Thông tin chương trình |
| Grades | `/sinhvienapi3/api/SV_ThongTin_MH/DSA4BRIVKS4oBiggLw0oIikJLiIP` | Thời gian đào tạo |
| Grades | `/sinhvienapi3/api/SV_ThongTin_MH/DSA4CiQ1EDQgFSgiKQ00OBUpJC4KKS4o` | Kết quả tích lũy theo khối |
| Curriculum | `/dangkyhocapi3/api/DKH_Chung_MH/DSA4BRICKTQuLyYVMygvKQPP` | Danh sách chương trình |
| Curriculum | `/kehoachchuongtrinhapi/api/KHCT_ThongTin_MH/DSA4BRIKEh4FIC4VIC4eCikuKBU0AikuLx4FLi8P` | Khối tự chọn |
| Curriculum | `/kehoachchuongtrinhapi/api/KHCT_ThongTin_MH/DSA4BRIKEh4FIC4VIC4eCikuKAMgNQM0LiIP` | Khối bắt buộc |
| Curriculum | `/kehoachchuongtrinhapi/api/KHCT_ThongTin_MH/DSA4BRIKEh4FIC4VIC4eCS4iESkgLx4CFR4RAwPP` | Phân bổ theo môn; UI tự phát nhiều request |

Dictionary: GET `/cmsapi/api/CMS_DanhMucThuocTinh/LayDanhSachDuLieuTheoBangDM`, 200 JSON, Bearer/cookie present, không body. Query key: `_`, `dTrangThai`, `strChucNang_Id`, `strMaBangDanhMuc`, `strNguoiThucHien_Id`, `strTieuChiSapXep`; không giữ value.

Quan hệ môn: action `KHCT_ThongTin_MH/DSA4BRIKEh4FIC4VIC4eEDQgLwkkCS4iESkgLwPP` mới thấy trong **STATIC**, chưa có Network. Tham số khai báo gồm tổ chức chương trình, học phần, loại quan hệ và pagination. Không đánh dấu là endpoint đã kiểm chứng.

Mã liên quan: `profile/script/tunhaphoso.js`, `thoikhoabieu/script/lichgiang.js`, `thoikhoabieu/script/lichthi.js`, `hoctap/script/diemhoc.js`, `hoctap/script/chuongtrinhhoc.js` dưới `/conggiangvien/ApisCongSinhVien/modules/`. Không gọi trực tiếp action, không đưa điểm danh/đăng ký môn/cập nhật hồ sơ vào connector đọc.

Pagination: STATIC curriculum có `pageIndex/pageSize`, có chỗ đặt pageSize rất lớn. Chưa kiểm chứng giới hạn server, tổng số trang hoặc completeness; `Pager` ngoài envelope không đủ mô tả dữ liệu bên trong B. Curriculum phát nhiều request theo môn nên cần tính chi phí sync, không sao chép pageSize lớn hoặc load test nguồn.

## Schema và mapping sang domain Phase 2

### Cấu trúc thực tế sau xử lý của portal

RUNTIME `main_doc.LichGiang.dtLichHoc` là array có phần tử; chỉ kiểm tra tên/kiểu, không lưu giá trị.

| Field | Kiểu quan sát |
| --- | --- |
| `ID`, `IDLICHHOC`, `IDLOPHOCPHAN`, `DANGKY_LOPHOCPHAN_ID` | String trong mẫu buổi học; record thi có thể thiếu hai field lớp |
| `TENHOCPHAN`, `TENLOPHOCPHAN`, `DANGKY_LOPHOCPHAN_TEN`, `PHANLOAI` | String |
| `NGAYHOC`, `THU`, `THUHOC`, `BUOIHOC` | String; UI ngày DD/MM/YYYY, GMT+7; format wire trong B chưa xác nhận |
| `SOTIET`, `TIETBATDAU`, `TIETKETTHUC`, `GIOBATDAU`, `PHUTBATDAU`, `GIOKETTHUC`, `PHUTKETTHUC` | Number |
| `IDPHONGHOC`, `PHONGHOC_TEN`, `TENPHONGHOC`, `PHONGHOC_MA`, `GIANGVIEN` | String trong mẫu |
| `BAIHOC`, `NGAYBATDAU`, `NGAYKETTHUC`, `CATHI` | Null trong mẫu buổi học; không suy luôn null |

`dtTKBKhongLichChiTiet` rỗng trong lần quan sát, chưa có item schema. Không thu thập nội dung chuyên cần hoặc ID người học để đưa vào tài liệu.

RUNTIME `main_doc.ChuongTrinh.dtHocPhan_ChuongTrinh` là array có phần tử:

| Field | Kiểu quan sát |
| --- | --- |
| `ID`, `DAOTAO_TOCHUCCHUONGTRINH_ID`, `DAOTAO_CHUONGTRINH_MA/TEN`, `DAOTAO_HOCPHAN_ID/MA/TEN` | String; dấu `/` gộp các field cùng prefix |
| `HOCTRINHAPDUNGHOCTAP`, `HOCTRINHAPDUNGTINHHOCPHI`, `LAMONTINHDIEMTHEOCHUONGTRINH` | Number |
| `DAOTAO_THOIGIAN_KEHOACH_ID`, `DAOTAO_THOIGIAN_KEHOACH`, `DAOTAO_THOIGIAN_KEHOACH_NAM` | String |
| Các field thời gian thực tế ID/nhãn/năm | Null hoặc string |
| Các field kỳ/đợt kế hoạch và thực tế | Null trong mẫu; semantics chưa chốt |
| `THUOCTINHHOCPHAN_ID/TEN/MA`, `KHOIKIENTHUC` | String |
| `THONGTINQUANHEHOCPHAN` | Null hoặc string; chưa parse thành prerequisite graph |

Profile/grades có Network envelope và UI nhưng chưa xác nhận type/nullability từng field sau giải mã. `main_doc` có lúc còn tham chiếu module trước nên không gán schema của module khác cho màn hình hiện tại. Bảng dưới phân biệt phần chỉ có STATIC.

### Mapping đề xuất, chưa triển khai

Confidence khá chỉ nói field/kiểu đã xuất hiện, không bảo đảm semantics, completeness hoặc độ ổn định dài hạn.

| AMS field | Source field | Available / evidence | Transformation | Confidence | Notes |
| --- | --- | --- | --- | --- | --- |
| StudentProfile.studentNumber/programName/cohort | `QLSV_NGUOIHOC_MASO`, nhãn chương trình/khóa | UI + STATIC; schema chi tiết UNKNOWN | Profile thuộc user AMS hiện tại, UUID nội bộ | Thấp | Không lấy learner ID tùy ý từ client; chỉ nhập dữ liệu cần thiết |
| Semester.academicYearStart/termCode | `DAOTAO_THOIGIANDAOTAO_ID`, `NAMHOC`, `HOCKY`, `DOTHOC` | STATIC + UI filter | Ánh xạ niên khóa/kỳ/đợt riêng | Thấp | Không mặc định hai kỳ hoặc suy năm từ ngày buổi học |
| Course.code/name/credits | `DAOTAO_HOCPHAN_MA/TEN`, `HOCTRINHAPDUNGHOCTAP` | RUNTIME string/string/number | Decimal credits | Khá | Không dùng tín chỉ tính học phí làm tín chỉ học tập |
| ClassSection.sectionCode/course/semester | `IDLOPHOCPHAN`, `DANGKY_LOPHOCPHAN_ID`, tên lớp; `MA_LOPHOCPHAN` từ STATIC | Có ID RUNTIME; quan hệ đầy đủ UNKNOWN | Source mapping sang FK nội bộ | Vừa | Hai ID bằng nhau trong mẫu, chưa coi là alias toàn nguồn |
| StudentCourse.attemptNumber/creditsAttempted | `LANHOC`, `LANTHI`, tín chỉ trong kết quả | STATIC + UI | Bảo toàn lần học lại | Thấp | Lần thi khác lần học; chưa chốt key attempt |
| AcademicResult.status/scores/creditsEarned | `DIEM`, `DIEMQUYDOI`, `DIEMQUYDOI_TEN`, `DANHGIA_TEN`, `KETQUA` | STATIC + UI; type/nullability UNKNOWN | Thiếu điểm giữ null; mapping trạng thái có kiểm chứng | Thấp | Không suy ngưỡng đạt/GPA/tín chỉ đạt từ màu UI |
| ClassSession.occurrenceKey/startsAt/endsAt/room | `IDLICHHOC`, `NGAYHOC`, giờ/phút, `TENPHONGHOC` | RUNTIME, có reload test | Xác minh parse ngày/timezone Asia/Ho_Chi_Minh trước đổi Instant | Vừa | Không bịa giờ hoặc hash ngày/giờ/phòng làm identity |
| Exam.occurrenceKey/studentCourseId/startsAt/endsAt | `IDLICHHOC`, `PHANLOAI`, ngày/giờ trong lịch cá nhân; `LANTHI/CATHI` từ STATIC | Có record thi trong lịch cá nhân, chưa có item từ endpoint exam riêng | Tách exam khỏi session, reconcile attempt | Vừa/thấp | Mẫu thi thiếu section ID; không tạo StudentCourse giả để đủ FK |
| Curriculum.code/revision/cohort | `DAOTAO_TOCHUCCHUONGTRINH_ID`, `DAOTAO_CHUONGTRINH_MA/TEN` | RUNTIME một phần; revision/cohort UNKNOWN | Version có provenance | Vừa | Chưa chứng minh ID tổ chức chương trình đồng nghĩa ID chương trình |
| CurriculumGroup.minimumCredits | `SOTINCHIQUYDINH`, `SOHOCPHANQUYDINH`, khối bắt buộc/tự chọn | STATIC, Network có endpoint nhóm | Tách yêu cầu tín chỉ và số môn | Thấp | Chưa có minimumCourseCount; xác minh trước migration |
| CurriculumCourse.requirement/credits/recommendedTerm | Môn, tín chỉ, thuộc tính, thời gian kế hoạch | RUNTIME + STATIC | Phân biệt required/elective và đợt học | Vừa | Không coi null là không bắt buộc |
| CoursePrerequisite.kind/quan hệ | `LOAIQUANHE_TEN`, `TOANTU_TEN`, `GIATRIDIEUKIEN`, `MUCDIEUKIEN_TEN` | STATIC, chưa Network quan hệ môn | Cần xác minh toán tử/mức điều kiện | Thấp | Model chỉ AND, PREREQUISITE/COREQUISITE; chưa OR/equivalence/minimum grade |
| GradingPolicy.code/revision/attemptPolicy | `THANGDIEM_MA`, `THUOCTINHLANTINH`, `LAMONTINHDIEMTHEOCHUONGTRINH` | STATIC; field cuối có RUNTIME number | Cần quy chế và phiên bản nguồn | Thấp | Không tự bật includedInGpa hoặc hard-code ngưỡng từ bảng điểm cá nhân |

Gap cần review: yêu cầu số môn bên cạnh tín chỉ; toán tử quan hệ môn; lần thi khác lần học; exam → StudentCourse khi thiếu class/attempt ID; grading policy thiếu provenance. AcademicResult hiện giữ kết quả của lần học, không lưu lịch sử từng lần thi. Không sửa domain Phase 2 chỉ dựa trên tên field hoặc một mẫu.

## Identifier stability và source mapping

Phép thử giữ record trong memory, ghép duy nhất theo thuộc tính quan sát để tìm lại cùng buổi sau reload, so candidate ID bằng hash có salt dùng một lần. Chỉ xuất present/sameAfterReload. Signature đối chiếu **chỉ phục vụ reload không có thay đổi**, không phải identity production; không lưu hash hoặc ID.

| Record / candidate | Present | Same after reload | Scope suy đoán / confidence |
| --- | --- | --- | --- |
| Class session: `ID` | Có | Không | ID render/response không ổn định; loại khỏi stable source key; chưa biết nguồn sinh ID |
| Class session: `IDLICHHOC` | Có | Có | Candidate lịch, vừa; chưa phân biệt buổi riêng với quy tắc lặp nhiều tuần |
| Class section: `IDLOPHOCPHAN` | Có | Có | Candidate lớp, vừa; global scope chưa xác minh |
| Class section: `DANGKY_LOPHOCPHAN_ID` | Có | Có | Candidate lớp/đăng ký, vừa; bằng IDLOPHOCPHAN trong mẫu, không khái quát |
| Exam trong lịch cá nhân: `ID` | Có | Không | Loại khỏi stable source key |
| Exam trong lịch cá nhân: `IDLICHHOC` | Có | Có | Candidate lịch thi, vừa; scope/lần thi UNKNOWN |
| Exam: hai candidate section ID trên | Không | Không áp dụng | Không suy endpoint khác cũng thiếu liên kết attempt |
| Course: `DAOTAO_HOCPHAN_ID` | Có trong curriculum RUNTIME | Chưa thử | Scope/stability UNKNOWN |
| Semester: `DAOTAO_THOIGIANDAOTAO_ID` | STATIC + UI filter | Chưa thử | Kỳ/đợt và scope UNKNOWN |
| Academic attempt: `LANHOC`, ID kết quả | STATIC | Chưa thử | Chưa có natural key được xác minh |

**Change stability của mọi candidate: UNKNOWN.** Không sửa lịch nguồn để thử. `IDLICHHOC` ổn định qua reload chưa đủ để cam kết ổn định khi đổi phòng/giờ hoặc là occurrence key duy nhất. Cần thay đổi tự nhiên/tài liệu nguồn và kiểm tra tính duy nhất giữa các tuần.

Proposal: map owner + source system + external account binding + entity kind + source scope + source identifier sang **AMS UUID**. Binding tách khỏi credential/connection có thể thay khi reconnect; phải kiểm chứng vẫn cùng tài khoản. Giữ provenance, mốc quan sát và phiên bản mapper; unique key cụ thể chờ xác minh scope.

Giữ `(profile_id, section_id, occurrence_key)` cho session và `(profile_id, student_course_id, occurrence_key)` cho exam. Thiếu key nguồn thì cần reconciliation với trạng thái chưa đối chiếu; không ghép mơ hồ, phát change giả hoặc dùng hash(date + time + room), DOM ID hay vị trí array làm identity. Chưa tạo bảng mapping/heuristic production.

## Push, polling và lỗi

NETWORK xác nhận WebSocket `wss://api-apis.com/socket.io/`, query key `EIO`, `sid`, `transport`, không value. Có frame gửi/nhận nhưng không đọc/lưu payload hoặc channel cá nhân. STATIC có `nodeChat`, nên chat/notification chung là suy luận, **academic-change push vẫn UNKNOWN**.

STATIC có Firebase app/messaging compat, `assets/js/fcm-notify.js`, service worker registration, `onMessage` và luồng push token. Không cấp quyền notification, lấy token hoặc gửi thông báo; chưa xác minh delivery thực tế. Không thấy response SSE `text/event-stream`; webhook, SSE và long polling học vụ chưa được xác minh, không kết luận không tồn tại.

Giữ timetable mở **143 giây**: không thấy API lịch học gọi lặp. GET template `/congsinhvien/ApisCongSinhVien/modules/thoikhoabieu/html/lichhoc.html` lặp khoảng **20 giây**, không phải evidence polling dữ liệu lịch. STATIC có timer 300000 ms kiểm tra version Config.js, không phải lịch sync học vụ.

Nếu không có push học vụ được phép sử dụng, đề xuất **near-real-time polling**, không gọi technical real-time. Mốc review ban đầu, chưa phải giới hạn/bảo đảm từ trường: schedule/exam 30–60 phút, grades 12–24 giờ, profile/curriculum hàng tuần hoặc thủ công. Cần jitter, coalescing, khóa chống chạy chồng, backoff, tôn trọng Retry-After và dừng khi hết phiên. Không tải toàn bộ curriculum trong mỗi lần polling lịch; xác minh chính sách và chi phí request trước production.

Không thấy HTTP lỗi >=400 của API portal trong cửa sổ quan sát. Unauthorized/expired-session, invalid-semester, rate limit và completeness UNKNOWN; không fuzzing để tạo lỗi. HTTP 200/bảng trống không đủ xác nhận business success. Phải phân biệt rỗng hợp lệ với mất phiên/lỗi/partial response trước khi coi lịch bị hủy.

## Kiến trúc đề xuất và kế hoạch Phase 4 có điều kiện

Ưu tiên API chính thức nếu trường cung cấp và cho phép; kế tiếp là **typed HTTP client cho internal API** sau khi xác minh protocol A/B, quản lý phiên hợp lệ và chính sách truy cập. Browser automation là fallback nếu API không usable; HTML parsing chỉ khi cần thiết. Không chọn Playwright làm production connector vì đang dùng được để nghiên cứu.

Chưa có PoC API độc lập ngoài browser, nên typed HTTP chưa được kiểm chứng end-to-end. Quyền xem UI không tự chứng minh quyền dùng API dài hạn, refresh Microsoft hoặc subscribe push.

### Review AcademicPortalClient

Giữ nguyên `AcademicSnapshot fetchSnapshot(StudentConnectionId)`. Adapter vẫn ném `UnsupportedOperationException`, chưa có HTTP implementation. Snapshot có semester/course/section/studentCourse (kèm điểm cơ bản)/classSession/exam, thiếu profile/curriculum/prerequisites/grading policy và completeness theo capability.

Network cho thấy module/filter khác nhau: lịch theo khoảng ngày, thi theo kỳ/môn, điểm theo chương trình, curriculum nhiều request phụ. Đề xuất Phase 4 tách capability profile/schedule/exams/academicRecords/curriculum, kèm thời điểm, phạm vi query, provenance và COMPLETE/PARTIAL/UNAVAILABLE. Orchestrator có thể tổng hợp snapshot nhưng không giả định portal là một transaction nhất quán. Source DTO/parser ở adapter, không leak field Phenikaa vào domain. Chưa refactor khi protocol/error/mapping chưa chắc chắn.

### Thứ tự sau khi được duyệt, chưa bắt đầu

1. Xác nhận cách truy cập được cho phép, protocol A/B và lifecycle phiên. Người dùng tự login/MFA; không lưu password hoặc xin thêm quyền OAuth của ứng dụng trường. Không thông được thì báo blocker, không làm integration giả.
2. Xác minh item schema exam, profile/grades, pagination/completeness, semester semantics, source scope và identity qua thay đổi tự nhiên. Chốt gap Phase 2 trước khi mở rộng schema.
3. Chốt capability contract và source mapping; thiết kế connection/reconnect có ownership, mã hóa credential, hết phiên/thu hồi; không đưa token vào DTO/log.
4. Mapper với fixture tổng hợp và mock HTTP/contract tests: success, empty, partial, expired, malformed; timezone, nullable, attempt, idempotency trước persistence. Không dùng payload cá nhân làm fixture.
5. Sau các prerequisite mới triển khai import trong phạm vi Phase 4 được duyệt: transaction, mapping, completeness, lỗi/retry. Scheduled sync/change detection/Google Calendar/email không tự động thuộc phạm vi này.

## Đối chiếu kế hoạch và kiểm chứng

Đợt research đã thực hiện các phép kiểm tra được yêu cầu; UNKNOWN có lý do, không biến kiểm tra chưa chạy thành pass. Đây không phải nghiệm thu production hoặc cam kết mọi câu hỏi về nguồn đã được giải quyết.

| Tiêu chí | Kết quả |
| --- | --- |
| Năm actual endpoints | Đã xác định bằng Network cùng method/host/path/status/content type/body/header metadata |
| Bearer/cookie và metadata | Đã ghi presence/flags, không value; auth requirement tối thiểu UNKNOWN |
| Refresh/session reload | Đã điều tra; reload/navigation thành công, token equality true; expiry/refresh UNKNOWN |
| Candidate ID timetable/exam | So cùng record qua reload, loại ID thay đổi; exam từ lịch cá nhân; change stability UNKNOWN |
| Socket.IO/Firebase/polling | WebSocket thực tế, Firebase STATIC; 143 giây không schedule API polling; push học vụ UNKNOWN |
| Mapping Phase 2 và contract | Đã review gap/capability, không sửa production dựa trên suy đoán |
| Kế hoạch Phase 4 | Có prerequisite/điểm dừng, chưa triển khai |
| Profile riêng/vệ sinh dữ liệu | Login tương tác, redact trong memory; Chrome đã đóng, script/profile đã xóa |
| CI không gọi portal | Adapter chưa có HTTP; test chỉ kiểm tra URI allowlist; không thêm live test vào CI |
| Git/phạm vi | Chỉ docs trên AMS-Solution, không merge main/rewrite/force push/đổi workflow |

Kiểm tra local đã đạt: observer self-test với dữ liệu tổng hợp, cookie projection bỏ value, nested schema/query redaction. Live verification local gồm năm màn hình, HTTP 200, reload/navigation, ID comparison và cửa sổ polling. Observer không ghi nhận lỗi xử lý request/response ở lần tổng kết; không đồng nghĩa đã quan sát toàn bộ giao thức.

Không chạy lại Maven/lint/build local vì không đổi production source/test/database. Không dùng kết quả phase trước để tuyên bố connector pass. Trước commit kiểm tra staged diff, file mới và secret patterns; trạng thái commit/push/CI được báo ở lần bàn giao, không đưa log runtime vào tài liệu.

UNKNOWN ưu tiên review: protocol A/B cho HTTP độc lập; refresh/expiry/invalidation; quyền truy cập dài hạn; scope/change stability IDLICHHOC; exam → attempt; pagination/business error; prerequisite/grade policy semantics. Một tài khoản và cửa sổ ngắn không đại diện mọi khóa/chương trình. Internal endpoint/schema có thể đổi không báo trước. Chưa triển khai Phase 4 cho tới khi phạm vi và prerequisite được duyệt.
