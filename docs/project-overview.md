# Tổng quan dự án

AMS là ứng dụng cá nhân giúp sinh viên quản lý dữ liệu học vụ từ nguồn chính, thay vì nhập lại lịch học và lịch thi trong workflow thông thường.

## Mục tiêu

- Biến dữ liệu học vụ rời rạc thành dashboard và kế hoạch học tập dễ theo dõi.
- Nhận biết thay đổi giữa các lần đồng bộ và giữ audit trail trước/sau.
- Chỉ sửa hoặc xóa Google Calendar event do AMS tạo.
- Gửi thông báo có idempotency, không biến API email thành spam relay.
- Cho phép thay nguồn học vụ mà không viết lại các module GPA, planner và calendar.

## Phạm vi hiện tại

Bootstrap thiết lập design system, API health, database migration, integration contracts, test và CI. Phase 1 bổ sung tài khoản AMS độc lập, đăng ký/đăng nhập/đăng xuất, session Redis, CSRF, thông tin người dùng hiện tại và cài đặt cá nhân. Dashboard và cài đặt yêu cầu đăng nhập.

Phase 2 bổ sung model và persistence cho hồ sơ sinh viên, học kỳ, môn học, chương trình/tiên quyết, lần học/kết quả, lớp mở/buổi học/kỳ thi, snapshot metadata, schedule change và grading policy theo phiên bản. Chi tiết quan hệ, constraint và giới hạn ở [mô hình database](database-model.md).

Chưa triển khai kết nối Phenikaa, Google Calendar, gửi email, xác minh email hoặc đặt lại mật khẩu. Chưa có dữ liệu học vụ thực tế, engine tính điểm/xếp loại hoặc giao diện quản trị tài khoản. Các contract tích hợp chỉ là ranh giới kiến trúc, không phải kết nối đã hoạt động.
