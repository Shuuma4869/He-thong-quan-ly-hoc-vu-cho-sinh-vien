# Tổng quan dự án

AMS là ứng dụng cá nhân giúp sinh viên quản lý dữ liệu học vụ từ nguồn chính, thay vì nhập lại lịch học và lịch thi trong workflow thông thường.

## Mục tiêu

- Biến dữ liệu học vụ rời rạc thành dashboard và kế hoạch học tập dễ theo dõi.
- Nhận biết thay đổi giữa các lần đồng bộ và giữ audit trail trước/sau.
- Chỉ sửa hoặc xóa Google Calendar event do AMS tạo.
- Gửi thông báo có idempotency, không biến API email thành spam relay.
- Cho phép thay nguồn học vụ mà không viết lại các module GPA, planner và calendar.

## Giới hạn bootstrap

Bootstrap chỉ thiết lập ứng dụng chạy được, design system, API health, database migration, integration contracts, test và CI. Chưa reverse-engineer đăng nhập Phenikaa, chưa tạo Google OAuth credential và chưa gửi email thật.
