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

- `AcademicPortalClient` trả về snapshot đã chuẩn hóa. Business module không phụ thuộc HTML hoặc request riêng của Phenikaa.
- `CalendarGateway` định nghĩa thao tác qua external ID; implementation sau này phải kiểm tra ownership và metadata trước khi sửa/xóa event.
- `EmailNotificationGateway` bắt buộc idempotency key.
- Session/token nhạy cảm sẽ được mã hóa at rest; contract hiện dùng connection ID thay vì truyền token qua business layer.

## Tài khoản và session hiện tại

Frontend gọi `/api/...` cùng origin; Next.js chuyển tiếp tới backend qua `API_INTERNAL_URL`. Spring Security xử lý đăng nhập/đăng xuất, Spring Session lưu session trong Redis, PostgreSQL lưu user và preferences. Trình duyệt chỉ giữ cookie `AMS_SESSION` có `HttpOnly`, không giữ JWT hay mật khẩu trong localStorage.

`auth` phụ trách xác thực và password encoder; `user` quản lý tài khoản, preferences và `/api/me`. Principal mang UUID nội bộ. Backend lấy UUID từ session cho mọi thao tác cá nhân, không lấy user ID do client gửi. Việc kiểm tra trạng thái ACTIVE hiện nằm ở dịch vụ user; module cá nhân mới phải giữ kiểm tra trạng thái và ownership, không chỉ dựa vào việc session đã xác thực.

Frontend kiểm tra `/api/me` ở server tại protected layout và từng protected page. `AuthBoundary` duy trì current-user state, kiểm tra lại khi focus cửa sổ và mỗi phút. Backend vẫn là nơi quyết định quyền truy cập. Khi đăng nhập/đăng xuất, frontend xóa query cache; dữ liệu tài khoản không dùng cache dùng chung giữa request.

Migration V2 mở rộng `app_user` sẵn có và thêm `user_preferences` quan hệ một-một qua UUID. Phase 2 thêm grading policy theo phiên bản và lựa chọn policy ở StudentProfile, chưa có engine tính điểm. Email nhận thông báo chỉ là lựa chọn chưa xác minh; locale được lưu nhưng giao diện hiện vẫn bằng tiếng Việt.

## Dữ liệu và thay đổi

Domain học vụ nằm trong `academic.domain`, change nằm trong `sync.domain`. Không có parser hoặc import adapter trong domain. Các tham chiếu dùng UUID nội bộ; catalog và kết quả được scope theo StudentProfile với composite FK chặn liên kết chéo hồ sơ. Đây là dữ liệu của từng user, chưa phải catalog toàn trường dùng chung.

V3–V5 bổ sung persistence cho học vụ, grading policy, snapshot metadata và schedule change; Hibernate chỉ validate schema. Môn học, lớp mở theo học kỳ, buổi học và kỳ thi là các entity riêng. Buổi học giữ occurrence key không phụ thuộc giờ/phòng, có optimistic locking khi cập nhật. [ERD và các quyết định database](database-model.md) mô tả quan hệ, nullability và giới hạn.

Snapshot DTO hiện là contract chuẩn hóa; metadata và change có thể lưu/đọc qua JPA nhưng chưa có use case import, repository học vụ, API CRUD hoặc engine phát hiện thay đổi. `DetectedAcademicChange` dùng entity UUID thay cho ID từ nguồn. Snapshot/change bất biến ở mapping Hibernate; payload snapshot đầy đủ và việc tính diff/hash chưa triển khai. Ở phase đồng bộ, engine mới đối soát nguồn và tạo change record như `ROOM_CHANGED` hoặc `EXAM_TIME_CHANGED` để các consumer xử lý.
