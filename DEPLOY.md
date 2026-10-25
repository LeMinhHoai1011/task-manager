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
- Không cần Java/Maven/Node trên VPS: Dockerfile backend và web tự build trong
  container. Lần build đầu cần Internet để tải dependency.
- Nếu muốn build/chạy ngoài Docker để dev: Java 21 + Maven, Node 22 + pnpm.

> Repo này không phải git repo. Khi deploy, copy thư mục dự án lên VPS hoặc
> đưa lên git riêng của bạn. Không copy `deploy/.env` lên nguồn công khai.

---

## 2. Chạy production-like trên máy local (Windows/macOS/Linux)

### 2.1 Build backend

Backend được build **bên trong Docker** (multi-stage `maven` -> `jre`), nên máy
local/VPS chỉ cần Docker + Compose, không cần Maven/JDK. Lần build đầu cần
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

Các user khác: tự đăng ký -> admin vào `/admin` duyệt (`PENDING` -> `ACTIVE`).
Nếu muốn đóng đăng ký công khai, đặt `SELF_REGISTRATION=false` sau khi đã tạo
đủ tài khoản; khi đó cần tạo user qua API admin (chưa có form tạo user trên web).

---

## 3. Deploy lên VPS có domain + HTTPS

### 3.1 Chuẩn bị VPS

```bash
# Ubuntu
sudo apt update && sudo apt install -y ca-certificates curl
curl -fsSL https://get.docker.com | sudo sh
sudo usermod -aG docker "$USER"   # logout/login lại
docker compose version
```

Mở firewall (ví dụ UFW):

```bash
sudo ufw allow 22/tcp
sudo ufw allow 80/tcp
sudo ufw allow 443/tcp
sudo ufw allow 443/udp
sudo ufw enable
```

DNS: tạo bản ghi `A` (và `AAAA` nếu có IPv6) cho `app.example.com` trỏ về IP VPS.
Chờ DNS propagate (`dig +short app.example.com`).

### 3.2 Cấu hình

```bash
cd /opt/smarttask/deploy       # thư mục đã copy lên VPS
cp .env.example .env
chmod 600 .env
```

Sinh mật khẩu mạnh:

```bash
openssl rand -base64 36
```

Điền `.env`:

```dotenv
DOMAIN=app.example.com
HTTP_PORT=80
HTTPS_PORT=443
TZ=Asia/Ho_Chi_Minh

POSTGRES_DB=smarttask
POSTGRES_USER=smarttask
POSTGRES_PASSWORD=<random-1>
MINIO_ROOT_USER=smarttask-root
MINIO_ROOT_PASSWORD=<random-2>
MINIO_APP_USER=smarttask-app
MINIO_APP_PASSWORD=<random-3>
MINIO_BUCKET=smarttask

BOOTSTRAP_ADMIN_USERNAME=
SELF_REGISTRATION=true
CORS_ALLOWED_ORIGINS=

VITE_API_BASE=/api/v1
VITE_ENABLE_AI=false     # bật true nếu có Ollama thật
OLLAMA_BASE_URL=http://host.docker.internal:11434
JAVA_OPTS=-XX:MaxRAMPercentage=60 -XX:+UseG1GC
```

Không dùng lại mật khẩu mẫu. Không commit `.env`.

### 3.3 Build + chạy

VPS chỉ cần Docker + Compose; backend jar và SPA đều được build trong Docker:

```bash
cd /opt/smarttask/deploy
docker compose up -d --build
docker compose ps
```

Lần đầu sẽ tải Maven/npm dependency và build image (vài phút). Các lần sau
Docker dùng cache; khi chỉ đổi source backend, chỉ layer build lại.

Lần đầu Caddy sẽ xin certificate Let's Encrypt cho `DOMAIN`. Kiểm tra:

```bash
curl -fsS https://app.example.com/actuator/health
docker compose logs --tail=100 web
```

Nếu DNS chưa đúng hoặc port 80/443 bị chặn, Caddy sẽ không lấy được cert.

### 3.4 Tạo admin đầu tiên trên production

Làm giống mục 2.4:

1. `https://app.example.com/register` -> đăng ký `admin`.
2. `deploy/.env`: `BOOTSTRAP_ADMIN_USERNAME=admin`.
3. `docker compose up -d --force-recreate backend`.
4. Đăng nhập, đổi mật khẩu nếu cần, duyệt user khác trong `/admin`.

Sau khi có admin, có thể đặt `SELF_REGISTRATION=false` nếu mô hình chỉ dùng nội bộ
(và tạo user qua API admin hoặc mở lại tạm thời để user đăng ký).

### 3.5 Cloudflare (tuỳ chọn)

Nếu dùng Cloudflare proxy:

- SSL/TLS mode: **Full (strict)**.
- Cho phép HTTP-01 đi qua (Cloudflare proxy thường vẫn qua được).
- Nếu Let's Encrypt HTTP-01 thất bại: tạm tắt proxy (grey cloud) để Caddy lấy
  cert rồi bật lại, hoặc cấu hình DNS-01 challenge trong Caddyfile.
- Không đặt SSL mode "Flexible" vì backend/Caddy sẽ bị redirect loop.

### 3.6 Tách web và API khác origin (tuỳ chọn)

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

---

## 4. Vận hành

### 4.1 Logs

```bash
cd /opt/smarttask/deploy
docker compose logs -f backend
docker compose logs -f web
docker compose logs -f db
docker compose logs -f minio
```

### 4.2 Update

```bash
cd /opt/smarttask
# backup trước khi update (Flyway là forward-only)
cd deploy && ./backup.sh

# cập nhật source (git pull / rsync / copy), rồi rebuild trong Docker
docker compose up -d --build
docker compose ps
SMOKE_USER=admin SMOKE_PASSWORD='...' ./smoke.sh
```

`docker compose up -d --build` build lại backend + web từ source; không cần
Maven/JDK trên VPS. Nếu build lỗi, container cũ vẫn chạy.

Hoặc dùng script có sẵn (backup + build + recreate + smoke nếu có biến môi trường):

```bash
cd /opt/smarttask/deploy
chmod +x *.sh
SMOKE_USER=admin SMOKE_PASSWORD='...' ./update.sh
```

Nếu image build lỗi, stack cũ vẫn chạy (compose chỉ thay container khi build
thành công). Với thay đổi schema, luôn backup trước.

### 4.3 Rollback

Cách an toàn nhất: restore DB backup + chạy lại image/jar cũ.

```bash
cd /opt/smarttask/deploy
./restore.sh --yes backups/db-<stamp>.dump backups/minio-<stamp>.tgz
```

Sau đó deploy lại phiên bản code cũ. Không rollback riêng lẻ app nếu migration
đã chạy và schema đã đổi.

### 4.4 Backup tự động

```bash
chmod +x /opt/smarttask/deploy/*.sh
crontab -e
```

Thêm:

```cron
0 2 * * * cd /opt/smarttask/deploy && ./backup.sh >> /var/log/smarttask-backup.log 2>&1
```

Backup mặc định giữ 14 ngày trong `deploy/backups/`. Nên copy backup sang nơi
khác (S3/rsync) vì backup cùng máy không chống được mất VPS.

### 4.5 Restore

```bash
cd /opt/smarttask/deploy
./restore.sh --yes backups/db-20261025T020000Z.dump backups/minio-20261025T020000Z.tgz
```

`restore.sh` dừng web/backend, `pg_restore --clean`, thay dữ liệu MinIO, rồi
start lại. Đây là thao tác phá huỷ dữ liệu hiện tại; chỉ chạy khi có `--yes`.

### 4.6 Smoke test

```bash
cd /opt/smarttask/deploy
SMOKE_USER=admin SMOKE_PASSWORD='<mat-khau>' ./smoke.sh
```

Kiểm tra: health, login, đọc workspace có auth, lấy realtime ticket, mở SSE lần
đầu 200, dùng lại ticket 401.

### 4.7 Tự động kéo backup về máy local

Khuyến nghị **máy local tự pull** từ VPS (máy local thường sau NAT nên VPS
không push vào được). Trên VPS vẫn chạy `./backup.sh` theo cron ở mục 4.4.

Chuẩn bị SSH key không mật khẩu cho tác vụ định kỳ:

```bash
# Trên máy local
ssh-keygen -t ed25519 -f ~/.ssh/smarttask_backup -N ''
# Copy nội dung ~/.ssh/smarttask_backup.pub vào VPS:
#   ~/.ssh/authorized_keys của user dùng để scp
```

Windows (Task Scheduler, chạy hằng ngày):

```powershell
powershell -NoProfile -ExecutionPolicy Bypass `
  -File C:\smarttask\deploy\pull-backups.ps1 `
  -VpsHost 203.0.113.10 -User smarttask `
  -KeyFile "$env:USERPROFILE\.ssh\smarttask_backup"
```

Tạo task:

```powershell
schtasks /Create /SC DAILY /ST 03:00 /TN "SmartTask pull backups" `
  /TR "powershell -NoProfile -ExecutionPolicy Bypass -File C:\smarttask\deploy\pull-backups.ps1 -VpsHost 203.0.113.10 -User smarttask -KeyFile %USERPROFILE%\.ssh\smarttask_backup"
```

Linux/macOS (cron trên máy local):

```bash
# crontab -e
0 3 * * * cd /path/to/smarttask/deploy && VPS_HOST=203.0.113.10 VPS_USER=smarttask SSH_KEY=$HOME/.ssh/smarttask_backup ./pull-backups.sh >> $HOME/smarttask-pull.log 2>&1
```

Kết quả nằm trong `deploy/local-backups/<timestamp>/` và giữ `KEEP_DAYS` ngày
(mặc định 30). Đây là bản copy đầy đủ; nếu dữ liệu lớn, cân nhắc `rsync`
hardlink hoặc đẩy lên S3/R2 rồi local sync.

Lưu ý:
- Key định kỳ nên **chỉ đọc** thư mục backup trên VPS; có thể giới hạn thêm
  `from="<IP>"`, `no-pty`, `no-port-forwarding` trong `authorized_keys`.
- Backup chứa dữ liệu nhạy cảm: mã hoá nếu lưu cloud, và không commit
  `deploy/local-backups/` lên git.
- Nếu máy local tắt vào giờ hẹn, Task Scheduler/cron có thể bỏ lần đó; backup
  trên VPS vẫn còn 14 ngày để kéo bù.

---

## 5. Bảo mật & vận hành production

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

---

## 6. Giới hạn hiện tại (biết trước khi mở Internet)

- **AI**: cần Ollama thật (`OLLAMA_BASE_URL`). Nếu không có, build với
  `VITE_ENABLE_AI=false` để ẩn màn AI; job AI sẽ lỗi `OLLAMA_DOWN` nếu vẫn gọi.
- **Push/email**: FCM/APNs/SMTP chưa có credentials; thông báo trong app vẫn
  hoạt động, push/email là stub.
- **Quên mật khẩu**: chưa có luồng reset qua email.
- **Realtime**: SSE một instance; chưa có multi-instance fan-out.
- **File**: MinIO single-node; chưa có malware scan thật (scan status là stub).
- **Reports**: có dashboard cá nhân/project + export; chưa có workspace/system
  dashboard và snapshot job.
- **Load test**: chưa có bằng chứng P95/load tier 2 trên hạ tầng thật.
- **Mobile**: không nằm trong bản web deploy này.

---

## 7. Lệnh nhanh

```bash
# local production-like
cd deploy
cp .env.example .env
# sửa .env: DOMAIN=:80, HTTP_PORT=8088, VITE_ENABLE_AI=false
docker compose up -d --build
docker compose ps

# production
cd deploy
cp .env.example .env
# sửa .env: DOMAIN=app.example.com + secrets
docker compose up -d --build
docker compose logs -f web

# update an toàn (backup + rebuild + smoke)
SMOKE_USER=admin SMOKE_PASSWORD='...' ./update.sh

# backup / restore / smoke
./backup.sh
./restore.sh --yes backups/db-....dump backups/minio-....tgz
SMOKE_USER=admin SMOKE_PASSWORD='...' ./smoke.sh

# kéo backup về máy local (chạy trên máy local, không phải VPS)
VPS_HOST=203.0.113.10 VPS_USER=smarttask ./pull-backups.sh
```

---

## 8. Deploy hộ?

Để deploy thực tế lên Internet, cần thêm thông tin không có trong repo:

- IP/SSH của VPS (hoặc tài khoản PaaS),
- domain đã trỏ DNS,
- cách cấp quyền (SSH key / panel).

Không nên gửi mật khẩu production vào chat. Có thể cung cấp VPS + domain và
dùng SSH key tạm; khi có các thông tin đó mới chạy được bước `docker compose up`
trên server thật.
