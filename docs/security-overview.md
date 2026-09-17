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

Bootstrap chưa cung cấp cơ chế đăng nhập production. Không có tài khoản mặc định. Các endpoint ngoài health và OpenAPI yêu cầu authentication; CSRF giữ bật và CORS chỉ nhận origin cấu hình. Profile `prod` tắt OpenAPI và bật secure cookie.

Đã có kiểm thử cho endpoint public, chặn request chưa xác thực, CSRF và CORS. Ownership, rate limiting, mã hóa token, OAuth và security audit pipeline chưa triển khai; bảng audit hiện mới là schema nền. Không triển khai production trước khi hoàn tất và kiểm chứng các phần này.
