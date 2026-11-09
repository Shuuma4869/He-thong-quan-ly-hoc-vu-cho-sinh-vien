# Quyết định kiến trúc desktop

Ngày 10/11/2026: giữ **development shell**, chưa phát hành installer. Hướng phát hành được đề xuất là Tauri dùng một dịch vụ AMS trên HTTPS khi đã có hạ tầng và quy trình vận hành. Chưa có origin production được cấp và kiểm chứng trong nhiệm vụ này.

| Tiêu chí | Tauri + AMS trên server | Tauri + dịch vụ local | Development shell hiện tại |
| --- | --- | --- | --- |
| Cài đặt/lần mở đầu | Client nhỏ; cần server hoạt động | Phải cài và quản lý Java, Next.js, PostgreSQL, Redis | Lập trình viên khởi động từng dịch vụ |
| Phiên và OAuth | HTTPS cùng origin; cần thiết kế handoff từ browser chính thức | Callback, cổng, khóa và phiên local phức tạp | Cookie WebView riêng; cấp quyền Google trên web |
| Offline | Chưa hỗ trợ | Có thể đọc dữ liệu local; nguồn vẫn cần mạng | Không cam kết offline |
| Sao lưu/khôi phục | Tập trung tại server | Mỗi máy cần backup database và khóa | Dùng quy trình development |
| Tài nguyên/chi phí | Máy client nhẹ; cần vận hành hosting | Tốn RAM/CPU và cập nhật nhiều dịch vụ | Không có chi phí hosting, phụ thuộc máy phát triển |
| Windows/cập nhật | Cần kiểm thử installer, WebView2, ký mã và updater | Thêm service lifecycle, nâng schema và rollback trên từng máy | Chỉ debug build; release bị chặn có chủ ý |
| Nội dung từ xa | HTTPS allowlist; không cấp native capability | Localhost không xác thực tiến trình giữ cổng | Chặn origin khác; vẫn còn rủi ro tiến trình local chiếm cổng |

Next.js có server components đọc cookie và proxy `/api`; static export không thay thế được kiến trúc hiện tại. Đóng gói thêm database và backend chỉ để có `.exe` sẽ tạo trách nhiệm cài đặt, migration, backup và cập nhật chưa được giải quyết.

## Ranh giới của bản hiện tại

WebView chỉ cho điều hướng đến `http://localhost:3000`, không chấp nhận IP thay thế, userinfo, port khác, file/data/javascript URL hoặc hostname có hậu tố. Cửa sổ mới luôn bị từ chối. Danh sách capability trống; không có plugin shell, filesystem, opener hoặc command native. Các kiểm thử Rust chứng minh bộ lọc URL, không chứng minh một tiến trình giữ cổng 3000 là Next.js đáng tin cậy. Chỉ chạy shell trên máy phát triển tin cậy, kiểm tra tiến trình đang nghe cổng trước khi mở; không coi localhost là ranh giới chống malware.

Marker desktop chỉ điều chỉnh UX: Google Calendar giải thích cách mở `/settings` trong browser và không bắt đầu OAuth trong WebView. Marker không cấp quyền. Browser và WebView phải đăng nhập riêng; kết nối Google được lưu theo tài khoản AMS trên backend. Không trao cookie hoặc token giữa hai môi trường.

Google không cho dùng embedded user-agent để cấp quyền; xem [chính sách OAuth](https://developers.google.com/identity/protocols/oauth2/policies). Capability theo nguyên tắc quyền tối thiểu được mô tả trong [tài liệu Tauri](https://v2.tauri.app/security/capabilities/).

## Điều kiện trước một bản phát hành

1. Có origin HTTPS thực, reverse proxy cùng origin với `/api`, cookie Secure/HttpOnly/SameSite, callback đăng ký chính xác và secret trên server.
2. Thiết kế luồng browser OAuth phù hợp cho desktop và kiểm thử cùng tài khoản; không đưa OAuth vào WebView hoặc giả danh client của portal.
3. Tách cấu hình dev/release, bỏ URL localhost khỏi release, giữ remote capability rỗng và kiểm soát mọi navigation/new-window.
4. Kiểm thử build/installer có version, cài/mở/gỡ, hết phiên, mất mạng, sleep/resume; có quy trình ký mã và cập nhật đáng tin cậy.
5. Chứng minh backup/restore và migration trên staging. Chưa đáp ứng các bước này thì `DESKTOP_RELEASE_READY=NO`.

CI Windows kiểm tra formatting, clippy, unit test và debug build theo lockfile. Job này không phải bằng chứng installer hoặc WebView runtime đã qua nghiệm thu.
