# Quy trình Git

- `main` giữ baseline có thể so sánh.
- Mọi phát triển hiện tại nằm trên `AMS-Solution`.
- Không merge, squash merge, rebase `main`, auto-merge hoặc xóa branch review nếu chưa có yêu cầu rõ ràng.
- Không dùng force push hoặc rewrite history.

## Commit

Commit phải nhỏ, có ý nghĩa và viết bằng tiếng Việt. Ví dụ:

```text
thiết lập frontend Next.js
cấu hình PostgreSQL và Redis
chuẩn bị adapter Phenikaa
```

Trước khi push, chạy test liên quan, kiểm tra `git status`, staged diff và secret/file tạm.
