# Kế hoạch học kỳ cá nhân

Trang `/planner` cho phép bạn tự xếp môn của **chương trình đang theo dõi trong AMS** vào “Kỳ kế hoạch 1”, “Kỳ kế hoạch 2”… Đây là thứ tự dự kiến do bạn đặt, không phải học kỳ, đợt đăng ký hoặc thời khóa biểu của nhà trường. Kế hoạch chỉ được lưu trong AMS; bấm xếp môn **không đăng ký học phần với nhà trường**.

Bạn cần chọn chương trình theo dõi ở `/curriculum` trước. Mỗi chương trình có năm phương án cố định, từ **Phương án 1** đến **Phương án 5**. Đây là các cách xếp thử riêng của bạn, không phải phiên bản chính thức. `scenarioNo` là số phương án; `plannedTerm` là kỳ kế hoạch bên trong phương án đó. Cả hai đều không phải học kỳ nguồn.

Trang kế hoạch có ô tìm theo mã/tên môn và tải thêm theo từng trang; danh sách môn lấy từ curriculum đã lưu trong PostgreSQL, không gọi Phenikaa. Một môn có thể nằm ở nhiều phương án nhưng chỉ một kỳ trong mỗi phương án. Bạn có thể chuyển kỳ hoặc bỏ môn khỏi phương án đang xem; thao tác bỏ không xóa môn khỏi chương trình hay phương án khác.

Bạn có thể sao chép phương án đang xem sang **một phương án trống**. Bản sao có assignment riêng: sửa bản sao không sửa bản gốc. Sao chép từ phương án trống cũng thành công nhưng không thêm môn nào. Không có thao tác ghi đè hoặc gộp vào phương án đã có môn. Muốn xóa các môn của một phương án, bạn phải xác nhận thêm một bước; các phương án khác được giữ nguyên.

## So sánh hai phương án

Ở `/planner`, chọn phương án đang xem làm bên trái, chọn một phương án khác làm bên phải rồi bấm **So sánh hai phương án**. Trang không tự so sánh khi mở hoặc khi đổi lựa chọn. Hai bên luôn thuộc **cùng chương trình đang theo dõi**; không có cách so sánh hai chương trình khác nhau trong màn hình này. Nếu chưa chọn chương trình theo dõi, bạn cần chọn ở `/curriculum` trước.

Kết quả cho biết mỗi bên đã xếp bao nhiêu môn, bao nhiêu kỳ kế hoạch và tổng tín chỉ dự kiến của các môn đã xếp. Từng kỳ cũng được đặt cạnh nhau để dễ đọc; kỳ chỉ có ở một bên sẽ hiện 0 môn và 0 tín chỉ ở bên kia. Môn được đối chiếu theo UUID nội bộ AMS, không ghép theo tên hoặc mã môn. Bốn trạng thái có nghĩa cụ thể:

- **Không đổi (`UNCHANGED`)**: cùng môn, cùng kỳ kế hoạch ở cả hai bên.
- **Chuyển kỳ (`MOVED`)**: cùng môn nhưng kỳ kế hoạch khác nhau.
- **Chỉ bên trái (`ONLY_LEFT`)** và **chỉ bên phải (`ONLY_RIGHT`)**: môn chỉ được xếp trong một phương án.

Mặc định trang chỉ hiện những môn xếp khác nhau. Bật **Hiện môn không thay đổi** để xem cả phần giống nhau mà không gọi lại API. Hai phương án trống vẫn so sánh được; hai phương án có cách xếp giống nhau sẽ được báo là không có khác biệt **về cách xếp môn**, không phải tương đương về học vụ. Sau khi thêm, chuyển, bỏ môn, sao chép hoặc xóa phương án, kết quả cũ được ẩn; bấm so sánh lại để đọc trạng thái mới. Đổi phương án đang xem hoặc chương trình theo dõi cũng không giữ kết quả của cặp cũ.

Đây là phép đối chiếu kế hoạch do bạn tự nhập, không phải phép chấm điểm phương án. AMS không xếp hạng, khuyến nghị, kiểm tra tiên quyết, trùng lịch, kết quả học tập hoặc tiến độ ở đây. Số tín chỉ là tổng `curriculum_course.credits` của các môn **đã xếp**, không phải tín chỉ đã đạt, tích lũy hay còn thiếu.

Mỗi card kỳ hiện số môn và **tín chỉ dự kiến trong kỳ**: tổng `curriculum_course.credits` của các môn bạn đã xếp. Nếu dữ liệu chương trình cập nhật tên hoặc số tín chỉ, lần đọc kế tiếp dùng giá trị mới, không dùng bản sao cũ trong kế hoạch. Con số này không phải tín chỉ đăng ký, đã học hay đã đạt. AMS chưa biết môn nào đã hoàn thành, chưa kiểm tra điều kiện tiên quyết hoặc trùng lịch, và không tính tiến độ, GPA, tín chỉ còn thiếu hay điều kiện tốt nghiệp từ kế hoạch.

Đổi chương trình theo dõi chỉ đổi các phương án đang xem. Các phương án của chương trình cũ vẫn còn khi bạn chọn lại. Bỏ lựa chọn theo dõi cũng không xóa chúng. Không có bước tự xếp môn theo `recommendedTerm`, dữ liệu nguồn hay Phương án 1: phương án khác chỉ có môn khi bạn tự thêm hoặc bấm sao chép.

## API và lưu trữ

Các endpoint đều cần session AMS; thao tác ghi cần CSRF token:

| Thao tác | Đường dẫn | Kết quả |
| --- | --- | --- |
| GET | `/api/me/academic/study-plan?scenario=2` | `mode=USER_PLANNED_AMS`, `scenarioNo`, curriculum đang theo dõi hoặc null, các kỳ đã xếp |
| GET | `/api/me/academic/study-plan/scenarios` | Tóm tắt đủ năm phương án của chương trình đang theo dõi; nếu chưa chọn thì danh sách rỗng |
| GET | `/api/me/academic/study-plan/compare?left=1&right=2` | Đối chiếu hai phương án khác nhau của chương trình đang theo dõi; chỉ đọc, không gọi nguồn |
| PUT | `/api/me/academic/study-plan/curricula/{curriculumId}/courses/{courseId}?scenario=2` | Body `{ "plannedTerm": 1 }`; tạo/chuyển môn, 204 |
| DELETE | Cùng đường dẫn môn | Bỏ assignment của phương án đã chọn, 204 |
| POST | `/api/me/academic/study-plan/scenarios/{targetScenario}/copy` | Body `{ "sourceScenario": 1 }`; chỉ sao chép vào đích trống, 204 |
| DELETE | `/api/me/academic/study-plan/scenarios/{scenarioNo}` | Xóa các assignment của phương án đó, 204 |

`curriculumId` và `courseId` là UUID **nội bộ AMS**, không phải ID nguồn. GET/PUT/DELETE môn không truyền `scenario` vẫn dùng Phương án 1 để giữ hành vi cũ. Phương án chỉ nhận số nguyên 1–5; sai trả `INVALID_STUDY_PLAN_SCENARIO` (400). PUT/DELETE chỉ nhận curriculum hiện được user chọn và môn thực sự thuộc curriculum đó. Khi chưa chọn, GET trả null và danh sách rỗng; mutation trả `CURRICULUM_SELECTION_REQUIRED` (409). Nếu lựa chọn đã đổi, trả `STUDY_PLAN_SELECTION_CHANGED` (409); môn không thuộc chương trình trả `STUDY_PLAN_COURSE_NOT_FOUND` (404). Đích copy không trống trả `STUDY_PLAN_SCENARIO_NOT_EMPTY` (409). Kỳ phải là số nguyên từ 1 đến 99; lỗi trả `INVALID_PLANNED_TERM` (400). Không trả UUID hồ sơ hoặc ID nguồn trong response.

Riêng GET compare cần `left` và `right` là hai số **khác nhau** trong khoảng 1–5; thiếu, rỗng hoặc sai đều trả `INVALID_STUDY_PLAN_COMPARISON` (400). Trình duyệt không gửi user ID hay curriculum ID cho endpoint này. Nếu chưa có hồ sơ hoặc chưa chọn chương trình, response có `curriculum: null`, hai summary bằng 0 và hai danh sách rỗng. Bên trái/bên phải, từng kỳ và từng môn đều được đọc từ cùng một database snapshot bằng transaction `REPEATABLE_READ`; như vậy thao tác cập nhật đồng thời không làm hai bên phản ánh hai thời điểm khác nhau. Danh sách môn sắp theo mã rồi UUID để có thứ tự ổn định. Mỗi bên vẫn có giới hạn 1.000 môn; vượt giới hạn sẽ báo lỗi, không cắt bớt.

V12 thêm `study_plan_course` với khóa ngoại ghép tới `curriculum_course`, bảo vệ cả quan hệ cùng hồ sơ. Từ V13, mỗi môn chỉ có một assignment **trong từng phương án** của một curriculum. PUT lặp cùng kỳ và DELETE lặp đều thành công; hai PUT đồng thời được tuần tự hóa qua hàng hồ sơ và unique constraint ngăn hàng trùng. Không có cascade xóa. GET giới hạn 1.000 môn cho mỗi phương án và báo lỗi nếu vượt, không cắt danh sách rồi giả vờ đã trả đủ.

V13 đưa mọi hàng V12 vào Phương án 1 mà không đổi UUID, kỳ hoặc timestamp cũ. Unique nay tính theo cả `scenario_no`; giới hạn 1.000 môn áp dụng **cho từng phương án**. Copy tạo UUID và timestamp mới cho bản sao. Không có bảng gốc cho phương án vì năm phương án chưa có tên hoặc metadata riêng. Các thao tác copy/clear cũng khóa hàng hồ sơ để không chạy lệch với việc đổi chương trình theo dõi.

Ứng dụng không lưu bản sao điểm, kết quả hay dữ liệu Phenikaa vào bảng này. `planned_term` không trỏ tới `Semester`; planner cũng không tạo `StudentCourse`, `AcademicResult`, sự kiện Google Calendar hoặc thông báo email.
