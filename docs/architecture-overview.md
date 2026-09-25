# Kiến trúc tổng quan

AMS là monorepo gồm Next.js frontend và Spring Boot API. Worker/scheduling được thiết kế chạy ngoài request flow; bootstrap mới có contract `SyncJobDispatcher`, chưa có scheduler hay queue consumer. Backend tổ chức theo feature, chỉ thêm layer khi cần.

Sơ đồ dưới đây là kiến trúc mục tiêu, không phải danh sách tính năng đã hoạt động.

```text
Next.js UI
    |
Spring Boot API ---- PostgreSQL
    |                   |
Sync dispatcher ----- Redis lock/cache
    |
AcademicPortalClient ---- Phenikaa adapter
    |
Change detection ---- CalendarGateway ---- Google Calendar
    |
EmailNotificationGateway ---- Email provider
```

## Ranh giới tích hợp

- `AcademicPortalClient` đọc theo capability: hồ sơ, lịch học, danh sách kỳ lọc lịch thi và lịch thi cá nhân, thay vì ép các API nguồn vào một snapshot lớn. Application truyền UUID user hiện tại và connection ID; không truyền phiên Phenikaa. Kết quả đã chuẩn hóa, không chứa parser hoặc tên trường nguồn.
- `CalendarGateway` định nghĩa thao tác qua external ID; implementation sau này phải kiểm tra ownership và metadata trước khi sửa/xóa event.
- `EmailNotificationGateway` bắt buộc idempotency key.
- Phiên Phenikaa được mã hóa AES-256-GCM khi lưu. Adapter kiểm tra ownership, giải mã trong phạm vi một lần gọi và đóng dữ liệu sau đó. Tính năng mặc định tắt, không có khóa mặc định hoặc API nhận token.

## Tài khoản và session hiện tại

Frontend gọi `/api/...` cùng origin; Next.js chuyển tiếp tới backend qua `API_INTERNAL_URL`. Spring Security xử lý đăng nhập/đăng xuất, Spring Session lưu session trong Redis, PostgreSQL lưu user và preferences. Trình duyệt chỉ giữ cookie `AMS_SESSION` có `HttpOnly`, không giữ JWT hay mật khẩu trong localStorage.

`auth` phụ trách xác thực và password encoder; `user` quản lý tài khoản, preferences và `/api/me`. Principal mang UUID nội bộ. Backend lấy UUID từ session cho mọi thao tác cá nhân, không lấy user ID do client gửi. Việc kiểm tra trạng thái ACTIVE hiện nằm ở dịch vụ user; module cá nhân mới phải giữ kiểm tra trạng thái và ownership, không chỉ dựa vào việc session đã xác thực.

Frontend kiểm tra `/api/me` ở server tại protected layout và từng protected page. `AuthBoundary` duy trì current-user state, kiểm tra lại khi focus cửa sổ và mỗi phút. Backend vẫn là nơi quyết định quyền truy cập. Khi đăng nhập/đăng xuất, frontend xóa query cache; dữ liệu tài khoản không dùng cache dùng chung giữa request.

Migration V2 mở rộng `app_user` sẵn có và thêm `user_preferences` quan hệ một-một qua UUID. Phase 2 thêm grading policy theo phiên bản và lựa chọn policy ở StudentProfile, chưa có engine tính điểm. Email nhận thông báo chỉ là lựa chọn chưa xác minh; locale được lưu nhưng giao diện hiện vẫn bằng tiếng Việt.

## Dữ liệu và thay đổi

Domain học vụ nằm trong `academic.domain`, change nằm trong `sync.domain`. Không có parser hoặc import adapter trong domain. Các tham chiếu dùng UUID nội bộ; catalog và kết quả được scope theo StudentProfile với composite FK chặn liên kết chéo hồ sơ. Đây là dữ liệu của từng user, chưa phải catalog toàn trường dùng chung.

V3–V5 bổ sung persistence cho học vụ, grading policy, snapshot metadata và schedule change; Hibernate chỉ validate schema. Môn học, lớp mở theo học kỳ, buổi học và kỳ thi là các entity riêng. Buổi học giữ occurrence key không phụ thuộc giờ/phòng, có optimistic locking khi cập nhật. [ERD và các quyết định database](database-model.md) mô tả quan hệ, nullability và giới hạn.

Snapshot DTO vẫn là model chuẩn hóa; metadata và change có thể lưu/đọc qua JPA nhưng chưa có engine tạo snapshot hoặc phát hiện thay đổi. `DetectedAcademicChange` dùng entity UUID thay cho ID từ nguồn. Snapshot/change bất biến ở mapping Hibernate; payload snapshot đầy đủ và việc tính diff/hash chưa triển khai. Ở phase đồng bộ, engine mới đối soát nguồn và tạo change record như `ROOM_CHANGED` hoặc `EXAM_TIME_CHANGED` để các consumer xử lý.

## Nhập hồ sơ trong Phase 4B

`ProfileImportService` lấy kết nối của user hiện tại, đọc hồ sơ rồi tạo/cập nhật `StudentProfile`. Chỉ thêm repository cho hai use case đang có: kết nối Phenikaa và hồ sơ sinh viên. Không tạo controller CRUD cho toàn bộ domain.

`PhenikaaAcademicPortalClient` khóa hàng user trong transaction để các lượt nhập của cùng tài khoản không ghi đè nhau hoặc cùng tạo hồ sơ lần đầu. HTTP có timeout và giới hạn response. Chỉ khi nguồn được đọc/kiểm tra đầy đủ mới ghi hồ sơ; lỗi nguồn giữ dữ liệu cũ và lưu mã lỗi an toàn, lỗi ghi database rollback transaction. Chi tiết ràng buộc nguồn, ngắt/kết nối lại và giới hạn giữ khóa trong lúc gọi HTTP nằm trong [tài liệu kết nối](phenikaa-integration.md#phase-4b-kết-nối-mã-hóa-và-nhập-hồ-sơ).

Phiên được cấp qua thao tác nội bộ có kiểm soát, chưa phải production connect flow. API duy nhất thêm cho frontend là đọc trạng thái kết nối khi bật tính năng. Lịch chưa được lưu vì chưa xác minh đủ quan hệ lớp–môn–học kỳ; không có source mapping giả, scheduler hoặc change detection.

## Đọc lịch thi trong Phase 4C

`fetchExamPeriods` trả những kỳ nguồn cho tài khoản hiện tại. `ExamPeriod` chỉ là bộ lọc của nguồn, không phải `Semester.Identifier` đã được xác minh. `fetchExams` đọc lại danh sách kỳ của chính tài khoản trước khi dùng mã kỳ được yêu cầu; nhãn do bên gọi truyền vào không được tin cậy.

`PhenikaaExamItem` kiểm tra đúng người học, kiểu dữ liệu và ngày/giờ trước khi tạo `ExamObservation`. Thời gian được chuyển từ `Asia/Ho_Chi_Minh` sang `Instant`, không phụ thuộc múi giờ máy chạy. Lần thi được giữ riêng, không dùng thay lần học của `StudentCourse`. Kết quả có `completeness=UNKNOWN`; không tạo import service hoặc API CRUD khi chưa đủ cơ sở lưu dữ liệu.

Payload phiên phiên bản 2 thêm ngữ cảnh lịch thi riêng; vẫn đọc được payload phiên bản 1. Phiên bản lớp bảo vệ AES-GCM/AAD vẫn là 1, không đổi khóa hay tự mã hóa lại bản ghi cũ. Thiếu ngữ cảnh lịch thi trả `CONNECTION_UNAVAILABLE` cho khả năng này, không làm hỏng khả năng đọc hồ sơ/lịch học. Cần cấp lại phiên qua quy trình nội bộ đã kiểm chứng để bổ sung ngữ cảnh; không suy từ mã chức năng lịch học.

Hai phần lưu lịch học và lịch thi vẫn bị chặn bởi bằng chứng nguồn, không phải thiếu bảng domain. Chi tiết nằm trong [kết quả Phase 4C](phenikaa-integration.md#phase-4c-lịch-học-và-lịch-thi). Không thay đổi schema, không thêm repository ngoài use case hiện có.

## Đọc kết quả học tập trong Phase 5A

`AcademicPortalClient` thêm khả năng đọc chương trình của tài khoản, danh sách kỳ tra cứu và kết quả học tập. `AcademicProgram` và `AcademicPeriod` là bộ lọc nguồn; tên kỳ không được tự tách để tạo học kỳ AMS. `AcademicRecordObservation` chứa môn, năm học/học kỳ đã đối chiếu, ID đăng ký, số lần học nguồn báo, các điểm thành phần và kết quả cuối môn nếu có. Các kiểu này bất biến và không in dữ liệu học tập trong `toString`.

Adapter kiểm tra chương trình thuộc tài khoản, rồi đọc kết quả và đăng ký học theo request đã quan sát trên UI. `PhenikaaAcademicRecords` đối chiếu toàn bộ phản hồi trước khi trả observation. ID danh sách học trong điểm là ID lớp: phải nối tiếp sang hàng đăng ký đúng người học/chương trình để lấy ID đăng ký thật. Một phép nối thiếu hoặc không duy nhất làm cả lượt đọc thất bại.

Observation giữ mỗi đăng ký nguồn riêng và đặt tên `reportedLearningAttempt` để tránh hiểu đó là số lần học AMS đã xác nhận. `hasAmbiguousLearningAttempts()` phát hiện nhiều đăng ký cùng ID môn và cùng số lần học nguồn báo. Đây là dữ liệu hợp lệ nhưng chưa đủ rõ để lưu, không phải lý do bỏ bớt hàng. Giá trị false cũng không phải một cam kết rằng mọi điều kiện import đã đạt.

Chưa có `AcademicRecordImportService`, source mapping table hoặc migration mới. Lượt kiểm chứng đầy đủ phát hiện xung đột trên; phần persistence thử nghiệm chưa commit đã được bỏ. Chỉ đọc observation không thay đổi `Course`, `Semester`, `StudentCourse` hoặc `AcademicResult`. Không mở API nhận user ID, learner ID hay credential.

Điểm thành phần không biến thành kết quả cuối môn. Outcome chuẩn hóa giữ riêng PASSED, FAILED và RETAKE_REQUIRED theo mã nguồn đã quan sát; chưa ép chúng vào enum domain hoặc tự tính tín chỉ đạt. Một đăng ký có nhiều kết quả cuối cũng chưa có quy tắc chọn hợp lệ; client hiện dừng thay vì tự chọn điểm cao nhất/mới nhất.

Payload phiên bản 3 thêm mã chức năng học tập riêng, vẫn đọc được phiên bản 1 và 2; AAD/khóa không đổi. Phiên cũ thiếu ngữ cảnh học tập chỉ không dùng được khả năng mới, không bị đánh dấu hết hạn. Không có scheduler, distributed lock, importer chương trình đầy đủ, importer lịch hoặc change detection trong 5A.
