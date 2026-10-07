# Kế hoạch học kỳ cá nhân

Trang `/planner` cho phép bạn tự xếp môn của **chương trình đang theo dõi trong AMS** vào “Kỳ kế hoạch 1”, “Kỳ kế hoạch 2”… Đây là thứ tự dự kiến do bạn đặt, không phải học kỳ, đợt đăng ký hoặc thời khóa biểu của nhà trường. Kế hoạch chỉ được lưu trong AMS; bấm xếp môn **không đăng ký học phần với nhà trường**.

Bạn cần chọn chương trình theo dõi ở `/curriculum` trước. Trang kế hoạch có ô tìm theo mã/tên môn và tải thêm theo từng trang; danh sách môn lấy từ curriculum đã lưu trong PostgreSQL, không gọi Phenikaa. Mỗi môn có thể nằm trong một kỳ kế hoạch của chương trình đó. Bạn có thể chuyển kỳ hoặc bỏ môn khỏi kế hoạch; thao tác bỏ không xóa môn khỏi chương trình.

Mỗi card kỳ hiện số môn và **tín chỉ dự kiến trong kỳ**: tổng `curriculum_course.credits` của các môn bạn đã xếp. Nếu dữ liệu chương trình cập nhật tên hoặc số tín chỉ, lần đọc kế tiếp dùng giá trị mới, không dùng bản sao cũ trong kế hoạch. Con số này không phải tín chỉ đăng ký, đã học hay đã đạt. AMS chưa biết môn nào đã hoàn thành, chưa kiểm tra điều kiện tiên quyết hoặc trùng lịch, và không tính tiến độ, GPA, tín chỉ còn thiếu hay điều kiện tốt nghiệp từ kế hoạch.

Đổi chương trình theo dõi chỉ đổi kế hoạch đang xem. Kế hoạch cũ vẫn còn để xem khi bạn chọn lại chương trình đó. Bỏ lựa chọn theo dõi cũng không xóa kế hoạch. Không có bước tự xếp môn theo `recommendedTerm`: trường đó chỉ là gợi ý trong dữ liệu chương trình, không phải một kỳ kế hoạch bạn đã đặt.

## API và lưu trữ

Các endpoint đều cần session AMS; thao tác ghi cần CSRF token:

| Thao tác | Đường dẫn | Kết quả |
| --- | --- | --- |
| GET | `/api/me/academic/study-plan` | `mode=USER_PLANNED_AMS`, curriculum đang theo dõi hoặc null, các kỳ đã xếp |
| PUT | `/api/me/academic/study-plan/curricula/{curriculumId}/courses/{courseId}` | Body `{ "plannedTerm": 1 }`; tạo/chuyển môn, 204 |
| DELETE | Cùng đường dẫn môn | Bỏ assignment, 204 |

`curriculumId` và `courseId` là UUID **nội bộ AMS**, không phải ID nguồn. PUT/DELETE chỉ nhận curriculum hiện được user chọn và môn thực sự thuộc curriculum đó. Khi chưa chọn, GET trả null và danh sách rỗng; mutation trả `CURRICULUM_SELECTION_REQUIRED` (409). Nếu lựa chọn đã đổi, trả `STUDY_PLAN_SELECTION_CHANGED` (409); môn không thuộc chương trình trả `STUDY_PLAN_COURSE_NOT_FOUND` (404). Kỳ phải là số nguyên từ 1 đến 99; lỗi trả `INVALID_PLANNED_TERM` (400). Không trả UUID hồ sơ hoặc ID nguồn trong response.

V12 thêm `study_plan_course` với khóa ngoại ghép tới `curriculum_course`, bảo vệ cả quan hệ cùng hồ sơ. Mỗi môn chỉ có một assignment trong một curriculum. PUT lặp cùng kỳ và DELETE lặp đều thành công; hai PUT đồng thời được tuần tự hóa qua hàng hồ sơ và unique constraint ngăn hàng trùng. Không có cascade xóa. GET giới hạn 1.000 môn và báo lỗi nếu vượt, không cắt danh sách rồi giả vờ đã trả đủ.

Ứng dụng không lưu bản sao điểm, kết quả hay dữ liệu Phenikaa vào bảng này. `planned_term` không trỏ tới `Semester`; planner cũng không tạo `StudentCourse`, `AcademicResult`, sự kiện Google Calendar hoặc thông báo email.
