# Xác nhận chạy local SmartTask

Ngày kiểm tra: 09/11/2026, Asia/Bangkok. Thực hiện trên Windows với Docker Desktop,
Java 21, Maven Wrapper 3.9.11 và Vite. Không deploy Ubuntu, không xóa volume,
không sửa secret hiện có, không commit/push.

## Lệnh đã chạy thành công

```powershell
cd D:\task
.\scripts\dev.ps1
.\scripts\check-dev.ps1
.\scripts\stop-dev.ps1
.\scripts\dev.ps1
```

Web: http://localhost:5173. Backend: http://localhost:8081/api/v1.
PostgreSQL Docker: 127.0.0.1:55432. Chạy lại dev.ps1 tái sử dụng dịch vụ healthy.
Hệ thống được để chạy sau kiểm thử để người dùng mở web kiểm tra.

## Các lỗi phát hiện và đã sửa

- PowerShell coi warning của Docker ghi vào stderr là terminating error: xử lý theo exit code của Docker.
- Compose không tự tìm `infra/docker-compose.dev.yml`: truyền `-f` tường minh trong start/stop.
- EnterpriseDB chiếm 8080 và 5432: mặc định local chuyển sang 8081 và 55432; script tự truyền datasource và Vite proxy.
- SQL cấp quyền bảng chạy trước Flyway làm PostgreSQL thoát: init mount chỉ còn script tạo role; giữ script cấp quyền bảng cho thao tác post-migration.
- Healthcheck PostgreSQL dùng username cũ: đọc POSTGRES_USER/POSTGRES_DB của container.
- Local MinIO client không khởi tạo được khi credential rỗng: cho phép client không credential khi mode local; s3 vẫn bắt buộc credential.
- Upload vẫn gọi MinIO ở mode local: bổ sung ghi staging, đọc staging, commit file version và download trực tiếp trên local filesystem; giữ luồng S3 riêng.
- Login trình duyệt tại 127.0.0.1 bị CORS 403: local allowlist bao gồm cả localhost:5173 và 127.0.0.1:5173.
- Script stop ghi vào biến read-only `$PID`: dùng processId; dừng cây tiến trình sau khi kiểm tra command line thuộc project.
- Script check có thể báo PASS khi biểu thức trả false: kiểm tra boolean thực tế.
- Script start có thể báo sẵn sàng dù process mới đã thoát: kiểm tra process trong vòng đợi HTTP.
- Backend giữ cổng 8081 nhưng mất kết nối khi PostgreSQL dừng: start và chờ database healthy trước khi kiểm tra cổng backend; chờ backend hiện có kết nối lại thay vì báo nhầm xung đột cổng.

## Kết quả kiểm thử thực tế

| Kiểm tra | Kết quả |
| --- | --- |
| Start local bằng dev.ps1 | PASS |
| Chạy dev.ps1 lần nữa khi dịch vụ đang chạy | PASS, dùng lại backend/Vite |
| check-dev.ps1 | PASS: Docker, PostgreSQL healthy, backend, frontend, API và Vite proxy |
| Stop rồi start lại | PASS, giải phóng cổng SmartTask và khởi động lại thành công |
| Dữ liệu PostgreSQL sau restart | PASS: 6 task trước và sau restart |
| Flyway | V17 đã áp dụng thành công |
| Backend unit tests được chọn | 45 tests, 0 failure/error/skip; gồm bootstrap, session, RBAC, personal task, MinIO config, file policy/version/stream |
| Frontend | TypeScript PASS; Vitest 24 tests/10 suites PASS; Vite production build PASS |
| Smoke HTTP + trình duyệt Edge headless | 24 checks PASS, chi tiết bên dưới |
| PowerShell syntax | PASS cho các script mới |

Smoke kiểm tra proxy Vite; anonymous API bị 401; đăng ký/đăng nhập; tạo workspace,
project, task; activate project/start task qua workflow; tạo personal task/note;
đọc calendar/dashboard/reports/notifications/tasks/notes; SSE kết nối và ticket
single-use; chặn user khác đọc task bằng 404; logout thu hồi token; đăng nhập qua
UI và điều hướng/refresh các route my-tasks/projects/notes/calendar/reports/notifications.
Upload local đi đủ init → content → complete → commit → download và so sánh bytes.

Script smoke: `web/scripts/local-smoke.mjs`, dùng Playwright tại `.tools/browser`
và Edge có sẵn trên máy. Chạy từ thư mục web: `node scripts/local-smoke.mjs`.
Script tạo tài khoản tên `local_smoke_*`, không in password/token, không xóa dữ liệu thử.
Ảnh kiểm tra: `.run/local-smoke-browser.png`.

## Phạm vi chưa nghiệm thu

Chưa chạy toàn bộ integration suite backend hoặc kiểm thử MinIO thật/SMTP/push/Ollama.
S3 integration fixtures được đặt mode=s3 tường minh để phân biệt với local filesystem.
Không coi các provider tùy chọn là đã kiểm thử nhờ smoke local.

RAM quan sát sau restart: PostgreSQL khoảng 39.67 MiB theo `docker stats` lúc idle.
Đây không phải benchmark peak hoặc bằng chứng đủ tài nguyên trên VPS 4 GB.

Mọi dữ liệu thử được giữ trong volume local `smarttask-dev_pgdata` và local file
storage của backend. Dừng bằng stop-dev.ps1 giữ nguyên dữ liệu này.
