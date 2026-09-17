# Quy trình Git

- `main` giữ baseline có thể so sánh.
- Mọi phát triển hiện tại nằm trên `AMS-Solution`.
- Không merge, squash merge, rebase `main`, auto-merge hoặc xóa branch review nếu chưa có yêu cầu rõ ràng.
- Không force push hoặc viết lại lịch sử nếu chưa được chủ dự án yêu cầu rõ ràng.

## Commit

Commit phải nhỏ, có ý nghĩa và viết bằng tiếng Việt. Ví dụ:

```text
thiết lập frontend Next.js
cấu hình PostgreSQL và Redis
chuẩn bị adapter Phenikaa
```

Trước khi push, chạy test liên quan, kiểm tra `git status`, staged diff và secret/file tạm.

## Pull request và hợp nhất

- Tiêu đề, mô tả PR, tiêu đề và nội dung commit hợp nhất viết bằng tiếng Việt.
- Mô tả PR nêu thay đổi, kết quả kiểm thử và giới hạn còn lại; không ghi kiểm thử đạt khi chưa chạy.
- PR cập nhật dependency cũng phải được kiểm tra diff, tương thích và CI trước khi hợp nhất.
- Dependabot không hỗ trợ dịch toàn bộ thông điệp sang tiếng Việt bằng cấu hình. Cần Việt hóa commit và PR trước khi hợp nhất; việc sửa tên PR không đổi thông điệp commit có sẵn.
- Chỉ chỉnh lịch sử nhánh PR khi được chủ dự án cho phép, sao lưu trước và dùng `--force-with-lease`. Không viết lại lịch sử `main`.
- Nâng `react` và `react-dom` cùng phiên bản. Giữ major của `@types/node` khớp môi trường Node.js được kiểm thử trong CI.
- Sau khi hoàn thành một phần việc, chuẩn bị sẵn nội dung PR và commit hợp nhất để chủ dự án kiểm tra. Không tự hợp nhất nếu chưa được yêu cầu.
