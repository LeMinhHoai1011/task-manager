# F4 — Web, Mobile & Realtime Integration — Báo cáo cuối

Ngày: 2026-10-25 · Phạm vi: F4 (F0–F3 đã báo cáo trước đó) · Không tự chuyển F5.

## 0. Trả lời câu hỏi: "tài khoản admin là cái nào?"

**Không có tài khoản admin mặc định.** `smarttask.bootstrap.admin-username` đang để trống và
self-registration tạo user ở trạng thái `PENDING`, nên DB sạch không có admin nào đăng nhập được.

Cách tạo admin đầu tiên (local, một lần):

1. Đăng ký một user (UI đăng ký hoặc `POST /api/v1/auth/register`). User này sẽ `PENDING`.
2. Đặt biến môi trường `SMARTTASK_BOOTSTRAP_ADMINUSERNAME=<username>` (hoặc
   `smarttask.bootstrap.admin-username` trong cấu hình) rồi khởi động lại backend.
3. `BootstrapRunner` chỉ chạy khi **chưa có grant SYSTEM_ADMIN live** và flag
   `bootstrap_completed` chưa được set. Nó sẽ:
   - grant `SYSTEM_ADMIN` (granted_by = null, reason BOOTSTRAP),
   - **kích hoạt tài khoản** (`status=ACTIVE`, `active=true`) và tạo personal workspace,
   - set flag `bootstrap_completed=true`.
4. Đăng nhập bình thường bằng username/password đã đăng ký.

Lượt này đã sửa lỗ hổng vận hành: trước đây bootstrap chỉ grant quyền mà không kích hoạt, nên
admin được grant vẫn `PENDING` và không thể đăng nhập (không có approver nào tồn tại trước admin
đầu tiên). Đã có IT chứng minh: `GrantIT#bootstrapActivatesConfiguredPendingUser`.

Lưu ý: nếu DB dev của bạn **đã** có flag `bootstrap_completed=true` nhưng chưa có admin, bootstrap
sẽ không chạy lại. Chỉ trên DB dev, chọn một trong hai: xoá flag đó rồi khởi động lại, hoặc grant
`SYSTEM_ADMIN` thủ công theo quy trình grant hiện có. Không làm việc này trên dữ liệu thật.

---

## 1. Realtime Architecture

**Transport: SSE** (`GET /api/v1/realtime/stream?ticket=...`), không dùng WebSocket.

Lý do: môi trường offline không có `spring-websocket` 6.1.x; trộn 6.2.7 với Spring 6.1 có rủi ro
linkage. SSE đáp ứng đủ yêu cầu F4 và đơn giản hơn cho cả web (`fetch` stream) và Flutter (Dio stream).

Luồng:

1. Client đã đăng nhập gọi `POST /api/v1/realtime/ticket` → ticket mờ, TTL **60s**, gắn `session_id`.
2. Client mở `GET /realtime/stream?ticket=...` (không kèm access/refresh token trong URL).
3. `RealtimeHandshakeFilter` validate + **consume nguyên tử** ticket trước khi vào MVC; ticket
   sai/hết hạn/đã dùng → `401 INVALID_SESSION`. Ticket không bao giờ bị log.
4. Server chỉ gửi `event: signal` với routing key:
   `{kind, scope_type, scope_id, object_id, seq, at}` — **không** title/body/nội dung riêng tư.
5. Client nhận signal → refetch qua REST có authz (403/404 nếu mất quyền), nên signal không thể
   rò rỉ nội dung.
6. Reconnect phải xin ticket mới (ticket cũ đã bị burn). Client có backoff + resync.
7. Logout/revoke session → `RealtimeRegistry.closeBySession` đóng stream cũ.

**Wiring thật (bổ sung trong lượt này):** trước đó registry chỉ được test gọi trực tiếp; chưa có
service nghiệp vụ nào phát signal. Đã thêm `RealtimePublisher` và nối vào:

- `TaskService`: create/subtask/update/deadline/assign/unassign/block/unblock/time-log/delete/restore
- `CommentService`: add/edit/delete
- `WorkflowService`: mọi transition (kind `WORKFLOW_*`)
- `ProjectService`: create/update/complete/reactivate/member add-remove
- `NotificationService`: notification created/read

Publisher resolve người nhận từ membership **ACTIVE** trong DB (không tin header client) và phát
sau commit (`afterCommit`), nên rollback không gửi signal. Publish là best-effort: lỗi publish không
làm hỏng giao dịch.

## 2. Web Feature Matrix

| Khu vực | Chức năng | Trạng thái |
|---|---|---|
| Auth | login, session, refresh, logout, reauth challenge/verify | PASS (API thật) |
| Workspaces | list/create/invite/members/transfer | PASS |
| Projects | list/create, lọc theo workspace, nested alias dự phòng | PASS |
| Tasks | list + filter/sort/cursor, create, detail, sửa, deadline, assign, block, checklist, time log, delete/restore | PASS |
| Workflow | submit/approve/reject/cancel/reopen, panel lịch sử | PASS |
| Comments | list/add/edit/delete | PASS |
| Notifications | cursor pagination, read, read-all | PASS |
| Files | upload multipart (init/complete/commit), list, download bytes | PASS |
| Calendar | list theo khoảng thời gian | PASS |
| Reports | tạo export job, poll trạng thái, tải file bytes | PASS |
| AI | request draft, poll job, xem suggestion, accept/reject/retry; accept chỉ sau xác nhận rõ ràng | PASS (inference thật: NOT_TESTED) |
| Admin | accounts PENDING, approve/reject, grant/revoke, audit | PASS |
| Realtime | chỉ báo connected/reconnecting, signal → refetch | PASS |

Không có màn hình production nào trả mock; mock chỉ nằm trong `src/test`.

## 3. Mobile Feature Matrix

| Khu vực | Chức năng | Trạng thái |
|---|---|---|
| Auth | login, refresh, single-session 409, logout, reauth screen | PASS |
| Offline | allowlist kind, TTL 24h, scope theo user, clear khi revoke, chặn nội dung private | PASS |
| Workspaces | list/create | PASS |
| Projects | list/create | PASS |
| Tasks | list/filter, detail, workflow actions (start/submit/approve/reject/cancel/reopen) | PASS |
| Comments | list/add | PASS |
| Notifications | cursor + "Tải thêm", read-one, read-all, deep-link revalidate | PASS |
| Files | upload (init/complete/commit, max 50MB), download qua gateway | PASS |
| Calendar | list | PASS |
| Reports | export job + tải bytes | PASS |
| AI | request, poll, suggestion, accept/reject; không auto-apply | PASS (inference thật: NOT_TESTED) |
| Realtime | SSE, ticket mới mỗi reconnect, `reauthRequired` khi 401 (không loop), seq cursor | PASS |
| Push | đăng ký device qua endpoint thật; không gửi token giả | PARTIAL (FCM/APNs: NOT_TESTED) |

## 4. API Contract Status

| Hạng mục | Kết quả |
|---|---|
| PageResponse canonical `items/page/limit/total/totalPages` | PASS, giữ alias legacy `data`; web + mobile chuẩn hoá cả hai |
| Reauth 2 bước | PASS: `POST /auth/reauth/challenge` + `POST /auth/reauth/verify`, ticket dùng một lần qua header `X-Reauth-Ticket`; alias cũ `/auth/reauth` giữ nguyên |
| Accounts | PASS: `GET /accounts`, `POST /accounts/{id}/approve` + `/reject`; legacy `/auth/approve`, `/auth/users` giữ nguyên |
| Unified sort/filter | PASS: `sort=-field,field2` + legacy `sort` + `order`; filter state/priority/assignee/overdue/tag/date |
| Error envelope | PASS: `code`, `message_key`, `message_params`, `details`, `correlation_id`; 404 trung tính |
| Files | PASS: init/complete/commit upload + download bytes; giới hạn 50MB |
| Notification cursor | PASS backend; web dùng cursor; mobile đã bổ sung cursor + tải thêm |
| Report export job | PASS: tạo job, trạng thái, tải bytes |
| AI job lifecycle | PASS: request/job/suggestion/accept/reject/retry; không auto approve/complete/cancel |
| Session revoke | PASS: `POST /sessions/revoke` đóng stream cũ |
| Devices | PASS: `GET/POST/DELETE /notifications/devices`, token chỉ lưu hash + hint |
| Nested aliases | PASS additive: `/workspaces/{id}/projects`, `/projects/{id}/tasks` (DTO rút gọn). Client vẫn dùng query đầy đủ trường; đã ghi rõ, không phải breaking change |

Không có breaking change bắt buộc phải báo trước.

## 5. E2E Evidence

Đã chạy thật (không phải mock):

- **Backend unit**: 296 run / 0 fail / 2 skip (2 test phụ thuộc Ollama/Docker).
- **Backend HTTP integration + PostgreSQL thật**: **91/91 PASS**, gồm:
  - `F4RealtimeIT` **7/7**: ticket hợp lệ, signal-only, ticket sai/đã dùng/hết hạn → 401,
    reconnect cần ticket mới, revoke/logout đóng stream, **và mutation thật qua API phát signal**
    (`committedMutationPublishesSignalToProjectMembers`).
  - `F4EndpointsIT` **5/5**: accounts, reauth, devices, session revoke, nested routes.
  - `StorageIT` **8/8**: upload/download thật qua S3Mock; `F3ExportIT`: export job + tải bytes.
  - `WorkflowIT` **3/3**: approval flow qua HTTP; `F3ContractIT`: contract compatibility.
  - `AuthFilterChainIT`, `RbacIT`, `ConcurrencyIT`, `TxRollbackIT`, `GrantIT` (4/4, gồm bootstrap
    activation), `F3JobLifecycleIT`, `F3NotificationIT`, `F3OutboxRelayIT`, `F3AiWorkerIT`, `PerfIT`.
- **Live SSE smoke** (Spring Boot thật + PostgreSQL thật, cổng 8081, không dùng MockMvc):
  login → ticket TTL 60s → mở stream → tạo workspace + project qua API → nhận đúng:
  `event:signal` `{"kind":"PROJECT_CREATED","scope_type":"WORKSPACE",...}`.
  Dùng lại ticket cũ → **HTTP 401**. Log server không chứa giá trị ticket.
- **Web**: `pnpm build` PASS; `vitest` **15/15 PASS** (component tests, jsdom + mock API).
- **Mobile**: `flutter analyze` sạch; `flutter test` **29/29 PASS** (unit/widget, `FakeBackend`
  là `HttpClientAdapter`, SSE parse, reauth, offline TTL).

**NOT_TESTED (E2E thật):** browser thật (Playwright/Selenium), thiết bị/emulator Android/iOS,
Ollama inference thật, FCM/APNs/SMTP delivery, load/P95, backup-restore drill, MinIO thật
(đã thay bằng S3Mock), Testcontainers harness, realtime nhiều instance.

## 6. Security Regression

- F0–F3 vẫn xanh: 296 unit + 91 IT, gồm auth filter chain, RBAC, grant expiry/revoke, IDOR 404
  trung tính, concurrency/version, tx rollback, outbox/idempotency.
- Realtime: ticket single-use + TTL 60s + session-bound; không token trong URL/query/frame; ticket
  không log; signal không chứa nội dung riêng tư; revoke đóng stream; reconnect bắt buộc ticket mới.
- Không có actor ID giả, không tin role/grant header từ client; mọi quyền kiểm tra ở backend.
- Publish chỉ tới membership ACTIVE đọc từ DB; signal là advisory, refetch vẫn qua authz.
- Bootstrap admin là đường local một lần (không qua HTTP API), có audit `auth.bootstrap.activate`.

## 7. Files Changed (chính)

**Backend**
- `realtime/`: `RealtimeController.java`, `RealtimeHandshakeFilter.java`, `RealtimeRegistry.java`,
  `RealtimeSignal.java`, `WsTicketService.java`, **`RealtimePublisher.java` (mới)**
- `identity/`: `AuthController.java`, `AccountController.java`, `SessionController.java`,
  `service/IdentityService.java`
- `notify/`: `DeviceController.java`, `service/NotificationService.java`, `service/DevicePushService.java`
- `workspace/WorkspaceProjectController.java`, `task/ProjectTaskController.java`,
  `task/service/TaskService.java`
- `collab/service/CommentService.java`, `workflow/service/WorkflowService.java`,
  `project/service/ProjectService.java`, `security/BootstrapRunner.java`
- Tests: `itg/F4RealtimeIT.java`, `F4EndpointsIT.java`, `GrantIT.java`, `StorageIT.java`,
  `F3ExportIT.java`, `ItgBase.java`
- Migrations liên quan: `V8__ws_ticket.sql`, `V10__f2_integrity.sql` (đã có từ trước)

**Web**
- `src/realtime/socket.ts`, `src/realtime/RealtimeContext.tsx` (SSE)
- `src/api/client.ts`, `src/api/types.ts` (PageResponse/error envelope/binary)
- `src/pages/` (12 trang: Admin, Ai, Calendar, Files, Login, Notifications, Projects, Reports,
  TaskDetail, Tasks, WorkflowPanel, Workspaces)
- `src/test/` (4 file test)

**Mobile**
- `lib/realtime/ws_client.dart` (SSE), `lib/main.dart`, `lib/app.dart`
- `lib/api/client.dart`, `lib/api/endpoints.dart`
- `lib/screens/` (12 màn hình, gồm `notifications_screen.dart` cursor + tải thêm)
- `lib/cache/offline_store.dart` (TTL biên `>=`), `lib/push/fcm_stub.dart`
- `test/` (7 file test)

**Docs**
- `docs/03-API-CONTRACT.md` (đề xuất, **không FROZEN**), `docs/F4-REPORT.md`

## 8. PASS / FAIL / NOT_TESTED

| Trạng thái | Nội dung |
|---|---|
| PASS | Backend unit 296; backend IT 91; web build + 15 test; mobile analyze + 29 test; live SSE signal + ticket reuse 401; realtime mutation wiring; bootstrap activation; contract aliases; files/export/notification cursor |
| FAIL | Không có lỗi đã biết |
| NOT_TESTED | Browser E2E thật; device/emulator E2E; Ollama thật; FCM/APNs/SMTP; load/P95; backup-restore; MinIO thật; Testcontainers; realtime đa instance |

## 9. Remaining Risks

1. **Realtime registry là in-memory**: chạy nhiều instance backend sẽ không fan-out signal giữa các
   node. V1 một instance ổn; nếu scale ngang cần Redis pub/sub hoặc sticky session.
2. **Signal best-effort**: publish lỗi hoặc client bỏ lỡ signal thì UI chỉ cập nhật ở lần refetch
   kế tiếp; nên có polling nhẹ/refresh khi focus (chưa làm).
3. **Fan-out theo membership**: mỗi mutation query member list; project rất lớn sẽ tốn chi phí,
   cần theo dõi và có thể chuyển sang topic/outbox chuyên dụng.
4. **SSE qua reverse proxy**: cần tắt buffering và timeout đủ lớn; heartbeat hiện có nhưng chưa
   kiểm chứng với nginx/ALB thật.
5. **Mobile offline/push**: chưa có E2E thiết bị thật; FCM/APNs chưa có credential nên chưa chứng
   minh delivery.
6. **AI**: chất lượng/safety của model thật chưa kiểm chứng; server vẫn là nơi quyết định quyền và
   chỉ áp dụng sau xác nhận người dùng.
7. **Storage**: S3Mock thay MinIO; khác biệt versioning/retention/KMS của MinIO chưa test.
8. **Bootstrap**: nếu flag đã set mà chưa có admin, cần thao tác vận hành thủ công (đã ghi ở mục 0).
9. **Test harness**: Testcontainers không dùng được trong môi trường này; IT chạy trên PG/S3Mock
   dựng sẵn, nên CI cần bổ sung harness tương đương.

**Dừng ở đây — chờ nghiệm thu F4. Không bắt đầu F5.**
