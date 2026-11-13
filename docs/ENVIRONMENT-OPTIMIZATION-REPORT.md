# Báo cáo chuẩn hóa môi trường SmartTask

> Cập nhật sau kiểm thử runtime: xem [LOCAL-RUNTIME-VALIDATION.md](LOCAL-RUNTIME-VALIDATION.md).
> Docker và Maven Wrapper đã chạy được. Backend local mặc định 8081, PostgreSQL
> Docker 55432. Các blocker môi trường ở mục 7 bên dưới là ghi nhận của lượt trước,
> đã được giải quyết cho luồng local cơ bản.

**Ngày:** 09/11/2026 (Asia/Bangkok)  
**Phạm vi:** local development Windows và Docker Compose production. Không deploy, không commit/push, không xóa database/volume/account/migration hay sửa các thay đổi UI có sẵn.

## 1. Kiến trúc trước và sau

Trước thay đổi, repository đã có Compose development, Compose production, Vite proxy và các script chạy khác nhau. Tuy nhiên development Compose luôn khởi động cả MinIO/MailHog, dùng credential mẫu trực tiếp trong YAML, chưa có local Spring profile thống nhất với database development, và không có bộ script chuẩn `dev/check/stop/test` tách rõ process local và Docker dependency.

Sau thay đổi, local development có mô hình sau:

```text
Vite/HMR :5173 ──proxy /api/v1──> Spring Boot local :8080 ──> PostgreSQL Docker :5432
                                           │
                                           └─> local file storage (mặc định)

MinIO :9000 và MailHog :8025 chỉ chạy khi chọn profile.
```

Production vẫn giữ Compose + Caddy + PostgreSQL + MinIO + backend + web. Không thay API contract, schema/migration, RBAC hoặc modular-monolith. Backend production vẫn private trong network Compose; local backend được chạy trực tiếp và publish `127.0.0.1:8080` tự nhiên qua Spring Boot.

## 2. File được thêm hoặc thay đổi

| File | Thay đổi |
| --- | --- |
| `infra/docker-compose.dev.yml` | PostgreSQL/MinIO/MailHog chỉ bind loopback; credential lấy từ `infra/.env`; MinIO và MailHog thành profile tùy chọn. |
| `infra/.env.example` | Mẫu secret local, không có giá trị usable production. |
| `backend/app/src/main/resources/application-local.yml` | Profile local cho datasource, CORS Vite, local file storage và Ollama tùy chọn. |
| `backend/app/src/main/resources/application.yml` | Bỏ password/access key hard-code; nhận datasource/MinIO credential từ environment. |
| `scripts/dev.ps1` | Kiểm tra prerequisite/port, khởi động PostgreSQL Docker, backend local, Vite và chờ health. |
| `scripts/check-dev.ps1` | Kiểm tra Docker, database, backend, frontend, API health và port. |
| `scripts/stop-dev.ps1` | Dừng process local + dependency container nhưng giữ volume. |
| `scripts/test-local.ps1` | Chạy lint/typecheck, web test/build, backend tests và ghi báo cáo timestamp. |
| `docs/LOCAL_DEVELOPMENT.md` | Hướng dẫn local, bootstrap admin và khắc phục sự cố. |
| `README.md` | Điểm vào tài liệu local/deploy/báo cáo. |
| `deploy/docker-compose.yml`, `backend/Dockerfile` | Đặt giới hạn memory container và default JVM heap 60% thay vì 75%. |
| `web/package.json` | `npm run lint` nay dùng TypeScript typecheck (`tsc --noEmit`), vốn là static check phù hợp TS/TSX. |
| `web/eslint.config.js` | Ghi rõ ESLint parser TypeScript chưa được cấu hình, tránh nhận nhầm đó là lint TypeScript hoạt động. |

Không có file hay dependency nào bị xóa. Không tự động gỡ dependency `eslint` vì lockfile hiện do pnpm quản lý và việc gỡ cần cài đặt/đồng bộ lockfile có kiểm soát.

## 3. Lệnh Windows

```powershell
# Lần đầu: copy infra/.env.example -> infra/.env, thay secret placeholder
powershell -ExecutionPolicy Bypass -File .\scripts\dev.ps1

# Tùy chọn MinIO hoặc MailHog
powershell -ExecutionPolicy Bypass -File .\scripts\dev.ps1 -WithStorage -WithMail

# Kiểm tra, test và dừng an toàn
powershell -ExecutionPolicy Bypass -File .\scripts\check-dev.ps1
powershell -ExecutionPolicy Bypass -File .\scripts\test-local.ps1
powershell -ExecutionPolicy Bypass -File .\scripts\stop-dev.ps1
```

Vite dùng `http://localhost:5173`; API local dùng `http://localhost:8080/api/v1`. Vite proxy `/api/v1` sang backend, cho HMR và route SPA mà không cần Caddy. Lệnh chuẩn dùng pnpm vì repository chỉ có `pnpm-lock.yaml`; `npm ci` không thể tái lập dependency khi không có `package-lock.json`.

## 4. Bootstrap Admin an toàn

`BootstrapRunner` không tạo admin/password tự động. Khi database chưa có System Admin, nó lấy chính xác một username từ `SMARTTASK_BOOTSTRAP_ADMINUSERNAME`. Nếu user tồn tại, runner cấp `SYSTEM_ADMIN`, kích hoạt user PENDING qua `IdentityService` và lưu `bootstrap_completed`; nếu không có username hoặc user không tồn tại, runner chỉ log và không thay đổi dữ liệu. Quy trình local chi tiết nằm trong `LOCAL_DEVELOPMENT.md`.

## 5. Production và RAM

Docker Compose production được giữ nguyên kiến trúc. Memory cap mới: database 700 MB, MinIO 450 MB, backend 900 MB, web/Caddy 160 MB; tổng cap danh nghĩa khoảng 2.21 GB, để lại headroom cho kernel, Docker và cache trên VPS 4 GB. JVM mặc định `-XX:MaxRAMPercentage=60 -XX:+UseG1GC`. Đây là giới hạn cấu hình, **không phải** phép đo RAM thực; cần đo RSS/latency/export/upload trên staging trước khi thay đổi thêm.

Production vẫn dùng:

```bash
cd deploy
cp .env.example .env        # thay toàn bộ change-me secret và DOMAIN
docker compose up -d --build
./smoke.sh                  # thêm SMOKE_USER/SMOKE_PASSWORD để kiểm tra login + SSE
```

Backup, restore và update tiếp tục theo `DEPLOY.md`. Restore hiện có cơ chế `--yes` vì thao tác thay database có chủ ý; không được chạy trong local development chuẩn.

## 6. Kiểm thử đã chạy trong lần này

| Kiểm tra | Kết quả |
| --- | --- |
| Parse toàn bộ script PowerShell mới | PASS |
| `docker compose config --quiet` với `infra/.env.example` | PASS |
| `docker compose config --quiet` với `deploy/.env.example` | PASS |
| Web TypeScript check | PASS |
| Vitest | PASS: 10 files, 24 tests |
| Vite production build | PASS; JS gzip khoảng 118.31 kB, CSS gzip khoảng 14.82 kB |
| `git diff --check` | PASS (chỉ có warning CRLF từ Git) |

## 7. Chưa thể xác nhận trong môi trường hiện tại

- Docker daemon không truy cập được từ session hiện tại (`npipe:////./pipe/docker_engine` permission denied), nên chưa khởi động container, kiểm tra persistence/restart/health runtime hoặc chạy integration Testcontainers.
- Maven không có trên PATH, nên chưa chạy Maven compile/unit/integration/Flyway runtime ở lần này.
- `pnpm` không có trên PATH; validation web được chạy qua executable đã có trong `web/node_modules`. Script `dev.ps1` sẽ báo rõ prerequisite này thay vì chạy nửa chừng.
- Không chạy đăng nhập, bootstrap admin, upload/download, SSE hay kiểm thử role bằng hệ thống đang hoạt động do các blocker trên.
- Cảnh báo Docker về không đọc được `C:\Users\PC\.docker\config.json` không làm `compose config` thất bại, nhưng cần sửa quyền Docker Desktop trước khi chạy runtime.

Vì các giới hạn trên, không tuyên bố đã nghiệm thu end-to-end local hay production. Các bước cần chạy tiếp theo là cài/đưa Maven và pnpm lên PATH, mở quyền Docker Desktop cho user, tạo `infra/.env`, rồi chạy `scripts/dev.ps1`, `scripts/check-dev.ps1` và `scripts/test-local.ps1`.

## 8. Rollback

Các thay đổi chỉ là script, cấu hình profile, Compose cap và tài liệu; không có migration/data mutation. Để rollback source, bỏ các file/mục thay đổi trong bảng ở mục 2 bằng commit/revert thông thường sau khi review. Không dùng `git reset --hard`; không xóa `infra/.env`, `deploy/.env`, Docker volume, database hoặc object storage. Các thay đổi UI chưa commit có trước lần làm này được giữ nguyên.
