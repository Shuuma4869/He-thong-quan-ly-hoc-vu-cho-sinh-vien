# Tổng quan bảo mật

Security là yêu cầu cấp một của AMS. OWASP ASVS được dùng làm tài liệu kiểm tra chính.

## Nguyên tắc nền

- Session production dùng cookie `HttpOnly`, `Secure` và `SameSite` phù hợp; state-changing request phải có CSRF protection.
- Authorization được enforce ở backend, ưu tiên `/api/me/...` và kiểm tra ownership để ngăn IDOR/BOLA.
- Host tích hợp được allowlist; không cung cấp endpoint fetch URL tùy ý.
- Refresh token, Phenikaa session và dữ liệu tương tự phải mã hóa bằng authenticated encryption như AES-256-GCM.
- Không log password, authorization header, token, session cookie, OAuth secret hoặc encryption key.
- Endpoint login, reconnect, manual sync và test email cần rate limit; mỗi user chỉ có một sync job cùng loại đang chạy.

## Production boundary

Cloudflare dự kiến đứng trước origin để cung cấp TLS, WAF, DDoS mitigation và edge rate limiting. PostgreSQL và Redis không public trực tiếp. Origin chỉ tin forwarded headers từ proxy được cấu hình rõ.

Profile `prod` tắt OpenAPI và bắt buộc cookie `Secure`; frontend phải được phục vụ qua HTTPS cùng origin với proxy `/api`. Cookie `AMS_SESSION` luôn có `HttpOnly`, `SameSite=Lax`, `Path=/` và không mở rộng domain. Không tắt `Secure` để chữa lỗi đăng nhập production. `CORS_ALLOWED_ORIGINS` chỉ nhận các origin tin cậy được cấu hình, không dùng wildcard với credential.

## Tài khoản AMS

- UUID là định danh nội bộ; email không phải primary key. Email được chuẩn hóa chữ thường và có unique constraint trong PostgreSQL.
- Đăng ký luôn tạo `STUDENT`, trạng thái `ACTIVE`; client không chọn role hoặc trạng thái. Có role `ADMIN` nhưng không tạo admin mặc định, chưa có API quản trị.
- Password dùng BCrypt cost 12, dài 12–64 ký tự và không quá 72 byte UTF-8 để tránh bị cắt bởi BCrypt. Backend kiểm tra cả định dạng email và độ dài password; không trả password/hash trong DTO, không ghi request chứa password vào log.
- Đăng nhập sai trả thông báo chung cho email không tồn tại và password sai. Đăng ký email trùng trả 409; đây vẫn là tín hiệu tồn tại tài khoản, cần cân nhắc lại khi có luồng xác minh email.
- Spring Security thay session ID khi đăng nhập và xóa thông tin credential khỏi authentication lưu trong session. Spring Session lưu session phía server trong Redis, namespace `ams:session`, idle timeout mặc định 30 phút (`SESSION_TIMEOUT`). Các request kiểm tra current user cũng duy trì session; chưa có absolute timeout.
- Logout vô hiệu hóa session phía server. Không dùng JWT hoặc lưu password/session token trong localStorage. Theme cục bộ có thể dùng localStorage nhưng không chứa thông tin xác thực.
- `/api/me` và settings lấy UUID từ principal; service kiểm tra tài khoản ACTIVE trong database. Dữ liệu người A không đọc/sửa được bằng cách gửi ID người B. Các module cá nhân bổ sung sau này phải thực hiện cùng kiểm tra này.

## API và CSRF

| Endpoint | Hành vi |
| --- | --- |
| `GET /api/auth/csrf` | Public; tạo/lấy CSRF token gắn session, trả `token` và `headerName` |
| `POST /api/auth/register` | JSON `email`, `password`, `displayName`; 201, không tự đăng nhập; 400 khi validation lỗi, 409 khi trùng email |
| `POST /api/auth/login` | Form URL-encoded `email`, `password`; 204 hoặc 401 |
| `POST /api/auth/logout` | 204; hủy session |
| `GET /api/me` | User hiện tại kèm settings, không trả hash; 401 nếu chưa xác thực, 403 nếu tài khoản bị vô hiệu hóa |
| `PUT /api/me/settings` | JSON `notificationEmail`, `timezone`, `locale`, `theme`; chỉ cập nhật tài khoản trong session |

Mọi POST/PUT ở trên phải kèm cookie session và header `X-CSRF-TOKEN` lấy từ endpoint CSRF. Thiếu hoặc sai token trả 403, kể cả đăng ký/đăng nhập/đăng xuất. Token đổi sau đăng nhập và đăng xuất; frontend lấy token mới trước mỗi thao tác ghi, không tự lặp lại thao tác ghi nếu thất bại. CSRF mặc định của Spring Security vẫn bật; CORS và SameSite không thay thế CSRF.

Frontend dùng kiểm tra session phía server cho protected pages và current-user state phía client. Response API cá nhân có `Cache-Control: no-store`; server fetch không cache dùng chung. Kết nối lỗi không được coi là đăng nhập thành công.

## Kiểm chứng và giới hạn

Integration test dùng HTTP thật, PostgreSQL và Redis riêng để kiểm tra đăng ký, password hash, login/logout, session fixation, replay sau logout, CSRF, current user, ownership, validation và cookie production. E2E đi qua Next.js proxy và giao diện thật, gồm tải lại trang và lưu settings. Migration được kiểm tra trên database mới và nâng cấp từ V1.

Email tài khoản và email nhận thông báo chưa được xác minh; không có nhãn xác minh giả, password reset hoặc email provider. Locale chỉ lưu lựa chọn, chưa đổi toàn bộ ngôn ngữ giao diện. Phase 2 có model chính sách điểm nhưng chưa tính GPA/xếp loại.

Học vụ được phân vùng theo StudentProfile liên kết với user. FK ghép chặn liên kết chéo hồ sơ, nhưng không thay thế authorization: use case học vụ sau này phải tìm hồ sơ từ user trong session và scope mọi truy vấn theo hồ sơ đó. Hiện chưa có API học vụ. Snapshot metadata và change chỉ lưu dữ liệu chuẩn hóa, không lưu raw credential/session hoặc response nguồn.

Chưa có rate limiting đăng ký/đăng nhập, MFA, absolute session timeout hay quy trình quản trị/recovery. Bảng audit hiện mới là schema nền, chưa ghi sự kiện. OAuth và mã hóa token tích hợp chưa triển khai vì chưa có kết nối ngoài. Cần bổ sung và đánh giá các biện pháp bảo vệ phù hợp trước khi mở đăng ký trên internet; test Phase 1 không thay thế đánh giá bảo mật production.
