# SmartTask — vận hành và cổng phát hành

Ngày: 13/11/2026, Asia/Bangkok. Tài liệu này là quy trình đề xuất cho bản audit; không phải chứng nhận đã triển khai. Chỉ chạy lệnh có thay đổi production sau khi người quản trị phê duyệt phiên bản, backup và cửa sổ bảo trì. Không chạy test bằng credential production.

## 1. Windows local và kiểm thử

Yêu cầu: JDK 21, Maven wrapper, Node đáp ứng dependency hiện tại (lần audit dùng 26.10.0), pnpm 9.15.4; PostgreSQL 16 và MinIO riêng cho test. Không tái sử dụng database cá nhân đang có để chạy integration test. Các test tạo dữ liệu và một số test cố tình thay đổi trạng thái.

```powershell
Set-Location D:\task
$env:JAVA_HOME='C:\Program Files\Java\jdk-21'
Set-Location backend
.\mvnw.cmd -version
# Cấu hình datasource và file.gateway riêng trước khi khởi động.
.\mvnw.cmd -pl app spring-boot:run
```

Trong terminal khác:

```powershell
Set-Location D:\task\web
pnpm install --frozen-lockfile
pnpm dev
```

Tệp `.env` phải được ignore; không copy giá trị của production vào máy dev. `VITE_*` được nhúng vào bundle công khai, tuyệt đối không chứa secret. Muốn proxy backend ở cổng khác: đặt `VITE_API_PROXY_TARGET` trong `web/.env.local`. Không mở Vite ra Internet.

```powershell
# Backend: chỉ thay các URL dưới đây bằng database TEST có thể tạo fixture.
$env:SMARTTASK_IT_DATABASE_URL='jdbc:postgresql://127.0.0.1:55439/smarttask_audit_20261113'
$env:SMARTTASK_F21_DATABASE_URL='jdbc:postgresql://127.0.0.1:55439/smarttask_f21_audit_20261113'
Set-Location D:\task\backend
.\mvnw.cmd -B -pl app -Dspring.datasource.hikari.maximum-pool-size=4 -Dspring.test.context.cache.maxSize=4 '-Dtest=*Test,*IT,!StorageIT,!F3ExportIT' package
```

Đây là bộ khả thi khi chưa có MinIO; không tính hai lớp bị loại khỏi lệnh là PASS. Maven mặc định không chọn các lớp `*IT`; `verify` đơn thuần trong POM hiện tại cũng không tự thêm chúng. Phải truyền selector rõ ràng như trên hoặc dùng workflow mới.

Chạy test storage thật sau khi MinIO TEST sẵn sàng:

```powershell
$env:SMARTTASK_IT_MINIO_ENDPOINT='http://127.0.0.1:9090'
$env:SMARTTASK_IT_MINIO_ACCESS_KEY='testaccess'
$env:SMARTTASK_IT_MINIO_SECRET_KEY='testsecret135'
.\mvnw.cmd -B -pl app '-Dtest=StorageIT,F3ExportIT' test
```

Bucket `smarttask-test` và account trên chỉ dành cho test. `F3ExportIT` hiện dùng tên bucket này cố định; không trỏ tới bucket thật. Một số Testcontainers test tự SKIP khi Docker không có; đọc XML Surefire để biết số lượng, không diễn giải thành PASS.

```powershell
Set-Location D:\task\web
pnpm typecheck
pnpm lint
pnpm test
pnpm build
pnpm audit --audit-level moderate
```

`typecheck` là TypeScript; `lint` là ESLint với parser TypeScript và các luật phát hiện lỗi runtime, chưa phải bộ kiểm tra React Hooks đầy đủ. Không đánh đồng hai bước. Nếu Vitest bị ENOENT ở thư mục tạm sandbox, dùng một thư mục TEMP/TMP riêng trong workspace rồi chạy lại; giữ log lần lỗi.

## 2. Chuẩn bị Ubuntu mới — chưa được thực thi

1. Kiểm tra `lsblk -f`, `findmnt`, `df -h`, `df -i`, `free -h`, swap và quyền thư mục dữ liệu. Xác nhận mount dữ liệu sau reboot trước khi tạo volume. Không format hoặc chuyển volume khi chưa có phê duyệt.
2. Cài Docker Engine/Compose từ nguồn chính thức; giới hạn SSH và chỉ mở 80/443 cho ứng dụng. Không publish PostgreSQL, MinIO, console hoặc backend. Truy cập quản trị qua SSH/VPN.
3. Checkout release đã duyệt. Tạo `deploy/.env` từ mẫu với `umask 077`; thay mọi `change-me`, chọn domain thật và credential ngẫu nhiên, khác nhau. Không in `docker compose config` có secret vào log; dùng `config --quiet`.
4. Để `BOOTSTRAP_ADMIN_USERNAME` trống mặc định. Trong cửa sổ provisioning được kiểm soát, đăng ký đúng account quản trị, đặt username đã xác minh rồi khởi động backend một lần để cấp grant. Bỏ cấu hình bootstrap sau khi kiểm tra; không để người ngoài đăng ký chiếm tên bootstrap trước.
5. MinIO app user phải khác root. Init mới tạo policy giới hạn bucket và không che lỗi gán policy. Với cài đặt cũ, kiểm tra policy `readwrite` đã gán từ trước: policy mới không tự thu hồi policy rộng cũ; quản trị viên phải review và thu hồi quyền dư có chủ đích. Không xóa user hoặc bucket.
6. Xác minh các image/tag còn tải được, quét vulnerability và ghi digest trước phát hành; cấu hình hiện tại chưa pin mọi image theo digest. Không coi nhánh Bitnami Legacy là bằng chứng image còn được hỗ trợ.
7. Đặt `VITE_ENABLE_AI=false` nếu không nghiệm thu Ollama. Cờ này chỉ ẩn giao diện; không phải công tắc tắt toàn bộ API AI. Không public Ollama; dùng mạng riêng/VPN, timeout và giới hạn worker. Lõi Task phải hoạt động khi AI lỗi.
8. Thử trên staging độc lập, có PostgreSQL và MinIO thật, trước khi phê duyệt `docker compose up -d --build` trên production.

Giới hạn hiện có: DB 700 MiB, MinIO 450 MiB, backend 900 MiB, web 160 MiB, init 128 MiB; JVM MaxRAMPercentage=60 (~540 MiB heap trong limit 900 MiB). Đây là ngân sách cấu hình, chưa phải kết quả load test. Cần đo RSS/native memory, connection, OOM, CPU, IO và disk pressure khi có tải. Giữ dung lượng trống ít nhất 20% làm ngưỡng vận hành ban đầu; xác nhận lại theo dữ liệu thực. Swap không thay cho RAM đủ dùng.

## 3. Backup nhất quán

`deploy/backup.sh` đã sửa để:

- Xác định volume thực từ container của Compose project, không dùng cứng `smarttask_*`.
- Dừng web/backend và MinIO trong cửa sổ backup; DB vẫn chạy để `pg_dump`.
- Sao chép volume MinIO khi MinIO đã dừng, lưu local fallback files cùng bộ.
- Ghi image inventory, Flyway schema history và SHA-256 manifest; dùng quyền tệp hạn chế.
- Khởi động lại đúng những service đang chạy trước backup, kể cả khi có lỗi; mã thoát vẫn phản ánh lỗi.
- Không tự xóa file bất kỳ trong `BACKUP_DIR`.

Trước khi dùng trên production: chạy thử và đo thời gian gián đoạn; ngăn mọi writer ngoài stack; không dùng mô hình này cho nhiều replica/server chưa có quy trình điều phối. Chỉ có manifest không chứng minh restore được.

Lịch đề xuất: backup hàng đêm 02:00 giờ máy chủ, retention 14 bộ ngày + 8 bộ tuần. Chỉ dọn **bộ backup hoàn chỉnh đã xác minh**, giữ bản cuối cùng restore được; script hiện không tự thực thi retention. Cron phải kiểm tra exit code và cảnh báo khi thất bại; thất bại liên tục hoặc tuổi bản off-host >24 giờ là cảnh báo vận hành. Mục tiêu ban đầu RPO 24 giờ, RTO 4 giờ; chưa được đo/nghiệm thu.

Backup có dữ liệu nhạy cảm: mã hóa bằng GPG/age hoặc kho backup có mã hóa trước khi chuyển ra ngoài máy chủ; khóa giải mã giữ riêng. Backup script hiện tạo archive plaintext tại chỗ với quyền hạn chế, **chưa tự mã hóa và chưa đẩy off-host**. Không đặt thư mục backup dưới web root. Bảo quản `.env`/TLS và credential MinIO bằng bản cấu hình được mã hóa riêng; image inventory không thay thế chúng.

## 4. Restore diễn tập và sự cố

Thực hiện trên máy/project độc lập trước. Kiểm SHA-256, giải mã, xác nhận cùng timestamp của DB/MinIO/local files và image/schema tương ứng. Đọc `pg_restore --list`; kiểm `gzip -t`/danh sách archive. Không dùng lệnh restore thử trên production.

`deploy/restore.sh --yes <db.dump> [minio.tgz]` là công cụ **thay thế dữ liệu**, không được tự động gọi. Bản sửa kiểm input trước khi dừng service, dừng MinIO khi thay objects, chọn đúng volume thực, dùng `pg_restore --single-transaction --exit-on-error` và không tiếp tục khi lỗi. PostgreSQL và MinIO không có distributed transaction: nếu bước object restore lỗi sau DB, giữ ứng dụng dừng và phục hồi lại toàn bộ bộ nhất quán. Không tự mở ứng dụng với DB/objects lệch nhau.

Giới hạn còn lại: script restore hiện không nhận archive `files-*.tgz`, không tự kiểm manifest và không xác minh app-user sau restore. Nếu có local file/export fallback, phải khôi phục volume đó vào môi trường độc lập từ cùng bộ trước khi mở app. Không dùng script này làm quy trình phục hồi hoàn chỉnh cho dữ liệu thật cho tới khi diễn tập đủ ba phần. Không trộn backup MinIO của thời điểm khác với DB. Không giả định user/policy/config MinIO tự được tái tạo đúng chỉ vì object archive giải nén thành công.

Sau restore: chạy Flyway validate, kiểm row counts/FK, login nhiều vai trò, Notes và Task, file SHA-256, phiên hết hạn/thu hồi, restart containers rồi download lại. Ghi thời gian RTO, lượng dữ liệu RPO, dung lượng và người nghiệm thu. Kiểm tra object tồn tại cho mọi file_version, thiếu/orphan và quyền download chéo tài khoản. Chỉ sau đó mới nghiệm thu bộ backup.

## 5. Cập nhật và rollback

Luồng được duyệt: **backup đã kiểm chứng → kiểm cấu hình/image digest → build phiên bản đã review → staging migration → health + API smoke + browser/RBAC → giám sát → phê duyệt production**. Workflow mới chỉ validation, không deploy. Chưa bật branch protection trên GitHub trong lần làm việc này.

Lưu image/tag/digest và cấu hình của release trước ngoài build cache. Không dựa vào `latest` hay image còn trên máy. Migration hiện có V1–V17 được giữ nguyên. Không sửa checksum hoặc chạy `flyway repair` để bỏ qua sai lệch.

Rollback ứng dụng: chỉ dùng image cũ khi đã chứng minh schema hiện tại tương thích. Không mặc định rollback database. Với schema không tương thích, quản trị viên phải phê duyệt thời điểm dừng ghi, chấp nhận RPO và restore **cả DB + objects + local fallback** từ cùng bộ đã kiểm chứng; dữ liệu ghi sau backup cần kế hoạch đối soát. Flyway không tự đảo migration. Không dùng các SQL rollback lịch sử trên dữ liệu thật mà chưa review.

`update.sh` hiện chỉ là công cụ bảo trì; không tự pull/push/merge và không thay thế gate phê duyệt. Không xem thông báo “Update complete” là nghiệm thu nếu smoke test chưa chạy.

## 6. Health, logging và xử lý lỗi

- `docker compose ps`, `docker stats --no-stream`, `docker compose logs --since 15m backend db minio web`; tránh xuất token/password, request body hoặc query ticket vào báo cáo công khai.
- `/actuator/health` kiểm DB và HTTP status; chưa kiểm đầy đủ file storage/background processing. Phải upload/download bằng tài khoản smoke trong môi trường được phép.
- Caddy chỉ reverse proxy `/actuator/health`, chặn actuator khác; kiểm TLS tự gia hạn, redirect HTTPS, CSP/security headers bằng domain staging thật.
- Log rotation mới giới hạn 10 MiB × 3/container. Theo dõi filesystem/inode, không chạy dọn volume/image hàng loạt; xem trước đối tượng và retention đã duyệt.
- PostgreSQL: kiểm `pg_stat_activity`, pool size, slow query, disk/WAL; không sửa production max_connections theo test. Lần audit dùng cluster riêng có cấu hình init mặc định chỉ 20 connection và đã tăng **cluster test** lên 100.
- MinIO: kiểm bucket private, app policy, quota/disk và credential. Storage reconciliation/malware scan hiện còn stub; không tự xóa orphan.
- Metrics route hiện dùng registry in-memory chưa nối đầy đủ request recorder/monitoring; cần giám sát uptime, HTTP error/latency, backup freshness, worker backlog và OOM trước pilot.
- Web Push/mobile push/email: DISABLED/không có provider thực; IN_APP và SSE là kênh riêng. Không thông báo đã gửi email hoặc push thành công.

## 7. Hiệu năng — ngưỡng đề xuất, chưa nghiệm thu

Staging đại diện: 5 workspace, 20 project, ít nhất 5.000 task, 1.000 notes, file đại diện 1–50 MiB và 20 người dùng đồng thời trong 30 phút. Đo p50/p95/p99, HTTP error rate, RSS/CPU/IO, connection và thời gian tải trang. Ngưỡng dự thảo trước đo: API đọc p95 <500 ms, p99 <1.500 ms; ghi p95 <1.000 ms; lỗi ngoài 4xx dự kiến <1%; không OOM, không tăng bộ nhớ/queue vô hạn, RAM host <85% duy trì, trang chính sử dụng được <3 giây trên mạng staging được mô tả. Upload/download có ngưỡng riêng theo băng thông và kích thước; không dùng các ngưỡng API nhỏ cho file 50 MiB.

Các ngưỡng này cần chủ hệ thống chấp nhận trước phép đo nghiệm thu, không được dùng test đơn lẻ `PerfIT` làm bằng chứng đạt SLA production.
