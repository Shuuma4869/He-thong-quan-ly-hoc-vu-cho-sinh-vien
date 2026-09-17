# Hệ thống quản lý học vụ cho sinh viên

**Academic Management System (AMS)**

AMS hỗ trợ sinh viên theo dõi kết quả học tập, chương trình đào tạo, lịch học, lịch thi và kế hoạch tốt nghiệp tại một nơi. Hệ thống được thiết kế để đồng bộ dữ liệu mà chính tài khoản sinh viên có quyền xem, phát hiện thay đổi và chuyển các sự kiện do AMS quản lý sang Google Calendar.

> Dự án đang ở giai đoạn bootstrap. Phenikaa, Google Calendar và email chưa được kết nối thực tế; dashboard hiện chỉ là giao diện nền và không trình bày dữ liệu giả như dữ liệu production.

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

## Branch và commit

- `main`: baseline tối thiểu để so sánh.
- `AMS-Solution`: branch phát triển của bootstrap và các phase tiếp theo.
- Không tự merge, rebase `main`, force push hoặc xóa branch review.
- Tất cả commit message viết bằng tiếng Việt, ngắn gọn và phản ánh đúng thay đổi.

Xem thêm tại [tài liệu dự án](docs/project-overview.md) và [hướng dẫn môi trường](docs/development-setup.md).
