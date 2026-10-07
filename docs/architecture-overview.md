# Kiến trúc tổng quan

AMS là monorepo gồm Next.js frontend và Spring Boot API. Worker tiêu thụ hàng đợi PostgreSQL cho phần dữ liệu được phép nhập. Phase 6B có thêm chính sách tự xếp hàng nhưng mặc định tắt; change detection vẫn chưa có. Backend tổ chức theo feature, chỉ thêm layer khi cần.

Sơ đồ dưới đây là kiến trúc mục tiêu, không phải danh sách tính năng đã hoạt động. Phase 7A chỉ có luồng kết nối/lịch phụ Google riêng, chưa nối vào change detection hoặc worker.

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

## Giao diện tổng quan và đồng bộ

Dashboard là các truy vấn độc lập tới API của tài khoản hiện tại: trạng thái Phenikaa, trạng thái Google Calendar, lượt đồng bộ gần nhất và chương trình được người dùng chọn để theo dõi. Nó không đọc trực tiếp cổng trường hay Google, cũng không tải trang đầu danh sách rồi đoán chương trình được chọn. Chưa có dữ liệu kết quả học tập và lịch được lưu nên dashboard không trình bày GPA, tín chỉ đã hoàn thành, kỳ thi hoặc sự kiện giả.

`/sync` nằm trong protected layout. Trang chỉ bật nút yêu cầu thủ công khi trạng thái Phenikaa là `CONNECTED`; backend vẫn kiểm tra lại kết nối, tài khoản, lượt đang hoạt động và giới hạn tần suất. POST lấy CSRF token theo cơ chế hiện có. Nếu backend trả lại một lượt đang chạy, giao diện theo dõi lượt đó thay vì tạo trạng thái giả. `GET /api/me/sync/current` nghĩa là lượt **gần nhất**, không mặc nhiên là lượt đang chạy. 404 có thể là chưa có lượt hoặc cả endpoint đã tắt theo cấu hình Phenikaa; vì vậy giao diện đọc riêng trạng thái nguồn. Khi tích hợp bị tắt, nó ghi "Không khả dụng" thay vì "Chưa kết nối" và không tải lịch sử.

Lượt `QUEUED` hoặc `RUNNING` được kiểm tra lại qua `/runs/{runId}` khoảng 2,5 giây một lần. Khi trang được tải lại, lượt gần nhất đang hoạt động được dùng để tiếp tục theo dõi. Truy vấn dừng ở `SUCCEEDED`, `PARTIAL` hoặc `FAILED`; nếu mạng lỗi, nó dừng và cho người dùng thử lại, không lặp vô hạn. `PARTIAL` chỉ nói rằng một phần bước thành công, còn toàn lượt chưa hoàn tất: trạng thái hồ sơ và chương trình/danh mục phải được đọc riêng. Mã lỗi được đổi thành lời giải thích an toàn, không đưa exception nội bộ lên màn hình.

Lịch sử lấy 10 lượt mỗi lần qua cursor và nút **Tải thêm**. Khi một lượt thành công hoặc hoàn tất một phần, cache chương trình, danh mục và lịch sử được làm mới. Các query key gắn UUID người dùng; truy vấn chương trình theo dõi trên dashboard có khóa riêng với danh sách phân trang để hai cấu trúc cache không ghi đè nhau. Thời gian hiển thị theo locale và múi giờ của trình duyệt. Giao diện không mở đường cấp phiên Phenikaa, không tự chọn chương trình hiện hành và không tạo sự kiện Google.

## Ranh giới tích hợp

- `AcademicPortalClient` đọc theo capability: hồ sơ, lịch học, danh sách kỳ lọc lịch thi và lịch thi cá nhân, thay vì ép các API nguồn vào một snapshot lớn. Application truyền UUID user hiện tại và connection ID; không truyền phiên Phenikaa. Kết quả đã chuẩn hóa, không chứa parser hoặc tên trường nguồn.
- `CalendarGateway` định nghĩa thao tác qua external ID; implementation sau này phải kiểm tra ownership và metadata trước khi sửa/xóa event.
- `EmailNotificationGateway` bắt buộc idempotency key.
- Phiên Phenikaa được mã hóa AES-256-GCM khi lưu. Adapter kiểm tra ownership, giải mã trong phạm vi một lần gọi và đóng dữ liệu sau đó. Tính năng mặc định tắt, không có khóa mặc định hoặc API nhận token.

## Tài khoản và session hiện tại

Frontend gọi `/api/...` cùng origin; Next.js chuyển tiếp tới backend qua `API_INTERNAL_URL`. Spring Security xử lý đăng nhập/đăng xuất, Spring Session lưu session trong Redis, PostgreSQL lưu user và preferences. Trình duyệt chỉ giữ cookie `AMS_SESSION` có `HttpOnly`, không giữ JWT hay mật khẩu trong localStorage.

`auth` phụ trách xác thực và password encoder; `user` quản lý tài khoản, preferences và `/api/me`. Principal mang UUID nội bộ. Backend lấy UUID từ session cho mọi thao tác cá nhân, không lấy user ID do client gửi. Việc kiểm tra trạng thái ACTIVE hiện nằm ở dịch vụ user; module cá nhân mới phải giữ kiểm tra trạng thái và ownership, không chỉ dựa vào việc session đã xác thực.

Frontend kiểm tra `/api/me` ở server tại protected layout và từng protected page. `AuthBoundary` duy trì current-user state, kiểm tra lại khi focus cửa sổ và mỗi phút. Backend vẫn là nơi quyết định quyền truy cập. Khi đăng nhập/đăng xuất, frontend xóa query cache; dữ liệu tài khoản không dùng cache dùng chung giữa request.

Migration V2 mở rộng `app_user` sẵn có và thêm `user_preferences` quan hệ một-một qua UUID. Phase 2 thêm grading policy theo phiên bản và lựa chọn policy ở StudentProfile, chưa có engine tính điểm. Email nhận thông báo có luồng xác minh và cảnh báo đồng bộ opt-in ở Phase 8A; locale được lưu nhưng email và giao diện hiện vẫn bằng tiếng Việt. [Chi tiết và giới hạn](email-notifications.md).

## Cảnh báo đồng bộ qua email Phase 8A

`user_preferences` giữ email đã chuẩn hóa, thời điểm xác minh và lựa chọn nhận cảnh báo (mặc định tắt). Mã xác minh gắn với user và email, chỉ lưu hash; đổi email làm mất xác minh và tắt cảnh báo. API lấy UUID từ session, không nhận recipient từ client. Resend nằm sau `EmailNotificationGateway`, không được gọi từ controller hay sync worker.

`SyncRunFinalizer` đặt bước kết thúc run và thêm outbox trong cùng transaction. Cả đường chạy thường và phục hồi run stale đều đi qua điểm này; retry còn `QUEUED` không tạo cảnh báo. Worker outbox claim bằng PostgreSQL, gọi provider sau khi claim đã commit rồi cập nhật hàng bằng lease token. Lỗi gửi được giữ trong outbox; trạng thái sync không thay đổi. Chỉ `PARTIAL`/`FAILED` có thể tạo hàng, và unique constraint bảo vệ mỗi run một hàng. Tắt tính năng email không ngăn đồng bộ hoặc giao diện học vụ hoạt động.

## Dữ liệu và thay đổi

### Đường đọc chương trình và danh mục đã lưu (Phase 9A)

`CurriculumController` nhận UUID tài khoản từ principal, không nhận user ID qua request. `CurriculumQueryService` kiểm tra tài khoản còn ACTIVE, tìm StudentProfile của tài khoản rồi gọi `CurriculumReadRepository`. Lớp repository dùng truy vấn SQL có tham số để chiếu các cột cần dùng sang DTO; không serialize entity hoặc gọi importer/adapter. API hoạt động cả khi tích hợp Phenikaa đang tắt.

Mỗi truy vấn đều giới hạn theo profile. Chi tiết và môn của chương trình kiểm tra cả profile lẫn UUID chương trình; UUID của người khác và UUID không tồn tại đều trả 404. Không có hồ sơ thì danh sách rỗng, không tạo hồ sơ trong GET. Tài khoản bị vô hiệu hóa không dùng tiếp phiên cũ để đọc dữ liệu này.

Một request chạy trong transaction chỉ đọc, mức REPEATABLE READ: metadata, số đếm và nhóm thấy cùng một phiên bản database trong request đó. Không có khóa ghi, HTTP ngoài, cập nhật mapping hoặc metadata đồng bộ. Các trang kế tiếp là request độc lập, không phải snapshot kéo dài; khi importer sửa dữ liệu giữa các trang, người dùng có thể đọc lại từ đầu.

Danh sách có giới hạn mặc định 50, tối đa 100; lấy thêm một hàng để biết còn trang sau. Thứ tự mã rồi UUID tạo điểm tiếp tục ổn định, kể cả hai chương trình cùng mã. Số môn/nhóm được đếm trong SQL; nhóm lấy bằng một truy vấn theo chương trình, không phát sinh một truy vấn Java cho mỗi hàng. Môn nối nhóm trong cùng truy vấn; catalog dùng EXISTS để xác định có liên kết đã lưu, không nối sang bảng nguồn. Không lấy toàn bộ catalog rồi lọc trong bộ nhớ.

Các unique index theo profile/mã và index membership hiện có đủ làm nền cho phạm vi này; không thêm V11 hoặc sửa V1–V10. Tìm chuỗi con trên tên/mã có thể cần quét các hàng trong một hồ sơ: giới hạn response không có nghĩa chi phí tìm kiếm luôn cố định. Nếu catalog tăng lớn, cần đo bằng EXPLAIN trước khi thêm index tìm kiếm, không thêm sẵn một hệ thống search riêng.

Frontend `/curriculum` đi qua protected layout và `requireCurrentUser`. Feature dùng TanStack Query với khóa có user UUID, dữ liệu luôn stale khi mount và GET `no-store`. Tìm kiếm có debounce 300 ms, hủy request cũ qua AbortSignal, phân trang “Tải thêm”. Dropdown chỉ đổi chương trình đang xem. Nút theo dõi là thao tác riêng: PUT/DELETE dùng CSRF, cập nhật `student_profile.curriculum_id` trong transaction ngắn và cập nhật cache lựa chọn; nó không gọi nguồn hay làm mới danh sách. [Hợp đồng API và cách hiểu giao diện](curriculum-catalog.md) được ghi riêng để người sửa UI không phải suy nghĩa từ schema.

### Chương trình theo dõi trong AMS (Phase 16A)

`CurriculumSelectionService` kiểm tra tài khoản còn ACTIVE, lấy hồ sơ của user hiện tại và chỉ tìm curriculum trong hồ sơ đó trước khi gọi `selectCurriculum`. UUID của user khác và UUID không tồn tại đều trả 404. GET dùng truy vấn riêng nối `student_profile` với curriculum đã chọn; vì thế lựa chọn vẫn đọc được nếu chương trình nằm ngoài trang danh sách đang tải. Không có hồ sơ hoặc chưa chọn thì trả `curriculum: null`, không tạo hồ sơ và không tự chọn chương trình duy nhất. DELETE bỏ lựa chọn nhưng giữ nguyên chương trình, nhóm và môn.

`USER_SELECTED_AMS` có nghĩa là người dùng tự đặt mốc để các tính năng kế hoạch sau này tham chiếu. Nó không chứng minh chương trình hiện hành của nguồn. `minimumCredits` và các yêu cầu nhóm là mức quy định của chương trình đã lưu, chưa phải tín chỉ còn thiếu. Phase này chưa tạo `StudentCourse`, `AcademicResult`, phép tính điểm hoặc phần trăm tiến độ. Cả ba endpoint chọn/đọc/bỏ chọn chỉ dùng PostgreSQL, kể cả khi tích hợp Phenikaa đang tắt.

### Kế hoạch học kỳ cá nhân (Phase 17A)

`/planner` dùng chương trình người dùng đã chọn làm mốc, nhưng chỉ đọc và ghi dữ liệu AMS trong PostgreSQL. `StudyPlanService` kiểm tra tài khoản ACTIVE và hồ sơ của user hiện tại. GET đọc lựa chọn và các assignment trong một transaction chỉ đọc; nếu chưa chọn thì trả curriculum null và danh sách kỳ rỗng. PUT/DELETE khóa hàng `student_profile` trước khi kiểm tra curriculum đang chọn và môn thuộc chương trình đó. Vì thao tác đổi lựa chọn cũng cập nhật hàng này, hai thao tác được tuần tự hóa; khóa ngoại ghép và unique constraint của V12 là lớp bảo vệ ở database.

`plannedTerm` chỉ là số thứ tự local 1–99, không phải `Semester` hoặc kỳ nguồn. Một môn chỉ có một assignment cho mỗi curriculum; chuyển kỳ cập nhật hàng cũ, bỏ môn chỉ xóa assignment. Đổi hoặc bỏ lựa chọn theo dõi không xóa plan cũ. GET nối curriculum/môn đã lưu để đọc tên, loại yêu cầu và tín chỉ hiện tại; tổng tín chỉ từng kỳ là tổng tín chỉ **dự kiến** của các môn user xếp, không phải tín chỉ đã đạt hay tiến độ. Dữ liệu nguồn và `recommendedTerm` không tự tạo hoặc sửa kế hoạch. API giới hạn 1.000 assignment, vượt giới hạn sẽ báo lỗi thay vì trả thiếu hàng. [Hướng dẫn kế hoạch](study-planner.md) ghi cách dùng và giới hạn.

Frontend tìm môn qua API curriculum đã phân trang, dùng CSRF cho PUT/DELETE, ẩn plan cũ trong lúc đọc lại lựa chọn và không thêm mục thứ sáu vào thanh mobile. Planner không gọi Phenikaa, Google Calendar hay email; không tạo `StudentCourse`, `AcademicResult`, học kỳ hoặc engine tiên quyết/xung đột lịch.

Phase 17B thêm tối đa năm phương án local cho mỗi curriculum. V13 gán mọi assignment cũ vào Phương án 1, rồi đổi unique/index để khóa theo `scenario_no`. Không có bảng `study_plan` riêng vì phương án chưa có metadata. GET kế hoạch và PUT/DELETE môn không truyền scenario vẫn là Phương án 1. GET summary trả năm phương án của curriculum đang theo dõi; copy chỉ nhận target trống, tạo UUID/timestamp mới; clear chỉ xóa assignment của một phương án. Copy/clear đọc lựa chọn trước, khóa `student_profile` rồi kiểm tra lại để không ghi nhầm khi lựa chọn đổi giữa hai bước. Các phương án độc lập, không so với kết quả học tập hoặc nguồn. Frontend dùng query key riêng theo user và scenario, ẩn dữ liệu cũ khi đổi phương án, có xác nhận hai bước trước khi clear.

Phase 17C thêm GET compare cho hai phương án khác nhau (1–5) của curriculum hiện được user chọn. Controller chỉ nhận hai số phương án; service lấy lựa chọn từ `student_profile` của user trong session, rồi đọc hai tập assignment qua repository đã scope theo hồ sơ và curriculum. Cả lựa chọn, metadata và hai tập hàng nằm trong **một transaction chỉ đọc `REPEATABLE_READ`**, không ghép hai HTTP request. Mỗi bên đọc tối đa 1.001 hàng để phát hiện vượt ngưỡng 1.000 thay vì trả danh sách bị cắt. Không có lựa chọn thì trả kết quả rỗng an toàn. Không đổi schema hoặc dữ liệu planner.

Logic so sánh dùng UUID môn nội bộ: cùng kỳ là `UNCHANGED`, đổi kỳ là `MOVED`, chỉ có một bên là `ONLY_LEFT`/`ONLY_RIGHT`. Từng bên và từng kỳ dùng tín chỉ của `curriculum_course` hiện đã lưu; kết quả sắp kỳ tăng dần, môn theo mã rồi UUID. Đây chỉ là đối chiếu `USER_PLANNED_AMS` với `USER_PLANNED_AMS`, không phải đánh giá học vụ, xếp hạng hay xác nhận tiến độ. Frontend chỉ gọi compare sau khi người dùng bấm nút, khóa cache theo user/curriculum/cặp phương án và ẩn kết quả cũ khi cặp, kế hoạch hoặc lựa chọn chương trình đổi. Không gọi Phenikaa, Google Calendar, engine tiên quyết hay lịch; [cách đọc kết quả](study-planner.md#so-sánh-hai-phương-án) dành cho người dùng.

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

## Nhập chương trình và danh mục môn trong Phase 5B

`AcademicPortalClient` thêm ba khả năng riêng: `fetchCurricula` đọc lựa chọn chương trình của tài khoản, `fetchCurriculum` đọc môn/nhóm và `fetchCourseRelations` đọc mô tả điều kiện giữa các môn. Bộ đọc luôn kiểm tra lại chương trình thuộc tài khoản trước khi dùng ID nguồn. Quan hệ môn chỉ được đọc khi môn nằm trong danh mục của chương trình đó; kết quả chưa phải quy tắc đủ điều kiện đăng ký học.

`PhenikaaCurriculumReader` lo request, phân trang có giới hạn và giải mã. `PhenikaaCurriculumMapper` kiểm tra cấu trúc, quyền sở hữu ở dữ liệu nguồn, tín chỉ và quan hệ nhóm rồi tạo observation bất biến. Domain không biết tên trường JSON hoặc đường dẫn API Phenikaa. Các observation không in dữ liệu nguồn qua `toString`.

Luồng ghi hiện tại là `CurriculumImportService` → kết nối của user ACTIVE → hồ sơ của chính user → observation → `PhenikaaCurriculumStore`. Store dùng EntityManager cho entity domain và JdbcTemplate cho ba bảng ánh xạ nguồn có kiểu rõ ràng. Không thêm repository/CRUD riêng cho từng bảng chỉ để thao tác lưu. Importer chưa được mở thành API; không có endpoint nhận user ID hoặc credential do client gửi.

Toàn bộ lượt nhập giữ khóa hàng user trong một transaction, kế thừa cơ chế nhập hồ sơ. Vì vậy, hai lượt nhập cùng user được xử lý lần lượt; constraint trong PostgreSQL là lớp bảo vệ bổ sung. Cách này đủ cho use case hiện tại nhưng giữ khóa cả trong thời gian gọi portal. Giới hạn số request không thay thế thời hạn cho toàn bộ một tác vụ; chưa phù hợp để tự coi đây là worker đồng bộ nền.

Nguồn chưa chứng minh đã trả đủ mọi phiên bản/chương trình, nên observation luôn có `completeness=UNKNOWN`. Import chỉ thêm/cập nhật những gì đã thấy, không xóa hàng vắng mặt, không bỏ lựa chọn curriculum cũ. Môn cùng ID nguồn/mã dùng lại UUID trong phạm vi hồ sơ; trùng mã nhưng khác ID hoặc thay đổi tín chỉ gây lỗi và rollback. Đổi nhóm cũng chưa được tự xử lý. Đây là chủ ý để dữ liệu nguồn chưa rõ không âm thầm thay đổi dữ liệu đã lưu.

Phiên bản 4 của payload mã hóa thêm mã chức năng curriculum, vẫn đọc được phiên bản 1–3. AAD và khóa không đổi; thiếu capability mới không làm phiên cũ bị coi là hết hạn. Không thay đổi đăng nhập AMS, cookie, CSRF hoặc allowlist HTTP.

Không tự chọn chương trình cho hồ sơ vì selector chưa xác nhận đâu là chương trình hiện hành. Không lưu tiên quyết vì mẫu thật có điều kiện điểm tối thiểu, trong khi model hiện chỉ biểu diễn quan hệ từng cặp môn. Không lưu điểm, StudentCourse, Semester, lịch hoặc tạo scheduler. Xem [bằng chứng và giới hạn 5B](phenikaa-integration.md#phase-5b-chương-trình-đào-tạo-danh-mục-môn-và-nhóm-môn).

## Đọc quan hệ điểm trong Phase 5C

`fetchAcademicResultDetail` đọc liên kết giữa một tổng kết và các điểm thành phần. Trước khi gọi endpoint chi tiết, client đọc lại chương trình/điểm/đăng ký của tài khoản để xác nhận ID tổng kết thuộc observation đó. Mapper nối bằng ID thành phần, đối chiếu ngữ cảnh và trả `AcademicResultDetail`; không tự gom thành StudentCourse hoặc ghi AcademicResult.

Năm học và số học kỳ được chuẩn hóa thành `Semester.Identifier`, dùng `T1`, `T2`… làm mã nội bộ. Một học kỳ có thể chứa nhiều kỳ điểm nguồn. Chưa có bảng ánh xạ mới vì chưa có luồng ghi lần học đủ bằng chứng.

Capability mới vẫn đi qua adapter kết nối hiện có, gồm ownership, phiên mã hóa, cập nhật lần truy cập và xử lý session hết hạn. Transaction của adapter còn bao quanh HTTP; phase này chưa sửa giới hạn đó. Khi triển khai importer học vụ, phải tách đọc/kiểm tra nguồn khỏi transaction ghi ngắn và kiểm tra lại quyền trước khi ghi. Không coi bộ đọc hiện tại là importer đã có idempotency/concurrency. Xem [kết quả 5C](phenikaa-integration.md#phase-5c-đối-chiếu-lần-học-và-chi-tiết-kết-quả).

## Ranh giới sau nghiên cứu Phase 5D

Đã đối chiếu thêm các màn hình điểm, khối kiến thức, đăng ký và quy chế đào tạo 2023/2026. `Semester.Identifier` dùng năm học và `Tn` đủ cơ sở cho **học kỳ của bảng điểm**; nhiều kỳ điểm nguồn có thể cùng thuộc một học kỳ. Chưa persist Semester hay ánh xạ kỳ vì không có luồng ghi độc lập cần dùng nó. Kỳ đăng ký và kỳ thi vẫn phải được giải thích riêng.

Quy chế mô tả cách xử lý học lại, cải thiện điểm, tín chỉ và GPA theo từng thời kỳ, nhưng các hàng nguồn hiện không cung cấp khóa lần học xuyên vòng đời, lựa chọn bản ghi kết quả hiện hành và tín chỉ đạt/GPA inclusion của từng bản ghi. Do đó không tạo `StudentCourse` hay `AcademicResult` từ observation, không đổi model hoặc constraint để ép dữ liệu vào. Đặc biệt, `includedInGpa=false` không thể thay cho “chưa biết”. Lịch thi vẫn chưa nối được tới một lần học; lịch học mới nối tới lớp/đăng ký. [Bảng quyết định và bằng chứng Phase 5D](phenikaa-integration.md#phase-5d-kết-luận-nghiên-cứu-ngữ-nghĩa-kết-quả-học-tập) là mốc để xét lại gate này sau khi có nguồn mới.

## API đọc trực tiếp trong Phase 5E

`AcademicSourceController` lấy UUID của user từ session, chuyển cho `AcademicSourceQueryService`, rồi mới gọi `AcademicPortalClient`. Service kiểm tra trạng thái kết nối và tìm chương trình trong danh sách của chính user trước khi đọc điểm. Response giữ nguyên nghĩa “dữ liệu nguồn quan sát được”: số lần học nguồn báo không trở thành `StudentCourse.attemptNumber`; điểm thành phần không bị biến thành kết quả cuối; `UNKNOWN` không bị đổi thành 0 hoặc false.

Client nhận mã tham chiếu dạng băm cho chương trình, đăng ký và chi tiết. Mã này không phải UUID domain hay khóa ổn định lâu dài. Mỗi lần dùng lại, backend đọc danh sách thuộc user hiện tại và chỉ chấp nhận mã khớp một hàng trong đó; không gọi portal bằng ID do browser tự đưa. Chi tiết được đọc lại từ nguồn và đối chiếu điểm thành phần; nếu hai lượt đọc không khớp, API báo lỗi thay vì ghép dữ liệu cũ và mới. Không lưu bản sao kết quả vào PostgreSQL hoặc Redis.

Các request học vụ trực tiếp có giới hạn tần suất ngắn theo user và loại thao tác bằng khóa Redis chỉ chứa UUID nội bộ. Bộ đọc mở transaction ngắn để kiểm tra kết nối/phiên mã hóa, đóng transaction trước khi gọi HTTP, rồi mở transaction ngắn khác để cập nhật trạng thái kết nối. Trước Phase 6A, đường nhập hồ sơ/chương trình còn giữ khóa qua HTTP; phần này đã được tách ở Phase 6A như mô tả bên dưới. [Hợp đồng endpoint và các giới hạn](phenikaa-integration.md#phase-5e-api-đọc-trực-tiếp-và-ranh-giới-nguồn) được ghi ở tài liệu kết nối.

## Giao diện học vụ đọc trực tiếp Phase 13B

`/academic` là trang cần đăng nhập. Giao diện kiểm tra kết nối Phenikaa trước, rồi đọc bảng khả năng ở `/api/me/academic/source/status`. Chỉ khi nguồn báo `ACADEMIC_RECORDS` ở chế độ `LIVE_READ_ONLY` mới tải chương trình và kết quả. Nếu tích hợp bị tắt, kết nối chưa có hoặc cần cấp lại, trang dừng ở thông báo trạng thái; nó không gọi các endpoint nguồn để thử đoán. Khả năng đọc chi tiết được kiểm tra riêng bằng `ACADEMIC_RESULT_DETAIL`.

Người xem chọn **Chương trình đang xem** khi nguồn trả nhiều lựa chọn. Việc chọn không thay đổi hồ sơ hay chương trình hiện hành trong AMS. Giao diện giữ mỗi đăng ký nguồn thành một hàng, không gộp các hàng cùng môn/kỳ/lần học. “Tín chỉ môn” là thuộc tính của môn, không phải tín chỉ đã đạt. Kết quả và điểm thành phần là các giá trị nguồn đang trả về; trang không tính tổng, GPA hoặc suy ra bản ghi nào là kết quả hiện hành. `UNKNOWN` và danh sách rỗng đều được giải thích như giới hạn quan sát, không đổi thành 0 hay “không có”.

Chi tiết tổng kết chỉ được gọi khi người xem mở một hàng. TanStack Query giữ dữ liệu tạm trong bộ nhớ theo user và reference để mở lại không gọi dồn nguồn; không ghi điểm vào localStorage, sessionStorage, Redis hay bảng học vụ. Reference chỉ dùng bên trong request/cache, không đưa vào URL trang hoặc nhãn giao diện. Các truy vấn không tự poll, refetch khi focus hoặc retry lỗi 429. Nút đọc lại là thao tác chủ động, và backend vẫn áp khoảng nghỉ giữa các lượt đọc. Test giao diện/E2E dùng dữ liệu tổng hợp; chúng không xác nhận đọc được một tài khoản Phenikaa thật.

## Tổng hợp tích lũy do nguồn báo Phase 16C

`GET /api/me/academic/source/progress-summary` không nhận ID chương trình từ trình duyệt. Service lấy chương trình người dùng đã chọn trong AMS bằng truy vấn ràng buộc `student_profile.user_id`, nối đúng mapping của hồ sơ và kiểm tra ID đó có mặt trong danh sách chương trình của kết nối nguồn hiện tại. Sau lượt HTTP, service đọc lại lựa chọn/mapping; nếu chúng đã đổi thì không trả kết quả cũ. Adapter còn kiểm tra thế hệ phiên bằng `authenticatedAt`. Các bước DB là truy vấn ngắn, không giữ transaction qua HTTP.

Adapter chỉ dùng endpoint `KetQuaHocTapCaNhan` đã có để đọc bảng tổng hợp. Parser yêu cầu đúng một hàng tích lũy toàn cục cho mỗi thang 4 và 10; thiếu hàng, trùng hàng hoặc kiểu số không đúng đều không biến thành số 0 hay chọn hàng đầu. Giá trị tín chỉ lấy từ hàng thang 10 mà giao diện nguồn dùng cho nhãn tích lũy. Response chỉ có UUID/mã/tên curriculum AMS và ba số nguồn báo, với `mode=SOURCE_REPORTED_LIVE_READ_ONLY`, `completeness=UNKNOWN`. Không trả ID nguồn hay bảng thô, không lưu kết quả và không tính GPA/tín chỉ còn thiếu/phần trăm.

Trang `/academic` đọc lựa chọn curriculum từ PostgreSQL, nhưng chỉ gọi tổng hợp nguồn khi người dùng bấm nút. Cache tạm gắn cả user và UUID curriculum; đổi lựa chọn sẽ ẩn số cũ, yêu cầu bấm đọc lại. Dashboard không gọi endpoint này. Test tự động dùng dữ liệu tổng hợp; chưa xác minh kiểu và giá trị trả về với một kết nối Phenikaa được cấp hợp lệ. Các giới hạn về nhóm, môn, `StudentCourse` và `AcademicResult` vẫn giữ nguyên.

## Lịch học và lịch thi đọc trực tiếp Phase 14A

`/schedule` là trang cần đăng nhập. Trang kiểm tra kết nối Phenikaa và bảng khả năng nguồn trước khi mở thao tác đọc lịch. Kết nối mất hoặc trạng thái nguồn đổi sang cần kết nối lại sẽ chặn request tiếp theo. Người dùng tự chọn khoảng ngày rồi bấm **Đọc lịch**; lịch thi có bước đọc danh sách bộ lọc kỳ thi, chọn một kỳ rồi bấm **Đọc lịch thi**. Chuyển tab hoặc sửa ngày không tự gọi nguồn. Mỗi loại request có cooldown riêng theo user trong Redis; lỗi 429 không tự retry.

Ba endpoint mới của `AcademicSourceController` là `GET /schedule?from=...&through=...`, `GET /exams/periods` và `GET /exams?periodRef=...`, cùng dưới `/api/me/academic/source`. Khoảng lịch cá nhân có 1–31 ngày tính cả hai đầu; lỗi định dạng hoặc vượt khoảng trả `INVALID_SOURCE_RANGE`/400 trước khi gọi nguồn. API so sánh lại ngày, múi giờ và giới hạn số hàng của observation. Kỳ thi chỉ là **bộ lọc nguồn**, không phải Semester AMS. `periodRef` là băm gắn với user và ID kỳ nguồn; khi sử dụng, service lấy lại danh sách của user rồi mới tìm kỳ khớp. Browser không nhận ID nguồn hay các `CandidateIdentity` của buổi/thi.

Response giữ `completeness=UNKNOWN` và `identityScope=UNVERIFIED`. `UNKNOWN` nghĩa là chưa chứng minh nguồn trả đủ bản ghi; `UNVERIFIED` nghĩa là chưa có khóa ổn định cho mỗi buổi hay lần thi. Danh sách rỗng chỉ nói rằng lượt đọc này không trả hàng. Các trường thời gian/phòng/giảng viên có thể null và frontend giải thích chúng bằng chữ, không tạo giờ/phòng giả. Lịch thi chuyển `Instant` về ngày/giờ `Asia/Ho_Chi_Minh` ở backend; lịch cá nhân giữ giờ địa phương nguồn. Hai hàng giống nhau không bị gộp.

Ba phương thức đọc của Phenikaa adapter dùng `accessRead`: transaction ngắn lấy và giải mã phiên, HTTP ngoài transaction, transaction ngắn cập nhật trạng thái với kiểm tra `authenticatedAt`. API không lưu lịch/thi vào PostgreSQL, Redis hay browser storage; chưa tạo `ClassSession`, `Exam`, snapshot, change record, Google event hoặc thông báo lịch. `IDLICHHOC` từng lặp qua nhiều ngày, nên việc đọc được trang không tháo gỡ chặn về định danh buổi và phát hiện thay đổi. Test tự động dùng dữ liệu tổng hợp; chưa kiểm chứng API mới với một kết nối Phenikaa được cấp hợp lệ.

## Hạ tầng đồng bộ Phase 6A

`POST /api/me/sync` chỉ tạo một `SyncRun` trạng thái `QUEUED` trong PostgreSQL và trả `202`; user bấm lại khi đang `QUEUED` hoặc `RUNNING` sẽ nhận cùng lượt, không tạo hàng mới. API trạng thái chỉ đọc database và chỉ cho xem lượt của chính user. Chưa có màn hình Sync Center hoặc lịch tự động tạo lượt theo từng tài khoản. Tính năng chỉ có khi bật kết nối Phenikaa và đã có kết nối được cấp hợp lệ; đăng nhập vào cổng trường trong trình duyệt không tự cấp kết nối AMS.

Worker poll các hàng đến hạn và claim bằng một lệnh SQL có `FOR UPDATE SKIP LOCKED`, nên hai backend không lấy cùng một hàng. PostgreSQL giữ trạng thái lâu bền; Redis chỉ giữ khóa theo UUID user và cooldown cho yêu cầu thủ công. Khóa có token ngẫu nhiên, TTL và thao tác gia hạn/nhả có so khớp chủ sở hữu. Trong lúc gọi nguồn, heartbeat gia hạn khóa và cập nhật thời điểm sống của run. Nếu process chết, khóa hết hạn; worker khác chỉ phục hồi `RUNNING` đã quá hạn sau khi xác nhận không còn khóa. Nếu worker cũ tỉnh lại, kiểm tra attempt của run trong transaction ghi sẽ ngăn nó ghi sau lượt mới.

Một lượt chạy hai bước: nhập hồ sơ, sau đó nhập mọi lựa chọn chương trình mà nguồn trả về (tối đa tám để có giới hạn tải). Mỗi bước dùng importer đã có, giữ UUID và quy tắc không xóa dữ liệu nguồn vắng mặt. Bộ đọc HTTP của hai importer được tách khỏi transaction ghi. Sau khi đọc, transaction ngắn khóa user, kiểm tra lại kết nối/phiên và quyền sở hữu trước khi lưu. Metadata `lastSuccessfulAccessAt` đo lần **đọc nguồn** thành công; nếu ghi database sau đó thất bại, dữ liệu học vụ rollback nhưng thời điểm đọc nguồn vẫn được giữ. Đây là thay đổi có chủ ý so với transaction dài trước 6A.

Lỗi tạm thời (`TIMEOUT`, `NETWORK_ERROR`) được xếp lại cùng run, tối đa ba attempt với backoff tăng dần và không ngủ trong transaction. Hết phiên, schema đổi hoặc lỗi nghiệp vụ không retry. `PARTIAL` chỉ có nghĩa hồ sơ đã ghi thành công nhưng bước chương trình thất bại cuối cùng; nó không phải đánh giá độ đầy đủ của nguồn. Dữ liệu cũ không bị xóa khi refresh lỗi. Job chỉ giữ UUID user và metadata an toàn, không giữ phiên, payload hoặc ID người học. Điểm, lịch học và lịch thi vẫn nằm ngoài worker.

Log và metrics chỉ dùng run UUID, trạng thái, số attempt và mã lỗi an toàn; metrics không gắn user/run ID làm tag. Tại mốc Phase 6A, lịch sử run chưa được dọn; Phase 6B bổ sung chính sách này bên dưới. [Schema V8](database-model.md#lượt-đồng-bộ-phase-6a) và [cấu hình local](development-setup.md#worker-đồng-bộ) có chi tiết để tiếp tục phát triển.

## Vận hành đồng bộ Phase 6B

Tự xếp hàng theo lịch là một công tắc **riêng** với worker. Mặc định `auto-enabled=false`; khi bật, mỗi lần quét chỉ chọn tối đa bốn tài khoản ACTIVE có kết nối CONNECTED và không có run đang chờ/chạy. Scheduler đọc metadata trong database, không giải mã phiên nguồn và không gọi Phenikaa. PostgreSQL khóa hàng user trong lúc chọn rồi tạo run `SCHEDULED` trong cùng transaction ngắn. Instance khác bỏ qua hàng đang khóa; unique index của V8 là lớp bảo vệ cuối. Nếu hai lượt quét cùng chọn một tài khoản, lượt gặp xung đột insert dùng lại run đã có nhưng không được tính là vừa tạo thêm run; số trả về, metric và log chỉ đếm hàng thực sự được insert. Redis không tham gia quyết định đến hạn, nên sự cố Redis không làm scheduler tạo hàng trùng hay giữ lịch chạy ở một nơi khác.

Sau một lượt kết thúc thành công, lượt kế tiếp đến hạn theo thời điểm **kết thúc** cộng chu kỳ mặc định 24 giờ. Sau `FAILED` hoặc `PARTIAL`, thời gian chờ mặc định cũng là 24 giờ, tránh tạo run mới ở mỗi lần quét khi nguồn đang lỗi. Với kết nối chưa từng có run, lần đầu đến hạn sau thời điểm tạo kết nối cộng một chu kỳ và một khoảng lệch ổn định từ UUID nội bộ. Khoảng lệch này trải yêu cầu ban đầu ra thay vì dồn cùng lúc; nếu rất nhiều tài khoản đã quá hạn sau khi khởi động lại, batch bốn tài khoản mỗi lần quét năm phút vẫn giới hạn tốc độ xếp hàng. Hai importer và worker giữ nguyên phạm vi PROFILE → CURRICULUM, chạy tuần tự trong từng instance; Redis lock/heartbeat và retry Phase 6A không đổi.

`GET /api/me/sync/runs` đọc lịch sử của chính user, mặc định 20 hàng/trang, tối đa 100. Con trỏ trang tiếp theo mã hóa thời điểm yêu cầu và UUID run cuối trang; backend kiểm tra run mốc vẫn thuộc user trước khi dùng. Cách này gọi là *phân trang theo khóa*: trang sau lấy các hàng cũ hơn mốc, nên không phải đi qua offset ngày càng lớn. Con trỏ không phải token phân quyền; nếu mốc đã bị dọn theo retention, client bắt đầu lại từ trang đầu. `/current` vẫn có nghĩa là lượt **gần nhất**, không nhất thiết đang chạy.

Lịch sử `sync_run` chỉ giữ 90 ngày mặc định. Mỗi lượt dọn xóa tối đa 100 run `SUCCEEDED`, `PARTIAL` hoặc `FAILED` có `finished_at` cũ hơn ngưỡng; không đụng `QUEUED`/`RUNNING`, kể cả run quá hạn cần worker phục hồi. Nhiều instance dọn đồng thời vẫn an toàn vì SQL khóa từng batch và bỏ qua hàng đã bị instance khác giữ. Nếu không còn run nào sau dọn, `/current` trả `RUN_NOT_FOUND` như trước. Lịch sử này phục vụ theo dõi đồng bộ, **không phải** audit log bảo mật giữ lâu dài. Metrics chỉ ghi số run tự xếp hàng và số hàng dọn, không gắn user ID.

Schema nền `AcademicSnapshotMetadata` và `ScheduleChange` vẫn tồn tại nhưng chưa có pipeline tạo snapshot hoặc change. Identity của buổi học và quan hệ lịch thi với lần học chưa đủ chắc chắn để phát hiện `ROOM_CHANGED`, `EXAM_CHANGED` hay thay đổi điểm ở mức entity. Phase 6B không ghi StudentCourse, AcademicResult, ClassSession, Exam hoặc change record từ nguồn; [bằng chứng còn thiếu](phenikaa-integration.md#phase-5d-kết-luận-nghiên-cứu-ngữ-nghĩa-kết-quả-học-tập) phải được giải quyết trước khi mở các khả năng đó.

## Nền kết nối Google Calendar Phase 7A

Frontend `/settings` gọi các endpoint `/api/me/connections/google-calendar/...` bằng session AMS và CSRF. Backend tách ba phần: `GoogleCalendarService` điều phối trạng thái; OAuth gateway dùng Spring Security OAuth2 Client để đổi code/refresh; Calendar gateway chỉ gọi API tạo/đọc **lịch phụ**. Redis giữ state OAuth dùng một lần và khóa thao tác ngắn; PostgreSQL giữ kết nối, token mã hóa và ID lịch. Không có event worker hoặc đường từ dữ liệu học vụ sang Google.

Các lệnh SQL ngắn hoàn tất trước/sau HTTP; không giữ transaction database trong lúc chờ Google. Khi đổi code xong nhưng chưa lưu token mà process chết, người dùng bắt đầu lại OAuth. Khi token đã lưu nhưng chưa tạo được lịch, trạng thái `SETUP_REQUIRED` cho phép thử lại. Khi Google đã tạo lịch nhưng process chết trước lúc lưu ID, có thể còn lịch phụ mồ côi; không mở rộng scope để quét mọi lịch chỉ để xử lý trường hợp hiếm này. [Luồng, lỗi và cách cấu hình](google-calendar-integration.md) mô tả ranh giới đó.
