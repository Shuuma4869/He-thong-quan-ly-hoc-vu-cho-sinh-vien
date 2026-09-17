# Thiết lập môi trường phát triển

## Yêu cầu

- Git 2.49 trở lên.
- Java 21; Maven toàn cục không bắt buộc vì có Maven Wrapper.
- Node.js 24 LTS trở lên và pnpm.
- Docker Desktop với Linux containers.

PostgreSQL của AMS dùng cổng host `5433` mặc định để không xung đột với PostgreSQL Windows thường dùng `5432`.

## Khởi động dependency

```powershell
Copy-Item .env.example .env
docker compose up -d
docker compose ps
```

Không commit `.env`. Credential trong `.env.example` chỉ dùng local và phải thay khi triển khai môi trường khác.

## Backend

```powershell
cd backend
.\mvnw.cmd test
.\mvnw.cmd spring-boot:run
```

Chạy backend từ thư mục `backend`: ứng dụng đọc `.env` ở thư mục gốc, biến môi trường hệ điều hành có độ ưu tiên cao hơn. Nếu đổi mật khẩu PostgreSQL/Redis trong `.env`, cập nhật các biến `DATABASE_*` tương ứng; đổi mật khẩu trong Compose không tự đổi mật khẩu của volume PostgreSQL đã tồn tại.

Flyway chạy migration khi ứng dụng kết nối PostgreSQL. `ddl-auto=validate` ngăn Hibernate tự sửa schema. `mvnw.cmd verify` chạy integration test với container riêng, không dùng dữ liệu local.

Compose chỉ mở PostgreSQL và Redis trên `127.0.0.1`. Cấu hình mẫu dành cho local. Profile `prod` yêu cầu credential qua môi trường, bật secure cookie và tắt Swagger; chưa đủ điều kiện triển khai production vì authentication và các integration chưa hoàn tất.

## Frontend

```powershell
cd frontend
Copy-Item .env.example .env.local
pnpm install --frozen-lockfile
pnpm dev
```

Biến `NEXT_PUBLIC_API_URL` trong `frontend/.env.local` xác định backend URL và được chốt tại thời điểm build. Chỉ đưa cấu hình công khai vào biến `NEXT_PUBLIC_*`. Mở frontend tại `http://localhost:3000` để khớp CORS local; nếu đổi origin, cập nhật `CORS_ALLOWED_ORIGINS` rồi khởi động lại backend.

Dashboard gọi `/api/health` bằng TanStack Query, kiểm tra response bằng Zod và hiển thị trạng thái kết nối thực tế. Health này xác nhận API phản hồi; `/actuator/health` kiểm tra thêm dependency.

Kiểm tra giao diện: chạy `pnpm build`, `pnpm exec playwright install chromium`, rồi `pnpm test:e2e`. Playwright tự khởi động frontend production. Unit test và E2E có dữ liệu giả lập riêng để kiểm tra lỗi; dashboard không sử dụng dữ liệu học vụ giả.
