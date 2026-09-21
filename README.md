# Hệ thống quản lý học vụ cho sinh viên

**Academic Management System (AMS)**

AMS hỗ trợ sinh viên theo dõi kết quả học tập, chương trình đào tạo, lịch học, lịch thi và kế hoạch tốt nghiệp tại một nơi. Hệ thống được thiết kế để đồng bộ dữ liệu mà chính tài khoản sinh viên có quyền xem, phát hiện thay đổi và chuyển các sự kiện do AMS quản lý sang Google Calendar.

> Đã có tài khoản AMS, đăng nhập bằng session và cài đặt cá nhân, cùng domain/database học vụ chuẩn hóa. Phase 4B bổ sung kết nối Phenikaa mã hóa và nhập hồ sơ đúng tài khoản, đã kiểm chứng bằng Java với PostgreSQL tạm. Chưa có giao diện kết nối Phenikaa; tính năng backend mặc định tắt. Lịch đã đọc được qua HTTP nhưng chưa đủ liên kết môn/học kỳ để lưu. Google Calendar và email chưa được kết nối; dashboard vẫn là giao diện nền, không trình bày dữ liệu giả như dữ liệu production.

## Phạm vi chính

- Dashboard GPA, tín chỉ, môn còn thiếu, lịch gần nhất và trạng thái đồng bộ.
- Đồng bộ học vụ qua `AcademicPortalClient`, với Phenikaa là adapter đầu tiên.
- Change Detection Engine lưu snapshot và phân loại thay đổi lịch học, lịch thi.
- Calendar, curriculum, course catalog, GPA calculator và semester/graduation planner.
- Google Calendar OAuth, email notification, retry, deduplication và Sync Center.
- Bảo mật theo defense in depth, OWASP ASVS và nguyên tắc quyền tối thiểu.

## Công nghệ

- Frontend: Next.js 16, React, TypeScript, Tailwind CSS, shadcn/ui foundation, TanStack Query, Zustand, Vitest và Playwright.
- Backend: Java 21, Spring Boot 4, Spring Security, Spring Data JPA, Flyway, PostgreSQL, Redis, OpenAPI và Testcontainers.
- Hạ tầng local: Docker Compose.
- CI: GitHub Actions và Dependabot.

## Cấu trúc repository

```text
.
├── frontend/          Next.js application
├── backend/           Spring Boot API và integration contracts
├── docs/              Tài liệu yêu cầu, kiến trúc, setup và security
├── .github/           CI và dependency updates
├── compose.yaml       PostgreSQL và Redis local
└── .env.example       Danh sách biến môi trường an toàn
```

## Chạy local

Yêu cầu: Git, Java 21, Node.js 24 LTS trở lên, pnpm và Docker Desktop.

```powershell
Copy-Item .env.example .env
docker compose up -d

cd backend
.\mvnw.cmd spring-boot:run
```

Mở terminal khác:

```powershell
cd frontend
Copy-Item .env.example .env.local
pnpm install --frozen-lockfile
pnpm dev
```

- Frontend: `http://localhost:3000`
- Backend health: `http://localhost:8080/api/health`
- Actuator health: `http://localhost:8080/actuator/health`
- Swagger UI: `http://localhost:8080/swagger-ui.html`

Mở `/register` để tạo tài khoản sinh viên, sau đó đăng nhập tại `/login`. Không có tài khoản admin mặc định. `/settings` lưu email nhận thông báo, múi giờ, ngôn ngữ ưu tiên và giao diện; email chưa được xác minh và chưa dùng để gửi thông báo.

Phần Phenikaa không tự kết nối khi chạy local. Không cần điền khóa để dùng tài khoản AMS. Nếu phát triển luồng nhập hồ sơ, đọc trước [cấu hình khóa, giới hạn cấp phiên và kết quả Phase 4B](docs/phenikaa-integration.md#phase-4b-kết-nối-mã-hóa-và-nhập-hồ-sơ); không sao chép token từ DevTools vào source hoặc `.env.example`.

## Kiểm tra

```powershell
cd frontend
pnpm lint
pnpm typecheck
pnpm test
pnpm build
pnpm exec playwright install chromium
pnpm test:e2e

cd ..\backend
.\mvnw.cmd verify
```

`mvnw.cmd test` chạy unit test. `mvnw.cmd verify` chạy thêm integration test với PostgreSQL và Redis qua Testcontainers; cần Docker và không tự bỏ qua khi Docker thiếu. Spring Boot 4.1 quản lý JUnit Jupiter 6, dùng cùng API test Jupiter.

E2E cần Java 21, Docker và cổng `3000`, `8080` trống. Playwright tự khởi động cả frontend production và backend với database/Redis riêng; luồng tài khoản không mock API. Nếu vừa di chuyển route, chạy `pnpm exec next typegen` trước `pnpm typecheck` để cập nhật kiểu route.

## Branch và commit

- `main`: baseline tối thiểu để so sánh.
- `AMS-Solution`: branch phát triển của bootstrap và các phase tiếp theo.
- Không tự merge, rebase `main`, force push hoặc xóa branch review.
- Tất cả commit message viết bằng tiếng Việt, ngắn gọn và phản ánh đúng thay đổi.

Xem thêm tại [tài liệu dự án](docs/project-overview.md), [mô hình dữ liệu học vụ](docs/database-model.md), [kết quả khảo sát kết nối Phenikaa](docs/phenikaa-integration.md) và [hướng dẫn môi trường](docs/development-setup.md).
