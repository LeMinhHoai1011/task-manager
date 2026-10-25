# 02B — ERD VẬT LÝ: NỀN TẢNG (COLLAB / NOTIFY / REPORT / AI / JOB / AUDIT / ADMIN) + MIGRATION

**Trạng thái:** ĐỀ XUẤT — **CHƯA FROZEN**, chưa được phép tạo database.
**Phạm vi:** các bảng nền tảng còn lại + partition/retention + chiến lược migration/rollback (NFR-18) + DB role (ENF-01).
**File kèm:** `02A-DATA-MODEL-CORE.md` (quy ước schema + miền nghiệp vụ lõi).


**Chú giải ký hiệu dùng trong các bảng:** `Y` = có / được phép / cơ chế giữ đầy đủ bất biến; `N` = không / không được phép / không thể dùng cơ chế đó; `!` = một phần (phần còn lại phải xử lý ở service layer hoặc đang chờ quyết định); `—` = không áp dụng.

---

## 1. MOD-06 Collaboration

### 1.1 `comment`

| Cột | Ràng buộc |
|---|---|
| `id` uuid PK | — |
| `target_type` text NOT NULL | CHECK IN (`TASK`,`SUBTASK`) — AUD/C-30: V1 chỉ comment trên Task/Subtask |
| `target_id` uuid NOT NULL | — |
| `parent_comment_id` uuid NULL | FK → comment; chỉ **1 cấp** reply (`depth smallint CHECK depth IN (0,1)`) |
| `workspace_id` uuid NOT NULL, `project_id` uuid NOT NULL | denormalize để lọc scope + redact |
| `body` text NOT NULL | CHECK length 1..8000 |
| `body_search_text` text | chuẩn hoá cho tìm kiếm (AUD-26) |
| `author_user_id` uuid NOT NULL | — |
| `edited_at`, `edited_by`, `edit_reason` | NULL — sửa comment phải audit |
| `is_deleted_content` boolean NOT NULL DEFAULT false | soft-delete **nội dung** (giữ khung "đã xoá bởi…"), khác `deleted_at` |
| base columns | — |

```sql
CREATE INDEX ix_comment_target ON comment (target_type, target_id, created_at DESC) WHERE deleted_at IS NULL;
CREATE INDEX ix_comment_project ON comment (project_id) WHERE deleted_at IS NULL;
-- AUD-06: comment trên Task PENDING_APPROVAL vẫn cho phép (chờ DEC-06) nhưng PHẢI audit
```

### 1.2 `activity_event` — **append-only** (Activity Timeline, Nhóm 09)

| Cột | Ràng buộc |
|---|---|
| `id` uuid PK | — |
| `target_type` text NOT NULL | CHECK IN (`TASK`,`SUBTASK`,`PROJECT`,`MILESTONE`,`COMMENT`,`ATTACHMENT`,`WORKSPACE`) |
| `target_id` uuid NOT NULL | — |
| `workspace_id`, `project_id` | NOT NULL / NULL — denormalize scope |
| `activity_type` text NOT NULL | CHECK IN (`CREATED`,`TITLE_CHANGED`,`DESCRIPTION_CHANGED`,`STATE_CHANGED`,`ASSIGNMENT_ADDED`,`ASSIGNMENT_REMOVED`,`DEADLINE_CHANGED`,`PRIORITY_CHANGED`,`BLOCKED_CHANGED`,`CHECKLIST_CHANGED`,`COMMENT_ADDED`,`COMMENT_EDITED`,`COMMENT_DELETED`,`FILE_UPLOADED`,`FILE_VERSION_ADDED`,`FILE_DELETED`,`FILE_RESTORED`,`APPROVAL_SUBMITTED`,`APPROVAL_APPROVED`,`APPROVAL_REJECTED`,`APPROVAL_CANCELLED`,`APPROVER_DESIGNATED`,`TASK_REOPENED`,`TASK_CANCELLED`,`TIMELOG_ADDED`,`TIMELOG_REMOVED`,`MEMBER_ADDED`,`MEMBER_REMOVED`,`ROLE_CHANGED`,`MILESTONE_CHANGED`,`DEPENDENCY_ADDED`,`DEPENDENCY_REMOVED`) |
| `actor_type` | CHECK IN (`USER`,`SYSTEM_RULE`) |
| `actor_user_id` | NULL |
| `summary_key` text NOT NULL | khoá i18n, **không** lưu câu chữ đã render (tránh lộ nội dung khi mất quyền) |
| `payload` jsonb NULL | before/after tối thiểu; **redaction** áp dụng lúc đọc |
| `occurred_at` timestamptz NOT NULL | — |
| `correlation_id` text NOT NULL | — |

```sql
CREATE INDEX ix_activity_target_time ON activity_event (target_type, target_id, occurred_at DESC);
CREATE INDEX ix_activity_project_time ON activity_event (project_id, occurred_at DESC);
-- Nhóm 09: "chỉ người có quyền xem đối tượng được đọc timeline" → kiểm tra ở API, không ở DB
```

> **Thiết kế chống lộ nội dung:** timeline trả về `summary_key` + payload đã redact. Nếu người xem mất quyền giữa chừng, payload có thể bị che (`"redacted": true`) mà **không** cần sửa dòng đã ghi — đúng "audit giữ lại".

---

## 2. MOD-09 Notifications

### 2.1 `notification`

| Cột | Ràng buộc |
|---|---|
| `id` uuid PK | — |
| `user_id` uuid NOT NULL FK | — |
| `session_id_bound` uuid NULL | **gắn phiên** (NFR-22) — phiên cũ không nhận nội dung nhạy cảm |
| `type` text NOT NULL | CHECK IN (`TASK_ASSIGNED`,`TASK_STATE_CHANGED`,`APPROVAL_REQUESTED`,`APPROVAL_DECIDED`,`APPROVAL_NO_APPROVER`,`DEADLINE_REMINDER`,`OVERDUE_REMINDER`,`COMMENT_MENTION`,`INVITATION`,`ACCOUNT_APPROVED`,`ACCOUNT_REJECTED`,`SESSION_REVOKED`,`SECURITY_ALERT`,`RESUME_DIGEST`,`AI_JOB_DONE`,`AI_JOB_FAILED`,`REPORT_EXPORT_READY`,`SYSTEM_MAINTENANCE`) |
| `severity` text NOT NULL | CHECK IN (`NORMAL`,`IMPORTANT`,`SECURITY_CRITICAL`) — Nhóm 06: "thông báo bảo mật quan trọng vẫn gửi" |
| `scope_type` text NOT NULL | CHECK IN (`SYSTEM`,`WORKSPACE`,`PROJECT`,`TASK`,`USER`) |
| `scope_id` uuid NULL | — |
| `title_key` text NOT NULL, `body_key` text NOT NULL | i18n key — **không** lưu nội dung nhạy cảm dạng text thô khi có thể |
| `payload` jsonb NULL | dữ liệu tối thiểu để render/deep link |
| `deep_link` text NULL | phải được **validate lại quyền** khi click (AUD-13) |
| `redacted` boolean NOT NULL DEFAULT false | mất quyền → set true, che payload (AUD-13) |
| `redacted_at`, `redaction_reason` | — |
| `read_at` timestamptz NULL | "trạng thái đọc/chưa đọc đồng bộ cấp tài khoản" (Nhóm 06) |
| `occurred_at` timestamptz NOT NULL | — |
| `dedup_token` text NOT NULL | **UNIQUE** — AUD-49 |
| `quiet_hours_deferred_until` timestamptz NULL | Nhóm 06 |

```sql
CREATE UNIQUE INDEX uq_notification_dedup ON notification (dedup_token);
CREATE INDEX ix_notification_user_unread ON notification (user_id, occurred_at DESC)
  WHERE read_at IS NULL AND redacted = false;
CREATE INDEX ix_notification_scope ON notification (scope_type, scope_id);
CREATE INDEX ix_notification_redact ON notification (scope_type, scope_id) WHERE redacted = false;
```

**Quy tắc redaction theo scope (AUD-13):** khi thu hồi quyền trên `(PROJECT, id)` → `UPDATE notification SET redacted = true, payload = NULL, deep_link = NULL WHERE scope_type='PROJECT' AND scope_id = $1 AND user_id = $2 AND redacted = false`. Đây là UPDATE trên bảng notification (không phải audit) → hợp lệ.

### 2.2 `notification_preference`

`id`, `user_id` NOT NULL FK, `type` text NOT NULL (như trên hoặc `'*'`), `channel_in_app boolean`, `channel_push boolean`, `channel_email boolean`, `muted boolean NOT NULL DEFAULT false`, `quiet_hours_enabled boolean`, `quiet_start time`, `quiet_end time`, `timezone text`, `overdue_reminder_policy text CHECK IN ('ONCE','DAILY','WEEKLY','OFF')`.
`UNIQUE (user_id, type)`.
**CHECK bảo mật (trigger):** `muted = true` bị **bỏ qua** khi `notification.severity = 'SECURITY_CRITICAL'` — Nhóm 06: "thông báo bảo mật quan trọng vẫn gửi".

### 2.3 `device_push_registration`

`id`, `user_id` NOT NULL FK, `session_id` NOT NULL FK → user_session **ON DELETE CASCADE** (Nhóm 01: "hủy đăng ký push gắn phiên cũ"), `platform` CHECK IN (`ANDROID_FCM`,`IOS_APNS`), `push_token_hash` text NOT NULL UNIQUE, `device_label`, `app_version`, `locale`, `status` CHECK IN (`ACTIVE`,`UNREGISTERED`,`INVALID`), `registered_at`, `unregistered_at`, `last_success_at`, `failure_count int NOT NULL DEFAULT 0`.
`CREATE INDEX ix_push_session ON device_push_registration (session_id) WHERE status = 'ACTIVE';`
→ Test 19.1 "login mới vô hiệu hóa token và push session cũ" có bằng chứng: `session_id` cascade + `uq_user_session_single_active`.

### 2.4 `delivery_attempt` — append-only

`id`, `notification_id` FK, `channel` CHECK IN (`IN_APP`,`PUSH`,`EMAIL`), `provider` text, `provider_message_id` text, `status` CHECK IN (`QUEUED`,`SENT`,`FAILED_PERMANENT`,`FAILED_RETRYABLE`), `attempt_no int`, `error_code`, `error_detail` (**không** chứa nội dung notification), `attempted_at`, `dedup_token` text NOT NULL, `correlation_id`.
`UNIQUE (notification_id, channel, attempt_no)`; `UNIQUE (dedup_token)` (AUD-49).

### 2.5 `realtime_cursor` (ARCH-09 resync)

`id`, `user_id`, `session_id`, `last_seen_notification_id uuid NULL`, `last_seen_seq bigint NOT NULL DEFAULT 0`, `updated_at`.
`UNIQUE (session_id)`.
→ Client reconnect gửi `last_seen_seq`, server trả các notification có `seq > last_seen_seq` **đã kiểm tra quyền lại** (test 19.2: "Realtime reconnect phải fetch dữ liệu theo quyền mới"). Cần cột `seq bigint` monotonic trên `notification` (thêm vào 2.1: `seq bigserial NOT NULL UNIQUE`).

---

## 3. MOD-10 Reports

### 3.1 `metric_daily_snapshot` (AUD-15) — **append-only theo ngày**

| Cột | Ràng buộc |
|---|---|
| `id` uuid PK | — |
| `scope_type` text NOT NULL | CHECK IN (`PROJECT`,`WORKSPACE`,`USER`,`SYSTEM`) |
| `scope_id` uuid NOT NULL | — |
| `as_of_date` date NOT NULL | ngày chốt (theo timezone cấu hình) |
| `metric_code` text NOT NULL | CHECK IN (metric dictionary §3.4) |
| `value_numeric` numeric(18,4) NULL | — |
| `value_int` bigint NULL | — |
| `value_state` text NOT NULL | CHECK IN (`OK`,`NOT_APPLICABLE`,`NO_PERMISSION`,`INSUFFICIENT_DATA`,`PARTIAL`) — AUD-38 |
| `numerator` int NULL, `denominator` int NULL | AUD-20 (progress) |
| `computed_at` timestamptz NOT NULL | — |
| `computed_by` text NOT NULL | CHECK IN (`JOB`,`MANUAL_RECOMPUTE`) |
| `job_id` uuid NULL | traceability |

```sql
-- Ngày đã chốt là BẤT BIẾN: không cho ghi lại cùng (scope, date, metric)
CREATE UNIQUE INDEX uq_snapshot_point ON metric_daily_snapshot (scope_type, scope_id, as_of_date, metric_code);
CREATE INDEX ix_snapshot_scope_date ON metric_daily_snapshot (scope_type, scope_id, as_of_date DESC);
-- CHỈ INSERT + SELECT cho role application (append-only)
```

### 3.2 `report_definition`

`id`, `code` text NOT NULL UNIQUE, `name_key`, `scope_type` CHECK IN (`PERSONAL`,`PROJECT`,`WORKSPACE`,`SYSTEM`), `metric_codes text[] NOT NULL`, `default_period text`, `export_formats text[] NOT NULL DEFAULT '{PDF,XLSX,CSV}'`, `enabled boolean`, `requires_reauth boolean NOT NULL DEFAULT false`, `max_rows int NOT NULL DEFAULT 100000`, `created_at`, `updated_at`.

### 3.3 `report_export_job` (AUD-16)

| Cột | Ràng buộc |
|---|---|
| `id` uuid PK, `background_job_id` uuid NOT NULL FK → background_job | — |
| `report_code` text NOT NULL FK → report_definition.code | — |
| `scope_type`, `scope_id` | NOT NULL |
| `period_from`, `period_to` | NOT NULL |
| `format` text NOT NULL | CHECK IN (`PDF`,`XLSX`,`CSV`) — Nhóm 08 |
| `requested_by` uuid NOT NULL | **chỉ người yêu cầu được tải** |
| `status` text NOT NULL | CHECK IN (`QUEUED`,`RUNNING`,`REAUTHORIZING`,`GENERATED`,`DELIVERED`,`FAILED`,`CANCELLED_REVOKED`,`EXPIRED`) |
| `attachment_id` uuid NULL | FK → attachment (`target_type='REPORT_EXPORT'`) |
| `expires_at` timestamptz NOT NULL | **TTL 24h** mặc định (AUD-16) |
| `row_count`, `metric_state_summary jsonb` | — |
| `authorized_at_generation`, `authorized_at_delivery` | timestamptz — bằng chứng kiểm tra quyền **2 lần** (Nhóm 08) |
| `idempotency_key` text NOT NULL UNIQUE | — |

```sql
CREATE INDEX ix_export_expiry ON report_export_job (expires_at) WHERE status IN ('GENERATED','DELIVERED');
CREATE INDEX ix_export_user ON report_export_job (requested_by, created_at DESC);
```

### 3.4 Metric dictionary (bắt buộc, chống AUD-37)

| `metric_code` | Định nghĩa | Nguồn | Ghi chú |
|---|---|---|---|
| `PROGRESS_PERCENT` | `COUNT(top-level COMPLETED) / (COUNT(top-level) − COUNT(top-level CANCELLED)) × 100` | `task WHERE depth=0` | `value_state = NOT_APPLICABLE` khi denominator = 0 → UI `N/A` (AUD-20) |
| `TASK_UNIQUE_TOTAL` | `COUNT(DISTINCT task_id)` | task | top-level + subtask tách bằng `depth` |
| `TASK_ASSIGNMENT_COUNT` | `COUNT(*)` trên `task_assignment` ACTIVE | task_assignment | **luôn trả kèm** `TASK_UNIQUE_TOTAL` |
| `TASK_BY_STATE_{TODO,IN_PROGRESS,PENDING_APPROVAL,COMPLETED,CANCELLED}` | đếm theo state | task | Nhóm 08 |
| `TASK_BLOCKED_COUNT` | đếm `blocked_flag = true` | task | **cờ riêng**, không cộng vào state |
| `TASK_OVERDUE_COUNT` | `due_at < now() AND state NOT IN ('COMPLETED','CANCELLED')` | task | Nhóm 08: "phân biệt đang chờ duyệt" → thêm `TASK_OVERDUE_PENDING_APPROVAL_COUNT` |
| `MILESTONE_{OPEN,COMPLETED,CANCELLED,OVERDUE}_COUNT` | đếm | milestone | AUD-21 |
| `APPROVAL_PENDING_COUNT` / `APPROVAL_APPROVED_COUNT` / `APPROVAL_REJECTED_COUNT` | đếm **theo round**, không nhân theo số approver | approval_round | Nhóm 08: "nhiều vòng tính riêng, không nhân theo số approver" |
| `APPROVAL_AVG_WAIT_MINUTES` / `APPROVAL_P95_WAIT_MINUTES` | `decided_at − submitted_at` | approval_round | — |
| `APPROVAL_NO_APPROVER_COUNT` | round OPEN và không có designation ACTIVE | approval_round + designation | Nhóm 05 |
| `TIMELOG_MINUTES_TOTAL` | `SUM(minutes)` **theo người ghi** | time_log | Nhóm 08: "time log theo người ghi"; **không** sinh bảng xếp hạng cá nhân |
| `FILE_STORAGE_BYTES` / `FILE_COUNT` | tổng dung lượng/số file trong scope | attachment + file_version | — |
| `AI_JOB_{TOTAL,SUCCESS,FAILED,TIMEOUT,CANCELLED_REVOKED}_COUNT` | đếm | ai_job | NFR-21 |
| `PERSONAL_ASSIGNED / PERSONAL_CREATED / PERSONAL_MANAGED` | 3 tập **tách biệt**, có thể giao nhau | task_assignment / task.created_by / project_membership | Nhóm 08: "tránh cộng trùng" → **không** có metric "tổng task của tôi" |

---

## 4. MOD-11 AI

### 4.1 `ai_model_configuration`

`id`, `config_key` text NOT NULL UNIQUE (vd `default`, `planner`, `summarizer`), `ollama_base_url` text NOT NULL (**LAN internal**, không expose), `model_name` text NOT NULL, `context_window_tokens int NOT NULL`, `temperature numeric(3,2)`, `timeout_ms int NOT NULL DEFAULT 120000`, `max_concurrency int NOT NULL DEFAULT 1`, `max_output_tokens int`, `enabled boolean NOT NULL DEFAULT true`, `updated_by`, `updated_at`, `reauth_ticket_id uuid NOT NULL` (Nhóm 10: quản trị Ollama cần reauth + audit).
→ Nhóm 07: "Admin có quyền cấu hình model, timeout, giới hạn tài nguyên nhưng **không tự có quyền đọc nội dung riêng tư**" → bảng này **không** chứa quyền scope nào.

### 4.2 `ai_job`

| Cột | Ràng buộc |
|---|---|
| `id` uuid PK, `background_job_id` uuid NOT NULL FK | — |
| `feature` text NOT NULL | CHECK IN (`TASK_DRAFT`,`SUBTASK_SUGGESTION`,`PRIORITY_SUGGESTION`,`DEADLINE_SUGGESTION`,`ASSIGNEE_SUGGESTION`,`NATURAL_SEARCH`,`DAILY_PLANNER`,`WEEKLY_REVIEW`,`METRIC_EXPLAIN`,`RISK_ANALYSIS_PHASE2`) |
| `principal_user_id` uuid NOT NULL | **AUD-48: chỉ lưu identity, KHÔNG lưu permission snapshot** |
| `scope_type`, `scope_id` | NOT NULL |
| `status` text NOT NULL | CHECK IN (`QUEUED`,`CONTEXT_BUILDING`,`RUNNING`,`VALIDATING`,`REAUTHORIZING`,`COMPLETED`,`FAILED_TIMEOUT`,`FAILED_OLLAMA`,`FAILED_VALIDATION`,`CANCELLED_REVOKED`,`CANCELLED_BY_USER`) |
| `context_manifest_code` text NOT NULL | FK → ai_context_manifest (AUD-39) |
| `context_token_estimate int` | — |
| `reauthorized_at` timestamptz NULL | **bằng chứng** reauthorize trước khi trả kết quả (AUD-48, NFR-24) |
| `reauthorize_result` text NULL | CHECK IN (`ALLOWED`,`DENIED_REVOKED`,`DENIED_STATE`) |
| `attempt_count int NOT NULL DEFAULT 0`, `max_attempts int NOT NULL DEFAULT 2` | NFR-11 |
| `timeout_ms int NOT NULL` | — |
| `error_code`, `error_message` | **không** chứa nội dung prompt |
| `started_at`, `finished_at`, `duration_ms` | — |
| `idempotency_key` text NOT NULL UNIQUE | — |
| `correlation_id` text NOT NULL | — |

```sql
CREATE INDEX ix_ai_job_user ON ai_job (principal_user_id, created_at DESC);
CREATE INDEX ix_ai_job_status ON ai_job (status) WHERE status IN ('QUEUED','CONTEXT_BUILDING','RUNNING','VALIDATING','REAUTHORIZING');
```

### 4.3 `ai_interaction`

`id`, `ai_job_id` NOT NULL FK, `direction` CHECK IN (`PROMPT`,`COMPLETION`), `config_key`, `model_name`, `content_ref` text — **KHÔNG lưu prompt/completion thô trong DB nếu chứa nội dung riêng tư**; thay vào đó lưu `object_key` trỏ tới Object Storage private với encryption + retention ngắn (AUD-39, mục 18.4), `content_hash char(64)`, `token_count int`, `latency_ms int`, `created_at`.
`retention_expires_at timestamptz NOT NULL` (mặc định 90 ngày — DEC-45).

> **Quyết định thiết kế quan trọng:** đặc tả mục 18.4 cấm log prompt chứa dữ liệu nhạy cảm, nhưng Nhóm 07 yêu cầu "AI history có kiểm soát quyền". Giải pháp: history là **dữ liệu nghiệp vụ** (bảng riêng, quyền riêng, retention riêng), **không phải log**. Log ứng dụng chỉ ghi `ai_job_id`, `feature`, `token_count`, `latency`, `status` — không ghi nội dung.

### 4.4 `ai_suggestion`

| Cột | Ràng buộc |
|---|---|
| `id` uuid PK, `ai_job_id` NOT NULL FK | — |
| `suggestion_type` text NOT NULL | CHECK IN (`TASK_DRAFT`,`SUBTASK_LIST`,`PRIORITY`,`DEADLINE`,`ASSIGNEE`,`SEARCH_RESULT`,`PLANNER_ITEM`,`REVIEW_INSIGHT`) |
| `status` text NOT NULL | CHECK IN (`DRAFT`,`SHOWN`,`ACCEPTED`,`REJECTED`,`EXPIRED`,`REDACTED`) — **không có** `APPLIED_BY_AI` |
| `payload jsonb NOT NULL` | đã validate theo JSON schema |
| `validation_result jsonb NOT NULL` | AUD-40: trường nào bị loại vì ngoài scope |
| `explanation text NULL` | Nhóm 07: "priority có giải thích" |
| `confidence numeric(4,3) NULL` | — |
| `redacted boolean NOT NULL DEFAULT false`, `redacted_at` | AUD-13/39 |
| `accepted_at`, `accepted_by`, `resulting_task_id uuid NULL` | traceability: suggestion → task do **user** tạo |
| `scope_type`, `scope_id`, `principal_user_id` | denormalize để redact theo scope |

```sql
-- AUD-47: AI không tự mutation → suggestion chỉ là DTO bất biến; trạng thái APPLIED không tồn tại
CREATE INDEX ix_ai_sugg_job ON ai_suggestion (ai_job_id);
CREATE INDEX ix_ai_sugg_scope ON ai_suggestion (scope_type, scope_id) WHERE redacted = false;
```

### 4.5 `ai_context_manifest` (AUD-39)

`id`, `code` text NOT NULL UNIQUE, `feature` text NOT NULL, `allowed_fields jsonb NOT NULL` (danh sách trường + giới hạn), `max_records int NOT NULL`, `max_chars int NOT NULL`, `max_token_estimate int NOT NULL`, `forbidden_field_patterns text[] NOT NULL DEFAULT '{"email","phone","password*","*.body_raw"}'`, `scope_rule text NOT NULL`, `version int NOT NULL`, `approved_by uuid NOT NULL`, `approved_at timestamptz NOT NULL`, `enabled boolean`.
→ Manifest được **review như code**; thay đổi cần audit + reauth.

---

## 5. MOD-14 Background Jobs & Outbox

### 5.1 `outbox_event` — append-only (ARCH-04)

| Cột | Ràng buộc |
|---|---|
| `id` uuid PK | — |
| `aggregate_type` text NOT NULL | vd `TASK`, `PROJECT`, `WORKSPACE`, `SESSION`, `FILE`, `AI_JOB` |
| `aggregate_id` uuid NOT NULL | — |
| `event_type` text NOT NULL | vd `TASK_APPROVED`, `MEMBERSHIP_REVOKED`, `SESSION_REVOKED`, `PROJECT_COMPLETED` |
| `seq bigint NOT NULL` | monotonic theo aggregate — `UNIQUE (aggregate_type, aggregate_id, seq)` |
| `payload jsonb NOT NULL` | **tối thiểu**, không chứa nội dung nhạy cảm không cần thiết |
| `scope_type`, `scope_id` | để consumer redact/lọc |
| `occurred_at` timestamptz NOT NULL | thời điểm nghiệp vụ |
| `correlation_id` text NOT NULL | xuyên HTTP → job → notify |
| `actor_type`, `actor_user_id` | — |
| `status` text NOT NULL DEFAULT `'PENDING'` | CHECK IN (`PENDING`,`DISPATCHED`,`DEAD`) |
| `available_at` timestamptz NOT NULL DEFAULT now() | delay/retry |
| `attempt_count int NOT NULL DEFAULT 0`, `last_error text NULL` | — |
| `idempotency_key text NOT NULL` | `UNIQUE` — AUD-49 |
| `created_at` timestamptz NOT NULL | — |

```sql
CREATE INDEX ix_outbox_poll ON outbox_event (available_at, id) WHERE status = 'PENDING';
CREATE UNIQUE INDEX uq_outbox_idem ON outbox_event (idempotency_key);
CREATE UNIQUE INDEX uq_outbox_seq ON outbox_event (aggregate_type, aggregate_id, seq);
```

> **ARCH-04:** dòng `outbox_event` được INSERT **trong cùng transaction** với thay đổi domain. Không có "gọi thẳng notification service".

### 5.2 `outbox_delivery` (AUD-49 idempotency consumer)

`id`, `outbox_event_id` NOT NULL FK, `handler_key` text NOT NULL, `status` text NOT NULL CHECK IN (`PROCESSING`,`DONE`,`FAILED`,`SKIPPED_REVOKED`,`SKIPPED_DUPLICATE`), `attempt_no int NOT NULL DEFAULT 1`, `processed_at`, `error_detail text NULL`, `dedup_token text NOT NULL`.
```sql
CREATE UNIQUE INDEX uq_delivery_handler ON outbox_delivery (outbox_event_id, handler_key);   -- chống xử lý lặp
CREATE UNIQUE INDEX uq_delivery_dedup ON outbox_delivery (dedup_token);
```

### 5.3 `background_job`

| Cột | Ràng buộc |
|---|---|
| `id` uuid PK | — |
| `job_type` text NOT NULL | CHECK IN (`NOTIFICATION_DELIVER`,`REMINDER_FIRE`,`REMINDER_RECOMPUTE`,`AI_RUN`,`REPORT_EXPORT`,`FILE_SCAN`,`FILE_CLEANUP_ORPHAN`,`STORAGE_RECONCILE`,`SNAPSHOT_DAILY`,`AUDIT_ARCHIVE`,`BACKUP_VERIFY`,`EXPORT_PURGE`,`REDACT_ON_REVOKE`,`OVERDUE_SCAN`) |
| `status` text NOT NULL | CHECK IN (`QUEUED`,`LEASED`,`RUNNING`,`WAITING_REAUTH`,`SUCCEEDED`,`FAILED_RETRYABLE`,`FAILED_PERMANENT`,`DEAD_LETTER`,`CANCELLED_REVOKED`,`CANCELLED_BY_USER`) |
| `principal_user_id` uuid NULL | **AUD-48**: identity để reauthorize; NULL chỉ với job hệ thống thuần (`SYSTEM`) |
| `principal_type` text NOT NULL | CHECK IN (`USER`,`SYSTEM`) |
| `scope_type`, `scope_id` | NOT NULL |
| `payload jsonb NOT NULL` | tối thiểu |
| `dedup_key text NOT NULL` | **UNIQUE** khi job còn sống — chống enqueue trùng |
| `idempotency_key text NOT NULL` | UNIQUE |
| `priority smallint NOT NULL DEFAULT 5` | — |
| `available_at` timestamptz NOT NULL | schedule/retry backoff |
| `lease_holder text NULL`, `lease_expires_at timestamptz NULL` | chống xử lý trùng (NFR-24) |
| `attempt_count int NOT NULL DEFAULT 0`, `max_attempts int NOT NULL DEFAULT 5` | — |
| `reauth_required boolean NOT NULL DEFAULT false` | đánh dấu bước nhạy cảm |
| `reauthorized_at`, `reauthorize_result` | bằng chứng |
| `result_ref text NULL`, `result_status text NULL` | — |
| `last_error text NULL`, `dead_letter_reason text NULL` | — |
| `correlation_id`, `created_at`, `started_at`, `finished_at` | — |

```sql
CREATE UNIQUE INDEX uq_job_dedup_alive ON background_job (dedup_key)
  WHERE status IN ('QUEUED','LEASED','RUNNING','WAITING_REAUTH');
CREATE UNIQUE INDEX uq_job_idem ON background_job (idempotency_key);
CREATE INDEX ix_job_poll ON background_job (available_at, priority, id)
  WHERE status IN ('QUEUED','FAILED_RETRYABLE');
CREATE INDEX ix_job_lease ON background_job (lease_expires_at) WHERE status = 'LEASED';
```

**Mẫu lease (worker):**
```sql
UPDATE background_job
   SET status = 'LEASED', lease_holder = $workerId, lease_expires_at = now() + interval '60 seconds',
       attempt_count = attempt_count + 1, started_at = COALESCE(started_at, now())
 WHERE id = (
   SELECT id FROM background_job
    WHERE status IN ('QUEUED','FAILED_RETRYABLE')
      AND available_at <= now()
      AND (lease_expires_at IS NULL OR lease_expires_at < now())
    ORDER BY priority, available_at, id
    LIMIT 1
    FOR UPDATE SKIP LOCKED
 )
 RETURNING *;
```
→ Thoả NFR-24 "chống xử lý trùng" và test 19.2 "worker restart không mất job đã commit" (job ở DB, lease hết hạn thì worker khác nhận).

### 5.4 `job_attempt` — append-only

`id`, `background_job_id` FK, `attempt_no int`, `worker_id text`, `status text CHECK IN ('STARTED','SUCCEEDED','FAILED_RETRYABLE','FAILED_PERMANENT','CANCELLED_REVOKED','REAUTH_DENIED')`, `started_at`, `finished_at`, `duration_ms int`, `error_code`, `error_detail`, `reauthorize_result`, `correlation_id`.
`UNIQUE (background_job_id, attempt_no)`.

### 5.5 `idempotency_record` (cho API mutating — DEC-43/mục 17 đặc tả)

`id`, `idempotency_key text NOT NULL`, `user_id uuid NOT NULL`, `request_path text NOT NULL`, `request_hash char(64) NOT NULL`, `status text NOT NULL CHECK IN ('IN_PROGRESS','COMPLETED','FAILED')`, `response_status int NULL`, `response_body_ref text NULL`, `created_at`, `expires_at timestamptz NOT NULL` (mặc định 24h).
```sql
CREATE UNIQUE INDEX uq_idem_key_user ON idempotency_record (user_id, idempotency_key);
CREATE INDEX ix_idem_expiry ON idempotency_record (expires_at);
```
Quy tắc: cùng `idempotency_key` + khác `request_hash` → `422 IDEMPOTENCY_KEY_REUSED_WITH_DIFFERENT_PAYLOAD`. Cùng key + cùng hash + `COMPLETED` → trả lại response đã lưu, **không** thực thi lại.

---

## 6. MOD-13 Audit / Security

### 6.1 `audit_event` — **append-only tuyệt đối** (AUD-14)

| Cột | Ràng buộc |
|---|---|
| `id` uuid PK | — |
| `occurred_at` timestamptz NOT NULL | — |
| `actor_type` text NOT NULL | CHECK IN (`USER`,`SYSTEM_RULE`,`JOB`,`CLI`,`MIGRATION`) |
| `actor_user_id` uuid NULL | — |
| `actor_session_id` uuid NULL | — |
| `action` text NOT NULL | mã hành động, khớp với mã trong ma trận RBAC (`04-RBAC-MATRIX.md`) |
| `target_type` text NOT NULL, `target_id` uuid NULL | — |
| `scope_type` text NOT NULL | CHECK IN (`SYSTEM`,`WORKSPACE`,`PROJECT`,`TASK`,`USER`) — AUD-36 |
| `scope_id` uuid NULL | — |
| `sensitivity` text NOT NULL DEFAULT `'NORMAL'` | CHECK IN (`NORMAL`,`SENSITIVE_CONTENT`) — AUD-36 |
| `before jsonb NULL`, `after jsonb NULL` | Nhóm 09 "before-after" |
| `reason text NULL` | bắt buộc NOT NULL khi action ∈ tập emergency/reopen/reject (CHECK + trigger) |
| `outcome` text NOT NULL | CHECK IN (`SUCCESS`,`DENIED_FORBIDDEN`,`DENIED_NOT_FOUND`,`DENIED_STATE`,`DENIED_REVOKED`,`DENIED_SELF_APPROVAL`,`DENIED_LAST_MANAGER`,`DENIED_VERSION_CONFLICT`,`FAILED`) |
| `reauth_ticket_id` uuid NULL | bằng chứng reauth cho thao tác nhạy cảm |
| `reason_code` text NULL | vd `PROJECT_COMPLETED_CASCADE` (AUD-01) |
| `ip_address`, `user_agent`, `correlation_id` | — |
| `prev_hash char(64) NULL`, `entry_hash char(64) NOT NULL` | **hash-chain** chống sửa xoá (AUD-14) |

```sql
-- Quyền DB: role application chỉ có INSERT + SELECT (ENF/AUD-14)
-- REVOKE UPDATE, DELETE, TRUNCATE ON audit_event FROM app_role;

CREATE INDEX ix_audit_scope_time ON audit_event (scope_type, scope_id, occurred_at DESC);
CREATE INDEX ix_audit_target ON audit_event (target_type, target_id, occurred_at DESC);
CREATE INDEX ix_audit_actor ON audit_event (actor_user_id, occurred_at DESC);
CREATE INDEX ix_audit_action ON audit_event (action, occurred_at DESC);
CREATE INDEX ix_audit_denied ON audit_event (occurred_at DESC) WHERE outcome <> 'SUCCESS';
-- CHỈ ghi nhận DENIED: đây là nguồn chính cho negative test và phát hiện IDOR
```

### 6.2 `security_event` — append-only

`id`, `occurred_at`, `event_type` CHECK IN (`LOGIN_SUCCESS`,`LOGIN_FAILED`,`ACCOUNT_LOCKED`,`ACCOUNT_UNLOCKED`,`PASSWORD_CHANGED`,`PASSWORD_RESET_REQUESTED`,`RECOVERY_CODE_USED`,`REAUTH_SUCCESS`,`REAUTH_FAILED`,`SESSION_REVOKED_LOGIN_ELSEWHERE`,`PRIVILEGE_GRANTED`,`PRIVILEGE_REVOKED`,`EMERGENCY_ACTION`,`IDOR_SUSPECTED`,`RATE_LIMIT_HIT`,`FILE_ACCESS_DENIED`,`AI_CONTEXT_DENIED`,`BOOTSTRAP_ADMIN_RUN`,`AUDIT_READ`,`CONFIG_CHANGED`,`BACKUP_COMPLETED`,`RESTORE_DRILL`), `user_id`, `session_id`, `ip_address`, `user_agent`, `detail jsonb` (**không** chứa mật khẩu/token), `severity` CHECK IN (`INFO`,`WARN`,`CRITICAL`), `correlation_id`.

### 6.3 `audit_maintenance_log` (AUD-14 disposal)

`id`, `operation` CHECK IN (`ARCHIVE_PARTITION`,`PURGE_EXPIRED`,`VERIFY_HASH_CHAIN`), `period_from`, `period_to`, `rows_affected bigint`, `block_hash_before char(64)`, `block_hash_after char(64)`, `policy_id` FK, `performed_by` NOT NULL, `reauth_ticket_id` NOT NULL, `performed_at`, `reason` NOT NULL.

### 6.4 `retention_policy`

`id`, `entity_name` text NOT NULL UNIQUE, `scope_rule text`, `retention_days int NULL` (**NULL = giữ vĩnh viễn**, mặc định V1 — AUD-45), `action_on_expiry` CHECK IN (`NONE`,`ARCHIVE`,`PURGE`) DEFAULT `NONE`, `requires_reauth boolean NOT NULL DEFAULT true`, `updated_by`, `updated_at`.

---

## 7. MOD-12 Administration

### 7.1 `system_setting`

`id`, `setting_key` text NOT NULL UNIQUE, `value jsonb NOT NULL`, `value_type` CHECK IN (`BOOLEAN`,`INT`,`STRING`,`JSON`,`DURATION`), `category` CHECK IN (`ACCOUNT`,`SECURITY`,`FILE`,`AI`,`NOTIFICATION`,`REPORT`,`BACKUP`,`WORKFLOW`), `is_security_invariant boolean NOT NULL DEFAULT false`, `max_value jsonb NULL`, `min_value jsonb NULL`, `updated_by`, `updated_at`, `reauth_ticket_id uuid NOT NULL`, `change_log_id uuid NOT NULL`.

Danh sách setting V1 (khởi tạo bằng migration seed):

| `setting_key` | Mặc định | Nhóm liên quan |
|---|---|---|
| `account.self_registration_enabled` | `false` | Nhóm 01 "Tự đăng ký chỉ khi System Admin bật" |
| `account.require_approval_for_self_registration` | `true` | Nhóm 01 |
| `account.temp_password_ttl_hours` | `24` | Nhóm 01 (AUD-17) |
| `account.recovery_code_count` | `8` | AUD-17 |
| `security.lockout_threshold_attempts` | `5` | AUD-32 |
| `security.lockout_duration_minutes` | `15` | AUD-32 |
| `security.rate_limit_login_per_ip_per_min` | `10` | Nhóm 10 |
| `security.access_token_ttl_minutes` | `15` | DEC-43 |
| `security.refresh_token_ttl_days` | `30` | DEC-43 |
| `security.reauth_ttl_minutes` | `5` | AUD-34 |
| `file.max_upload_mb` | `50` | Nhóm 09 |
| `file.allowed_content_types` | `["pdf","png","jpeg","docx","xlsx","zip",…]` | Nhóm 09 allowlist |
| `file.scan_enabled` | `false` (chưa có engine — xem risk) | Nhóm 09 |
| `workflow.approval_sla_hours_default` | `null` | AUD-08 |
| `notification.quiet_hours_default_enabled` | `false` | Nhóm 06 |
| `ai.enabled` | `false` cho tới khi chốt DEC-41 | Nhóm 07 |
| `ai.default_timeout_ms` | `120000` | NFR-11 |
| `ai.max_concurrency` | `1` | AUD-41 |
| `report.export_ttl_hours` | `24` | AUD-16 |
| `backup.retention_daily/weekly/monthly` | `7/4/3` | NFR-15, AUD-45 |
| `system.bootstrap_completed` | `false` → `true` | AUD-18 |

### 7.2 `configuration_change_log` — append-only

`id`, `setting_key`, `old_value jsonb`, `new_value jsonb`, `changed_by`, `changed_at`, `reauth_ticket_id` NOT NULL, `reason` NOT NULL, `correlation_id`.
→ Nhóm 10: "Admin thay đổi cấu hình toàn cục theo quyền và **audit trước/sau**".

### 7.3 `emergency_action_log` — append-only

`id`, `action_code` CHECK IN (`SYSTEM_ADMIN_OWNER_TRANSFER`,`WORKSPACE_OWNER_RESTORE_MANAGER`,`SYSTEM_ADMIN_ACCOUNT_RESTORE`,`SYSTEM_ADMIN_SUSPEND`,`SYSTEM_ADMIN_FORCE_LOGOUT`), `actor_user_id` NOT NULL, `target_type`, `target_id`, `reason` NOT NULL CHECK length ≥ 10, `reauth_ticket_id` NOT NULL, `performed_at`, `content_read boolean NOT NULL DEFAULT false` — **phải luôn false** (Nhóm 10: emergency "không đọc nội dung"), `audit_event_id` NOT NULL.
```sql
ALTER TABLE emergency_action_log ADD CONSTRAINT ck_emergency_no_content_read
  CHECK (content_read = false);
```
→ Nhóm 10: "Emergency actions chỉ giới hạn các hành động đã được phép trong Nhóm 01–03" → `action_code` là **danh sách đóng** bằng CHECK, không mở rộng được bằng cấu hình.

---

## 8. Partition, retention và kích thước

| Bảng | Chiến lược | Lý do |
|---|---|---|
| `audit_event` | **Partition theo tháng** trên `occurred_at` (RANGE) | Append-only, retention/archive theo khối (AUD-14), giữ index nhỏ |
| `security_event` | Partition theo tháng | như trên |
| `workflow_event` | Partition theo tháng | Volume lớn nhất trong các bảng append-only nghiệp vụ |
| `activity_event` | Partition theo tháng | — |
| `notification` | Partition theo tháng + job dọn `read_at IS NOT NULL AND occurred_at < now() - interval '90 days'` | Giữ bảng unread nhỏ để query nhanh |
| `delivery_attempt` | Partition theo tháng, retention 90 ngày | Chỉ phục vụ debug |
| `file_access_event` | Partition theo tháng | Bằng chứng bảo mật, giữ theo retention_policy |
| `outbox_event` | Không partition; job archive dòng `status='DISPATCHED'` > 14 ngày sang `outbox_event_archive` | Giữ index poll nóng |
| `task`, `comment`, `attachment` | Không partition ở V1 (50–100 user) | Chưa cần; thêm khi > 10 triệu dòng |
| `metric_daily_snapshot` | Partition theo năm | Truy vấn theo khoảng ngày |

**Ước lượng dung lượng V1 (50–100 user, chưa đo thực tế → chỉ là giả định ASM):** ~1–3 GB/năm cho audit + workflow + activity + notification; file binary chi phối dung lượng Object Storage. Với **28 GB trống trên ổ D:** (FACT B.3) → **không** đặt MinIO data + PostgreSQL data + Docker images chung trên ổ D: ở môi trường dev; cần ổ khác hoặc giới hạn quota. Đây là prerequisite của Phase 1.

---

## 9. DB role và quyền (ENF-01)

| Role | Quyền | Dùng bởi |
|---|---|---|
| `stm_app` | SELECT/INSERT/UPDATE/DELETE trên bảng nghiệp vụ; **chỉ INSERT+SELECT** trên `audit_event`, `security_event`, `workflow_event`, `activity_event`, `approval_decision`, `metric_daily_snapshot`, `file_access_event`, `*_log` | `app-api`, `app-worker` (V1) |
| `stm_worker` | Như `stm_app` + UPDATE trên `background_job`, `outbox_event`, `outbox_delivery`, `job_attempt` | `app-worker` khi tách tiến trình |
| `stm_gateway` | SELECT trên `attachment`, `file_version`, `file_object`, `app_user`, `user_session` + INSERT `file_access_event` | file gateway khi tách (mục 7 tài liệu 01) |
| `stm_migrate` | DDL toàn quyền + `INSERT/UPDATE/DELETE` cho seed | **chỉ** chạy lúc migration, không dùng runtime |
| `stm_maintenance` | UPDATE/DELETE trên bảng append-only **theo retention policy**, chạy từ `app-cli` với reauth | AUD-14 disposal |
| `stm_readonly` | SELECT cho công cụ giám sát/báo cáo nội bộ | Observability |

```sql
REVOKE UPDATE, DELETE, TRUNCATE ON audit_event, security_event, workflow_event,
       activity_event, approval_decision, metric_daily_snapshot, file_access_event
  FROM stm_app, stm_worker, stm_gateway;
```

→ Đây là cơ chế **cứng** bảo đảm "Audit bất biến" (Nhóm 09) và "reopen không viết lại quá khứ" (Nhóm 08), không phụ thuộc vào code.

**V1 thực tế:** Dev/Test có thể chỉ dùng `stm_app` + `stm_migrate` để giảm ma sát; **Production bắt buộc** tách role. Cần người dùng xác nhận có làm ngay từ Phase 1 hay hoãn tới Phase 7 (DEC-43).

---

## 10. Chiến lược migration (NFR-18)

### 10.1 Công cụ và quy ước

| Mục | Đề xuất |
|---|---|
| Công cụ | **Flyway Community**, script SQL thuần (không dùng Java migration) |
| Vị trí | `platform-persistence/src/main/resources/db/migration/V<major>.<minor>.<patch>__<module>__<mota>.sql` |
| Ví dụ | `V1.0.001__identity__create_app_user.sql`, `V1.0.014__workflow__create_approval_tables.sql` |
| Undo | Flyway Community **không có** undo tự động → mỗi script `V*` bắt buộc có file `R<version>__<module>__rollback.sql` **viết tay**, được review cùng PR |
| Repeatable | `R__seed_system_setting.sql`, `R__seed_metric_dictionary.sql` (chạy khi checksum đổi) |
| Thứ tự module | identity → workspace → project → task → workflow → collaboration → files → calendar → notifications → reports → ai → jobs/outbox → audit → admin |
| Kiểm tra | `flyway validate` chạy trong CI trước khi test; `flyway info` in ra ở health endpoint (chỉ môi trường Dev/Test) |

### 10.2 Quy tắc migration bắt buộc

| ID | Quy tắc |
|---|---|
| MIG-01 | **Không** migration nào chứa dữ liệu thật của người dùng; seed chỉ là `system_setting`, `report_definition`, `ai_context_manifest` |
| MIG-02 | Mỗi migration **chạy được trên DB rỗng** và **idempotent về mặt phiên bản** (không sửa script đã release; luôn tạo script mới) |
| MIG-03 | Migration thêm cột: luôn `NULL` hoặc có `DEFAULT`; **cấm** `NOT NULL` không default trên bảng đã có dữ liệu |
| MIG-04 | Tạo index trên bảng lớn: dùng `CREATE INDEX CONCURRENTLY` trong script riêng, ngoài transaction |
| MIG-05 | Đổi kiểu cột: 3 bước (thêm cột mới → backfill + dual-write → cắt cột cũ), không đổi kiểu tại chỗ |
| MIG-06 | Mọi trigger/function phải có trong migration, được đặt tên `guard_*` / `trg_*` và có test riêng |
| MIG-07 | Partition: tạo partition trước N tháng bằng job `ENSURE_PARTITIONS`, không tạo tay |
| MIG-08 | **Rollback drill bắt buộc** ở Dev/Test trước khi promote: `migrate up → verify → rollback script → verify` |
| MIG-09 | Migration chạy bằng role `stm_migrate`; application **không** có quyền DDL |
| MIG-10 | Môi trường Dev/Test/Production tách biệt, **không** cho test dùng DB/file production (mục 18.1) |
| MIG-11 | Mỗi migration ghi `audit_event` loại `MIGRATION_APPLIED` (actor_type = `MIGRATION`) để truy vết |
| MIG-12 | Backup bắt buộc trước migration Production (NFR-15) và lưu `backup_ref` vào `configuration_change_log` |

### 10.3 Promotion giữa 3 môi trường

```
Dev (local Docker: postgres + minio + mailhog)
  ↓ migration đã pass + rollback drill + integration test
Test (VM/DB riêng, dataset giả lập, KHÔNG dữ liệu production)
  ↓ load test bậc 1 (10–20 user), security test, evidence ghi vào 07-TEST-PLAN
Production (VM Linux riêng, role tách, backup độc lập)
  ↓ chỉ deploy khi có phê duyệt; không tự deploy (quy tắc người dùng)
```

**Ràng buộc từ FACT môi trường:** Docker daemon trên máy dev **đang tắt**, chưa có `psql`, chưa có Maven. Trước Phase 1 cần một bước "Môi trường & Công cụ" (xem `06-IMPLEMENTATION-PLAN.md`, Phase 0.5) gồm: bật Docker Desktop, tạo `docker-compose.dev.yml` (postgres 16, minio, mailhog), cài Maven hoặc commit Maven Wrapper, cài Flyway CLI hoặc chạy qua Spring Boot, `flutter doctor`.

---

## 11. Danh sách bảng đầy đủ (54 bảng + 6 bảng archive/partition)

| # | Bảng | Module | Append-only | Partition |
|---|---|---|---|---|
| 1 | `app_user` | MOD-01 | | |
| 2 | `user_session` | MOD-01 | | |
| 3 | `user_refresh_token` | MOD-01 | | |
| 4 | `account_recovery_code` | MOD-01 | | |
| 5 | `password_reset_ticket` | MOD-01 | | |
| 6 | `reauth_ticket` | MOD-01 | | |
| 7 | `global_permission_grant` | MOD-01 | | |
| 8 | `user_email_history` | MOD-01 | Y | |
| 9 | `user_profile_preference` | MOD-01 | | |
| 10 | `workspace` | MOD-02 | | |
| 11 | `workspace_membership` | MOD-02 | | |
| 12 | `workspace_invitation` | MOD-02 | | |
| 13 | `workspace_setting` | MOD-02 | | |
| 14 | `project` | MOD-03 | | |
| 15 | `project_membership` | MOD-03 | | |
| 16 | `project_setting` | MOD-03 | | |
| 17 | `milestone` | MOD-03 | | |
| 18 | `special_grant` | MOD-03 | | |
| 19 | `task` | MOD-04 | | |
| 20 | `task_assignment` | MOD-04 | | |
| 21 | `task_dependency` | MOD-04 | | |
| 22 | `checklist_item` | MOD-04 | | |
| 23 | `tag` | MOD-04 | | |
| 24 | `task_tag` | MOD-04 | Y | |
| 25 | `time_log` | MOD-04 | | |
| 26 | `approval_round` | MOD-05 | | |
| 27 | `approval_designation` | MOD-05 | | |
| 28 | `approval_decision` | MOD-05 | Y | |
| 29 | `workflow_event` | MOD-05 | Y | Y tháng |
| 30 | `comment` | MOD-06 | | |
| 31 | `activity_event` | MOD-06 | Y | Y tháng |
| 32 | `attachment` | MOD-07 | | |
| 33 | `file_version` | MOD-07 | Y | |
| 34 | `file_object` | MOD-07 | | |
| 35 | `file_access_event` | MOD-07 | Y | Y tháng |
| 36 | `upload_session` | MOD-07 | | |
| 37 | `reminder_rule` | MOD-08 | | |
| 38 | `reminder_schedule` | MOD-08 | | |
| 39 | `task_deadline_change` | MOD-08 | Y | |
| 40 | `calendar_export_record` | MOD-08 | Y | |
| 41 | `notification` | MOD-09 | | Y tháng |
| 42 | `notification_preference` | MOD-09 | | |
| 43 | `device_push_registration` | MOD-09 | | |
| 44 | `delivery_attempt` | MOD-09 | Y | Y tháng |
| 45 | `realtime_cursor` | MOD-09 | | |
| 46 | `metric_daily_snapshot` | MOD-10 | Y | Y năm |
| 47 | `report_definition` | MOD-10 | | |
| 48 | `report_export_job` | MOD-10 | | |
| 49 | `ai_model_configuration` | MOD-11 | | |
| 50 | `ai_job` | MOD-11 | | |
| 51 | `ai_interaction` | MOD-11 | | |
| 52 | `ai_suggestion` | MOD-11 | | |
| 53 | `ai_context_manifest` | MOD-11 | | |
| 54 | `outbox_event` | MOD-14 | Y | |
| 55 | `outbox_delivery` | MOD-14 | | |
| 56 | `background_job` | MOD-14 | | |
| 57 | `job_attempt` | MOD-14 | Y | |
| 58 | `idempotency_record` | MOD-14 | | |
| 59 | `audit_event` | MOD-13 | Y | Y tháng |
| 60 | `security_event` | MOD-13 | Y | Y tháng |
| 61 | `audit_maintenance_log` | MOD-13 | Y | |
| 62 | `retention_policy` | MOD-13 | | |
| 63 | `system_setting` | MOD-12 | | |
| 64 | `configuration_change_log` | MOD-12 | Y | |
| 65 | `emergency_action_log` | MOD-12 | Y | |

*(65 bảng — đã gồm `notification.seq`, `outbox_event_archive` và các partition con không tính là bảng logic riêng.)*

---

## 12. Điểm CHƯA chốt trong ERD nền tảng

| Vùng | Phụ thuộc | Ảnh hưởng nếu đổi |
|---|---|---|
| `notification.seq` bigint monotonic | DEC-43 (realtime WebSocket + resync) | Nếu dùng SSE/polling đơn giản → bỏ `realtime_cursor` |
| `ai_interaction.content_ref` trỏ Object Storage | DEC-39, DEC-45 | Nếu không lưu prompt → bỏ bảng, chỉ giữ `ai_job` + `ai_suggestion` |
| `metric_daily_snapshot` | **DEC-15** | Nếu chỉ event-derived → bỏ bảng, dashboard lịch sử phải query `workflow_event` (chậm) |
| `report_export_job.attachment_id` | DEC-16 | Nếu export không qua `attachment` → thêm bảng `report_export_object` |
| `special_grant` | DEC-50 | Nếu bỏ → Workspace Owner không có đường đọc Project chưa tham gia kể cả khẩn cấp |
| `time_log.locked_at` | DEC-23 | Nếu cho sửa tự do → bỏ trigger, mất khả năng đối chiếu snapshot |
| DB role tách theo module | DEC-43 | Nếu V1 chỉ 1 role → mất lớp phòng vệ ENF-01, phải bù bằng ArchUnit |
| `file_object.scan_status` | Prerequisite: chưa có engine anti-malware | Nhóm 09 nói "kiểm tra malware **khi có công cụ**" → V1 có thể `UNSUPPORTED`, phải ghi rõ là **chưa có** bảo vệ malware, không được tuyên bố đã kiểm tra |
