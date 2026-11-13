# Phát triển local trên Windows

## Mục tiêu

Web chạy bằng Vite/HMR tại `http://localhost:5173`; Spring Boot chạy trực tiếp trên Windows tại `http://localhost:8081`; PostgreSQL Docker được publish tại `127.0.0.1:55432`. Các cổng này tránh xung đột EnterpriseDB đang dùng 8080/5432. Caddy, MinIO, MailHog và Ollama không phải điều kiện để đăng nhập, quản lý task hoặc phát triển giao diện.

## Yêu cầu

Backend dùng Maven Wrapper (`backend/mvnw.cmd` trên Windows, `backend/mvnw` trên Linux/macOS).
Từ thư mục gốc chạy `backend\mvnw.cmd -f backend/pom.xml validate`.
Các script tự gọi Wrapper; không cần cài Maven riêng.
Lần đầu cần Internet để tải Maven 3.9.11 vào cache `.m2/wrapper/dists` của người dùng.
Các lần sau dùng cache, không lưu bộ Maven/ZIP trong repository.

- Docker Desktop đang chạy.
- JDK 21, Node.js 20+ và pnpm 9 (`corepack enable`).
- Repository dùng `pnpm-lock.yaml` làm lockfile chuẩn, nên cài dependency bằng `pnpm install --frozen-lockfile`, không dùng `npm ci`.

## Khởi động

1. Sao chép `infra/.env.example` thành `infra/.env` và thay placeholder secret bằng secret local.
2. Tại thư mục gốc, chạy:

   ```powershell
   powershell -ExecutionPolicy Bypass -File .\scripts\dev.ps1
   ```

3. Mở `http://localhost:5173`. Vite proxy `/api/v1` sang backend, do đó không cần chạy Caddy hay thiết lập CORS production.

Chạy lại script khi SmartTask đang healthy sẽ tái sử dụng backend và Vite đang chạy.
Đổi cổng bằng `scripts\dev.ps1 -BackendPort 8181 -DatabasePort 55433` nếu cần;
script tự đặt datasource và proxy cho tiến trình mới.

Thêm `-WithStorage` để chạy MinIO, hoặc `-WithMail` để chạy MailHog. Local profile dùng file storage tại `backend/app/var/files`; chỉ đặt `FILE_GATEWAY_MODE=s3` khi thật sự kiểm thử MinIO.

## Admin đầu tiên

Database local Docker là database riêng, không tự chứa tài khoản ở môi trường
production hoặc PostgreSQL native. Nếu chưa có user trong database này, đăng ký
tại `http://localhost:5173/register` trước khi đăng nhập. API login hiện trả
`INVALID_SESSION`/401 khi thông tin đăng nhập không hợp lệ; frontend hiển thị
thông báo tên đăng nhập/email hoặc mật khẩu không đúng cho trường hợp này.

1. Đăng ký user bằng UI khi backend đang chạy.
2. Dừng local environment.
3. Đặt biến `SMARTTASK_BOOTSTRAP_ADMINUSERNAME` bằng đúng username vừa đăng ký trước lần start backend tiếp theo.
4. Khởi động lại backend. `BootstrapRunner` chỉ hoạt động khi chưa tồn tại System Admin; nó chỉ cấp grant cho username được cấu hình, kích hoạt tài khoản PENDING, tạo personal workspace qua service và lưu cờ hoàn tất.
5. Bỏ biến bootstrap sau khi thành công; đăng nhập Dashboard bằng tài khoản đó và quản trị các tài khoản khác theo luồng bình thường.

Bootstrap không tạo password, không quét/ghi đè user hiện có và không cấp quyền cho tài khoản ngoài cấu hình. Nếu log nói username không tồn tại, hãy kiểm tra lại chính tả thay vì đổi dữ liệu database.

## Kiểm tra và dừng

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\check-dev.ps1
powershell -ExecutionPolicy Bypass -File .\scripts\test-local.ps1
powershell -ExecutionPolicy Bypass -File .\scripts\stop-dev.ps1
```

Lệnh dừng chỉ dừng process và container dependency; không gọi `down -v`, không xóa volume hay dữ liệu.

## Khắc phục nhanh

Mặc định backend dùng 8081 và database dùng 55432. `check-dev.ps1` tự đọc cổng
backend đã chạy từ `.run/ports.json`.

- Port 8081/55432 đang bận: dùng `-BackendPort`/`-DatabasePort`. Port 5173 đang bận bởi Vite khác: dừng phiên Vite đó trước. Script chỉ dùng lại trang SmartTask có proxy API hoạt động.
- Backend không healthy: xem `.run/backend.log` và `docker compose -f infra/docker-compose.dev.yml logs postgres`.
- Hết phiên: đăng nhập lại; API protected trả `INVALID_SESSION`/401 theo contract.
- Không chạy Ollama: các chức năng lõi không bị ảnh hưởng. Có thể đặt `VITE_ENABLE_AI=false` để ẩn màn hình AI trong local.
