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

## Dữ liệu và thay đổi

Ở phase đồng bộ, snapshot nguồn sẽ được lưu bất biến để so sánh. Change Detection Engine sẽ tạo change record như `ROOM_CHANGED` hoặc `EXAM_TIME_CHANGED`; consumer calendar và email xử lý change record thay vì suy đoán lại từ dữ liệu mới. Bootstrap mới định nghĩa kiểu snapshot và change, chưa lưu snapshot hoặc chạy đồng bộ.
