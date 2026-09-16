# Thiết lập môi trường phát triển

## Yêu cầu

- Git 2.49 trở lên.
- Java 21; Maven toàn cục không bắt buộc vì có Maven Wrapper.
- Node.js 24 LTS trở lên và pnpm.
- Docker Desktop với Linux containers.

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

Flyway chạy migration khi ứng dụng kết nối PostgreSQL. `ddl-auto=validate` ngăn Hibernate tự sửa schema.

## Frontend

```powershell
cd frontend
pnpm install --frozen-lockfile
pnpm dev
```

Biến `NEXT_PUBLIC_API_URL` xác định backend URL; không hard-code production domain vào source.
