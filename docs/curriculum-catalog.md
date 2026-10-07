# Xem chương trình đào tạo và danh mục môn

Trang `/curriculum` dành cho tài khoản AMS đã đăng nhập. Trang đọc PostgreSQL của AMS, không đọc trực tiếp cổng trường. Vì vậy việc mở trang hoặc bấm **Đọc lại** không làm phát sinh một lượt đồng bộ. Khi dữ liệu đã được importer lưu, trang vẫn hoạt động dù kết nối nguồn đang tắt hoặc hết phiên.

## Những gì màn hình đang nói

**Chương trình** liệt kê các chương trình đã lưu của hồ sơ. Nếu có một chương trình, trang mở ngay phần chi tiết. Nếu có nhiều chương trình, dropdown chỉ chọn bản ghi **đang xem**. Chương trình đầu tiên là mặc định hiển thị theo thứ tự mã và UUID; AMS không tự chọn nó để theo dõi, kể cả khi danh sách chỉ có một chương trình.

Card **Chương trình theo dõi trong AMS** là lựa chọn riêng của người dùng. Khi đang xem chương trình muốn theo dõi, bấm **Đặt làm chương trình theo dõi**; có thể chuyển sang xem chương trình khác mà lựa chọn vẫn giữ nguyên, đổi lựa chọn hoặc bấm **Bỏ chương trình theo dõi**. Thao tác bỏ chọn không xóa chương trình hay môn đã lưu. Đây là mốc cho [kế hoạch học kỳ cá nhân](study-planner.md), **không phải** chương trình hiện hành được Phenikaa xác nhận.

Ba số tóm tắt là số môn có liên kết đã lưu, số nhóm đã lưu và tín chỉ quy định của chương trình. Chúng không nói sinh viên đã học được bao nhiêu. Chẳng hạn chương trình có yêu cầu 120 tín chỉ thì con số 120 vẫn là yêu cầu, không phải tín chỉ tích lũy.

Nhóm giữ phân loại **Bắt buộc** hoặc **Tự chọn** theo dữ liệu đã lưu. Tín chỉ tối thiểu và số môn tối thiểu là hai yêu cầu riêng. Nhóm gồm bốn môn, mỗi môn ba tín chỉ, có thể chỉ yêu cầu chọn sáu tín chỉ; không được thay yêu cầu sáu bằng tổng mười hai. Khi một yêu cầu chưa xác định, màn hình ghi rõ **Chưa xác định**, không hiển thị 0. Giá trị 0 thật trong database vẫn hiển thị 0.

Mỗi môn trong chương trình có mã, tên, tín chỉ, loại yêu cầu, nhóm nếu đã có và **kỳ gợi ý trong dữ liệu chương trình** nếu đã lưu. Kỳ gợi ý không phải học kỳ sinh viên đang học môn đó và không tự xếp môn vào kế hoạch cá nhân. Tín chỉ giữ phần thập phân: 1.5 không bị làm tròn thành 1 hay 2. Khóa và phiên bản chương trình chưa biết cũng không được đoán từ tên chương trình.

**Danh mục môn** chứa mọi Course của hồ sơ, kể cả môn chưa có CurriculumCourse. Nhãn **Chưa có liên kết đã lưu** chỉ nói về dữ liệu AMS đang có. Nó không kết luận môn nằm ngoài chương trình, là tự chọn hoặc không cần học. Một môn có thể có liên kết tới chương trình khác; nhãn “Có liên kết chương trình đã lưu” trong catalog không cam kết liên kết tới chương trình vừa xem.

Không có hồ sơ/chương trình/môn thì trang báo rỗng. Đăng ký AMS không tự nhập học vụ. Trang này không có nút tạo dữ liệu mẫu hoặc cấp phiên Phenikaa; quy trình kết nối/đồng bộ hiện tại vẫn giữ nguyên.

## API dữ liệu đã lưu và lựa chọn theo dõi

Các endpoint sau đều cần session AMS; không nhận user ID hoặc profile ID từ client. Tích hợp Phenikaa không cần bật để sử dụng.

| GET | Nội dung |
| --- | --- |
| `/api/me/academic/curricula` | Trang danh sách chương trình và số môn/nhóm có liên kết |
| `/api/me/academic/curricula/{curriculumId}` | Metadata chương trình và một trang nhóm |
| `/api/me/academic/curricula/{curriculumId}/courses` | Một trang môn có liên kết tới chương trình |
| `/api/me/academic/catalog/courses` | Một trang Course thuộc hồ sơ, gồm cả môn chưa liên kết |
| `/api/me/academic/curriculum-selection` | Chương trình người dùng đã chọn để theo dõi, hoặc null |

`PUT /api/me/academic/curriculum-selection` nhận `{ "curriculumId": "<UUID nội bộ AMS>" }` và trả lựa chọn sau khi lưu. `DELETE` cùng đường dẫn bỏ chọn và trả `curriculum: null`. Cả hai cần CSRF token như các thao tác ghi khác. GET/PUT/DELETE chỉ dùng PostgreSQL; không gọi Phenikaa và không cần kết nối nguồn còn hiệu lực. Một user chưa có StudentProfile nhận GET 200 với null, còn PUT trả 404 và không tự tạo hồ sơ.

Response lựa chọn có `selectionMode: "USER_SELECTED_AMS"` cùng `curriculum` là null hoặc object gồm `id`, `code`, `name`, `cohort`, `revision`, `minimumCredits`. Không có profile ID hoặc ID nguồn. Endpoint đọc thẳng curriculum đã chọn, không phụ thuộc nó có nằm trong trang đầu `/curricula` hay không. `minimumCredits` là **tín chỉ tối thiểu theo chương trình**, không phải tín chỉ còn thiếu.

Danh sách có dạng `{ "items": [...], "nextCursor": null }`. Khi còn trang, `nextCursor` là chuỗi để truyền nguyên vẹn vào request kế tiếp. Chi tiết chương trình có dạng `{ "curriculum": {...}, "groups": { "items": [...], "nextCursor": ... } }`; cursor của endpoint này phân trang **nhóm**, không phải môn.

Mọi endpoint nhận `limit` từ 1–100, mặc định 50, và `cursor` tùy chọn. Mã tăng dần rồi UUID tăng dần là thứ tự cố định. Không có offset hoặc tham số sort động. Khi một mã chương trình trùng nhau và revision chưa biết, UUID vẫn phân biệt được từng chương trình.

Hai endpoint môn nhận thêm `search`: tìm chuỗi con trong mã hoặc tên, không phân biệt hoa/thường. Server bỏ khoảng trắng đầu/cuối và từ chối từ khóa dài quá 100 ký tự. `%`, `_`, `!` là ký tự tìm kiếm bình thường, không dùng làm wildcard; chuỗi luôn được truyền qua tham số SQL. Tìm kiếm không bỏ dấu tiếng Việt. Đổi từ khóa thì bắt đầu lại không có cursor; giao diện làm việc này tự động.

Cursor phiên bản 1 chứa vị trí mã–UUID, không chứa thông tin nguồn. Đây không phải chữ ký cấp quyền hoặc bí mật: client sửa cursor vẫn chỉ đọc được phạm vi hồ sơ của mình. Không dùng cursor của endpoint/bộ lọc khác. Một request có góc nhìn database nhất quán, nhưng cả quá trình nhiều trang không khóa dữ liệu: có thay đổi đồng thời thì bấm đọc lại để duyệt từ dữ liệu mới. Không hứa cursor là bản chụp bất biến.

### Các trường DTO

- Chương trình: `id`, `code`, `name`, `cohort`, `revision`, `minimumCredits`, `courseCount`, `groupCount`.
- Nhóm: `id`, `code`, `name`, `requirement`, `minimumCredits`, `minimumCourseCount`.
- Môn trong chương trình: `id` của liên kết, `courseId`, `code`, `name`, `credits` của liên kết, `requirement`, `groupId`, `groupName`, `recommendedTerm`.
- Môn catalog: `id` của Course, `code`, `name`, `credits` của Course, `curriculumLinked`.

UUID đều là định danh nội bộ AMS. Không trả ID nguồn, selector, profile ID, version Hibernate hay dữ liệu kết nối. `cohort`, `revision`, yêu cầu nhóm và kỳ kế hoạch có thể null; frontend phải giữ nghĩa “chưa xác định”. API không thêm cờ source-current/completed/eligible.

### Lỗi và quyền truy cập

| HTTP | Ý nghĩa |
| --- | --- |
| 401 | Chưa đăng nhập hoặc phiên AMS hết hạn |
| 403 | Tài khoản không còn ACTIVE |
| 400 | UUID/body không hợp lệ, hoặc `INVALID_CATALOG_QUERY` cho limit, cursor, từ khóa của API danh sách |
| 404 | `CURRICULUM_NOT_FOUND`: không tìm thấy chương trình trong phạm vi tài khoản |
| 503 | `CATALOG_UNAVAILABLE`: tạm thời không đọc được database |
| 503 | `CURRICULUM_SELECTION_UNAVAILABLE`: tạm thời không đọc/ghi được lựa chọn |

Lỗi trả thông báo an toàn, không đưa SQL, tên bảng hoặc stack trace cho client. Chương trình của tài khoản khác và UUID không tồn tại đều trả 404. Một tài khoản chưa có StudentProfile nhận danh sách rỗng ở API danh sách, null ở API lựa chọn; GET không tạo hồ sơ. Cookie/session và CSRF hiện có không thay đổi.

## Giao diện và kiểm thử

Tìm kiếm chờ 300 ms sau khi ngừng gõ, có nút xóa từ khóa. Lỗi có nút thử lại; danh sách rỗng và không có kết quả tìm được là hai trạng thái khác nhau. Mỗi danh sách có nút tải thêm khi cần. Đọc lại thông tin chương trình làm mới metadata/nhóm, đọc lại môn làm mới danh sách đang xem. Tải lại trang cũng đọc database mới, không gọi đồng bộ.

Navigation hoạt động trên desktop lẫn thanh dưới của mobile. Layout dùng màu nền/chữ của design system, có dark theme. Input/select có nhãn, trạng thái tải có `role=status`, lỗi có `role=alert`; loại yêu cầu có chữ chứ không chỉ có màu. Có thể dùng bàn phím để chuyển lựa chọn và tìm kiếm.

Integration test chạy PostgreSQL/Redis thật với fixture tổng hợp: hai tài khoản, nhiều chương trình cùng mã, nhóm có/không có yêu cầu, tín chỉ thập phân và môn catalog-only. Test kiểm tra ownership, schema DTO, ký tự tìm kiếm, ranh giới trang và ảnh chụp hàng trước/sau GET để phát hiện ghi ngoài ý muốn. Mock của `AcademicPortalClient` phải không có tương tác. Không kết nối tài khoản trường hoặc đưa dữ liệu thật vào fixture.

E2E đăng nhập bằng backend thật. Kiểm thử đọc danh mục vẫn giả lập response để kiểm tra navigation, nhóm, tìm kiếm, mobile và dark theme; luồng chọn chương trình dùng hai bản ghi tổng hợp tạo qua fixture **chỉ tồn tại trong test runtime**, rồi kiểm tra PUT, đổi A sang B, tải lại, dashboard và DELETE trên PostgreSQL thật. Fixture không được đăng ký trong ứng dụng production. CI không gọi Phenikaa, Google hoặc dịch vụ email.

## Ranh giới chưa thay đổi

Đây là tính năng đọc curriculum/catalog, không phải màn hình kết quả học tập. StudentCourse và AcademicResult chưa có persistence từ nguồn; API kết quả hiện có vẫn đọc trực tiếp. Chưa lưu lịch học/lịch thi, chưa suy tiên quyết, điều kiện đăng ký, điểm trung bình hay mức độ hoàn thành. Google Calendar chỉ có nền kết nối/lịch phụ, chưa dùng Events API; email và Phase 7B chưa bắt đầu. Không mở lại nghiên cứu nguồn chỉ để lấp những giá trị chưa biết trên màn hình này.
