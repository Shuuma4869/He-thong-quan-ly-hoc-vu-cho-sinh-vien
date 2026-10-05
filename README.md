# Hệ thống quản lý học vụ cho sinh viên

**Academic Management System (AMS)**

AMS hỗ trợ sinh viên theo dõi kết quả học tập, chương trình đào tạo, lịch học, lịch thi và kế hoạch tốt nghiệp tại một nơi. Hệ thống được thiết kế để đồng bộ dữ liệu mà chính tài khoản sinh viên có quyền xem, phát hiện thay đổi và chuyển các sự kiện do AMS quản lý sang Google Calendar.

AMS là dự án cá nhân, không phải hệ thống chính thức của Phenikaa University và không đại diện cho nhà trường. Các tích hợp chỉ làm việc với dữ liệu mà tài khoản người dùng được phép truy cập.

> Đã có tài khoản AMS, đăng nhập bằng session, cài đặt cá nhân và domain/database học vụ. Chương trình đào tạo, danh mục môn và nhóm môn đã được nhập trong phạm vi xác minh; kết quả học tập và chi tiết có API chỉ đọc. Hạ tầng đồng bộ làm mới **chỉ hồ sơ và phần chương trình/danh mục đã được phép lưu**. Phase 6B thêm chính sách tự xếp hàng (mặc định tắt), lịch sử có phân trang và dọn lịch sử cũ. **Chưa lưu lần học hoặc kết quả học tập** vì chưa đủ căn cứ chọn kết quả hiện hành và xác định tín chỉ/GPA cho từng lần học. Chưa lưu lịch/thi, tiên quyết hoặc tự chọn chương trình hiện tại. Chưa có giao diện kết nối Phenikaa; tính năng backend mặc định tắt. Phase 7A thêm nền kết nối Google Calendar và tạo lịch riêng, cũng tắt mặc định; **chưa tạo sự kiện** hoặc xác minh với tài khoản Google thật. Email chưa kết nối; dashboard vẫn là giao diện nền. Xem [ranh giới đọc dữ liệu Phase 5E](docs/phenikaa-integration.md#phase-5e-api-đọc-trực-tiếp-và-ranh-giới-nguồn), [vận hành đồng bộ Phase 6B](docs/architecture-overview.md#vận-hành-đồng-bộ-phase-6b) và [kết nối Google Calendar](docs/google-calendar-integration.md).

Phase 9A có màn hình **Chương trình** tại `/curriculum` để đọc chương trình, nhóm môn và danh mục môn từ PostgreSQL AMS. Màn hình dùng dữ liệu đã lưu, không gọi Phenikaa và không tự đồng bộ khi mở trang. Môn chưa có liên kết chương trình vẫn xem được trong tab **Danh mục môn**, không bị gán thành môn tự chọn. [Hướng dẫn đọc dữ liệu và API](docs/curriculum-catalog.md) giải thích các giá trị chưa xác định và cách phân trang.

## Phạm vi hướng tới

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

Sau khi đăng nhập, chọn **Chương trình** ở thanh điều hướng. Tài khoản mới có thể thấy danh sách rỗng vì đăng ký AMS không tự tạo hồ sơ học vụ. Dữ liệu chương trình đã nhập vẫn đọc được khi tắt tích hợp Phenikaa hoặc phiên cổng trường hết hạn. Trang không chọn chương trình hiện hành cho hồ sơ, không hiển thị tiến độ học, điểm hoặc điều kiện đăng ký môn.

Phần Phenikaa không tự kết nối khi chạy local. Không cần điền khóa để dùng tài khoản AMS. Nếu phát triển luồng nhập hồ sơ, đọc trước [cấu hình khóa, giới hạn cấp phiên và kết quả Phase 4B](docs/phenikaa-integration.md#phase-4b-kết-nối-mã-hóa-và-nhập-hồ-sơ); không sao chép token từ DevTools vào source hoặc `.env.example`.

Google Calendar cũng tắt mặc định. Muốn thử kết nối, cần tự cấu hình OAuth client và khóa mã hóa riêng theo [hướng dẫn này](docs/google-calendar-integration.md). Không cần cấu hình Google để dùng AMS thông thường. Kết nối thành công chỉ tạo một lịch phụ của AMS; chưa thêm lịch học, lịch thi hay bất kỳ sự kiện nào.

Khi đã có kết nối Phenikaa được cấp qua quy trình nội bộ, backend bật tính năng sẽ có các endpoint `/api/me/academic/source/...` để đọc chương trình và kết quả của chính tài khoản AMS. Chúng **không** tạo `StudentCourse` hoặc `AcademicResult`, không bảo đảm nguồn trả đủ mọi hàng và hiện chưa có màn hình học vụ hoàn chỉnh. Không bật tính năng chỉ để thử API nếu chưa có khóa mã hóa và kết nối hợp lệ.

Khi đã có kết nối hợp lệ, `POST /api/me/sync` yêu cầu làm mới hồ sơ rồi đến những chương trình nguồn có thể đọc; request chỉ xếp hàng và trả `202`, không chờ cổng trường. Xem lượt gần nhất ở `GET /api/me/sync/current`, một lượt cụ thể ở `/api/me/sync/runs/{runId}`, hoặc lịch sử ở `/api/me/sync/runs?limit=20`. Tự xếp hàng theo lịch **mặc định tắt** và phải được người vận hành bật riêng. Worker không tự tạo kết nối từ phiên trình duyệt. [Trạng thái, lịch chạy và giới hạn](docs/architecture-overview.md#vận-hành-đồng-bộ-phase-6b) được ghi riêng.

[Kết quả Phase 4C](docs/phenikaa-integration.md#phase-4c-lịch-học-và-lịch-thi) giải thích vì sao đọc được lịch chưa đồng nghĩa với lưu được lịch đúng. Đặc biệt, `IDLICHHOC` đã xuất hiện ở nhiều ngày khác nhau; không dùng riêng mã này làm định danh từng buổi học.

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

Xem thêm tại [tài liệu dự án](docs/project-overview.md), [mô hình dữ liệu học vụ](docs/database-model.md), [kết quả khảo sát kết nối Phenikaa](docs/phenikaa-integration.md), [kết nối Google Calendar](docs/google-calendar-integration.md) và [hướng dẫn môi trường](docs/development-setup.md).
