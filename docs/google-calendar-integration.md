# Kết nối Google Calendar (Phase 7A)

Phase này cho phép một người dùng AMS cấp quyền Google và tạo một **lịch phụ riêng cho AMS**. Chưa có dữ liệu học vụ nào được đưa lên lịch: không tạo, sửa hay xóa sự kiện. Việc kết nối cũng không thay thế đăng nhập AMS. Người dùng phải đăng nhập AMS trước, rồi mới bấm kết nối Google trong `/settings`.

Tính năng tắt mặc định. CI dùng cổng Google giả, không gửi request tới tài khoản Google thật. Tính đến 01/10/2026, luồng OAuth với tài khoản Google thật chưa được kiểm chứng vì chưa có OAuth client local được cấu hình. Trạng thái `CONNECTED` trong kiểm thử chỉ chứng minh luồng qua mock, không phải xác nhận đã có lịch trên một tài khoản thật.

## Chuẩn bị để chạy local

1. Tạo OAuth client loại **Web application** trong Google Cloud Console của chính bạn. Cấu hình màn hình xin quyền và tài khoản thử nghiệm theo chính sách của Google. Bật Google Calendar API cho project đó.
2. Thêm redirect URI chính xác `http://localhost:3000/api/integrations/google-calendar/callback` vào OAuth client. URI này đi qua proxy `/api` của Next.js tới backend. Nếu chạy qua một HTTPS origin khác, thay bằng cùng đường dẫn trên origin đó và cấu hình proxy tương ứng. Không dùng URI động do trình duyệt gửi.
3. Trong `.env` local (không commit), đặt `AMS_GOOGLE_CALENDAR_ENABLED=true`, `AMS_GOOGLE_CLIENT_ID`, `AMS_GOOGLE_CLIENT_SECRET`, `AMS_GOOGLE_REDIRECT_URI`. Tạo **khóa riêng** 32 byte ngẫu nhiên, mã hóa Base64 và đặt vào `AMS_GOOGLE_TOKEN_KEY`; giữ `AMS_GOOGLE_KEY_VERSION=1`. Khóa này không được dùng chung với khóa phiên Phenikaa. Không đặt credential trong `.env.example`, source, issue, screenshot hoặc log.
4. Khởi động PostgreSQL, Redis, backend và frontend theo [hướng dẫn môi trường](development-setup.md). Đăng nhập AMS, mở `/settings`, chọn **Kết nối** và hoàn tất màn hình cấp quyền Google. Không cần gửi mật khẩu hay token cho người phát triển khác.

Chỉ bật trên môi trường đã có HTTPS, reverse proxy cùng origin và nơi lưu secret an toàn khi triển khai thật. Ứng dụng OAuth public có thể cần hoàn tất màn hình consent và quy trình xác minh của Google tùy scope/trạng thái xuất bản; cấu hình local chưa đủ để gọi là sẵn sàng production. Cookie AMS production phải là `Secure`/`HttpOnly`; callback cần nhận chính cookie session đã bắt đầu OAuth. Đổi khóa mã hóa hoặc tăng `AMS_GOOGLE_KEY_VERSION` mà chưa có quy trình mã hóa lại sẽ khiến token cũ không giải mã được; hiện **chưa có công cụ xoay khóa**. Sao lưu và quản lý khóa như một secret vận hành.

## Luồng kết nối

`POST /api/me/connections/google-calendar/authorize` cần session AMS và CSRF. Backend tạo `state` ngẫu nhiên dùng một lần, lưu trong Redis 10 phút cùng UUID người dùng, version hàng kết nối và PKCE verifier. Response chỉ trả URL cấp quyền Google. Frontend kiểm tra URL này thuộc `accounts.google.com` trước khi chuyển trang.

Google gọi lại `GET /api/integrations/google-calendar/callback`. Backend yêu cầu **cùng session AMS**, tiêu thụ `state` một lần, kiểm tra UUID và version, rồi mới đổi authorization code lấy token. Code không xuất hiện trong HTML hoặc URL trả về: callback chỉ chuyển về `/settings?google=...` với mã kết quả cố định. Request và redirect không được cache; redirect đặt `Referrer-Policy: no-referrer`. Người dùng từ chối cấp quyền thì không tạo kết nối.

AMS chỉ yêu cầu scope `https://www.googleapis.com/auth/calendar.app.created`, `access_type=offline` và PKCE S256. Khi kết nối lại sau ngắt/hết quyền, AMS yêu cầu Google hiện lại màn hình consent để có cơ hội nhận refresh token mới. Google có thể không cấp refresh token; trường hợp đó AMS báo cần kết nối lại, không giả định access token ngắn hạn là một kết nối bền vững. Nếu một kết nối cũ còn token hợp lệ, lỗi lần cấp quyền mới không ghi đè token cũ.

Token truy cập và refresh token nằm chung trong một gói AES-256-GCM với nonce ngẫu nhiên, gắn AAD gồm mục đích, phiên bản khóa, connection UUID và user UUID. Bảng chỉ lưu ciphertext, thời điểm hết hạn và metadata; API trạng thái không trả token, Google email hoặc calendar ID. Access token được làm mới trước hạn một phút. Khóa Redis theo user giảm khả năng nhiều instance cùng refresh; cập nhật database dùng version để không ghi đè lẫn nhau. `invalid_grant` chuyển sang `RECONNECTION_REQUIRED` và xóa token đã lưu.

Sau khi lưu token, backend thử đọc `calendar_id` đã biết; nếu lịch còn truy cập được thì dùng lại. Nếu chưa có hoặc Google trả 404, backend tạo lịch phụ tên **AMS - Lịch học vụ**, múi giờ `Asia/Ho_Chi_Minh`, rồi lưu ID. Không tìm/xóa lịch theo tên và không gọi lịch `primary`. Chỉ khi lưu được ID mới báo `CONNECTED`. Lỗi tạm thời để lại `SETUP_REQUIRED`; người dùng có thể bấm **Hoàn tất thiết lập** mà không cấp quyền lại. Nếu process chết sau khi Google tạo lịch nhưng trước lúc lưu ID, lần thử lại có thể tạo thêm một lịch phụ không được AMS quản lý. Không có transaction chung giữa Google và PostgreSQL; không xin scope rộng hơn chỉ để tìm/xóa lịch mồ côi. Đây là giới hạn cần vận hành thủ công nếu xảy ra.

`POST /api/me/connections/google-calendar/disconnect` thử thu hồi refresh token ở Google rồi xóa token local và đặt `DISCONNECTED`, kể cả khi Google không xác nhận thu hồi. API trả `remoteRevocationConfirmed` để phân biệt hai việc đó. Lịch phụ đã tạo **không bị xóa**; user có thể tự quản lý nó trong Google Calendar. Kết nối lại sẽ dùng lại ID đã lưu nếu còn truy cập được, nếu không sẽ tạo lịch phụ mới. Các request Google có connect timeout 5 giây, read timeout 8 giây và không theo redirect. Không retry tự động lệnh tạo lịch: khi timeout, phía Google có thể đã tạo lịch dù AMS chưa nhận phản hồi.

## Trạng thái và lỗi

`GET /api/me/connections/google-calendar` trả `available=false` khi tính năng tắt. Khi bật, bốn trạng thái là `DISCONNECTED`, `SETUP_REQUIRED`, `CONNECTED` và `RECONNECTION_REQUIRED`. Các thao tác ghi dùng POST và CSRF hiện có; UUID user luôn lấy từ session, không có tham số user ID. Lỗi trả mã cố định như `GOOGLE_STATE_INVALID`, `GOOGLE_UNAVAILABLE`, `GOOGLE_TOKEN_EXCHANGE_FAILED` hoặc `GOOGLE_CALENDAR_SETUP_FAILED`, không đưa response Google/token ra trình duyệt.

Trạng thái kết nối chỉ nói về OAuth và lịch phụ. Nó **không** nói rằng lịch học/lịch thi trong AMS đã có identity đủ chắc chắn để đồng bộ. Các giới hạn nguồn Phenikaa vẫn giữ nguyên; Phase 7A không thêm `StudentCourse`, `AcademicResult`, `ClassSession` hoặc `Exam` từ nguồn và không gọi `events.insert`, `events.patch`, `events.delete`.

## Kiểm chứng và tài liệu gốc

Unit test kiểm tra mã hóa, state, PKCE, lỗi HTTP và tạo/đọc lịch qua HTTP giả. Integration test dùng PostgreSQL/Redis Testcontainers, kiểm tra migration V9→V10, ownership, CSRF, replay, thiếu refresh token, refresh đồng thời, ngắt kết nối và setup lại. Frontend có test component/API và E2E chặn Google URL, không cần credential thật. `mvnw.cmd verify` và các lệnh frontend trong README là mốc kiểm thử local/CI; live OAuth phải được kiểm tra riêng sau khi người vận hành tự cấu hình client.

Tài liệu Google đã đối chiếu: [OAuth cho web server](https://developers.google.com/identity/protocols/oauth2/web-server), [danh sách scope Calendar](https://developers.google.com/workspace/calendar/api/auth), [tạo lịch](https://developers.google.com/workspace/calendar/api/v3/reference/calendars/insert), [đọc lịch](https://developers.google.com/workspace/calendar/api/v3/reference/calendars/get). OAuth client dùng [Spring Security OAuth2 Client](https://docs.spring.io/spring-security/reference/servlet/oauth2/index.html) cho bước đổi code và refresh token, thay vì tự viết phần token exchange. Hai lệnh Calendar đơn giản dùng REST sẵn có của Spring, không thêm một Google SDK lớn chỉ cho thao tác tạo/đọc lịch.
