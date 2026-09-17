# AMS Backend

Backend của AMS sử dụng Java 21, Spring Boot, PostgreSQL, Redis và Flyway.

## Chạy local

Khởi động PostgreSQL và Redis từ thư mục gốc, sau đó chạy API:

```powershell
docker compose up -d
cd backend
.\mvnw.cmd spring-boot:run
```

Health endpoint: `http://localhost:8080/api/health`.

Actuator health: `http://localhost:8080/actuator/health`.

Swagger UI: `http://localhost:8080/swagger-ui.html`.

## Kiểm thử

```powershell
.\mvnw.cmd test
.\mvnw.cmd verify
```

Test context dùng Testcontainers và tự bỏ qua khi Docker không sẵn sàng. Các adapter Phenikaa, Google Calendar và email mới chỉ có contract, chưa kết nối dịch vụ thật.
