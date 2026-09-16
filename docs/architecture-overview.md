# Kiến trúc tổng quan

AMS là monorepo gồm Next.js frontend, Spring Boot API và worker/scheduling chạy ngoài request flow. Backend tổ chức theo feature, mỗi feature chỉ thêm layer cần thiết thay vì tạo sẵn hàng trăm file rỗng.

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
- `CalendarGateway` chỉ thao tác event có external ID và metadata do AMS quản lý.
- `EmailNotificationGateway` bắt buộc idempotency key.
- Session/token nhạy cảm sẽ được mã hóa at rest; contract hiện dùng connection ID thay vì truyền token qua business layer.

## Dữ liệu và thay đổi

Snapshot nguồn được lưu bất biến để so sánh. Change Detection Engine tạo change record như `ROOM_CHANGED` hoặc `EXAM_TIME_CHANGED`; consumer calendar và email xử lý change record thay vì suy đoán lại từ dữ liệu mới.
