# SmartTask — báo cáo nghiệm thu audit ngày 13/11/2026

**Kết luận: NOT READY. Không được triển khai production từ kết quả này.**

Đã sửa các lỗi phân quyền và toàn vẹn dữ liệu xác nhận được, chạy kiểm thử mới trên PostgreSQL thật, kiểm tra trình duyệt thật và diễn tập restore database. Chưa có bằng chứng MinIO thật, restore đầy đủ DB/objects/local files, HTTPS/staging Ubuntu và tải đại diện. Dependency backend còn nhiều advisory chưa phân loại/khắc phục. Build thành công không giải quyết các điều kiện chặn này.

## 1. Phiên bản và phạm vi

| Mục | Giá trị |
|---|---|
| Repository | https://github.com/LeMinhHoai1011/task-manager.git |
| Workspace | `D:\task` |
| HEAD nền | `ddb46878a559fcd8e4c77034b1e18930ef398066` |
| Nhánh lúc bắt đầu | `main`, working tree sạch |
| Nhánh bản sửa | `codex/production-audit-20261113` |
| Trạng thái thay đổi | Chưa commit, chưa push/merge; không deploy production |
| Ngày kiểm tra | 13/11/2026, Asia/Bangkok |
| Công cụ thực dùng | Windows; Java 21.0.9; Maven wrapper 3.9.11; Node 26.10.0; pnpm 9.15.4; PostgreSQL 16.6; Edge headless/Playwright có sẵn |
| Frontend sau sửa | React 18; TypeScript 5.6.3; Vite 6.4.6; ESLint 9.39.5; Vitest 5.0.3 |
| Backend | Spring Boot 3.2.5; Hibernate 6.4.4.Final; Flyway V1–V17 |

Đối chiếu `git ls-remote origin refs/heads/main` thành công: main GitHub trùng HEAD nền ở thời điểm kiểm tra. Không fetch/push hoặc thay đổi remote. Các thay đổi từ đợt nghiệm thu lịch sử `3d35760` được lưu riêng trong inventory. Không coi kết quả test trong `AUDIT-REPORT.md`, `PROJECT-REPORT-2026-11-09.md` hoặc log cũ là bằng chứng mới.

Inventory trước khi hoàn tất: 685 tracked files, 253 Java source, 30 controller, 62 TypeScript/TSX source, 17 migration; 121 file Java trong cây test, gồm cả lớp trợ giúp/base. Có khảo sát router, API/authorization, module, schema, deployment và test. Review sâu tập trung vào P0: identity/session, policy, Workspace, Notes, Files, Reports, jobs và cấu hình vận hành. **Không tuyên bố đã kiểm chứng thủ công mọi dòng của toàn repository hoặc nghiệm thu đầy đủ mọi API.** Mobile Flutter có trong repository nhưng không build/nghiệm thu trong lần này.

Toàn bộ database tạo mới cho audit nằm trong cluster riêng `D:\task\.run\production-audit-20261113\pgdata`, chỉ nghe `127.0.0.1:55439`. Không dùng database/volume/tài khoản thật. Không sửa Flyway đã có, không tạo migration mới, không xóa volume/bucket/database có sẵn, không reset/clean Git.

## 2. Kế hoạch và bảo toàn dữ liệu

Thực hiện: inventory/Git → khảo sát luồng và lỗi → sửa nhỏ tại service/repository/client hiện hữu → kiểm thử theo nhóm → hồi quy → runtime trình duyệt → restore database → tài liệu/gate phát hành. Staging và production không được tự triển khai.

Giữ kiến trúc modular monolith: React/Flutter → REST và SSE → Spring Boot → PostgreSQL/Flyway; file metadata trong PostgreSQL, bytes qua local/MinIO; AI qua Ollama phía backend. Không thay JWT: xác thực thực tế là **opaque Bearer session**, kiểm hash/session/user trong DB mỗi request. Các endpoint ticket public có lớp xác minh riêng; không coi `permitAll` tự động là IDOR hoặc bằng chứng an toàn.

Các bản sửa không đổi schema hay xóa dữ liệu lịch sử. Ghi chú legacy sai scope/quan hệ bị từ chối theo policy, không bị xóa/chuyển owner. Rollback code có thể khôi phục hành vi lỗi cũ; không nên rollback bảo mật một cách máy móc. Bản trước phải được backport fix hoặc có biện pháp được duyệt. Rollback ứng dụng không có nghĩa đảo migration.

## 3. Lỗi xác nhận và bản sửa

| Nhóm và file chính | Lỗi/nguyên nhân | Bản sửa, giới hạn và xác nhận |
|---|---|---|
| Notes: `note/service/NoteService.java`, `note/repo/NoteRepository.java` | `listNotes(projectId/taskId)` truy vấn trực tiếp; `TASK` có nhánh đọc mặc định cho qua; owner được bỏ qua kiểm tài nguyên; `requireWrite` không có tác dụng ở nhánh PROJECT/SHARED | Kiểm tài nguyên trước truy vấn; lọc từng candidate theo quyền thật; TASK dùng task visibility/personal owner; PROJECT đọc/ghi theo policy Project; SHARED phân biệt đọc và sửa; không có owner bypass khi tài nguyên bị thu hồi. `NoteSecurityIT`: 12 test PostgreSQL thật, nhiều account/role. |
| Notes create/move/restore | Tạo và đổi scope không kiểm tài nguyên đích/quan hệ, scope bất kỳ; restore chỉ kiểm owner | Validate scope và liên hệ task/project/workspace; derive quan hệ từ tài nguyên thật; chỉ owner được move; restore vẫn kiểm quyền hiện tại. PERSONAL xóa liên kết chia sẻ trên bản ghi được tạo/cập nhật, không tự cập nhật dữ liệu legacy. Lỗi input sai trả 400/404/403 đúng tình huống. |
| Notes concurrency: service/controller, `common/ApiExceptionHandler.java`, `web/src/pages/Notes.tsx` | Header version sai bị bỏ qua; DTO trả trước flush có thể mang version cũ; autosave không gửi version và nuốt lỗi | Header sai trả 400; `saveAndFlush` trả version hiện tại; optimistic conflict ánh xạ 409; frontend gửi `If-Match`, hiển thị lỗi lưu, lấy lại version khi pin. Header PUT còn optional để giữ tương thích client cũ: client không gửi version chưa có bảo vệ stale-edit tuần tự tương đương web mới. |
| Workspace: `workspace/service/WorkspaceService.java` | Remove/leave chỉ đổi workspace membership; project membership còn ACTIVE | Thu hồi project membership trong cùng transaction cho các project của workspace. Không đổi guard chủ sở hữu/last manager. Hai test xác minh mất quyền ngay sau remove và leave. Việc mời trực tiếp vào project mà chưa thuộc workspace vẫn theo mô hình hiện hữu. |
| Files IDOR: `files/service/FileService.java`, `files/FileController.java` | Commit chỉ authorize task của upload, có thể chỉ định attachment thuộc task khác; idempotency key trả upload của actor khác | Bắt attachment.taskId trùng upload.taskId; kiểm owner/task của idempotent upload; thao tác staging/complete/commit chỉ nhận upload của owner. Negative tests từ chối trước khi chạm storage. Không mở rộng quyền upload của Member. |
| Files restore | Helper chặn attachment đã soft-delete ngay cả ở endpoint restore-attachment | Cho phép helper resolve bản đã xóa riêng cho restore; vẫn kiểm task write. MinIO end-to-end của restore này chưa chạy. |
| Files concurrent/rollback: service và repository Attachment/UploadSession | Hai commit có thể cùng chọn `vN` và copy đè object trước khi DB báo xung đột; xóa staging trước DB commit làm mất khả năng retry khi transaction rollback | Khóa upload session và attachment khi ghi; các đường restore-version/delete/restore dùng cùng khóa attachment; kiểm expiry khi commit. Chỉ xóa staging trong afterCommit. Test PostgreSQL + filesystem thật: hai commit đồng thời tạo v1/v2/v3, so SHA-256 và bytes từng version; rollback giữ staging và retry được; session READY quá hạn bị chặn. **Không tính đây là kiểm thử MinIO.** Orphan object sau DB rollback vẫn cần reconciliation thực. |
| Reports: `reports/service/ReportService.java`, repository Assignment/ApprovalRound | Task đã unassign vẫn được tính nếu chỉ EXISTS; thống kê approval lấy mọi task của Project dù người đọc chỉ được xem subset | Chỉ lấy ACTIVE assignments; approval statuses theo đúng visible task IDs; history mất quyền sau unassign. `ReportIsolationIT` xác minh manager thấy 2 approval, member thấy 1 rồi 0 sau thu hồi. |
| Web Push: `notify/service/PushDeliveryService.java`, `VapidController.java`, `NotificationProcessor.java`, `PushEmailGateway.java`, `web/src/pages/Settings.tsx` | Scheduler, endpoint send và retry cập nhật thành công dù không có provider; danh sách delivery in-memory tăng không giới hạn | Scheduler không fake-send; send endpoint trả lỗi provider disabled; external attempt `FAILED_PERMANENT/PROVIDER_DISABLED`; retry không dựng SENT giả. Gateway trả DISABLED và giữ tối đa 100 diagnostic entries; UI tắt nút, nói rõ chưa hỗ trợ. IN_APP/SSE độc lập vẫn hoạt động. Không triển khai provider mới. |
| Rate limiting: `common/RateLimiter.java` | Get/put không atomic dù dùng ConcurrentHashMap; không dọn key | Đồng bộ cập nhật window, dọn window hết hạn định kỳ và fail closed khi đạt 10.000 key. Test 100 lời gọi đồng thời chỉ cho đúng hạn mức AUTH; kiểm cap và reclaim. Các threshold không tự tăng để làm browser test qua. Cần load test chi phí lock và cân chỉnh rate cho nhóm dùng chung IP. |
| Bootstrap: `application.yml`, Compose và `.env.example` | Username admin được cấu hình mặc định, có nguy cơ bootstrap nhầm account đăng ký trước | Default rỗng, dùng biến môi trường bootstrap được ghi rõ. Không tạo/sửa account thật. Cài đặt đã có `.env` phải review giá trị đang dùng; mẫu mới không tự ghi đè file đó. |
| Frontend tooling: `web/package.json`, lockfile, ESLint config; `scripts/test-local.ps1` | `lint` thực chất là `tsc`; `verify` không chọn `*IT` trong POM hiện tại | Tách typecheck/ESLint, cài parser và plugin có phiên bản; lint phát hiện runtime hazards, không tự nhận là bộ hooks audit toàn diện. Script local chọn IT rõ ràng và yêu cầu URL test; không báo PASS integration chỉ vì Maven verify chạy unit test. |
| Frontend dependencies | Scan đầu có 4 High, 8 Moderate | Vá Vite 6.4.4 → 6.4.6 và override undici 8.x lỗi → 8.12.2 cùng major. Scan mới 0 High/Critical, 3 Moderate. Không tự nâng Router 6 → 7 hoặc minimatch lên major khác. |
| Mobile layout Settings: `web/src/styles.css`, `Settings.tsx` | Nội dung có chiều rộng intrinsic làm trang tràn ngang tại 390px | Giới hạn width/min-width của content ở mobile; không thiết kế lại giao diện. Screenshot và phép đo overflow sau sửa PASS. |
| Build contexts/ignore | Backend/web `.dockerignore` chưa chặn `.env` rõ ràng; log Maven được tracked | Thêm ignore secret/build artifact cần thiết. `%MAVEN_OUT%` giữ nguyên trên disk và trong index; chỉ bổ sung ignore và đề xuất bỏ tracking khi được duyệt. |
| Ops: `deploy/backup.sh`, `restore.sh`, `minio-init.sh`, Compose | Snapshot MinIO live; volume name cố định; restore không stop MinIO và lỗi có thể bị che; policy readwrite rộng; không giới hạn log | Backup quiesce writer/MinIO, inspect volume thực, schema/image inventory/checksum. Restore preflight, single-transaction/exit-on-error, stop MinIO, tránh shell interpolation tên archive. Init không che lỗi private-bucket/policy, app user riêng/bucket policy; log rotation 10 MiB ×3. **Mới kiểm cú pháp/config; chưa nghiệm thu runtime Docker của script.** |
| CI: `.github/workflows/production-validation.yml`, `.gitleaksignore`, `scripts/audit-maven-advisories.ps1` | Chưa có workflow build/test/security có thể làm gate | Thêm workflow PR/manual: type/lint/test/build, PostgreSQL/MinIO tests, dependency scan, history secret scan và Docker build; không deploy. Scan có finding hoặc lỗi sẽ fail, không `continue-on-error`. Chưa chạy GitHub Actions và chưa bật branch protection. |

Kiểm thử mới tập trung vào hành vi có rủi ro, không chỉ mirror implementation. Không disable test để né lỗi. Hai kiểm tra Flyway cũ mong V16 được sửa thành V17 kèm kiểm đủ 17 applied/0 pending/checksum validation. Test “platform không hỗ trợ” cũ dùng `WEB_PUSH` dù V17 đã hỗ trợ tên đó; giữ assertion 400 và đổi input sang platform thực sự không hợp lệ. Test retry cũ chỉ kiểm có SENT bất kỳ (có thể chính IN_APP) được tăng độ chặt để kiểm riêng PUSH và không có success giả.

## 4. Ma trận quyền được đối chiếu từ code

Không suy quyền theo tên role chung; quyền Project tách quyền Workspace và global grant.

| Actor | Workspace/Project | Task/Files | Notes |
|---|---|---|---|
| System Admin | Quản trị qua live global grant; không mặc định đọc nội dung private | Không tự bypass scope chỉ nhờ SYSTEM_ADMIN | PERSONAL vẫn owner-only; tài nguyên khác vẫn theo policy |
| Workspace OWNER/ADMIN | Quản lý workspace/thành viên theo service; không tự suy thành Project MANAGER | Theo project membership/grant riêng | Đọc SHARED trong workspace; management có thể sửa SHARED của người khác khi workspace writable |
| Workspace MEMBER | Một số thao tác workspace được cho phép theo policy; không quản trị role tùy ý | Không tự có quyền mọi task của workspace | Có thể tạo/sửa SHARED của mình khi workspace writable; chỉ đọc SHARED người khác |
| Project OWNER/ADMIN/MANAGER | Đọc/ghi theo trạng thái và manager guard | Đọc/quản lý task; file write theo task policy | PROJECT đọc/ghi theo Project; TASK theo task policy |
| Project MEMBER | Tham gia project ACTIVE | Task được giao: đọc/execution; metadata/assignment cần manager hoặc scoped custom grant phù hợp; Files không tự nâng quyền | Đọc PROJECT; không mặc định sửa PROJECT; đọc TASK khi có quyền task |
| GUEST | Quyền đọc phạm vi được cấp | Chỉ đọc task được giao; không mặc định ghi | PROJECT chỉ đọc; TASK theo quyền task; SHARED cần workspace membership |
| Người ngoài | Không có scope access | 404 với dữ liệu không được phép biết; 403 với write-denied trên dữ liệu đã thấy | Không có nhánh cho qua TASK hay chỉ cần biết UUID |

Các lớp IT/units kiểm admin/grant, manager/member/guest/outsider, phiên thu hồi, membership, task và export. Không coi ma trận trên thay thế việc thử toàn bộ mọi endpoint trên staging. UI vẫn có những control chưa phản ánh hết quyền resource-level; backend là nơi quyết định. Cần nghiệm thu đa account trên trình duyệt trước phát hành.

## 5. Kiểm thử mới và phân biệt kết quả lịch sử

Kết quả trong phần này được chạy mới ngày 13/11, không lấy từ báo cáo cũ. Các lần lỗi đều giữ log; kết quả cuối và giới hạn phải đọc cùng nhau.

| Kiểm tra | Kết quả cuối | Ghi chú |
|---|---|---|
| Backend bộ khả thi cuối | **459 PASS / 0 FAIL / 0 ERROR / 36 SKIPPED**; 495 test được chọn | PostgreSQL thật trên database đã restore, gồm concurrency và rollback; loại riêng StorageIT/F3ExportIT khi không có MinIO. Package BUILD SUCCESS lúc 15:26:43 +07 |
| Frontend unit/component | **27 PASS / 0 FAIL**, 12 test files | Log `web-test-release.log` |
| TypeScript | **PASS**, exit 0 | `web-typecheck-release.log` |
| ESLint thật | **PASS**, exit 0 | `web-lint-release.log`; khác typecheck |
| Production frontend build | **PASS**, exit 0 | Vite 6.4.6, bundle JS khoảng 436,55 kB / gzip 119,32 kB |
| Browser production preview | **20 PASS / 0 FAIL** | Login, 12 direct URLs, Notes create/autosave có version, Push disabled, 5 mobile overflow checks; không pageerror hoặc HTTP ≥400 trong lượt cuối |
| PostgreSQL dump/restore | **PASS trong phạm vi database test** | Database mới cùng instance; row count và fingerprint của cả 60 bảng khớp |
| API/Flyway trên DB restore | **16 PASS / 0 FAIL** ở lượt smoke riêng | ContextBootIT, NoteSecurityIT, ReportIsolationIT; bộ backend cuối tiếp tục chạy trên database restore |
| Gitleaks history | **PASS sau triage**, 22 commit / ~11,94 MB | 3 finding ban đầu đều là cùng ví dụ UUID Idempotency-Key ở docs; ignore đúng 3 fingerprint, không ignore toàn file/rule |
| pnpm audit mới | **FAIL gate dependency**, 3 Moderate / 0 High / 0 Critical | Hai advisory Router, một minimatch; chưa có chấp thuận giảm thiểu |
| Maven runtime advisory scan | **FAIL cần triage**, 107 match trên 109 dependency được hỏi | Đây là match từ OSV, không phải 107 đường khai thác đã chứng minh |
| Compose config | **PASS** | `docker compose ... config --quiet` với biến audit/mẫu, không xuất secret |
| Shell backup/restore/init | **PASS cú pháp** | `bash -n`, không thực hiện restore/deletion trên server |
| Git diff whitespace | **PASS** | `git diff --check` |
| MinIO runtime, Docker image build | **NOT_RUN** | Docker client có, Linux engine không sẵn sàng kể cả sau yêu cầu khởi động Docker Desktop |
| HTTPS/TLS staging, tải đại diện, off-host restore | **NOT_RUN** | Không có staging/Ubuntu/domain/backup object thật được cung cấp |

36 SKIPPED trong bộ backend: 34 yêu cầu Docker (integrity container, Flyway container, stream bytes/concurrency, session flow); 2 yêu cầu Ollama/live benchmark. Hai lớp không chọn khi thiếu MinIO có **15 test NOT_RUN** (StorageIT 8, F3ExportIT 7). Cần đối chiếu inventory nếu bổ sung test sau báo cáo; SKIPPED và NOT_RUN không phải PASS.

Không cộng lượt smoke 16 test vào 459 như thể đó là các test case khác nhau. Browser không bao phủ upload file: lượt browser production preview chạy trước lần hoàn tất bổ sung rollback/expiry của Files; nhóm Files cuối được xác minh lại trong bộ backend 495 test. Nghiệm thu browser storage trên MinIO vẫn NOT_RUN.

Các lần chạy thất bại trước kết quả cuối:

- Vitest lần đầu có ENOENT ở TEMP sandbox; đổi TEMP/TMP và chạy lại đủ suite, không sửa assertion.
- Mockito attach bị kẹt trong sandbox; các run có bằng chứng cuối chạy ngoài sandbox. Những process audit bị kẹt được nhận diện và dừng, không kill Java process của ứng dụng khác.
- Test DB chưa có, sau đó cluster init chọn mặc định chỉ 20 connections; nhiều cached Spring contexts làm cạn connection. Tạo/config cluster audit riêng, giới hạn Hikari/test cache và tăng **cluster audit** lên 100; không chỉnh DB thật.
- Browser sweep quá nhanh chạm rate 100/min. Giữ lần lỗi, chạy lại với nhịp request phù hợp, không nâng threshold hoặc spoof client IP cho lượt nghiệm thu browser.
- Một lần restore so sánh bị lệch audit_event/realtime_ticket vì runtime vẫn ghi; dừng mọi writer rồi tạo bộ mới và restore lại, khớp đủ 60 bảng. Không gọi lần đầu là restore PASS.
- Windows giữ JAR đang chạy nên Maven không rename được ở bước repackage dù test đã xong. Dừng đúng runtime audit, rồi package lại; đây không phải test failure được bỏ qua.

## 6. Kết quả theo nhóm nghiệm thu

| Nhóm | Trạng thái | Phạm vi bằng chứng / việc còn thiếu |
|---|---|---|
| Authentication | PASS giới hạn | Unit/IT session/login/revocation; live Docker session test vẫn skipped; không có email recovery đã nghiệm thu |
| RBAC | PASS giới hạn | Policy/grant và IT nhiều role; chưa có exhaustive endpoint + browser matrix trên staging |
| Workspace/Project | PASS giới hạn | Lifecycle suite và kiểm thu hồi quyền mới; cần acceptance với dữ liệu đại diện |
| Task/My Tasks | PASS giới hạn | Workflow/concurrency/personal API suites, browser smoke; chưa nghiệm thu tải và mọi thao tác UI |
| Notes | PASS giới hạn | 12 security IT, stale version, scope/resource checks, browser create/autosave; cần acceptance với dữ liệu legacy/nhiều ghi chú và client cũ |
| Kanban | PASS giới hạn | Component tests; workflow backend. Không xem component tests là drag/drop đa account thực tế |
| Calendar | PASS giới hạn | IT/timezone/visibility và browser smoke; chưa đủ mọi tổ hợp dữ liệu lớn |
| Dashboard/Reports | PASS giới hạn | Scope test mới, aggregate suites, browser; binary export/TTL cần MinIO NOT_RUN |
| Notifications | PASS giới hạn; external delivery DISABLED | Inbox/SSE tests và UI; không có email/Web Push/mobile delivery thực |
| File/MinIO | **NOT_RUN đầy đủ** | DB negative tests và concurrent/rollback local storage PASS; không có bằng chứng MinIO/restart/khôi phục object |
| PostgreSQL/Flyway | PASS giới hạn | V1–V17, validate, integrity và restore test; chưa thử bản sao dữ liệu thật/rollback app cũ trên staging |
| Docker/Caddy/HTTPS | **NOT_RUN runtime** | Cấu hình/cú pháp PASS; chưa build image, chưa TLS/domain/renewal thật |
| Backup/Restore | **NOT_RUN đầy đủ** | Database-only test PASS; chưa restore DB + MinIO + fallback/config đồng bộ trên host độc lập |
| Performance | **NOT_RUN nghiệm thu** | PerfIT chỉ foundation/visibility; không p50/p95/p99/load đại diện hoặc resource trace đạt ngưỡng |
| Monitoring/Logging | **FAIL độ sẵn sàng vận hành** | Log rotation đã cấu hình; metrics chưa nối đầy đủ, chưa cảnh báo backup/worker/OOM được thử |
| CI/CD | **NOT_RUN trên GitHub** | Workflow đã chuẩn bị; dependency gate hiện sẽ chặn; branch protection chưa bật |
| Deployment/Rollback | **NOT_RUN** | Runbook đã viết, chưa chạy trên staging/Ubuntu và chưa có previous image/restore MinIO đã nghiệm thu |

PASS giới hạn là kết quả của các kiểm tra được nêu, không phải chứng nhận toàn bộ nhóm production-ready.

## 7. Dependency và secret

Quét backend theo dependency graph resolve thực tế, không chỉ đọc POM. 107 advisory match thuộc 22 cặp package/version; đáng chú ý Boot 3.2.5, Framework 6.1.6, Security 6.2.4, Tomcat 10.1.20, Jackson 2.15.4, MinIO client 8.5.4, Bouncy Castle 1.70 và thư viện xử lý tài liệu. Phải xem affected range, điều kiện khai thác, cấu hình và fix version; không tự gán toàn bộ là Critical/High hoặc nói đã miễn nhiễm vì app không dùng một endpoint nào đó.

Việc cập nhật BOM/framework lớn, Router 6 → 7 hoặc thay dependency có breaking change cần phương án review và phê duyệt riêng; không tự thực hiện trong lượt sửa an toàn này. Cần security triage có bằng chứng và bản vá/mitigation được duyệt trước phát hành. Tham chiếu dữ liệu: [OSV](https://osv.dev/), [Spring security advisories](https://spring.io/security/), [Vite security advisories](https://github.com/vitejs/vite/security/advisories). Kết quả registry và version-match cụ thể nằm trong JSON bằng chứng.

Git history scan không phát hiện credential sau khi phân loại đúng false positives. Không có cơ sở để khẳng định image hay secret production không tồn tại ở môi trường chưa tiếp cận. `.env` hiện có không bị chỉnh sửa hoặc đưa vào báo cáo. Secret scanning là một lớp kiểm tra, không chứng minh không có bất kỳ dạng secret nào.

`%MAVEN_OUT%` là **Maven output log bị commit**, không phải source/config. Đã xác minh nội dung là tải dependency và build output. Đề xuất sau review: bỏ tracking bằng `git rm --cached -- '%MAVEN_OUT%'`, giữ file local; ignore đã thêm. **Chưa chạy lệnh đó, chưa stage/commit/xóa file** theo yêu cầu bảo toàn log và phê duyệt Git.

## 8. Phân loại phần còn lại

### Bắt buộc sửa/xác minh trước phát hành

1. Triage/vá dependency backend và 3 Moderate frontend; scan lại, ghi nhận mọi mitigation được duyệt. Không có cơ chế approval thì không tự waive.
2. MinIO thật: upload/commit/download SHA-256; concurrent version/retry/rollback; Unicode, MIME/bytes, file rỗng/hỏng/vượt giới hạn; ticket/IDOR; restart khi upload/commit; restart Docker rồi download. MIME allowlist hiện dựa nhiều vào declared MIME, chưa có nghiệm thu kiểm content signature/malware thực.
3. Diễn tập backup/restore trên host độc lập, đủ DB + objects + local fallback + cấu hình/credential/image; verify manifest, permissions, RPO/RTO, mã hóa/off-host và retention. Script restore hiện chưa nhận archive fallback files; không dùng làm quy trình phục hồi hoàn chỉnh.
4. Staging Ubuntu 24.04: volume/mount, image digest/scan, internal exposure, Caddy HTTPS/TLS/renewal, proxy headers, security headers, health/smoke và rollback app thực.
5. Load test theo ngưỡng được chấp thuận, trên dữ liệu đại diện và RAM ~4 GB; theo dõi resource/DB/storage/worker, không kết luận từ idle RAM hay một request.
6. Chạy bộ Docker tests đang skipped, StorageIT/F3ExportIT với MinIO thật; CI trên GitHub; review code và branch protection. Nghiệm thu browser nhiều role, mọi form và lỗi API, accessibility/theme/ngôn ngữ theo router.
7. Hoàn thiện monitoring/alerting và xử lý storage orphan. AfterCommit bảo toàn staging khi rollback nhưng không tạo distributed transaction PostgreSQL/MinIO; object orphan vẫn có thể tồn tại. Không chạy cleanup xóa hàng loạt khi chưa có đối soát.

### Có thể vô hiệu hóa an toàn nếu công bố rõ

- Web Push/mobile push/email: không có provider; external delivery DISABLED, không báo SENT giả.
- AI: có thể ẩn màn hình bằng build flag và không cấp grant AI; cần cấu hình network/provider nếu muốn dùng. Flag UI không phải API kill switch.
- Malware scanning/storage reconcile: hiện UNSUPPORTED/stub, không được quảng cáo là đã bảo vệ/quét sạch. Việc chấp nhận file thiếu malware scanner cần quyết định rủi ro của chủ hệ thống; không tự xem là đã được chấp thuận.

### Có thể phát triển sau khi các gate trên đạt

Tối ưu Notes pagination/N+1, đồng bộ readonly controls theo quyền resource, bổ sung accessibility/i18n/theme coverage, tối ưu dashboard và thông báo nâng cao. Bản audit không thiết kế lại UI hoặc thêm AI/provider mới để lấn ưu tiên P0.

## 9. Bằng chứng và cách tái lập

Thư mục bằng chứng local, được ignore để không commit log/backup/fixture: `D:\task\.run\production-audit-20261113`.

- Git/inventory: `remote-main.txt`, `tracked-files.txt`, `api-authorization-inventory.txt`, `web-route-inventory.txt`, `source-inventory.json`, `since-last-historical-acceptance.txt`, `diff-check.log`.
- Backend: `backend-final-verified.log`, `final-backend-summary.json`; các run trước được giữ nguyên. `verified-selected-reports/` chỉ chứa 116 XML suite được log cuối ghi là Running. Không cộng XML StorageIT/F3ExportIT cũ còn trong target từ lần chạy khác.
- Frontend: `web-*-release.log`, `web-release-exit-codes.json`; `browser-production-preview.log`, `browser-results.json` và script browser dùng lại trong thư mục audit.
- Security: `backend-dependencies.tgf`, `backend-advisories.json`, `backend-advisory-scan.log`, `web-dependency-audit-final.json`, `gitleaks-history.json` (đã redacted), `gitleaks-verified.log`.
- Restore: `postgres-restore-result.txt`, `postgres-restore-verified.log`, `database-quiesced-before.csv`, `database-quiesced-restored.csv`, `verify-db.sql`, `restored-database-smoke.log`. Dump test nằm trong thư mục ignore, không phải backup production.
- Infra: `compose-validation.log`, `shell-syntax.log`, `docker-start-result.log`. Những log này không phải bằng chứng container health/resource usage.
- Ảnh chụp Edge thực: `docs/screenshots/audit-20261113/` (desktop các trang, mobile 5 trang). `failure.png` là ảnh thất bại của lần trước, không dùng làm ảnh PASS; ảnh cuối được chụp lại sau sửa. Không tạo ảnh giả thay runtime.

Các lệnh local, kiểm thử, chuẩn bị Ubuntu, cập nhật, backup/restore, khắc phục sự cố và rollback nằm trong [hướng dẫn vận hành](PRODUCTION-OPERATIONS-2026-11-13.md). Runbook có bước cần phê duyệt; không có lệnh production nào đã được thực thi trong lần audit này.

## 10. Quyết định phát hành

**NOT READY** — các lỗi code đã sửa là tiến bộ có kiểm chứng, nhưng chưa đủ điều kiện vận hành với dữ liệu thật. Không dùng trạng thái CONDITIONALLY READY để né các gate thiếu MinIO, backup đầy đủ và dependency triage. Sau khi hoàn tất các việc chặn, chạy lại nghiệm thu trên đúng release/image/schema/config được phê duyệt rồi mới xem xét quyết định phát hành.
