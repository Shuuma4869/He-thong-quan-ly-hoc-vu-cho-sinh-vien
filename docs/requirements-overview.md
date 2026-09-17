# Tổng quan yêu cầu

## Nhóm chức năng

1. Dashboard và Sync Center.
2. Hồ sơ học vụ, GPA và tín chỉ.
3. Chương trình đào tạo, prerequisite và course catalog.
4. Lịch học, lịch thi và conflict detector.
5. Semester Planner, Graduation Planner và what-if GPA.
6. Phenikaa sync, change detection, Google Calendar và email notification.

## Yêu cầu phi chức năng

- Responsive trên desktop và mobile; hỗ trợ light, dark và system theme.
- Near-real-time polling có cấu hình nếu nguồn không hỗ trợ push.
- Job đồng bộ idempotent, có lock, retry giới hạn, backoff và trạng thái lỗi.
- PostgreSQL là nguồn dữ liệu chính; Redis chỉ dùng đúng vai trò cache, lock, rate limit và trạng thái tạm.
- Không bypass CAPTCHA, MFA hoặc quyền truy cập của người dùng.
- Không log credential, token, session cookie hoặc encryption key.
