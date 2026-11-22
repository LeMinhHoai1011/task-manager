# SmartTask — build & deploy

Tài liệu này mô tả cách chạy SmartTask ở chế độ production-like trên máy local
và cách deploy lên một VPS có domain + HTTPS. Stack gồm:

```
Internet -> Caddy (80/443, TLS, SPA + /api proxy)
                         |
                         +--> backend :8080 (Spring Boot)
                         |        |
                         |        +--> Postgres :5432 (internal)
                         |        +--> MinIO :9000 (internal)
                         |
                         +--> /srv (React dist)
```

Chỉ Caddy mở port ra ngoài. Postgres và MinIO không publish port.

---

## 1. Yêu cầu

- VPS Linux (Ubuntu 22.04/24.04 hoặc tương đương), tối thiểu **2 vCPU / 4 GB RAM**.
  Stack hiện tại chạy 1 backend + 1 Postgres + 1 MinIO + Caddy trên một máy.
- Docker Engine 24+ và Docker Compose v2 (`docker compose version`).
- Domain (ví dụ `app.example.com`) trỏ A/AAAA record về IP VPS nếu deploy HTTPS.
- Mở firewall: `22/tcp` (SSH), `80/tcp`, `443/tcp`, `443/udp` (HTTP/3).
- Ubuntu 24.04 dùng Docker Engine và Docker Compose 2.24.4 trở lên.
- Chế độ build local cần Docker hoặc Java 21, Maven, Node và pnpm.

---

## 2. Chạy production-like trên máy local (Windows/macOS/Linux)

### 2.1 Build backend

Backend được build **bên trong Docker** (multi-stage `maven` -> `jre`), nên máy
local chỉ cần Docker + Compose, không cần Maven/JDK. Lần build đầu cần
Internet để tải dependency Maven; các lần sau dùng layer cache.

Nếu muốn chạy backend ngoài Docker để dev, vẫn có thể build jar thủ công:

```bash
cd backend
mvn -pl app -am package -DskipTests
# jar: backend/app/target/smart-task-app-0.0.1-SNAPSHOT.jar
```

### 2.2 Tạo file môi trường

```powershell
cd D:\task\deploy
Copy-Item .env.example .env
```

Sửa `.env` cho local:

```dotenv
DOMAIN=:80
HTTP_PORT=8088
HTTPS_PORT=8443
POSTGRES_PASSWORD=local-pg-change-me
MINIO_ROOT_PASSWORD=local-minio-root-change-me
MINIO_APP_PASSWORD=local-minio-app-change-me
BOOTSTRAP_ADMIN_USERNAME=
SELF_REGISTRATION=true
VITE_ENABLE_AI=false
```

`DOMAIN=:80` nghĩa là Caddy chỉ chạy HTTP trong container; ra ngoài qua
`http://localhost:8088`. Không dùng cấu hình này cho Internet.

### 2.3 Khởi động

```powershell
cd D:\task\deploy
docker compose up -d --build
docker compose ps
docker compose logs -f backend
```

Mở `http://localhost:8088`.

Kiểm tra nhanh:

```powershell
$env:BASE_URL="http://localhost:8088"
# Nếu dùng Git Bash/WSL:
# BASE_URL=http://localhost:8088 ./smoke.sh
```

Trên Windows PowerShell, có thể gọi trực tiếp:

```powershell
Invoke-RestMethod http://localhost:8088/actuator/health
```

### 2.4 Tạo admin đầu tiên

1. Mở `http://localhost:8088/register`, đăng ký username `admin`
   (email bất kỳ, ví dụ `admin@local.test`, mật khẩu ≥ 8 ký tự).
2. Tài khoản mới ở trạng thái `PENDING` và chưa đăng nhập được.
3. Sửa `deploy/.env`:
   ```dotenv
   BOOTSTRAP_ADMIN_USERNAME=admin
   ```
4. Recreate backend một lần:
   ```powershell
   cd D:\task\deploy
   docker compose up -d --force-recreate backend
   ```
5. Bootstrap sẽ activate admin, cấp `SYSTEM_ADMIN` (reason `BOOTSTRAP`) và tạo
   workspace cá nhân. Đăng nhập bằng `admin` + mật khẩu đã đăng ký.

Các user khác tự đăng ký và được duyệt qua `/admin` bởi tài khoản có grant
`ACCOUNT_APPROVE_GLOBAL`. Bootstrap chỉ cấp `SYSTEM_ADMIN`. Với cài đặt mới,
cần hoàn tất cấp quyền duyệt qua quy trình quản trị trước khi tạo fixture smoke.
Nếu muốn đóng đăng ký công khai, đặt `SELF_REGISTRATION=false` sau khi đã tạo
đủ tài khoản; khi đó cần tạo user qua API admin (chưa có form tạo user trên web).

---

## 3. Thiết lập CI/CD

Workflow **Production validation** chạy khi mở/cập nhật PR, push lên `main`
hoặc `codex/ci-cd`, và khi chạy thủ công. Check **SmartTask release gate**
yêu cầu tất cả job kiểm thử, bảo mật và build thành công. Maven chạy các lớp
`*Test,*IT` với PostgreSQL và MinIO thật, gồm kiểm tra Flyway. JUnit XML và
kết quả OSV nằm trong artifacts. Danh sách ca được phép skip nằm trong
`scripts/check-test-reports.py`. Để kiểm tra nhánh mà không deploy, chạy PR
hoặc chọn `deploy=false`.

Workflow chạy trên `main` sẽ publish hai image với tag SHA đầy đủ
`ghcr.io/leminhhoai1011/task-manager-{backend,web}:<sha>`. Bước deploy dùng
digest từ cùng lần chạy workflow. Nó chỉ chạy khi chọn `deploy=true` trên
`main` và qua phê duyệt của environment `production`.

1. Trong Settings → Rules/Branches, yêu cầu PR được duyệt và check
   **SmartTask release gate** từ GitHub Actions, bật yêu cầu cập nhật với main,
   cấm force-push, xóa nhánh và bypass. Chọn check sau khi nó xuất hiện trong
   một lần chạy thành công. Yêu cầu người có quyền duyệt xác nhận thay đổi
   workflow, danh sách skip, migration và script deploy.
2. Tạo environment `production`, chỉ cho phép nhánh `main`, bật required
   reviewers, prevent self-review và tắt admin bypass nếu gói hỗ trợ. Giữ
   environment chưa cấp SSH key nếu chưa thiết lập được bước phê duyệt.
3. Đặt environment secrets `DEPLOY_HOST` (DNS hoặc IP, SSH port 22),
   `DEPLOY_SSH_KEY` (private key riêng cho deploy), và `DEPLOY_KNOWN_HOSTS`
   (dòng OpenSSH đã đối chiếu fingerprint từ console VPS, ví dụ dùng
   `ssh-keygen -lf /etc/ssh/ssh_host_ed25519_key.pub`). Quyền GHCR publish
   dùng `GITHUB_TOKEN` có `packages: write` trong job publish; cho phép
   repository này ghi hai package nếu đã tồn tại.

Tham khảo [branch protection](https://docs.github.com/en/repositories/configuring-branches-and-merges-in-your-repository/managing-protected-branches/about-protected-branches)
và [deployment environments](https://docs.github.com/en/actions/how-tos/deploy/configure-and-manage-deployments/manage-environments).

## 4. Chuẩn bị Ubuntu 24.04

- Cài Docker Engine, Compose v2.24.4+, `jq`, `curl` và `util-linux`.
  Bật Docker khi boot bằng `sudo systemctl enable --now docker`. Máy 4 GB
  dùng giới hạn RAM trong Compose và chỉ chạy một cặp backend/web. Không đặt
  GitHub runner hay Ollama trên VPS này. Dành đủ dung lượng để chứa image
  cũ, image mới và toàn bộ bộ backup.
- Chép thư mục `deploy/` đã duyệt vào `/opt/smarttask/deploy`. Thư mục, script,
  Compose và cấu hình trên host do root sở hữu. Giữ tên project/mount hiện tại
  (`smarttask` mặc định) khi chuyển từ hệ thống cũ. Giữ nguyên `.env` của
  instance đang chạy. Với máy mới, tạo từ `.env.example` và đặt quyền 0600.
  Cấu hình DNS cho `DOMAIN` và mở cổng TCP 22, 80, 443 và UDP 443 nếu dùng HTTP/3.
- Nếu GHCR private, đăng nhập trên host dưới root bằng credential chỉ có quyền
  đọc package (`docker login ghcr.io --password-stdin`). Runner chỉ gửi SHA
  và digest qua SSH. Nếu instance hiện tại dùng giá trị `VITE_*` khác mặc định,
  đặt repository variables `VITE_API_BASE`, `VITE_ENABLE_AI` và
  `VITE_SECURE_BROWSER_SESSION` tương ứng trước khi build. Đây là giá trị
  công khai được đóng gói vào SPA. Thay đổi chúng cần build image mới.
- Tạo user riêng `smarttask-deploy` với quyền đăng nhập bằng đúng key tự động.
  Không thêm vào nhóm `docker`. Dùng `visudo` để cho phép duy nhất lệnh cố định
  sau, với `""` giới hạn lệnh sudo không có đối số.

  ```sudoers
  smarttask-deploy ALL=(root) NOPASSWD: /opt/smarttask/deploy/update.sh ""
  ```

  Trong `authorized_keys` của user, dùng một dòng

  ```text
  restrict,command="/usr/bin/sudo -n /opt/smarttask/deploy/update.sh" ssh-ed25519 AAAA... smarttask-ci
  ```

  Đặt quyền chạy cho `update.sh` đã được root kiểm tra. Không cấp quyền ghi
  thư mục deploy hoặc cấp shell tương tác qua key này.

### 4.1 Khởi tạo và chạy thử

Trên instance Ubuntu x86_64 thử nghiệm riêng, lấy hai digest từ cùng lần publish đã
PASS. Dùng riêng tài khoản, volume, domain/port và dữ liệu thử nghiệm. Trên
máy mới, quản trị viên khởi tạo bằng các image đã xác minh.

```bash
cd /opt/smarttask/deploy
# release.images contains BACKEND_IMAGE and WEB_IMAGE, both full GHCR digests.
dc() { docker compose --env-file .env --env-file release.images -f docker-compose.yml -f compose.release.yml "$@"; }
dc pull
dc up -d --no-build --pull never --wait --wait-timeout 240
dc ps
```

Với stack đang chạy, bỏ qua khởi tạo. Tạo admin đầu tiên theo mục 2.4 bằng
Compose với image đã phát hành. Tạo hai tài khoản ACTIVE chuyên cho smoke,
một tài khoản có quyền đọc/đính kèm vào một task riêng trong project ACTIVE,
một tài khoản không có quyền truy cập task. Dùng hai tài khoản thường.
Ghi `.env.smoke` quyền 0600 theo cú pháp shell, quote các giá trị bí mật.
`BASE_URL` là URL HTTPS của instance, hoặc HTTP của môi trường thử cô lập.

```bash
BASE_URL='https://app.example.com'
SMOKE_USER='smoke-writer'
SMOKE_PASSWORD='…'
SMOKE_OUTSIDER_USER='smoke-outsider'
SMOKE_OUTSIDER_PASSWORD='…'
SMOKE_TASK_ID='…'
```

Chạy `(set -a; . ./.env.smoke; SMOKE_REQUIRED=1 sh ./smoke.sh)`. Smoke bắt
buộc kiểm tra health, giao diện, đăng nhập, API, truy cập trái quyền,
SSE ticket dùng một lần và upload/download so từng byte. Nó xóa mềm
attachment thử. Audit và dữ liệu vòng đời file có thể vẫn tồn tại, vì vậy
chỉ dùng task dành riêng cho kiểm tra.

Chạy thử cập nhật và smoke thất bại trên staging trước khi cấp key production.
Có thể chạy kiểm thử logic cục bộ bằng `sh deploy/test-update.sh` tại gốc repo.
Cấu hình GitHub environment production phải trỏ vào host production chỉ sau
khi xác nhận runbook staging. Dùng key staging riêng khi chạy thử SSH.

### 4.2 Log, backup và rollback

```bash
docker compose ps -a
docker compose logs --tail=150 db minio backend web
sudo journalctl -u docker --since today
```

Workflow log ghi trạng thái SSH và từng bước. Script khóa bằng `flock`,
pull trước khi dừng dịch vụ, rồi dùng backup hiện có để quiesce các writer,
xác minh SHA-256, đọc đầy đủ PostgreSQL dump và kiểm tra hai tarball. Log
backup có mã thời gian UTC. Giữ backup mã hóa ngoài máy. Nếu chạy backup
định kỳ, dùng cùng khóa (tại thư mục deploy, `flock -n .release.lock sh ./backup.sh`).
Chỉ dọn backup và image cũ sau khi kiểm tra còn bộ đủ và image cần để rollback.

Khi health hoặc smoke fail, script dừng app mới. Chỉ tự rollback nếu hash
migration của hai image trùng nhau và Flyway history thực tế khớp bản sao
trước deploy. Đây là kiểm tra bảo thủ, bao gồm trường hợp image legacy thiếu
label hoặc migration thay đổi dù tương thích. Rollback thành công vẫn trả
về lỗi để workflow giữ trạng thái FAIL. `previous.images` giữ cặp image
liền trước lần deploy thành công. Với rollback thủ công, khóa maintenance,
dừng web/backend, đối chiếu migration label và Flyway history với bộ backup,
rồi chạy `dc up -d --no-deps --no-build --pull never --wait backend web` bằng
cặp image rollback qua file env tương ứng. Chạy smoke rồi mới ghi nhận
`release.images`. Giữ nguyên DB và object storage.

### 4.3 Mất điện, Docker restart và restore có phê duyệt

1. Kiểm tra `systemctl status docker`, `docker compose ps -a` và log.
   Compose dùng `restart: unless-stopped` cho dịch vụ chính. Nếu DB hoặc
   MinIO đang dừng sau khi backup bị ngắt, kiểm tra log và khởi động lại hạ
   tầng bằng `docker compose start db minio`.
2. Nếu có `.release.pending`, giữ web/backend dừng trong khi đối chiếu
   image ID của containers, digest ứng viên, image rollback, dump và
   Flyway history. Nếu schema tương thích và đã xác minh backup, khởi động
   cặp image phù hợp theo mục 4.2 và chạy smoke. Chỉ xóa journal sau khi
   hoàn tất kiểm tra và ghi cặp image thực sự đang chạy.
3. Nếu dữ liệu hỏng hoặc schema không tương thích, ghi lại trạng thái và xin
   phê duyệt recovery. Thử phục hồi bộ backup đã kiểm checksum trên instance
   cô lập trước. Khi được phê duyệt, tham khảo checklist thử restore
   trong [backup/restore](docs/09-BACKUP-RESTORE.md) và lệnh
   `sh ./restore.sh --yes <db.dump> <minio.tgz> <files.tgz>` dưới cùng khóa
   maintenance. Restore có thể thay thế dữ liệu hiện tại.

## 5. Mạng và vận hành

### 5.1 Cloudflare (tuỳ chọn)

Nếu dùng Cloudflare proxy:

- SSL/TLS mode: **Full (strict)**.
- Cho phép HTTP-01 đi qua (Cloudflare proxy thường vẫn qua được).
- Nếu Let's Encrypt HTTP-01 thất bại: tạm tắt proxy (grey cloud) để Caddy lấy
  cert rồi bật lại, hoặc cấu hình DNS-01 challenge trong Caddyfile.
- Không đặt SSL mode "Flexible" vì backend/Caddy sẽ bị redirect loop.

### 5.2 Tách web và API khác origin (tuỳ chọn)

Mặc định web + `/api` cùng origin qua Caddy, không cần CORS. Nếu host SPA ở nơi
khác (Vercel/Netlify) và API ở VPS:

1. Build web với `VITE_API_BASE=https://api.example.com/api/v1`.
2. Đặt `CORS_ALLOWED_ORIGINS=https://app.example.com` trong `deploy/.env`
   (danh sách origin chính xác, phân tách bằng dấu phẩy).
3. Sửa CSP trong `web/Caddyfile`: thêm origin API vào `connect-src`, ví dụ
   `connect-src 'self' https://api.example.com`.
4. Cấu hình Caddy/API chỉ phục vụ `/api/*` và `/actuator/*`; không cần phục vụ
   `/srv` nếu SPA ở nơi khác.

Nếu không cần tách, giữ nguyên cấu hình same-origin để giảm bề mặt tấn công.

### 5.3 Lưu ý bảo mật

- Chỉ publish 80/443. Không mở 5432/9000/9001 ra Internet.
- Dùng mật khẩu random ≥ 32 ký tự cho Postgres/MinIO; `.env` quyền `600`.
- Caddy tự bật HSTS, CSP, `X-Frame-Options`, `nosniff`.
- Caddy ghi đè `X-Forwarded-For` bằng IP thật, tránh bypass rate limit.
- Upload giới hạn 50MB ở backend (`spring.servlet.multipart`) và 64MB ở Caddy.
  Nếu tăng, sửa cả `backend/app/src/main/resources/application.yml` và
  `web/Caddyfile` để tránh 413 khó hiểu.
- Rate limit tầng app vẫn chạy; có thể thêm rate limit ở Caddy/Cloudflare.
- `SELF_REGISTRATION=true` vẫn cần admin duyệt; nếu không cần đăng ký công khai
  hãy đặt `false`.
- Backup định kỳ + copy off-site; test restore định kỳ.
- Cập nhật image `postgres:16`, `caddy:2-alpine`, MinIO legacy theo lịch.
- MinIO dùng image Bitnami Legacy vì image `minio/minio` không còn public trên
  Docker Hub. Pin tag và theo dõi nguồn image; nếu có S3/R2 riêng, có thể thay
  endpoint/credentials mà không đổi code.
- Không chạy `BOOTSTRAP_ADMIN_USERNAME` với user không mong muốn; bootstrap chỉ
  nên dùng một lần cho admin đầu tiên.


### 5.4 Giới hạn hiện tại

- **AI**: cần Ollama thật (`OLLAMA_BASE_URL`). Nếu không có, build với
  `VITE_ENABLE_AI=false` trong CI để ẩn màn AI; job AI sẽ lỗi `OLLAMA_DOWN` nếu vẫn gọi.
- **Push/email**: FCM/APNs/SMTP chưa có credentials; thông báo trong app vẫn
  hoạt động, push/email là stub.
- **Quên mật khẩu**: chưa có luồng reset qua email.
- **Realtime**: SSE một instance; chưa có multi-instance fan-out.
- **File**: MinIO single-node; chưa có malware scan thật (scan status là stub).
- **Reports**: có dashboard cá nhân/project + export; chưa có workspace/system
  dashboard và snapshot job.
- **Load test**: chưa có bằng chứng P95/load tier 2 trên hạ tầng thật.
- **Mobile**: không nằm trong bản web deploy này.
