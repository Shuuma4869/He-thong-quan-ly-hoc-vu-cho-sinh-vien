# Tiến độ học tập do nguồn báo: bằng chứng Phase 16B

## Kết luận để đọc trước

**`PHASE_16B_OVERALL = PARTIAL`; `PROGRESS_LIVE_READ_ONLY_READY = PARTIAL`.** Cổng Phenikaa có các bảng tổng hợp tín chỉ, điểm trung bình và khối kiến thức mà AMS chưa đọc qua contract hiện tại. Mã giao diện công khai cho biết endpoint, tên bảng và cách trang điểm chọn một số hàng để hiển thị; các lượt kiểm tra hợp lệ ở Phase 5A–5D đã quan sát dữ liệu ở những phần này. Tuy nhiên, trong Phase 16B không có kết nối Phenikaa được cấp hợp lệ để kiểm tra lại response, kiểu giá trị, các trường hợp nhiều chương trình và sự thay đổi theo thời gian. **Chưa mở API hay giao diện tiến độ.**

Lát cắt có thể xem xét cho Phase 16C là **điểm trung bình tích lũy và tổng tín chỉ tích lũy do nguồn báo**, ở chế độ đọc trực tiếp, với điều kiện chương trình AMS đã chọn nối được bằng ID nguồn chính xác và từng hàng tổng hợp được kiểm tra nghiêm ngặt lúc đọc. Đây không phải phép tính của AMS và chưa phải lời hứa có dữ liệu cho mọi tài khoản. Tiến độ theo nhóm, hoàn thành từng môn, tín chỉ còn thiếu và phần trăm hoàn thành **chưa đủ cơ sở để trình bày như kết quả đã xác nhận**.

`SOURCE_REPORTED_PROGRESS` là giá trị mà cổng trả về; `AMS_DERIVED_PROGRESS` là giá trị AMS tự chọn kết quả, cộng tín chỉ hoặc tính GPA. Phase này chỉ nghiên cứu loại thứ nhất. Không có `StudentCourse`, `AcademicResult`, bộ tính GPA, migration hoặc thay đổi runtime.

## Nguồn đã đối chiếu

Baseline là main `1e5978d575a9b16600db00c6b66ab81207ae66dc`; CI main `37496026890` đã xanh. Local `AMS-Solution` được fast-forward lên đúng commit này trước khi nghiên cứu.

Ngày **06/10/2026**, hai script công khai của chính portal trả HTTP 200:

- [Màn hình điểm `diemhoc.js`](https://qldtbeta.phenikaa-uni.edu.vn/conggiangvien/ApisCongSinhVien/modules/hoctap/script/diemhoc.js), SHA-256 `300cdbda1cd04d765f1666d0ce65d583382fe900c80131ec172569737519242a`.
- [Màn hình chương trình `chuongtrinhhoc.js`](https://qldtbeta.phenikaa-uni.edu.vn/conggiangvien/ApisCongSinhVien/modules/hoctap/script/chuongtrinhhoc.js), SHA-256 `3e67f77b57f2a4a7d8b258998295ba05d581d63fd175df93ff010236cb470b22`.

Đường dẫn đầy đủ có tiền tố `/conggiangvien/ApisCongSinhVien/modules/`; thử `hoctap/script/...` trực tiếp ở gốc host trả 404. Chỉ đọc mã công khai, không tải hay lưu phản hồi của tài khoản. `PUBLIC_SOURCE_RESEARCH = PASS` cho việc xác minh hai tài nguyên này, **không** đồng nghĩa public API cho bên thứ ba hoặc live data đã được kiểm tra.

Các quan sát live trước đây ở [Phase 5A–5D](phenikaa-integration.md#phase-5a-môn-học-học-kỳ-lần-học-và-kết-quả) là bằng chứng có giới hạn thời gian và một tài khoản. Phase 5D ghi nhận 14 hàng tổng hợp khối, 85 hàng môn trong khối, 40 hàng tổng kết điểm và bốn hàng học phần nợ; tài liệu này chỉ dùng số lượng, không sao chép giá trị học tập. Contract Java hiện tại chỉ chuẩn hóa `rsDiemThanhPhan`, `rsDiemKetThucHocPhan` và dữ liệu đăng ký, không chuẩn hóa các bảng tiến độ. `LIVE_PROGRESS_RESEARCH = NOT_RUN_NO_PROVISIONED_CONNECTION` trong lượt này.

## Endpoint, bảng và trường đã tìm thấy

Đây là các **API nội bộ của portal**, chưa có cam kết dùng cho ứng dụng bên ngoài. Request đi qua cơ chế mã hóa sẵn có của portal; không thể gọi trực tiếp chỉ với URL và không được dùng trình duyệt của sinh viên làm cách cấp token cho AMS.

| Nội dung | POST path sau host | `func` | Bảng frontend đọc |
| --- | --- | --- | --- |
| Điểm và tổng hợp chung | `/sinhvienapi3/api/SV_ThongTin_MH/CiQ1EDQgCS4iFSAxAiAPKSAv` | `pkg_congthongtin_hssv_thongtin.KetQuaHocTapCaNhan` | `rsDiemTrungBinhChung`, `rsHocPhanChuaHoanThanh` cùng các bảng điểm đã biết |
| Tích lũy theo khối | `/sinhvienapi3/api/SV_ThongTin_MH/DSA4CiQ1EDQgFSgiKQ00OBUpJC4KKS4o` | `pkg_congthongtin_hssv_thongtin.LayKetQuaTichLuyTheoKhoi` | `rsTongHop`, `rsChiTiet` |
| Chương trình của màn hình điểm | `/sinhvienapi3/api/SV_ThongTin_MH/DSA4FSkuLyYVKC8CKTQuLyYVMygvKQkuIgPP` | `pkg_congthongtin_hssv_thongtin.LayThongTinChuongTrinhHoc` | Danh sách lựa chọn có `DAOTAO_TOCHUCCHUONGTRINH_ID` |

Khi đổi dropdown chương trình, `diemhoc.js` gọi lại cả endpoint điểm lẫn tích lũy theo khối. Hai request gửi `strQLSV_NguoiHoc_Id` của phiên và `strDaoTao_ChuongTrinh_Id` lấy từ dropdown; mã JS không cho thấy phân trang ở hai request này. `Pager` của endpoint điểm được đọc nhưng không dùng để tải trang tiếp. Vì vậy **phạm vi request theo chương trình đã rõ; độ đầy đủ của response vẫn UNKNOWN**.

| Bảng | Trường frontend thực sự dùng | Điều có thể nói; điều chưa thể nói |
| --- | --- | --- |
| `rsDiemTrungBinhChung` | `DAOTAO_THOIGIANDAOTAO_ID`, `LOAIDIEMTRUNGBINH_MA`, `THUOCTINHLANTINH`, `THANGDIEM_MA`, `DIEMTRUNGBINH`, `TONGSOTINCHI`, `TONGSOTINCHICTDT`; ở hàng kỳ còn có `NAMHOC`, `DAOTAO_THOIGIANDAOTAO_KY`, `DOTHOC`, `PHAMVITONGHOPDIEM_TEN` | UI lọc hàng tổng quát theo kỳ null, loại `TRUNGBINHTICHLUY` hoặc `TRUNGBINHCHUNG`, thang `4`/`10`, `THUOCTINHLANTINH = 0`. Chưa xác minh tính duy nhất, nullability, kiểu số và ý nghĩa đầy đủ của `THUOCTINHLANTINH`. |
| `rsTongHop` | `MAKHOI`, `TENKHOI`, `TONGSOTINCHICUAKHOI`, `SOBATBUOC`, `SODATICHLUY` | UI hiển thị giá trị nguồn theo khối. `MAKHOI` chưa được chứng minh là cùng ID với mapping nhóm V7; `SOBATBUOC` chưa được chứng minh là số môn bắt buộc hay tín chỉ yêu cầu. |
| `rsChiTiet` | `MAKHOI`, `TENKHOI`, `DAOTAO_HOCPHAN_MA/TEN`, `DAOTAO_HOCPHAN_HOCTRINH`, `DIEM`, `DANHGIA_TEN`, `DIEMQUYDOI`, `DIEMQUYDOI_TEN`, `KETQUA`, `HOCPHANTHUA`, `HOCPHANTHUA_LOAIXULY` | UI viết “Hoàn thành” khi `KETQUA == 1`; các giá trị khác cho ô trống, **không** phải “chưa hoàn thành” được xác nhận. Không thấy ID môn hoặc ID nhóm trong các cột frontend dùng. |
| `rsHocPhanChuaHoanThanh` | `DAOTAO_HOCPHAN_MA/TEN`, `DAOTAO_HOCPHAN_HOCTRINH`, `DIEM`, `DANHGIA_TEN`, `LANHOC`, `LANTHI`, `THOIGIAN`, `DIEM_DANHSACHHOC_TEN` | UI gọi đây là học phần nợ/chưa hoàn thành. Chưa biết bảng gồm cả môn chưa học, môn tự chọn chưa chọn, miễn/công nhận hay chỉ các hàng có điểm chưa đạt. Không dùng nó để nói “còn thiếu môn X”. |

Các tên bảng/trường trên được đọc từ JS hiện còn public. Bằng chứng cũ cho thấy các bảng đã xuất hiện trong response của một tài khoản, nhưng Phase 16B **không** xác minh lại payload hiện tại, kiểu từng field, tập mã đầy đủ hoặc số hàng. Không nâng tên biến frontend thành một bảo đảm về chính sách học vụ. `CURRENT_SOURCE_PROGRESS_TABLES = PARTIAL`.

## Nối chương trình AMS đã chọn với chương trình nguồn

Phase 16A lưu `student_profile.curriculum_id` với nghĩa `USER_SELECTED_AMS`, không phải chương trình hiện hành do Phenikaa xác nhận. V7 lưu `phenikaa_curriculum_mapping` theo `(profile_id, source_curriculum_id) → curriculum_id`. Importer 5B lấy `source_curriculum_id` từ `DAOTAO_TOCHUCCHUONGTRINH_ID` của selector curriculum. Bộ đọc điểm lấy `AcademicProgram.sourceId` từ trường cùng tên ở danh sách chương trình của màn hình điểm; mẫu 5B từng quan sát hai ID khớp nhau. Cả hai request tiến độ trên JS cũng lấy ID từ dropdown màn hình điểm.

Một đường nối **có điều kiện, chưa triển khai** là: lấy lựa chọn của user hiện tại → tìm đúng một mapping của chính hồ sơ → lấy lại danh sách `AcademicProgram` của chính kết nối nguồn đang `CONNECTED` → chỉ tiếp tục khi có đúng một ID **bằng hẳn** `source_curriculum_id`. Backend tự xử lý ID; browser không gửi ID Phenikaa. Cần kiểm tra lại connection/`authenticatedAt` trước khi trả kết quả, theo ranh giới đọc hiện có.

| Trường hợp | Quyết định an toàn |
| --- | --- |
| Có mapping và chương trình nguồn cùng ID trong danh sách hiện tại | Có thể đọc thử theo ID đó; vẫn phải kiểm tra schema/semantics của progress. |
| Chương trình AMS không có mapping nguồn | Trả trạng thái không khả dụng; không ghép theo mã/tên. |
| Nguồn không còn trả ID đã map | Không fallback sang chương trình đầu tiên/duy nhất. |
| Nhiều program cùng nhãn | Nhãn không tham gia phép nối. |
| Nhiều curriculum AMS cùng mã nhưng source ID khác | UUID và mapping riêng quyết định; mã không tham gia phép nối. |

Đây là **`TRACKED_CURRICULUM_TO_SOURCE_PROGRAM = PARTIAL`**: phép đối chiếu bằng đúng ID đã có nền và một mẫu khớp, nhưng chưa thử live trong Phase 16B với nhiều chương trình, mapping vắng mặt và vòng đời đổi tài khoản nguồn. Không dùng `program_name`, code, cohort, vị trí mảng hay số lượng chương trình để nâng thành VERIFIED.

## Từng loại “tiến độ” có thể hiểu đến đâu?

**Hoàn thành môn.** `rsChiTiet.KETQUA == 1` được frontend hiển thị “Hoàn thành”. Đây là cờ ở hàng môn trong khối, không phải `DANHGIA_MA = DAT` của một đăng ký và không mang ID lần học/kết quả. Các mã khác, null, cách server tính cờ và khả năng một môn xuất hiện ở nhiều khối chưa rõ. `SOURCE_COURSE_COMPLETION = PARTIAL`. Chưa thấy `DAOTAO_HOCPHAN_ID` ở các cột JS dùng cho hàng này; phép nối qua `phenikaa_course_mapping` chưa được kiểm chứng. Tên không phải khóa, mã duy nhất trong hồ sơ chỉ có thể là đề xuất fallback cần review, **không** là VERIFIED. `COURSE_COMPLETION_TO_AMS_COURSE = BLOCKED`.

**Khối kiến thức.** `rsTongHop` có giá trị `SODATICHLUY` nguồn báo và một tổng `TONGSOTINCHICUAKHOI`. UI còn chèn hàng tổng từ bảng, nhưng AMS không được lấy phép cộng UI đó thay aggregate nguồn. `rsChiTiet` có mã/tên khối; không có bằng chứng `MAKHOI` cùng identity với `source_group_id` của V7 (mapping V7 dựa trên `ID` ở response curriculum). Không nối theo tên hay mã khối. Chưa có parent ID, cây khối, yêu cầu số môn hoặc quy tắc tín chỉ dư được xác minh. `SOURCE_GROUP_PROGRESS = PARTIAL`; `SOURCE_GROUP_TO_AMS_GROUP = BLOCKED`.

**Tổng tín chỉ tích lũy.** UI dùng `TONGSOTINCHI` của hàng `TRUNGBINHTICHLUY`, kỳ null, thang `10` để ghi nhãn “Tích lũy”. Đây là **giá trị tổng nguồn báo**, không phải `minimumCredits` của AMS, tín chỉ đạt từng môn hay tín chỉ còn thiếu. Chưa xác minh nó gồm miễn học, công nhận, chuyển đổi, môn học lại, môn tự chọn vượt yêu cầu hay scope của mọi bản ghi. Không gọi nó là “tín chỉ đã đạt” trong AMS. `SOURCE_OVERALL_ACCUMULATED_CREDITS = PARTIAL`; `REMAINING_CREDITS = NOT_IMPLEMENTED`.

**Điểm trung bình.** `rsDiemTrungBinhChung` có `DIEMTRUNGBINH`. UI phân biệt trung bình chung/tích lũy, hệ 4/10 và tổng thể/theo học kỳ bằng các bộ lọc nêu trên. Các giá trị là server trả; JS chỉ chọn hàng, không tính từ điểm môn. Nhưng `.find(...)` lấy hàng đầu tiên khi có nhiều hàng khớp, còn Phase 16B chưa xác minh uniqueness, null, kiểu số hoặc chính sách chọn hàng. Vì vậy có thể đề xuất “Điểm trung bình tích lũy — nguồn báo” sau khi một parser nghiêm ngặt xác minh **đúng một** hàng phù hợp, nhưng hiện chưa nâng lên VERIFIED. `SOURCE_GPA_SUMMARY = PARTIAL`; không ghi vào `GradingPolicy` hay `AcademicResult`.

**Học phần chưa hoàn thành.** `rsHocPhanChuaHoanThanh` có thật trong code frontend và đã có hàng ở lượt quan sát cũ. Nó không chứng minh mọi môn còn thiếu của chương trình; không rõ môn chưa học hoặc chưa chọn tự chọn có nằm trong đó không. `SOURCE_UNFINISHED_COURSES = PARTIAL`. `HOCPHANTHUA`/`HOCPHANTHUA_LOAIXULY` cho thấy UI có nhãn tín chỉ/môn thừa ở `rsChiTiet`, không chứng minh aggregate đã xử lý miễn/công nhận, thay thế và P/NP theo quy tắc nào. `SOURCE_AGGREGATE_INCLUDES_POLICY_PROCESSING = UNKNOWN`.

**Độ đầy đủ và ổn định.** Mã frontend không chia trang các bảng progress, nhưng `Pager`/giới hạn ẩn, dữ liệu chưa công bố và response rỗng chưa được giải thích. Các lượt đọc cũ lặp lại cho cùng mẫu giữ số hàng; đó là `OBSERVED` trong thời gian ngắn, không chứng minh schema, identity hay số tổng sẽ ổn định sau cập nhật điểm. `PROGRESS_COMPLETENESS = UNKNOWN`; `SOURCE_PROGRESS_STABILITY = OBSERVED` (chỉ ở bằng chứng cũ). Mọi contract tương lai phải giữ `completeness = UNKNOWN`.

**Yêu cầu chương trình.** `curriculum.minimumCredits`, `curriculum_group.minimumCredits` và `minimumCourseCount` là yêu cầu đã nhập từ selector/nhóm curriculum. `TONGSOTINCHICUAKHOI`, `SOBATBUOC`, `SODATICHLUY` thuộc một endpoint kết quả khác. Chưa có định danh nhóm chung hay tài liệu chứng minh hai “tín chỉ yêu cầu” cùng scope. Kể cả hai số bằng nhau trong một mẫu, không được coi là cùng nghĩa. `REQUIREMENT_SCOPE_ALIGNMENT = BLOCKED`.

## Bảng quyết định

`PARTIAL` nghĩa là thấy nguồn/luồng sử dụng nhưng thiếu ít nhất một điều kiện để trình bày không gây hiểu nhầm. `BLOCKED` nghĩa là không có phép nối hoặc nghĩa dữ liệu đủ tin cậy; `UNKNOWN` nghĩa là chưa có bằng chứng để kết luận. Không dùng `VERIFIED` cho một fact chỉ vì tên trường gợi ý đúng.

| Gate | Kết quả | Bằng chứng và giới hạn chính |
| --- | --- | --- |
| `TRACKED_CURRICULUM_TO_SOURCE_PROGRAM` | PARTIAL | V7 và hai selector dùng cùng tên ID; một mẫu cũ khớp. Chưa kiểm tra live nhiều chương trình/vòng đời. |
| `SOURCE_COURSE_COMPLETION` | PARTIAL | JS hiển thị `KETQUA == 1` là “Hoàn thành”; các giá trị khác/nguồn tính chưa rõ. |
| `COURSE_COMPLETION_TO_AMS_COURSE` | BLOCKED | Chưa xác minh ID môn trong `rsChiTiet`; không join theo tên/mã. |
| `SOURCE_GROUP_PROGRESS` | PARTIAL | `rsTongHop` có `SODATICHLUY`; scope và quy tắc cộng chưa rõ. |
| `SOURCE_GROUP_TO_AMS_GROUP` | BLOCKED | `MAKHOI` chưa chứng minh cùng ID với mapping nhóm V7. |
| `SOURCE_OVERALL_ACCUMULATED_CREDITS` | PARTIAL | UI đọc `TONGSOTINCHI` của hàng tích lũy; chính sách/scope chưa rõ. |
| `SOURCE_GPA_SUMMARY` | PARTIAL | UI đọc `DIEMTRUNGBINH` từ các hàng đã lọc; uniqueness, null và kiểu chưa kiểm tra live. |
| `SOURCE_UNFINISHED_COURSES` | PARTIAL | Có bảng riêng nhưng chưa biết phủ môn chưa học/tự chọn/miễn. |
| `REQUIREMENT_SCOPE_ALIGNMENT` | BLOCKED | Không có identity nhóm hoặc quy tắc so sánh requirement hai nguồn. |
| `PROGRESS_COMPLETENESS` | UNKNOWN | JS không paging nhưng không chứng minh response đầy đủ. |

## Ranh giới cho Phase 16C nếu được duyệt riêng

**Lát cắt nghiên cứu đủ để thiết kế tiếp, chưa đủ để tự triển khai:** đọc trực tiếp summary tích lũy do nguồn báo. Contract đề xuất có thể là `GET /api/me/academic/source/progress-summary`, không có tham số source ID. Cần user đã chọn curriculum (`409 CURRICULUM_SELECTION_REQUIRED` nếu chưa), mapping ID chính xác và chương trình đó trong danh sách của kết nối `CONNECTED` (`SOURCE_PROGRESS_UNAVAILABLE` nếu không); không tự chọn chương trình. Backend dùng phiên và cooldown hiện có, không lưu response, không cache vào database, không trả dữ liệu cũ khi kết nối mất.

Nếu lập contract ở Phase 16C, response phải ghi rõ `mode = SOURCE_REPORTED_LIVE_READ_ONLY`, `completeness = UNKNOWN` và chương trình **được người dùng chọn**. Chỉ đưa `DIEMTRUNGBINH` của hàng tích lũy hệ 4/10 và `TONGSOTINCHI` của hàng tích lũy đúng scope sau khi xác minh schema, kiểu số, không mâu thuẫn và **đúng một** hàng phù hợp; nếu thiếu hoặc trùng thì trả lỗi/trạng thái không khả dụng, không chọn hàng đầu như JS. Tên hiển thị là “Điểm trung bình tích lũy — nguồn báo” và “Giá trị tín chỉ tích lũy — nguồn báo”, không gọi “GPA AMS” hay “tín chỉ đã đạt”. Không thêm `groups`/`courses` vào response chỉ vì UI muốn có; hai phần đó cần gate identity/semantics riêng. Đây là **đề xuất**, không phải API Phase 16B đã tồn tại hoặc sự xác nhận live của giá trị.

Không tính `minimumCredits - sourceAccumulated`, không tạo tiến độ %, không suy tốt nghiệp hay điều kiện tiên quyết. `StudentCourse` vẫn bị chặn vì chưa có khóa lần học xuyên suốt, đặc biệt khi nhiều đăng ký cùng góp một tổng kết. `AcademicResult` vẫn bị chặn vì chưa chọn được kết quả hiệu lực và thiếu tín chỉ đạt/GPA inclusion cho từng lần học. Việc đọc một số tổng hợp do server trả **không** tháo hai blocker đó. `STUDENTCOURSE_MAPPING = UNCHANGED_BLOCKED`; `ACADEMICRESULT_PERSISTENCE = UNCHANGED_BLOCKED`.

Phase 16B dừng ở tài liệu. Để nâng `PARTIAL`, cần một kết nối được cấp hợp lệ cho chính tài khoản, chỉ đọc metadata/kiểu/cardinality của các bảng qua luồng bảo mật hiện có; xác minh mapping ở trường hợp nhiều chương trình và đối chiếu các scope. Không cần người dùng gửi token, cookie hay điểm thật. Không lưu raw response, HAR hoặc ảnh màn hình.
