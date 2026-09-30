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

V10 thêm bảng kết nối Google Calendar riêng; không sửa hoặc seed các bảng học vụ. Tính năng `AMS_GOOGLE_CALENDAR_ENABLED=false` mặc định, nên không cần OAuth client hay khóa Google để khởi động AMS. Nếu cần thử kết nối thật, làm theo [hướng dẫn Google Calendar](google-calendar-integration.md); đừng dùng các biến Google mẫu cũ hoặc đưa khóa vào repository.

## Worker đồng bộ

V8 thêm `sync_run`; Flyway sẽ nâng cấp database V7, không cần xóa volume. Khi `AMS_PHENIKAA_ENABLED=false` (mặc định), API và worker Phenikaa không hoạt động. Nếu bật, worker poll hàng đợi mặc định mỗi 2 giây; đây chỉ là lịch **tiêu thụ** run đã được yêu cầu, không tự tạo run định kỳ cho user. `AMS_SYNC_WORKER_ENABLED=false` tắt polling trong môi trường kiểm thử/local nhưng vẫn cho phép gọi worker trực tiếp trong test. Không bật tính năng bằng một khóa giả hoặc tự chép phiên trình duyệt vào database.

Có thể chỉnh `AMS_SYNC_POLL_INTERVAL`, `AMS_SYNC_BATCH_SIZE`, `AMS_SYNC_LOCK_TTL`, `AMS_SYNC_STALE_TIMEOUT`, `AMS_SYNC_MAX_ATTEMPTS`, `AMS_SYNC_BACKOFF` và `AMS_SYNC_MANUAL_COOLDOWN` qua môi trường. Mặc định lần lượt là `2s`, `4`, `2m`, `3m`, `3`, `10s`, `1m`. TTL khóa cần ngắn và được heartbeat gia hạn khi worker còn sống; stale timeout phải dài hơn TTL ít nhất 5 giây. Retry tạm thời dùng cùng run với backoff tăng, không giữ transaction hay khóa chỉ để chờ. `mvnw.cmd verify` chạy worker với adapter giả và PostgreSQL/Redis Testcontainers thật; CI không gọi Phenikaa.

Phase 6B bổ sung lịch tự **xếp hàng**, tách khỏi vòng poll của worker. `AMS_SYNC_AUTO_ENABLED=false` mặc định, kể cả khi worker đã bật. Chỉ bật sau khi có kết nối hợp lệ và cân nhắc tải lên cổng nguồn; scheduler không tự lấy phiên từ trình duyệt. Mặc định nó quét mỗi `5m`, xếp tối đa `4` user/lần, và mỗi user chỉ đến hạn sau `24h` kể từ run gần nhất. Run kết thúc lỗi cũng chờ ít nhất `24h` trước khi xếp lượt mới. Kết nối chưa từng đồng bộ có khoảng lệch ổn định theo UUID để các lượt đầu không cùng đến hạn. Các giá trị tương ứng là `AMS_SYNC_AUTO_SCAN_INTERVAL`, `AMS_SYNC_AUTO_BATCH_SIZE`, `AMS_SYNC_AUTO_INTERVAL` và `AMS_SYNC_AUTO_FAILURE_COOLDOWN`.

Lịch sử run được dọn khi ứng dụng chạy: giữ `90d`, quét mỗi `1h` và xóa tối đa `100` run kết thúc/lần. Có thể chỉnh `AMS_SYNC_RETENTION`, `AMS_SYNC_CLEANUP_INTERVAL`, `AMS_SYNC_CLEANUP_BATCH_SIZE`; cleanup vẫn hoạt động khi tích hợp Phenikaa tắt, nhưng không đụng run đang chờ hoặc đang chạy. `AMS_SYNC_HISTORY_MAX_PAGE_SIZE` mặc định `100`; API lịch sử trả 20 hàng nếu không chỉ định `limit`. Cấu hình quá nhỏ/quá lớn bị từ chối khi khởi động. V9 chỉ thêm/thay chỉ mục; Flyway nâng cấp V8 mà không xóa run cũ.

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
