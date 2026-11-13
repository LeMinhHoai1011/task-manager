# Báo cáo tổng quan dự án SmartTask

**Ngày khảo sát:** 09/11/2026 (Asia/Bangkok)  
**Phạm vi:** toàn bộ mã nguồn, cấu hình, migration, hạ tầng, kiểm thử và tài liệu có trong thư mục repository tại thời điểm khảo sát.  
**Lưu ý phương pháp:** đây là báo cáo đọc mã nguồn và đối chiếu các kết quả kiểm thử đã lưu. Không có build/deploy/test mới nào được thực thi trong lần lập báo cáo này. Các số liệu kiểm thử lịch sử được ghi rõ ngày và giới hạn.

## 1. Tóm tắt điều hành

SmartTask là hệ thống quản lý công việc cá nhân và nhóm theo mô hình **modular monolith**. Hệ thống có ba client: web React, mobile Flutter và backend REST Spring Boot. Dữ liệu chính nằm trong PostgreSQL, file được lưu qua local storage hoặc MinIO/S3-compatible storage; frontend nhận cập nhật gần thời gian thực bằng SSE. Hệ thống còn có mô-đun AI dùng Ollama thông qua backend, không cho client gọi trực tiếp model.

Phạm vi chức năng khá rộng: định danh/phiên đăng nhập, workspace và dự án, task/subtask, quy trình nghiệm thu, nhóm, phân quyền theo dự án, lịch/nhắc hạn, thông báo, tệp đính kèm, ghi chú, báo cáo/xuất dữ liệu, AI, audit và background jobs. Migration đã có đến **V17**; web có 23 trang và mobile có các màn hình tương ứng cho các luồng chính.

Mức độ hoàn thiện là tốt ở lõi nghiệp vụ và có bằng chứng kiểm thử lịch sử đáng kể. Tuy nhiên chưa nên xem là production-ready toàn diện: push/email thực vẫn là stub, quét mã độc và reconciliation storage còn stub, metrics chưa nối hệ quan sát bên ngoài, và chưa có bằng chứng Docker/MinIO/backup object-storage thật trên môi trường production.

## 2. Cấu trúc repository

| Thư mục/tệp | Vai trò |
| --- | --- |
| `backend/` | Parent Maven và ứng dụng Spring Boot. |
| `backend/app/` | Backend chính: Java, REST API, Flyway migration, test unit/integration, Dockerfile. |
| `web/` | SPA React + TypeScript + Vite, PWA, test Vitest và script E2E. |
| `mobile/` | Ứng dụng Flutter cho Android/iOS, cache offline và WebSocket client. |
| `deploy/` | Docker Compose production-like, backup/restore/update/smoke scripts và mẫu biến môi trường. |
| `infra/` | Docker Compose phát triển/kiểm thử cho PostgreSQL, MinIO và MailHog. |
| `docs/` | Đặc tả, API inventory, kế hoạch/test report, ảnh kiểm thử V2. |
| `_recovery/` | Bản lưu các tài liệu baseline/quyết định kiến trúc. |
| `testlogs/` | Log test lịch sử. |
| `SMART_TASK_MANAGEMENT_FINAL_SPEC.md` | Đặc tả tổng hợp nghiệp vụ và kiến trúc baseline. |
| `DEPLOY.md` | Hướng dẫn build, triển khai, backup/restore và vận hành. |
| `AUDIT-REPORT.md` | Báo cáo audit trước đó; cần đọc cùng code hiện tại vì một số nội dung đã được thay đổi ở V2/V3. |

Tại lần khảo sát, working tree có các thay đổi chưa commit ở một số file UI web và ba tệp chưa theo dõi: `%MAVEN_OUT%`, `run-all.ps1`, `run_test.bat`. Báo cáo này không sửa các thay đổi đó.

## 3. Kiến trúc và công nghệ

```text
React/Vite PWA ─┐
                 ├─ HTTP /api/v1 + SSE ─ Spring Boot 3.2.5 / Java 21
Flutter mobile ─┘                              │
                                  ┌────────────┼─────────────┐
                               PostgreSQL   MinIO/local    Ollama
                                  Flyway      files       (tùy chọn)
```

| Lớp | Công nghệ chính |
| --- | --- |
| Backend | Java 21, Spring Boot 3.2.5, Spring Web/Security/Validation/JPA, Flyway. |
| Database | PostgreSQL; Hibernate chỉ `validate`, schema được quản lý bằng Flyway. |
| File | Local gateway mặc định hoặc MinIO S3-compatible. Giới hạn upload cấu hình 50 MB/tệp, 52 MB/request. |
| Web | React 18, TypeScript 5.6, React Router 6, Vite 6, Tailwind 4, Vitest. |
| Mobile | Flutter/Dart, Dio, go_router, Provider, secure storage, offline store, WebSocket. |
| Xuất báo cáo | Apache POI (XLS/XLSX) và PDFBox (PDF). |
| Kiểm thử backend | JUnit/Spring Boot Test, ArchUnit, Testcontainers PostgreSQL. |
| Triển khai | Docker Compose: PostgreSQL 16, MinIO, backend, web/Caddy và init container cho MinIO. |

Backend được tổ chức theo package nghiệp vụ thay vì tách microservice: `identity`, `workspace`, `project`, `task`, `workflow`, `files`, `calendar`, `notify`, `reports`, `ai`, `realtime`, `collab`, `note`, `security`, `common`, `jobs`, `admin`. Đây là nền tảng phù hợp để giữ transaction và policy tập trung, đồng thời vẫn có ranh giới cho việc tách module/worker sau này.

## 4. Nghiệp vụ và chức năng đã có

### 4.1 Định danh, tài khoản và an toàn phiên

- Đăng ký, đăng nhập, refresh/logout, đổi mật khẩu, re-authentication và lookup người dùng.
- Quản lý tài khoản: phê duyệt/từ chối, lock/unlock, reset mật khẩu.
- Phiên Bearer opaque; backend áp dụng filter xác thực, actor hiện tại và policy tập trung.
- Có bootstrap admin và cấu hình self-registration. Mẫu triển khai mặc định đăng ký mới ở trạng thái chờ admin duyệt.

### 4.2 Workspace, project, team và phân quyền

- Personal workspace và team workspace; mời/tham gia/chuyển owner workspace.
- Project có thành viên, vai trò, milestones, trạng thái và bật/tắt approval.
- Team trong workspace; quản lý thành viên team và gán task theo team.
- Custom role scoped theo project cho quyền sửa metadata task và quản lý assignment.
- Policy bảo vệ phạm vi tài nguyên và phân biệt 403/404, đồng thời kiểm thử nhiều trường hợp thu hồi quyền.

### 4.3 Task, công việc cá nhân và workflow

- Task dự án: CRUD, phân trang/lọc/sắp xếp, subtask một cấp, assignee chính/cộng tác viên, dependency, checklist, time log, tags, trạng thái blocked và deadline.
- Personal task: scope riêng của chủ sở hữu, labels, checklist, start/completion, summary, soft-delete/restore.
- Luồng task gồm `TODO`, `IN_PROGRESS`, `PENDING_APPROVAL`, `COMPLETED`, `CANCELLED`; workflow hỗ trợ start, submit, approve, reject, cancel và reopen.
- Approval có guard chống tự duyệt, vòng approval, audit và xử lý approver không còn hợp lệ.
- Kiểm soát đồng thời hiện diện qua version/`If-Match`, row lock và idempotency cho các đường ghi quan trọng.

### 4.4 Cộng tác, lịch và thông báo

- Bình luận và activity feed theo task.
- Lịch, reminder cá nhân, pause/resume reminder, preference timezone và xuất ICS.
- Inbox thông báo, unread count, đánh dấu đã đọc/đọc toàn bộ, preferences và thiết bị đăng ký push.
- SSE ticket/stream để đẩy realtime; web có cơ chế reconnect và offline banner; mobile có WebSocket client.
- Deadline được xử lý với timezone/IANA và có kiểm thử DST trong bộ test.

### 4.5 Tệp, ghi chú, báo cáo và AI

- File attachment có upload session, commit/version, preview/download bằng stream ticket, access decision/audit và soft delete/restore attachment.
- Notes hỗ trợ scope `PERSONAL`, `PROJECT`, `SHARED`, `TASK`, ghim và tags (bổ sung ở migration V17).
- Progress log thủ công của project, tách với progress tính từ task (V17).
- Dashboard, báo cáo metric, export CSV/XLSX/PDF và export job nền.
- AI có request/job/suggestion/history/config; backend kiểm tra scope/manifest, sanitize prompt và validate output. Gợi ý cần được người dùng accept/reject; AI không tự biến đổi trạng thái nghiệp vụ.

## 5. API và giao diện người dùng

API được đặt dưới `/api/v1`. Có khoảng 30 controller, bao phủ các nhóm: auth/accounts, workspaces/invites/teams, projects/milestones/progress logs, tasks/personal tasks/workflow, collaboration, files, calendar/reminders, notifications/devices, reports/dashboard, notes, realtime, AI và admin.

Các route web chính: `/dashboard`, `/workspaces`, `/projects`, `/projects/:id`, `/tasks`, `/my-tasks`, `/notes`, `/teams`, `/roles`, `/tasks/:id`, `/files`, `/calendar`, `/notifications`, `/reports`, `/settings`, `/change-password`, `/ai`, `/admin`, cùng login/register. Route AI được ẩn và chuyển hướng khi `VITE_ENABLE_AI=false`; route admin có guard riêng.

Mobile có màn hình cho login, workspace, project/project detail, task/task detail, my tasks, calendar, notifications, reports, notes, files, comments, settings, re-auth và AI. Cấu trúc mobile cũng có secure session, cache/offline store, connectivity và realtime indicator.

## 6. Dữ liệu và migration

Có **17 migration Flyway** (`V1__core.sql` đến `V17__v3_features.sql`) và rollback script cho các phase U2–U6. Các nhóm bảng bao gồm:

- Identity/session/recovery token và global permission grant.
- Workspace, invitation, membership; project, membership, milestone, team và team member.
- Task, assignment, dependency, checklist, tag, time log, approval/workflow event.
- Comment/activity, notification/preference/device/delivery attempt, reminder.
- File object/version/attachment/upload/stream ticket/access event.
- Audit, outbox, background job, idempotency record.
- AI context/interaction/job/model/suggestion; report export job.
- Note, progress log và các trường Web Push ở V17.

Migration V17 bổ sung notes, project progress log và các cột endpoint/key cho `WEB_PUSH`. Hibernate được cấu hình `ddl-auto: validate`; vì vậy migration là nguồn sự thật schema.

## 7. Kiểm thử và bằng chứng hiện có

### Kiểm thử trong source hiện tại

- Backend có 116 tệp test unit/integration theo tên file; phạm vi trải trên security, workflow, task, project, calendar, notifications, file, report, realtime, AI, database và architecture.
- Web có 11 tệp test Vitest, gồm route guard, task/checklist/responsibility, workflow, Kanban, notifications, realtime reconnect và error alert.
- Mobile có 7 tệp test, gồm API client, session, offline store, login flow, WebSocket và widget.
- Có ba script E2E web: personal tasks, V2 acceptance và V2 workflow.

### Kết quả lịch sử đã lưu (không phải kết quả chạy lại ngày 09/11)

Theo `docs/V2-VALIDATION-RESULTS.json`, ngày 02/11/2026 trên PostgreSQL 16.6: backend **478 tổng, 442 thực thi, 0 failures, 0 errors, 36 skipped, BUILD SUCCESS**; frontend **23 tests/10 suites, 0 failures, production build PASS**; ba luồng browser PASS; restore database PASS với 16 migration ở thời điểm đó. Các mục chưa xác nhận khi đó: Docker Linux build/restart/healthcheck, MinIO thật và restore object storage, SMTP/push thực, Ollama live/latency.

Những số này là bằng chứng hữu ích nhưng đã cũ hơn thay đổi V3/V17 hiện tại. Trước khi release nên chạy lại backend, web lint/build/test, Flutter test/analyze, E2E và smoke Docker trên revision sẽ phát hành.

## 8. Triển khai và vận hành

`deploy/docker-compose.yml` dựng năm service: PostgreSQL 16, MinIO, `minio-init`, backend và web. Web dùng Caddy; compose có network nội bộ và volume riêng cho database, object storage, backend files và Caddy. `run-all.ps1` hỗ trợ `up`, `down`, `restart`, `status`, `logs`; tùy chọn `-ResetData` sẽ xóa volumes Docker nên chỉ phù hợp môi trường local khi chủ động chấp nhận mất dữ liệu.

Mẫu `.env.example` yêu cầu đổi password PostgreSQL/MinIO, cấu hình domain/ports/timezone, bootstrap admin, self-registration, CORS, API base, cờ AI và địa chỉ Ollama. Tài liệu triển khai cũng có script backup, restore, update, smoke và kéo backup từ VPS.

Khuyến nghị vận hành:

- Không dùng các secret `change-me-*` hay tài khoản bootstrap mặc định trên Internet.
- Bật HTTPS/domain, đặt CORS chính xác và sao lưu cả PostgreSQL lẫn object storage.
- Kiểm thử khôi phục định kỳ, không chỉ tạo backup.
- Theo dõi outbox/background job, lượng file và log audit; đưa metrics/logs vào hệ quan sát tập trung trước khi vận hành quy mô lớn.

## 9. Điểm mạnh đáng ghi nhận

- Kiến trúc modular monolith rõ domain, phù hợp transaction phức tạp và dễ vận hành hơn microservice ở giai đoạn đầu.
- Quy tắc workflow/approval và policy quyền được triển khai ở backend, không chỉ phụ thuộc UI.
- Có migration versioned, Hibernate validate, test integration/Testcontainers và kiểm soát đồng thời cho các thao tác nhạy cảm.
- File được che bằng ticket ngắn hạn và access check; có audit/outbox/job thay vì thực hiện toàn bộ side effect trong request.
- Web/mobile có độ bao phủ nghiệp vụ tốt, hỗ trợ offline/realtime ở mức client.
- Tài liệu triển khai, thiết kế, API inventory và ảnh kiểm thử khá đầy đủ.

## 10. Hạn chế, rủi ro và việc cần ưu tiên

| Ưu tiên | Vấn đề | Tác động/khuyến nghị |
| --- | --- | --- |
| Cao | Push/email provider đang là stub; chưa có SMTP/FCM/APNs/Web Push delivery thực. | Không cam kết được thông báo ngoại vi. Chọn provider, quản lý secret, retry/DLQ và kiểm thử E2E. |
| Cao | Scan file trả `UNSUPPORTED`; storage reconciliation còn stub. | Không nên coi file upload đã được bảo vệ khỏi malware. Tích hợp antivirus/quarantine và worker reconciliation thật. |
| Cao | Chưa có bằng chứng production-like hiện hành cho Docker, MinIO thật, restart/healthcheck và restore object storage. | Thiết lập staging, chạy smoke + disaster-recovery rehearsal cho cả DB và bucket. |
| Trung bình | Metrics là in-memory stub, không có registry/alerting bên ngoài. | Thêm Prometheus/OpenTelemetry, dashboard và cảnh báo vận hành. |
| Trung bình | Calendar hiện thiên về danh sách theo khoảng ngày; chưa có thao tác drag-drop đổi hạn/lưới tháng hoàn chỉnh theo báo cáo V2. | Xác nhận yêu cầu UX rồi lập backlog nếu đây là luồng trọng yếu. |
| Trung bình | Một số lọc/phân trang Task/Kanban vẫn có phần xử lý trong bộ nhớ theo báo cáo V2. | Benchmark với dữ liệu lớn và chuyển filter/page quan trọng về SQL. |
| Trung bình | 36 test bị skip trong validation lịch sử. | Phân loại từng skip; bắt buộc chạy phần phụ thuộc Docker/Ollama trên CI/staging phù hợp. |
| Thấp | Tài liệu cũ có thể mâu thuẫn với code V2/V3 (ví dụ mô tả AI trong POM cũ). | Chọn một tài liệu trạng thái hiện hành, ghi version/date và cập nhật inventory sau mỗi release. |

## 11. Tình trạng mã nguồn tại thời điểm bàn giao

- Commit HEAD: `0f0f9ff feat: set default BOOTSTRAP_ADMIN_USERNAME=admin`.
- Các commit gần đây cho thấy V3 bổ sung Web Push, reports có dữ liệu thật, mobile screens, PWA; trước đó là redesign V2.
- Working tree không sạch ở web UI và có các tệp local chưa theo dõi, vì vậy nên review/commit hoặc loại trừ chúng trước khi tạo release tag.
- Không có thay đổi mã nguồn nào được thực hiện để lập báo cáo này.

## 12. Kết luận

SmartTask là một codebase quản lý công việc đa nền tảng có nền tảng nghiệp vụ vững, phạm vi chức năng lớn và cấu trúc kỹ thuật hợp lý. Nó phù hợp để tiếp tục hoàn thiện theo hướng staging/production. Điều kiện quan trọng trước khi công bố rộng rãi là thay các stub vận hành/bảo mật bằng dịch vụ thật, xác nhận khả năng khôi phục toàn bộ dữ liệu, chạy lại validation trên revision phát hành và xử lý working tree hiện tại.

## 13. Tài liệu tham chiếu trong repository

- `SMART_TASK_MANAGEMENT_FINAL_SPEC.md`: baseline yêu cầu và kiến trúc.
- `docs/00-AUDIT-SPEC.md` đến `docs/10-MIGRATION-ROLLBACK.md`: đặc tả theo module, RBAC, API, test, vận hành và migration.
- `docs/V2-API-INVENTORY.md`: inventory endpoint/contract hỗ trợ rà soát.
- `docs/SMART-TASK-MANAGER-V2-FINAL-REPORT.md` và `docs/V2-VALIDATION-RESULTS.json`: bằng chứng validation V2 ngày 02/11/2026.
- `DEPLOY.md`: quy trình build/deploy/backup/restore.
- `docs/screenshots/v2/`: 27 ảnh giao diện/luồng kiểm thử V2.
