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

Compose chỉ mở PostgreSQL và Redis trên `127.0.0.1`. Cấu hình mẫu dành cho local. Profile `prod` yêu cầu credential qua môi trường, bật secure cookie và tắt Swagger. Authentication đã có; rate limiting và đánh giá bảo mật triển khai vẫn chưa hoàn tất, không coi cấu hình local là cấu hình production.

`SESSION_TIMEOUT` mặc định `30m`, tính từ lần sử dụng session gần nhất. Redis phải hoạt động để duy trì đăng nhập. Tài khoản AMS đăng ký từ giao diện, không dùng tài khoản Phenikaa và không có admin mặc định.

V2 chuẩn hóa email về chữ thường, bổ sung password hash/trạng thái và preferences. User có từ V1 được giữ UUID, chuyển sang `DISABLED` với giá trị hash không dùng để đăng nhập (`!`). Nếu email cũ trùng nhau sau chuẩn hóa hoặc có role `SYSTEM`, migration sẽ dừng và rollback; cần kiểm tra dữ liệu trước khi nâng cấp, không tự xóa hoặc đổi quyền các bản ghi đó. V1 không bị sửa, Hibernate vẫn chỉ validate schema.

V3–V5 thêm schema học vụ, không tự tạo hồ sơ sinh viên hay seed dữ liệu. `mvnw.cmd verify` kiểm tra database sạch và nâng cấp từ V2, mapping JPA và constraint bằng PostgreSQL thật trong Testcontainers. Phase 2 chưa có API nhập/sửa học vụ; xem [database model](database-model.md) trước khi thêm use case.

## Frontend

```powershell
cd frontend
Copy-Item .env.example .env.local
pnpm install --frozen-lockfile
pnpm dev
```

`API_INTERNAL_URL` trong `frontend/.env.local` là địa chỉ backend cho proxy `/api/...` và kiểm tra session phía server, mặc định `http://localhost:8080`. Giá trị này phải có lúc build và chạy Next.js vì rewrite được chốt khi build. Không đặt đường dẫn con hoặc dấu `/` cuối URL. Cookie tài khoản đi qua cùng origin frontend.

`NEXT_PUBLIC_API_URL` vẫn được dùng cho health check trực tiếp và được chốt khi build. Chỉ đưa cấu hình công khai vào biến `NEXT_PUBLIC_*`. Mở frontend tại `http://localhost:3000` để khớp CORS local; nếu đổi origin, cập nhật `CORS_ALLOWED_ORIGINS` rồi khởi động lại backend. `.env` gốc dành cho Compose/backend; Next.js đọc `frontend/.env.local`.

Dashboard gọi `/api/health` bằng TanStack Query, kiểm tra response bằng Zod và hiển thị trạng thái kết nối thực tế. Health này xác nhận API phản hồi; `/actuator/health` kiểm tra thêm dependency.

Kiểm tra giao diện: chạy `pnpm build`, `pnpm exec playwright install chromium`, rồi `pnpm test:e2e`. Cần Java 21, Docker và hai cổng `3000`/`8080` trống. Playwright khởi động frontend production và backend bằng `spring-boot:test-run`, dùng PostgreSQL/Redis Testcontainers riêng. Đăng ký, đăng nhập, settings và đăng xuất đi qua API thật với tài khoản tổng hợp `@example.test`; chỉ tình huống health mất kết nối được chặn request để giả lập lỗi.

Sau khi di chuyển route, chạy `pnpm exec next typegen` để cập nhật kiểu route trước typecheck. Không commit thư mục build, `test-results`, `playwright-report`, trace hoặc thông tin session trong kết quả test.
