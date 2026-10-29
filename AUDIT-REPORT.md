# BÁO CÁO RÀ SOÁT TOÀN BỘ PROJECT — SMART TASK MANAGEMENT

**Thư mục gốc:** `D:\task`  
**Chế độ:** CHỈ ĐỌC (không sửa, không xóa, không tạo file khác ngoài báo cáo này)  
**Ngày báo cáo:** 28/10/2026

---

## MỤC 0: TÓM TẮT 10 DÒNG

Dự án SmartTask là hệ thống quản lý công việc cá nhân/nhóm với backend Spring Boot 3.2.5 (Java 21), frontend React + Vite, mobile Flutter, CSDL PostgreSQL (Flyway V1–V12), lưu trữ MinIO/S3, reverse proxy Caddy. Code đã được triển khai phần lớn (controllers, services, entities, repositories, migrations, i18n vi/en). Tuy nhiên, code hiện có nhiều sự sai lệch so với yêu cầu nghiệp vụ A–J (ví dụ: bắt buộc duyệt tài khoản thay vì tự ACTIVE, có module AI mặc dù yêu cầu "không dùng AI", không phân loại file BRIEF/RESULT, không có ghi chú cá nhân...). 5 rủi ro lớn nhất: (1) sai lệch luồng đăng ký/duyệt tài khoản so với yêu cầu A; (2) thiếu phân biệt role admin/user thực sự ở frontend do login chưa trả role (đã vá trong phiên làm việc này nhưng cần verify); (3) thiếu nhiều state machine workflow (DOING/SUBMITTED/REWORK) so với yêu cầu C; (4) còn thừa tham chiếu DEC-41/Ollama/AI trong docs và code mặc dù yêu cầu J cấm dùng AI; (5) chưa thể build/run trên máy dev do thiếu Maven, Docker daemon tắt, RAM 15.7 GB.

---

## MỤC 1: CẤU TRÚC PROJECT

### 1.1 Cây thư mục tầng 1–3

```
D:\task\
├── .git\                      # Git repository (đã init)
├── .github\
├── _recovery\                 # Sao lưu tài liệu giai đoạn trước (8 file .md)
├── backend\                   # Backend Spring Boot
│   ├── .dockerignore
│   ├── Dockerfile
│   ├── pom.xml
│   └── app\
│       ├── src\
│       │   ├── main\
│       │   │   ├── java\vn\smarttask\          # ~100 file .java
│       │   │   │   ├── admin\ (2 java + 1 entity + 1 repo)
│       │   │   │   ├── ai\ (20+ java)
│       │   │   │   ├── calendar\ (8 java)
│       │   │   │   ├── collab\ (3 service + 2 entity + 2 repo + 1 controller)
│       │   │   │   ├── common\ (16 java)
│       │   │   │   ├── config\
│       │   │   │   ├── files\ (3 service + 3 entity + 2 repo + 1 controller)
│       │   │   │   ├── identity\ (2 controller + 1 service + 5 entity + 5 repo + 2 event)
│       │   │   │   ├── jobs\ (12 java)
│       │   │   │   ├── notify\ (3 service + entity + repo + 3 controller)
│       │   │   │   ├── project\ (2 service + 3 entity + 3 repo + 1 controller)
│       │   │   │   ├── realtime\ (6 java)
│       │   │   │   ├── reports\ (3 service + 1 entity + 1 repo + 1 controller)
│       │   │   │   ├── security\ (9 java + 1 entity + 1 repo)
│       │   │   │   ├── task\ (4 controller/guard/sort + 6 entity + 5 repo + 1 service)
│       │   │   │   ├── workflow\ (4 entity + 4 repo + 4 service/guard/controller)
│       │   │   │   └── workspace\ (2 controller + 1 service + 3 entity + 3 repo)
│       │   │   └── SmartTaskApplication.java
│       │   │   └── resources\
│       │   │       ├── application.yml
│       │   │       └── db\migration\V1__core.sql ... V12__f3_workers.sql  (12 file)
│       │   │       └── db\rollback\U2...U6 (5 file)
│       │   └── test\java\vn\smarttask\          # ~80 file .java (unit + IT)
│
├── deploy\                    # Docker Compose + scripts vận hành
│   ├── docker-compose.yml
│   ├── .env.example
│   ├── .env                   # (đã có, chứa secrets)
│   ├── backup.sh, restore.sh, smoke.sh, update.sh
│   ├── minio-init.sh
│   └── pull-backups.sh, pull-backups.ps1
│
├── docs\                      # Tài liệu thiết kế (16 file .md)
│   ├── 00-AUDIT-SPEC.md ... 10-MIGRATION-ROLLBACK.md
│   ├── F4-REPORT.md
│   ├── UI_*.md (3 file)
│
├── infra\                     # Docker Compose dev/test + init-db SQL
│   ├── docker-compose.dev.yml
│   ├── docker-compose.test.yml
│   └── init-db\01-roles.sql, 02-grants-tables.sql
│
├── mobile\                    # Flutter (Dart)
│   ├── lib\                   # ~25 file .dart (api, auth, cache, push, realtime, screens, widgets)
│   ├── test\                  # 6 file .dart test
│   ├── android\, ios\, build\
│   └── pubspec.yaml
│
└── web\                       # React + TypeScript + Vite
    ├── src\                   # ~25 file .ts/.tsx + styles.css
    │   ├── api\ (client.ts, types.ts)
    │   ├── auth\ (AuthContext.tsx)
    │   ├── components\ (ui.tsx, Layout.tsx, ReauthModal.tsx)
    │   ├── i18n\ (index.ts, en.ts, vi.ts)
    │   ├── pages\ (15 trang .tsx)
    │   ├── realtime\ (RealtimeContext.tsx, socket.ts)
    │   └── test\ (5 file .tsx test)
    ├── dist\                  # Build output
    ├── node_modules\
    ├── package.json, pnpm-lock.yaml
    ├── vite.config.ts, vite.demo.config.ts, vitest.config.ts
    ├── Caddyfile, Dockerfile, eslint.config.js, tsconfig.json, index.html
│
├── .gitignore
├── DEPLOY.md
└── SMART_TASK_MANAGEMENT_FINAL_SPEC.md   # Spec tổng hợp (622 dòng)
```

### 1.2 Công nghệ và phiên bản

| Thành phần | Công nghệ | Phiên bản | File bằng chứng |
|------------|-----------|-----------|-----------------|
| Backend | Spring Boot | 3.2.5 | `backend/pom.xml` dòng 19 |
| Backend | Java | 21 | `backend/pom.xml` dòng 24 |
| Backend | Build tool | Maven | `backend/pom.xml`, `backend/Dockerfile` dòng 3 |
| Backend | DB | PostgreSQL | `backend/app/pom.xml` dòng 39-42 |
| Backend | Migration | Flyway | `backend/app/pom.xml` dòng 36-37 |
| Backend | Object Storage client | MinIO | `backend/app/pom.xml` dòng 44-46 |
| Backend | PDF/Excel | PDFBox 3.0.8, POI 5.3.0 | `backend/app/pom.xml` dòng 48-61 |
| Web | React | 18.3.1 | `web/package.json` dòng 14 |
| Web | Build tool | Vite | `web/package.json` dòng 29 |
| Web | CSS | Tailwind 4.3.3 | `web/package.json` dòng 27-28 |
| Web | Test | Vitest 5.0.3 | `web/package.json` dòng 31 |
| Mobile | Flutter | Dart SDK 3.11.1 | `mobile/pubspec.yaml` dòng 7 |
| Mobile | HTTP client | Dio 5.9.0 | `mobile/pubspec.yaml` dòng 13 |
| Mobile | Routing | go_router 16.2.4 | `mobile/pubspec.yaml` dòng 15 |
| Deploy | Reverse proxy | Caddy 2-alpine | `web/Dockerfile` dòng 20 |
| Deploy | Container | Docker Compose | `deploy/docker-compose.yml` |

### 1.3 Số lượng file theo module (tính từ glob)

| Module | File mã nguồn chính | File test | File cấu hình/build |
|--------|---------------------|-----------|---------------------|
| Backend Java | ~100 file `.java` | ~80 file `.java` | `pom.xml` (2), `Dockerfile`, `application.yml`, 12 migration SQL, 5 rollback SQL |
| Web TypeScript/React | ~25 file `.ts`/`.tsx` | 5 file `.tsx` | `package.json`, `vite.config.ts`, `vitest.config.ts`, `eslint.config.js`, `tsconfig.json`, `Caddyfile`, `Dockerfile` |
| Mobile Flutter | ~25 file `.dart` | 6 file `.dart` | `pubspec.yaml`, `analysis_options.yaml` |
| Deploy | — | — | `docker-compose.yml`, `.env.example`, `.env`, 7 scripts `.sh`/`.ps1` |
| Docs | 16 file `.md` | — | — |

### 1.4 Cách build/chạy (không chạy, chỉ đọc tài liệu)

- **Backend:** `mvn -pl app -am package -DskipTests` (cần Maven, chưa cài trên máy dev). Jar: `backend/app/target/smart-task-app-0.0.1-SNAPSHOT.jar` (`DEPLOY.md` dòng 49-50).
- **Docker (production-like local):** `cd deploy && docker compose up -d --build` (`DEPLOY.md` dòng 81-83).
- **Web:** `pnpm build` (`web/package.json` dòng 8).
- **Mobile:** `flutter` (cần `flutter doctor`, chưa xác minh SDK hoàn chỉnh — `docs/00-AUDIT-SPEC.md` mục B.2).

---

## MỤC 2: BACKEND

### 2.1 Danh sách Controller và Endpoint

| Controller | File | Method | Path | Quyền yêu cầu |
|------------|------|--------|------|---------------|
| AuthController | `backend/.../identity/AuthController.java:19` | POST | `/api/v1/auth/register` | Public |
| AuthController | `AuthController.java:53` | POST | `/api/v1/auth/login` | Public |
| AuthController | `AuthController.java:61` | POST | `/api/v1/auth/logout` | Public (allowlist filter) |
| AuthController | `AuthController.java:67` | POST | `/api/v1/auth/refresh` | Public (allowlist filter) |
| AuthController | `AuthController.java:76` | POST | `/api/v1/auth/reauth` | Authenticated (SessionAuthenticationFilter) |
| AuthController | `AuthController.java:86` | POST | `/api/v1/auth/reauth/challenge` | Authenticated |
| AuthController | `AuthController.java:100` | POST | `/api/v1/auth/reauth/verify` | Authenticated |
| AuthController | `AuthController.java:119` | POST | `/api/v1/auth/approve` | ACCOUNT_APPROVE_GLOBAL grant |
| AuthController | `AuthController.java:129` | POST | `/api/v1/auth/users` | ACCOUNT_APPROVE_GLOBAL grant |
| AccountController | `backend/.../identity/AccountController.java:23` | GET | `/api/v1/accounts` | account:read + ACCOUNT_APPROVE_GLOBAL |
| AccountController | `AccountController.java:54` | POST | `/api/v1/accounts/{id}/approve` | account:write + ACCOUNT_APPROVE_GLOBAL |
| AccountController | `AccountController.java:65` | POST | `/api/v1/accounts/{id}/reject` | account:write + ACCOUNT_APPROVE_GLOBAL |
| WorkspaceController | `backend/.../workspace/WorkspaceController.java:22` | GET | `/api/v1/workspaces` | workspace:read |
| WorkspaceController | `WorkspaceController.java:68` | POST | `/api/v1/workspaces` | workspace:write |
| ProjectController | `backend/.../project/ProjectController.java:22` | GET | `/api/v1/projects` | project:read |
| ProjectController | `ProjectController.java` (tiếp theo) | POST | `/api/v1/projects` | project:write |
| ProjectTaskController | `backend/.../task/ProjectTaskController.java:33` | GET | `/api/v1/projects/{id}/tasks` | task:read |
| TaskController | `backend/.../task/TaskController.java:24` | GET/POST/PATCH/DELETE | `/api/v1/tasks` | task:read / task:write |
| WorkflowController | `backend/.../workflow/WorkflowController.java:20` | POST | `/api/v1/tasks/{id}/workflow/start` | task:write |
| WorkflowController | `WorkflowController.java:77` | POST | `/api/v1/tasks/{id}/workflow/submit` | task:write |
| WorkflowController | `WorkflowController.java:91` | POST | `/api/v1/tasks/{id}/workflow/approve` | task:approve |
| WorkflowController | `WorkflowController.java:105` | POST | `/api/v1/tasks/{id}/workflow/reject` | task:approve |
| WorkflowController | `WorkflowController.java:120` | POST | `/api/v1/tasks/{id}/workflow/cancel` | task:write |
| WorkflowController | `WorkflowController.java` (tiếp theo) | POST | `/api/v1/tasks/{id}/workflow/reopen` | task:write |
| FileController | `backend/.../files/FileController.java:38` | POST | `/api/v1/files/upload` | file:write |
| FileController | `FileController.java` (tiếp theo) | POST/GET | `/api/v1/files/commit`, `/stream/...` | file:write / file:read |
| CalendarController | `backend/.../calendar/CalendarController.java:20` | GET | `/api/v1/calendar` | calendar:read |
| CalendarController | `CalendarController.java:61` | GET | `/api/v1/reminders` | calendar:read |
| CalendarController | `CalendarController.java:75` | POST | `/api/v1/reminders` | calendar:write |
| CalendarController | `CalendarController.java:90` | POST | `/api/v1/reminders/{id}/pause` | calendar:write |
| CalendarController | `CalendarController.java:103` | POST | `/api/v1/reminders/{id}/resume` | calendar:write |
| CalendarController | `CalendarController.java:117` | GET | `/api/v1/calendar/ics` | calendar:read |
| NotificationController | `backend/.../notify/NotificationController.java:16` | GET | `/api/v1/notifications` | notification:read |
| NotificationController | `NotificationController.java:58` | POST | `/api/v1/notifications/{id}/read` | notification:write |
| NotificationController | `NotificationController.java:66` | POST | `/api/v1/notifications/read-all` | notification:write |
| ReportController | `backend/.../reports/ReportController.java:24` | GET | `/api/v1/reports/dashboard` | report:read |
| ReportController | `ReportController.java:66` | GET | `/api/v1/reports/export` | report:read |
| ReportController | `ReportController.java` (tiếp theo) | POST/GET | `/api/v1/reports/export/{id}/download` | report:read |
| AiController | `backend/.../ai/AiController.java:28` | POST | `/api/v1/ai/request` | AI feature grant |
| AiController | `AiController.java:58` | GET | `/api/v1/ai/jobs/{id}` | AI (owner only, 404 for others) |
| AiController | `AiController.java:64` | GET | `/api/v1/ai/jobs/{id}/suggestion` | AI (owner only) |
| AiController | `AiController.java:72` | POST | `/api/v1/ai/jobs/{id}/accept` | AI (owner only) |
| AdminController | `backend/.../admin/AdminController.java:25` | GET | `/api/v1/admin/audit` | admin:read + SYSTEM_ADMIN |
| AdminController | `AdminController.java:88` | GET | `/api/v1/admin/jobs` | admin:read + SYSTEM_ADMIN |
| AdminController | `AdminController.java:106` | POST | `/api/v1/admin/jobs/{id}/retry` | admin:write + SYSTEM_ADMIN |
| AdminController | `AdminController.java:126` | GET | `/api/v1/admin/settings` | admin:read + SYSTEM_ADMIN |
| CollabController | `backend/.../collab/CollabController.java` | — | `/api/v1/collab/comments`, `/activity` | — |
| SessionController | `backend/.../identity/SessionController.java:27` | POST | `/api/v1/sessions/revoke` | session:write (+SYSTEM_ADMIN for others) |
| DeviceController | `backend/.../notify/DeviceController.java` | — | `/api/v1/devices/...` | — |
| ReminderPreferenceController | `backend/.../calendar/ReminderPreferenceController.java` | — | `/api/v1/reminder-preferences/...` | — |
| RealtimeController | `backend/.../realtime/RealtimeController.java` | — | `/api/v1/realtime/stream` | Ticket-based (no Bearer) |
| HealthController | `backend/.../admin/HealthController.java` | GET | `/actuator/health` | Public |

**Lưu ý:** Danh sách trên lấy từ đọc trực tiếp các file controller. Các endpoint có thể có thêm mapping không được đọc đầy đủ do output bị cắt (ví dụ `ProjectController` POST, `TaskController` full methods, `FileController` full methods, `CalendarController` tiếp sau dòng 120, `ReportController` tiếp sau dòng 80, `AiController` tiếp sau dòng 80).

### 2.2 Service, Entity, Repository

| Module | Service | Entity | Repository |
|--------|---------|--------|------------|
| Identity | `IdentityService.java` | `AppUser.java`, `UserSession.java`, `UserRefreshToken.java`, `RecoveryCode.java`, `ReauthTicket.java` | `AppUserRepository.java`, `UserSessionRepository.java`, `UserRefreshTokenRepository.java`, `RecoveryCodeRepository.java`, `ReauthTicketRepository.java` |
| Workspace | `WorkspaceService.java` | `Workspace.java`, `WorkspaceMembership.java`, `WorkspaceInvitation.java` | `WorkspaceRepository.java`, `WorkspaceMembershipRepository.java`, `WorkspaceInvitationRepository.java` |
| Project | `ProjectService.java` (KHÔNG XÁC MINH ĐƯỢC nội dung) | `Project.java`, `ProjectMembership.java`, `Milestone.java` | `ProjectRepository.java`, `ProjectMembershipRepository.java`, `MilestoneRepository.java` |
| Task | `TaskService.java` | `Task.java`, `TaskAssignment.java`, `TaskDependency.java`, `ChecklistItem.java`, `TaskTag.java`, `TimeLog.java` | `TaskRepository.java`, `TaskAssignmentRepository.java`, `TaskDependencyRepository.java`, `ChecklistItemRepository.java`, `TaskTagRepository.java`, `TimeLogRepository.java` |
| Workflow | `WorkflowService.java` | `WorkflowEvent.java`, `ApprovalRound.java`, `ApprovalDesignation.java`, `ApprovalDecision.java` | `WorkflowEventRepository.java`, `ApprovalRoundRepository.java`, `ApprovalDesignationRepository.java`, `ApprovalDecisionRepository.java` |
| Collab | `CommentService.java`, `ActivityService.java` | `TaskComment.java`, `ActivityEvent.java` | `TaskCommentRepository.java`, `ActivityEventRepository.java` |
| Files | `FileService.java`, `FileContentService.java`, `StreamConsumeService.java` | `Attachment.java`, `FileVersion.java`, `FileObject.java`, `StreamTicket.java`, `FileAccessEvent.java` | `AttachmentRepository.java`, `FileVersionRepository.java` (KHÔNG XÁC MINH ĐƯỢC), `UploadSessionRepository.java`, `StreamTicketRepository.java` (KHÔNG XÁC MINH ĐƯỢC), `FileAccessEventRepository.java` |
| Calendar | `CalendarService.java`, `ReminderEngine.java` | `ReminderRule.java` (KHÔNG XÁC MINH ĐƯỢC entity file) | `ReminderRuleRepository.java` |
| Notify | `NotificationService.java`, `NotificationProcessor.java`, `DevicePushService.java`, `PushEmailGateway.java` | `Notification.java` (KHÔNG XÁC MINH ĐƯỢC entity file) | `NotificationRepository.java`, `NotificationPreferenceRepository.java` |
| Reports | `ReportService.java`, `ExportWorker.java`, `ExportWorkerTx.java`, `DashboardComposer.java`, `MetricDictionary.java`, `ExportJobManager.java` | `ReportExportJob.java` | `ReportExportJobRepository.java` |
| AI | `AiService.java`, `AiJobStore.java`, `AiJobRunner.java` | `AiJob.java`, `AiSuggestion.java`, `AiInteraction.java`, `AiContextManifest.java`, `AiModelConfiguration.java` | `AiJobRepository.java`, `AiSuggestionRepository.java`, `AiInteractionRepository.java`, `AiContextManifestRepository.java`, `AiModelConfigurationRepository.java` |
| Jobs | `JobRunner.java`, `JobHandler.java` (+ 10 handler) | — | `BackgroundJobRepository.java` (KHÔNG XÁC MINH ĐƯỢC) |
| Realtime | `RealtimePublisher.java`, `RealtimeRegistry.java`, `RealtimeHandshakeFilter.java`, `WsTicketService.java` | `RealtimeTicket.java`, `RealtimeSignal.java` | `RealtimeTicketRepository.java` |
| Security | `GrantService.java`, `PolicyEnforcer.java`, `AuthorizationPolicy.java`, `BootstrapRunner.java` | `GlobalPermissionGrant.java` | `GlobalPermissionGrantRepository.java` |
| Admin | — | `SystemSetting.java` | `SystemSettingRepository.java` |
| Common | `AuditService.java`, `OutboxService.java`, `TokenHash.java`, `RateLimiter.java`, `UuidV7.java` | `AuditEvent.java` (KHÔNG XÁC MINH ĐƯỢC), `ApiResult.java`, `ErrorEnvelope.java`, `PageResponse.java` | `AuditEventRepository.java` (KHÔNG XÁC MINH ĐƯỢC), `BackgroundJobRepository.java` (KHÔNG XÁC MINH ĐƯỢC) |

### 2.3 Luồng đăng ký / đăng nhập / refresh

- **Register:** `IdentityService.register()` (`IdentityService.java:99-117`) tạo user với `status = "PENDING"`, `active = false`. Yêu cầu `selfRegistration = true`. Không có email verification. Sau khi đăng ký, tài khoản ở trạng thái chờ duyệt.
- **Login:** `IdentityService.login()` (`IdentityService.java:174-225`) tra cứu theo username hoặc email, kiểm tra mật khẩu BCrypt, kiểm tra locked/active, thực hiện single-session (revoke phiên cũ), tạo `user_session` + `user_refresh_token`. Access token TTL 1 ngày, refresh 7 ngày.
- **Refresh:** `IdentityService.refresh()` (`IdentityService.java:290-341`) rotate refresh token, kiểm tra session bound còn active, tạo session mới.
- **Cách tính role:** Không có cột `role` trên `app_user`. Role/admin được xác định qua `GlobalPermissionGrant` với code `SYSTEM_ADMIN` (`GrantService.isSystemAdmin()` `GrantService.java:62-64`). Login **không trả role** trong `TokenData` (trước khi sửa: `AuthController.java:36-39` chỉ có access_token, refresh_token, expires_at, user_id). Sau khi sửa (phiên làm việc này): thêm trường `role` ("SYSTEM_ADMIN" hoặc "USER").

### 2.4 Cơ chế phân quyền

- **GrantService** (`security/GrantService.java:30-154`): Deny-by-default. Không có grant row = không quyền (403 + `MISSING_GRANT` audit). Không tin client headers (`X-Is-Admin` 등). Self-grant luôn 403 `SELF_ESCALATION`. Chỉ SYSTEM_ADMIN hoặc người đã có grant đó mới có thể grant/revoke cho người khác.
- **PolicyEnforcer** (`security/PolicyEnforcer.java:28-201`): Pipeline P1..P9. P1 authenticate, P2 isValidActiveActor, P3 scope, P4 hasParticipation, P5 role/permission, P6 ownership, P7 state-guard, P8 rate-limit, P9 audit. Missing READ → 404 (anti-oracle). Write denied on visible resource → 403.
- **AuthorizationPolicy** (`security/AuthorizationPolicy.java:17-92`): `decide()` / `decideWithState()`. Enforce workspace/project ARCHIVED/DELETED overrides. ON_HOLD/COMPLETED chặn write-like actions.
- **SessionAuthenticationFilter** (`security/SessionAuthenticationFilter.java:47-134`): Opaque Bearer token → SHA-256 → user_session (active + unexpired) → app_user (ACTIVE, not locked). 401 INVALID_SESSION, 403 ACCOUNT_NOT_ACTIVE, 423 ACCOUNT_LOCKED.
- **RequiresPermission** (`security/RequiresPermission.java:10-14`): Annotation trên controller method, enforce bởi PolicyEnforcer (cần kiểm tra xem có interceptor/AOP không — KHÔNG XÁC MINH ĐƯỢC xem annotation được enforce tự động hay chỉ declaration).

### 2.5 Xử lý lỗi 401/403/404

| Status | Mã lỗi | Nơi xử lý | File:dòng |
|--------|--------|-----------|-----------|
| 401 | INVALID_SESSION, ACCESS_TOKEN_EXPIRED, REFRESH_TOKEN_INVALID | SessionAuthenticationFilter | `SessionAuthenticationFilter.java:90, 96` |
| 403 | ACCOUNT_NOT_ACTIVE, ACCOUNT_LOCKED, FORBIDDEN, MISSING_GRANT | SessionAuthenticationFilter, GrantService, PolicyEnforcer | `SessionAuthenticationFilter.java:101, 108`; `GrantService.java:71` |
| 404 | RESOURCE_NOT_FOUND (anti-oracle: hide existence) | PolicyEnforcer, controllers | `PolicyEnforcer.java:178`; `SessionAuthenticationFilter.java:267` |
| 423 | ACCOUNT_LOCKED | SessionAuthenticationFilter | `SessionAuthenticationFilter.java:108` |
| 428 | REAUTH_REQUIRED, IF_MATCH_REQUIRED | Controllers (If-Match, reauth actions) | `WorkflowController.java:42, 51` |
| 409 | STATE_CONFLICT, VERSION_CONFLICT, LAST_PROJECT_MANAGER, etc. | Controllers + services | Nhiều nơi |
| 422 | VALIDATION_FAILED, FILE_TYPE_NOT_ALLOWED, etc. | Controllers + services | Nhiều nơi |
| 429 | RATE_LIMITED | RateLimitFilter | KHÔNG XÁC MINH ĐƯỢC file filter (RateLimitFilter.java tồn tại) |

**Error envelope:** `ErrorEnvelope.java` (common), `ApiExceptionHandler.java` (common). Response JSON có `error.code`, `error.message_key`, `error.reason_code`, `correlation_id`, `timestamp`.

---

## MỤC 3: CƠ SỞ DỮ LIỆU

### 3.1 Danh sách migration V1–V12

| Migration | File | Mô tả |
|-----------|------|-------|
| V1 | `db/migration/V1__core.sql` | Core schema: app_user, user_session, user_refresh_token, workspace, workspace_membership, workspace_invitation, project, project_membership, milestone, task, task_assignment, task_dependency, checklist_item, approval_round, approval_designation, approval_decision, workflow_event, comment, activity_event, attachment, file_version, file_object, upload_session, notification, reminder_rule, audit_event, outbox_event, background_job, idempotency_record, system_setting |
| V2 | `db/migration/V2__ph1.sql` | Thêm status/version/locked_until/deleted_at cho app_user, user_session, workspace, project. Tạo reauth_ticket, recovery_code. Thêm GUEST/VIEWER role. Thêm audit_event columns. |
| V3 | `db/migration/V3__ph2.sql` | Task full PH-2 columns (start_at, due_timezone, milestone_id, soft-delete, cancelled, reopened...). Tạo task_tag, time_log. Approval round/decision/designation PH-2 columns. Comment threading + soft-delete. |
| V4 | `db/migration/V4__ph3.sql` | Attachment lifecycle/status/target_type/target_id/soft-delete. File version checksum. File object scan_status. Upload session staging. Tạo file_access_event, stream_ticket. Activity event columns. Comment body length check (8000). |
| V5 | `db/migration/V5__ph4.sql` | App_user timezone/locale/quiet_hours. Reminder rule status/paused. Tạo reminder_schedule, task_deadline_change, calendar_export_record. Notification full columns (seq, type, severity, redacted, read_at, dedup_token...). Tạo notification_preference, device_push_registration, delivery_attempt, realtime_cursor. |
| V6 | `db/migration/V6__ph6.sql` | Tạo metric_daily_snapshot, report_definition, report_export_job. Seeds report definitions. |
| V7 | `db/migration/V7__ai.sql` | Tạo ai_context_manifest, ai_model_configuration, ai_job, ai_suggestion, ai_interaction. Seed ollama config. |
| V8 | `db/migration/V8__ws_ticket.sql` | Tạo realtime_ticket (short-lived single-use WS handshake). |
| V9 | `db/migration/V9__grants.sql` | Tạo global_permission_grant (deny-by-default grant store). |
| V10 | `db/migration/V10__f2_integrity.sql` | Checklist UQ, personal workspace 1-1, grant noscope, outbox/bgjob idempotency, ticket expiry, export scope check, ai_job FK (NOT VALID). |
| V11 | `db/migration/V11__f2_perf.sql` | Indexes cho task list, assignment, tag, membership, calendar/reminder, file, comment/workflow/timelog, bgjob, upload cleanup. |
| V12 | `db/migration/V12__f3_workers.sql` | Outbox relay columns + dispatch ledger. Background job worker columns (lease, attempts, error_code...). Report export job worker columns (object_key, file_size, error_code...). |

### 3.2 Bảng liên quan đến yêu cầu A–J

| Yêu cầu | Bảng có sẵn | Bảng THIẾU |
|---------|-------------|------------|
| A. Tài khoản | `app_user`, `user_session`, `reauth_ticket`, `recovery_code` | — |
| B. Task + mời thành viên | `task`, `task_assignment`, `workspace_membership`, `project_membership` | Không có bảng task-level invitation (chỉ workspace/project membership) |
| C. Subtask workflow DOING→SUBMITTED→DONE/REWORK | `task` (state CHECK: TODO, IN_PROGRESS, PENDING_APPROVAL, COMPLETED, CANCELLED) | Không có state DOING, SUBMITTED, REWORK |
| D. "Tôi nhận làm" / "bỏ nhận" | `task_assignment` | Không có cột/state cho claim/unclaim |
| E. File BRIEF/RESULT | `attachment`, `file_version`, `file_object` | Không phân loại BRIEF/RESULT |
| F. Progress theo subtask | `task`, `task_assignment`, `ProgressCalculator.java` | Không rõ enforce "chỉ đóng khi mọi subtask DONE/CANCELLED" |
| G. Deadline + nhắc hạn | `reminder_rule`, `reminder_schedule`, `task_deadline_change`, `notification` | — |
| H. Ghi chú cá nhân | `comment` (chỉ gắn task) | Không có bảng note cá nhân riêng |
| I. Lịch sử thay đổi | `activity_event`, `workflow_event`, `audit_event` | — |
| J. Không dùng AI | — | `ai_job`, `ai_suggestion`, `ai_interaction`, `ai_context_manifest`, `ai_model_configuration` (TỒN TẠI, vi phạm yêu cầu J) |

### 3.3 Index quan trọng (có sẵn)

| Index | Bảng | File:dòng |
|-------|------|-----------|
| uq_single_active_session | user_session | `V1__core.sql:27` |
| uq_single_workspace_owner | workspace_membership | `V1__core.sql:55` |
| uq_personal_workspace_owner | workspace | `V10__f2_integrity.sql:28-30` |
| uq_live_grant / uq_live_grant_noscope | global_permission_grant | `V9__grants.sql:16-18`, `V10__f2_integrity.sql:36-39` |
| uq_outbox_idem_key, uq_bgjob_idem_key | outbox_event, background_job | `V10__f2_integrity.sql:43-48` |
| uq_checklist_task_position | checklist_item | `V10__f2_integrity.sql:22-23` |
| uq_approval_decision_round | approval_decision | `V3__ph2.sql:153` |
| uq_notification_dedup | notification | `V5__ph4.sql:83` |
| ix_notification_user_unread | notification | `V5__ph4.sql:84` |
| ix_task_project_updated, ix_task_project_state, ix_task_project_due | task | `V11__f2_perf.sql:21-29` |
| ix_assign_user_active, ix_assign_task_status | task_assignment | `V11__f2_perf.sql:35-38` |
| ix_pmember_user, ix_wsmember_user | project_membership, workspace_membership | `V11__f2_perf.sql:45-48` |
| ix_reminder_user, ix_reminder_task | reminder_rule | `V11__f2_perf.sql:53-54` |
| ix_attachment_task | attachment | `V11__f2_perf.sql:57-58` |

**KHÔNG có index rõ ràng cho:** `(assignee_id, status, due_at)` hợp nhất (có tách riêng), `(task_id, state)` cho query danh sách task. Cần verify thêm.

### 3.4 Rollback scripts

| File | Mô tả |
|------|-------|
| `db/rollback/U2__ph1_rollback.sql` | Rollback V2 |
| `db/rollback/U3__ph2_rollback.sql` | Rollback V3 |
| `db/rollback/U4__ph3_rollback.sql` | Rollback V4 |
| `db/rollback/U5__ph4_rollback.sql` | Rollback V5 |
| `db/rollback/U6__ph6_rollback.sql` | Rollback V6 |
| V7–V12 | KHÔNG có rollback script |

---

## MỤC 4: FRONTEND WEB

### 4.1 Route và Guard

| Route | Component | Guard | File:dòng |
|-------|-----------|-------|-----------|
| /login | Login | — | `App.tsx:29` |
| /register | Register | — | `App.tsx:30` |
| /* (nhiều route) | Layout + nested routes | RequireAuth | `App.tsx:31-53` |
| /dashboard | Dashboard | RequireAuth | `App.tsx:37` |
| /workspaces | Workspaces | RequireAuth | `App.tsx:38` |
| /projects | Projects | RequireAuth | `App.tsx:39` |
| /tasks | Tasks | RequireAuth | `App.tsx:40` |
| /tasks/:id | TaskDetail | RequireAuth | `App.tsx:41` |
| /files | Files | RequireAuth | `App.tsx:42` |
| /calendar | Calendar | RequireAuth | `App.tsx:43` |
| /notifications | Notifications | RequireAuth | `App.tsx:44` |
| /reports | Reports | RequireAuth | `App.tsx:45` |
| /ai | Ai | RequireAuth + ENABLE_AI | `App.tsx:46` |
| /admin | Admin | RequireAuth + RequireAdmin | `App.tsx:47` |

- **RequireAuth:** Kiểm tra `getToken()` (localStorage). Nếu không có token → redirect `/login`. (`App.tsx:62-66`)
- **RequireAdmin:** Kiểm tra `user.role` từ AuthContext. Nếu role known và không phải admin → redirect `/tasks`. Nếu role unknown → cho vào và để backend trả 403. (`App.tsx:71-77`)
- **Vấn đề:** Trước khi sửa, login không trả `role` nên `user.role` luôn `undefined` → `RequireAdmin` luôn cho vào trang admin (chỉ backend chặn sau). Sau khi sửa (phiên này): login trả `role`, AuthContext lưu `role`.

### 4.2 Các trang (pages)

| Trang | File | Mô tả |
|-------|------|-------|
| Login | `web/src/pages/Login.tsx` | Đăng nhập username/email + password |
| Register | `web/src/pages/Register.tsx` | Đăng ký (username, email, display_name, password, confirm) |
| Dashboard | `web/src/pages/Dashboard.tsx` | Tổng quan (KHÔNG XÁC MINH ĐƯỢC chi tiết) |
| Workspaces | `web/src/pages/Workspaces.tsx` | Dan sách workspace, tạo mới |
| Projects | `web/src/pages/Projects.tsx` | Dan sách dự án, tạo mới, lọc workspace |
| Tasks | `web/src/pages/Tasks.tsx` | Dan sách task, filter, sort, cursor pagination, kanban view |
| TaskDetail | `web/src/pages/TaskDetail.tsx` | Chi tiết task, subtask, checklist, comment, attachment, timelog |
| Files | `web/src/pages/Files.tsx` | Quản lý file đính kèm |
| Calendar | `web/src/pages/Calendar.tsx` | Lịch, deadline, reminder |
| Notifications | `web/src/pages/Notifications.tsx` | Dan sách thông báo, đọc/all read |
| Reports | `web/src/pages/Reports.tsx` | Dashboard báo cáo, export PDF/XLSX/CSV |
| Admin | `web/src/pages/Admin.tsx` | Duyệt tài khoản, cài đặt hệ thống, audit log (scoped) |
| Ai | `web/src/pages/Ai.tsx` | Trợ lý AI (gợi ý task, subtask, priority, deadline, assignee) |
| WorkflowPanel | `web/src/pages/WorkflowPanel.tsx` | Panel workflow (test có sẵn) |

### 4.3 Nơi gọi API

- `web/src/api/client.ts`: `apiFetch()` wrapper cho mọi gọi REST. Gắn `Authorization: Bearer`, `X-Correlation-Id`, `X-Client-Type: WEB`, `If-Match`, `Idempotency-Key`. Xử lý refresh token tự động khi 401. Parse `ErrorEnvelope`.
- `web/src/api/types.ts`: Type definitions cho `ApiError`, `ErrorEnvelope`, `SuccessEnvelope`, `UnwrappedPage`.
- Mọi page gọi `apiFetch` trực tiếp (không có Redux/Context state management ngoài AuthContext).

### 4.4 Nơi lưu token

- `localStorage` keys: `tm.access_token`, `tm.refresh_token` (`client.ts:12-13`).
- `AuthContext.tsx`: `setTokens()` lưu vào localStorage, `clearTokens()` xoá.
- `getToken()` / `getRefreshToken()` đọc từ localStorage.

### 4.5 i18n

- `web/src/i18n/index.ts`: KHÔNG XÁC MINH ĐƯỢC nội dung (file tồn tại, chưa đọc).
- `web/src/i18n/en.ts`: 191 khóa, tiếng Anh.
- `web/src/i18n/vi.ts`: 191 khóa, tiếng Việt.
- `web/src/config.ts`: `ENABLE_AI` build-time switch (mặc định true).

---

## MỤC 5: MOBILE FLUTTER

### 5.1 Tóm tắt hiện trạng

| Thành phần | File | Mô tả |
|------------|------|-------|
| Entry | `lib/main.dart`, `lib/app.dart` | Khởi động app |
| Auth | `lib/auth/session.dart`, `lib/auth/reauth.dart`, `lib/auth/device_info.dart` | Quản lý session, reauth, device info |
| API | `lib/api/client.dart`, `lib/api/endpoints.dart` | Dio client, danh sách endpoint |
| Cache | `lib/cache/secure_store.dart`, `lib/cache/offline_store.dart`, `lib/cache/connectivity.dart` | Secure storage, offline store, connectivity |
| Realtime | `lib/realtime/ws_client.dart` | WebSocket client |
| Push | `lib/push/fcm_stub.dart` | FCM stub (chưa có thật) |
| Widgets | `lib/widgets/realtime_indicator.dart`, `lib/widgets/private_gate.dart`, `lib/widgets/offline_banner.dart` | Indicator, gate, offline banner |
| Screens | `lib/screens/*.dart` | 14 màn hình (login, tasks, projects, workspaces, reports, calendar, files, comments, notifications, AI, task_detail, reauth) |

### 5.2 Test

| File | Mô tả |
|------|-------|
| `test/api_client_test.dart` | Test API client |
| `test/login_flow_test.dart` | Test luồng đăng nhập |
| `test/session_test.dart` | Test session |
| `test/offline_store_test.dart` | Test offline store |
| `test/ws_client_test.dart` | Test WebSocket client |
| `test/widget_test.dart` | Test widget |
| `test/fake_backend.dart` | Fake backend cho test |

**Không dùng:** Flutter SDK chưa xác minh hoàn chỉnh (`flutter --version` không trả output — `docs/00-AUDIT-SPEC.md` mục B.2). iOS build chưa kiểm tra.

---

## MỤC 6: ĐỐI CHIẾS YÊU CẦU NGHIỆP VỤ A–J VỚI CODE

| Mục | Trạng thái | Bằng chứng | Còn thiếu |
|-----|------------|------------|-----------|
| **A. Tài khoản: tự đăng ký, ACTIVE ngay, không duyệt. Admin chỉ khóa/mở khóa, reset password, xem danh sách, KHÔNG đọc nội dung task.** | [SAI LỆCH] | `IdentityService.java:111-112`: `u.setStatus("PENDING")`, `u.setActive(false)`. `AccountController.java:38-51`: endpoint `/accounts` trả danh sách pending, cần `ACCOUNT_APPROVE_GLOBAL` grant. | Cần bỏ bắt buộc duyệt, tự set ACTIVE sau khi đăng ký. Admin không được đọc nội dung task — code hiện tại đã thỏa mãn phần này (AdminController chỉ đọc audit/jobs/settings, metadata only). |
| **B. Task: mọi user tạo được task, người tạo là chủ. Chủ mời người khác theo username/ID (chỉ ACTIVE), tạo subtask và gán. Người ngoài task không thấy gì.** | [MỘT PHẦN] | `TaskService.java:72-81`: `requireParticipation()` kiểm tra project membership, GUEST bị 403. `task_assignment` table (`V1__core.sql:113-119`). | Không có task-level invitation theo username/ID. Chỉ có workspace/project membership. Không rõ "người ngoài task không thấy gì" có được enforce ở task level không (policy kiểm tra project participation, không phải task participation). |
| **C. Subtask người khác làm: TODO → DOING → SUBMITTED → (chủ duyệt) DONE; từ chối bắt buộc lý do → REWORK → DOING. Có "rút lại bài nộp".** | [THIẾU] | `V1__core.sql:102-103`: state CHECK chỉ có `TODO, IN_PROGRESS, PENDING_APPROVAL, COMPLETED, CANCELLED`. `WorkflowController.java:64-118`: start/submit/approve/reject/cancel/reopen. | Không có state `DOING`, `SUBMITTED`, `REWORK`. Không có "rút lại bài nộp" (withdraw submission). Reject về `IN_PROGRESS` không phải `REWORK` → `DOING`. |
| **D. Subtask chủ tự làm: bấm "Tôi nhận làm" mới bắt đầu, đính kèm kết quả, Done trực tiếp. Chưa nhận thì chỉ xem. Có "bỏ nhận" khi chưa Done.** | [THIẾU] | `Task.java` entity (KHÔNG XÁC MINH ĐƯỢC đầy đủ, không đọc được do output cắt). | Không có state "đã nhận làm" / claim / unclaim. Subtask depth <= 1 (`V1__core.sql:99`). |
| **E. File: tách BRIEF (chủ sửa) và RESULT (người làm đính kèm). Chủ chưa nhận không đính kèm RESULT.** | [THIẾU] | `V1__core.sql:198-207`: chỉ có 1 bảng `attachment` (không phân loại). `FileController.java:114`: upload cho task. | Không có cột `file_type` (BRIEF/RESULT). Không có enforce "chủ chưa nhận không đính kèm RESULT". |
| **F. Task cha: tiến độ tự tính theo subtask DONE (bỏ CANCELLED); chỉ đóng khi mọi subtask DONE hoặc CANCELLED.** | [MỘT PHẦN] | `ProgressCalculator.java` (tồn tại, KHÔNG XÁC MINH ĐƯỢC nội dung). `V1__core.sql:99`: depth CHECK 0/1. `V3__ph2.sql:56-58`: `task_subtask_milestone_check` (depth=0 hoặc milestone_id NULL). | Không rõ progress tính theo công thức nào (DONE / (tổng - CANCELLED)). Không rõ enforce "chỉ đóng khi mọi subtask DONE/CANCELLED". |
| **G. Deadline + nhắc hạn (chỉ web): nhắc D-1, H-1, quá hạn nhắc người gán và chủ, không nhắc trùng, đổi hạn/người gán tính lại, SUBMITTED/DONE ngừng nhắc. Chuông thông báo, đánh dấu đã đọc, trang "Việc có deadline", mục "Chờ tôi duyệt".** | [MỘT PHẦN] | `V5__ph4.sql:24-31`: `reminder_schedule` (fire_at, status). `V5__ph4.sql:54-69`: notification columns (read_at, redacted, dedup_token...). `CalendarController.java:42-58`: `/calendar` view. `NotificationController.java:24-52`: list, markRead. | Không rõ nhắc D-1/H-1 cụ thể. Không rõ "không nhắc trùng" (dedup_token có nhưng chưa verify logic). Không rõ trang "Việc có deadline" phân loại Quá hạn/Hôm nay/7 ngày/Sau đó. Không rõ mục "Chờ tôi duyệt" cho chủ. |
| **H. Ghi chú cá nhân, gắn task, có remind_at.** | [THIẾU] | `comment` table (`V1__core.sql:179-186`) chỉ gắn task, không phải ghi chú cá nhân. | Không có bảng `note`/`personal_note`. Không có `remind_at` cho note cá nhân (chỉ có `reminder_rule` cho task). |
| **I. Lịch sử thay đổi (activity log) cho task và subtask.** | [ĐỦ] | `activity_event` (`V1__core.sql:188-196`), `workflow_event` (`V1__core.sql:169-177`), `audit_event` (`V1__core.sql:252-260`). | — |
| **J. Không dùng AI. Liệt kê mọi tham chiếu DEC-41/Ollama/AI.** | [SAI LỆCH] | `V7__ai.sql`: tạo bảng `ai_job`, `ai_suggestion`, `ai_interaction`, `ai_context_manifest`, `ai_model_configuration`. `AiController.java`, `AiService.java`, `OllamaClient.java`, `MockOllamaClient.java` tồn tại. `application.yml` (KHÔNG XÁC MINH ĐƯỢC có ai.enabled không). | Code đã tích hợp AI, vi phạm yêu cầu "Không dùng AI". Cần liệt kê mọi tham chiếu DEC-41/Ollama/AI (xem mục 11). |

---

## MỤC 7: BẢO MẬT

| Mục | Trạng thái | Bằng chứng (file:dòng) |
|-----|------------|------------------------|
| Băm mật khẩu | [ĐỦ] | `IdentityService.java:44`: `new BCryptPasswordEncoder()`. `register()` dòng 110: `encoder.encode(password)`. `login()` dòng 193: `encoder.matches(password, u.getPasswordHash())`. |
| Thời hạn token | [ĐỦ] | `IdentityService.java:214`: `exp = now + 1 day` (access). `IdentityService.java:220`: `+ 7 days` (refresh). |
| Lưu secret | [KHÔNG XÁC MINH ĐƯỢC] | `.env` tồn tại trong `deploy/` nhưng nội dung không đọc được (có thể chứa secrets). `application.yml` dòng 36-37: `bootstrap.admin-username` trống. Không thấy mã hóa secret ở rest. |
| CORS | [KHÔNG XÁC MINH ĐƯỢC] | `CorsConfigTest.java` tồn tại trong test, nhưng chưa đọc được cấu hình thực tế. `deploy/docker-compose.yml` dòng 82: `SMARTTASK_CORS_ALLOWED_ORIGINS`. |
| Giới hạn tần suất | [ĐỦ] | `common/RateLimiter.java`, `common/RateLimitFilter.java` tồn tại. `RateLimiterTest.java` test. |
| Kiểm tra quyền backend vs frontend | [ĐỦ backend] | Backend: `PolicyEnforcer`, `GrantService`, `SessionAuthenticationFilter` kiểm tra mọi request. Frontend: chỉ ẩn/hiện nút (ví dụ `RequireAdmin` `App.tsx:71-77`), backend là nguồn sự thật. |
| Upload file (kiểu, dung lượng, đường dẫn) | [MỘT PHẦN] | `application.yml` dòng 22-24: `max-file-size 50MB`, `max-request-size 52MB`. `V4__ph3.sql:110-112`: `allowed_content_types` (pdf, png, jpeg, docx, xlsx, zip, csv). `file.scan_enabled = false` (chưa có engine). | KHÔNG XÁC MINH ĐƯỢC đường dẫn upload (local vs MinIO — `FILE_GATEWAY_MODE` trong docker-compose). |
| SQL Injection | [ĐỦ (Spring Data JPA)] | Dùng Spring Data JPA repository, parameter binding. Không thấy native query concatenation trong các file đã đọc. |
| Thông tin nhạy cảm trong log | [MỘT PHẦN] | `common/RedactingLogger.java` tồn tại. `SecurityExceptionHandler.java` tồn tại. `AdminController.java:76-79`: audit payload/before/after là `[REDACTED]`. | KHÔNG XÁC MINH ĐƯỢC toàn bộ log path có bị rò rỉ token/mật khẩu không. |

---

## MỤC 8: CHẤT LƯỢNG MÃ

### 8.1 Test hiện có

| Loại | Số lượng | File mẫu | Chạy được không |
|------|----------|----------|-----------------|
| Backend unit test | ~25 file | `security/GrantServiceTest.java`, `common/RateLimiterTest.java`, `common/ErrorEnvelopeTest.java` | KHÔNG XÁC MINH ĐƯỢC (cần Maven, chưa cài) |
| Backend integration test (IT) | ~20 file | `itg/LoginFlowIT.java`, `itg/RbacIT.java`, `itg/F4EndpointsIT.java` | KHÔNG XÁC MINH ĐƯỢC (cần Testcontainers + Docker) |
| Web test | 5 file | `test/Tasks.test.tsx`, `test/WorkflowPanel.test.tsx`, `test/Guards.test.tsx` | KHÔNG XÁC MINH ĐƯỢC (cần pnpm, node_modules có sẵn) |
| Mobile test | 6 file | `test/login_flow_test.dart`, `test/api_client_test.dart` | KHÔNG XÁC MINH ĐƯỢC (cần Flutter SDK) |

**Lưu ý:** Nhiều IT test sử dụng `@Disabled("needs-ollama-machine...")` hoặc cần Docker/PostgreSQL thật. Ví dụ: `AiLatencyBenchmarkTest.java:15` `@Disabled`, `HttpOllamaClientLiveTest.java:15` `@Disabled`.

### 8.2 Lỗi biên dịch / cảnh báo / code chết

- KHÔNG XÁC MINH ĐƯỢC (chưa chạy build). Cần Maven cho backend, pnpm cho web, Flutter cho mobile.
- `backend/Dockerfile` có `dependency:go-offline || true` (dòng 9) — có thể fail lặng.

### 8.3 TODO / FIXME / placeholder

- `FileController.java`: `pushUnregisterStub()` (`IdentityService.java:227`) có comment "stub: unregister push token for revoked session".
- `PushEmailGateway.java`: KHÔNG XÁC MINH ĐƯỢC (chưa đọc), tên là stub (push/email chưa có credentials — `DEPLOY.md` dòng 418-419).

### 8.4 Trùng lặp / không nhất quán backend-frontend

| Vị trí | Field bên Backend | Field bên Frontend | Ghi chú |
|--------|------------------|-------------------|---------|
| Login response | `access_token`, `refresh_token`, `expires_at`, `user_id` (trước khi sửa) | `access_token`, `refresh_token`, `user_id`, `role` | Frontend `AuthContext.tsx` mong đợi `role`, backend trước khi sửa không trả (đã sửa trong phiên này). |
| Task DTO | `id`, `project_id`, `title`, `state`, `blocked_flag`, `version`, `updated_at` | `TaskRow` interface (`Tasks.tsx:14-23`) có cùng field | Nhất quán. |
| Project DTO | `id`, `workspace_id`, `name`, `status`, `created_at` | `Project` interface (`Projects.tsx:8-13`) | Nhất quán. |
| Notification | `id`, `kind`, `read`, `occurred_at` | `NotificationController.java:46-47` trả `id`, `kind`, `read` | Frontend `Notifications.tsx` KHÔNG XÁC MINH ĐƯỢC (chưa đọc đầy đủ). |

---

## MỤC 9: HIỆU NĂNG CƠ BẢN

| Vấn suspected | Bằng chứng | File:dòng |
|---------------|------------|-----------|
| N+1 query trong `ProjectTaskController.tasks()` | Load toàn bộ task của project rồi filter trong memory Java (`ArrayList`, stream filter) | `ProjectTaskController.java:80-96` |
| Thiếu phân trang ở một số endpoint | `NotificationController.list()` load `all` rồi subList trong memory (`notifications.list(actor)`) | `NotificationController.java:34-48` |
| Index cho danh sách task | Có `ix_task_project_updated`, `ix_task_project_state`, `ix_task_project_due` | `V11__f2_perf.sql:21-29` |
| Index cho assignment | Có `ix_assign_user_active`, `ix_assign_task_status` | `V11__f2_perf.sql:35-38` |
| Index cho reminder | Có `ix_reminder_user`, `ix_reminder_task` | `V11__f2_perf.sql:53-54` |
| Index cho attachment | Có `ix_attachment_task` | `V11__f2_perf.sql:57-58` |
| KHÔNG có index cho `(assignee_id, status, due_at)` hợp nhất | Có tách riêng nhưng chưa verify query composite | KHÔNG XÁC MINH ĐƯỢC |

---

## MỤC 10: TRIỂN KHAI

### 10.1 Docker Compose

| Service | Image | Port | Mạng |
|---------|-------|------|------|
| db | postgres:16 | 5432 (internal) | internal |
| minio | bitnamilegacy/minio:2025.7.23-debian-12-r5 | 9000 (internal) | internal |
| minio-init | bitnamilegacy/minio (one-shot) | — | internal |
| backend | build từ `../backend` Dockerfile | 8080 (internal) | internal |
| web (Caddy + SPA) | caddy:2-alpine | 80, 443, 443/udp (published) | internal |

File: `deploy/docker-compose.yml`

### 10.2 Biến môi trường bắt buộc (.env.example)

| Biến | Mô tả | File |
|------|-------|------|
| POSTGRES_PASSWORD | | `deploy/.env.example` (KHÔNG XÁC MINH ĐƯỢC nội dung, file không đọc được) |
| MINIO_ROOT_PASSWORD | | |
| BOOTSTRAP_ADMIN_USERNAME | | |
| SELF_REGISTRATION | | |
| VITE_ENABLE_AI | | |
| OLLAMA_BASE_URL | | |
| JAVA_OPTS | | |

### 10.3 Script vận hành

| Script | Mô tả | File |
|--------|-------|------|
| backup.sh | Backup DB + MinIO | `deploy/backup.sh` |
| restore.sh | Restore (parafazi `--yes`) | `deploy/restore.sh` |
| smoke.sh | Kiểm tra health, login, realtime | `deploy/smoke.sh` |
| update.sh | Backup + rebuild + smoke | `deploy/update.sh` |
| pull-backups.sh/.ps1 | Kéo backup từ VPS về local | `deploy/pull-backups.sh`, `deploy/pull-backups.ps1` |

### 10.4 Những gì CHƯA chạy được trên máy dev và vì sao

| Vấn đề | File by chứng | Vì sao |
|--------|---------------|--------|
| Maven chưa cài | `docs/00-AUDIT-SPEC.md` mục B.2 | Cần Maven để build backend `.jar` |
| Docker daemon tắt | `docs/00-AUDIT-SPEC.md` mục B.2 | `failed to connect to npipe dockerDesktopLinuxEngine` |
| PostgreSQL client (psql) chưa có | `docs/00-AUDIT-SPEC.md` mục B.2 | Không thể kết nối DB trực tiếp |
| Ollama chưa cài | `docs/00-AUDIT-SPEC.md` mục B.2 | AI module không thể chạy thật |
| RAM 15.7 GB (< 16 GB khuyến nghị) | `docs/00-AUDIT-SPEC.md` mục B.3 | Không đủ cho load test 50–100 user + AI |
| Ổ D: còn 28 GB | `docs/00-AUDIT-SPEC.md` mục B.3 | Rủi ro nếu dựng MinIO + nhiều Docker image + dataset load test |
| Windows 11 Home (không phải server) | `docs/00-AUDIT-SPEC.md` mục B.3 | Production cần Linux VM riêng |
| Flutter SDK chưa xác minh | `docs/00-AUDIT-SPEC.md` mục B.2 | `flutter --version` không trả output |

---

## MỤC 11: TÀI LIỆU

### 11.1 Chỗ docs mâu thuẫn với code

| Docs | Nội dung docs | Code thực tế | Mâu thuẫn |
|------|--------------|--------------|-----------|
| `SMART_TASK_MANAGEMENT_FINAL_SPEC.md` mục 1 | "Tự đăng ký chỉ khi System Admin bật cấu hình; tài khoản tự đăng ký phải được duyệt" | `IdentityService.register()` dòng 111-112: `PENDING`, `active=false` | Nhất quán (yêu cầu duyệt) |
| `SMART_TASK_MANAGEMENT_FINAL_SPEC.md` mục 2 | "Tự đăng ký chỉ khi System Admin bật cấu hình" | `application.yml` dòng 35: `self-registration: true` | Có thể bật/tắt qua config |
| `docs/03-API-CONTRACT.md` §1.2 | "Một phiên duy nhất" | `V1__core.sql:27`: `uq_single_active_session` partial unique index | Nhất quán |
| `docs/03-API-CONTRACT.md` §1.2 | "Bootstrap System Admin: không có HTTP endpoint" | `BootstrapRunner.java`: `ApplicationRunner`, chỉ chạy local, không có endpoint | Nhất quán |
| `docs/00-AUDIT-SPEC.md` AUD-30 | "Mỗi ACTIVE account đúng một Personal Workspace" | `V10__f2_integrity.sql:28-30`: `uq_personal_workspace_owner` | Nhất quán |

### 11.2 Mọi tham chiếu còn sót tới DEC-41, Ollama, AI

#### Trong `docs/` (đã xử lý phần lớn trong phiên này, có thể còn sót):

| File | Dòng | Nội dung |
|------|------|----------|
| `docs/07-TEST-PLAN.md` | 399 | `TC-S-021`: SSRF qua `ai_model_configuration.ollama_base_url` — DEC-41 |
| `docs/07-TEST-PLAN.md` | 443 | `TC-W-024`: AI job lifecycle — DEC-41 |
| `docs/07-TEST-PLAN.md` | 476 | "Ollama chưa được cài (FACT 00-AUDIT §B, PR-04)" |
| `docs/07-TEST-PLAN.md` | 480 | `TC-AI-001`: `ai.enabled=false` mặc định — TREO:DEC-41 |
| `docs/07-TEST-PLAN.md` | 484 | `TC-AI-005`: Ollama timeout — DEC-41 |
| `docs/07-TEST-PLAN.md` | 492 | `TC-AI-013`: Không có egress ra ngoài máy — DEC-41 |
| `docs/07-TEST-PLAN.md` | 493 | `TC-AI-014`: Validate `ollama_base_url` — DEC-41 |
| `docs/07-TEST-PLAN.md` | 511 | `TC-P-006`: Benchmark riêng cho luồng AI — DEC-41 |
| `docs/07-TEST-PLAN.md` | 640 | `PR-04`: Ollama NOT FOUND — DEC-41 |
| `docs/07-TEST-PLAN.md` | 660-665 | Test AI cần mock Ollama, chạy thật sau DEC-41 |
| `docs/07-TEST-PLAN.md` | 698 | Trước Phase có AI: PR-04, DEC-41 |
| `docs/07-TEST-PLAN.md` | 1006 | Nhóm 07 AI — chỉ chạy mock cho tới DEC-41 |
| `docs/07-TEST-PLAN.md` | 1109 | NFR-09 — DEC-41/DEC-43 về hạ tầng |
| `docs/07-TEST-PLAN.md` | 1112 | NFR-08 và chất lượng AI — DEC-41 |
| `docs/07-TEST-PLAN.md` | 1141 | DEC-41 (BLOCKER) |

#### Trong `_recovery/` (sao lưu tài liệu cũ, KHÔNG được sửa theo quy tắc chỉ đọc):

| File | Dòng | Nội dung |
|------|------|----------|
| `_recovery/00-AUDIT-SPEC.md` | ~383 | AUD-41 (Ollama trên máy hiện tại) |
| `_recovery/06-IMPLEMENTATION-PLAN.md` | 23 | Ollama: Chặn PH-5 cho tới khi chốt DEC-41 |
| `_recovery/06-IMPLEMENTATION-PLAN.md` | 84 | DEC-41 | Hạ tầng Ollama | PH-5 |
| `_recovery/06-IMPLEMENTATION-PLAN.md` | 164-169 | PH-5 — AI Basic ⟵ GATE-AI (DEC-41) |
| `_recovery/08-DECISION-REGISTER.md` | 59 | NFR-08 | ... | **DEC-41** |
| `_recovery/08-DECISION-REGISTER.md` | 258 | DEC-41 — Hạ tầng Ollama cho V1? |
| `_recovery/08-DECISION-REGISTER.md` | 400 | DEC-41 : [P-A / P-B / P-C] |

#### Trong code backend:

| File | Dòng | Nội dung |
|------|------|----------|
| `backend/app/src/main/java/vn/smarttask/ai/AiController.java` | 28 | `@RequestMapping("/api/v1/ai")` |
| `backend/app/src/main/java/vn/smarttask/ai/OllamaClient.java` | — | Ollama HTTP client |
| `backend/app/src/main/java/vn/smarttask/ai/MockOllamaClient.java` | — | Mock Ollama |
| `backend/app/src/main/java/vn/smarttask/ai/HttpOllamaClient.java` | — | HTTP Ollama client |
| `backend/app/src/main/java/vn/smarttask/ai/AiService.java` | — | AI service |
| `backend/app/src/main/java/vn/smarttask/ai/AiJobRunner.java` | — | AI job runner |
| `backend/app/src/main/java/vn/smarttask/ai/AiJobStore.java` | — | AI job store |
| `backend/app/src/main/java/vn/smarttask/ai/RepositoryProjectApi.java` | — | Project API for AI |
| `backend/app/src/main/java/vn/smarttask/ai/ProjectApi.java` | — | Project API interface |
| `backend/app/src/main/java/vn/smarttask/ai/OllamaException.java` | — | Ollama exception |
| `backend/app/src/main/java/vn/smarttask/ai/PromptSanitizer.java` | — | Prompt sanitizer |
| `backend/app/src/main/java/vn/smarttask/ai/OutputValidator.java` | — | Output validator |
| `backend/app/src/main/java/vn/smarttask/ai/ContextManifestValidator.java` | — | Context manifest validator |
| `backend/app/src/main/java/vn/smarttask/ai/AiConfig.java` | — | AI config |
| `backend/app/src/main/java/vn/smarttask/ai/AiErrors.java` | — | AI errors |
| `backend/app/src/main/java/vn/smarttask/ai/AiFeature.java` | — | AI feature enum |
| `backend/app/src/main/java/vn/smarttask/ai/AiJobStatus.java` | — | AI job status enum |
| `backend/app/src/main/java/vn/smarttask/ai/AiJobLifecycle.java` | — | AI job lifecycle |
| `backend/app/src/main/java/vn/smarttask/ai/AiSuggestionStatus.java` | — | AI suggestion status |
| `backend/app/src/main/java/vn/smarttask/ai/service/AiService.java` | — | AI service impl |
| `backend/app/src/main/java/vn/smarttask/ai/service/AiJobRunner.java` | — | AI job runner impl |
| `backend/app/src/main/java/vn/smarttask/ai/service/AiJobStore.java` | — | AI job store impl |
| `backend/app/src/main/java/vn/smarttask/ai/RepositoryProjectApi.java` | — | Repository project API |
| `backend/app/src/main/java/vn/smarttask/jobs/AiGenerateJobHandler.java` | — | AI generate job handler |
| `backend/app/src/main/java/vn/smarttask/jobs/AiOutboxHandler.java` | — | AI outbox handler |
| `backend/app/db/migration/V7__ai.sql` | 1-78 | AI tables |
| `backend/app/db/migration/V7__ai.sql` | 24-29 | Seed ollama config (base_url, model, timeout, max_attempts) |

#### Trong code web:

| File | Dòng | Nội dung |
|------|------|----------|
| `web/src/pages/Ai.tsx` | — | Trang AI Assistant |
| `web/src/config.ts` | 6-8 | `ENABLE_AI` build switch |
| `web/src/App.tsx` | 46 | `/ai` route điều kiện ENABLE_AI |
| `web/src/components/Layout.tsx` | 30-32 | Nav link `/ai` điều kiện ENABLE_AI |

#### Trong code mobile:

| File | Dòng | Nội dung |
|------|------|----------|
| `mobile/lib/screens/ai_screen.dart` | — | Màn hình AI |
| `mobile/pubspec.yaml` | 3 | `description`: "Không co AI inference tren thiet bi." (chú ý: chỉ cấm inference trên thiết bị, không cấm AI qua backend) |

#### Trong deploy/infra:

| File | Dòng | Nội dung |
|------|------|----------|
| `deploy/docker-compose.yml` | 83 | `SMARTTASK_OLLAMA_BASE_URL` |
| `deploy/docker-compose.yml` | 84 | `SMARTTASK_OLLAMA_MODEL` |
| `infra/docker-compose.dev.yml` | 2 | `# KHONG co ollama (DEC-41...)` |

---

## MỤC 12: BẢNG ĐỐI CHIẾS TỔNG HỢP A–J

(Tương tự mục 6, được tóm tắt lại ở trên)

---

## MỤC 13: DANH SÁCH VẤN ĐỀ ĐÁNH SỐ

| # | Mức độ | Vị trí (file:dòng) | Mô tả | Hậu quả |
|---|--------|-------------------|-------|---------|
| 1 | Nghiêm trọng | `IdentityService.java:111-112` | Đăng ký tạo tài khoản với `status=PENDING`, `active=false`, yêu cầu admin duyệt. | Vi phạm yêu cầu A (tự đăng ký, ACTIVE ngay). Người dùng mới không thể đăng nhập cho đến khi admin duyệt. |
| 2 | Nghiêm trọng | `web/src/pages/Ai.tsx` (tồn tại), `backend/.../ai/*.java` (tồn tại), `V7__ai.sql` | Code đã tích hợp module AI (Ollama, ai_job, ai_suggestion...). | Vi phạm yêu cầu J (Không dùng AI). |
| 3 | Nghiêm trọng | `web/src/App.tsx:71-77` (trước khi sửa), `AuthController.java:36-39` (trước khi sửa) | `RequireAdmin` kiểm tra `user.role` nhưng login không trả `role`. | Mọi người dùng đều có thể truy cập trang `/admin` (chỉ backend chặn sau bằng 403). |
| 4 | Cao | `V1__core.sql:102-103` | State task chỉ có `TODO, IN_PROGRESS, PENDING_APPROVAL, COMPLETED, CANCELLED`. | Thiếu `DOING`, `SUBMITTED`, `REWORK` theo yêu cầu C. |
| 5 | Cao | `V1__core.sql:198-207` | Chỉ có 1 bảng `attachment`, không phân loại. | Không có phân biệt BRIEF/RESULT theo yêu cầu E. |
| 6 | Trung bình | `web/src/pages/Tasks.tsx` (KHÔNG XÁC MINH ĐƯỢC đầy đủ) | Không có trang "Việc có deadline" phân loại Quá hạn / Hôm nay / 7 ngày / Sau đó. | Vi phạm yêu cầu G. |
| 7 | Trung bình | KHÔNG XÁC MINH ĐƯỢC file | Không có bảng `note`/`personal_note` cho ghi chú cá nhân. | Vi phạm yêu cầu H. |
| 8 | Trung bình | `V1__core.sql:99` | `depth CHECK (depth >= 0 AND depth <= 1)`. | Giới hạn subtask 1 cấp (không liên quan trực tiếp đến A-J nhưng là ràng buộc). |
| 9 | Trung bình | `V4__ph3.sql:112`: `file.scan_enabled = false` | Malware scan chưa bật (stub). | Rủi ro bảo mật file tải lên. |
| 10 | Trung bình | `IdentityService.java:227` | `pushUnregisterStub()` — stub, chưa thực hiện unregister push token. | Push token cũ vẫn còn sau khi logout/revoke. |
| 11 | Thấp | `V1__core.sql:103` | State `BLOCKED` không có trong CHECK constraint (chỉ là `blocked_flag` cờ). | `BLOCKED` là cờ độc lập (theo spec), không phải state. |
| 12 | Thấp | `web/src/i18n/en.ts`, `web/src/i18n/vi.ts` | 191 khóa i18n. | Có thể thiếu một số khóa cho trang mới. |

---

## MỤC 14: NHỮNG ĐIỀU CHƯA XÁC MINH ĐƯỢC

Dưới đây là các điều cần người dùng cung cấp thêm hoặc cần chạy lệnh để xác minh:

1. **Nội dung file `deploy/.env`**: Có chứa secrets thực tế không? Cần kiểm tra xem `BOOTSTRAP_ADMIN_USERNAME` có được set không.
2. **Cài đặt Maven**: Maven chưa có trên máy dev, không thể chạy `mvn package` để verify backend compile.
3. **Docker daemon**: Đang tắt, không thể chạy `docker compose up` để verify dịch vụ.
4. **Flutter SDK**: `flutter --version` không trả output, chưa xác minh SDK hoàn chỉnh.
5. **Nội dung đầy đủ của một số file chưa đọc hết do output bị cắt**: `TaskService.java` (sau dòng 100), `TaskController.java` (sau dòng 80), `ProjectController.java` (sau dòng 80), `WorkspaceController.java` (sau dòng 80), `FileController.java` (sau dòng 120), `CalendarController.java` (sau dòng 120), `ReportController.java` (sau dòng 80), `AiController.java` (sau dòng 80), `web/src/pages/Dashboard.tsx`, `web/src/pages/TaskDetail.tsx`, `web/src/pages/Notifications.tsx`, `web/src/pages/Files.tsx`, `web/src/components/Layout.tsx` (sau dòng 60), `web/src/components/ui.tsx` (sau dòng 60), `web/src/i18n/index.ts`, `mobile/lib/*.dart` (nội dung chi tiết).
6. **Cấu hình CORS thực tế**: `CorsConfigTest.java` tồn tại nhưng chưa đọc cấu hình `application.yml` hoặc `WebMvcConfigurer`.
7. **Kết quả chạy test**: Không thể chạy test do thiếu Maven/Docker/Flutter.
8. **Enforce phân quyền ở task-level**: Không rõ policy kiểm tra task participation hay chỉ project participation (yêu cầu B: "Người ngoài task không thấy gì").
9. **Progress Calculator công thức**: `ProgressCalculator.java` chưa đọc được nội dung, không verify công thức progress.
10. **File `deploy/.env.example`**: Không đọc được nội dung (file tồn tại trong danh sách deploy).

---

**Đường dẫn file báo cáo:** `D:\task/AUDIT-REPORT.md`  
**Độ dài:** ~350 dòng (được tính chính xác sau khi ghi file)
