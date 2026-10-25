# 03 — API CONTRACT: QUY ƯỚC, ERROR MODEL, ENDPOINT, DTO, REALTIME

**Trạng thái: ĐỀ XUẤT — CHƯA FROZEN.**
Tài liệu này **chưa phải quyết định đã duyệt** và **không** tự trở thành baseline. Đặc tả mục 17 ("API — Nhóm chức năng đề xuất, CHƯA FROZEN contract chi tiết") chỉ là **gợi ý phân nhóm** đường dẫn; toàn bộ bảng endpoint `EP-nnn` dưới đây là **đề xuất endpoint chính thức chờ phê duyệt**. Chưa viết code, chưa tạo database, chưa sinh OpenAPI, chưa kiểm thử — do đó **không có** bất kỳ tuyên bố PASS/đạt nào trong tài liệu này.

**Bám baseline:** Nhóm 01–11 (FROZEN), ARCH-01..ARCH-10 (FROZEN), NFR-01..NFR-24 (FROZEN).

**Nguồn tên gọi bắt buộc dùng lại (không được đặt tên khác):** `00-AUDIT-SPEC.md` (`AUD-nn`, `DEC-nn`), `01-MODULES-BOUNDARIES.md` (`MOD-01..MOD-15`, `DEP-nn`), `02A-DATA-MODEL-CORE.md` + `02B-DATA-MODEL-PLATFORM.md` (tên bảng/cột), `04-RBAC-MATRIX.md` (mã action `A-*`, `W-*`, `P-*`, `TK-*`, `F-*`, `FL-*`, `CM-*`, `C-*`, `N-*`, `AI-*`, `R-*`, `S-*`; pipeline `P1..P9`), `05-WORKFLOW-STATE-MACHINES.md` (`T-01..T-12` = **transition**, không được nhầm với `TK-*` là mã action của 04 — AUD-52; `ST-01..ST-05`, `PJ-01..PJ-10`, `WS-01..WS-04`, `RACE-01..RACE-15`).

**Quy ước ID của file này:** `EP-nnn` = một endpoint (để `07-TEST-PLAN.md` tham chiếu).

---

## 1. Quy ước chung

### 1.1 Base path, định dạng, phương thức

| Mục | Quy ước đề xuất | Căn cứ |
|---|---|---|
| Base path | `/api/v1` cho **mọi** endpoint REST và WebSocket | AUD-43 (prefix `/api/v1`), ARCH-02 |
| Client dùng chung | React/TypeScript Web và Flutter Mobile gọi **cùng một** contract; **không** có API riêng cho mobile, không có prefix `/api/mobile` | ARCH-02 |
| Content type request | `application/json; charset=utf-8`, ngoại trừ `EP-145` (`application/octet-stream`) | — |
| Content type response | `application/json; charset=utf-8`; `EP-153`/`EP-154` trả `content_type` đã lưu trong `attachment`; `EP-165` trả `text/calendar`; `EP-192` trả `application/pdf` hoặc `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet` hoặc `text/csv` theo `report_export_job.format` | Nhóm 06, Nhóm 08 |
| Resource | **Số nhiều**, kebab-case cho từ ghép: `/workspaces`, `/projects`, `/tasks`, `/comments`, `/notifications`, `/upload-sessions`, `/export-jobs`, `/special-grants`, `/global-permissions`, `/recovery-codes`, `/time-logs`, `/checklist-items`, `/model-configurations`, `/context-manifests`, `/system-settings`, `/security-events`, `/retention-policies` | — |
| Sub-resource | Lồng tối đa 2 cấp sau resource gốc. Ngoại lệ duy nhất: `/tasks/{taskId}/workflow/rounds/{roundId}/designations` (round là aggregate con của workflow) | ARCH-01 |
| Identifier | UUIDv7 trong path; **không** dùng số tự tăng (chống enumeration) | AUD-43, 02A §1 |
| Method semantics | `GET` đọc, không side effect, không `Idempotency-Key`; `POST` tạo tài nguyên **hoặc** thực thi một hành động nghiệp vụ (kể cả chuyển trạng thái); `PATCH` cập nhật một phần; `PUT` thay thế toàn bộ tài nguyên cấu hình (settings, preferences, tag list); `DELETE` xoá — soft-delete là mặc định | Nhóm 09, AUD-45 |
| Hành động không phải CRUD | `POST /{resource}/{id}/{verb}` (ví dụ `/archive`, `/approve`, `/owner-transfer`, `/restore`). **Không** mô tả mutation trạng thái bằng `PATCH` một trường `status` | 05 §1.1 |
| Thời gian | RFC 3339 / ISO-8601 UTC (hậu tố `Z`) ở **mọi** trường thời điểm. Deadline ngày-only trả thêm `due_date_only` + `due_timezone` + `due_is_date_only` | Nhóm 06, 02A §5.1 |
| Ngôn ngữ thông điệp | Server **không** render câu chữ; chỉ trả `message_key` i18n. Client render theo `app_user.locale` | 02B §1.2 `summary_key`, AUD-44 |
| Tên trường JSON | `snake_case`, khớp tên cột 02A/02B khi ánh xạ 1-1 | 02A §1 |

### 1.2 Xác thực (theo DEC-43 — chưa chốt)

| Mục | Đề xuất | Căn cứ |
|---|---|---|
| Loại token | **Opaque access token** (không phải JWT), tra cứu server-side, gắn `user_session.id`; header `Authorization: Bearer <access_token>` | AUD-43 dòng "Token/Session", DEC-43 |
| TTL access token | `security.access_token_ttl_minutes` = 15 (seed 02B §7.1) | DEC-43 |
| Refresh | `EP-003` dùng `refresh_token` gắn `user_refresh_token.session_id`; rotation ghi `rotated_from_id` | 02A §2.3 |
| Một phiên duy nhất | Login mới revoke phiên cũ + refresh token + `device_push_registration`; WebSocket phiên cũ bị đóng bằng close code riêng (`4401`) | Nhóm 01, AUD-19, 02A §2.2 `uq_user_session_single_active` |
| Hiệu lực thu hồi | Kiểm tra `user_session.status = 'ACTIVE'` ở **mỗi** request (P1), kể cả file gateway, mỗi message WebSocket và job worker | Nhóm 10, NFR-24, AUD-48 |
| Khoá tạm | `app_user.locked_until` **không** đổi `status`; trả `423 ACCOUNT_LOCKED`, phân biệt với `403 ACCOUNT_NOT_ACTIVE` | AUD-32 |
| Reauthentication | `reauth_ticket` TTL `security.reauth_ttl_minutes` = 5, single-use, gắn `(action_code, target_type, target_id)`; gửi bằng header `X-Reauth-Token`; thiếu → `428 REAUTH_REQUIRED` | AUD-34, 04-RBAC §14 |
| Endpoint không cần auth | `EP-001`, `EP-002`, `EP-003`, `EP-009`, `EP-010`, `EP-011`, `EP-048`, `EP-049`, `EP-221`, `EP-222`, `EP-223`. Ngoài danh sách này **mọi** endpoint đều qua P1 | 04-RBAC §1 |
| Bootstrap System Admin | **Không có** HTTP endpoint thực thi bootstrap; chỉ có `EP-223` đọc cờ. Bootstrap chạy bằng CLI local (`app-cli`) | AUD-18, A-20 |

### 1.3 Header bắt buộc và tuỳ chọn

| Header | Chiều | Bắt buộc khi | Nội dung / ràng buộc |
|---|---|---|---|
| `Authorization` | request | mọi endpoint có auth | `Bearer <opaque_access_token>` |
| `X-Correlation-Id` | request | luôn (bắt buộc với mutation, khuyến nghị với read) | UUID hoặc chuỗi ≤ 64 ký tự `[A-Za-z0-9._-]`. Nếu client không gửi, server sinh và echo lại. Truyền xuyên suốt HTTP → `outbox_event.correlation_id` → `background_job.correlation_id` → `notification` → `delivery_attempt` (MOD-15) |
| `X-Client-Type` | request | luôn | `WEB` / `ANDROID` / `IOS` / `CLI`, khớp `user_session.client_type`; phục vụ AUD-19 |
| `X-Device-Label` | request | khi login | ≤ 120 ký tự, lưu `user_session.device_label` |
| `Accept-Language` | request | tuỳ chọn | `vi-VN` (mặc định) hoặc `en`; chỉ ảnh hưởng client vì server trả `message_key` |
| `Idempotency-Key` | request | **bắt buộc** với tập endpoint ở §4.1 | UUID, ≤ 64 ký tự; scope theo `uq_idem_key_user (user_id, idempotency_key)` |
| `If-Match` | request | mọi `PATCH`/`PUT`/`POST` mutation trên entity có cột `version` (`task`, `project`, `workspace`, `approval_round`, `attachment`, `app_user`) | ETag dạng `"<version>"` (số thập phân của cột `version`). Thiếu → `428 IF_MATCH_REQUIRED` |
| `X-Reauth-Token` | request | mọi action trong 04-RBAC §14 | token của `reauth_ticket` chưa consume, đúng `action_code` + `target_id` |
| `ETag` | response | mọi `GET`/mutation trên entity có `version` | `"<version>"` hiện tại; sau mutation là version mới |
| `X-Correlation-Id` | response | luôn | echo |
| `Idempotent-Replay` | response | khi trả kết quả đã lưu từ `idempotency_record` | `true` |
| `Retry-After` | response | `429`, `503`, `409 IDEMPOTENCY_IN_PROGRESS` | số giây |
| `X-RateLimit-Scope` | response | `429` | tên nhóm giới hạn, **không** kèm ngưỡng (§1.5, §9) |

### 1.4 Quy tắc 404 thay vì 403 khi actor không có quyền READ

Áp dụng đúng bước **P3** và **P4** của pipeline authorization trong `04-RBAC-MATRIX.md` §1:

1. Nếu actor **không có quyền READ metadata** của resource (không có `workspace_membership` ACTIVE, không có `project_membership` ACTIVE, không có `special_grant` còn hiệu lực, không thoả `hasParticipation`) → trả **`404 RESOURCE_NOT_FOUND`**, **không** trả `403`.
2. Đây là yêu cầu chống lộ thông tin: Nhóm 10 FROZEN buộc "xử lý lỗi không lộ thông tin đối tượng ẩn"; Nhóm 02/03 buộc Workspace Owner/Admin **không** tự đọc Project chưa tham gia và System Admin **không** tự đọc nội dung riêng tư. Trả `403` sẽ xác nhận với người gọi rằng **đối tượng có tồn tại**, biến API thành oracle dò ID — đúng rủi ro "IDOR/cross-tenant data leak — Rất cao" ở đặc tả mục 20. Với UUIDv7 (time-ordered) việc lộ "tồn tại hay không" còn cho phép suy ra thời điểm tạo và nhịp tăng trưởng dữ liệu.
3. `403 FORBIDDEN` **chỉ** dùng ở P5/P7/P8: actor **đã** chứng minh quyền đọc metadata (là thành viên scope) nhưng không đủ quyền thực thi hành động, hoặc vi phạm invariant (self-approval, last manager, self-escalation), hoặc reauth không hợp lệ.
4. Hệ quả bắt buộc cho client: **không** được suy luận "404 nghĩa là dữ liệu đã bị xoá". Thông điệp UI phải trung tính: "Không tìm thấy hoặc bạn không có quyền truy cập".
5. Quy tắc áp cho cả tài nguyên con: `EP-157` (access events của file), `EP-190` (export job của người khác, kể cả SA), `EP-196` (AI job của người khác), `EP-225` (audit event ngoài scope) đều trả `404`.
6. Mọi lần từ chối ghi `audit_event.outcome` tương ứng: `DENIED_NOT_FOUND` (P3/P4), `DENIED_FORBIDDEN` (P5), `DENIED_STATE` (P6), `DENIED_REVOKED`, `DENIED_SELF_APPROVAL`, `DENIED_LAST_MANAGER`, `DENIED_VERSION_CONFLICT`. Riêng P1 không ghi `audit_event` nội dung mà ghi `security_event`.
7. Test âm bắt buộc (tham chiếu `07-TEST-PLAN.md`): Workspace Owner gọi `EP-064` trên Project chưa tham gia phải nhận `404`; System Admin gọi `EP-224` với `scope_type=PROJECT` phải nhận `404`; Project Guest gọi `EP-096` trên Task của Project khác phải nhận `404`.

> **Ghi chú trích dẫn:** đề bài dẫn nguồn quy tắc này là "AUD-42/P3". Trong `00-AUDIT-SPEC.md`, `AUD-42` là phát hiện về **hạ tầng nghiệm thu NFR-09/NFR-10**, không liên quan 404/403. Tài liệu này bám **P3 của 04-RBAC §1** và Nhóm 10. Sai lệch trích dẫn được ghi ở §11.

### 1.5 Quy tắc không lộ ngưỡng rate limit

- Response `429` **không** chứa ngưỡng, công thức tính, độ dài cửa sổ hay bộ đếm đã tiêu thụ. Chỉ có `Retry-After` (giây, làm tròn lên) và `X-RateLimit-Scope` (tên nhóm mức thô: `AUTH`, `RECOVERY`, `FILE_UPLOAD`, `FILE_DOWNLOAD`, `AI`, `REPORT_EXPORT`, `REALTIME`, `DEFAULT`).
- **Không** trả `X-RateLimit-Limit`. Việc có trả `X-RateLimit-Remaining` hay không là điểm chưa chốt (§9, §11) vì 04-RBAC §15 chỉ cấm tiết lộ ngưỡng trong response body.
- `EP-211`/`EP-212` (đọc/ghi cấu hình rate limit) chỉ dành cho System Admin có grant `SYSTEM_CONFIG_MANAGE` + reauth (S-04).
- Vượt ngưỡng luôn ghi `security_event(event_type='RATE_LIMIT_HIT')`; `detail` **không** chứa nội dung request, mật khẩu hay token.
- Không dùng thông điệp lỗi để phân biệt "sai mật khẩu" và "tài khoản không tồn tại" ở `EP-002`/`EP-009` (chống enumeration, bổ trợ AUD-31).

---

## 2. Error model chuẩn

### 2.1 Envelope duy nhất (dùng chung Web + Flutter)

Mọi phản hồi lỗi 4xx/5xx có **cùng một** cấu trúc. Không có envelope thứ hai cho mobile, không có lỗi dạng chuỗi thuần, không có lỗi HTML.

| Trường | Kiểu | Bắt buộc | Ràng buộc | Ghi chú |
|---|---|---|---|---|
| `error.code` | string | Có | `^[A-Z][A-Z0-9_]{2,63}$`, thuộc danh sách §2.3 | Mã ổn định trong suốt major version; client rẽ nhánh theo mã này, **không** theo message |
| `error.message_key` | string | Có | khoá i18n, ví dụ `error.approval.already_decided` | **Không** phải text đã render. Server không tự dịch (AUD-44) |
| `error.message_params` | object | Không | map string → string/number | tham số render `message_key`; **không** chứa nội dung riêng tư của đối tượng ẩn |
| `error.reason_code` | string | Không | mã miền chi tiết hơn `code` | ví dụ `PROJECT_COMPLETED_CASCADE`, `LOST_MEMBERSHIP`, `ROUND_CLOSED`, `TASK_CANCELLED`, `APPROVAL_DISABLED`, `DENIED_STATE`. Tái dùng tập giá trị của `audit_event.reason_code`, `approval_round.cancelled_reason_code`, `approval_designation.revoked_reason` |
| `error.details[]` | array | Không | field-level validation | mỗi phần tử: `field` (đường dẫn JSON, ví dụ `title` hoặc `assignee_ids[2]`), `issue` ∈ {`MISSING`,`TOO_LONG`,`TOO_SHORT`,`INVALID_FORMAT`,`OUT_OF_RANGE`,`NOT_ALLOWED`,`DUPLICATE`,`UNKNOWN_REFERENCE`}, `message_key`, `rejected_value` (chỉ khi không nhạy cảm) |
| `error.reauth_required` | boolean | Có (mặc định `false`) | `true` khi `code = REAUTH_REQUIRED` hoặc action thuộc 04-RBAC §14 | client mở luồng reauth rồi retry **đúng request cũ** với `Idempotency-Key` cũ |
| `error.reauth_action_code` | string | Không | chỉ có khi `reauth_required = true` | `action_code` cần đưa vào `EP-007` |
| `error.retry_after_seconds` | integer | Không | ≥ 0 | chỉ có với `429`, `503`, `504`, `IDEMPOTENCY_IN_PROGRESS`, `EXPORT_NOT_READY` |
| `error.resource_version` | integer | Không | = `version` hiện tại | chỉ trả khi actor có quyền đọc resource; giúp resync sau `VERSION_CONFLICT` |
| `error.winning_decision` | object | Không | `ApprovalDecisionResponse` rút gọn | chỉ có với `APPROVAL_ALREADY_DECIDED` (RACE-01) — trả quyết định đã thắng, không lộ thông tin ngoài quyền |
| `correlation_id` | string | Có | = `X-Correlation-Id` | đối chiếu log/audit; bắt buộc hiển thị ở màn hình lỗi (AUD-44) |
| `resource_state` | object | Không | `{ resource_type, resource_id, state, effective_state }` | trạng thái hiệu lực hiện tại nếu hữu ích (ví dụ `effective_state = ON_HOLD_READONLY`). **Chỉ** trả khi actor qua được P3/P4; nếu từ chối ở P3/P4 thì bỏ trống hoàn toàn |
| `timestamp` | string | Có | ISO-8601 UTC | thời điểm server sinh lỗi |

```json
{
  "error": {
    "code": "APPROVAL_ALREADY_DECIDED",
    "message_key": "error.approval.already_decided",
    "reason_code": "ROUND_CLOSED",
    "details": [],
    "reauth_required": false,
    "resource_version": 9,
    "winning_decision": {
      "round_id": "0197c0a2-0000-7000-8000-000000000001",
      "round_no": 2,
      "decision_outcome": "APPROVED",
      "decided_by": "0197c0a2-0000-7000-8000-0000000000aa",
      "decided_at": "2026-10-11T09:14:02Z"
    }
  },
  "resource_state": {
    "resource_type": "TASK",
    "resource_id": "0197c0a2-0000-7000-8000-0000000000ff",
    "state": "COMPLETED",
    "effective_state": "TASK_COMPLETED"
  },
  "correlation_id": "7f1c9a1e-6d2b-4c33-9a10-5b7c8d9e0f11",
  "timestamp": "2026-10-11T09:14:03Z"
}
```

### 2.2 Mapping HTTP status → mã lỗi

| HTTP | Nghĩa trong hệ thống | Mã lỗi | Bước pipeline | `audit_event.outcome` |
|---|---|---|---|---|
| `400` | Sai cú pháp, thiếu header bắt buộc, JSON không parse được | `MALFORMED_REQUEST`, `VALIDATION_FAILED`, `IDEMPOTENCY_KEY_MISSING`, `IF_MATCH_HEADER_INVALID`, `UNSUPPORTED_SORT_FIELD` | trước P1 | không ghi |
| `401` | Token thiếu/hết hạn/sai, session không ACTIVE | `INVALID_SESSION`, `ACCESS_TOKEN_EXPIRED`, `REFRESH_TOKEN_INVALID` | P1 | `security_event` |
| `403` | Đã xác thực và qua P3/P4 nhưng không đủ quyền hành động hoặc vi phạm invariant quyền | `FORBIDDEN`, `ACCOUNT_NOT_ACTIVE`, `REAUTH_INVALID`, `SELF_APPROVAL_FORBIDDEN_ASSIGNEE`, `SELF_APPROVAL_FORBIDDEN_SUBMITTER`, `NOT_ELIGIBLE_APPROVER`, `SELF_ESCALATION_FORBIDDEN`, `INVITE_EMAIL_MISMATCH`, `INVITE_ROLE_NOT_ALLOWED`, `AI_CONTEXT_DENIED`, `STREAM_TICKET_INVALID` | P2, P5, P7, P8 | `DENIED_FORBIDDEN`, `DENIED_SELF_APPROVAL`, `DENIED_REVOKED` |
| `404` | Không tồn tại **hoặc** fail P3/P4 (chống lộ thông tin) | `RESOURCE_NOT_FOUND`, `AI_JOB_NOT_FOUND`, `AI_RESULT_REVOKED`, `AUDIT_NO_PERMISSION` | P3, P4 | `DENIED_NOT_FOUND` |
| `405` | Method không hỗ trợ trên path | `METHOD_NOT_ALLOWED` | — | không ghi |
| `409` | Xung đột trạng thái, cạnh tranh, bất biến nghiệp vụ | `STATE_CONFLICT`, `VERSION_CONFLICT`, `TASK_STATE_CHANGED`, `PROJECT_READONLY`, `APPROVAL_ALREADY_DECIDED`, `ROUND_NOT_OPEN`, `APPROVAL_NOT_ENABLED`, `SUBTASKS_INCOMPLETE`, `LAST_PROJECT_MANAGER`, `LAST_WORKSPACE_OWNER`, `LAST_SYSTEM_ADMIN_PROTECTED`, `PENDING_APPROVALS_EXIST`, `OWNER_ALREADY_TRANSFERRED`, `PERSONAL_WORKSPACE_IMMUTABLE`, `WORKSPACE_HAS_ACTIVE_PROJECTS`, `SESSION_ALREADY_ACTIVE`, `INVITE_ALREADY_REDEEMED`, `BOOTSTRAP_ALREADY_COMPLETED`, `WORKFLOW_TRANSITION_NOT_ALLOWED`, `UPLOAD_SESSION_STATE_INVALID`, `ATTACHMENT_NOT_AVAILABLE`, `CHECKLIST_POSITION_CONFLICT`, `TAG_DUPLICATE_IN_WORKSPACE`, `USERNAME_OR_EMAIL_RESERVED`, `AI_DISABLED`, `EXPORT_NOT_READY`, `JOB_NOT_CANCELABLE`, `JOB_RETRY_NOT_ALLOWED`, `IDEMPOTENCY_IN_PROGRESS` | P6, P7, P9 | `DENIED_STATE`, `DENIED_VERSION_CONFLICT`, `DENIED_LAST_MANAGER` |
| `410` | Đã hết hạn vĩnh viễn | `INVITE_EXPIRED`, `UPLOAD_SESSION_EXPIRED`, `EXPORT_EXPIRED` | P6 | `DENIED_STATE` |
| `413` | Vượt kích thước cho phép | `FILE_TOO_LARGE` | P7 | `DENIED_FORBIDDEN` |
| `415` | `Content-Type` không hỗ trợ | `UNSUPPORTED_MEDIA_TYPE` | — | không ghi |
| `422` | Đúng cú pháp nhưng sai ngữ nghĩa / vi phạm ràng buộc miền | `IDEMPOTENCY_KEY_REUSED_WITH_DIFFERENT_PAYLOAD`, `FILE_TYPE_NOT_ALLOWED`, `FILE_SCAN_BLOCKED`, `AI_OUTPUT_INVALID`, `SUBTASK_MAX_DEPTH_EXCEEDED`, `DEPENDENCY_CYCLE_DETECTED`, `DEPENDENCY_CROSS_PROJECT`, `GUEST_CANNOT_HAVE_WRITE_ROLE`, `REJECT_REASON_REQUIRED`, `REOPEN_REASON_REQUIRED`, `CANCEL_REASON_REQUIRED`, `TIMELOG_OUTSIDE_EDIT_WINDOW`, `TIMELOG_FUTURE_DATE`, `SUBTASK_MILESTONE_FORBIDDEN` | P7 | `DENIED_FORBIDDEN` |
| `423` | Bị khoá (tài khoản hoặc nội dung thực thi) | `ACCOUNT_LOCKED`, `EXECUTION_LOCKED_PENDING_APPROVAL` | P2, P6 | `DENIED_FORBIDDEN`, `DENIED_STATE` |
| `428` | Thiếu điều kiện tiên quyết của request | `REAUTH_REQUIRED`, `IF_MATCH_REQUIRED` | P7, P8 | `DENIED_FORBIDDEN`, `DENIED_VERSION_CONFLICT` |
| `429` | Vượt rate limit / quota | `RATE_LIMITED` | P9 | `security_event(RATE_LIMIT_HIT)` |
| `500` | Lỗi server không phân loại được | `INTERNAL_ERROR` — không lộ stack trace, tên bảng/cột, SQL | — | `FAILED` |
| `503` | Hạ tầng phụ thuộc chưa sẵn sàng | `SERVICE_UNAVAILABLE`, `AI_OLLAMA_UNAVAILABLE` | — | `FAILED` |
| `504` | Timeout tác vụ nặng | `AI_TIMEOUT` | — | `FAILED` |

`500`/`503`/`504` **không** được chứa tên bảng, tên cột, câu SQL, stack trace, đường dẫn file server hay thông điệp gốc của Ollama/PostgreSQL (Nhóm 10; mục 18.4). Chi tiết kỹ thuật chỉ nằm trong log có `correlation_id`.

### 2.3 Danh sách mã lỗi nghiệp vụ đầy đủ

Cột "Nguồn" ghi nơi mã lỗi đã xuất hiện. Mã đánh dấu **(mới đề xuất)** là bổ sung của tài liệu này vì chưa có trong tài liệu gốc nhưng cần để mô tả hành vi endpoint.

| Mã lỗi | HTTP | Khi nào phát sinh | Nguồn |
|---|---|---|---|
| `INVALID_SESSION` | 401 | Token không tồn tại, hoặc `user_session.status <> 'ACTIVE'` (gồm `REVOKED_LOGIN_ELSEWHERE`) | 04-RBAC P1 |
| `ACCESS_TOKEN_EXPIRED` | 401 | `user_session.expires_at` đã qua nhưng refresh token còn hạn — client tự gọi `EP-003` | (mới đề xuất) |
| `REFRESH_TOKEN_INVALID` | 401 | Refresh token sai, đã rotate, hoặc session cha đã revoke | (mới đề xuất; 02A §2.3) |
| `ACCOUNT_NOT_ACTIVE` | 403 | `app_user.status` ∈ {`PENDING_APPROVAL`,`REJECTED`,`SUSPENDED`,`DISABLED`,`DELETED`} | 04-RBAC P2 |
| `ACCOUNT_LOCKED` | 423 | `app_user.locked_until > now()`; `status` vẫn `ACTIVE` | 04-RBAC P2, AUD-32 |
| `RESOURCE_NOT_FOUND` | 404 | Không tồn tại, đã soft-delete, **hoặc** fail P3/P4 | 04-RBAC P3/P4 |
| `FORBIDDEN` | 403 | Fail P5 — đúng scope nhưng sai role/quyền hành động | 04-RBAC P5 |
| `STATE_CONFLICT` | 409 | Fail P6 — effective state không cho phép action | 04-RBAC P6 |
| `LAST_PROJECT_MANAGER` | 409 | Rời / bị loại / hạ cấp / soft-delete tài khoản khiến Project chưa `DELETED` không còn Manager ACTIVE hợp lệ | AUD-03, W-11, RACE-05, I-04 |
| `LAST_WORKSPACE_OWNER` | 409 | Owner gọi `EP-044` mà chưa chuyển quyền | (mới đề xuất; W-12, Nhóm 02) |
| `LAST_SYSTEM_ADMIN_PROTECTED` | 409 | `EP-024`/`EP-026`/`EP-027` nhắm System Admin cuối cùng (`app_user.protected_flag`) | (mới đề xuất; A-07, I-21) |
| `GUEST_CANNOT_HAVE_WRITE_ROLE` | 422 | Thêm/mời Workspace Guest vào Project với role `MANAGER` hoặc `MEMBER` | AUD-04, I-06, 02A §4.2 |
| `SUBTASKS_INCOMPLETE` | 409 | `EP-126` khi còn subtask `state NOT IN ('COMPLETED','CANCELLED')` và `deleted_at IS NULL` | 05 §2, I-08, RBAC F-02 |
| `SUBTASK_MAX_DEPTH_EXCEEDED` | 422 | `EP-093` khi parent đã có `depth = 1` | 05 §2, I-07, 02A §5.1 |
| `SUBTASK_MILESTONE_FORBIDDEN` | 422 | Gán `milestone_id` cho task có `depth = 1` | (mới đề xuất; 02A `ck_task_subtask_no_milestone`) |
| `DEPENDENCY_CYCLE_DETECTED` | 422 | `EP-108` tạo chu trình — hard-block, dò bằng `WITH RECURSIVE` | AUD-09, I-09, RACE-07 |
| `DEPENDENCY_CROSS_PROJECT` | 422 | Dependency vượt Project hoặc liên quan subtask | (mới đề xuất; DEC-09, I-10) |
| `SELF_APPROVAL_FORBIDDEN_ASSIGNEE` | 403 | `EP-127` khi actor ∈ `task_assignment(task, status='ACTIVE')` | 02A §6.3, I-11 |
| `SELF_APPROVAL_FORBIDDEN_SUBMITTER` | 403 | `EP-127` khi actor = `approval_round.submitted_by`, kể cả khi không còn là assignee | 02A §6.3, I-12 |
| `NOT_ELIGIBLE_APPROVER` | 403 | `EP-127`/`EP-128` khi không có `approval_designation` ACTIVE hoặc không thoả `isEligibleApprover` | 02A §6.3, 04-RBAC §8.1 |
| `APPROVAL_ALREADY_DECIDED` | 409 | Round đã có quyết định terminal (`uq_decision_single_terminal`); response kèm `winning_decision` | AUD-27, I-13, RACE-01 |
| `ROUND_NOT_OPEN` | 409 | `approval_round.status <> 'OPEN'` | 02A §6.3, 05 §3 |
| `APPROVAL_NOT_ENABLED` | 409 | Gọi `EP-127`/`EP-128` trên Project có `approval_enabled = false` | (mới đề xuất; Nhóm 03) |
| `REJECT_REASON_REQUIRED` | 422 | `EP-128` thiếu `reason` hoặc `length(btrim(reason)) < 3` | RBAC F-05, 02A `ck_decision_reject_reason` |
| `REOPEN_REASON_REQUIRED` | 422 | `EP-130` từ `COMPLETED` thiếu `reason` | RBAC F-07, 05 T-08 |
| `CANCEL_REASON_REQUIRED` | 422 | `EP-129` thiếu `reason` khi `workflow.cancel_reason_required = true` | (mới đề xuất; 02A `task.cancel_reason`) |
| `PENDING_APPROVALS_EXIST` | 409 | `EP-078` tắt `approval_enabled` khi còn `approval_round(status='OPEN')` trong Project | RBAC P-16, I-22, 05 §4 |
| `EXECUTION_LOCKED_PENDING_APPROVAL` | 423 | Task `PENDING_APPROVAL` mà gọi mutation nội dung thực thi (nội dung, attachment, checklist, time log, assignment, dependency) | AUD-06, DEC-06, RBAC §3 `TASK_PENDING_APPROVAL` |
| `VERSION_CONFLICT` | 409 | `If-Match` khác `version` hiện tại | RBAC P7 `DENIED_VERSION_CONFLICT`, 02A §10 |
| `IF_MATCH_REQUIRED` | 428 | Mutation trên entity có `version` nhưng thiếu `If-Match` | (mới đề xuất) |
| `IF_MATCH_HEADER_INVALID` | 400 | `If-Match` không phải ETag số hợp lệ | (mới đề xuất) |
| `IDEMPOTENCY_KEY_REUSED_WITH_DIFFERENT_PAYLOAD` | 422 | Cùng `(user_id, idempotency_key)` nhưng `request_hash` khác | 02B §5.5 |
| `IDEMPOTENCY_KEY_MISSING` | 400 | Endpoint bắt buộc (§4.1) mà thiếu header | (mới đề xuất) |
| `IDEMPOTENCY_IN_PROGRESS` | 409 | `idempotency_record.status = 'IN_PROGRESS'` — client retry quá sớm | (mới đề xuất; 02B §5.5) |
| `REAUTH_REQUIRED` | 428 | Action thuộc 04-RBAC §14 mà thiếu `X-Reauth-Token` | RBAC P8, AUD-34 |
| `REAUTH_INVALID` | 403 | `reauth_ticket` đã consume, hết hạn 5 phút, hoặc sai `action_code`/`target_id` | RBAC P8 |
| `RATE_LIMITED` | 429 | Vượt ngưỡng §9; kèm `Retry-After`, **không** kèm ngưỡng | RBAC P9 và §15 |
| `SESSION_ALREADY_ACTIVE` | 409 | `EP-002` khi đã có phiên ACTIVE và request thiếu `confirm_revoke_existing_session = true`; `details` kèm `device_label` + `client_type` của phiên hiện tại, chỉ tiết lộ **sau khi** đã xác minh đúng mật khẩu | (mới đề xuất; AUD-19) |
| `FILE_TYPE_NOT_ALLOWED` | 422 | `content_type` phát hiện thực tế không nằm trong `file.allowed_content_types` | Nhóm 09, 05 §7 |
| `FILE_TOO_LARGE` | 413 | `declared_size` hoặc tổng byte thực nhận vượt `file.max_upload_mb` | Nhóm 09 |
| `FILE_SCAN_BLOCKED` | 422 | `file_object.scan_status = 'INFECTED'` → attachment `QUARANTINED`/`BLOCKED` | 05 §7, Nhóm 09 |
| `UPLOAD_SESSION_EXPIRED` | 410 | `upload_session.expires_at < now()`; staging object sẽ bị `FILE_CLEANUP_ORPHAN` dọn | 02A §7.4, 05 §7 |
| `UPLOAD_SESSION_STATE_INVALID` | 409 | Commit khi `upload_session.status` không thuộc {`UPLOADING`,`SCAN_PENDING`} | (mới đề xuất; 05 §7) |
| `ATTACHMENT_NOT_AVAILABLE` | 409 | Download/preview khi `attachment.status <> 'AVAILABLE'` | (mới đề xuất; 02A §7.1) |
| `STREAM_TICKET_INVALID` | 403 | `StreamTicket` đã dùng, hết TTL ≤ 30s, hoặc quyền bị thu hồi giữa chừng | RBAC FL-02, ARCH-07, RACE-09 |
| `AI_DISABLED` | 409 | `system_setting.ai.enabled = false` (mặc định cho tới khi chốt DEC-41) | 02B §7.1, Nhóm 07 |
| `AI_JOB_NOT_FOUND` | 404 | `ai_job` không tồn tại **hoặc** `principal_user_id` khác actor (AI-02: người khác, kể cả SA, không đọc được) | RBAC AI-02 |
| `AI_TIMEOUT` | 504 | `ai_job.status = 'FAILED_TIMEOUT'`; kèm `retry_after_seconds` | 05 §8, NFR-11 |
| `AI_OLLAMA_UNAVAILABLE` | 503 | `ai_job.status = 'FAILED_OLLAMA'`; core nghiệp vụ **không** bị ảnh hưởng | 05 §8, NFR-12 |
| `AI_OUTPUT_INVALID` | 422 | `FAILED_VALIDATION` — output sai JSON schema hoặc chứa trường ngoài scope; **không retry** | AUD-40, 05 §8 |
| `AI_RESULT_REVOKED` | 404 | `CANCELLED_REVOKED` — mất quyền trước bước `REAUTHORIZING`; suggestion đã bị ẩn | AUD-48, RACE-13, NFR-24 |
| `AI_CONTEXT_DENIED` | 403 | Context Manifest từ chối trường/scope yêu cầu | AUD-39, `security_event(AI_CONTEXT_DENIED)` |
| `EXPORT_EXPIRED` | 410 | `report_export_job.expires_at` (TTL 24h) đã qua; object đã bị `EXPORT_PURGE` dọn | AUD-16, 02B §3.3 |
| `EXPORT_NOT_READY` | 409 | Download khi `status` không thuộc {`GENERATED`,`DELIVERED`}; kèm `retry_after_seconds` | (mới đề xuất) |
| `PERSONAL_WORKSPACE_IMMUTABLE` | 409 | `EP-036` (đổi `type`), `EP-037`, `EP-039`, `EP-046`, `EP-050` trên Workspace `type = 'PERSONAL'` | 05 WS-01/WS-03, Nhóm 02, I-02 |
| `OWNER_ALREADY_TRANSFERRED` | 409 | Thua race chuyển Owner (RACE-04) | RACE-04, AUD-28 |
| `WORKSPACE_HAS_ACTIVE_PROJECTS` | 409 | `EP-039` khi còn Project chưa `DELETED` trong Workspace | 05 WS-03 (chờ xác nhận) |
| `PROJECT_READONLY` | 409 | Effective state ∈ {`ON_HOLD_READONLY`,`COMPLETED_READONLY`,`ARCHIVED_READONLY`} mà gọi mutation; hoặc thua RACE-03 | RACE-03, RBAC §3, AUD-02 |
| `TASK_STATE_CHANGED` | 409 | Thua race workflow (RACE-02, RACE-11): `task.state` không khớp precondition lúc commit | RACE-02, RACE-11 |
| `WORKFLOW_TRANSITION_NOT_ALLOWED` | 409 | Transition không tồn tại trong 05 §1.1 (ví dụ `TODO → COMPLETED`, `COMPLETED → PENDING_APPROVAL`, `* → BLOCKED`) | 05 §1.2 |
| `INVITE_EXPIRED` | 410 | `workspace_invitation.status = 'EXPIRED'` hoặc `expires_at < now()` | 02A §3.3 |
| `INVITE_ALREADY_REDEEMED` | 409 | `status = 'REDEEMED'` — invite dùng một lần, không xoá để giữ audit | AUD-11, 02A §3.3 |
| `INVITE_EMAIL_MISMATCH` | 403 | `app_user.email_normalized` của người accept khác `invitee_email_normalized` | 02A §3.3, AUD-11 |
| `INVITE_ROLE_NOT_ALLOWED` | 403 | Vi phạm ma trận mời: Member mời `ADMIN`, hoặc Guest mời bất kỳ ai | (mới đề xuất; W-08, Nhóm 02) |
| `BOOTSTRAP_ALREADY_COMPLETED` | 409 | Chạy bootstrap khi `system.bootstrap_completed = true` hoặc đã có System Admin; ghi `security_event(BOOTSTRAP_ADMIN_RUN)` | AUD-18, A-20 |
| `SELF_ESCALATION_FORBIDDEN` | 403 | `actor_user_id = target_user_id` ở `EP-031`, `EP-055`, `EP-080`, `EP-208`, `EP-216` | RBAC S-09, Nhóm 10 |
| `AUDIT_NO_PERMISSION` | 404 | Không có `AUDIT_READ` trên đúng scope; hoặc `audit_event.sensitivity = 'SENSITIVE_CONTENT'` mà actor không còn quyền đọc đối tượng gốc tại thời điểm truy vấn | FL-09, AUD-36 |
| `TIMELOG_OUTSIDE_EDIT_WINDOW` | 422 | `work_date` ngoài `project_setting.timelog_edit_window_days`, hoặc `time_log.locked_at` đã set | AUD-23, 02A §5.6 |
| `TIMELOG_FUTURE_DATE` | 422 | `work_date > CURRENT_DATE` | 02A §5.6 |
| `CHECKLIST_POSITION_CONFLICT` | 409 | Trùng `(task_id, position)` khi `deleted_at IS NULL` | (mới đề xuất; 02A §5.4) |
| `TAG_DUPLICATE_IN_WORKSPACE` | 409 | Trùng `(workspace_id, name_normalized)` | (mới đề xuất; AUD-22) |
| `USERNAME_OR_EMAIL_RESERVED` | 409 | `username_normalized`/`email_normalized` đã bị chiếm, **kể cả bởi tài khoản đã soft-delete** | AUD-31, I-16 |
| `JOB_NOT_CANCELABLE` | 409 | `background_job.status` không thuộc {`QUEUED`,`LEASED`,`RUNNING`,`WAITING_REAUTH`,`FAILED_RETRYABLE`} | (mới đề xuất; S-07) |
| `JOB_RETRY_NOT_ALLOWED` | 409 | Job `CANCELLED_REVOKED` hoặc `FAILED_VALIDATION` — không retry trái phép | AUD-48, REC-05, NFR-24 |
| `MALFORMED_REQUEST` | 400 | JSON không parse được, thiếu field bắt buộc ở mức cú pháp | (mới đề xuất) |
| `VALIDATION_FAILED` | 400 | Validation nghiệp vụ mức request fail; chi tiết ở `error.details[]` | (mới đề xuất) |
| `UNSUPPORTED_SORT_FIELD` | 400 | `sort` chứa trường ngoài allowlist của endpoint | (mới đề xuất; §3.3) |
| `UNSUPPORTED_MEDIA_TYPE` | 415 | `Content-Type` không được chấp nhận | (mới đề xuất) |
| `METHOD_NOT_ALLOWED` | 405 | Sai HTTP method trên path tồn tại | (mới đề xuất) |
| `SERVICE_UNAVAILABLE` | 503 | Object Storage hoặc DB chưa sẵn sàng | (mới đề xuất) |
| `INTERNAL_ERROR` | 500 | Lỗi không phân loại; không lộ chi tiết kỹ thuật | (mới đề xuất) |

**Tổng: 81 mã lỗi xuyên suốt** = 54 mã kế thừa từ tài liệu gốc + 27 mã mới đề xuất. Các mã đặc thù theo miền được đăng ký tiếp ở §2.4.

### 2.4 Mã lỗi đặc thù theo miền — tất cả đều "(mới đề xuất)"

§2.3 liệt kê các mã xuyên suốt. Các mã dưới đây chỉ có nghĩa **trong miền của chúng**, được đặt tên theo quy ước suy dẫn được: `<ĐỐI_TƯỢNG>_<VẤN_ĐỀ>`, hậu tố chuẩn là `NOT_FOUND`, `INVALID`, `REQUIRED`, `EXCEEDED`, `NOT_ALLOWED`, `ALREADY_<TRẠNG_THÁI>`, `CONFLICT`, `MISMATCH`. Client không switch trên chuỗi mã để suy ra ngữ nghĩa ngoài `code` + `reason_code` + `details[]`; thêm mã mới là thay đổi additive, không đổi phiên bản API (§10).

| Miền (dải `EP-`) | Số mã | Mã lỗi mới đề xuất |
|---|---|---|
| Account, Session, Reauth, Recovery (EP-001–EP-012) | 15 | `PASSWORD_POLICY_VIOLATION`, `SELF_REGISTRATION_DISABLED`, `INVALID_CREDENTIALS`, `REFRESH_TOKEN_REUSED`, `SESSION_NOT_ACTIVE`, `REASON_REQUIRED`, `REAUTH_NOT_REQUIRED_FOR_ACTION`, `ACTION_CODE_UNKNOWN`, `TARGET_TYPE_MISMATCH`, `RECOVERY_CODE_INVALID`, `RECOVERY_CODE_EXHAUSTED`, `RESET_TICKET_EXPIRED`, `RESET_TICKET_CONSUMED`, `PASSWORD_REUSED_FROM_HISTORY`, `CURRENT_PASSWORD_INCORRECT` |
| Account do System Admin quản lý (EP-013–EP-032) | 26 | `DISPLAY_NAME_INVALID`, `LOCALE_NOT_SUPPORTED`, `AVATAR_ATTACHMENT_NOT_OWNED`, `AVATAR_TARGET_TYPE_MISMATCH`, `PREFERENCE_KEY_UNKNOWN`, `PREFERENCE_VALUE_INVALID`, `WORKSPACE_ID_REQUIRED`, `WORKSPACE_NOT_MEMBER`, `ROLE_CODE_UNKNOWN`, `WA_CANNOT_CREATE_ACTIVE_ACCOUNT`, `DELIVERY_CHANNEL_REQUIRED`, `ACCOUNT_NOT_PENDING`, `ACCOUNT_ALREADY_DECIDED`, `PERSONAL_WORKSPACE_ALREADY_EXISTS`, `PERSONAL_WORKSPACE_CREATE_FAILED`, `ACCOUNT_ALREADY_SUSPENDED`, `SELF_ACCOUNT_ACTION_FORBIDDEN`, `ACCOUNT_NOT_SUSPENDED`, `ACCOUNT_HAS_OPEN_TASKS`, `ACCOUNT_NOT_DELETED`, `RETENTION_WINDOW_EXPIRED`, `MEMBERSHIP_NOT_RECOVERABLE`, `PERMISSION_CODE_UNKNOWN`, `GRANT_ALREADY_EXISTS`, `GRANT_NOT_FOUND`, `GRANT_ALREADY_REVOKED` |
| Workspace, thành viên, mời, tag (EP-033–EP-060) | 32 | `WORKSPACE_NAME_DUPLICATE`, `SLUG_ALREADY_EXISTS`, `WORKSPACE_ALREADY_ARCHIVED`, `WORKSPACE_NOT_ARCHIVED`, `WORKSPACE_NOT_DELETED`, `ROLE_TRANSITION_NOT_ALLOWED`, `MEMBER_NOT_FOUND`, `NOT_A_WORKSPACE_MEMBER`, `INVITE_EXPIRY_TOO_LONG`, `INVITE_EMAIL_ALREADY_PENDING`, `INVITEE_ACCOUNT_NOT_ACTIVE`, `INVITATION_NOT_REVOCABLE`, `INVITATION_NOT_FOUND`, `INVITATION_TOKEN_INVALID`, `TARGET_NOT_WORKSPACE_MEMBER`, `TARGET_ACCOUNT_NOT_ACTIVE`, `TARGET_IS_GUEST`, `REASON_TOO_SHORT`, `EMERGENCY_ACTION_CODE_UNKNOWN`, `WORKSPACE_SETTING_KEY_UNKNOWN`, `WORKSPACE_SETTING_VALUE_INVALID`, `WORKSPACE_SETTING_IMMUTABLE_KEY`, `WORKSPACE_SETTING_EXCEEDS_INVARIANT`, `GRANT_EXCEEDS_GRANTOR`, `VALID_UNTIL_REQUIRED`, `SCOPE_NOT_ALLOWED_FOR_GRANTOR`, `GRANT_ALREADY_EXPIRED`, `TAG_NAME_INVALID`, `TAG_COLOR_INVALID`, `TAG_QUOTA_EXCEEDED`, `TAG_NOT_FOUND`, `TAG_IN_USE_AND_PROTECTED` |
| Project, thành viên, Milestone, cấu hình duyệt (EP-061–EP-091) | 30 | `PROJECT_NAME_DUPLICATE_IN_WORKSPACE`, `PROJECT_NAME_INVALID`, `MEMBER_CANNOT_CREATE_PROJECT`, `DESIGNATED_MANAGER_NOT_WORKSPACE_MEMBER`, `CROSS_WORKSPACE_QUERY_FORBIDDEN`, `PROJECT_CONTENT_NO_GRANT`, `DATE_RANGE_INVALID`, `PROJECT_STATUS_CHANGED_CONCURRENTLY`, `PROJECT_ALREADY_IN_STATUS`, `PROJECT_ALREADY_ARCHIVED`, `PROJECT_NOT_ARCHIVED`, `PREVIOUS_STATUS_MISSING`, `PROJECT_HAS_OPEN_APPROVAL_ROUNDS`, `PROJECT_HAS_INCOMPLETE_TASKS`, `PROJECT_NOT_DELETED`, `USER_NOT_WORKSPACE_MEMBER`, `USER_ALREADY_PROJECT_MEMBER`, `NOT_A_PROJECT_MEMBER`, `PROJECT_SETTING_KEY_UNKNOWN`, `PROJECT_SETTING_VALUE_INVALID`, `SLA_AUTO_DECISION_FORBIDDEN`, `APPROVAL_SETTING_UNCHANGED`, `PROJECT_HAS_ACTIVE_MANAGER`, `MILESTONE_NAME_DUPLICATE`, `MILESTONE_NAME_INVALID`, `MILESTONE_DATE_OUTSIDE_PROJECT_RANGE`, `MILESTONE_NOT_FOUND`, `MILESTONE_TRANSITION_FORBIDDEN`, `MILESTONE_ALREADY_IN_STATUS`, `FILTER_COMBINATION_INVALID` |
| Task, Subtask, Assignment, Dependency, Checklist, Tag, Time log, tìm kiếm (EP-092–EP-124) | 39 | `TASK_TITLE_INVALID`, `MILESTONE_NOT_IN_PROJECT`, `DUE_BEFORE_START`, `MEMBER_CANNOT_CREATE_TASK`, `SUBTASK_PARENT_NOT_ALLOWED`, `SUBTASK_DEADLINE_AFTER_PARENT`, `PROJECT_NOT_ACTIVE`, `SEARCH_QUERY_TOO_SHORT`, `SEARCH_QUERY_TOO_BROAD`, `DUE_DATE_BEFORE_SUBTASK_DEADLINE`, `DEADLINE_CHANGE_REASON_REQUIRED`, `TASK_READONLY`, `TASK_HAS_OPEN_APPROVAL_ROUND`, `TASK_ALREADY_DELETED`, `TASK_NOT_DELETED`, `RESTORE_PARTIAL_IRRECOVERABLE`, `ALREADY_BLOCKED`, `NOT_BLOCKED`, `BLOCKING_DEPENDENCY_STILL_OPEN`, `ASSIGNEE_NOT_PROJECT_MEMBER`, `ASSIGNMENT_ALREADY_EXISTS`, `ASSIGNMENT_NOT_FOUND`, `LAST_ASSIGNEE_OF_IN_PROGRESS_TASK`, `DEPENDENCY_SELF_REFERENCE`, `DEPENDENCY_ALREADY_EXISTS`, `SUBTASK_DEPENDENCY_FORBIDDEN`, `TARGET_TASK_READONLY`, `DEPENDENCY_NOT_FOUND`, `CHECKLIST_TITLE_INVALID`, `CHECKLIST_ITEM_LIMIT_EXCEEDED`, `CHECKLIST_ITEM_NOT_FOUND`, `CHECKLIST_ALREADY_IN_STATE`, `TAG_NOT_IN_WORKSPACE`, `TAG_LIMIT_PER_TASK_EXCEEDED`, `TIMELOG_DURATION_INVALID`, `TIMELOG_OVERLAP_DETECTED`, `TIMELOG_NOT_OWNED`, `TIMELOG_NOT_FOUND`, `TIME_LOG_LOCKED_BY_SNAPSHOT` |
| Workflow và vòng nghiệm thu (EP-125–EP-136) | 17 | `TASK_IS_BLOCKED`, `BLOCKED_BY_DEPENDENCY`, `NO_ASSIGNEE`, `NOT_ASSIGNEE_OF_TASK`, `ROUND_ALREADY_OPEN`, `REOPEN_WINDOW_EXPIRED`, `TASK_HAS_IMMUTABLE_DEPENDENTS`, `NO_OPEN_ROUND`, `DESIGNATED_USER_NOT_PARTICIPANT`, `DESIGNATED_USER_IS_GUEST`, `DESIGNATED_USER_IS_SUBMITTER`, `DESIGNATED_USER_IS_ASSIGNEE`, `DESIGNATION_ALREADY_EXISTS`, `DESIGNATION_NOT_FOUND`, `DESIGNATION_ALREADY_REVOKED`, `DESIGNATION_ALREADY_DECIDED`, `REVOCATION_REASON_CODE_UNKNOWN` |
| Comment (EP-137–EP-142) | 15 | `COMMENT_BODY_REQUIRED`, `COMMENT_BODY_TOO_LONG`, `TARGET_TYPE_NOT_SUPPORTED`, `COMMENTING_DISABLED_FOR_GUEST`, `PARENT_COMMENT_NOT_FOUND`, `REPLY_DEPTH_EXCEEDED`, `PARENT_COMMENT_DELETED`, `PARENT_COMMENT_OTHER_TARGET`, `COMMENT_NOT_EDITABLE`, `COMMENT_IS_DELETED_CONTENT`, `EDIT_REASON_REQUIRED_FOR_MODERATION`, `COMMENT_NOT_FOUND`, `COMMENT_FORBIDDEN_NOT_AUTHOR`, `COMMENT_ALREADY_DELETED`, `ACTIVITY_SCOPE_TOO_BROAD` |
| File, upload, phiên bản, nội dung (EP-143–EP-158) | 25 | `TARGET_NOT_FOUND`, `UPLOAD_QUOTA_DAILY_EXCEEDED`, `CONCURRENT_UPLOAD_LIMIT_EXCEEDED`, `UPLOAD_SESSION_NOT_OWNED`, `CHUNK_OFFSET_MISMATCH`, `CHUNK_TOO_LARGE`, `CONTENT_RANGE_INVALID`, `TARGET_DELETED`, `UPLOAD_INCOMPLETE`, `CHECKSUM_MISMATCH`, `FILE_NAME_DUPLICATE_IN_TARGET`, `UPLOAD_SESSION_NOT_FOUND`, `ATTACHMENT_DELETED`, `VERSION_UPLOAD_IN_PROGRESS`, `CONTENT_TYPE_MISMATCH_WITH_ORIGINAL`, `VERSION_NOT_FOUND`, `VERSION_IS_CURRENT`, `CHANGE_NOTE_REQUIRED`, `FILE_OBJECT_PURGED`, `DOWNLOAD_QUOTA_EXCEEDED`, `PREVIEW_NOT_SUPPORTED_FOR_TYPE`, `PREVIEW_SIZE_LIMIT_EXCEEDED`, `ATTACHMENT_ALREADY_DELETED`, `ATTACHMENT_IS_CURRENT_AVATAR`, `ATTACHMENT_NOT_DELETED` |
| Calendar và Reminder (EP-159–EP-170) | 18 | `DATE_PARAM_INVALID`, `TIMEZONE_UNKNOWN`, `MONTH_PARAM_INVALID`, `RESULT_TOO_LARGE_SUGGEST_LIST_VIEW`, `SCOPE_PARAM_INVALID`, `ICS_RANGE_TOO_LARGE`, `ICS_EXPORT_DISABLED_BY_WORKSPACE`, `CALENDAR_EXPORT_RATE_EXCEEDED`, `QUIET_HOURS_INVALID`, `CALENDAR_VIEW_UNKNOWN`, `OVERDUE_POLICY_UNKNOWN`, `REMINDER_OFFSET_INVALID`, `REMINDER_DUPLICATE_RULE`, `REMINDER_BEFORE_TASK_START`, `EVENT_TYPE_UNKNOWN`, `REMINDER_RULE_NOT_FOUND`, `REMINDER_RULE_NOT_OWNED`, `REMINDER_RULE_SYSTEM_IMMUTABLE` |
| Notification và push (EP-171–EP-179) | 12 | `SINCE_SEQ_TOO_OLD`, `NOTIFICATION_NOT_OWNED`, `READ_ALL_CURSOR_REQUIRED`, `PREFERENCE_TYPE_UNKNOWN`, `PUSH_TOKEN_INVALID`, `PUSH_PLATFORM_UNSUPPORTED`, `DEVICE_REGISTRATION_LIMIT_EXCEEDED`, `PUSH_TOKEN_ALREADY_REGISTERED_OTHER_USER`, `DEVICE_REGISTRATION_NOT_OWNED`, `DEVICE_REGISTRATION_ALREADY_UNREGISTERED`, `RESYNC_WINDOW_EXCEEDED`, `SEQ_PARAM_INVALID` |
| Report, dashboard, metric (EP-181–EP-193) | 18 | `METRIC_CODE_UNKNOWN`, `PERIOD_TOO_WIDE`, `PERIOD_INVALID`, `PROJECT_NOT_FOUND`, `WORKSPACE_NOT_FOUND`, `SCOPE_PARTIAL_NO_DATA`, `SCOPE_TYPE_UNSUPPORTED`, `METRIC_NOT_APPLICABLE_TO_SCOPE`, `DATE_RANGE_TOO_WIDE`, `REPORT_CODE_UNKNOWN`, `EXPORT_FORMAT_UNSUPPORTED`, `EXPORT_ROWS_LIMIT_EXCEEDED`, `DUPLICATE_EXPORT_IN_FLIGHT`, `EXPORT_JOB_NOT_FOUND`, `EXPORT_JOB_NOT_CANCELABLE`, `EXPORT_JOB_ALREADY_TERMINAL`, `PERMISSION_REVOKED_AT_DELIVERY`, `ATTACHMENT_OBJECT_MISSING` |
| AI (EP-194–EP-206) | 24 | `AI_FEATURE_UNKNOWN`, `AI_FEATURE_NOT_ALLOWED_FOR_ROLE`, `AI_CONTEXT_MANIFEST_MISSING`, `AI_CONCURRENCY_LIMIT`, `AI_HINT_TOO_LONG`, `AI_JOB_NOT_CANCELABLE`, `AI_JOB_NOT_COMPLETED`, `AI_RESULT_STATE_LOCKED`, `AI_SUGGESTION_NOT_FOUND`, `AI_SUGGESTION_REDACTED`, `AI_SUGGESTION_ALREADY_DECIDED`, `AI_SUGGESTION_EXPIRED`, `AI_DRAFT_SCHEMA_MISMATCH`, `AI_REJECT_REASON_UNKNOWN`, `HISTORY_WINDOW_TOO_WIDE`, `AI_ADMIN_NOT_GRANTED`, `AI_CONFIG_KEY_UNKNOWN`, `AI_TIMEOUT_OUT_OF_RANGE`, `AI_CONCURRENCY_OUT_OF_RANGE`, `AI_OLLAMA_URL_NOT_INTERNAL`, `AI_MANIFEST_CODE_UNKNOWN`, `AI_MANIFEST_FORBIDDEN_PATTERN_EMPTY`, `AI_MANIFEST_SCOPE_RULE_INVALID`, `AI_MANIFEST_IN_USE_CANNOT_DISABLE` |
| Quản trị hệ thống (EP-207–EP-223) | 24 | `ADMIN_NOT_GRANTED`, `SETTING_KEY_UNKNOWN`, `SETTING_VALUE_TYPE_MISMATCH`, `SETTING_VALUE_VIOLATES_INVARIANT`, `SETTING_IMMUTABLE_IN_V1`, `RATE_LIMIT_SCOPE_UNKNOWN`, `RATE_LIMIT_BELOW_FLOOR`, `RATE_LIMIT_ABOVE_CEILING`, `BACKUP_ALREADY_RUNNING`, `BACKUP_WINDOW_BLOCKED`, `RESTORE_DRILL_IN_PROGRESS`, `RESTORE_DRILL_TARGET_UNSAFE`, `RETENTION_NOT_GRANTED`, `RETENTION_ENTITY_UNKNOWN`, `RETENTION_APPEND_ONLY_ENTITY`, `RETENTION_DAYS_OUT_OF_RANGE`, `DB_ROLE_NOT_ALLOWED`, `PURGE_RANGE_IMMUTABLE`, `PURGE_ALREADY_RUNNING`, `EMERGENCY_TARGET_NOT_FOUND`, `EMERGENCY_REASON_TOO_SHORT`, `EMERGENCY_CONTENT_READ_MUST_BE_FALSE`, `EMERGENCY_SELF_TARGET_FORBIDDEN`, `EMERGENCY_ACTION_NOT_ALLOWED_FOR_ROLE` |
| Truy vấn audit (EP-224–EP-226) | 4 | `AUDIT_SCOPE_REQUIRED`, `AUDIT_DATE_RANGE_TOO_WIDE`, `AUDIT_SENSITIVITY_NOT_GRANTED`, `AUDIT_EVENT_NOT_FOUND` |
| Background job (EP-227–EP-231) | 5 | `JOB_TYPE_UNKNOWN`, `JOB_STATUS_UNKNOWN`, `JOB_NOT_FOUND`, `JOB_MAX_ATTEMPTS_EXCEEDED`, `JOB_PRINCIPAL_PERMISSION_REVOKED` |
| Validation mức DTO và frame realtime (§6, §7) | 17 | `UNKNOWN_FIELD`, `SUBTASK_DEPTH_EXCEEDED`, `TITLE_REQUIRED`, `PRIORITY_INVALID`, `IF_MATCH_MISMATCH`, `TASK_LOCKED_PENDING_APPROVAL`, `BLOCKED_REASON_REQUIRED`, `SUBTASK_MOVE_NOT_SUPPORTED`, `STATE_CHANGE_VIA_WORKFLOW_ONLY`, `REASON_CODE_UNKNOWN`, `ROUND_NOT_FOUND`, `EVIDENCE_NOT_IN_SCOPE`, `CASCADE_ACK_REQUIRED`, `TARGET_TYPE_NOT_CLIENT_CREATABLE`, `DECLARED_SIZE_MISMATCH`, `CONTENT_TYPE_NOT_ALLOWED`, `PUSH_PROVIDER_UNAVAILABLE` |

**Tổng cộng toàn tài liệu: 402 mã lỗi** = 54 mã kế thừa từ tài liệu gốc + 27 mã mới đề xuất ở §2.3 + 321 mã đặc thù theo miền mới đề xuất ở §2.4. Không mã nào trong §2.4 trùng tên với mã ở §2.3. (Con số này đã được kiểm bằng script cộng trực tiếp 16 dòng của bảng §2.4 — tổng khai báo và tổng thực đếm khớp nhau ở cả 16 dòng; hai mã `CANNOT_DISABLE_SECURITY_CRITICAL_CHANNEL` và `CHANNELS_EMPTY` bị loại khi `EP-167`/`EP-175` được viết lại theo AUD-51/DEC-52, và `CALENDAR_VIEW_UNKNOWN` được thêm cho `EP-167`.)

> **Sáu tên viết hoa cố ý KHÔNG được đếm vào 402 mã lỗi** — chúng không phải `error.code` và đã được chú thích ngay tại endpoint dùng chúng: `SNAPSHOT_NOT_AVAILABLE` (`EP-186`, chỉ là envelope `INSUFFICIENT_DATA`), `PROGRESS_DENOMINATOR_ZERO` (`EP-193`, trả `NOT_APPLICABLE`), `SNAPSHOT_IMMUTABLE_VIOLATION` (chỉ dùng ở migration/job, không đi qua API), `SECURITY_EVENT_DETAIL_REDACTED` (`EP-210`, cờ `redacted=true` trên từng dòng), `AUDIT_BEFORE_AFTER_REDACTED` (`EP-225`, cờ lược trường), `JOB_PAYLOAD_WITHHELD` (`EP-228`, cờ). Client **không** được switch trên sáu tên này như thể chúng là mã lỗi. Danh sách này là kết quả đối chiếu bằng script giữa "mã xuất hiện ở §5" và "mã đăng ký ở §2.3/§2.4"; ngoài sáu tên này không còn khoản chênh nào.

---

## 3. Pagination, filter, sort

### 3.1 Cursor-based là mặc định

| Tham số | Kiểu | Mặc định | Ràng buộc | Ghi chú |
|---|---|---|---|---|
| `cursor` | string opaque (base64url) | rỗng (trang đầu) | ≤ 512 ký tự; server không cam kết cấu trúc nội bộ | Con trỏ ổn định, **không** chứa dữ liệu nghiệp vụ nhạy cảm. Vô hiệu khi đổi `sort` hoặc đổi filter → `400 VALIDATION_FAILED` với `details[].field = "cursor"` |
| `limit` | integer | 20 | `1 ≤ limit ≤ 100` | Vượt 100 → `400 VALIDATION_FAILED`, **không** tự cắt im lặng |

Response của **mọi** endpoint trả danh sách là `PageResponse<T>` (§6.15) gồm `items[]`, `next_cursor`, `has_more`, `limit`. Không có `total_count` mặc định.

**Vì sao không dùng offset cho list lớn:**

1. `task`, `activity_event`, `workflow_event`, `notification`, `audit_event`, `file_access_event` là các bảng append-heavy và partition theo tháng (02B §8). `OFFSET n` buộc PostgreSQL đọc rồi vứt bỏ `n` dòng → độ trễ tăng tuyến tính theo độ sâu trang, phá mục tiêu NFR-10 (P95 ≤ 500 ms) ở trang sâu.
2. Offset **không ổn định** khi có concurrent insert/delete: dòng bị bỏ qua hoặc bị lặp giữa hai trang. Với `activity_event` và `notification` (có `seq` monotonic) điều này tạo timeline sai thứ tự ở client.
3. Cursor neo trên `(sort_key, id)` cho kết quả nhất quán kể cả khi dữ liệu thay đổi giữa các trang, và cho phép index seek thay vì scan.
4. Cursor bắt buộc kèm `id` làm tie-breaker (UUIDv7 time-ordered, 02A §1) để không mất dòng khi sort key trùng giá trị.

**Ngoại lệ có chủ đích:** `total_count` chỉ được trả khi (a) scope nhỏ và đã lọc quyền (ví dụ số Project actor có participation), và (b) việc tiết lộ số lượng **không** làm lộ đối tượng ẩn (AUD-38 → `PARTIAL`). Với dashboard Workspace, số Project **không** được trả cho người chỉ tham gia một phần.

### 3.2 Filter query param chuẩn

| Param | Kiểu | Áp dụng cho | Ghi chú |
|---|---|---|---|
| `state` | enum, lặp lại (`state=TODO&state=IN_PROGRESS`) | `EP-089`, `EP-094`, `EP-095`, `EP-162` | đúng tập `task.state` (02A §5.1). `BLOCKED` **không** phải state → dùng `blocked` |
| `project_id` | uuid, lặp lại | `EP-094`, `EP-159`..`EP-164` | server **luôn** giao với tập Project actor có participation; giá trị ngoài quyền bị bỏ qua và **không** báo lỗi (chống oracle) |
| `workspace_id` | uuid | `EP-063`, `EP-094`, `EP-159`..`EP-164` | như trên |
| `assignee_id` | uuid, lặp lại; alias `assignee_id=me` | `EP-089`, `EP-094`, `EP-181` | join `task_assignment(status='ACTIVE')` |
| `created_by` | uuid; alias `created_by=me` | `EP-094`, `EP-181` | tách bạch ASSIGNED / CREATED / MANAGED (AUD-37, R-01) |
| `milestone_id` | uuid, lặp lại; `milestone_id=none` | `EP-089` | `none` = Task không thuộc Milestone (Nhóm 03) |
| `due_before` / `due_after` | ISO-8601 | `EP-089`, `EP-094`, `EP-159`..`EP-164` | so trên `task.due_at` (instant chuẩn hoá, Nhóm 06) |
| `start_before` / `start_after` | ISO-8601 | `EP-089`, `EP-159`..`EP-164` | so trên `task.start_at` |
| `q` | string ≤ 200 | `EP-095`, `EP-137`, `EP-171`, `EP-224` | full-text + fuzzy trên `task.search_text`/`search_tsv` và `pg_trgm` (AUD-26). Không dùng `LIKE '%q%'` thô |
| `tag` | string (tên tag), lặp lại | `EP-089`, `EP-094` | resolve qua `tag(workspace_id, name_normalized)`; tag không tồn tại trong scope → tập rỗng |
| `blocked` | boolean | `EP-089`, `EP-094` | cờ độc lập `task.blocked_flag` (Nhóm 04) |
| `depth` | integer `0` hoặc `1` | `EP-089`, `EP-094` | `0` = top-level (mặc định cho progress/dashboard), `1` = subtask |
| `overdue` | boolean | `EP-094`, `EP-164` | `due_at < now() AND state NOT IN ('COMPLETED','CANCELLED')`; tách thêm `overdue_pending_approval` (02B §3.4) |
| `type`, `severity`, `read`, `redacted` | enum/boolean | `EP-171` | theo `notification.type`, `severity`, `read_at IS NULL`, `redacted` |
| `scope_type` / `scope_id` | enum + uuid | `EP-185`, `EP-186`, `EP-224` | theo 02B §3.1 và 02B §6.1 |
| `status` | enum | `EP-021`, `EP-063`, `EP-189`, `EP-227` | theo bảng tương ứng |
| `occurred_from` / `occurred_to` | ISO-8601 | `EP-121`, `EP-122`, `EP-190`, `EP-210`, `EP-224` | theo `occurred_at` |
| `include_deleted` | boolean, mặc định `false` | `EP-089`, `EP-094`, `EP-148` | chỉ có hiệu lực khi actor có quyền restore tương ứng (TK-14, FL-04, P-10, W-07); ngược lại bị bỏ qua |

### 3.3 Sort

- Cú pháp: `sort=field` (tăng dần), `sort=-field` (giảm dần). Nhiều trường: `sort=-due_at,title`. Tối đa 3 trường.
- Field được phép sort là **danh sách đóng** khai báo trên từng endpoint. Mặc định: `EP-094` → `-updated_at`; `EP-121`/`EP-122`/`EP-171`/`EP-224` → `-occurred_at`.
- Trường ngoài allowlist → `400 UNSUPPORTED_SORT_FIELD`. **Không** fallback im lặng.
- Server luôn append `id` làm tie-breaker cuối để cursor ổn định.

### 3.4 Không cho phép sort/filter vượt quyền

1. Filter và sort áp dụng **sau** khi Policy Layer xác định tập resource actor được đọc, không phải trước. Không có đường nào dùng filter để suy ra dữ liệu ẩn (ví dụ `q` không được trả số lượng khớp của Project chưa tham gia).
2. Giá trị scope trong query mà actor không có quyền bị **loại bỏ im lặng**; response không tiết lộ "bạn gửi 5 `project_id` nhưng chỉ 2 hợp lệ". Nếu toàn bộ bị loại → trả danh sách rỗng, không phải 403/404.
3. Không tồn tại tham số sort/filter nào sinh **bảng xếp hạng năng suất cá nhân**: `R-07 PRODUCTIVITY_RANKING_INDIVIDUAL` không tồn tại trong hệ thống (04-RBAC §10). Do đó không có `sort=-timelog_minutes` theo người, không có `sort=-task_completed_count` theo user, và `EP-185` không chấp nhận `metric_code = TIMELOG_MINUTES_TOTAL` với `scope_type = USER` của người khác.
4. Mọi số liệu tổng hợp bọc trong `MetricEnvelope` (§6.7) để phân biệt `0` / `N/A` / không có quyền / thiếu dữ liệu / một phần (AUD-38).
5. `EP-224` áp dụng quyền đọc **động**: filter theo `scope_id` mà actor không có quyền đọc đối tượng gốc → dòng `sensitivity = 'SENSITIVE_CONTENT'` bị loại; dòng `NORMAL` cùng scope có thể vẫn hiển thị nếu có grant `AUDIT_READ_SCOPE` (AUD-36).

---

## 4. Idempotency & optimistic concurrency

### 4.1 Endpoint BẮT BUỘC có `Idempotency-Key`

Thiếu header trên các endpoint này → `400 IDEMPOTENCY_KEY_MISSING`.

| Nhóm | Endpoint | Lý do |
|---|---|---|
| Workflow (mọi POST) | `EP-125`, `EP-126`, `EP-127`, `EP-128`, `EP-129`, `EP-130` | Chống double-approve/double-submit khi client retry. Đặc tả 14.3 bước 4: "nếu thông báo lỗi, retry notification, **không** lặp approve" |
| Approval designation | `EP-134`, `EP-135` | Chống chỉ định/thu hồi trùng giữa round OPEN |
| Upload | `EP-143` (initiate), `EP-146` (commit), `EP-151` (version mới), `EP-152` (restore version), `EP-156` | RACE-08: 2 request upload cùng attachment → trùng `version_no` |
| AI | `EP-194`, `EP-197`, `EP-200`, `EP-201` | `ai_job.idempotency_key` UNIQUE (02B §4.2); job AI tốn tài nguyên, không được nhân bản |
| Report export | `EP-188`, `EP-191` | `report_export_job.idempotency_key` UNIQUE (02B §3.3) |
| Invitation | `EP-046`, `EP-047` | `uq_winv_open_email` — chống gửi 2 invite cho cùng email |
| Owner transfer | `EP-050`, `EP-051` | RACE-04 — chuyển Owner 2 hướng đồng thời |
| Membership add / role change | `EP-042`, `EP-072`, `EP-073`, `EP-104`, `EP-105`, `EP-106`, `EP-169` | `uq_psm_active`, `uq_assignment_active`, `uq_reminder_dedup` |
| Special grant | `EP-055`, `EP-056`, `EP-080`, `EP-081` | `uq_special_grant_active`; thao tác nhạy cảm có reauth |
| Account lifecycle | `EP-017`, `EP-020`, `EP-022`, `EP-023`, `EP-024`, `EP-025`, `EP-026`, `EP-027`, `EP-028`, `EP-029`, `EP-031`, `EP-032` | Tránh double-approve / double-suspend; mỗi lần đều ghi audit |
| Project status & lifecycle | `EP-066`, `EP-067`, `EP-068`, `EP-069`, `EP-070`, `EP-078`, `EP-087` | `EP-066` với `to_status = COMPLETED` chạy cascade đồng bộ (PJ-05) — tuyệt đối không chạy hai lần |
| Emergency actions | `EP-082`, `EP-219` | Ghi `emergency_action_log`; không được lặp |
| Admin config & retention | `EP-204`, `EP-206`, `EP-208`, `EP-212`, `EP-216`, `EP-217` | `configuration_change_log` before/after; chống ghi log trùng |
| Backup | `EP-213`, `EP-214` | Job nặng, không lặp |
| Notification read-all | `EP-173` | RACE-14: mark-all-read đồng thời redaction |
| Job control | `EP-230`, `EP-231` | REC-06: chống cancel/retry trùng |
| Task create / subtask create | `EP-092`, `EP-093`, `EP-099` | Client mobile retry khi mạng chập chờn |
| Session revoke / logout | `EP-006` | Tránh ghi nhiều `security_event` cho cùng một lần revoke |

**Không** bắt buộc (nhưng vẫn được chấp nhận và lưu) với các `POST` idempotent tự nhiên: `EP-100`/`EP-101`, `EP-113`, `EP-172`, `EP-004`, `EP-012`. **Không** áp dụng cho `GET` và cho `DELETE` trên sub-resource đã idempotent (`EP-075`, `EP-109`, `EP-114`, `EP-155`, `EP-170`, `EP-178`).

### 4.2 Semantics lưu trong `idempotency_record` (02B §5.5)

1. Khoá duy nhất `uq_idem_key_user (user_id, idempotency_key)`. Key **không** dùng chung giữa hai tài khoản.
2. Khi nhận request: `INSERT` bản ghi `status = 'IN_PROGRESS'` với `request_hash = sha256(method + path + canonical_json(body))` trong **cùng transaction** nghiệp vụ (cùng nguyên tắc ARCH-04 với `outbox_event`).
3. Cùng key + **cùng** `request_hash` + `status = 'COMPLETED'` → trả lại `response_status` và nội dung trỏ bởi `response_body_ref`, **không** thực thi lại use case, không ghi `audit_event` nghiệp vụ lần hai, không phát `outbox_event` lần hai. Response thêm header `Idempotent-Replay: true`.
4. Cùng key + **khác** `request_hash` → `422 IDEMPOTENCY_KEY_REUSED_WITH_DIFFERENT_PAYLOAD`. Không tiết lộ payload cũ.
5. Cùng key + `status = 'IN_PROGRESS'` (request trước chưa xong) → `409 IDEMPOTENCY_IN_PROGRESS` + `Retry-After: 2`.
6. Cùng key + `status = 'FAILED'` → cho phép thực thi lại (ghi đè bản ghi), vì chưa có tác động nghiệp vụ nào được commit.
7. `expires_at` mặc định 24 giờ; sau đó key được tái sử dụng tự do. Dọn theo `ix_idem_expiry`.
8. `response_body_ref` **không** lưu nội dung nhạy cảm thô của đối tượng mà actor có thể đã mất quyền. Khi replay, server **vẫn** chạy lại P1..P8: nếu lúc replay actor đã mất quyền → `404`/`403`, **không** trả cache. Đây là điểm khác biệt so với idempotency cache thông thường, bắt buộc bởi Nhóm 10 "thu hồi quyền có hiệu lực ngay với yêu cầu mới".
9. Idempotency **không** thay optimistic concurrency: request replay vẫn phải mang `If-Match` khớp `version` hiện tại.

### 4.3 Optimistic concurrency qua `version` + `If-Match`

| Bước | Hành vi |
|---|---|
| Đọc | `GET` trả `ETag: "<version>"` và trường `version` trong body |
| Ghi | Client gửi `If-Match: "<version>"`. Server `UPDATE ... SET version = version + 1 WHERE id = $1 AND version = $2` |
| 0 dòng bị ảnh hưởng | `409 VERSION_CONFLICT` + `error.resource_version` = version hiện tại (chỉ khi actor có quyền đọc) |
| Thiếu header | `428 IF_MATCH_REQUIRED` (khác 401/403 để client phân biệt — cùng tinh thần 04-RBAC §14 dùng `428 REAUTH_REQUIRED`) |
| Entity có `version` | `task`, `project`, `workspace`, `approval_round`, `attachment`, `app_user` (02A §1) |
| Entity append-only | `audit_event`, `security_event`, `workflow_event`, `activity_event`, `approval_decision`, `file_access_event`, `outbox_event` **không** có `version` → không dùng `If-Match`; mọi mutation trên chúng trả `405 METHOD_NOT_ALLOWED` |
| Lock tường minh | Approve/reject: `SELECT ... FOR UPDATE` trên `approval_round` và `task` (05 §8.2, AUD-27). Owner transfer: lock `workspace`. Cascade PJ-05: lock `project`. Dependency: lock **cả hai** task theo thứ tự `id` tăng dần (RACE-07; quy tắc chống deadlock 05 §10) |
| Isolation | `READ COMMITTED` mặc định; `REPEATABLE READ` chỉ cho job snapshot (RACE-15). Không dùng `SERIALIZABLE` mặc định (02A §10) |

```http
POST /api/v1/tasks/0197c0a2-0000-7000-8000-0000000000ff/workflow/approve HTTP/1.1
Host: stm.example.internal
Authorization: Bearer 7c1f-opaque-access-token-9a
Content-Type: application/json; charset=utf-8
X-Correlation-Id: 7f1c9a1e-6d2b-4c33-9a10-5b7c8d9e0f11
X-Client-Type: WEB
Idempotency-Key: 3d6b0f2a-9c1e-4b7a-8f55-1a2b3c4d5e6f
If-Match: "8"
```

---

## 5. Bảng endpoint đầy đủ theo nhóm

Đây là phần trọng tâm của tài liệu. **Toàn bộ 231 endpoint dưới đây là ĐỀ XUẤT chờ phê duyệt**; đặc tả mục 17 chỉ gợi ý phân nhóm, không chốt đường dẫn.

**Chú giải cột (áp dụng cho mọi bảng ở §5):**

| Cột | Ý nghĩa |
|---|---|
| `ID` | `EP-nnn`, định danh ổn định để `06-USE-CASES.md` và `07-TEST-PLAN.md` tham chiếu |
| `Method` / `Path` | Path đã lược prefix `/api/v1`. `{...}` là path param kiểu UUIDv7 |
| `Mã quyền` | Mã hành động trong `04-RBAC-MATRIX.md`. Hậu tố `(mới)` = chưa có trong 04-RBAC, do tài liệu này đề xuất (tổng hợp ở §11) |
| `State gate` | Điều kiện trạng thái hiệu lực (04-RBAC §3). `WS=ACTIVE`: `workspace.status` là ACTIVE; `PJ-writable`: `project.status` thuộc PLANNING hoặc ACTIVE; `PJ-exec`: `project.status` là ACTIVE; `exec-unlocked`: Task không ở `TASK_PENDING_APPROVAL` (AUD-06); `round OPEN`: `approval_round.status` là OPEN; `OPEN`: không gate thêm ngoài pipeline |
| `Idem` | `BB` = bắt buộc `Idempotency-Key` (§4.1); `TN` = tự nhiên idempotent (PUT/DELETE lặp lại không đổi kết quả); `—` = GET, không áp dụng |
| `Reauth` | `Y` = action nằm trong 04-RBAC §14; thiếu `X-Reauth-Token` → `428 REAUTH_REQUIRED` |
| `Audit action` | Giá trị `audit_event.action`. Với Nhóm 01/02/03 (04-RBAC §4/§5/§6) dùng **đúng** tên ở cột "Audit action" của ma trận. Với Nhóm 04/05/06/07/08/09/10 (04-RBAC §7..§11 **không có** cột audit) tên do tài liệu này đề xuất theo prefix ở §13 — chưa frozen, ghi ở §11 |
| `Error riêng` | Chỉ mã **đặc thù**. Mọi endpoint ngầm chịu các mã pipeline chung: `INVALID_SESSION`, `ACCOUNT_NOT_ACTIVE`, `ACCOUNT_LOCKED`, `RESOURCE_NOT_FOUND`, `FORBIDDEN`, `STATE_CONFLICT`, `REAUTH_REQUIRED`, `REAUTH_INVALID`, `VERSION_CONFLICT`, `IF_MATCH_REQUIRED`, `IDEMPOTENCY_KEY_MISSING`, `RATE_LIMITED`, `MALFORMED_REQUEST`, `VALIDATION_FAILED`, `UNSUPPORTED_MEDIA_TYPE`, `METHOD_NOT_ALLOWED`, `INTERNAL_ERROR` |

Quy tắc áp cho **mọi** endpoint ghi: chạy đủ pipeline P1..P9 trước khi chạm dữ liệu; ghi `audit_event` **trong cùng transaction**; ghi `outbox_event` **trước khi commit** (ARCH-04); notification/realtime chỉ phát **sau khi commit** (05 §8.2 và đặc tả 14.3 bước 4). Thiếu quyền READ trên tài nguyên đích → `404`, không bao giờ `403` (§1.4).

### 5.1 Nhóm A — Auth, Session, Reauthentication, Recovery (MOD-01)

| ID | Method | Path | Mô tả | Mã quyền | State gate | Idem | Reauth | Audit action | Error riêng |
|---|---|---|---|---|---|---|---|---|---|
| EP-001 | POST | `/auth/register` | Tự đăng ký: tạo `app_user.status` là PENDING_APPROVAL; **không** cấp session, **không** tạo Personal Workspace (chỉ tạo sau khi ACTIVE — A-01) | `A-01` | OPEN | BB | — | `ACCOUNT_REGISTER` | `USERNAME_OR_EMAIL_RESERVED`; `PASSWORD_POLICY_VIOLATION`; `SELF_REGISTRATION_DISABLED` |
| EP-002 | POST | `/auth/login` | Xác thực credential; tạo `user_session` (opaque access token) và `user_refresh_token`. Body: `identifier`, `password`, `client_type` (WEB/ANDROID/IOS/CLI), `device_label`, `confirm_revoke_existing_session`. Vì bất biến một phiên duy nhất (`uq_user_session_single_active`), phiên cũ bị thu hồi với status REVOKED_LOGIN_ELSEWHERE | `A-15` | OPEN | BB | — | `LOGIN` | `INVALID_CREDENTIALS` (thông điệp giống hệt nhau cho sai định danh và sai mật khẩu); `ACCOUNT_NOT_ACTIVE` (kèm `reason_code` là PENDING_APPROVAL / REJECTED / SUSPENDED / DISABLED); `ACCOUNT_LOCKED` (423); `SESSION_ALREADY_ACTIVE` (409 khi `confirm_revoke_existing_session=false` và đã có phiên ACTIVE — `details[]` chỉ lộ `device_label` và `client_type` **sau khi** đã xác minh đúng mật khẩu, AUD-19) |
| EP-003 | POST | `/auth/refresh` | Đổi `refresh_token` lấy access token mới; rotation ghi `rotated_from_id`; tái sử dụng token đã rotate → thu hồi cả họ và ghi `security_event` | `—` | OPEN | BB | — | `SESSION_TOKEN_REFRESH` (mới) | `REFRESH_TOKEN_INVALID`; `REFRESH_TOKEN_REUSED`; `SESSION_NOT_ACTIVE` |
| EP-004 | POST | `/auth/logout` | Thu hồi phiên hiện tại (status REVOKED_LOGOUT), cascade huỷ `user_refresh_token` và `device_push_registration`; phát realtime `SESSION_REVOKED` | `A-16` | OPEN | TN | — | `LOGOUT` | — |
| EP-005 | GET | `/sessions/current` | Phiên hiện hành: `session_id`, `client_type`, `device_label`, `issued_at`, `expires_at`, `last_activity_at`, `ip_label` đã che | `—` | OPEN | — | — | không audit | — |
| EP-006 | POST | `/sessions/revoke` | Thu hồi cưỡng bức một phiên (`session_id` trong body) → status REVOKED_ADMIN; SA làm cho người khác bắt buộc reauth | `A-17` | OPEN | BB | Y (cho người khác) | `SESSION_REVOKE` | `SESSION_NOT_ACTIVE`; `REASON_REQUIRED` |
| EP-007 | POST | `/auth/reauth/initiate` | Phát `reauth_ticket` gắn `(action_code, target_type, target_id)` theo 04-RBAC §14; TTL `security.reauth_ttl_minutes` = 5 phút, single-use. **Không** tạo session mới | `A-18` | OPEN | BB | — | `REAUTH_ISSUE` | `REAUTH_NOT_REQUIRED_FOR_ACTION`; `ACTION_CODE_UNKNOWN`; `TARGET_TYPE_MISMATCH` |
| EP-008 | POST | `/auth/reauth/verify` | Xác minh mật khẩu hoặc recovery code với `reauth_ticket` → trả token để đưa vào header `X-Reauth-Token` | `A-18` | OPEN | BB | — | `REAUTH_VERIFY` | `REAUTH_INVALID`; `ACCOUNT_LOCKED` |
| EP-009 | POST | `/auth/recovery/request` | Yêu cầu tự khôi phục mật khẩu; tạo `password_reset_ticket` với `ticket_type` là SELF_SERVICE_RESET. **Luôn** trả `202` kể cả email không tồn tại (chống enumeration, bổ trợ AUD-31) | `—` | OPEN | BB | — | `RECOVERY_REQUEST` (mới) | không trả lỗi phân biệt; chỉ `RATE_LIMITED` (3/giờ/email) |
| EP-010 | POST | `/auth/recovery/recovery-code` | Redeem recovery code khi mất cả phiên lẫn mật khẩu; code một lần, tạo phiên mới và đặt `must_change_password=true` | `A-14` | OPEN | BB | — | `RECOVERY_CODE_USED` | `RECOVERY_CODE_INVALID`; `RECOVERY_CODE_EXHAUSTED`; `ACCOUNT_LOCKED` |
| EP-011 | POST | `/auth/recovery/confirm` | Hoàn tất bằng `password_reset_ticket`: đặt mật khẩu mới, thu hồi **mọi** phiên, refresh token, reauth ticket và push registration (đặc tả 14.6) | `A-11` | OPEN | BB | — | `PASSWORD_CHANGE` | `RESET_TICKET_EXPIRED`; `RESET_TICKET_CONSUMED`; `PASSWORD_POLICY_VIOLATION`; `PASSWORD_REUSED_FROM_HISTORY` |
| EP-012 | POST | `/auth/password/change` | Đổi mật khẩu khi đang đăng nhập; thu hồi mọi phiên **khác** (status REVOKED_PASSWORD_CHANGE) | `A-11` | OPEN | TN | Y | `PASSWORD_CHANGE` | `CURRENT_PASSWORD_INCORRECT`; `PASSWORD_POLICY_VIOLATION`; `PASSWORD_REUSED_FROM_HISTORY` |

**Không có** endpoint bootstrap System Admin qua HTTP (AUD-18; A-20 ghi rõ "chỉ CLI local"). Trạng thái bootstrap chỉ đọc được qua EP-223; mọi nỗ lực thực thi bootstrap qua HTTP trả `409 BOOTSTRAP_ALREADY_COMPLETED`.

### 5.2 Nhóm B — Users, Accounts, Approval, Global grants (MOD-01, MOD-02)

| ID | Method | Path | Mô tả | Mã quyền | State gate | Idem | Reauth | Audit action | Error riêng |
|---|---|---|---|---|---|---|---|---|---|
| EP-013 | GET | `/users/me` | Hồ sơ chính mình: `display_name`, `username`, `email`, `status`, `locale`, `first_day_of_week`, `calendar_default_view`, `avatar_attachment_id`, `must_change_password` | `A-09` | OPEN | — | — | `ACCOUNT_READ` | — |
| EP-014 | PATCH | `/users/me` | Tự sửa `display_name`, `locale`, `first_day_of_week`, `calendar_default_view`, `avatar_attachment_id`. Đổi **`email`** bắt buộc reauth và ghi `user_email_history` (A-10) | `A-10` | OPEN | TN | chỉ khi đổi `email` | `PROFILE_UPDATE` | `DISPLAY_NAME_INVALID`; `LOCALE_NOT_SUPPORTED`; `USERNAME_OR_EMAIL_RESERVED`; `AVATAR_ATTACHMENT_NOT_OWNED`; `AVATAR_TARGET_TYPE_MISMATCH` |
| EP-015 | GET | `/users/me/preferences` | Gộp tuỳ chọn hiển thị, `notification_preference` và `reminder_rule` scope USER_DEFAULT của chính mình để client khởi động bằng một lần gọi | `A-09` | OPEN | — | — | không audit | — |
| EP-016 | PUT | `/users/me/preferences` | Ghi đè tuỳ chọn hiển thị cá nhân (không gồm notification preference — dùng EP-175) | `A-10` | OPEN | TN | — | `PROFILE_UPDATE` | `PREFERENCE_KEY_UNKNOWN`; `PREFERENCE_VALUE_INVALID` |
| EP-017 | POST | `/users/me/recovery-codes/regenerate` | Sinh lại 8 recovery code; code cũ vô hiệu ngay; plaintext trả về **một lần**, DB chỉ lưu hash | `A-13` | OPEN | BB | Y | `RECOVERY_CODE_REGEN` | — |
| EP-018 | GET | `/users` | Tra cứu người dùng để mời hoặc gán. Bắt buộc `workspace_id` với actor không phải SA; chỉ trả người có `workspace_membership` ACTIVE trong Workspace đó. Trường trả về ở mức metadata (A-09), **không** kèm nội dung Task/Project | `A-09` | OPEN | — | — | `ACCOUNT_READ` | `WORKSPACE_ID_REQUIRED`; `WORKSPACE_NOT_MEMBER` → trả `404` (§1.4) |
| EP-019 | GET | `/users/{userId}` | Hồ sơ metadata một người dùng: `id`, `username`, `email` (che một phần nếu actor không phải SA/WA), `status`, `workspace_count` | `A-09` | OPEN | — | — | `ACCOUNT_READ` | — |
| EP-020 | POST | `/accounts` | Admin tạo tài khoản. **SA** → status ACTIVE ngay. **WA** có grant `ACCOUNT_APPROVE_GLOBAL` → chỉ tạo được tài khoản thường ở PENDING_APPROVAL trong phạm vi quyền (A-04) | `A-04` | OPEN | BB | Y (SA) | `ACCOUNT_CREATE` | `USERNAME_OR_EMAIL_RESERVED`; `PASSWORD_POLICY_VIOLATION`; `ROLE_CODE_UNKNOWN`; `WA_CANNOT_CREATE_ACTIVE_ACCOUNT`; `DELIVERY_CHANNEL_REQUIRED` |
| EP-021 | GET | `/accounts` | Danh sách tài khoản, filter `status` (PENDING_APPROVAL, ACTIVE, REJECTED, SUSPENDED, DISABLED, DELETED), `registered_after`, `q` | `A-09` | OPEN | — | — | `ACCOUNT_READ` | — |
| EP-022 | POST | `/accounts/{userId}/approve` | Duyệt tài khoản PENDING_APPROVAL → ACTIVE. **Cùng transaction**: tạo Personal Workspace (`type` là PERSONAL), `workspace_membership` role OWNER status ACTIVE, và `workspace_setting` mặc định (W-02: tự động khi account ACTIVE) | `A-02` | OPEN | BB | Y (WA) | `ACCOUNT_APPROVE` | `ACCOUNT_NOT_PENDING`; `ACCOUNT_ALREADY_DECIDED`; `PERSONAL_WORKSPACE_ALREADY_EXISTS`; `PERSONAL_WORKSPACE_CREATE_FAILED` (rollback toàn bộ) |
| EP-023 | POST | `/accounts/{userId}/reject` | Từ chối tài khoản chờ; `reason` bắt buộc; không tạo Personal Workspace | `A-03` | OPEN | BB | Y (WA) | `ACCOUNT_REJECT` | `ACCOUNT_NOT_PENDING`; `REASON_REQUIRED`; `ACCOUNT_ALREADY_DECIDED` |
| EP-024 | POST | `/accounts/{userId}/suspend` | `status` thành SUSPENDED, thu hồi mọi phiên ngay (REVOKED_ADMIN) và huỷ push registration; `reason` bắt buộc, `suspended_until` tuỳ chọn | `A-05` | OPEN | BB | Y | `ACCOUNT_SUSPEND` | `ACCOUNT_ALREADY_SUSPENDED`; `SELF_ACCOUNT_ACTION_FORBIDDEN`; `LAST_SYSTEM_ADMIN_PROTECTED`; `REASON_REQUIRED` |
| EP-025 | POST | `/accounts/{userId}/unsuspend` | Trả `status` về ACTIVE. **Không** tự tạo lại phiên — người dùng phải đăng nhập mới | `A-05` | OPEN | BB | Y | `ACCOUNT_SUSPEND` | `ACCOUNT_NOT_SUSPENDED`; `LAST_SYSTEM_ADMIN_PROTECTED` |
| EP-026 | POST | `/accounts/{userId}/disable` | `status` thành DISABLED; thu hồi phiên; membership chuyển không hoạt động; dữ liệu giữ nguyên để audit | `A-06` | OPEN | BB | Y | `ACCOUNT_DISABLE` | `SELF_ACCOUNT_ACTION_FORBIDDEN`; `LAST_SYSTEM_ADMIN_PROTECTED`; `LAST_WORKSPACE_OWNER`; `LAST_PROJECT_MANAGER`; `REASON_REQUIRED` |
| EP-027 | POST | `/accounts/{userId}/delete` | `status` thành DELETED kèm `deleted_at` và `delete_group_id`. Bắt buộc đã chuyển WO/PM hợp lệ trước (A-07); không xoá được SA cuối cùng (`protected_flag`) | `A-07` | OPEN | BB | Y | `ACCOUNT_DELETE` | `LAST_WORKSPACE_OWNER`; `LAST_PROJECT_MANAGER`; `LAST_SYSTEM_ADMIN_PROTECTED`; `ACCOUNT_HAS_OPEN_TASKS`; `SELF_ACCOUNT_ACTION_FORBIDDEN`; `REASON_REQUIRED` |
| EP-028 | POST | `/accounts/{userId}/restore` | Khôi phục tài khoản soft-delete. **Không hồi sinh membership đã bị gỡ** (AUD-29) — response liệt kê phần không khôi phục được trong `details[]` | `A-08` | OPEN | BB | Y | `ACCOUNT_RESTORE` | `ACCOUNT_NOT_DELETED`; `RETENTION_WINDOW_EXPIRED`; `MEMBERSHIP_NOT_RECOVERABLE` |
| EP-029 | POST | `/accounts/{userId}/password-reset` | Admin phát mật khẩu tạm: `password_reset_ticket` với `ticket_type` là ADMIN_ISSUED_TEMP_PASSWORD, TTL **24 giờ** (AUD-17), đặt `must_change_password=true`; thu hồi mọi phiên của nạn nhân | `A-12` | OPEN | BB | Y | `PASSWORD_RESET_ISSUE` | `ACCOUNT_NOT_ACTIVE`; `DELIVERY_CHANNEL_REQUIRED` |
| EP-030 | GET | `/accounts/{userId}/global-permissions` | Liệt kê `global_permission_grant` còn hiệu lực: `permission_code` (ACCOUNT_APPROVE_GLOBAL, AUDIT_READ_SYSTEM, PROJECT_CONTENT_READ_GLOBAL, SYSTEM_CONFIG_MANAGE, OLLAMA_CONFIG_MANAGE, RETENTION_MANAGE), `granted_by`, `expires_at` | `A-19` | OPEN | — | — | `PRIVILEGE_GRANT` | — |
| EP-031 | POST | `/accounts/{userId}/global-permissions` | Cấp grant hệ thống; `reason` bắt buộc, `expires_at` tuỳ chọn; `actor_user_id` phải khác `target_user_id` (S-09) | `A-19` | OPEN | BB | Y | `PRIVILEGE_GRANT` | `SELF_ESCALATION_FORBIDDEN`; `PERMISSION_CODE_UNKNOWN`; `GRANT_ALREADY_EXISTS`; `REASON_REQUIRED` |
| EP-032 | DELETE | `/accounts/{userId}/global-permissions/{permissionCode}` | Thu hồi grant (`revoked_at`, `revoked_by`); hiệu lực ở request kế tiếp vì P2/P5 luôn re-check | `A-19` | OPEN | BB | Y | `PRIVILEGE_REVOKE` | `SELF_ESCALATION_FORBIDDEN`; `GRANT_NOT_FOUND`; `GRANT_ALREADY_REVOKED`; `LAST_SYSTEM_ADMIN_PROTECTED` |

### 5.3 Nhóm C — Workspaces, Members, Invitations, Owner transfer, Settings, Tags (MOD-02)

| ID | Method | Path | Mô tả | Mã quyền | State gate | Idem | Reauth | Audit action | Error riêng |
|---|---|---|---|---|---|---|---|---|---|
| EP-033 | POST | `/workspaces` | Tạo Workspace `type` là TEAM; người tạo nhận `workspace_membership` role OWNER. Personal Workspace **không** tạo được qua endpoint này (W-02: tự động khi account ACTIVE, 02A bất biến `workspace.type` immutable) | `W-01` | OPEN | BB | — | `WORKSPACE_CREATE` | `WORKSPACE_NAME_DUPLICATE`; `SLUG_ALREADY_EXISTS`; `PERSONAL_WORKSPACE_IMMUTABLE` (khi body khai `type=PERSONAL`) |
| EP-034 | GET | `/workspaces` | Workspace mà actor có `workspace_membership` status ACTIVE hoặc PENDING_ACTIVATION; mỗi phần tử kèm `role`, `status`, `effective_state` | `W-03` | OPEN | — | — | `WORKSPACE_READ` | — |
| EP-035 | GET | `/workspaces/{workspaceId}` | Chi tiết Workspace: `name`, `slug`, `type`, `status`, `owner_user_id`, `created_at`, bộ đếm `project_count` và `member_count` bọc trong `MetricEnvelope` (§6.7) | `W-03` | OPEN | — | — | `WORKSPACE_READ` | — |
| EP-036 | PATCH | `/workspaces/{workspaceId}` | Sửa `name`, `slug`, `description`. **Không** sửa được `type` (trigger `raise_immutable_column`) | `W-05` | `WS=ACTIVE` | TN | — | `WORKSPACE_UPDATE` | `WORKSPACE_NAME_DUPLICATE`; `SLUG_ALREADY_EXISTS`; `PERSONAL_WORKSPACE_IMMUTABLE`; `STATE_CONFLICT` (khi ARCHIVED/DELETED) |
| EP-037 | POST | `/workspaces/{workspaceId}/archive` | `status` thành ARCHIVED (**WS-01**; `05 §5` — bản draft ghi nhầm `WS-04`, mà `WS-04` là *restore*, xem `07 §8.2` X-11). Được phép **dù còn Task dở dang** → toàn bộ Project con thành readonly và reminder bị pause (`workspace_setting.reminder_paused=true`, `paused_reason=WORKSPACE_ARCHIVED`) | `W-06` | `WS=ACTIVE` | BB | Y | `WORKSPACE_ARCHIVE` | `WORKSPACE_ALREADY_ARCHIVED`; `REASON_REQUIRED` |
| EP-038 | POST | `/workspaces/{workspaceId}/unarchive` | Trả `status` về ACTIVE và bỏ pause reminder. Project con **giữ nguyên** trạng thái trước khi archive (Project đã COMPLETED không tự mở lại) | `W-06` | OPEN | BB | Y | `WORKSPACE_ARCHIVE` | `WORKSPACE_NOT_ARCHIVED`; `PERSONAL_WORKSPACE_IMMUTABLE` |
| EP-039 | DELETE | `/workspaces/{workspaceId}` | Soft-delete Workspace (`deleted_at` + `delete_group_id`). Chỉ áp dụng cho TEAM; không xoá được khi còn Project chưa DELETED | `W-07` | `WS=ACTIVE` | BB | Y | `WORKSPACE_DELETE` | `PERSONAL_WORKSPACE_IMMUTABLE`; `WORKSPACE_HAS_ACTIVE_PROJECTS`; `LAST_WORKSPACE_OWNER` |
| EP-040 | POST | `/workspaces/{workspaceId}/restore` | Khôi phục Workspace soft-delete. **Không hồi sinh membership** đã bị gỡ trong thời gian xoá (AUD-29); response liệt kê phần không khôi phục được | `W-07` | OPEN | BB | Y | `WORKSPACE_DELETE` | `WORKSPACE_NOT_DELETED`; `RETENTION_WINDOW_EXPIRED`; `MEMBERSHIP_NOT_RECOVERABLE`; `PERSONAL_WORKSPACE_IMMUTABLE` |
| EP-041 | GET | `/workspaces/{workspaceId}/members` | Thành viên: `user_id`, `display_name`, `role` (OWNER/ADMIN/MEMBER/GUEST), `status`, `joined_at`, `left_at`, `left_reason`, `left_via` | `W-03` | OPEN | — | — | `WORKSPACE_READ` | — |
| EP-042 | PATCH | `/workspaces/{workspaceId}/members/{userId}` | Đổi `role` giữa ADMIN / MEMBER / GUEST. Không đổi được OWNER (dùng EP-050); ADMIN không sửa được ADMIN khác và không nâng ai lên OWNER (W-13) | `W-13` | `WS=ACTIVE` | BB | — | `WORKSPACE_ROLE_CHANGE` | `ROLE_TRANSITION_NOT_ALLOWED`; `MEMBER_NOT_FOUND`; `LAST_WORKSPACE_OWNER`; `PERSONAL_WORKSPACE_IMMUTABLE` |
| EP-043 | DELETE | `/workspaces/{workspaceId}/members/{userId}` | Gỡ thành viên (W-11) → `status` REMOVED, `left_via` là REMOVED_BY_OWNER hoặc REMOVED_BY_ADMIN. **Cascade bắt buộc trong cùng transaction** (04-RBAC §5, 9 hậu quả): thu hồi quyền ngay; `task_assignment` Task chưa xong thành REMOVED với `removal_reason=MEMBERSHIP_REVOKED`; giữ nguyên lịch sử; notify Manager liên quan; **không** tự approve Task nào; chặn nếu là Manager cuối; revoke mọi `approval_designation` ACTIVE (`revoked_reason=LOST_MEMBERSHIP`); redact notification và chặn deep link (AUD-13); huỷ job đang chạy dưới `principal_user_id` đó (AUD-48) | `W-11` | `WS=ACTIVE` | BB | — | `WORKSPACE_MEMBER_REMOVE` | `LAST_PROJECT_MANAGER` (409, AUD-03); `LAST_WORKSPACE_OWNER`; `MEMBER_NOT_FOUND`; `PERSONAL_WORKSPACE_IMMUTABLE` |
| EP-044 | POST | `/workspaces/{workspaceId}/leave` | Thành viên tự rời (W-12) → `status` LEFT, `left_via=SELF_LEAVE`. OWNER phải chuyển quyền trước. Cascade giống EP-043 | `W-12` | OPEN | BB | — | `WORKSPACE_LEAVE` | `LAST_WORKSPACE_OWNER`; `LAST_PROJECT_MANAGER`; `NOT_A_WORKSPACE_MEMBER` |
| EP-045 | GET | `/workspaces/{workspaceId}/invitations` | Lời mời: `status` (PENDING_ACCEPT, REDEEMED, EXPIRED, REVOKED, PENDING_ACCOUNT_APPROVAL), `invitee_email` (che một phần với actor không phải WO/WA), `target_role`, `expires_at`, `invited_by` | `W-03` | OPEN | — | — | `WORKSPACE_READ` | — |
| EP-046 | POST | `/workspaces/{workspaceId}/invitations` | Tạo lời mời theo email cụ thể, `target_role` (ADMIN/MEMBER/GUEST), `expires_at` tối đa 7 ngày, token một lần (AUD-11). ADMIN chỉ mời được ADMIN/MEMBER/GUEST; MEMBER chỉ mời được MEMBER/GUEST; GUEST không mời được (W-08) | `W-08` | `WS=ACTIVE` | BB | — | `WORKSPACE_INVITE` | `INVITE_ROLE_NOT_ALLOWED`; `INVITE_EXPIRY_TOO_LONG`; `INVITE_EMAIL_ALREADY_PENDING` (409 theo `uq_winv_open_email`); `INVITEE_ACCOUNT_NOT_ACTIVE` (→ `status` PENDING_ACCOUNT_APPROVAL thay vì lỗi, nếu Workspace cho phép) |
| EP-047 | DELETE | `/workspaces/{workspaceId}/invitations/{invitationId}` | Thu hồi lời mời → `status` REVOKED; token vô hiệu ngay. ADMIN thu hồi được mời do mình hoặc do ADMIN khác; MEMBER chỉ mời do mình (W-09) | `W-09` | OPEN | BB | — | `WORKSPACE_INVITE_REVOKE` | `INVITATION_NOT_REVOCABLE`; `INVITATION_NOT_FOUND` |
| EP-048 | GET | `/invitations/{invitationToken}` | Xem trước lời mời **không cần đăng nhập**: Workspace `name`, `target_role`, `invitee_email_masked`, `expires_at`, `status`. Không tiết lộ `workspace_id` đầy đủ hay danh sách thành viên | `—` | OPEN | — | — | `WORKSPACE_INVITE_PREVIEW` (mới) | `INVITATION_TOKEN_INVALID`; `INVITE_EXPIRED`; `INVITE_ALREADY_REDEEMED` |
| EP-049 | POST | `/invitations/{invitationToken}/accept` | Chấp nhận lời mời (W-10): chỉ người có `app_user.email_normalized` khớp `invitee_email_normalized`; tài khoản phải ACTIVE. Tạo `workspace_membership` với `target_role` | `W-10` | OPEN | BB | — | `WORKSPACE_INVITE_ACCEPT` | `INVITE_EMAIL_MISMATCH`; `INVITE_EXPIRED`; `INVITE_ALREADY_REDEEMED`; `ACCOUNT_NOT_ACTIVE`; `WORKSPACE_ALREADY_ARCHIVED` |
| EP-050 | POST | `/workspaces/{workspaceId}/owner-transfer` | Chuyển OWNER nguyên tử (W-14, RACE-04): lock row `workspace`, target phải ACTIVE và không GUEST, Owner cũ xuống MEMBER với `left_via=OWNER_TRANSFERRED` nếu rời, **vai trò Project không đổi** (AUD-28). `uq_wsm_single_owner` bảo đảm không có hai OWNER | `W-14` | `WS=ACTIVE` | BB | Y | `WORKSPACE_OWNER_TRANSFER` | `TARGET_NOT_WORKSPACE_MEMBER`; `TARGET_ACCOUNT_NOT_ACTIVE`; `TARGET_IS_GUEST`; `OWNER_ALREADY_TRANSFERRED`; `PERSONAL_WORKSPACE_IMMUTABLE`; `REASON_REQUIRED` |
| EP-051 | POST | `/admin/emergency/workspace-owner-transfer` | SA chỉ định OWNER trong tình huống khẩn (W-15). **Không đọc nội dung**; ghi `emergency_action_log` với `action_code=SYSTEM_ADMIN_OWNER_TRANSFER` và `content_read=false` | `W-15` | OPEN | BB | Y | `EMERGENCY_OWNER_TRANSFER` | `REASON_TOO_SHORT`; `TARGET_NOT_WORKSPACE_MEMBER`; `EMERGENCY_ACTION_CODE_UNKNOWN`; `OWNER_ALREADY_TRANSFERRED` |
| EP-052 | GET | `/workspaces/{workspaceId}/settings` | `workspace_setting`: `allow_member_create_project`, `default_reminder_offsets_minutes`, `reminder_paused`, `paused_reason`, `paused_at` | `W-03` | OPEN | — | — | `WORKSPACE_READ` | — |
| EP-053 | PUT | `/workspaces/{workspaceId}/settings` | Ghi đè `workspace_setting`. Không cấu hình nào được vượt invariant bảo mật (W-16, Nhóm 10) | `W-16` | `WS=ACTIVE` | BB | Y | `WORKSPACE_SETTING_UPDATE` | `WORKSPACE_SETTING_KEY_UNKNOWN`; `WORKSPACE_SETTING_VALUE_INVALID`; `WORKSPACE_SETTING_IMMUTABLE_KEY`; `WORKSPACE_SETTING_EXCEEDS_INVARIANT` |
| EP-054 | GET | `/workspaces/{workspaceId}/special-grants` | Grant ngoại lệ cấp WORKSPACE (AUD-50): `grantee_user_id`, `permission_code` (WORKSPACE_CONTENT_READ hoặc AUDIT_READ_SCOPE), `granted_by`, `expires_at`, `revoked_at` | `P-23` | OPEN | — | — | `WORKSPACE_CONTENT_READ` | — |
| EP-055 | POST | `/workspaces/{workspaceId}/special-grants` | Cấp `special_grant` scope WORKSPACE (P-23): SA cấp cho scope SYSTEM/WORKSPACE/PROJECT, WO chỉ cấp cho scope WORKSPACE/PROJECT của mình, WA không cấp được. Bắt buộc `reason`, `expires_at` và `reauth_ticket_id`; cấm self-grant; không cấp vượt quyền của chính người cấp | `P-23` | `WS=ACTIVE` | BB | Y | `SPECIAL_GRANT_ISSUE` | `SELF_ESCALATION_FORBIDDEN`; `GRANT_EXCEEDS_GRANTOR`; `GRANT_ALREADY_EXISTS` (409 theo `uq_special_grant_active`); `VALID_UNTIL_REQUIRED`; `PERMISSION_CODE_UNKNOWN`; `SCOPE_NOT_ALLOWED_FOR_GRANTOR` |
| EP-056 | DELETE | `/workspaces/{workspaceId}/special-grants/{grantId}` | Thu hồi grant (`revoked_at`, `revoked_by`); hiệu lực ở request kế tiếp của người bị thu hồi | `P-23` | OPEN | BB | Y | `SPECIAL_GRANT_ISSUE` | `GRANT_NOT_FOUND`; `GRANT_ALREADY_REVOKED`; `GRANT_ALREADY_EXPIRED` |
| EP-057 | GET | `/workspaces/{workspaceId}/tags` | Định nghĩa tag thuộc **Workspace** (AUD-22): `name`, `name_normalized`, `color`, `description`, `usage_count` bọc `MetricEnvelope` | `TAG_READ (mới)` | OPEN | — | — | không audit | — |
| EP-058 | POST | `/workspaces/{workspaceId}/tags` | Tạo định nghĩa tag. Chỉ WO/WA (AUD-22); MEMBER/GUEST chỉ **gắn** tag có sẵn lên Task (EP-116) | `TAG_MANAGE (mới)` | `WS=ACTIVE` | BB | — | `WORKSPACE_TAG_CREATE` (mới) | `TAG_DUPLICATE_IN_WORKSPACE`; `TAG_NAME_INVALID`; `TAG_COLOR_INVALID`; `TAG_QUOTA_EXCEEDED` |
| EP-059 | PATCH | `/tags/{tagId}` | Sửa `name`, `color`, `description`. Đổi tên ghi `activity_event` cho từng Task đang gắn tag, payload chứa cả tên cũ và tên mới | `TAG_MANAGE (mới)` | `WS=ACTIVE` | TN | — | `WORKSPACE_TAG_UPDATE` (mới) | `TAG_DUPLICATE_IN_WORKSPACE`; `TAG_NOT_FOUND`; `TAG_NAME_INVALID` |
| EP-060 | DELETE | `/tags/{tagId}` | Soft-delete định nghĩa tag; gỡ các `task_tag` liên quan trong cùng transaction và ghi `activity_event` cho từng Task bị ảnh hưởng | `TAG_MANAGE (mới)` | `WS=ACTIVE` | TN | — | `WORKSPACE_TAG_DELETE` (mới) | `TAG_NOT_FOUND`; `TAG_IN_USE_AND_PROTECTED` |

### 5.4 Nhóm D — Projects, Participants, Managers, Settings, Milestones (MOD-03)

| ID | Method | Path | Mô tả | Mã quyền | State gate | Idem | Reauth | Audit action | Error riêng |
|---|---|---|---|---|---|---|---|---|---|
| EP-061 | POST | `/workspaces/{workspaceId}/projects` | Tạo Project (P-01): `status` khởi tạo PLANNING, `approval_enabled=false`, `allow_member_create_task=false`, `created_by` là actor và mặc định thành MANAGER. WO/WA luôn được; WM chỉ khi `workspace_setting.allow_member_create_project=true`; WG không | `P-01` | `WS=ACTIVE` | BB | — | `PROJECT_CREATE` | `PROJECT_NAME_DUPLICATE_IN_WORKSPACE` (409 theo `uq_project_name_in_ws`); `PROJECT_NAME_INVALID`; `MEMBER_CANNOT_CREATE_PROJECT`; `DESIGNATED_MANAGER_NOT_WORKSPACE_MEMBER`; `STATE_CONFLICT` (Workspace ARCHIVED) |
| EP-062 | GET | `/workspaces/{workspaceId}/projects` | Project trong Workspace mà actor được thấy. Theo P-02 WO/WA thấy metadata **mọi** Project trong WS; WM/WG chỉ thấy Project được cấp rõ — **điểm mâu thuẫn với 04-RBAC §12**, ghi ở §11 | `P-02` | OPEN | — | — | `PROJECT_READ` | — |
| EP-063 | GET | `/projects` | Liệt kê Project xuyên các Workspace mà actor tham gia; filter `workspace_id`, `status`, `q`, `sort`. Actor không phải SA/WA mà truyền `workspace_id` ngoài participation → `404` | `P-02` | OPEN | — | — | `PROJECT_READ` | `CROSS_WORKSPACE_QUERY_FORBIDDEN` |
| EP-064 | GET | `/projects/{projectId}` | Chi tiết Project. P-02 cho metadata (`id`, `name`, `status`, `manager_count`); P-03 cho nội dung (`description`, thống kê Task, tiến độ chi tiết). WO/WA/SA chưa tham gia chỉ có đường `special_grant(PROJECT_CONTENT_READ)` + reauth (AUD-50). Thiếu cả hai → `404` | `P-02`, `P-03` | OPEN | — | Y (khi dùng grant) | `PROJECT_READ` / `PROJECT_CONTENT_READ` | `PROJECT_CONTENT_NO_GRANT` → trả `404` (§1.4) |
| EP-065 | PATCH | `/projects/{projectId}` | Sửa `name`, `description`. Đổi ngày không tự dời deadline Task con | `P-04` | `PJ-writable` | TN | — | `PROJECT_UPDATE` | `PROJECT_NAME_DUPLICATE_IN_WORKSPACE`; `PROJECT_NAME_INVALID`; `PROJECT_READONLY`; `DATE_RANGE_INVALID` |
| EP-066 | POST | `/projects/{projectId}/status` | Chuyển `status` theo máy trạng thái PJ-01..PJ-10 với `to_status` (PLANNING / ACTIVE / ON_HOLD / COMPLETED). `to_status=COMPLETED` chạy **cascade đồng bộ một transaction** (AUD-01, P-08, F-09): Task chưa xong thành CANCELLED; round OPEN đóng với `round_outcome=CANCELLED` và `cancelled_reason_code=PROJECT_COMPLETED_CASCADE`; Milestone chưa xong thành CANCELLED; Task COMPLETED giữ nguyên. Được phép còn Task dở dang và **không** bắt buộc lý do. Ghi `completed_cascade_audit_id` | `P-05`, `P-06`, `P-07`, `P-08` | OPEN | BB | — | `PROJECT_STATUS_CHANGE` (P-05/P-06/P-07) hoặc `PROJECT_COMPLETED` (P-08) | `WORKFLOW_TRANSITION_NOT_ALLOWED` (409, liệt kê transition hợp lệ trong `details[]`); `PROJECT_STATUS_CHANGED_CONCURRENTLY` (RACE-03); `REASON_REQUIRED` (bắt buộc với ON_HOLD theo cấu hình Project); `PROJECT_ALREADY_IN_STATUS` |
| EP-067 | POST | `/projects/{projectId}/archive` | `status` thành ARCHIVED → `ARCHIVED_READONLY` (mọi mutation bị P6 chặn); lưu `previous_status`, `archived_at`, `archived_by` | `P-09` | OPEN | BB | — | `PROJECT_ARCHIVE` | `PROJECT_ALREADY_ARCHIVED`; `PROJECT_READONLY`; `REASON_REQUIRED` |
| EP-068 | POST | `/projects/{projectId}/unarchive` | Khôi phục `status` từ `previous_status` (P-09: "unarchive khôi phục previous_status") | `P-09` | OPEN | BB | — | `PROJECT_ARCHIVE` | `PROJECT_NOT_ARCHIVED`; `PREVIOUS_STATUS_MISSING`; `STATE_CONFLICT` (Workspace ARCHIVED) |
| EP-069 | DELETE | `/projects/{projectId}` | Soft-delete Project; `delete_group_id` phủ Task, Milestone, membership con (AUD-29) | `P-10` | OPEN | BB | Y | `PROJECT_DELETE` | `PROJECT_HAS_OPEN_APPROVAL_ROUNDS`; `PROJECT_HAS_INCOMPLETE_TASKS`; `STATE_CONFLICT` |
| EP-070 | POST | `/projects/{projectId}/restore` | Khôi phục theo `delete_group_id`; không hồi sinh membership đã bị gỡ (AUD-29); không tự reopen Task đã COMPLETED/CANCELLED | `P-10` | OPEN | BB | Y | `PROJECT_DELETE` | `PROJECT_NOT_DELETED`; `RETENTION_WINDOW_EXPIRED`; `MEMBERSHIP_NOT_RECOVERABLE` |
| EP-071 | GET | `/projects/{projectId}/members` | `project_membership`: `user_id`, `display_name`, `role` (MANAGER/MEMBER/GUEST), `status`, `added_by`, `added_at`, `is_creator`, `source` (DIRECT / SPECIAL_GRANT) | `P-02` | OPEN | — | — | `PROJECT_READ` | — |
| EP-072 | POST | `/projects/{projectId}/members` | Thêm thành viên (P-11): target phải là `workspace_membership` ACTIVE; WG chỉ được gắn role GUEST (AUD-04, trigger `guest_cannot_have_write_role`); WG không thành MANAGER | `P-11` | `PJ-writable` | BB | — | `PROJECT_MEMBER_ADD` | `GUEST_CANNOT_HAVE_WRITE_ROLE`; `USER_NOT_WORKSPACE_MEMBER`; `USER_ALREADY_PROJECT_MEMBER`; `ACCOUNT_NOT_ACTIVE`; `ROLE_CODE_UNKNOWN` |
| EP-073 | PATCH | `/projects/{projectId}/members/{userId}` | Đổi `role` (P-13). Bất biến: luôn còn ít nhất một MANAGER ACTIVE (`trg_project_has_manager`, DEFERRABLE) | `P-13` | `PJ-writable` | BB | — | `PROJECT_ROLE_CHANGE` | `LAST_PROJECT_MANAGER`; `ROLE_TRANSITION_NOT_ALLOWED`; `MEMBER_NOT_FOUND`; `PROJECT_READONLY` |
| EP-074 | DELETE | `/projects/{projectId}/members/{userId}` | Gỡ thành viên (P-12) → `status` REMOVED, `removal_reason` phù hợp; gỡ `task_assignment` Task chưa xong; revoke `approval_designation` ACTIVE với `revoked_reason=LOST_PROJECT_ROLE` | `P-12` | `PJ-writable` | BB | — | `PROJECT_MEMBER_REMOVE` | `LAST_PROJECT_MANAGER`; `MEMBER_NOT_FOUND`; `PROJECT_READONLY` |
| EP-075 | DELETE | `/projects/{projectId}/members/me` | Tự rời Project (P-12 cho phép PR tự rời); cascade như EP-074 | `P-12` | OPEN | — | — | `PROJECT_MEMBER_REMOVE` | `LAST_PROJECT_MANAGER`; `NOT_A_PROJECT_MEMBER`; `PROJECT_READONLY` |
| EP-076 | GET | `/projects/{projectId}/settings` | `project_setting` (`approval_sla_hours`, `reminder_offsets_minutes`, `default_task_priority`, `timelog_edit_window_days`) và các cờ trên `project` (`approval_enabled`, `allow_member_create_task`) | `P-02` | OPEN | — | — | `PROJECT_READ` | — |
| EP-077 | PUT | `/projects/{projectId}/settings` | Cập nhật `project_setting` và `allow_member_create_task`. **SLA không bao giờ sinh quyết định tự động** (AUD-08, F-10 chỉ set `sla_breached` và notify) | `P-17` | `PJ-writable` | BB | — | `PROJECT_SETTING_UPDATE` | `PROJECT_SETTING_KEY_UNKNOWN`; `PROJECT_SETTING_VALUE_INVALID`; `SLA_AUTO_DECISION_FORBIDDEN`; `PROJECT_READONLY` |
| EP-078 | POST | `/projects/{projectId}/approval-setting` | Bật/tắt `approval_enabled` (P-15 enable / P-16 disable) kèm `reason` → ghi `approval_enabled_changed_at/by/reason`. Áp dụng cho Task **đang thực hiện**; tắt giữa chừng bị chặn khi còn round OPEN (I-22); Task COMPLETED/CANCELLED không bị đổi ngược | `P-15`, `P-16` | `PJ-writable` | BB | — | `APPROVAL_SETTING_CHANGE` | `PENDING_APPROVALS_EXIST` (409); `APPROVAL_SETTING_UNCHANGED`; `REASON_REQUIRED`; `PROJECT_READONLY` |
| EP-079 | GET | `/projects/{projectId}/special-grants` | Grant ngoại lệ scope PROJECT: `permission_code` (PROJECT_CONTENT_READ hoặc AUDIT_READ_SCOPE), `grantee_user_id`, `granted_by`, `expires_at`, `revoked_at` | `P-02` | OPEN | — | — | `PROJECT_READ` | — |
| EP-080 | POST | `/projects/{projectId}/special-grants` | Cấp `special_grant` scope PROJECT (P-23); bắt buộc `reason`, `expires_at`, `reauth_ticket_id`; không self-grant | `P-23` | `PJ-writable` | BB | Y | `SPECIAL_GRANT_ISSUE` | `SELF_ESCALATION_FORBIDDEN`; `GRANT_EXCEEDS_GRANTOR`; `GRANT_ALREADY_EXISTS`; `VALID_UNTIL_REQUIRED`; `PERMISSION_CODE_UNKNOWN`; `PROJECT_READONLY` |
| EP-081 | DELETE | `/projects/{projectId}/special-grants/{grantId}` | Thu hồi grant scope PROJECT; hiệu lực ở request kế tiếp | `P-23` | OPEN | BB | Y | `SPECIAL_GRANT_ISSUE` | `GRANT_NOT_FOUND`; `GRANT_ALREADY_REVOKED`; `GRANT_ALREADY_EXPIRED` |
| EP-082 | POST | `/admin/emergency/project-manager-restore` | **WO** (không phải SA — P-14) khôi phục MANAGER khi Project mồ côi quản lý. Chỉ restore membership/role, **không mặc nhiên đọc Task**; ghi `emergency_action_log` với `action_code=WORKSPACE_OWNER_RESTORE_MANAGER`, `content_read=false` | `P-14` | OPEN | BB | Y | `EMERGENCY_MANAGER_RESTORE` | `TARGET_NOT_WORKSPACE_MEMBER`; `TARGET_IS_GUEST`; `PROJECT_HAS_ACTIVE_MANAGER`; `REASON_TOO_SHORT`; `EMERGENCY_ACTION_CODE_UNKNOWN` |
| EP-083 | GET | `/projects/{projectId}/milestones` | Danh sách Milestone: `name`, `status` (OPEN/COMPLETED/CANCELLED), `due_at`, `due_timezone`, `sort_order`, kèm bộ đếm Task bọc `MetricEnvelope` | `P-02` | OPEN | — | — | `PROJECT_READ` | — |
| EP-084 | POST | `/projects/{projectId}/milestones` | Tạo Milestone (P-18). Milestone là **tuỳ chọn**; Task có thể không thuộc Milestone nào | `P-18` | `PJ-writable` | BB | — | `MILESTONE_CHANGE` | `MILESTONE_NAME_DUPLICATE`; `MILESTONE_NAME_INVALID`; `MILESTONE_DATE_OUTSIDE_PROJECT_RANGE`; `PROJECT_READONLY` |
| EP-085 | GET | `/milestones/{milestoneId}` | Chi tiết Milestone + `progress` theo AUD-20 + Task gắn mốc (chỉ metadata actor có quyền đọc) | `P-02` | OPEN | — | — | `PROJECT_READ` | — |
| EP-086 | PATCH | `/milestones/{milestoneId}` | Sửa `name`, `description`, `due_at`, `due_timezone`, `sort_order`. Đổi `due_at` **không** tự dời `task.due_at` | `P-18` | `PJ-writable` | TN | — | `MILESTONE_CHANGE` | `MILESTONE_NOT_FOUND`; `MILESTONE_NAME_DUPLICATE`; `MILESTONE_DATE_OUTSIDE_PROJECT_RANGE`; `PROJECT_READONLY` |
| EP-087 | POST | `/milestones/{milestoneId}/status` | Đóng hoặc huỷ Milestone **thủ công** (P-20, AUD-21): `to_status` COMPLETED hoặc CANCELLED kèm `cancel_reason`; không làm đổi trạng thái Task nào | `P-20` | `PJ-writable` | BB | — | `MILESTONE_CHANGE` | `MILESTONE_TRANSITION_FORBIDDEN`; `REASON_REQUIRED` (với CANCELLED); `MILESTONE_ALREADY_IN_STATUS`; `PROJECT_READONLY` |
| EP-088 | DELETE | `/milestones/{milestoneId}` | Xoá Milestone: **chỉ bỏ liên kết Task** (`task.milestone_id` thành NULL theo `ON DELETE SET NULL`), không xoá Task (P-19) | `P-19` | `PJ-writable` | TN | — | `MILESTONE_DELETE` | `MILESTONE_NOT_FOUND`; `PROJECT_READONLY` |
| EP-089 | GET | `/projects/{projectId}/tasks` | Task trong Project; phân trang cursor §3.1; filter §3.2; sort allowlist `due_at`, `start_at`, `priority`, `state`, `updated_at`, `title` | `TK-03` | OPEN | — | — | không audit | `FILTER_COMBINATION_INVALID`; `UNSUPPORTED_SORT_FIELD` |
| EP-090 | GET | `/projects/{projectId}/activity` | `activity_event` có `target_type=PROJECT` hoặc Task thuộc Project; quyền lọc **tại thời điểm đọc** | `FL-08` | OPEN | — | — | không audit | — |
| EP-091 | GET | `/projects/{projectId}/attachments` | `attachment` có `target_type=PROJECT`; chỉ metadata, nội dung tải qua EP-153/EP-154 | `FL-02` | OPEN | — | — | không audit | — |

### 5.5 Nhóm E — Tasks, Subtasks, Assignments, Dependencies, Checklist, Tags, Time logs (MOD-04)

| ID | Method | Path | Mô tả | Mã quyền | State gate | Idem | Reauth | Audit action | Error riêng |
|---|---|---|---|---|---|---|---|---|---|
| EP-092 | POST | `/projects/{projectId}/tasks` | Tạo Task gốc (`depth=0`): `title` (1..300), `description`, `priority` (LOW/MEDIUM/HIGH/URGENT), `start_at`, `due_at`, `due_timezone`, `due_is_date_only`, `milestone_id`. `state` khởi tạo TODO. PR chỉ tạo được khi `project.allow_member_create_task=true` | `TK-01` | `PJ-writable`, `WS=ACTIVE` | BB | — | `TASK_CREATE` (mới) | `TASK_TITLE_INVALID`; `MILESTONE_NOT_IN_PROJECT`; `DATE_RANGE_INVALID`; `DUE_BEFORE_START`; `MEMBER_CANNOT_CREATE_TASK`; `PROJECT_READONLY` |
| EP-093 | POST | `/tasks/{taskId}/subtasks` | Tạo subtask (`depth=1`, `parent_task_id` = Task cha). Subtask **không có vòng duyệt riêng** (AUD-05); không được có `milestone_id` (`ck_task_subtask_no_milestone`) và không được có dependency (DEC-09). Cha phải `depth=0` (I-07) | `TK-02` | `PJ-exec`, `exec-unlocked` | BB | — | `SUBTASK_CREATE` (mới) | `SUBTASK_MAX_DEPTH_EXCEEDED` (422); `SUBTASK_MILESTONE_FORBIDDEN`; `SUBTASK_PARENT_NOT_ALLOWED` (cha đã COMPLETED/CANCELLED); `SUBTASK_DEADLINE_AFTER_PARENT`; `EXECUTION_LOCKED_PENDING_APPROVAL` (423); `PROJECT_NOT_ACTIVE` |
| EP-094 | GET | `/tasks` | Task xuyên các Project actor tham gia; cursor §3.1; filter §3.2; sort allowlist `due_at`, `start_at`, `priority`, `state`, `updated_at`, `title`, `created_at` | `TK-03` | OPEN | — | — | không audit | `FILTER_COMBINATION_INVALID`; `UNSUPPORTED_SORT_FIELD` |
| EP-095 | GET | `/tasks/search` | Tìm kiếm `q` trên `task.search_text` / `search_tsv` và `pg_trgm` (AUD-26), kèm `comment.body_search_text` khi `include_comments=true`. Chỉ tìm trong phạm vi `TASK_READ`; đoạn highlight bị cắt theo quyền | `TK-03` | OPEN | — | — | `TASK_SEARCH` (mới) | `SEARCH_QUERY_TOO_SHORT`; `SEARCH_QUERY_TOO_BROAD` |
| EP-096 | GET | `/tasks/{taskId}` | `TaskResponse` (§6.1). Thiếu participation → `404` (P3/P4). Trường nhạy cảm (time log người khác khi actor không phải MANAGER, nội dung file) bị loại **trước khi serialize** | `TK-03` | OPEN | — | — | `TASK_READ` (mới) | — |
| EP-097 | PATCH | `/tasks/{taskId}` | Sửa nội dung và metadata. `title`/`description` theo TK-04; `priority`/`milestone_id` theo TK-05 (**chỉ MANAGER**); `start_at`/`due_at`/`due_timezone` theo TK-06. Đổi `due_at` ghi thêm `task_deadline_change` và recompute `reminder_schedule` + notify assignee | `TK-04`, `TK-05`, `TK-06` | `PJ-writable`, `exec-unlocked` | TN | — | `TASK_UPDATE` (mới) | `EXECUTION_LOCKED_PENDING_APPROVAL` (423; TK-05 cho MANAGER vẫn được khi PENDING_APPROVAL — chờ DEC-06); `TASK_TITLE_INVALID`; `MILESTONE_NOT_IN_PROJECT`; `SUBTASK_MILESTONE_FORBIDDEN`; `DUE_DATE_BEFORE_SUBTASK_DEADLINE`; `DEADLINE_CHANGE_REASON_REQUIRED`; `TASK_READONLY`; `PROJECT_READONLY` |
| EP-098 | DELETE | `/tasks/{taskId}` | Soft-delete Task; cascade theo `delete_group_id` (AUD-29) tới subtask, `checklist_item`, `task_assignment`, `task_dependency`, `task_tag`, `time_log` | `TK-13` | `PJ-writable`, `exec-unlocked` | TN | — | `TASK_DELETE` (mới) | `TASK_HAS_OPEN_APPROVAL_ROUND`; `TASK_ALREADY_DELETED`; `TASK_READONLY`; `PROJECT_READONLY` |
| EP-099 | POST | `/tasks/{taskId}/restore` | Khôi phục các dòng cùng `delete_group_id` **và** còn hợp lệ (AUD-29). **Không** tự reopen: Task COMPLETED/CANCELLED giữ nguyên trạng thái. Từ chối nếu Project cha đã readonly | `TK-14` | OPEN | BB | — | `TASK_RESTORE` (mới) | `TASK_NOT_DELETED`; `PROJECT_READONLY`; `RETENTION_WINDOW_EXPIRED`; `RESTORE_PARTIAL_IRRECOVERABLE` |
| EP-100 | POST | `/tasks/{taskId}/blocked` | Đặt `blocked_flag=true` kèm `blocked_reason` bắt buộc (`ck` đòi reason khi flag bật). `BLOCKED` là **cờ độc lập**, không phải trạng thái máy (Nhóm 04, 05 §3) | `TK-08` | `PJ-writable` | TN | — | `TASK_BLOCKED_SET` (mới) | `REASON_REQUIRED`; `ALREADY_BLOCKED`; `TASK_READONLY`; `PROJECT_READONLY` |
| EP-101 | DELETE | `/tasks/{taskId}/blocked` | `blocked_flag=false`, xoá `blocked_reason`; ghi `activity_event` (BLOCKED_CLEARED) kèm lý do gỡ | `TK-08` | `PJ-writable` | — | — | `TASK_BLOCKED_CLEAR` (mới) | `NOT_BLOCKED`; `BLOCKING_DEPENDENCY_STILL_OPEN` (409 khi còn `task_dependency` RELATED_BLOCKS chưa đóng) |
| EP-102 | GET | `/tasks/{taskId}/subtasks` | Subtask trực tiếp (`depth=1`, `parent_task_id` = Task). Vì bất biến một cấp (I-07) nên **không** có tham số `depth` ở đây | `TK-03` | OPEN | — | — | không audit | — |
| EP-103 | GET | `/tasks/{taskId}/assignments` | `task_assignment`: `user_id`, `status` (ACTIVE/REMOVED/COMPLETED_CONFIRMED), `assigned_by`, `assigned_at`, `removed_at`, `removal_reason`, `confirmed_at`. Kèm `unique_task_count` và `assignment_count` tách biệt (AUD-37) | `TK-03` | OPEN | — | — | `TASK_READ` (mới) | — |
| EP-104 | POST | `/tasks/{taskId}/assignments` | Gán người thực hiện (`uq_assignment_active`): target phải có participation Project và tài khoản ACTIVE | `TK-07` | `PJ-writable`, `exec-unlocked` | BB | — | `TASK_ASSIGNMENT_ADD` (mới) | `ASSIGNEE_NOT_PROJECT_MEMBER`; `ASSIGNMENT_ALREADY_EXISTS`; `ACCOUNT_NOT_ACTIVE`; `EXECUTION_LOCKED_PENDING_APPROVAL`; `TASK_READONLY` |
| EP-105 | DELETE | `/tasks/{taskId}/assignments/{userId}` | Gỡ người được gán → `status=REMOVED`, `removal_reason` phù hợp, giữ lịch sử. Nếu là ASG duy nhất và Task đang IN_PROGRESS → `409`, yêu cầu chuyển trạng thái trước | `TK-07` | `PJ-writable`, `exec-unlocked` | BB | — | `TASK_ASSIGNMENT_REMOVE` (mới) | `ASSIGNMENT_NOT_FOUND`; `LAST_ASSIGNEE_OF_IN_PROGRESS_TASK`; `EXECUTION_LOCKED_PENDING_APPROVAL` |
| EP-106 | DELETE | `/tasks/{taskId}/assignments/me` | ASG tự gỡ mình (TK-07 cho phép ASG tự gỡ); `reason` bắt buộc; notify MANAGER | `TK-07` | `PJ-writable` | BB | — | `TASK_ASSIGNMENT_REMOVE` (mới) | `ASSIGNMENT_NOT_FOUND`; `REASON_REQUIRED`; `LAST_ASSIGNEE_OF_IN_PROGRESS_TASK` |
| EP-107 | GET | `/tasks/{taskId}/dependencies` | Dependency hai chiều: `depends_on` (chặn Task này) và `blocks` (Task này chặn), kèm `state` hiện tại của Task liên quan | `TK-03` | OPEN | — | — | không audit | — |
| EP-108 | POST | `/tasks/{taskId}/dependencies` | Tạo `task_dependency` (`dependency_type` chỉ có RELATED_BLOCKS ở V1). Server lock **cả hai** Task theo thứ tự `id` tăng dần (RACE-07, 05 §10) rồi mới chạy `guard_dependency_acyclic` (WITH RECURSIVE) | `TK-10` | `PJ-writable`, `exec-unlocked` | BB | — | `TASK_DEPENDENCY_ADD` (mới) | `DEPENDENCY_CYCLE_DETECTED` (422); `DEPENDENCY_CROSS_PROJECT` (422); `DEPENDENCY_SELF_REFERENCE`; `DEPENDENCY_ALREADY_EXISTS` (409 theo UNIQUE); `SUBTASK_DEPENDENCY_FORBIDDEN`; `TARGET_TASK_READONLY` |
| EP-109 | DELETE | `/tasks/{taskId}/dependencies/{dependsOnTaskId}` | Gỡ dependency. Server **không** tự xoá `blocked_flag`; client được gợi ý gọi EP-101 | `TK-10` | `PJ-writable` | — | — | `TASK_DEPENDENCY_REMOVE` (mới) | `DEPENDENCY_NOT_FOUND`; `PROJECT_READONLY` |
| EP-110 | GET | `/tasks/{taskId}/checklist-items` | `checklist_item` sắp theo `position`; kèm `checked_count` / `total_count` bọc `MetricEnvelope` | `TK-03` | OPEN | — | — | không audit | — |
| EP-111 | POST | `/tasks/{taskId}/checklist-items` | Thêm mục: `title` (1..200), `position`, `assignee_user_id` tuỳ chọn | `TK-09` | `PJ-writable`, `exec-unlocked` | BB | — | `CHECKLIST_ITEM_CREATE` (mới) | `CHECKLIST_TITLE_INVALID`; `CHECKLIST_POSITION_CONFLICT` (409); `CHECKLIST_ITEM_LIMIT_EXCEEDED`; `EXECUTION_LOCKED_PENDING_APPROVAL` |
| EP-112 | PATCH | `/checklist-items/{itemId}` | Sửa `title`, `position`, `assignee_user_id` | `TK-09` | `PJ-writable`, `exec-unlocked` | TN | — | `CHECKLIST_ITEM_UPDATE` (mới) | `CHECKLIST_ITEM_NOT_FOUND`; `CHECKLIST_POSITION_CONFLICT`; `CHECKLIST_TITLE_INVALID`; `EXECUTION_LOCKED_PENDING_APPROVAL` |
| EP-113 | POST | `/checklist-items/{itemId}/check` | Đặt `checked` true/false kèm `checked_by`, `checked_at`. Tích đủ 100% **không** tự chuyển trạng thái Task | `TK-09` | `PJ-writable`, `exec-unlocked` | TN | — | `CHECKLIST_ITEM_CHECK` (mới) | `CHECKLIST_ITEM_NOT_FOUND`; `CHECKLIST_ALREADY_IN_STATE`; `EXECUTION_LOCKED_PENDING_APPROVAL` |
| EP-114 | DELETE | `/checklist-items/{itemId}` | Soft-delete mục checklist | `TK-09` | `PJ-writable`, `exec-unlocked` | — | — | `CHECKLIST_ITEM_DELETE` (mới) | `CHECKLIST_ITEM_NOT_FOUND`; `EXECUTION_LOCKED_PENDING_APPROVAL` |
| EP-115 | GET | `/tasks/{taskId}/tags` | Tag đang gắn (join `task_tag` → `tag`): `tag_id`, `name`, `color` | `TK-03` | OPEN | — | — | không audit | — |
| EP-116 | PUT | `/tasks/{taskId}/tags` | Thay toàn bộ danh sách `tag_id` (toán tử replace, tự nhiên idempotent). Tag phải thuộc Workspace cha và chưa bị soft-delete | `TK-05` | `PJ-writable`, `exec-unlocked` | TN | — | `TASK_TAGS_REPLACE` (mới) | `TAG_NOT_IN_WORKSPACE`; `TAG_NOT_FOUND`; `TAG_LIMIT_PER_TASK_EXCEEDED`; `EXECUTION_LOCKED_PENDING_APPROVAL` |
| EP-117 | GET | `/tasks/{taskId}/time-logs` | `time_log` của Task. Actor không phải MANAGER **chỉ** thấy log của chính mình (AUD-23); bản cắt do server quyết định, client không tham số hoá được | `TK-11` | OPEN | — | — | không audit | — |
| EP-118 | POST | `/tasks/{taskId}/time-logs` | Ghi giờ: `minutes` (1..1440), `work_date` (không vượt `CURRENT_DATE`), `note`. `user_id` **luôn** = actor, không nhận từ body (AUD-23) | `TK-11` | `PJ-writable`, `exec-unlocked` | BB | — | `TIME_LOG_CREATE` (mới) | `TIMELOG_DURATION_INVALID`; `TIMELOG_FUTURE_DATE`; `TIMELOG_OUTSIDE_EDIT_WINDOW`; `TIMELOG_OVERLAP_DETECTED`; `EXECUTION_LOCKED_PENDING_APPROVAL`; `TASK_READONLY` |
| EP-119 | DELETE | `/time-logs/{timeLogId}` | Xoá log giờ trong `project_setting.timelog_edit_window_days` và khi `locked_at` còn NULL. MANAGER xoá được log người khác kèm `reason` (TK-12); **không có** endpoint sửa số giờ của người khác | `TK-11`, `TK-12` | OPEN | — | — | `TIME_LOG_DELETE` (mới) | `TIMELOG_NOT_OWNED`; `TIMELOG_NOT_FOUND`; `TIMELOG_OUTSIDE_EDIT_WINDOW`; `REASON_REQUIRED` (T-12); `TIME_LOG_LOCKED_BY_SNAPSHOT` (409 khi `locked_at` đã set) |
| EP-120 | GET | `/tasks/{taskId}/attachments` | `attachment` có `target_type` là TASK hoặc SUBTASK; chỉ metadata, nội dung qua EP-153/EP-154 | `FL-02` | OPEN | — | — | không audit | — |
| EP-121 | GET | `/tasks/{taskId}/activity` | `activity_event` của Task, sort mặc định `-occurred_at`, filter `occurred_from` / `occurred_to` / `activity_type` | `FL-08` | OPEN | — | — | không audit | — |
| EP-122 | GET | `/tasks/{taskId}/workflow-events` | `workflow_event` (append-only): `event_type` (TASK_CREATED, TASK_STARTED, TASK_SUBMITTED, TASK_APPROVED, TASK_REJECTED, TASK_CANCELLED, TASK_REOPENED, TASK_STATE_CHANGED, ROUND_OPENED, ROUND_CLOSED, APPROVER_DESIGNATED, APPROVER_REVOKED, SLA_BREACHED, PROJECT_COMPLETED_CASCADE...), `from_state`, `to_state`, `actor_type` (USER / SYSTEM_RULE), `reason_code`. Không có endpoint sửa hay xoá → `405` | `TK-03` | OPEN | — | — | không audit | — |
| EP-123 | GET | `/tasks/{taskId}/deadline-changes` | `task_deadline_change`: `old_due_at`, `new_due_at`, `old_due_timezone`, `new_due_timezone`, `changed_by`, `reason`, `changed_at` | `TK-03` | OPEN | — | — | không audit | — |
| EP-124 | GET | `/tasks/{taskId}/reminders` | `reminder_rule` áp dụng cho Task và `reminder_schedule` tương ứng: `status` (SCHEDULED, FIRED, CANCELLED, PAUSED, SKIPPED_QUIET_HOURS, DEFERRED_QUIET_HOURS), `paused_reason`, `next_fire_at`. Chỉ đọc — tạo reminder chung qua EP-169 | `C-04` | OPEN | — | — | không audit | — |

### 5.6 Nhóm F — Workflow & Approval (MOD-05)

Quy ước riêng: **mọi** endpoint ghi trong nhóm này bắt buộc cả `Idempotency-Key` **và** `If-Match` (version của `task`). Mỗi hành động là **một transaction duy nhất** theo đúng 10 bước ở 04-RBAC §8.2: P1..P8 → `SELECT ... FOR UPDATE` trên `task` → `FOR UPDATE` trên `approval_round` → trigger `guard_no_self_approval` → `INSERT approval_decision` (chặn bởi `uq_decision_single_terminal`) → cập nhật round → cập nhật `task.state` và `version` → `INSERT workflow_event` → `INSERT audit_event` → `INSERT outbox_event` → COMMIT rồi mới phát thông báo. Toàn bộ invariant I-01..I-23 được kiểm lại trong transaction; thua race → `409` với mã riêng.

| ID | Method | Path | Mô tả | Mã quyền | State gate | Idem | Reauth | Audit action | Error riêng |
|---|---|---|---|---|---|---|---|---|---|
| EP-125 | POST | `/tasks/{taskId}/workflow/start` | `TODO → IN_PROGRESS` (F-01). Điều kiện P7: `isValidActiveActor`; Project **ACTIVE** (PLANNING bị chặn — Nhóm 03 "chưa thực thi", chờ xác nhận ở 04-RBAC §16); không `blocked_flag`; không còn `task_dependency` RELATED_BLOCKS chưa đóng; Task chưa soft-delete | `F-01` | `PJ-exec`, `exec-unlocked` | BB | — | `TASK_START` (mới) | `WORKFLOW_TRANSITION_NOT_ALLOWED`; `TASK_IS_BLOCKED`; `BLOCKED_BY_DEPENDENCY`; `PROJECT_NOT_ACTIVE`; `NO_ASSIGNEE`; `TASK_STATE_CHANGED` (RACE-02); `TASK_READONLY` |
| EP-126 | POST | `/tasks/{taskId}/workflow/submit` | Một endpoint, hai nhánh theo `project.approval_enabled`: **OFF** → F-02 `IN_PROGRESS → COMPLETED`; **ON** → F-03 `IN_PROGRESS → PENDING_APPROVAL`, mở `approval_round` mới (`round_no = max+1`), `submitted_by` = actor, designation mặc định `DEFAULT_MANAGER` cho MANAGER đủ điều kiện. Nếu không có approver độc lập → vẫn PENDING_APPROVAL, notify MANAGER và phát `APPROVAL_NO_APPROVER`. Điều kiện: mọi subtask thuộc COMPLETED hoặc CANCELLED (I-08) | `F-02`, `F-03` | `PJ-exec`, `exec-unlocked` | BB | — | `TASK_SUBMIT` (mới) | `SUBTASKS_INCOMPLETE` (409, liệt kê subtask chưa đóng trong `details[]`); `WORKFLOW_TRANSITION_NOT_ALLOWED`; `NOT_ASSIGNEE_OF_TASK`; `ROUND_ALREADY_OPEN`; `APPROVAL_NOT_ENABLED`; `TASK_STATE_CHANGED` |
| EP-127 | POST | `/tasks/{taskId}/workflow/approve` | APPROVE quyết định của round hiện hành (F-04). `isEligibleApprover` theo 04-RBAC §8.1. Cấm nếu actor thuộc `task_assignment(status=ACTIVE)` hoặc là `submitted_by`. Khi đủ `required_count` → round APPROVED, Task COMPLETED. Nhiều quyết định terminal bị `uq_decision_single_terminal` chặn | `F-04` | `round OPEN`, `exec-unlocked` | BB | — | `TASK_APPROVE` (mới) | `ROUND_NOT_OPEN`; `NOT_ELIGIBLE_APPROVER`; `SELF_APPROVAL_FORBIDDEN_ASSIGNEE`; `SELF_APPROVAL_FORBIDDEN_SUBMITTER`; `APPROVAL_ALREADY_DECIDED` (409, kèm `winning_decision`); `APPROVAL_NOT_ENABLED`; `TASK_STATE_CHANGED` |
| EP-128 | POST | `/tasks/{taskId}/workflow/reject` | REJECT (F-05): `reason` bắt buộc tối thiểu 3 ký tự (`ck_decision_reject_reason`); round đóng `round_outcome=REJECTED`; Task về IN_PROGRESS (hoặc TODO nếu không còn ASG). **Không** có trạng thái REQUEST_CHANGES | `F-05` | `round OPEN` | BB | — | `TASK_REJECT` (mới) | `ROUND_NOT_OPEN`; `NOT_ELIGIBLE_APPROVER`; `SELF_APPROVAL_FORBIDDEN_ASSIGNEE`; `SELF_APPROVAL_FORBIDDEN_SUBMITTER`; `REJECT_REASON_REQUIRED`; `APPROVAL_ALREADY_DECIDED`; `APPROVAL_NOT_ENABLED` |
| EP-129 | POST | `/tasks/{taskId}/workflow/cancel` | `TODO / IN_PROGRESS / PENDING_APPROVAL → CANCELLED` (F-06). Nếu đang PENDING_APPROVAL → đóng round với `round_outcome=CANCELLED` và `decision_outcome=CANCELLED`, `cancelled_reason_code=TASK_CANCELLED`; **không giả mạo** approve/reject (AUD-01). Cascade subtask chưa xong thành CANCELLED | `F-06` | `PJ-writable` | BB | — | `TASK_CANCEL` (mới) | `WORKFLOW_TRANSITION_NOT_ALLOWED`; `CANCEL_REASON_REQUIRED`; `TASK_STATE_CHANGED`; `PROJECT_READONLY` |
| EP-130 | POST | `/tasks/{taskId}/workflow/reopen` | F-07 `COMPLETED → IN_PROGRESS` (chỉ MANAGER, `reason` bắt buộc) hoặc F-08 `CANCELLED → IN_PROGRESS`. **Lịch sử approval giữ nguyên**, round cũ bất biến (I-14); vòng duyệt mới chỉ tạo ở lần submit kế tiếp. Không tự reopen subtask. Nếu Task bị cascade bởi Project COMPLETED → chỉ reopen khi Project đã về ACTIVE | `F-07`, `F-08` | `PJ-writable` | BB | — | `TASK_REOPEN` (mới) | `REOPEN_REASON_REQUIRED`; `WORKFLOW_TRANSITION_NOT_ALLOWED`; `REOPEN_WINDOW_EXPIRED`; `PROJECT_READONLY`; `TASK_HAS_IMMUTABLE_DEPENDENTS` |
| EP-131 | GET | `/tasks/{taskId}/rounds` | Lịch sử `approval_round`: `round_no`, `status` (OPEN/APPROVED/REJECTED/CANCELLED), `round_outcome`, `required_count`, `approved_count`, `rejected_count`, `submitted_by`, `opened_at`, `closed_at`, `sla_due_at`, `sla_breached`, `cancelled_reason_code` | `TK-03` | OPEN | — | — | không audit | — |
| EP-132 | GET | `/tasks/{taskId}/rounds/current` | Round OPEN hiện tại + `approval_decision` của round + cờ `no_eligible_approver` để client ẩn nút duyệt thay vì nhận `403` | `TK-03` | OPEN | — | — | không audit | `NO_OPEN_ROUND` (404) |
| EP-133 | GET | `/tasks/{taskId}/eligible-approvers` | Kết quả `isEligibleApprover` cho round hiện tại, đã loại ASG và `submitted_by`, đã loại GUEST và PG. Trả `user_id`, `display_name`, `eligibility_reason` (DEFAULT_MANAGER / DESIGNATED). Không có ai → `200` với mảng rỗng và cờ, không phải lỗi | `TK-03` | OPEN | — | — | không audit | — |
| EP-134 | POST | `/tasks/{taskId}/rounds/{roundId}/designations` | Chỉ định approver bổ sung (P-21) khi round OPEN không đủ người hợp lệ. Target phải là participant ACTIVE, khác GUEST và PG (AUD-07). Ghi `approval_designation` với `designation_type=DESIGNATED`, `designated_by`, `reason` | `P-21` | `round OPEN` | BB | — | `APPROVER_DESIGNATED` | `ROUND_NOT_OPEN`; `DESIGNATED_USER_NOT_PARTICIPANT`; `DESIGNATED_USER_IS_GUEST`; `DESIGNATED_USER_IS_SUBMITTER`; `DESIGNATED_USER_IS_ASSIGNEE`; `DESIGNATION_ALREADY_EXISTS`; `REASON_REQUIRED` |
| EP-135 | DELETE | `/tasks/{taskId}/rounds/{roundId}/designations/{userId}` | Thu hồi designation (P-22): set `revoked_at`, `revoked_by`, `revoked_reason` (LOST_MEMBERSHIP / LOST_PROJECT_ROLE / ACCOUNT_NOT_ACTIVE / MANAGER_REASSIGNED / ROUND_CLOSED). Round **vẫn giữ OPEN** và cảnh báo MANAGER. `approval_decision` đã ghi là append-only, không thu hồi được | `P-22` | `round OPEN` | BB | — | `APPROVER_REVOKED` | `DESIGNATION_NOT_FOUND`; `DESIGNATION_ALREADY_REVOKED`; `DESIGNATION_ALREADY_DECIDED` (409); `ROUND_NOT_OPEN`; `REVOCATION_REASON_CODE_UNKNOWN` |
| EP-136 | GET | `/tasks/{taskId}/decisions` | Toàn bộ `approval_decision` (append-only): `round_id`, `round_no`, `decision_outcome` (APPROVED/REJECTED/CANCELLED), `decided_by`, `decided_at`, `reason`, `actor_type`. `reason` bị che nếu actor chỉ có metadata | `TK-03` | OPEN | — | — | không audit | — |

### 5.7 Nhóm G — Comments & Activity timeline (MOD-06, MOD-07)

V1 giới hạn `comment.target_type` trong TASK và SUBTASK (02B §1.1, AUD/C-30) — **không có** comment cấp Project, Milestone hay Workspace, dù `activity_event.target_type` có hỗ trợ các loại đó. Đây là điểm hẹp hơn gợi ý nhóm `/comments` tổng quát của đặc tả §17, ghi ở §11. Reply chỉ **một cấp** (`depth` 0 hoặc 1). `comment.body` tối đa 8000 ký tự.

04-RBAC **đã có** họ mã action riêng cho Comment: **`CM-01..CM-05`** ở `04 §7.1` (bổ sung theo AUD-53a), tách khỏi `C-01..C-06` là **Calendar** ở `04 §10`. Tài liệu này dùng đúng dãy đó, không đặt ký hiệu riêng nữa. **Đọc** comment không có mã riêng — đi kèm `TK-03 TASK_READ` và vẫn qua P3/P4 (không participation → `404`). Hai điểm khác biệt so với bản draft trước của chính file này, nay đã sửa để khớp `04 §7.1`: (i) **không có** mã nào cho phép **sửa** comment của người khác (bất biến (1) của `04 §7.1`), nên `EP-140` chỉ còn `CM-03` cho tác giả; (ii) moderation **không** mở cho Workspace Owner — `WO` không có quyền nội dung trên Project mình không tham gia (`04 §12`), chỉ `PM` của Project đó (`CM-05`).

| ID | Method | Path | Mô tả | Mã quyền | State gate | Idem | Reauth | Audit action | Error riêng |
|---|---|---|---|---|---|---|---|---|---|
| EP-137 | GET | `/tasks/{taskId}/comments` | Comment và reply của Task, sort tăng dần `created_at`, cursor; hỗ trợ `q` trên `body_search_text`. Comment đã xoá vẫn hiện khung với `is_deleted_content=true` và `author_label` để giữ mạch hội thoại | `TK-03` | OPEN | — | — | không audit | — |
| EP-138 | POST | `/tasks/{taskId}/comments` | Đăng comment (`target_type` TASK hoặc SUBTASK, `body` 1..8000). Được phép cả khi Task PENDING_APPROVAL (AUD-06 xếp comment vào nhóm vẫn cho phép) nhưng **bắt buộc** ghi `audit_event` với `sensitivity=SENSITIVE_CONTENT`; chốt cuối chờ DEC-06 | `CM-01` | `PJ-writable` | BB | — | `COMMENT_CREATE` (mới) | `COMMENT_BODY_REQUIRED`; `COMMENT_BODY_TOO_LONG`; `TARGET_TYPE_NOT_SUPPORTED`; `PROJECT_READONLY`; `COMMENTING_DISABLED_FOR_GUEST` |
| EP-139 | POST | `/comments/{commentId}/replies` | Trả lời một comment gốc (`depth=0`). Không cho phép reply của reply | `CM-02` | `PJ-writable` | BB | — | `COMMENT_CREATE` (mới) | `PARENT_COMMENT_NOT_FOUND`; `REPLY_DEPTH_EXCEEDED` (422); `PARENT_COMMENT_DELETED`; `PARENT_COMMENT_OTHER_TARGET`; `COMMENT_BODY_REQUIRED` |
| EP-140 | PATCH | `/comments/{commentId}` | Sửa comment **của chính mình** (`CM-03 COMMENT_EDIT_OWN`): `body` mới 1..8000; ghi `edited_at`, `edited_by` = actor, `activity_event(COMMENT_EDITED)` và `audit_event`. **Không có đường nào sửa comment của người khác** — `04 §7.1` bất biến (1); moderation đi qua `EP-141` (xoá có lý do). Comment đã `is_deleted_content=true` thì không sửa được | `CM-03` | `PJ-writable` | TN | — | `COMMENT_UPDATE` (mới) | `COMMENT_NOT_FOUND`; `COMMENT_FORBIDDEN_NOT_AUTHOR` (403 khi actor ≠ tác giả); `COMMENT_NOT_EDITABLE`; `COMMENT_BODY_REQUIRED`; `COMMENT_BODY_TOO_LONG`; `COMMENT_IS_DELETED_CONTENT` |
| EP-141 | DELETE | `/comments/{commentId}` | Set `is_deleted_content=true` và xoá `body` (giữ khung), **không** xoá vật lý, để giữ `activity_event` và audit. Hai nhánh: tác giả tự xoá (`CM-04 COMMENT_DELETE_OWN`); **PM của Project đó** xoá của người khác (`CM-05 COMMENT_DELETE_OTHER`) kèm `reason` bắt buộc + `audit_event(sensitivity=SENSITIVE_CONTENT)`. **WO/WA/SA không có quyền này** trên Project mình không tham gia (`04 §12`, `04 §7.1` bất biến (3)); reply con không tự xoá | `CM-04`, `CM-05` | OPEN | — | — | `COMMENT_DELETE` (mới) | `COMMENT_NOT_FOUND`; `COMMENT_FORBIDDEN_NOT_AUTHOR` (403 khi không phải tác giả **và** không phải PM); `EDIT_REASON_REQUIRED_FOR_MODERATION` (422 khi PM xoá của người khác mà thiếu `reason`); `COMMENT_ALREADY_DELETED` |
| EP-142 | GET | `/workspaces/{workspaceId}/activity` | Dòng thời gian cấp Workspace từ `activity_event` (`target_type` hỗ trợ WORKSPACE, PROJECT, TASK, MILESTONE, COMMENT, ATTACHMENT, SUBTASK), filter `project_id`, `actor_id`, `activity_type`, `occurred_from`, `occurred_to`, `q`. Quyền lọc **tại thời điểm đọc**: actor chỉ nhận sự kiện thuộc Project mình còn quyền thấy | `FL-08` | OPEN | — | — | không audit | `ACTIVITY_SCOPE_TOO_BROAD` (422 khi actor không phải WO/WA mà thiếu `project_id`) |

### 5.8 Nhóm H — Files & Attachments (MOD-08, ARCH-07)

Nguyên tắc bắt buộc cho toàn bộ nhóm:

1. **Không có presigned URL công khai, không trả URL S3/MinIO, không có đường dẫn tĩnh.** Mọi byte đi qua Backend gateway (ARCH-07). Response không bao giờ chứa `file_object.bucket`, `file_object.object_key`, endpoint nội bộ hay bất kỳ URL nào truy cập trực tiếp được.
2. Quyền được **kiểm tra lại trên từng chunk upload và trên từng request tải** (FL-02: "kiểm tra tại mỗi request"). Không có khái niệm cấp quyền một lần cho cả phiên.
3. `file_object.scan_status` nhận PENDING / CLEAN / INFECTED / UNSUPPORTED / ERROR. Khi `file.scan_enabled=false` (seed 02B §7.1) giá trị là UNSUPPORTED — client **không** được hiển thị UNSUPPORTED như "đã quét an toàn".
4. `StreamTicket` là cấu trúc **nội bộ**: single-use, TTL tối đa 30 giây, không bao giờ xuất hiện trong response body hay query string do client kiểm soát. Hết TTL hoặc dùng lại → `403 STREAM_TICKET_INVALID`.
5. Mọi lần truy cập nội dung, **kể cả bị từ chối**, ghi `file_access_event` với `access_type` (PREVIEW / DOWNLOAD / AI_READ / RESTORE) và `decision` (ALLOWED / DENIED_NOT_FOUND / DENIED_FORBIDDEN / DENIED_STATE_LOCKED / DENIED_REVOKED) cùng `reason_code`.
6. Hành vi cắt luồng đang truyền khi session bị thu hồi giữa chừng (RACE-09) chưa được chốt — DEC-51 đề xuất ở 05 §12. Xem §11.
7. Dọn object mồ côi và đối soát dung lượng do job nền `FILE_CLEANUP_ORPHAN` và `STORAGE_RECONCILE` đảm trách; **không** có endpoint HTTP cho hai việc này.
8. Task PENDING_APPROVAL khoá cả thao tác file trên Task đó (AUD-06), kể cả khi thao tác đi vòng qua file cấp Project, file Milestone hay attachment trong comment.

| ID | Method | Path | Mô tả | Mã quyền | State gate | Idem | Reauth | Audit action | Error riêng |
|---|---|---|---|---|---|---|---|---|---|
| EP-143 | POST | `/files/upload-sessions` | Khởi tạo phiên upload chunk: `target_type` (TASK / SUBTASK / PROJECT / COMMENT / MILESTONE / AVATAR / REPORT_EXPORT), `target_id`, `client_filename`, `declared_content_type`, `declared_size`. Trả `upload_session_id` (status INITIATED), `chunk_bytes` **do server chốt**, `expires_at`, `max_size_bytes` | `FL-01` | `PJ-writable`, `exec-unlocked` | BB | — | `FILE_UPLOAD_INITIATE` (mới) | `FILE_TOO_LARGE` (413, ngưỡng `file.max_upload_mb`); `FILE_TYPE_NOT_ALLOWED`; `TARGET_NOT_FOUND` (404); `UPLOAD_QUOTA_DAILY_EXCEEDED`; `CONCURRENT_UPLOAD_LIMIT_EXCEEDED`; `EXECUTION_LOCKED_PENDING_APPROVAL` |
| EP-144 | GET | `/files/upload-sessions/{uploadSessionId}` | Trạng thái phiên: `status` (INITIATED / UPLOADING / SCAN_PENDING / COMMITTED / FAILED / CLEANED_UP), `received_bytes`, `next_offset`, `declared_size`, `detected_content_type`, `expires_at`, `error_detail`. Chỉ người khởi tạo đọc được; người khác → `404` | `FL-01` | OPEN | — | — | không audit | `UPLOAD_SESSION_EXPIRED` (410); `UPLOAD_SESSION_NOT_OWNED` → trả `404` (§1.4) |
| EP-145 | PUT | `/files/upload-sessions/{uploadSessionId}/bytes` | Gửi một chunk. `Content-Type: application/octet-stream`, header `Content-Range: bytes <start>-<end>/<total>`; body là byte thô, **không** phải JSON. `start` phải đúng bằng `next_offset` hiện hành. Quyền và trạng thái đối tượng đích được kiểm lại **trên mỗi chunk** | `FL-01` | `PJ-writable`, `exec-unlocked` | — | — | không audit (chỉ audit ở commit) | `CHUNK_OFFSET_MISMATCH` (409); `CHUNK_TOO_LARGE` (413); `CONTENT_RANGE_INVALID` (400); `UPLOAD_SESSION_EXPIRED` (410); `UPLOAD_SESSION_STATE_INVALID` (409); `TARGET_DELETED`; `EXECUTION_LOCKED_PENDING_APPROVAL` (423) |
| EP-146 | POST | `/files/upload-sessions/{uploadSessionId}/commit` | Hoàn tất: đối chiếu tổng byte và `sha256`, tạo `file_object` (status COMMITTED) + `attachment` + `file_version` (`version_no=1`) trong một transaction, ghi `audit_event` và `outbox_event`; nếu bật quét thì chuyển SCAN_PENDING và phát job `FILE_SCAN` | `FL-01` | `PJ-writable`, `exec-unlocked` | BB | — | `FILE_UPLOAD_COMMIT` (mới) | `UPLOAD_INCOMPLETE` (422, trả `received_bytes` và `declared_size`); `CHECKSUM_MISMATCH` (422); `UPLOAD_SESSION_STATE_INVALID`; `UPLOAD_SESSION_EXPIRED`; `FILE_NAME_DUPLICATE_IN_TARGET`; `TARGET_DELETED` |
| EP-147 | DELETE | `/files/upload-sessions/{uploadSessionId}` | Huỷ phiên (status CLEANED_UP); staging object do job `FILE_CLEANUP_ORPHAN` dọn | `FL-01` | OPEN | — | — | `FILE_UPLOAD_ABORT` (mới) | `UPLOAD_SESSION_NOT_FOUND`; `UPLOAD_SESSION_STATE_INVALID` (đã COMMITTED) |
| EP-148 | GET | `/attachments` | Liệt kê attachment theo filter `target_type`, `target_id`, `workspace_id`, `project_id`, `uploaded_by`, `uploaded_after`, `content_type`, `scan_status`, `status`, `include_deleted`. Chỉ metadata, không kèm URL nội dung | `FL-02` | OPEN | — | — | không audit | `FILTER_COMBINATION_INVALID` |
| EP-149 | GET | `/attachments/{attachmentId}` | `AttachmentResponse` (§6.8): metadata + `current_version_no` + `version_count` + `scan_status` + `size_bytes`. **Không** kèm `bucket`, `object_key` hay URL | `FL-02` | OPEN | — | — | `FILE_METADATA_READ` (mới) | `ATTACHMENT_NOT_AVAILABLE` (409 khi `status` không phải AVAILABLE và client yêu cầu bản xem được) |
| EP-150 | GET | `/attachments/{attachmentId}/versions` | Lịch sử `file_version`: `version_no`, `size_bytes`, `sha256`, `uploaded_by`, `uploaded_at`, `is_current`, `change_note` | `FL-02` | OPEN | — | — | `FILE_VERSION_LIST` (mới) | — |
| EP-151 | POST | `/attachments/{attachmentId}/versions` | Tải phiên bản mới cho cùng attachment: mở `upload_session` với `target_type` giữ nguyên và `attachment_id` đích; commit sẽ tăng `version_no` và đặt bản cũ `is_current=false`. **Không ghi đè** object cũ (FL-05) | `FL-05` | `PJ-writable`, `exec-unlocked` | BB | — | `FILE_VERSION_ADD` (mới) | `FILE_TOO_LARGE`; `FILE_TYPE_NOT_ALLOWED`; `ATTACHMENT_DELETED`; `VERSION_UPLOAD_IN_PROGRESS` (409); `CONTENT_TYPE_MISMATCH_WITH_ORIGINAL`; `EXECUTION_LOCKED_PENDING_APPROVAL` |
| EP-152 | POST | `/attachments/{attachmentId}/versions/{versionNo}/restore` | Phục hồi phiên bản cũ bằng cách **tạo phiên bản mới** trỏ tới object cũ (FL-06); không xoá hay đánh tráo lịch sử | `FL-06` | `PJ-writable` | BB | — | `FILE_VERSION_RESTORE` (mới) | `VERSION_NOT_FOUND`; `VERSION_IS_CURRENT` (422); `ATTACHMENT_DELETED`; `CHANGE_NOTE_REQUIRED`; `FILE_OBJECT_PURGED` (410) |
| EP-153 | GET | `/attachments/{attachmentId}/content` | Tải nội dung: Backend stream byte từ Object Storage về client. `Content-Disposition: attachment`, `Content-Type` theo `file_object.content_type`, `ETag` theo `sha256`. Query `version_no` tuỳ chọn (mặc định bản current). Ghi `file_access_event(access_type=DOWNLOAD)` | `FL-02` | OPEN | — | — | `FILE_DOWNLOAD` (mới) | `ATTACHMENT_NOT_AVAILABLE`; `FILE_SCAN_BLOCKED` (423 khi QUARANTINED/BLOCKED); `FILE_OBJECT_PURGED` (410); `STREAM_TICKET_INVALID`; `DOWNLOAD_QUOTA_EXCEEDED` |
| EP-154 | GET | `/attachments/{attachmentId}/preview` | Xem trước inline (ảnh, PDF, text) với `Content-Disposition: inline`. Chỉ phục vụ `content_type` trong allowlist preview; loại khác → `415`. Ghi `file_access_event(access_type=PREVIEW)` | `FL-02` | OPEN | — | — | `FILE_PREVIEW` (mới) | `PREVIEW_NOT_SUPPORTED_FOR_TYPE` (415); `PREVIEW_SIZE_LIMIT_EXCEEDED` (413); `ATTACHMENT_NOT_AVAILABLE`; `FILE_SCAN_BLOCKED`; `STREAM_TICKET_INVALID` |
| EP-155 | DELETE | `/attachments/{attachmentId}` | Soft-delete attachment (`status` thành DELETED); object vật lý do job dọn theo `retention_policy` | `FL-03` | `PJ-writable`, `exec-unlocked` | — | — | `FILE_DELETE` (mới) | `ATTACHMENT_ALREADY_DELETED`; `ATTACHMENT_IS_CURRENT_AVATAR` (422 — phải đổi `app_user.avatar_attachment_id` trước); `EXECUTION_LOCKED_PENDING_APPROVAL` |
| EP-156 | POST | `/attachments/{attachmentId}/restore` | Khôi phục attachment soft-delete theo `delete_group_id`, chỉ khi đối tượng cha còn hợp lệ và object chưa bị purge | `FL-04` | `PJ-writable` | BB | — | `FILE_RESTORE` (mới) | `ATTACHMENT_NOT_DELETED`; `FILE_OBJECT_PURGED` (410); `RETENTION_WINDOW_EXPIRED`; `TARGET_DELETED`; `EXECUTION_LOCKED_PENDING_APPROVAL` |
| EP-157 | GET | `/attachments/{attachmentId}/access-events` | `file_access_event` của attachment, **gồm cả** các lần bị từ chối. Chỉ MANAGER của Project, WO trong Workspace, hoặc người có grant `AUDIT_READ_SCOPE`; người khác (kể cả SA không có grant) → `404` | `FL-09` | OPEN | — | Y (khi dùng grant) | `AUDIT_READ` | `AUDIT_NO_PERMISSION` → trả `404` (§1.4) |
| EP-158 | GET | `/files/quota` | Hạn mức của chính actor: `daily_bytes_used`, `daily_bytes_limit`, `upload_count_today`, `reset_at`. **Không** trả dữ liệu hạn mức của người khác | `FL-01` | OPEN | — | — | không audit | — |

### 5.9 Nhóm I — Calendar & Reminders (MOD-09)

| ID | Method | Path | Mô tả | Mã quyền | State gate | Idem | Reauth | Audit action | Error riêng |
|---|---|---|---|---|---|---|---|---|---|
| EP-159 | GET | `/calendar/views/day` | Việc trong một ngày theo `timezone` của actor (`date=YYYY-MM-DD`), gom theo Task và Milestone, kèm `due_at`, `due_is_date_only`, cờ `overdue`. Phạm vi mặc định là cá nhân (C-01: Task mình là ASG/CRT/MANAGER) | `C-01` | OPEN | — | — | không audit | `DATE_PARAM_INVALID`; `TIMEZONE_UNKNOWN` |
| EP-160 | GET | `/calendar/views/week` | Khung tuần (`week_start_date`); ngày đầu tuần lấy từ `app_user.first_day_of_week` (0 = Chủ nhật) | `C-01` | OPEN | — | — | không audit | `DATE_PARAM_INVALID` |
| EP-161 | GET | `/calendar/views/month` | Lưới tháng (`month=YYYY-MM`), gồm cả ngày giáp ranh tháng kế; mỗi ô kèm bộ đếm bọc `MetricEnvelope`. Phạm vi Project (C-02) khi truyền `project_id` | `C-02` | OPEN | — | — | không audit | `MONTH_PARAM_INVALID`; `RESULT_TOO_LARGE_SUGGEST_LIST_VIEW` (422) |
| EP-162 | GET | `/calendar/views/list` | Dạng danh sách phân trang cursor; `scope` (mine / assigned / project / workspace), `project_id`, `workspace_id`, `state`, `due_before`, `due_after`, `start_before`, `start_after`. `scope=workspace` áp dụng C-03: **chỉ** tổng hợp Project mà người xem có participation, không tiết lộ Project ẩn (AUD-38 → `PARTIAL`) | `C-03` | OPEN | — | — | không audit | `SCOPE_PARAM_INVALID`; `ACTIVITY_SCOPE_TOO_BROAD` |
| EP-163 | GET | `/calendar/unscheduled` | Task actor có quyền thấy mà `due_at IS NULL AND due_date_only IS NULL` (Nhóm 06: "Task không có cả hai nằm trong danh sách chưa xếp lịch") | `C-01` | OPEN | — | — | không audit | — |
| EP-164 | GET | `/calendar/overdue` | Task quá hạn và chưa đóng: `due_at < now()` và `state` không thuộc COMPLETED/CANCELLED; sort theo mức quá hạn giảm dần. Kèm `overdue_pending_approval` tách riêng (02B §3.4) | `C-01` | OPEN | — | — | không audit | — |
| EP-165 | GET | `/calendar/ics` | Xuất ICS để đăng ký vào client lịch ngoài. `scope_type` (PERSONAL / PROJECT / WORKSPACE), `scope_id`, `date_from`, `date_to`. Nội dung ở `redaction_level=MINIMAL` (mặc định): chỉ `title` Task và thời gian, **không** mô tả, không comment, không attachment. Ghi `calendar_export_record` kèm `ics_hash`, `ip_address`, `audit_event_id` | `C-06` | OPEN | — | — | `CALENDAR_ICS_EXPORT` (mới) | `ICS_RANGE_TOO_LARGE` (422, tối đa 12 tháng); `DATE_RANGE_INVALID`; `ICS_EXPORT_DISABLED_BY_WORKSPACE`; `CALENDAR_EXPORT_RATE_EXCEEDED`; `SCOPE_PARAM_INVALID` |
| EP-166 | GET | `/reminders/preferences` | Cấu hình nhắc của chính actor, đọc từ `user_profile_preference` (`02A §2.9`): `default_reminder_offsets`, `quiet_hours_enabled`, `quiet_hours_start`, `quiet_hours_end`, `overdue_reminder_policy` (ONCE / DAILY / WEEKLY / OFF), `calendar_default_view`, `first_day_of_week`, `digest_enabled`; kèm `timezone` đọc từ `app_user` — **chỉ đọc** ở đây, đổi timezone đi qua `EP-014` / `A-10` (AUD-51 / DEC-52). Cờ kênh in-app/push/email và `muted` **không** có ở response này: nguồn thật là `EP-174` (`notification_preference`, `02B §2.2`) — tránh hai nguồn sự thật | `C-04` | OPEN | — | — | không audit | — |
| EP-167 | PUT | `/reminders/preferences` | Ghi đè cấu hình nhắc cá nhân. Body **chỉ** nhận các cột của `user_profile_preference`: `default_reminder_offsets` (mảng ≤5 phần tử, `minutes` 0..10080 — `ck_reminder_offsets_array`), `quiet_hours_enabled` + `quiet_hours_start`/`quiet_hours_end` (kiểu `time`, giờ **địa phương** theo `app_user.timezone`; `ck_quiet_hours_pair` đòi đủ hai đầu mút khi bật; khoảng vắt qua nửa đêm do worker MOD-09 xử lý), `overdue_reminder_policy`, `calendar_default_view`, `first_day_of_week`, `digest_enabled`. **Không** nhận `timezone`/`locale` (thuộc `app_user` → `A-10`) và **không** nhận cờ kênh hay `muted` (thuộc `notification_preference` → `EP-175` / `N-03`). Quiet hours chỉ trì hoãn thông báo thường; `SECURITY_CRITICAL` vẫn gửi ngay (Nhóm 06) — ràng buộc đó nằm ở MOD-09, không phải ở endpoint này | `C-04` | OPEN | TN | — | `REMINDER_PREFERENCE_UPDATE` (mới) | `QUIET_HOURS_INVALID`; `OVERDUE_POLICY_UNKNOWN`; `REMINDER_OFFSET_INVALID`; `CALENDAR_VIEW_UNKNOWN` (422) |
| EP-168 | GET | `/reminders/rules` | `reminder_rule` áp dụng cho actor theo `scope_type` (USER_DEFAULT / WORKSPACE / PROJECT / TASK / MILESTONE) và `event_type` (DEADLINE / START_DATE / APPROVAL_PENDING / OVERDUE); kèm `offset_minutes`, `channels`, `enabled`, `next_fire_at` và `delivery_status` lần gần nhất | `C-04` | OPEN | — | — | không audit | — |
| EP-169 | POST | `/tasks/{taskId}/reminders/shared` | Tạo nhắc **chung** cho Task (C-05, chỉ MANAGER). Người nhận do server suy ra = ASG ACTIVE + MANAGER của Project (AUD-24); client **không** truyền `recipient_ids`. `dedup_key` duy nhất theo (task, event_type, offset_minutes) | `C-05` | `PJ-writable` | BB | — | `REMINDER_RULE_CREATE_SHARED` (mới) | `REMINDER_OFFSET_INVALID`; `REMINDER_DUPLICATE_RULE` (409); `REMINDER_BEFORE_TASK_START`; `EVENT_TYPE_UNKNOWN`; `TASK_READONLY`; `PROJECT_READONLY` |
| EP-170 | DELETE | `/reminders/rules/{ruleId}` | Xoá rule. Chỉ xoá được rule do mình tạo, hoặc MANAGER/WO xoá rule cấp Task/Project trong scope. `reminder_schedule` chưa phát (SCHEDULED) bị huỷ trong cùng transaction; rule hệ thống không xoá được | `C-05` | OPEN | — | — | `REMINDER_RULE_DELETE` (mới) | `REMINDER_RULE_NOT_FOUND`; `REMINDER_RULE_NOT_OWNED`; `REMINDER_RULE_SYSTEM_IMMUTABLE` (422) |

### 5.10 Nhóm J — Notifications & Push registration (MOD-07)

| ID | Method | Path | Mô tả | Mã quyền | State gate | Idem | Reauth | Audit action | Error riêng |
|---|---|---|---|---|---|---|---|---|---|
| EP-171 | GET | `/notifications` | Hộp thông báo của chính actor (N-01), cursor theo `seq` giảm dần; filter `type`, `severity` (NORMAL / IMPORTANT / SECURITY_CRITICAL), `read`, `redacted`, `scope_type`, `scope_id`, `q`. Thông báo đã redact vẫn hiện với `redacted=true`, `payload=null`, `deep_link=null` (AUD-13) | `N-01` | OPEN | — | — | không audit | `SINCE_SEQ_TOO_OLD` (410, ngoài cửa sổ retention — yêu cầu đồng bộ lại toàn bộ) |
| EP-172 | POST | `/notifications/{notificationId}/read` | Đánh dấu đã đọc: set `read_at`. Trạng thái đọc **đồng bộ cấp tài khoản** (Nhóm 06) nên mọi thiết bị thấy cùng một trạng thái | `N-01` | OPEN | TN | — | không audit | `NOTIFICATION_NOT_OWNED` → trả `404` (§1.4) |
| EP-173 | POST | `/notifications/read-all` | Đánh dấu đã đọc hàng loạt. Thực thi đúng RACE-14: `UPDATE ... WHERE user_id = $me AND read_at IS NULL AND redacted = false AND seq <= $max_seq`. Trả `updated_count` thật, không ước lượng | `N-01` | OPEN | BB | — | không audit | `READ_ALL_CURSOR_REQUIRED` (400 — bắt buộc `max_seq` để không đọc nhầm thông báo vừa đến) |
| EP-174 | GET | `/notifications/preferences` | `notification_preference` (`02B §2.2`) theo `type` (hoặc `*`): `channel_in_app`, `channel_push`, `channel_email`, `muted`. **Không** kèm quiet hours / timezone / `overdue_reminder_policy` — ba thứ đó thuộc `user_profile_preference` và `app_user`, đọc qua `EP-166` (AUD-51 / DEC-52) | `N-01` | OPEN | — | — | không audit | — |
| EP-175 | PUT | `/notifications/preferences` | Ghi đè `muted` + ba cờ kênh cho một `type` (`N-03 NOTIFICATION_PREFERENCE_SET_SELF`). Với loại có `severity = SECURITY_CRITICAL` (`SECURITY_ALERT`, `SESSION_REVOKED`, `APPROVAL_REQUESTED`): request **được chấp nhận** nhưng trigger bỏ qua phần tắt — Nhóm 06 "thông báo bảo mật quan trọng vẫn gửi" (`04 §10` N-03). Vì vậy **không** trả `422`; response là dòng **sau khi áp**, nên client thấy `muted=false`/kênh vẫn bật và không cần logic riêng. Không nhận quiet hours/timezone (dùng `EP-167`) | `N-03` | OPEN | TN | — | `NOTIFICATION_PREFERENCE_UPDATE` (mới) | `PREFERENCE_TYPE_UNKNOWN` |
| EP-176 | GET | `/notifications/devices` | `device_push_registration` của chính actor: `device_label`, `platform` (ANDROID_FCM / IOS_APNS), `app_version`, `locale`, `status` (ACTIVE / UNREGISTERED / INVALID), `registered_at`, `last_success_at`, `failure_count`, `session_id`. Chỉ trả `token_hint` đã che, **không** trả `push_token_hash` | `N-02` | OPEN | — | — | không audit | — |
| EP-177 | POST | `/notifications/devices` | Đăng ký thiết bị nhận push. Bản ghi **gắn chặt `session_id`** với `ON DELETE CASCADE` (Nhóm 01: huỷ đăng ký push gắn phiên cũ) nên phiên bị thu hồi là registration vô hiệu ngay, không cần thao tác thủ công. `push_token_hash` UNIQUE | `N-02` | OPEN | BB | — | `PUSH_DEVICE_REGISTER` (mới) | `PUSH_TOKEN_INVALID`; `PUSH_PLATFORM_UNSUPPORTED`; `DEVICE_REGISTRATION_LIMIT_EXCEEDED`; `PUSH_TOKEN_ALREADY_REGISTERED_OTHER_USER` (409 — từ chối và ghi `security_event`) |
| EP-178 | DELETE | `/notifications/devices/{registrationId}` | Huỷ đăng ký → `status` UNREGISTERED, `unregistered_at` | `N-02` | OPEN | — | — | `PUSH_DEVICE_UNREGISTER` (mới) | `DEVICE_REGISTRATION_NOT_OWNED` → trả `404`; `DEVICE_REGISTRATION_ALREADY_UNREGISTERED` |
| EP-179 | GET | `/notifications/resync` | Đồng bộ lại sau khi mất kết nối: `?last_seen_seq=N` trả mọi notification có `seq > N` (kể cả đã redacted) và cập nhật `realtime_cursor(session_id)`. **Quyền được kiểm tra lại tại thời điểm gọi**, không dùng quyền lúc phát sự kiện (02B §2.5, test 19.2) | `N-01` | OPEN | — | — | không audit | `SINCE_SEQ_TOO_OLD` (410); `RESYNC_WINDOW_EXCEEDED` (422 — yêu cầu chia nhỏ); `SEQ_PARAM_INVALID` |

### 5.11 Nhóm K — Realtime (MOD-07, ARCH-09)

| ID | Method | Path | Mô tả | Mã quyền | State gate | Idem | Reauth | Audit action | Error riêng |
|---|---|---|---|---|---|---|---|---|---|
| EP-180 | GET (Upgrade) | `/realtime` | Nâng cấp HTTP lên WebSocket. Query bắt buộc `token` (opaque access token của phiên đang ACTIVE); query tuỳ chọn `last_seen_seq`. Subprotocol `v1.stm.realtime`. **Một phiên chỉ một kết nối**: kết nối thứ hai trên cùng `session_id` làm kết nối cũ bị đóng bằng close code riêng. Chi tiết đầy đủ ở §7 | `—` | OPEN | — | — | `REALTIME_CONNECT` / `REALTIME_DISCONNECT` (mới; không audit từng message) | Trước khi upgrade: `401 INVALID_SESSION`, `423 ACCOUNT_LOCKED`, `403 ACCOUNT_NOT_ACTIVE`, `429 RATE_LIMITED`. Sau khi upgrade: close code `4401`, `4403`, `4408`, `4429`, `4000` (§7.6) |

### 5.12 Nhóm L — Reports, Dashboards, Metrics (MOD-10)

Nguyên tắc bắt buộc cho toàn bộ nhóm:

1. **Không tồn tại endpoint xếp hạng/điểm năng suất cá nhân.** `R-07 PRODUCTIVITY_RANKING_INDIVIDUAL` là action **không tồn tại** (04-RBAC §10, Nhóm 08). Không có đường dẫn nào dạng `/rankings`, `/leaderboard`, `/productivity-score` — kể cả cho SA, WO hay PM.
2. Mọi số liệu trả về đều bọc trong `MetricEnvelope` (§6.7) và mang `value_state`. **Ba giá trị 0, `N/A` và "không có quyền" là ba thông tin khác nhau** (AUD-38): `0` → `OK` + `value_int=0`; chia cho 0 → `NOT_APPLICABLE`; actor không được đọc scope → `NO_PERMISSION` (không phải 404, vì đây là một ô trong dashboard, không phải một tài nguyên); thiếu dữ liệu do snapshot chưa chạy → `INSUFFICIENT_DATA`; scope bị cắt vì chỉ tính trên Project có participation → `PARTIAL`.
3. `ASSIGNED`, `CREATED`, `MANAGED` là **ba tập tách biệt có thể giao nhau**; không bao giờ trả metric "tổng task của tôi" (AUD-37, `PERSONAL_*` trong metric dictionary 02B §3.4). `TASK_ASSIGNMENT_COUNT` **luôn** đi kèm `TASK_UNIQUE_TOTAL` vì một Task nhiều người nhận sẽ đếm nhiều lần.
4. `PROGRESS_PERCENT` luôn trả kèm `numerator` và `denominator` (AUD-20). Khi `denominator = 0` → `NOT_APPLICABLE`, UI hiện `N/A`, **không** hiện `0%` và **không** hiện `100%`.
5. Dashboard Workspace chỉ tổng hợp trên Project mà người xem có participation; không tiết lộ **số lượng** Project bị ẩn (AUD-38) — không trả `hidden_project_count`, không trả `total_project_count`, chỉ trả `included_project_count` + `value_state=PARTIAL`.
6. Dashboard System (SA) chỉ có số liệu vận hành/tài khoản/AI; **không** chứa nội dung Task riêng tư, không chứa `task.title`, không chứa bình luận (Nhóm 10, 04-RBAC §12).
7. Số liệu quá khứ chỉ đọc từ `metric_daily_snapshot` (append-only, `uq_snapshot_point`): ngày đã chốt là bất biến. Không endpoint nào tính lại lịch sử theo yêu cầu — `MANUAL_RECOMPUTE` là job nội bộ.
8. Export là **job bất đồng bộ** qua `background_job(job_type='REPORT_EXPORT')` + `report_export_job`. Quyền được kiểm tra **hai lần**: `authorized_at_generation` và `authorized_at_delivery` (AUD-16, NFR-24). TTL mặc định 24h (`report.export_ttl_hours`).
9. `EP-192` là **ngoại lệ duy nhất có chủ đích** của quy tắc 404 (§1.4): tải export do chính actor yêu cầu mà quyền đã bị thu hồi trả `403 PERMISSION_REVOKED_AT_DELIVERY`, vì actor đã biết job là của mình — 404 ở đây không bảo vệ được thông tin nào mà còn phá khả năng giải thích cho người dùng.

| ID | Method | Path | Mô tả | Mã quyền | State gate | Idem | Reauth | Audit action | Error riêng |
|---|---|---|---|---|---|---|---|---|---|
| EP-181 | GET | `/dashboards/personal` | Dashboard của chính actor. Query: `period_from`, `period_to`, `metric_codes`. Trả ba khối tách rời `assigned` / `created` / `managed` (không cộng trùng), mỗi khối là danh sách `MetricEnvelope`; kèm `APPROVAL_PENDING_COUNT` của các round actor là APR, `TASK_OVERDUE_COUNT`, `TASK_BLOCKED_COUNT` | `R-01` | OPEN | — | — | không audit (đọc tổng hợp, không phải nội dung riêng lẻ) | `METRIC_CODE_UNKNOWN`; `PERIOD_TOO_WIDE` (422, giới hạn theo `report.max_period_days`); `PERIOD_INVALID` (`period_from > period_to`) |
| EP-182 | GET | `/dashboards/projects/{projectId}` | Dashboard một Project. PG chỉ nhận số liệu tổng, **không** drill-down nội dung (R-02) → response có `drill_down_allowed=false` và các mảng chi tiết bị lược bỏ, không trả `null` mập mờ | `R-02` | OPEN (Project ARCHIVED_READONLY vẫn đọc được dashboard, giá trị lấy từ snapshot) | — | — | không audit | `PROJECT_NOT_FOUND` (404 khi không có participation — P3) |
| EP-183 | GET | `/dashboards/workspaces/{workspaceId}` | Dashboard Workspace, chỉ trên Project có participation. Mọi envelope có khả năng thiếu scope đều mang `value_state=PARTIAL` và `included_project_count`. Không lộ số Project ẩn | `R-03` | OPEN | — | — | không audit | `WORKSPACE_NOT_FOUND`; `SCOPE_PARTIAL_NO_DATA` (khi `included_project_count=0`: toàn bộ envelope là `NO_PERMISSION`, HTTP vẫn `200`) |
| EP-184 | GET | `/dashboards/system` | Dashboard vận hành cho SA: số tài khoản theo `status`, phiên ACTIVE, lockout, dung lượng object storage, trạng thái Ollama, `background_job` theo `status`, `outbox_event` tồn đọng. Chỉ metadata, không nội dung Task riêng tư | `R-04` | OPEN | — | — | `DASHBOARD_SYSTEM_READ` (mới) | — |
| EP-185 | GET | `/metrics` | Đọc metric theo mã, dùng chung cho cả 4 scope. Query bắt buộc `scope_type` (PROJECT/WORKSPACE/USER/SYSTEM), `scope_id`, `metric_codes`; tuỳ chọn `as_of_date` (mặc định hôm nay, tính realtime) | `R-01..R-04` theo `scope_type` | OPEN | — | — | không audit | `METRIC_CODE_UNKNOWN`; `SCOPE_TYPE_UNSUPPORTED`; `METRIC_NOT_APPLICABLE_TO_SCOPE` (422 — mã hợp lệ nhưng không định nghĩa cho scope này) |
| EP-186 | GET | `/metrics/historical` | Chuỗi thời gian đọc từ `metric_daily_snapshot`. Query: `scope_type`, `scope_id`, `metric_code`, `date_from`, `date_to`, `granularity=DAY`. Chỉ trả ngày đã chốt; ngày chưa có snapshot **không** được nội suy | `như EP-185` | OPEN | — | — | không audit | `SNAPSHOT_NOT_AVAILABLE` (envelope `INSUFFICIENT_DATA`, không phải lỗi HTTP); `DATE_RANGE_TOO_WIDE`; `SNAPSHOT_IMMUTABLE_VIOLATION` (không dùng ở tầng API — dành cho migration/job) |
| EP-187 | GET | `/reports/definitions` | Danh sách `report_definition` khả dụng: `code`, `name_key`, `scope_type`, `metric_codes`, `export_formats`, `max_rows`, `requires_reauth`. Lọc theo scope actor **được** yêu cầu; không tiết lộ definition của scope actor không có quyền | `R-05` | OPEN | — | — | không audit | — |
| EP-188 | POST | `/reports/export-jobs` | Yêu cầu export. Body: `report_code`, `scope_type`, `scope_id`, `period_from`, `period_to`, `format` (PDF/XLSX/CSV), `reason`. Ghi `report_export_job(status='QUEUED')` + `background_job(job_type='REPORT_EXPORT')` + `outbox_event`. Trả `202` và `ReportExportJobResponse` (§6.12) | `R-05` | OPEN | BB | theo `report_definition.requires_reauth` | `REPORT_EXPORT_REQUEST` (mới) | `REPORT_CODE_UNKNOWN`; `EXPORT_FORMAT_UNSUPPORTED` (415); `EXPORT_ROWS_LIMIT_EXCEEDED` (422 — ước lượng vượt `max_rows`, yêu cầu thu hẹp kỳ); `DUPLICATE_EXPORT_IN_FLIGHT` (409 — cùng `dedup_key` còn sống) |
| EP-189 | GET | `/reports/export-jobs` | Danh sách export job **của chính actor** (`requested_by = actor`). Query: `status`, `report_code`, `cursor`, `limit`. SA **không** thấy job của người khác (AUD-16) | `R-06` | OPEN | — | — | không audit | — |
| EP-190 | GET | `/reports/export-jobs/{jobId}` | Chi tiết một job: `status`, `row_count`, `metric_state_summary`, `expires_at`, `authorized_at_generation`, `authorized_at_delivery`. Người khác — **kể cả SA** — nhận `404` | `R-06` | OPEN | — | — | không audit | `EXPORT_JOB_NOT_FOUND` (404, dùng chung cho "không tồn tại" và "không phải của bạn") |
| EP-191 | POST | `/reports/export-jobs/{jobId}/cancel` | Huỷ job đang `QUEUED`/`RUNNING`/`REAUTHORIZING` → `CANCELLED_BY_USER`; nếu job đã lease thì đánh dấu và để worker tự dừng ở điểm kiểm tra kế tiếp (không kill giữa chừng transaction) | `R-06` | job chưa `GENERATED`/`DELIVERED`/`FAILED`/`EXPIRED` | BB | — | `REPORT_EXPORT_CANCEL` (mới) | `EXPORT_JOB_NOT_CANCELABLE` (409); `EXPORT_JOB_ALREADY_TERMINAL` |
| EP-192 | GET | `/reports/export-jobs/{jobId}/download` | Tải file kết quả qua Backend gateway (ARCH-07, cùng cơ chế EP-154 — không presigned URL). **Reauthorize lần hai** tại thời điểm tải và ghi `authorized_at_delivery`; hết TTL → `EXPIRED` | `R-06` | job ở `GENERATED` hoặc `DELIVERED` và `now() < expires_at` | — | — | `REPORT_EXPORT_DOWNLOAD` (mới) | `PERMISSION_REVOKED_AT_DELIVERY` (**403** — ngoại lệ có chủ đích, quy tắc 9); `EXPORT_EXPIRED` (410, job chuyển `EXPIRED`); `EXPORT_NOT_READY` (409); `ATTACHMENT_OBJECT_MISSING` (500 + `security_event`) |
| EP-193 | GET | `/projects/{projectId}/progress` | Tiến độ Project theo AUD-20: `numerator` = số top-level COMPLETED, `denominator` = số top-level trừ top-level CANCELLED, `percent`, `display`. Tách kèm `by_milestone[]` với cùng công thức cho từng Milestone | `R-02` | OPEN | — | — | không audit | `PROJECT_NOT_FOUND`; `PROGRESS_DENOMINATOR_ZERO` (không dùng làm lỗi — trả `NOT_APPLICABLE`) |

### 5.13 Nhóm M — AI Assist (MOD-11)

Nguyên tắc bắt buộc cho toàn bộ nhóm:

1. **AI chỉ đề xuất, không bao giờ tự thay đổi dữ liệu nghiệp vụ** (AUD-47). Không endpoint nào trong nhóm này tạo/sửa/xoá Task, Project, Assignment, Milestone, Comment hay File. `ai_suggestion.status` **không có** giá trị `APPLIED_BY_AI`.
2. `accept` chỉ chuyển `ai_suggestion.status` sang `ACCEPTED` và trả về **draft đã chuẩn hoá** theo JSON schema. Mutation thật phải đi qua `EP-092` (tạo Task), `EP-093` (tạo subtask), `EP-097` (sửa Task) hoặc `EP-134` (chỉ định approver) với actor là **user thật**, `Idempotency-Key` riêng và pipeline P1..P9 đầy đủ. `resulting_task_id` chỉ được điền bởi chính các endpoint đó.
3. DEP-04 cấm MOD-11 gọi MOD-05. AI đọc ngữ cảnh qua Manifest (`ai_context_manifest`, AUD-39) đã duyệt như code; không có endpoint nào cho phép client chỉ định trường tự do hay truyền prompt hệ thống.
4. Quyền đọc ngữ cảnh được **kiểm tra lại tại thời điểm build context** và **kiểm tra lại lần nữa trước khi giao kết quả** (`ai_job.reauthorized_at`, `reauthorize_result`). Mất quyền giữa chừng → `CANCELLED_REVOKED` và toàn bộ suggestion liên quan bị `REDACTED` (AUD-13, AUD-39).
5. `ai_job` và `ai_suggestion` là dữ liệu **riêng của `principal_user_id`**. Người khác — **kể cả SA** — nhận `404`, không phải `403` (AI-02).
6. Prompt/completion thô không nằm trong response và không nằm trong DB nghiệp vụ; `ai_interaction.content_ref` trỏ tới object private, retention 90 ngày. Response chỉ có `token_count`, `latency_ms`, `model_name`, `config_key`.
7. Khi `ai.enabled=false` (seed 02B §7.1, chờ DEC-41): endpoint ghi (`EP-194`, `EP-200`, `EP-201`) trả `409 AI_DISABLED`; endpoint đọc vẫn hoạt động để trả lịch sử cũ (đã redact nếu mất quyền).
8. `AI_JOB_*_COUNT` trong dashboard chỉ là số liệu vận hành; không có metric nào suy ra năng suất cá nhân từ AI (R-07 không tồn tại).

| ID | Method | Path | Mô tả | Mã quyền | State gate | Idem | Reauth | Audit action | Error riêng |
|---|---|---|---|---|---|---|---|---|---|
| EP-194 | POST | `/ai/requests` | Yêu cầu gợi ý. Body: `feature` (TASK_DRAFT / SUBTASK_SUGGESTION / PRIORITY_SUGGESTION / DEADLINE_SUGGESTION / ASSIGNEE_SUGGESTION / NATURAL_SEARCH / DAILY_PLANNER / WEEKLY_REVIEW / METRIC_EXPLAIN), `scope_type`, `scope_id`, `hint` (tối đa 500 ký tự, không nhận chỉ dẫn hệ thống), `target_task_id` (bắt buộc với nhóm gợi ý theo Task). Tạo `ai_job(status='QUEUED')` + `background_job(job_type='AI_RUN')`. Trả `202` | `AI-01` | `ai.enabled=true`; scope phải ở state cho phép đọc tại thời điểm build context | BB | — | `AI_REQUEST` (mới) | `AI_DISABLED` (409); `AI_FEATURE_UNKNOWN`; `AI_FEATURE_NOT_ALLOWED_FOR_ROLE` (403 — WG/PG gọi feature cần quyền ghi); `AI_CONTEXT_MANIFEST_MISSING`; `AI_CONCURRENCY_LIMIT` (429, `ai.max_concurrency=1`); `AI_HINT_TOO_LONG` |
| EP-195 | GET | `/ai/jobs` | Danh sách job **của chính actor**. Query: `feature`, `status`, `cursor`, `limit`. Không có tham số `user_id` — mọi nỗ lực lọc theo người khác bị bỏ qua và không audit như truy vấn hợp lệ | `AI-02` | OPEN | — | — | không audit | — |
| EP-196 | GET | `/ai/jobs/{aiJobId}` | Chi tiết job: `status`, `context_manifest_code`, `context_token_estimate`, `attempt_count`, `timeout_ms`, `error_code`, `reauthorize_result`, `started_at`, `finished_at`, `duration_ms`. Người khác **kể cả SA** → `404` | `AI-02` | OPEN | — | — | không audit | `AI_JOB_NOT_FOUND` (404) |
| EP-197 | POST | `/ai/jobs/{aiJobId}/cancel` | Huỷ job chưa `COMPLETED` → `CANCELLED_BY_USER`. Job đang `RUNNING` được dừng ở điểm kiểm tra kế tiếp; nếu Ollama đã trả kết quả thì kết quả bị huỷ, không lưu suggestion | `AI-02` | job ở `QUEUED`/`CONTEXT_BUILDING`/`RUNNING`/`VALIDATING`/`REAUTHORIZING` | BB | — | `AI_JOB_CANCEL` (mới) | `AI_JOB_NOT_CANCELABLE` (409); `AI_JOB_NOT_FOUND` |
| EP-198 | GET | `/ai/jobs/{aiJobId}/suggestions` | Danh sách suggestion của một job. Chỉ trả khi job `COMPLETED` **và** `reauthorize_result='ALLOWED'`. Mục nào bị redact vẫn xuất hiện với `redacted=true`, `payload` bị thay bằng `null` và `redacted_reason_key` — không xoá khỏi danh sách, để UI không hiển thị sai thứ tự | `AI-02` | job `COMPLETED` | — | — | không audit | `AI_JOB_NOT_COMPLETED` (409); `AI_RESULT_REVOKED` (403 khi `reauthorize_result` là `DENIED_REVOKED`); `AI_RESULT_STATE_LOCKED` (403 khi `DENIED_STATE`) |
| EP-199 | GET | `/ai/suggestions/{suggestionId}` | Chi tiết một suggestion: `suggestion_type`, `status`, `payload`, `validation_result` (AUD-40: trường nào bị loại vì ngoài scope), `explanation`, `confidence`, `redacted` | `AI-02` | OPEN | — | — | không audit | `AI_SUGGESTION_NOT_FOUND` (404); `AI_SUGGESTION_REDACTED` (410 — payload không còn, chỉ giữ metadata) |
| EP-200 | POST | `/ai/suggestions/{suggestionId}/accept` | Đánh dấu ACCEPTED và trả về draft chuẩn hoá (`AiSuggestionResponse.draft`, §6.13) cùng `next_action_endpoint` để client gọi tiếp. **Không** tạo Task, **không** đổi state, **không** ghi `resulting_task_id` tại đây | `AI-03` | suggestion ở `DRAFT` hoặc `SHOWN`; scope vẫn còn quyền ghi cho actor | BB | — | `AI_SUGGESTION_ACCEPT` (mới) | `AI_SUGGESTION_ALREADY_DECIDED` (409, kèm `winning_status`); `AI_SUGGESTION_EXPIRED` (410); `AI_SUGGESTION_REDACTED` (410); `AI_DRAFT_SCHEMA_MISMATCH` (422 — payload cũ không còn khớp schema hiện hành, yêu cầu tạo job mới) |
| EP-201 | POST | `/ai/suggestions/{suggestionId}/reject` | Đánh dấu REJECTED, body tuỳ chọn `reason_code` trong danh sách đóng (`WRONG_PRIORITY`, `WRONG_ASSIGNEE`, `OUT_OF_SCOPE`, `DUPLICATE`, `LOW_QUALITY`, `OTHER`). Phản hồi cho vòng lặp chất lượng, không phải dữ liệu huấn luyện | `AI-03` | suggestion ở `DRAFT` hoặc `SHOWN` | BB | — | `AI_SUGGESTION_REJECT` (mới) | `AI_SUGGESTION_ALREADY_DECIDED`; `AI_REJECT_REASON_UNKNOWN`; `AI_SUGGESTION_EXPIRED` |
| EP-202 | GET | `/ai/history` | Lịch sử AI của chính actor. Query: `feature`, `scope_type`, `scope_id`, `date_from`, `date_to`, `cursor`. Mục mất quyền được trả với `redacted=true` và che `scope_id` — không loại bỏ khỏi danh sách (AUD-13) | `AI-05` | OPEN | — | — | không audit | `HISTORY_WINDOW_TOO_WIDE` (422) |
| EP-203 | GET | `/admin/ai/model-configurations` | Đọc cấu hình Ollama: `config_key`, `model_name`, `context_window_tokens`, `temperature`, `timeout_ms`, `max_concurrency`, `max_output_tokens`, `enabled`, `updated_by`, `updated_at`. **Không** trả `ollama_base_url` đầy đủ — chỉ trả nhãn mạng nội bộ đã che | `AI-04` | OPEN | — | — | `AI_MODEL_CONFIG_READ` (mới) | `AI_ADMIN_NOT_GRANTED` (404 khi SA thiếu grant `OLLAMA_CONFIG_MANAGE` — P3) |
| EP-204 | PUT | `/admin/ai/model-configurations/{configKey}` | Cập nhật một cấu hình. Bắt buộc `If-Match` (cột `version`). Ghi `ai_model_configuration.reauth_ticket_id`. Action này **không** kéo theo bất kỳ quyền đọc Project/Task nào (Nhóm 07, 04-RBAC §12) | `AI-04` | OPEN | BB | Y | `AI_MODEL_CONFIG_UPDATE` | `AI_CONFIG_KEY_UNKNOWN` (404); `AI_TIMEOUT_OUT_OF_RANGE` (422 — vượt `min_value`/`max_value` của `system_setting`); `AI_CONCURRENCY_OUT_OF_RANGE`; `AI_OLLAMA_URL_NOT_INTERNAL` (422 — chặn cấu hình trỏ ra ngoài LAN) |
| EP-205 | GET | `/admin/ai/context-manifests` | Danh sách `ai_context_manifest`: `code`, `feature`, `allowed_fields`, `max_records`, `max_chars`, `max_token_estimate`, `forbidden_field_patterns`, `scope_rule`, `version`, `approved_by`, `approved_at`, `enabled` | `AI-04` | OPEN | — | — | `AI_CONTEXT_MANIFEST_READ` (mới) | — |
| EP-206 | PUT | `/admin/ai/context-manifests/{code}` | Cập nhật Manifest; `version` tự tăng, bắt buộc `If-Match`. Manifest mới chỉ áp dụng cho job tạo **sau** thời điểm duyệt — job đang chạy giữ `context_manifest_code` cũ để tái lập được | `AI-04` | OPEN | BB | Y | `AI_CONTEXT_MANIFEST_UPDATE` (mới) | `AI_MANIFEST_CODE_UNKNOWN`; `AI_MANIFEST_FORBIDDEN_PATTERN_EMPTY` (422 — không được để rỗng); `AI_MANIFEST_SCOPE_RULE_INVALID`; `AI_MANIFEST_IN_USE_CANNOT_DISABLE` (409 khi vẫn còn job `QUEUED` trỏ tới) |

### 5.14 Nhóm N — System Administration & Configuration (MOD-12, MOD-13)

Nguyên tắc bắt buộc cho toàn bộ nhóm:

1. SA **không** mặc nhiên đọc dữ liệu riêng tư. Mọi endpoint trong nhóm này trả **metadata**, không trả nội dung Task/Project/bình luận/nội dung file. `security_event.detail` không chứa mật khẩu hay token.
2. **Không self-escalation** (S-09): `actor_user_id ≠ target_user_id` cho `EP-208`, cho cấp/quyền toàn cục và cho khôi phục tài khoản. Vi phạm → `422 SELF_ESCALATION_FORBIDDEN`, không phải 409.
3. Secret không bao giờ trả plaintext. `system_setting.value` với `category='SECURITY'` và trường nhạy cảm được trả dạng `{"redacted": true, "fingerprint": "<8 ký tự hash>"}`; chỉ nhận giá trị mới, không đọc lại giá trị cũ.
4. Mọi thay đổi cấu hình ghi `configuration_change_log` với `old_value`/`new_value`/`reason`/`reauth_ticket_id` **trong cùng transaction**, và ghi `audit_event`. Không có đường ghi nào bỏ qua change log.
5. `is_security_invariant=true` + `min_value`/`max_value` được kiểm tra ở tầng DB bằng CHECK; API trả `422 SETTING_VALUE_VIOLATES_INVARIANT` chứ không âm thầm kẹp giá trị.
6. Emergency action là **danh sách đóng bằng CHECK** (`emergency_action_log.action_code`): `SYSTEM_ADMIN_OWNER_TRANSFER`, `WORKSPACE_OWNER_RESTORE_MANAGER`, `SYSTEM_ADMIN_ACCOUNT_RESTORE`, `SYSTEM_ADMIN_SUSPEND`, `SYSTEM_ADMIN_FORCE_LOGOUT`. Không mở rộng bằng cấu hình. Bắt buộc `reason` ≥ 10 ký tự, reauth, audit, và `content_read=false` (CHECK cứng).
7. Bootstrap tài khoản SA đầu tiên **chỉ** qua CLI (`AUD-18`); không có endpoint HTTP nào tạo SA đầu tiên. `EP-223` chỉ trả cờ `system.bootstrap_completed` để client biết trạng thái, không phải là cổng bootstrap.
8. `AUDIT_PURGE` và `RETENTION_UPDATE` chỉ chạy được khi kết nối DB mang role `stm_maintenance` (ENF-01, 02B §9). Gọi qua role application → `403 DB_ROLE_NOT_ALLOWED`, và bản thân lần gọi bị ghi `audit_event(outcome='DENIED_FORBIDDEN')`.
9. Không endpoint nào tuyên bố đạt RPO/RTO. `EP-213`/`EP-214` trả về `job_id` và trạng thái; kết quả đối soát nằm ở `EP-227`..`EP-229`.

| ID | Method | Path | Mô tả | Mã quyền | State gate | Idem | Reauth | Audit action | Error riêng |
|---|---|---|---|---|---|---|---|---|---|
| EP-207 | GET | `/admin/system-settings` | Đọc toàn bộ `system_setting`. Query: `category`. Trường nhạy cảm bị redact (quy tắc 3). Trả kèm `is_security_invariant`, `min_value`, `max_value`, `updated_by`, `updated_at`, `change_log_id` | `S-01` | OPEN | — | — | `SYSTEM_SETTING_READ` (mới) | `ADMIN_NOT_GRANTED` (404 khi thiếu grant `SYSTEM_CONFIG_MANAGE` — P3) |
| EP-208 | PUT | `/admin/system-settings/{settingKey}` | Cập nhật một setting. Bắt buộc `If-Match`, `reason` (≥ 10 ký tự). Ghi `configuration_change_log` + `audit_event(before/after)` + `system_setting.reauth_ticket_id`. Setting lan tới hành vi đang chạy (TTL token, lockout, allowlist file) chỉ áp dụng cho yêu cầu **mới**; phiên và upload session đang sống giữ tham số cũ | `S-02` | OPEN | BB | Y | `SYSTEM_SETTING_UPDATE` | `SETTING_KEY_UNKNOWN` (404); `SETTING_VALUE_TYPE_MISMATCH` (422 theo `value_type`); `SETTING_VALUE_VIOLATES_INVARIANT` (422); `SELF_ESCALATION_FORBIDDEN` (422); `REASON_REQUIRED`; `SETTING_IMMUTABLE_IN_V1` (422 — vd `system.bootstrap_completed`) |
| EP-209 | GET | `/admin/configuration-change-log` | Truy vấn `configuration_change_log`. Query: `setting_key`, `changed_by`, `date_from`, `date_to`, `cursor`. Append-only: không có PUT/DELETE cho tài nguyên này ở bất kỳ phiên bản nào | `S-01` | OPEN | — | — | `CONFIG_CHANGE_LOG_READ` (mới) | — |
| EP-210 | GET | `/admin/security-events` | Truy vấn `security_event`. Query: `event_type`, `severity`, `user_id`, `date_from`, `date_to`, `cursor`. Chỉ metadata; `detail` được lọc theo allowlist trường trước khi trả | `S-03` | OPEN | — | — | `SECURITY_EVENT_READ` (mới) | `SECURITY_EVENT_DETAIL_REDACTED` (không phải lỗi — cờ `redacted=true` trên từng dòng) |
| EP-211 | GET | `/admin/rate-limits` | Đọc cấu hình rate limit theo `scope_key`. Giá trị ngưỡng **chỉ** hiện ở endpoint quản trị này; không endpoint nghiệp vụ nào trả ngưỡng (§1.5, §9) | `S-04` | OPEN | — | — | `RATE_LIMIT_CONFIG_READ` (mới) | `RATE_LIMIT_SCOPE_UNKNOWN` |
| EP-212 | PUT | `/admin/rate-limits/{scopeKey}` | Cập nhật ngưỡng. Bắt buộc `If-Match`, `reason`. Có hiệu lực từ cửa sổ kế tiếp, không hồi tố. Không được đặt ngưỡng thấp hơn sàn bảo vệ (`min_value`) | `S-04` | OPEN | BB | Y | `RATE_LIMIT_CONFIG_UPDATE` (mới) | `RATE_LIMIT_SCOPE_UNKNOWN` (404); `RATE_LIMIT_BELOW_FLOOR` (422); `RATE_LIMIT_ABOVE_CEILING` (422); `REASON_REQUIRED` |
| EP-213 | POST | `/admin/backups/trigger` | Khởi động backup, tạo `background_job(job_type='BACKUP_VERIFY')`. Body: `backup_type` (FULL/INCREMENTAL), `reason`. Trả `202` + `job_id`. Không endpoint nào trả nội dung backup hay đường dẫn lưu trữ | `S-05` | OPEN | BB | Y | `BACKUP_TRIGGER` | `BACKUP_ALREADY_RUNNING` (409); `BACKUP_WINDOW_BLOCKED` (409 — đang trong khung bảo trì); `REASON_REQUIRED` |
| EP-214 | POST | `/admin/backups/restore-drill` | Diễn tập khôi phục trên môi trường cô lập. Ghi `security_event(event_type='RESTORE_DRILL')`. Kết quả chỉ là `SUCCEEDED`/`FAILED_*` + số liệu đối soát, **không** phải cam kết RPO/RTO (quy tắc 9) | `S-05` | OPEN | BB | Y | `RESTORE_DRILL` | `RESTORE_DRILL_IN_PROGRESS` (409); `RESTORE_DRILL_TARGET_UNSAFE` (422 — từ chối chạy trên môi trường production); `REASON_REQUIRED` |
| EP-215 | GET | `/admin/retention-policies` | Đọc `retention_policy`. `retention_days = NULL` nghĩa là **giữ vĩnh viễn** (mặc định V1, AUD-45) và phải được trả là `null`, không phải `0` hay `-1` | `S-06` | OPEN | — | — | `RETENTION_READ` (mới) | `RETENTION_NOT_GRANTED` (404 khi thiếu grant `RETENTION_MANAGE`) |
| EP-216 | PUT | `/admin/retention-policies/{entityName}` | Cập nhật retention. Bắt buộc `If-Match`, `reason`. Rút ngắn retention của `audit_event` bị chặn ở tầng bất biến; mọi thay đổi đều ghi `configuration_change_log` | `S-06` | OPEN | BB | Y | `RETENTION_UPDATE` | `RETENTION_ENTITY_UNKNOWN` (404); `RETENTION_APPEND_ONLY_ENTITY` (422 — `audit_event`/`security_event`/`workflow_event` không được đặt `PURGE` dưới ngưỡng tối thiểu); `RETENTION_DAYS_OUT_OF_RANGE`; `REASON_REQUIRED` |
| EP-217 | POST | `/admin/audit/purge` | Dọn dữ liệu hết hạn theo policy. Chỉ chạy khi kết nối mang role `stm_maintenance` (quy tắc 8). Ghi `audit_maintenance_log` với `block_hash_before`/`block_hash_after`/`rows_affected`/`reauth_ticket_id`/`reason` — **không** ghi `audit_event` cho chính lần purge vào bảng đã purge | `S-06` | OPEN | BB | Y | `AUDIT_PURGE` | `DB_ROLE_NOT_ALLOWED` (403); `PURGE_RANGE_IMMUTABLE` (422 — chạm khối hash-chain đã verify); `PURGE_ALREADY_RUNNING` (409); `REASON_REQUIRED` |
| EP-218 | GET | `/admin/audit/maintenance-log` | Đọc `audit_maintenance_log`. Query: `operation`, `performed_by`, `date_from`, `date_to`, `cursor` | `S-06` | OPEN | — | — | `AUDIT_MAINTENANCE_LOG_READ` (mới) | — |
| EP-219 | POST | `/admin/emergency/actions` | Thực thi emergency action. Body: `action_code` (danh sách đóng — quy tắc 6), `target_type`, `target_id`, `reason` (≥ 10 ký tự), `content_read` (bắt buộc `false`). Ghi `emergency_action_log` + `audit_event(reason NOT NULL)` + `security_event(EMERGENCY_ACTION)` trong cùng transaction | `S-08` | theo `action_code` | BB | Y | chính `action_code` (vd `SYSTEM_ADMIN_OWNER_TRANSFER`) | `EMERGENCY_ACTION_CODE_UNKNOWN` (422); `EMERGENCY_TARGET_NOT_FOUND` (404); `EMERGENCY_REASON_TOO_SHORT` (422); `EMERGENCY_CONTENT_READ_MUST_BE_FALSE` (422); `EMERGENCY_SELF_TARGET_FORBIDDEN` (422); `EMERGENCY_ACTION_NOT_ALLOWED_FOR_ROLE` (403 — WO chỉ được `WORKSPACE_OWNER_RESTORE_MANAGER`) |
| EP-220 | GET | `/admin/emergency/actions` | Truy vấn `emergency_action_log`. Query: `action_code`, `actor_user_id`, `date_from`, `date_to`, `cursor`. Trả `content_read` để bằng chứng "không đọc nội dung" kiểm tra được từ bên ngoài | `S-08` | OPEN | — | — | `EMERGENCY_ACTION_LOG_READ` (mới) | — |
| EP-221 | GET | `/system/health` | Health sống (liveness) cho load balancer: `{"status":"UP"}` hoặc `DOWN`. Không yêu cầu authentication, không trả phiên bản, không trả tên dịch vụ nội bộ, không trả chi tiết lỗi kết nối | `—` | không áp dụng | — | — | không audit | — |
| EP-222 | GET | `/system/health/ready` | Readiness: kiểm tra kết nối DB, object storage, outbox dispatcher. Trả `status` + mảng `checks[]` với tên **chung chung** (`db`, `storage`, `queue`) và trạng thái từng mục; không tiết lộ host, port, tên bucket, chuỗi kết nối | `—` | không áp dụng | — | — | không audit | — |
| EP-223 | GET | `/admin/bootstrap-status` | Chỉ trả `{"bootstrap_completed": true|false}` để client hiển thị hướng dẫn khởi động. **Không** phải cổng bootstrap: bootstrap SA đầu tiên chỉ qua CLI (AUD-18, quy tắc 7). Không yêu cầu authentication, không đếm lượt gọi vào `security_event` | `—` | không áp dụng | — | — | không audit | — |

### 5.15 Nhóm O — Audit Query (MOD-13)

Nguyên tắc: `audit_event` là append-only tuyệt đối (AUD-14) — **không có** POST/PUT/PATCH/DELETE cho tài nguyên `audit/events` trong bất kỳ phiên bản nào. Quyền đọc là **động** (AUD-36, FL-09): SA không mặc nhiên đọc.

| ID | Method | Path | Mô tả | Mã quyền | State gate | Idem | Reauth | Audit action | Error riêng |
|---|---|---|---|---|---|---|---|---|---|
| EP-224 | GET | `/audit/events` | Truy vấn audit theo scope. Query: `scope_type` (SYSTEM/WORKSPACE/PROJECT/TASK/USER), `scope_id`, `target_type`, `target_id`, `actor_user_id`, `action`, `outcome`, `sensitivity`, `date_from`, `date_to`, `cursor`, `limit`. Ba đường cấp quyền (FL-09): (a) `global_permission_grant('AUDIT_READ_SYSTEM')` cho scope SYSTEM + sensitivity NORMAL; (b) `special_grant('AUDIT_READ_SCOPE')` đúng scope; (c) **có quyền đọc chính đối tượng tại thời điểm truy vấn** thì đọc được dòng `sensitivity='SENSITIVE_CONTENT'` của đối tượng đó. Dòng actor không đủ quyền bị **loại khỏi kết quả**, không trả về rồi che — và toàn bộ lần truy vấn được ghi `security_event(AUDIT_READ)` | `FL-09` | OPEN | — | — | `AUDIT_READ` (mọi lần truy vấn, kể cả kết quả rỗng) | `AUDIT_NO_PERMISSION` (404 — actor không có cả ba đường cấp quyền trên scope này); `AUDIT_SCOPE_REQUIRED` (422 — thiếu `scope_type` khi không có grant SYSTEM); `AUDIT_DATE_RANGE_TOO_WIDE` (422); `AUDIT_SENSITIVITY_NOT_GRANTED` (403 khi cố tình lọc `sensitivity=SENSITIVE_CONTENT` mà không có đường (c)) |
| EP-225 | GET | `/audit/events/{auditEventId}` | Đọc một dòng audit: `occurred_at`, `actor_type`, `actor_user_id`, `action`, `target_type`, `target_id`, `scope_*`, `sensitivity`, `before`, `after`, `reason`, `outcome`, `reason_code`, `reauth_ticket_id`, `correlation_id`, `prev_hash`, `entry_hash`. **Không** trả hash-chain của dòng khác; không có endpoint verify chain qua HTTP (verify là job `AUDIT_ARCHIVE`/`VERIFY_HASH_CHAIN`) | `FL-09` | OPEN | — | — | `AUDIT_READ` | `AUDIT_EVENT_NOT_FOUND` (404 — dùng chung cho "không tồn tại" và "không đủ quyền", theo §1.4); `AUDIT_BEFORE_AFTER_REDACTED` (cờ, không phải lỗi: `before`/`after` bị lược trường nhạy cảm) |
| EP-226 | GET | `/audit/scopes` | Liệt kê scope mà actor **hiện** có quyền đọc audit, kèm đường cấp quyền (`GRANT_SYSTEM`, `SPECIAL_GRANT`, `OBJECT_READ`) và `expires_at` của `special_grant`. Dùng để UI dựng bộ lọc mà không phải đoán; scope hết hạn không xuất hiện | `FL-09` | OPEN | — | — | không audit | — |

### 5.16 Nhóm P — Background Jobs (MOD-14)

Nguyên tắc: job nằm trong DB, lease + `FOR UPDATE SKIP LOCKED` (ARCH-05). Endpoint chỉ **đọc trạng thái và điều khiển vòng đời**, không phải là cơ chế thực thi. `principal_user_id` chỉ lưu identity, **không** lưu permission snapshot (AUD-48) — retry luôn đánh giá lại quyền (NFR-24).

| ID | Method | Path | Mô tả | Mã quyền | State gate | Idem | Reauth | Audit action | Error riêng |
|---|---|---|---|---|---|---|---|---|---|
| EP-227 | GET | `/jobs` | Danh sách job. Mặc định chỉ trả job có `principal_user_id = actor`. SA chỉ thấy thêm job `principal_type='SYSTEM'`; SA **không** thấy job nghiệp vụ của người khác (AUD-48). Query: `job_type`, `status`, `scope_type`, `scope_id`, `cursor`, `limit` | `S-07` | OPEN | — | — | không audit | `JOB_TYPE_UNKNOWN`; `JOB_STATUS_UNKNOWN` |
| EP-228 | GET | `/jobs/{jobId}` | Chi tiết job: `job_type`, `status`, `principal_type`, `scope_*`, `priority`, `available_at`, `attempt_count`, `max_attempts`, `lease_expires_at` (chỉ giờ còn lại, **không** trả `lease_holder`), `reauth_required`, `reauthorized_at`, `reauthorize_result`, `result_status`, `last_error`, `dead_letter_reason`, `correlation_id`. `payload` chỉ trả khi actor là principal hoặc job là `SYSTEM` | `S-07` | OPEN | — | — | không audit | `JOB_NOT_FOUND` (404 — dùng chung "không tồn tại" và "không phải của bạn"); `JOB_PAYLOAD_WITHHELD` (cờ, không phải lỗi) |
| EP-229 | GET | `/jobs/{jobId}/attempts` | Lịch sử `job_attempt` (append-only): `attempt_no`, `worker_id` đã che còn nhãn, `status`, `started_at`, `finished_at`, `duration_ms`, `error_code`, `error_detail`, `reauthorize_result`. `error_detail` không chứa prompt, không chứa nội dung Task, không chứa token | `S-07` | OPEN | — | — | không audit | `JOB_NOT_FOUND` |
| EP-230 | POST | `/jobs/{jobId}/cancel` | Huỷ job → `CANCELLED_BY_USER`. Job đang `LEASED`/`RUNNING` được đánh dấu và worker dừng ở điểm kiểm tra kế tiếp; không rollback transaction của worker từ HTTP. Job `WAITING_REAUTH` bị huỷ ngay | `S-07` | job ở `QUEUED`/`LEASED`/`RUNNING`/`WAITING_REAUTH`/`FAILED_RETRYABLE` | BB | — | `JOB_CANCEL` (mới) | `JOB_NOT_CANCELABLE` (409 — đã `SUCCEEDED`/`DEAD_LETTER`/`CANCELLED_*`/`FAILED_PERMANENT`); `JOB_NOT_FOUND` |
| EP-231 | POST | `/jobs/{jobId}/retry` | Retry thủ công: tăng `attempt_count`, đặt `available_at=now()`, chuyển về `QUEUED`. **Bắt buộc đánh giá lại quyền** của `principal_user_id` tại thời điểm retry, không dùng quyền lúc enqueue (NFR-24); mất quyền → job chuyển `CANCELLED_REVOKED` thay vì chạy. Body: `reason` | `S-07` | job ở `FAILED_RETRYABLE`/`FAILED_PERMANENT`/`DEAD_LETTER` và `attempt_count < max_attempts` | BB | — | `JOB_RETRY` (mới) | `JOB_RETRY_NOT_ALLOWED` (409 — `CANCELLED_REVOKED`, `CANCELLED_BY_USER`, `FAILED_VALIDATION`); `JOB_MAX_ATTEMPTS_EXCEEDED` (409); `JOB_PRINCIPAL_PERMISSION_REVOKED` (403); `JOB_NOT_FOUND`; `REASON_REQUIRED` |

---

## 6. DTO chuẩn

### 6.0 Quy ước áp dụng cho mọi DTO

| Quy ước | Nội dung |
|---|---|
| Tên trường | `snake_case`, khớp tên cột 02A/02B khi ánh xạ 1-1 (§1.1). Trường không có cột tương ứng phải nêu rõ nguồn tính ở cột "Ghi chú" |
| Trường chung không liệt kê lại | `id`, `created_at`, `created_by`, `updated_at`, `updated_by`, `version` có mặt trong **mọi** Response trên entity có optimistic lock (02A §1). Các bảng bên dưới chỉ ghi khi có ràng buộc đặc thù |
| Thiếu khoá ≠ `null` | **Khoá không xuất hiện** = trường không được serialize cho actor này (thiếu quyền). `null` = giá trị thật sự rỗng và actor **có** quyền đọc. Client không bao giờ suy diễn khoá vắng thành `null` |
| Chống oracle | Trường hợp việc "có/không có khoá" sẽ tiết lộ sự tồn tại của dữ liệu ẩn (số liệu tổng hợp, số Task trong Project bị ẩn), không dùng cơ chế vắng khoá mà trả `MetricEnvelope` với `value_state = 'NO_PERMISSION'` (§6.7). Số lượng không được trả dưới mọi dạng, kể cả `hidden_count` |
| Trường nội bộ không bao giờ xuất hiện | `search_text`, `search_tsv`, `password_hash`, `totp_secret`, `access_token`, `refresh_token`, `recovery_code_hash`, `push_token_hash`, `file_object.bucket`, `file_object.object_key`, `upload_session.staging_object_key`, `background_job.lease_holder`, `ollama_base_url` đầy đủ, `ai_interaction.content_ref`, prompt/completion thô, `system_setting.value` của trường nhạy cảm |
| `permissions` / `allowed_actions` | Khối **tư vấn** cho UI, tính từ cùng `AuthorizationPolicy` với request hiện tại. Client có thể dùng để ẩn nút; backend **vẫn** kiểm tra lại mọi request (04-RBAC §12, ARCH-06). Không phải là cam kết: quyền có thể mất trước lần gọi kế tiếp |
| Enum | Trả đúng chữ in hoa của `CHECK` trong 02A/02B. Thêm giá trị enum là **breaking** với client cũ → theo §10 |
| Thời điểm | RFC 3339 UTC hậu tố `Z`. Trường ngày-only trả bộ ba `*_date_only` + `*_timezone` + `*_is_date_only` (Nhóm 06) |
| Số | `integer` cho đếm và phút (`time_log.minutes` không dùng float — 02A §1); `number` cho `numeric(18,4)`; `percent` là `number` 2 chữ số thập phân, không nhân 100 hai lần |

### 6.1 `TaskResponse`

Trả bởi `EP-092` (tạo Task), `EP-093` (tạo Subtask), `EP-094`/`EP-095` (danh sách và tìm kiếm — bản rút gọn, xem ghi chú cuối bảng), `EP-096` (chi tiết), `EP-097` (PATCH), và là body sau mọi mutation workflow `EP-125`..`EP-130`.

| Trường | Kiểu | Bắt buộc | Ràng buộc | Ghi chú |
|---|---|---|---|---|
| `id` | uuid | Y | UUIDv7 | `task.id` |
| `project_id` | uuid | Y | — | không trả `workspace_id` của Task: suy ra từ `EP-062` |
| `parent_task_id` | uuid \| null | Y | `null` khi `depth = 0` | `task.parent_task_id` |
| `depth` | integer | Y | `0` hoặc `1` | bất biến subtask một cấp (`ck_task_depth`) |
| `title` | string | Y | 1..300 | `task.title` |
| `description` | string \| null | Y | — | bị **loại khỏi serialize** khi actor chỉ có quyền metadata (PG theo R-02) |
| `state` | enum | Y | `TODO`,`IN_PROGRESS`,`PENDING_APPROVAL`,`COMPLETED`,`CANCELLED` | 02A §5.1 |
| `blocked_flag` | boolean | Y | — | **cờ độc lập**, không phải một state (Nhóm 04) |
| `blocked_reason` | string \| null | Y | khác `null` chỉ khi `blocked_flag = true` | `ck` ở 02A §5.1 |
| `priority` | enum | Y | `LOW`,`MEDIUM`,`HIGH`,`URGENT` | — |
| `start_at` | datetime \| null | Y | — | Nhóm 06: start date tuỳ chọn |
| `start_date_only` | date \| null | Y | — | — |
| `due_at` | datetime \| null | Y | — | instant chuẩn hoá |
| `due_date_only` | date \| null | Y | — | — |
| `due_timezone` | string \| null | Y | IANA tz | Nhóm 06: lưu cả instant và tz gốc |
| `due_is_date_only` | boolean | Y | — | `true` → hạn là 23:59:59 tz người đặt |
| `is_overdue` | boolean | Y | tính tại thời điểm response | `due_at < now() AND state NOT IN ('COMPLETED','CANCELLED')`. Tách thêm `is_overdue_pending_approval` để không nhập nhằng "đang chờ duyệt" (metric dictionary 02B §3.4) |
| `milestone_id` | uuid \| null | Y | luôn `null` khi `depth = 1` | `ck_task_subtask_no_milestone` (DEC-09) |
| `completed_at` / `completed_by` | datetime \| null, uuid \| null | Y | — | — |
| `cancelled_at` / `cancelled_by` / `cancel_reason` | — | Y | `cancel_reason` NOT NULL khi `state = 'CANCELLED'` | 05 T-09 |
| `effective_state` | enum | Y | tập state hiệu lực ở 04-RBAC §3 | ví dụ `TASK_PENDING_APPROVAL`, `ARCHIVED_READONLY`. **Khác** `state`: phản ánh cả state của Project/Workspace phủ lên Task |
| `assignees[]` | array | Y | có thể rỗng (Task 0..n assignee — Nhóm 04) | mỗi phần tử: `assignment_id`, `user_id`, `display_name`, `status` (`ACTIVE`/`REMOVED`/`COMPLETED_CONFIRMED`), `assigned_at`, `assigned_by`. `REMOVED` chỉ hiện khi actor có `TASK_READ_HISTORY`; nếu không, mảng chỉ chứa `ACTIVE` |
| `assignment_count` | integer | Y | `COUNT(*)` trên `task_assignment` ACTIVE | **luôn** trả kèm `unique_task_count` ngữ cảnh ở danh sách (AUD-37) |
| `subtask_summary` | object | Y | `total`, `completed`, `in_progress` | chỉ đếm `depth = 1` có `parent_task_id = id` |
| `checklist_summary` | object | Y | `total`, `checked` | không trả nội dung từng item ở bản chi tiết Task; dùng `EP-110` |
| `dependency_summary` | object | Y | `blocks_count`, `blocked_by_count`, `has_unmet_blocker` | AUD-09; `has_unmet_blocker` là cờ để gate **start** ở UI (`F-01` / transition `05 T-01` / `EP-125`) |
| `tag_list[]` | array | Y | phần tử: `tag_id`, `name`, `color` (`#RRGGBB` \| null) | AUD-22 |
| `time_log_summary` | `MetricEnvelope` | Y | §6.7 | `TIMELOG_MINUTES_TOTAL` của Task. `NO_PERMISSION` khi actor không phải ASG/CRT/PM (AUD-23: time log theo **người ghi**, không lộ thời gian người khác) |
| `my_time_log_minutes` | integer \| null | N | chỉ actor | phần của actor; `null` nếu chưa ghi |
| `attachment_summary` | object | Y | `count`, `total_size_bytes` | metadata; **không** kèm `object_key`, không kèm URL |
| `current_round` | object \| null | Y | `null` khi không có round OPEN | `round_id`, `round_no`, `status`, `submitted_at`, `submitted_by`, `sla_due_at`, `sla_breached` (cờ tính toán, **không** phải quyết định — AUD-08), `eligible_approver_count`. `eligible_approver_count = 0` → UI cảnh báo `APPROVAL_NO_APPROVER`, không auto-approve |
| `round_history[]` | array | N | chỉ khi query `?include=round_history` | mỗi phần tử: `round_no`, `round_outcome`, `submitted_at`, `decided_at`, `decided_by`, `decision_outcome`. Nhiều vòng tính riêng, không nhân theo số approver (Nhóm 08) |
| `permissions` | object | Y | khối tư vấn (§6.0) | `can_update`, `can_submit`, `can_approve`, `can_reject`, `can_cancel`, `can_reopen`, `can_assign`, `can_upload`, `can_comment`, `can_delete`, `can_restore`. `can_approve` = `isEligibleApprover AND NOT isSelfApproval` (04-RBAC §8.1) |
| `version` | integer | Y | ≥ 0 | dùng cho `If-Match`; echo trong `ETag` |

Bản danh sách (`EP-089`, `EP-094`, `EP-095`, `EP-102`) trả **projection rút gọn**: `id`, `project_id`, `parent_task_id`, `depth`, `title`, `state`, `effective_state`, `blocked_flag`, `priority`, `due_at`, `due_is_date_only`, `is_overdue`, `milestone_id`, `assignees[]` (chỉ `user_id` + `display_name`), `subtask_summary`, `checklist_summary`, `version`. Các khoá còn lại **vắng mặt** (không phải `null`) theo quy tắc §6.0.

```json
{
  "id": "0197c0a2-1111-7000-8000-0000000000a1",
  "project_id": "0197c0a2-0000-7000-8000-0000000000c1",
  "parent_task_id": null,
  "depth": 0,
  "title": "Chuẩn hoá deadline theo timezone người đặt",
  "description": null,
  "state": "PENDING_APPROVAL",
  "effective_state": "TASK_PENDING_APPROVAL",
  "blocked_flag": false,
  "blocked_reason": null,
  "priority": "HIGH",
  "due_at": "2026-10-20T17:00:00Z",
  "due_date_only": null,
  "due_timezone": "Asia/Ho_Chi_Minh",
  "due_is_date_only": false,
  "is_overdue": false,
  "milestone_id": "0197c0a2-2222-7000-8000-0000000000e1",
  "assignees": [
    { "assignment_id": "0197c0a2-3333-7000-8000-0000000000a5", "user_id": "0197c0a2-4444-7000-8000-0000000000c9", "display_name": "Nguyen Van A", "status": "ACTIVE", "assigned_at": "2026-10-11T02:10:00Z", "assigned_by": "0197c0a2-5555-7000-8000-0000000000b6" }
  ],
  "assignment_count": 1,
  "subtask_summary": { "total": 3, "completed": 1, "in_progress": 1 },
  "checklist_summary": { "total": 5, "checked": 2 },
  "dependency_summary": { "blocks_count": 1, "blocked_by_count": 0, "has_unmet_blocker": false },
  "tag_list": [ { "tag_id": "0197c0a2-6666-7000-8000-0000000000d1", "name": "backend", "color": "#2F6FED" } ],
  "time_log_summary": { "metric_code": "TIMELOG_MINUTES_TOTAL", "value_int": null, "value_state": "NO_PERMISSION", "state_reason_code": "TIMELOG_OTHERS_VISIBLE_TO_MANAGER_ONLY", "source": "LIVE" },
  "attachment_summary": { "count": 2, "total_size_bytes": 481902 },
  "current_round": { "round_id": "0197c0a2-7777-7000-8000-0000000000f1", "round_no": 2, "status": "OPEN", "submitted_at": "2026-10-15T09:00:00Z", "submitted_by": "0197c0a2-4444-7000-8000-0000000000c9", "sla_due_at": "2026-10-17T09:00:00Z", "sla_breached": false, "eligible_approver_count": 1 },
  "permissions": { "can_update": false, "can_submit": false, "can_approve": true, "can_reject": true, "can_cancel": false, "can_reopen": false, "can_assign": false, "can_upload": false, "can_comment": true, "can_delete": false, "can_restore": false },
  "version": 8
}
```

### 6.2 `TaskCreateRequest`

Body của `EP-092` (Task) và `EP-093` (Subtask). Server bỏ qua mọi trường không có trong bảng; trường lạ → `422 UNKNOWN_FIELD` (không âm thầm chấp nhận).

| Trường | Kiểu | Bắt buộc | Ràng buộc | Ghi chú |
|---|---|---|---|---|
| `project_id` | uuid | Y với `EP-092`; **cấm** với `EP-093` | Project phải tồn tại và actor có `TK-01` (với `EP-092`) hoặc `TK-02` (với `EP-093`) | Subtask kế thừa `project_id` từ parent (`guard_subtask_same_project`) |
| `parent_task_id` | uuid | N với `EP-092`; **cấm** với `EP-093` (lấy từ path) | parent phải `depth = 0` | nếu parent đã `depth = 1` → `422 SUBTASK_DEPTH_EXCEEDED` |
| `title` | string | Y | 1..300 sau `btrim` | rỗng/toàn khoảng trắng → `422 TITLE_REQUIRED` |
| `description` | string \| null | N | ≤ 20000 ký tự (validation tầng application — 02A §1) | — |
| `priority` | enum | N | mặc định `MEDIUM` | giá trị ngoài enum → `422 PRIORITY_INVALID` |
| `start_at` / `start_date_only` | datetime \| date | N | không được sau `due_at` | cả hai cùng null là hợp lệ (Nhóm 06: "chưa xếp lịch") |
| `due_at` / `due_date_only` / `due_timezone` / `due_is_date_only` | — | N | `due_timezone` bắt buộc khi `due_is_date_only = true` | `due_date_only` được server chuẩn hoá thành `due_at` = 23:59:59 tz người đặt; client **không** tự quy đổi |
| `milestone_id` | uuid \| null | N | Milestone phải thuộc **cùng** Project | cấm khi `depth = 1` → `422 SUBTASK_MILESTONE_FORBIDDEN` |
| `assignee_user_ids[]` | array\<uuid\> | N | 0..50 phần tử, không trùng | mỗi user phải là `project_membership` ACTIVE, nếu không `422 ASSIGNEE_NOT_PROJECT_MEMBER`. Mảng rỗng hợp lệ (Task 0 assignee) |
| `tag_ids[]` | array\<uuid\> | N | 0..20, tag phải thuộc **cùng Workspace** với Project | `422 TAG_NOT_IN_WORKSPACE`; trùng trong Workspace → `409 TAG_DUPLICATE_IN_WORKSPACE` khi tạo tag mới qua `EP-058` |
| `checklist_items[]` | array | N | 0..100; mỗi phần tử `title` 1..200, `position` int | `position` trùng → `409 CHECKLIST_POSITION_CONFLICT` |
| `depends_on_task_ids[]` | array\<uuid\> | N | 0..50, cùng Project, không tự trỏ | tạo chu trình → `422 DEPENDENCY_CYCLE_DETECTED` (`guard_dependency_acyclic`); khác Project → `422 DEPENDENCY_CROSS_PROJECT` |
| `client_draft_id` | string \| null | N | ≤ 64, `[A-Za-z0-9._-]` | **không** phải `Idempotency-Key`; chỉ để đối chiếu draft AI (`EP-200`) với Task thật sau khi user tạo |

### 6.3 `TaskUpdateRequest`

Body của `EP-097`. Ngữ nghĩa `PATCH`: **chỉ** các khoá có mặt được cập nhật; khoá vắng = không đổi. Đây là điểm duy nhất mà "khoá vắng" mang nghĩa khác §6.0, vì đây là request chứ không phải response.

| Trường | Kiểu | Bắt buộc | Ràng buộc | Ghi chú |
|---|---|---|---|---|
| `version` | integer | Y | phải khớp `If-Match` | body và header lệch nhau → `400 IF_MATCH_MISMATCH` (không đoán cái nào đúng) |
| `title` | string | N | 1..300 | — |
| `description` | string \| null | N | ≤ 20000 | gửi `null` tường minh = xoá mô tả |
| `priority` | enum | N | — | đổi priority khi Task ở `PENDING_APPROVAL` → `409 TASK_LOCKED_PENDING_APPROVAL` (AUD-06) |
| `blocked_flag` | boolean | N | — | `true` mà thiếu `blocked_reason` → `422 BLOCKED_REASON_REQUIRED`; `false` thì server tự đặt `blocked_reason = null` |
| `blocked_reason` | string \| null | N | ≤ 1000 | CHECK ở 02A §5.1 |
| `start_at` / `start_date_only` | — | N | — | — |
| `due_at` / `due_date_only` / `due_timezone` / `due_is_date_only` | — | N | — | đổi hạn **luôn** ghi `task_deadline_change` (append-only, 02A §8.3) với `old_due_at`, `new_due_at`, `change_reason_key`, và có thể tái tính `reminder_schedule` qua job `REMINDER_RECOMPUTE` |
| `milestone_id` | uuid \| null | N | cùng Project; cấm khi `depth = 1` | `null` = bỏ liên kết. Xoá Milestone chỉ bỏ liên kết Task, không xoá Task (Nhóm 03) |
| `parent_task_id` | uuid \| null | N | chỉ WM/PM; parent mới phải `depth = 0`, cùng Project | di chuyển Task kèm subtask → `422 SUBTASK_MOVE_NOT_SUPPORTED` ở V1 (giữ bất biến một cấp) |
| `state` | — | **cấm** | — | không đổi state bằng `PATCH`. Dùng `EP-125`..`EP-130`. Gửi `state` → `422 STATE_CHANGE_VIA_WORKFLOW_ONLY` (05 §1.1) |

### 6.4 `WorkflowActionRequest`

Body chung cho `EP-125` (start), `EP-126` (submit), `EP-127` (approve), `EP-128` (reject), `EP-129` (cancel), `EP-130` (reopen), `EP-134`/`EP-135` (chỉ định / thu hồi approver). Không có action nào trong body: action nằm ở **đường dẫn**, để `AuthorizationPolicy` map 1-1 với mã quyền.

| Trường | Kiểu | Bắt buộc | Ràng buộc | Ghi chú |
|---|---|---|---|---|
| `version` | integer | Y | khớp `If-Match`, là `task.version` | lệch → `400 IF_MATCH_MISMATCH`; stale → `409 VERSION_CONFLICT` kèm `error.resource_version` |
| `reason` | string | Y với reject/cancel/reopen/revoke-approver; N với start/submit/approve | ≥ 3 ký tự sau `btrim` cho reject (`ck_decision_reject_reason`); ≥ 10 ký tự cho reopen và revoke-approver | thiếu → `422 REASON_REQUIRED`; quá ngắn → `422 REASON_TOO_SHORT`. `reason` được ghi vào `audit_event.reason` và `approval_decision.reason` |
| `reason_code` | enum \| null | N | danh sách đóng theo action | reject: `INCOMPLETE_WORK`,`QUALITY_ISSUE`,`SCOPE_MISMATCH`,`EVIDENCE_MISSING`,`OTHER`. cancel: `DUPLICATE`,`NO_LONGER_NEEDED`,`BLOCKED_PERMANENT`,`PROJECT_COMPLETED_CASCADE`,`OTHER`. reopen: `APPROVED_BY_MISTAKE`,`NEW_REQUIREMENT`,`DEFECT_FOUND`,`OTHER`. Mã ngoài danh sách → `422 REASON_CODE_UNKNOWN` |
| `round_id` | uuid \| null | N | mặc định round OPEN hiện hành của Task | truyền round đã đóng → `409 ROUND_NOT_OPEN`; round không thuộc Task → `404 ROUND_NOT_FOUND` (không `403`) |
| `target_user_id` | uuid | Y với `EP-134`/`EP-135`; cấm với action khác | phải là `project_membership` ACTIVE; `EP-134` không được trỏ chính actor khi actor là ASG hoặc SUB | `EP-134` tạo `approval_designation(designation_type='DESIGNATED')`; `EP-135` set `revoked_at` + `revoked_reason` |
| `evidence_attachment_ids[]` | array\<uuid\> | N | 0..10; attachment phải `target_type='TASK'`/`'SUBTASK'` và cùng Task | dùng cho approve/reject. Attachment không thuộc Task → `422 EVIDENCE_NOT_IN_SCOPE`. Không upload file mới trong request workflow |
| `acknowledge_cascade` | boolean | N, mặc định `false` | bắt buộc `true` cho `EP-129` khi Task có subtask chưa terminal hoặc có round OPEN | `false` → `409 CASCADE_ACK_REQUIRED` kèm `details[]` liệt kê `subtask_count` và `open_round_count` (chỉ số lượng, không nội dung). Cascade huỷ round ghi `approval_decision(decision_outcome='CANCELLED', actor_type='SYSTEM_RULE')` (AUD-01) |
| `correlation_hint` | string \| null | N | ≤ 64 | **không** thay `X-Correlation-Id`; chỉ dành cho client cần gắn mã nội bộ của họ. Không ghi vào `audit_event` |

### 6.5 `ApprovalDecisionResponse`

Body của `EP-127` và `EP-128`. Được tạo **sau khi transaction 10 bước ở 04-RBAC §8.2 commit**; nếu bất kỳ bước nào fail, trả error chứ không trả DTO này một phần.

| Trường | Kiểu | Bắt buộc | Ràng buộc | Ghi chú |
|---|---|---|---|---|
| `decision_id` | uuid | Y | — | `approval_decision.id`; append-only, không có endpoint sửa |
| `task_id` | uuid | Y | — | — |
| `task_state` | enum | Y | sau quyết định | approve → `COMPLETED`; reject → `IN_PROGRESS` (05 T-05/T-06) |
| `task_version` | integer | Y | version mới | echo trong `ETag` |
| `round_id` / `round_no` | uuid / integer | Y | — | round vừa đóng |
| `round_status` | enum | Y | `APPROVED` hoặc `REJECTED` | `approval_round.status`; round đã đóng là bất biến |
| `round_outcome` | enum | Y | `APPROVED`,`REJECTED`,`CANCELLED` | AUD-01: `CANCELLED` là kết quả huỷ vòng, không phải approve/reject giả mạo |
| `decision_outcome` | enum | Y | `APPROVED`,`REJECTED` | — |
| `decided_by` | uuid | Y | = actor | `guard_no_self_approval()` đã chặn actor là SUB hoặc `submitted_by` |
| `decided_at` | datetime | Y | — | — |
| `actor_type` | enum | Y | `USER` hoặc `SYSTEM_RULE` | quyết định qua HTTP luôn là `USER`; `SYSTEM_RULE` chỉ xuất hiện khi đọc lịch sử cascade |
| `reason` | string \| null | Y | NOT NULL khi `REJECTED` | — |
| `reason_code` | enum \| null | Y | — | — |
| `sla_breached` | boolean | Y | cờ tính toán | AUD-08: **không** phải quyết định, không tự đổi outcome |
| `audit_event_id` | uuid | Y | — | bằng chứng audit ghi trong cùng transaction |
| `correlation_id` | string | Y | — | — |
| `next_round` | object \| null | Y | `null` sau approve | sau reject: round mới **chỉ** được tạo khi SUB submit lại (`EP-126`); `next_round` ở đây luôn `null` và response kèm `next_action_hint = 'RESUBMIT_REQUIRED'` |
| `notifications_queued` | integer | Y | ≥ 0 | số `outbox_event` đã ghi trước commit; **không** phải bằng chứng đã gửi (ARCH-04) |

### 6.6 `ProjectResponse`

| Trường | Kiểu | Bắt buộc | Ràng buộc | Ghi chú |
|---|---|---|---|---|
| `id`, `workspace_id` | uuid | Y | — | — |
| `name` | string | Y | 1..160; unique trong Workspace theo `lower(name)` | — |
| `description` | string \| null | Y | — | bị loại khi actor chỉ có `P-02` (metadata) |
| `status` | enum | Y | `PLANNING`,`ACTIVE`,`ON_HOLD`,`COMPLETED`,`ARCHIVED`,`DELETED` | 02A §4.1 |
| `effective_state` | enum | Y | 04-RBAC §3 | `PLANNING`,`ACTIVE`,`ON_HOLD_READONLY`,`COMPLETED_READONLY`,`ARCHIVED_READONLY`,`DELETED` |
| `previous_status` | enum \| null | Y | — | dùng cho unarchive/restore "về trạng thái trước đó" (Nhóm 03) |
| `approval_enabled` | boolean | Y | — | nghiệm thu bật/tắt theo Project (Nhóm 03) |
| `allow_member_create_task` | boolean | Y | — | Member tạo Task chỉ khi cờ này `true` |
| `my_role` | enum \| null | Y | `OWNER_MANAGER`,`MANAGER`,`MEMBER`,`VIEWER`,`SPECIAL_GRANT`,`null` | `null` khi truy cập qua `special_grant` không gắn role |
| `my_participation` | object | Y | `is_participant`, `participation_source[]` | AUD-50: nguồn participation (`PROJECT_MEMBERSHIP`,`TASK_ASSIGNEE`,`TASK_CREATOR`,`APPROVAL_DESIGNATION`,`SPECIAL_GRANT`) |
| `managers[]` | array | Y | `user_id`, `display_name`, `status` | bất biến ≥ 1 Manager ACTIVE (02A §4.1, I-04); mảng không bao giờ rỗng khi Project chưa `DELETED` |
| `member_count` | `MetricEnvelope` | Y | §6.7 | `NO_PERMISSION` với PG |
| `progress` | object | Y | xem dưới | AUD-20 |
| `milestone_summary` | object | Y | `open_count`, `completed_count`, `cancelled_count`, `overdue_count` | AUD-21; mỗi số là một `MetricEnvelope` khi trả qua `EP-182`/`EP-193` |
| `task_state_summary` | object | Y | `todo`,`in_progress`,`pending_approval`,`completed`,`cancelled`,`blocked`,`overdue`,`overdue_pending_approval` | `blocked` là **cờ riêng**, không cộng vào state (metric dictionary 02B §3.4) |
| `unique_task_count` / `assignment_count` | integer | Y | luôn trả **cả hai** | AUD-37: một Task nhiều người nhận sẽ làm `assignment_count` lớn hơn `unique_task_count`; không có trường "tổng task" nhập nhằng |
| `completed_at` / `completed_by` / `completed_cascade_audit_id` | — | Y | — | AUD-01: link tới audit event cascade |
| `archived_at` / `archived_by` | — | Y | — | — |
| `permissions` | object | Y | khối tư vấn | `can_update`, `can_change_status`, `can_archive`, `can_delete`, `can_restore`, `can_manage_members`, `can_change_approval_setting`, `can_create_task`, `can_read_content`, `can_read_audit` |
| `version` | integer | Y | — | — |

`progress` (AUD-20) — **bắt buộc** đủ bốn khoá, không được suy ra `display` từ `percent`:

| Trường | Kiểu | Ràng buộc | Ghi chú |
|---|---|---|---|
| `numerator` | integer \| null | `COUNT(task WHERE depth=0 AND state='COMPLETED')` | `null` khi `value_state ≠ 'OK'` |
| `denominator` | integer \| null | `COUNT(task WHERE depth=0) − COUNT(task WHERE depth=0 AND state='CANCELLED')` | `0` là giá trị hợp lệ và là tín hiệu `NOT_APPLICABLE` |
| `percent` | number \| null | làm tròn **xuống** 2 chữ số thập phân; kẹp 0..100 | `null` khi `denominator = 0` — không trả `0`, không trả `100` |
| `display` | string | `"N/A"` \| `"42.5%"` \| `"<1%"` \| `">99%"` | chuỗi duy nhất UI được phép hiển thị; `"<1%"` khi `0 < percent < 1`, `">99%"` khi `99 < percent < 100` — tránh hiện `0%` cho Project đang chạy và `100%` cho Project chưa xong hết |
| `metric_code` | string | cố định `PROGRESS_PERCENT` | 02B §3.4 |
| `value_state` | enum | `OK`,`NOT_APPLICABLE`,`NO_PERMISSION`,`INSUFFICIENT_DATA`,`PARTIAL` | AUD-38 |
| `as_of` | datetime | thời điểm tính | — |

```json
{
  "progress": { "metric_code": "PROGRESS_PERCENT", "numerator": 0, "denominator": 0, "percent": null, "display": "N/A", "value_state": "NOT_APPLICABLE", "as_of": "2026-10-15T09:12:44Z" },
  "task_state_summary": { "todo": 0, "in_progress": 0, "pending_approval": 0, "completed": 0, "cancelled": 0, "blocked": 0, "overdue": 0, "overdue_pending_approval": 0 },
  "unique_task_count": 0,
  "assignment_count": 0
}
```

### 6.7 `MetricEnvelope`

Dùng cho **mọi** số liệu tổng hợp ở `EP-181`..`EP-186`, `EP-193`, và các trường bộ đếm trong `TaskResponse`/`ProjectResponse`/`WorkspaceResponse`.

| Trường | Kiểu | Bắt buộc | Ràng buộc | Ghi chú |
|---|---|---|---|---|
| `metric_code` | string | Y | thuộc metric dictionary 02B §3.4 | mã ngoài dictionary → `422 METRIC_CODE_UNKNOWN` |
| `value_state` | enum | Y | `OK`,`NOT_APPLICABLE`,`NO_PERMISSION`,`INSUFFICIENT_DATA`,`PARTIAL` | **bắt buộc** có mặt; không có trạng thái ngầm định |
| `value_int` | integer \| null | Y | NOT NULL khi `value_state = 'OK'` và metric là đếm | `0` là giá trị thật, khác `null` |
| `value_numeric` | number \| null | Y | `numeric(18,4)` | dùng cho phút, byte, phần trăm |
| `numerator` / `denominator` | integer \| null | Y | bắt buộc có mặt với metric dạng tỉ lệ | AUD-20 |
| `percent` | number \| null | Y | 0..100, 2 chữ số | — |
| `display` | string | Y | chuỗi UI được phép hiện | §6.6 |
| `state_reason_code` | string \| null | Y khi `value_state ≠ 'OK'` | danh sách ở dưới | giải thích **tại sao** không phải `OK`, không phải thông điệp |
| `scope_type` / `scope_id` | enum / uuid \| null | Y | — | — |
| `as_of` | datetime \| date | Y | — | `date` khi đọc từ `metric_daily_snapshot` |
| `source` | enum | Y | `LIVE`,`SNAPSHOT` | `SNAPSHOT` = ngày đã chốt, bất biến (AUD-15) |
| `included_project_count` | integer \| null | Y khi `value_state = 'PARTIAL'` | ≥ 0 | chỉ số Project **được** tính. Không có `hidden_project_count`, không có `total_project_count` (AUD-38) |
| `drill_down_allowed` | boolean | Y | — | `false` với PG (R-02): client không được gọi `EP-089`/`EP-094` từ ô số liệu này |

Bảng phân biệt bắt buộc (AUD-38) — ba tình huống dưới đây **không** được trả về cùng một hình dạng:

| Tình huống | `value_state` | `value_int` | `display` | `state_reason_code` |
|---|---|---|---|---|
| Đếm được và kết quả là không | `OK` | `0` | `"0"` | `null` |
| Công thức chia cho 0 (Project chưa có Task top-level) | `NOT_APPLICABLE` | `null` | `"N/A"` | `DENOMINATOR_ZERO` |
| Actor không có quyền đọc scope này | `NO_PERMISSION` | `null` | `"—"` | `SCOPE_READ_DENIED` |
| Snapshot ngày này chưa chạy / job chưa xong | `INSUFFICIENT_DATA` | `null` | `"—"` | `SNAPSHOT_NOT_AVAILABLE` |
| Chỉ tính được trên một phần scope (chỉ Project có participation) | `PARTIAL` | số đếm được | `"12 (một phần)"` → client render theo `display` server gửi | `SCOPE_PARTIALLY_VISIBLE` |

```json
{
  "metric_code": "APPROVAL_AVG_WAIT_MINUTES",
  "value_state": "PARTIAL",
  "value_int": null,
  "value_numeric": 184.5000,
  "numerator": 37,
  "denominator": 5,
  "percent": null,
  "display": "184.5 phút (một phần)",
  "state_reason_code": "SCOPE_PARTIALLY_VISIBLE",
  "scope_type": "WORKSPACE",
  "scope_id": "0197c0a2-8888-7000-8000-0000000000e9",
  "as_of": "2026-10-14",
  "source": "SNAPSHOT",
  "included_project_count": 5,
  "drill_down_allowed": true
}
```

### 6.8 `AttachmentResponse`

| Trường | Kiểu | Bắt buộc | Ràng buộc | Ghi chú |
|---|---|---|---|---|
| `id` | uuid | Y | — | `attachment.id` |
| `target_type` | enum | Y | `TASK`,`SUBTASK`,`PROJECT`,`COMMENT`,`MILESTONE`,`AVATAR`,`REPORT_EXPORT` | 02A §7.1 |
| `target_id` | uuid | Y | — | — |
| `workspace_id` / `project_id` | uuid / uuid \| null | Y | — | denormalize bắt buộc để lọc scope và redact khi mất quyền |
| `display_name` | string | Y | ≤ 255 | tên gốc client đặt; **không** phải `object_key` |
| `content_type` | string | Y | thuộc allowlist `file.allowed_content_types` | — |
| `size_bytes` | integer | Y | > 0 | của `current_version` |
| `status` | enum | Y | `STAGING`,`SCANNING`,`AVAILABLE`,`QUARANTINED`,`BLOCKED`,`DELETED` | Nhóm 09 |
| `scan_status` | enum | Y | `PENDING`,`CLEAN`,`INFECTED`,`UNSUPPORTED`,`ERROR` | `UNSUPPORTED` khi `file.scan_enabled=false` — client **không** được hiển thị như "đã quét an toàn" |
| `scan_status_note_key` | string | Y | i18n key | server không render câu chữ (§1.1) |
| `uploaded_by` / `uploaded_at` | uuid / datetime | Y | — | — |
| `current_version_no` | integer | Y | ≥ 1 | — |
| `version_count` | integer | Y | ≥ 1 | — |
| `sha256` | string | Y | 64 hex | của `current_version`; bằng chứng toàn vẹn, không phải đường dẫn |
| `preview_allowed` / `download_allowed` | boolean | Y | tính tại thời điểm response | FL-02 kiểm tra lại ở **mỗi** request; giá trị này không phải giấy phép dùng lại |
| `preview_endpoint` / `download_endpoint` | string \| null | Y | đường dẫn **tương đối** `/api/v1/attachments/{id}/preview` | không bao giờ là URL S3/MinIO, không kèm query token (ARCH-07) |
| `latest_version` | object | Y | `version_no`, `created_at`, `created_by`, `change_note`, `size_bytes`, `sha256` | — |
| `versions[]` | array | N | chỉ khi `EP-150` | khôi phục phiên bản cũ = tạo phiên bản **mới** trỏ object cũ (FL-06), không ghi đè |
| `restorable` | boolean | Y | `true` chỉ khi `status = 'DELETED'` **và** parent còn hợp lệ | FL-04 |
| `delete_group_id` | uuid \| null | N | chỉ khi actor có quyền restore | AUD-29 |
| `version` | integer | Y | — | `attachment` có optimistic lock |

**Không bao giờ xuất hiện:** `bucket`, `object_key`, `staging_object_key`, `storage_class`, presigned URL, đường dẫn tĩnh, `StreamTicket`.

### 6.9 `FileUploadInitiateRequest`

| Trường | Kiểu | Bắt buộc | Ràng buộc | Ghi chú |
|---|---|---|---|---|
| `target_type` | enum | Y | như §6.8 | `AVATAR` chỉ cho chính actor; `REPORT_EXPORT` **không** nhận từ client (chỉ job tạo) → `422 TARGET_TYPE_NOT_CLIENT_CREATABLE` |
| `target_id` | uuid | Y | phải tồn tại và actor có quyền ghi trên đối tượng đó | `guard_attachment_target_exists()`; Task `PENDING_APPROVAL` → `409 TASK_LOCKED_PENDING_APPROVAL` (AUD-06) |
| `display_name` | string | Y | 1..255 | giữ nguyên tên người dùng đặt, kể cả ký tự Unicode; server không dùng tên này làm khoá lưu trữ |
| `declared_size` | integer | Y | > 0 và ≤ `file.max_upload_mb` × 1048576 | vượt → `413 FILE_TOO_LARGE`; `declared_size` khác tổng byte thật → `422 DECLARED_SIZE_MISMATCH` lúc commit |
| `declared_content_type` | string | Y | thuộc allowlist | ngoài allowlist → `415 CONTENT_TYPE_NOT_ALLOWED` **ngay tại initiate**, không đợi upload xong |
| `sha256` | string \| null | N | 64 hex | nếu gửi, commit sẽ đối soát; lệch → `422 CHECKSUM_MISMATCH` và object bị dọn |
| `upload_mode` | enum | Y | `SINGLE_PUT`,`CHUNKED` | `CHUNKED` bắt buộc khi `declared_size` > `file.chunk_threshold_mb` |
| `chunk_size_bytes` | integer \| null | Y khi `CHUNKED` | bội số của 1 MiB, trong khoảng `[file.chunk_min_bytes, file.chunk_max_bytes]` | server có thể **hạ** xuống giới hạn cấu hình và trả giá trị đã chốt trong response |

### 6.10 `FileUploadInitiateResponse`

| Trường | Kiểu | Bắt buộc | Ràng buộc | Ghi chú |
|---|---|---|---|---|
| `upload_session_id` | uuid | Y | — | `upload_session.id`; dùng trong path `EP-145`, `EP-146`, `EP-147` |
| `attachment_id` | uuid | Y | — | attachment ở `status='STAGING'`; **chưa** đọc được qua `EP-149` cho tới khi commit |
| `status` | enum | Y | `INITIATED` | 02A §7.4 |
| `expires_at` | datetime | Y | TTL cấu hình; hết hạn → job `FILE_CLEANUP_ORPHAN` dọn object STAGING | `410 UPLOAD_SESSION_EXPIRED` khi dùng lại |
| `upload_mode` | enum | Y | như request hoặc đã hạ giới hạn | — |
| `chunk_size_bytes` | integer \| null | Y khi `CHUNKED` | giá trị **đã chốt** bởi server | client phải chia theo giá trị này, không theo giá trị mình gửi |
| `put_path` | string | Y | `/api/v1/files/upload-sessions/{id}/bytes` | đường dẫn tương đối qua Backend gateway (ARCH-07) |
| `required_headers[]` | array\<string\> | Y | `Content-Type`, `Content-Range`, `X-Correlation-Id` | với `SINGLE_PUT` thì `Content-Range` là `bytes 0-{n-1}/{n}` |
| `commit_path` | string | Y | `/api/v1/files/upload-sessions/{id}/commit` | `EP-146` |
| `scan_enabled` | boolean | Y | = `file.scan_enabled` | `false` → `scan_status` sẽ là `UNSUPPORTED`, không phải `CLEAN` |
| `max_upload_mb` | integer | Y | — | trả ở đây vì đây là thông tin cấu hình **áp dụng cho chính request của actor**, không phải ngưỡng rate limit (§1.5) |

### 6.11 `NotificationResponse`

| Trường | Kiểu | Bắt buộc | Ràng buộc | Ghi chú |
|---|---|---|---|---|
| `id` | uuid | Y | — | — |
| `seq` | integer | Y | monotonic theo user | dùng cho resync `EP-179` và `last_seen_seq` của WebSocket. **Điểm chưa chốt:** 02B §2.1 không liệt kê cột `seq`, chỉ §2.5 yêu cầu — xem §11 |
| `type` | enum | Y | 18 giá trị ở 02B §2.1 | — |
| `severity` | enum | Y | `NORMAL`,`IMPORTANT`,`SECURITY_CRITICAL` | `SECURITY_CRITICAL` không mute được (N-03) |
| `scope_type` / `scope_id` | enum / uuid \| null | Y | — | `scope_id` bị che thành `null` khi `redacted = true` |
| `title_key` / `body_key` | string | Y | i18n key | server không render câu chữ |
| `payload` | object \| null | Y | `null` khi `redacted = true` | dữ liệu tối thiểu để render/deep link |
| `deep_link` | string \| null | Y | `null` khi `redacted = true` | **phải** được server validate lại quyền khi click (AUD-13); client không mở link này mà không gọi REST |
| `redacted` | boolean | Y | — | `true` → `payload`, `deep_link`, `scope_id` đã bị che; dòng vẫn nằm trong danh sách để không phá thứ tự và `seq` |
| `redacted_at` | datetime \| null | Y | — | — |
| `redaction_reason_key` | string \| null | Y | i18n key | ví dụ `notification.redacted.project_access_revoked`. Không trả `redaction_reason` thô nếu chứa tên đối tượng |
| `read_at` | datetime \| null | Y | — | đồng bộ cấp **tài khoản**, không theo thiết bị (Nhóm 06) |
| `occurred_at` | datetime | Y | — | thứ tự sắp xếp mặc định |
| `quiet_hours_deferred_until` | datetime \| null | Y | — | khác `null` = push bị hoãn; in-app vẫn hiện |
| `bound_to_current_session` | boolean | Y | — | thay cho `session_id_bound`: không trả `session_id` của phiên khác (NFR-22) |
| `channels[]` | array\<enum\> | Y | `IN_APP`,`PUSH`,`EMAIL` | kênh đã lên lịch theo `notification_preference`; không phải bằng chứng đã gửi |
| `delivery_state` | enum | Y | `PENDING`,`SENT`,`PARTIALLY_FAILED`,`FAILED`,`DEFERRED` | tổng hợp từ `delivery_attempt`; `error_detail` **không** được trả (02B §2.4) |

### 6.12 `ReportExportJobResponse`

| Trường | Kiểu | Bắt buộc | Ràng buộc | Ghi chú |
|---|---|---|---|---|
| `id` | uuid | Y | — | `report_export_job.id` |
| `background_job_id` | uuid | Y | — | tra cứu thêm qua `EP-228` |
| `report_code` / `report_name_key` | string | Y | — | `report_definition.code` + i18n key |
| `scope_type` / `scope_id` | enum / uuid | Y | — | — |
| `period_from` / `period_to` | date \| datetime | Y | — | — |
| `format` | enum | Y | `PDF`,`XLSX`,`CSV` | Nhóm 08 |
| `status` | enum | Y | `QUEUED`,`RUNNING`,`REAUTHORIZING`,`GENERATED`,`DELIVERED`,`FAILED`,`CANCELLED_REVOKED`,`EXPIRED` | 02B §3.3. `CANCELLED_REVOKED` = mất quyền giữa chừng; **không** phân biệt được với "không tồn tại" qua HTTP status (vẫn `200` ở `EP-190`, nhưng `EP-192` trả `403`) |
| `requested_by` | uuid | Y | = actor | người khác nhận `404` ở `EP-190`/`EP-192`, **kể cả SA** (AUD-16) |
| `row_count` | integer \| null | Y | — | `null` trước khi `GENERATED` |
| `metric_state_summary` | object \| null | Y | map `metric_code` → `value_state` | để UI cảnh báo "báo cáo có ô N/A" trước khi tải |
| `expires_at` | datetime | Y | TTL `report.export_ttl_hours` (mặc định 24h) | quá hạn → `410 EXPORT_EXPIRED` |
| `authorized_at_generation` | datetime \| null | Y | — | bằng chứng kiểm tra quyền lần 1 |
| `authorized_at_delivery` | datetime \| null | Y | — | bằng chứng kiểm tra quyền lần 2; chỉ set sau `EP-192` thành công |
| `download_endpoint` | string \| null | Y | `null` khi chưa `GENERATED` | `/api/v1/reports/export-jobs/{id}/download` |
| `error_code` | string \| null | Y | — | `null` khi không lỗi; không kèm `error_message` thô |
| `created_at` / `correlation_id` | — | Y | — | — |

### 6.13 `AiSuggestionResponse`

| Trường | Kiểu | Bắt buộc | Ràng buộc | Ghi chú |
|---|---|---|---|---|
| `id` | uuid | Y | — | `ai_suggestion.id` |
| `ai_job_id` | uuid | Y | — | — |
| `feature` | enum | Y | 10 giá trị ở 02B §4.2 | — |
| `suggestion_type` | enum | Y | `TASK_DRAFT`,`SUBTASK_LIST`,`PRIORITY`,`DEADLINE`,`ASSIGNEE`,`SEARCH_RESULT`,`PLANNER_ITEM`,`REVIEW_INSIGHT` | — |
| `status` | enum | Y | `DRAFT`,`SHOWN`,`ACCEPTED`,`REJECTED`,`EXPIRED`,`REDACTED` | **không có** `APPLIED_BY_AI` (AUD-47) |
| `payload` | object \| null | Y | đã validate JSON schema | `null` khi `redacted = true` hoặc `status = 'REDACTED'` |
| `draft` | object \| null | Y | chỉ có sau `EP-200` | bản chuẩn hoá sẵn để đưa vào `TaskCreateRequest`/`TaskUpdateRequest`. **Không** phải Task đã tạo |
| `next_action_endpoint` | object \| null | Y | `method` + `path` + `required_headers[]` | chỉ `EP-092`, `EP-093`, `EP-097`, `EP-134`. Không có đường tắt nào cho AI tự ghi |
| `validation_result` | object | Y | `schema_version`, `dropped_fields[]`, `out_of_scope_refs[]`, `truncated` | AUD-40: trường nào bị loại vì ngoài scope, có lý do |
| `explanation` | string \| null | Y | ≤ 2000 | Nhóm 07: gợi ý priority có giải thích |
| `confidence` | number \| null | Y | 0..1, 3 chữ số | `null` khi model không trả; **không** được suy ra từ `validation_result` |
| `redacted` | boolean | Y | — | `true` → `payload`/`draft` = `null`, `scope_id` bị che |
| `redacted_at` / `redaction_reason_key` | — | Y | — | AUD-13, AUD-39 |
| `scope_type` / `scope_id` | enum / uuid \| null | Y | — | — |
| `principal_user_id` | uuid | Y | = actor | luôn là chính actor; người khác nhận `404` |
| `resulting_task_id` | uuid \| null | Y | — | chỉ được điền bởi `EP-092`/`EP-093` khi user thật tạo Task từ draft — **không** bởi `EP-200` |
| `accepted_at` / `accepted_by` | — | Y | — | — |
| `created_at` | datetime | Y | — | — |

**Không bao giờ xuất hiện:** prompt, completion thô, `content_ref`, `ollama_base_url`, `system prompt`, `context` đầy đủ. Chỉ `token_count` và `latency_ms` ở cấp `ai_job`.

### 6.14 `AuditEventResponse`

| Trường | Kiểu | Bắt buộc | Ràng buộc | Ghi chú |
|---|---|---|---|---|
| `id` | uuid | Y | — | — |
| `occurred_at` | datetime | Y | — | khoá phân vùng; không sửa được |
| `actor_type` | enum | Y | `USER`,`SYSTEM_RULE`,`JOB`,`CLI`,`MIGRATION` | 02B §6.1 |
| `actor_user_id` | uuid \| null | Y | `null` khi `actor_type ≠ 'USER'` | — |
| `actor_display_name` | string \| null | Y | `null` khi actor đã bị disable/delete hoặc khi người đọc không còn quyền đọc tài khoản đó | không trả email, không trả `username` |
| `actor_session_id` | uuid \| null | Y | — | — |
| `action` | string | Y | khớp mã trong 04-RBAC | — |
| `target_type` / `target_id` | string / uuid \| null | Y | — | — |
| `scope_type` / `scope_id` | enum / uuid \| null | Y | `SYSTEM`,`WORKSPACE`,`PROJECT`,`TASK`,`USER` | AUD-36 |
| `sensitivity` | enum | Y | `NORMAL`,`SENSITIVE_CONTENT` | AUD-36: dòng `SENSITIVE_CONTENT` chỉ đọc được qua đường (c) của FL-09 |
| `before` / `after` | object \| null | Y | có thể bị lược trường nhạy cảm → kèm `before_after_redacted = true` | Nhóm 09 "before-after" |
| `before_after_redacted` | boolean | Y | — | — |
| `reason` | string \| null | Y | NOT NULL với emergency/reopen/reject (CHECK + trigger) | — |
| `reason_code` | string \| null | Y | ví dụ `PROJECT_COMPLETED_CASCADE` | AUD-01 |
| `outcome` | enum | Y | `SUCCESS`,`DENIED_FORBIDDEN`,`DENIED_NOT_FOUND`,`DENIED_STATE`,`DENIED_REVOKED`,`DENIED_SELF_APPROVAL`,`DENIED_LAST_MANAGER`,`DENIED_VERSION_CONFLICT`,`FAILED` | 02B §6.1 — nguồn chính cho negative test và phát hiện IDOR |
| `reauth_ticket_id` | uuid \| null | Y | — | bằng chứng reauth cho thao tác nhạy cảm |
| `grant_path` | enum | Y | `GRANT_SYSTEM`,`SPECIAL_GRANT`,`OBJECT_READ` | **trường suy dẫn, không có cột**: lý do actor được đọc dòng này (FL-09). Giúp audit chính khả kiểm |
| `ip_label` | string \| null | Y | đã che, ví dụ `10.20.*.*` | không trả `ip_address` đầy đủ |
| `user_agent_label` | string \| null | Y | đã rút gọn (tên client + phiên bản lớn) | không trả chuỗi UA thô |
| `correlation_id` | string | Y | — | — |
| `entry_hash` | string | Y | 64 hex | hash của chính dòng này |
| `prev_hash` | string \| null | Y | 64 hex \| `null` ở dòng đầu | digest, không phải nội dung: biết `prev_hash` **không** cho phép đọc dòng trước (vẫn cần FL-09). Verify chain là job `VERIFY_HASH_CHAIN`, không có endpoint HTTP |

### 6.15 `PageResponse<T>`

| Trường | Kiểu | Bắt buộc | Ràng buộc | Ghi chú |
|---|---|---|---|---|
| `items[]` | array\<T\> | Y | 0..`limit` | mảng rỗng là hợp lệ và **khác** với `404` |
| `next_cursor` | string \| null | Y | `null` khi `has_more = false` | opaque, base64url; client không giải mã, không tự dựng |
| `has_more` | boolean | Y | — | — |
| `limit` | integer | Y | 1..100 | giá trị **đã áp dụng**, có thể nhỏ hơn client yêu cầu (§3.1) |
| `total_count` | integer \| null | N | chỉ ở endpoint được phép theo §3.1 | mặc định **vắng mặt**; không suy ra `0` |
| `filters_applied` | object | Y | echo các filter server đã chấp nhận | filter bị bỏ qua vì thiếu quyền **không** xuất hiện ở đây và không báo lỗi — nhưng `scope_note_key` sẽ giải thích |
| `scope_note_key` | string \| null | Y | i18n key \| null | ví dụ `list.partial.workspace_dashboard` khi kết quả bị cắt theo participation (AUD-38) |

### 6.16 `ErrorResponse`

Ngữ nghĩa từng trường, quy tắc `message_key`, và cách dùng `details[]` đã nêu ở §2.1 — không lặp lại. Bảng này chỉ chốt **kiểu** và tính bắt buộc để hai client (React/TS và Flutter) sinh cùng một model:

| Trường | Kiểu | Bắt buộc | Ràng buộc |
|---|---|---|---|
| `error.code` | string | Y | `UPPER_SNAKE_CASE`, thuộc §2.3 hoặc §2.4 |
| `error.message_key` | string | Y | i18n key; **không** phải câu chữ đã render; không chứa dữ liệu người dùng |
| `error.reason_code` | string \| null | Y | chi tiết máy đọc được; `null` khi `code` đã đủ nghĩa |
| `error.details[]` | array\<object\> | Y (có thể rỗng) | mỗi phần tử: `field` \| `item_id` \| `key` (ít nhất một), `issue_code`, `message_key`, `constraint` \| `expected` \| `actual` (tuỳ `issue_code`) |
| `error.reauth_required` | boolean | Y | `true` chỉ đi kèm `428 REAUTH_REQUIRED` |
| `error.retry_after_seconds` | integer \| null | Y | khác `null` với `429` và `503`; khớp header `Retry-After` |
| `error.resource_version` | integer \| null | Y | khác `null` với `409 VERSION_CONFLICT` khi actor có quyền đọc |
| `error.resource_state` | object \| null | Y | `null` hoặc `{ "state": …, "effective_state": …, "locked_actions": […] }` — chỉ khi actor có quyền đọc tài nguyên |
| `correlation_id` | string | Y | ở **cấp cao nhất**, không lồng trong `error` |
| `http_status` | integer | N | có mặt trong body để client log được khi tầng HTTP đã mất ngữ cảnh; không phải nguồn chân lý |

---

## 7. Realtime contract (ARCH-09)

### 7.1 Kênh kết nối và xác thực

| Mục | Quy ước đề xuất | Căn cứ |
|---|---|---|
| Endpoint | `EP-180` — `GET /api/v1/realtime` với `Upgrade: websocket`, subprotocol `v1.stm.realtime` | ARCH-09, §1.1 (cùng base path) |
| Tham số | `token` = opaque access token của phiên ACTIVE (query string, vì browser không đặt được header khi mở WebSocket); `last_seen_seq` tuỳ chọn | §1.2 |
| Ràng buộc token | Token trong query **không** được ghi vào access log, không đưa vào `audit_event`, không echo lại. Log chỉ ghi `session_id`. Hết TTL access token giữa chừng → server gửi message `SESSION_TOKEN_EXPIRING` rồi đóng `4408` sau `security.access_token_ttl_minutes`; client phải mở lại kết nối bằng token mới lấy từ `EP-003` | NFR-22, AUD-43 |
| Số kết nối | **Một** kết nối trên mỗi `session_id`. Kết nối thứ hai làm kết nối cũ đóng với `4401` và message `SESSION_REVOKED`/`CONNECTION_SUPERSEDED` | Nhóm 01, `uq_user_session_single_active` |
| Xác thực lại | Toàn bộ pipeline P1..P9 chạy **trước khi** upgrade. Thất bại → lỗi HTTP thường (`401`/`403`/`423`/`429`), không phải close code | 04-RBAC §1 |
| Kênh duy nhất | Không có SSE, không long-polling endpoint riêng. Fallback khi client không mở được WebSocket là **gọi REST thường** (`EP-094`, `EP-171`) theo chu kỳ do client tự quyết; server không cung cấp tham số "poll mode" | ARCH-02, ARCH-09 |

### 7.2 Envelope message

Mọi frame text (UTF-8, JSON) từ server → client đều có đúng cấu trúc sau; không có frame nào ngoài cấu trúc này:

| Trường | Kiểu | Bắt buộc | Ràng buộc | Ghi chú |
|---|---|---|---|---|
| `type` | string | Y | thuộc danh sách §7.3 | `UPPER_SNAKE_CASE` |
| `seq` | integer | Y | monotonic tăng theo `session_id`, không tái sử dụng | dùng cho resync. **Điểm chưa chốt về nguồn `seq` thống nhất** — xem §11 |
| `occurred_at` | datetime | Y | RFC 3339 UTC | thời điểm nghiệp vụ xảy ra, **không** phải thời điểm phát |
| `correlation_id` | string | Y | — | nối ngược về request REST đã sinh ra sự kiện |
| `payload` | object | Y | tín hiệu + id, xem §7.4 | có thể là `{}` nhưng không bao giờ vắng mặt |
| `redacted` | boolean | Y | mặc định `false` | `true` khi nội dung đã bị che do mất quyền (AUD-13) |

Client → server chỉ được gửi ba loại frame: `{"type":"PING"}`, `{"type":"RESYNC","payload":{"last_seen_seq":N}}`, `{"type":"ACK","payload":{"seq":N}}`. Frame khác → đóng `4000`. **Không** có frame nào từ client gây ra mutation nghiệp vụ: mọi thay đổi dữ liệu phải đi qua REST.

```json
{
  "type": "TASK_STATE_CHANGED",
  "seq": 48213,
  "occurred_at": "2026-10-15T09:14:02Z",
  "correlation_id": "7f1c9a1e-6d2b-4c33-9a10-5b7c8d9e0f11",
  "redacted": false,
  "payload": { "task_id": "0197c0a2-1111-7000-8000-0000000000a1", "project_id": "0197c0a2-0000-7000-8000-0000000000c1" }
}
```

### 7.3 Danh sách `type` tối thiểu của V1

| `type` | `payload` (chỉ id) | Ai nhận | Client phải gọi | Ghi chú |
|---|---|---|---|---|
| `TASK_STATE_CHANGED` | `task_id`, `project_id` | actor có `TK-03` trên Task tại thời điểm phát | `EP-096` | không kèm `state` mới |
| `TASK_BLOCKED_CHANGED` | `task_id`, `project_id` | như trên | `EP-096` | cờ độc lập (Nhóm 04) |
| `APPROVAL_REQUESTED` | `task_id`, `round_id` | eligible approver của round | `EP-132`, `EP-096` | AUD-07 |
| `APPROVAL_DECIDED` | `task_id`, `round_id` | `submitted_by` + eligible approver còn lại | `EP-096`, `EP-136` | không kèm `decision_outcome` |
| `APPROVAL_NO_APPROVER` | `task_id`, `round_id` | PM của Project | `EP-133` | round vẫn OPEN, **không** auto-approve |
| `PROJECT_STATUS_CHANGED` | `project_id` | `project_membership` ACTIVE | `EP-064` | không kèm `status` |
| `WORKSPACE_UPDATED` | `workspace_id` | `workspace_membership` ACTIVE | `EP-035` | bao gồm owner transfer, archive, setting change |
| `NOTIFICATION_NEW` | `notification_id` | chính user | `EP-171` hoặc item riêng | push và in-app là hai kênh của **cùng** một `notification` |
| `NOTIFICATION_REDACTED` | `notification_id` | chính user | `EP-171` | client **bắt buộc** xoá nội dung đã cache của notification này (AUD-13) |
| `SESSION_REVOKED` | `reason_code` | phiên bị thu hồi | không — client phải đăng xuất ngay | `reason_code` ∈ `LOGIN_ELSEWHERE`,`ADMIN_REVOKE`,`LOGOUT`,`PASSWORD_CHANGED`,`IDLE_TIMEOUT` |
| `JOB_STATUS_CHANGED` | `job_id`, `job_kind` | `principal_user_id` của job | `EP-228` | `job_kind` ∈ `AI`,`REPORT_EXPORT`,`REMINDER`,`FILE_SCAN` — không lộ `job_type` đầy đủ |
| `AI_JOB_DONE` / `AI_JOB_FAILED` | `ai_job_id` | chính `principal_user_id` | `EP-196`, `EP-198` | SA **không** nhận job của người khác (AI-02) |
| `REPORT_EXPORT_READY` | `export_job_id` | chính `requested_by` | `EP-190` | AUD-16 |
| `SESSION_TOKEN_EXPIRING` | `expires_at` | chính phiên | `EP-003` | cảnh báo trước khi đóng `4408` |
| `CONNECTION_SUPERSEDED` | — | kết nối cũ | không | đi kèm close `4401` |

Thêm `type` mới là thay đổi **additive** (§10). Client phải bỏ qua `type` lạ mà không đóng kết nối.

### 7.4 Payload là tín hiệu, không phải dữ liệu

1. `payload` chỉ chứa **định danh** và, khi thật cần thiết, một `reason_code` thuộc danh sách đóng. Không chứa `title`, `description`, `state`, `status`, `decision_outcome`, `percent`, tên người dùng, tên file, nội dung comment, số liệu.
2. Lý do: payload realtime đi qua bộ nhớ của client và có thể bị log lại bởi tầng trung gian; nếu chứa nội dung thì việc redact sau khi thu hồi quyền (AUD-13) trở nên vô nghĩa.
3. **Client không bao giờ được tin payload để cập nhật UI.** Quy trình bắt buộc: nhận tín hiệu → gọi endpoint REST tương ứng ở cột "Client phải gọi" → REST chạy lại P1..P9 → render theo response. Nếu REST trả `404`, client loại đối tượng khỏi UI, kể cả khi payload vừa nói rằng đối tượng tồn tại.
4. Server kiểm tra quyền **tại thời điểm phát** cho từng người nhận. Mất quyền giữa lúc enqueue và lúc phát → không phát, và phát `NOTIFICATION_REDACTED` nếu đã có notification.
5. Không có cơ chế broadcast theo phòng công khai: mỗi kết nối có tập subscription suy ra từ `workspace_membership`/`project_membership`/`task_assignment`/`approval_designation` tại thời điểm đó, và tập này được **tính lại** sau mỗi sự kiện `WORKSPACE_UPDATED`, `PROJECT_STATUS_CHANGED`, `SESSION_REVOKED`.

### 7.5 Reconnect, resync, heartbeat

| Mục | Quy ước |
|---|---|
| Heartbeat | Server gửi frame `{"type":"PING"}` mỗi 30 giây; client phải trả `{"type":"PING"}` (pong) trong 10 giây. Hai lần liên tiếp không trả → đóng `4000`. Client cũng được chủ động gửi `PING` |
| Resync khi mở lại | Client gửi `last_seen_seq` (query lúc upgrade hoặc frame `RESYNC`). Server trả mọi sự kiện có `seq > last_seen_seq` **còn trong cửa sổ lưu**, đã kiểm tra lại quyền tại thời điểm resync, không dùng quyền tại thời điểm phát (test 19.2) |
| Cửa sổ resync | `realtime.resync_window_minutes` (đề xuất 15 phút) và tối đa `realtime.resync_max_events` (đề xuất 500). Vượt → gửi `RESYNC_TRUNCATED` kèm `oldest_available_seq`, client phải fetch lại toàn bộ danh sách qua REST (`EP-094`, `EP-171`) |
| `last_seen_seq` quá cũ | `410`-tương đương: frame `RESYNC_REJECTED` với `reason_code = 'SINCE_SEQ_TOO_OLD'`, sau đó server gửi đầy đủ trạng thái hiện hành dưới dạng tín hiệu tổng hợp (`RESYNC_FULL_REQUIRED`) — không phát lại lịch sử |
| Cập nhật cursor | `realtime_cursor(session_id)` cập nhật khi client gửi `ACK` hoặc khi server phát thành công; `UNIQUE (session_id)` nên mỗi phiên một cursor |
| Backoff | Client dùng exponential backoff có jitter, bắt đầu 1s, tối đa 60s. Không reconnect nhanh hơn `Retry-After` nếu server gửi `4429` |
| Không đảm bảo thứ tự tuyệt đối giữa các tài nguyên | `seq` monotonic theo phiên, nhưng sự kiện của hai Task khác nhau có thể tới client sau khi REST đã trả trạng thái mới hơn. Client phải so `version`/`updated_at` từ REST, không so `seq` |

### 7.6 Close code

| Code | Ý nghĩa | Client nên làm |
|---|---|---|
| `4401` | Token hết hạn/không hợp lệ tại thời điểm đóng, hoặc phiên bị thu hồi (`SESSION_REVOKED`) | Đăng xuất, không tự reconnect. Nếu là `LOGIN_ELSEWHERE` → hiện thông báo theo `message_key` |
| `4403` | Tài khoản không còn ACTIVE (SUSPENDED/DISABLED/REJECTED) hoặc mất quyền trên toàn bộ subscription | Đăng xuất hoặc về màn hình "cần kết nối lại" |
| `4408` | Access token sắp/đã hết TTL trong khi kết nối đang mở | Gọi `EP-003` rồi mở lại kết nối với token mới |
| `4429` | Vượt giới hạn tần suất mở kết nối | Chờ theo `Retry-After` (trả trong frame `RATE_LIMITED` trước khi đóng) |
| `4000` | Frame không hợp lệ, `type` lạ từ client, hoặc heartbeat timeout | Mở lại kết nối sạch; nếu lặp lại → báo lỗi client |

Khoảng `4000`–`4999` là khoảng private dành cho application theo RFC 6455; **không** dùng code chuẩn `1000`/`1001` để biểu nghĩa nghiệp vụ. Trước khi đóng bằng code khác `1000`, server luôn gửi một frame cuối cùng có `type` giải thích (`SESSION_REVOKED`, `RATE_LIMITED`, `CONNECTION_SUPERSEDED`, …) để client có `reason_code` và `message_key`.

---

## 8. Quy ước riêng cho Mobile (Flutter)

### 8.1 Không có API riêng

Mobile gọi **đúng** contract Web: cùng `/api/v1`, cùng DTO, cùng mã lỗi, cùng pipeline P1..P9 (ARCH-02). Không có prefix `/api/mobile`, không có endpoint "bản rút gọn cho mobile", không có DTO song song. Khác biệt duy nhất nằm ở `X-Client-Type` (`ANDROID`/`IOS`) và `user_session.client_type`. Hệ quả: mọi quy tắc ở §1..§7 áp dụng nguyên văn cho Flutter; `07-TEST-PLAN.md` có thể dùng chung một `EP-nnn` cho cả hai client.

Vì không có API riêng, tài liệu này **không** đề xuất endpoint `POST /sessions/reauth-hint` (từng xuất hiện trong một bản draft). Thay vào đó là các quy tắc client-side dưới đây — reauthentication luôn là hành động của người dùng thật qua `EP-007`/`EP-008`, không phải một gợi ý do server suy đoán.

### 8.2 Offline cache — allowlist đóng (AUD-12, DEC-12)

Nguyên tắc: offline chỉ **đọc**, và chỉ đọc đúng allowlist. Ngoài allowlist → màn hình "Cần kết nối để xem" + nút Reauthorize. Không có endpoint nào tồn tại để phục vụ chế độ offline; cache là trách nhiệm của client.

| Được cache (chỉ đọc) | Không được cache dưới mọi dạng |
|---|---|
| Hồ sơ tài khoản của chính mình: `display_name`, `username`, `avatar_attachment_id`, `timezone`, `locale` | `title`, `description` của Task/Subtask |
| Danh sách **tên** Workspace/Project mà user là thành viên, kèm `status` | Nội dung checklist, comment, `time_log` |
| Cấu hình UI: ngôn ngữ, chủ đề, quiet hours, `first_day_of_week`, `calendar_default_view` | Attachment và mọi byte nội dung file (`EP-153`/`EP-154` bắt buộc online) |
| Timestamp lần sync cuối theo từng scope | Số liệu dashboard/report, `MetricEnvelope` |
| Notification **đã redact** (`redacted = true`, `payload = null`) | Notification chưa redact, `deep_link`, `payload` |
| `message_key` đã dùng (để render offline) | Prompt/completion/suggestion của AI |
| — | Access token, refresh token, `reauth_ticket`, `push_token` thô |

Ràng buộc thi hành: (a) cache được mã hoá bằng keystore của thiết bị và gắn `session_id`; (b) mọi mục cache mang `cached_at` và TTL ngắn (đề xuất 24h); (c) khi nhận `SESSION_REVOKED` hoặc `NOTIFICATION_REDACTED`, hoặc khi `EP-171` trả dòng `redacted = true`, client **bắt buộc** xoá mục tương ứng khỏi local store; (d) nếu phát hiện thu hồi quyền khi thiết bị đang offline, việc xoá chỉ xảy ra ở lần online kế tiếp — đây là residual risk đã ghi nhận ở AUD-12/AUD-13, không phải lỗi thiết kế.

### 8.3 Push registration gắn phiên

| Quy tắc | Nội dung |
|---|---|
| Đăng ký | `EP-177` với `platform` (`ANDROID_FCM`/`IOS_APNS`) và `push_token`; server chỉ lưu `push_token_hash` (UNIQUE), **không** lưu token thô |
| Gắn phiên | `device_push_registration.session_id` NOT NULL, `ON DELETE CASCADE` → phiên bị thu hồi thì registration bị huỷ theo (Nhóm 01, test 19.1) |
| Một phiên duy nhất | Login ở thiết bị khác thu hồi phiên cũ → cascade huỷ push registration của thiết bị cũ. Không có trạng thái "hai thiết bị cùng nhận push" |
| Payload push tối thiểu | Push chỉ mang `"Bạn có cập nhật mới"` + badge + `notification_id`. **Không** mang tiêu đề Task, tên Project, nội dung comment (AUD-13(b)) |
| Mở từ push | Client dùng `notification_id` gọi `EP-171`/`EP-172` rồi mới điều hướng; **không** mở trực tiếp `deep_link` từ payload push |
| Khi `DEC-46` chưa chốt | Nếu FCM/APNS chưa có hạ tầng, `EP-177` trả `503 PUSH_PROVIDER_UNAVAILABLE` và client phải dựa hoàn toàn vào in-app + WebSocket; không được coi push là kênh đảm bảo |

### 8.4 Thay đổi dữ liệu bắt buộc online (NFR-14)

1. Không có hàng đợi mutation offline, không có "chỉnh sửa ngoại tuyến rồi đồng bộ", không có draft lưu cục bộ được đẩy lên sau. Lý do: quyền và state hiệu lực phải được đánh giá **tại thời điểm ghi** (P5, P6); một mutation xếp hàng từ hôm qua có thể đã mất quyền, và việc áp dụng nó sẽ vi phạm AUD-48.
2. Nếu người dùng mất kết nối giữa chừng một thao tác ghi: client dùng **lại cùng `Idempotency-Key`** đã sinh cho lần thử đầu (§4.1, §4.2). Đây là lý do `Idempotency-Key` phải được sinh **trước** khi gửi request và được lưu cùng draft trong bộ nhớ phiên, không sinh lại mỗi lần retry.
3. Màn hình soạn thảo (Task, comment, time log) được giữ trong bộ nhớ phiên để không mất chữ khi rớt mạng, nhưng nút gửi chỉ hoạt động khi online; trạng thái "đang chờ đồng bộ" **không** tồn tại trong contract này.
4. `EP-092`/`EP-093`/`EP-097`/`EP-118`/`EP-138` trả `409`/`422` khi state đã đổi trong lúc người dùng offline; client phải refetch (`EP-096`) và cho người dùng quyết định lại, không tự merge.

### 8.5 Deep link phải được server xác minh lại

1. Mọi deep link (`stm://task/{id}`, `stm://project/{id}`, `stm://notification/{id}`, universal link/app link tương ứng) chỉ là **con trỏ**, không phải bằng chứng quyền.
2. Khi mở deep link, client gọi REST tương ứng (`EP-096`, `EP-064`, `EP-171`). `404` nghĩa là không có quyền hoặc không tồn tại → hiện màn hình "Không tìm thấy hoặc bạn không có quyền xem", **không** hiện "đã bị xoá" và **không** hiện "bạn không có quyền" (chống suy diễn, §1.4).
3. `notification.deep_link` bị đặt `null` khi `redacted = true`; client không được tự dựng lại link từ `notification_id`.
4. Deep link mở từ notification/email cũ vẫn phải đi qua P1..P9 đầy đủ, bao gồm cả P8 nếu action đích là nhạy cảm. Không có "grace period" cho link cũ.
5. Không nhúng token, `session_id`, `reauth_ticket` hay bất kỳ bí mật nào vào deep link.

---

## 9. Rate limit và quota

### 9.1 Bảng ngưỡng kế thừa từ `04-RBAC-MATRIX.md` §15

Tài liệu này **không** đặt ngưỡng mới; toàn bộ con số dưới đây là nguyên văn đề xuất của 04-RBAC §15 (chưa chốt — DEC-43). Cột "Ánh xạ `EP-`" là phần tài liệu này bổ sung để bảng trở nên thi hành được.

| Nhóm endpoint (04-RBAC §15) | Ngưỡng đề xuất | Khóa theo | Ánh xạ `EP-` | `X-RateLimit-Scope` |
|---|---|---|---|---|
| `POST /auth/login` | 10/phút/IP, 5/phút/username | IP + username | `EP-002` | `AUTH` |
| `POST /auth/reauth` | 5/phút/user | user | `EP-007`, `EP-008` | `AUTH` |
| `POST /auth/recovery/*` | 3/giờ/email | email | `EP-009`, `EP-010`, `EP-011`, `EP-029` | `RECOVERY` |
| `POST /files/*/upload` | 30/phút/user, tổng 500 MB/ngày/user | user | `EP-143`, `EP-145`, `EP-146`, `EP-151` | `FILE_UPLOAD` |
| `GET /files/*/download` | 120/phút/user | user | `EP-153`, `EP-154` | `FILE_DOWNLOAD` |
| `POST /ai/*` | 10/phút/user, concurrency toàn cục = `ai.max_concurrency` | user + global | `EP-194`, `EP-200`, `EP-201` | `AI` |
| `POST /reports/*/export` | 5/phút/user, 50/ngày/user | user | `EP-188` | `REPORT_EXPORT` |
| Mọi API khác | 300/phút/user | user | phần còn lại | `DEFAULT` |
| WebSocket | 60 msg/phút/phiên | session | `EP-180` | `REALTIME` |

Bổ sung do tài liệu này đề xuất (chưa có trong 04-RBAC §15 — ghi ở §11.3): `EP-001` (register) dùng scope `AUTH` với ngưỡng của `EP-002`; `EP-165` (ICS export) dùng scope `REPORT_EXPORT` vì cùng bản chất sinh tệp; `EP-173` (mark-all-read) dùng scope `DEFAULT` nhưng có trần riêng theo số dòng cập nhật để tránh một request chạm toàn bộ `notification`.

### 9.2 Header và hành vi `429`

| Mục | Quy ước |
|---|---|
| `Retry-After` | **luôn** có mặt trên `429`, đơn vị giây, làm tròn lên. Client phải tôn trọng giá trị này thay vì tự chọn backoff |
| `X-RateLimit-Scope` | tên nhóm mức thô ở cột cuối §9.1. Không tiết lộ ngưỡng, độ dài cửa sổ hay bộ đếm (§1.5) |
| `X-RateLimit-Limit` | **không bao giờ** trả (04-RBAC §15: "không tiết lộ ngưỡng") |
| `X-RateLimit-Remaining` | **điểm chưa chốt** — xem §11.2 (DEC-43). Đề xuất hiện tại: không trả |
| `X-RateLimit-Reset` | không trả; `Retry-After` đã đủ và ít lộ thông tin hơn |
| Body `429` | envelope chuẩn §2.1 với `error.code = 'RATE_LIMITED'`, `error.retry_after_seconds` khớp `Retry-After`, `error.details[]` rỗng. Không kèm tên người dùng, không kèm IP, không kèm số request đã dùng |
| Thứ tự trong pipeline | Rate limit là **P9**, tức sau khi đã xác thực và đã kiểm tra quyền. Hệ quả: request sai token nhận `401` chứ không nhận `429`; nhưng `EP-002` (chưa có token) là ngoại lệ có chủ đích và được đếm theo IP + username |
| Ghi nhận | Mọi lần vượt ngưỡng ghi `security_event(event_type='RATE_LIMIT_HIT')` với `detail` chỉ gồm `scope_key`, `user_id` (nếu có), `ip_address`; **không** ghi nội dung request, mật khẩu hay token |
| Không tiết lộ qua thời gian phản hồi | Khi vượt ngưỡng, server trả `429` ngay, không giả lập độ trễ. Ngược lại, với `EP-002`, thời gian trả lời phải **không đổi** giữa "sai mật khẩu" và "tài khoản không tồn tại" (chống enumeration theo thời gian) |
| Khóa theo IP sau proxy | `ip_address` lấy từ hop cuối cùng đáng tin (`Forwarded`/`X-Forwarded-For` đã cấu hình rõ số proxy). Không cấu hình → khóa theo địa chỉ proxy và toàn bộ người dùng sau proxy dùng chung một bộ đếm. Đây là rủi ro vận hành, ghi ở §11.3 |

### 9.3 Quota dung lượng và số lượng

Quota là khái niệm **khác** rate limit: quota giới hạn tổng lượng dữ liệu tồn tại, rate limit giới hạn tần suất. Quota được trả qua `EP-158` và kiểm tra tại `EP-143`.

| Quota | Nguồn cấu hình | Kiểm tra tại | Mã lỗi khi vượt |
|---|---|---|---|
| Kích thước một file | `file.max_upload_mb` (seed 50) | `EP-143` (khai báo) và `EP-145`/`EP-146` (byte thật) | `413 FILE_TOO_LARGE`, `422 DECLARED_SIZE_MISMATCH` |
| Dung lượng upload/ngày/user | 04-RBAC §15: 500 MB | `EP-143` | `429 UPLOAD_QUOTA_DAILY_EXCEEDED` (scope `FILE_UPLOAD`) |
| Dung lượng lưu trữ/scope | chưa có `system_setting` tương ứng — xem §11.3 | `EP-158` (đọc), `EP-146` (ghi) | `507 STORAGE_QUOTA_EXCEEDED` |
| Số upload session đồng thời/user | đề xuất mới | `EP-143` | `429 CONCURRENT_UPLOAD_LIMIT_EXCEEDED` |
| Số `device_push_registration` ACTIVE/phiên | đề xuất mới | `EP-177` | `409 DEVICE_REGISTRATION_LIMIT_EXCEEDED` |
| Số tag/Workspace, số tag/Task | đề xuất mới (DEC-22 chưa chốt phạm vi tag) | `EP-058`, `EP-116` | `409 TAG_QUOTA_EXCEEDED`, `422 TAG_LIMIT_PER_TASK_EXCEEDED` |
| Số checklist item/Task, số subtask/Task, số dependency/Task | đề xuất mới | `EP-111`, `EP-093`, `EP-108` | `422 CHECKLIST_ITEM_LIMIT_EXCEEDED`, `422 SUBTASK_DEPTH_EXCEEDED`, `422 DEPENDENCY_LIMIT_EXCEEDED` |
| Số dòng một export | `report_definition.max_rows` (seed 100000) | `EP-188` (ước lượng), job (thật) | `422 EXPORT_ROWS_LIMIT_EXCEEDED` |
| Số suggestion/AI job giữ lại | theo `retention_policy` của `ai_interaction` (90 ngày, DEC-45) | job `REDACT_ON_REVOKE` / retention | không có mã lỗi HTTP — dữ liệu tự hết hạn |

`EP-158` trả `MetricEnvelope` cho từng quota (`value_int` = đã dùng, `denominator` = trần) để client vẽ thanh dung lượng mà không cần biết công thức tính. Quota đã dùng **không** phải thông tin nhạy cảm giữa các user vì nó chỉ tính trên scope của chính actor.

---

## 10. Versioning và tương thích

### 10.1 Quy tắc trong một major version (`/api/v1`)

| Loại thay đổi | Phân loại | Điều kiện |
|---|---|---|
| Thêm endpoint mới | Không breaking | cấp `EP-nnn` mới; không tái sử dụng `EP-nnn` đã bỏ |
| Thêm trường **tuỳ chọn** vào request | Không breaking | server phải chấp nhận request thiếu trường đó và dùng giá trị mặc định đã công bố |
| Thêm trường mới vào response | Không breaking | client bắt buộc bỏ qua trường lạ (quy tắc "unknown field tolerance" — cả Web và Flutter phải tuân thủ) |
| Thêm giá trị enum mới vào response | **Rủi ro cao, chỉ làm khi cần** | bắt buộc kèm `*_note_key` để client cũ hiển thị được; client không được `switch` exhaustively mà không có nhánh mặc định |
| Thêm mã lỗi mới | Không breaking | client switch theo nhóm HTTP status, không theo danh sách mã đầy đủ |
| Thêm `type` sự kiện realtime mới | Không breaking | §7.3: client bỏ qua `type` lạ, không đóng kết nối |
| Đổi kiểu dữ liệu của trường đang có | **Breaking** | cấm trong `/api/v1` |
| Đổi ngữ nghĩa của trường đang có | **Breaking** | cấm — kể cả đổi đơn vị (phút ↔ giây) hay đổi mốc thời gian |
| Xoá trường, đổi tên trường | **Breaking** | cấm; thay vào đó thêm trường mới và đánh dấu trường cũ `deprecated` trong tài liệu, giữ nguyên hành vi |
| Siết validation đang lỏng | **Breaking** với client hợp lệ cũ | chỉ được làm sau một chu kỳ cảnh báo bằng `error.details[].issue_code = 'DEPRECATION_WARNING'` |
| Nới validation đang chặt | Không breaking | — |
| Đổi đường dẫn, đổi method | **Breaking** | thêm endpoint mới song song, giữ endpoint cũ |
| Đổi `EP-nnn` đã công bố | Cấm tuyệt đối | `07-TEST-PLAN.md` tham chiếu bằng `EP-nnn` |

### 10.2 Major version mới (`/api/v2`)

1. `/api/v2` chỉ mở khi có ít nhất một thay đổi breaking **không thể** trì hoãn. Không mở major version cho tính năng mới.
2. Hai major version chạy song song tối thiểu một chu kỳ phát hành; `/api/v1` trả header `Deprecation` và `Sunset` (RFC 8594) trong suốt chu kỳ đó.
3. `Sunset` chỉ được đặt sau khi có bằng chứng từ `audit_event` rằng lưu lượng trên `/api/v1` đã về dưới ngưỡng công bố. Không tuyên bố đã ngắt khi chưa có số liệu.
4. WebSocket có subprotocol riêng (`v2.stm.realtime`) và có thể được nâng cấp độc lập với REST; client khai báo subprotocol khi mở `EP-180`.
5. `EP-nnn` là định danh **toàn cục và vĩnh viễn**, không đánh số lại theo major version. Endpoint chỉ có ở v2 vẫn nhận `EP-nnn` mới.
6. Mã lỗi không bao giờ bị tái sử dụng cho ngữ nghĩa khác. Mã bị bỏ được ghi vào danh sách "reserved — do not reuse".
7. Client mobile phát hành qua store có vòng đời dài hơn Web: mọi thay đổi breaking phải tính đến phiên bản Flutter cũ còn đang chạy. Đây là lý do quy tắc "không xoá trường" là tuyệt đối trong `/api/v1`.

---

## 11. Điểm CHƯA chốt

Toàn bộ tài liệu này là **ĐỀ XUẤT**. Các mục dưới đây là những chỗ tài liệu này **phát hiện mâu thuẫn giữa các tài liệu gốc**, **phụ thuộc quyết định chưa chốt**, hoặc **tự đặt ra giả định**. Theo yêu cầu, không có file nào khác bị sửa; mọi khác biệt chỉ được ghi nhận ở đây.

### 11.1 Mâu thuẫn giữa các tài liệu gốc (chỉ ghi nhận, không tự sửa)

| # | Mâu thuẫn | Tài liệu A | Tài liệu B | Ảnh hưởng trong file này | Hướng đề xuất (chưa áp dụng) |
|---|---|---|---|---|---|
| C-01 | `P-02 PROJECT_READ_METADATA` cấp cho WM/WG quyền đọc metadata của **mọi** Project trong Workspace | `04-RBAC` §6 dòng `P-02` | `04-RBAC` §2 và §12: "WM truy cập Project vì là thành viên WS → N, cần `project_membership`"; "WO đọc được mọi Project trong WS → N" | `EP-062`, `EP-063`, `EP-064`, `EP-183` | Nếu giữ `P-02` như hiện tại thì §12 phải bỏ hai dòng phủ định; nếu giữ §12 thì `P-02` phải giới hạn "chỉ Project có participation". File này đang viết theo hướng **hẹp** (§12) nên `EP-064` trả `404` khi không có participation |
| C-02 | WG được đọc calendar Workspace nhưng không được đọc dashboard Workspace | `04-RBAC` `C-03` (WO/WA/WM/**WG**) | `04-RBAC` `R-03` (WO/WA/WM — không có WG) | `EP-159`..`EP-164` cho WG, `EP-183` thì không | Cả hai đều "chỉ tổng hợp Project có participation" nên về bản chất giống nhau. Đề xuất hoặc cho WG vào `R-03`, hoặc bỏ WG khỏi `C-03`; chưa tự sửa |
| C-03 | Cột `seq` của `notification` | `02B` §2.5: "Cần cột `seq bigint` monotonic trên `notification` (thêm vào 2.1: `seq bigserial NOT NULL UNIQUE`)" | `02B` §2.1: bảng cột **không có** `seq`; §11 và §12 cũng chỉ nhắc ở phần ghi chú | `EP-179`, `EP-180`, `NotificationResponse.seq` (§6.11), §7.5 | File này dùng `seq` như đã có thật, vì không có `seq` thì resync không thể thực hiện. Cần một change request thêm cột vào §2.1 |
| C-04 | Nguồn `seq` thống nhất cho realtime | `02B` §2.5: `realtime_cursor.last_seen_seq` gắn với **notification** | §7.3 file này: feed còn carrying `TASK_STATE_CHANGED`, `PROJECT_STATUS_CHANGED`, `JOB_STATUS_CHANGED` — không phải notification | `EP-180`, §7.2, §7.5 | Cần một `seq` cấp **phiên** (một dãy duy nhất cho mọi loại sự kiện của phiên) thay vì `seq` cấp notification. Đề xuất mở quyết định mới; file này giả định dãy cấp phiên |
| C-05 | Tiết lộ ngưỡng rate limit | `04-RBAC` §15: "không tiết lộ ngưỡng trong response body" | Đề bài yêu cầu mô tả `X-RateLimit-*` | §1.5, §9.2 | Đã giải quyết theo hướng không mâu thuẫn: trả `Retry-After` + `X-RateLimit-Scope`, **không** trả `X-RateLimit-Limit`. `X-RateLimit-Remaining` để mở (DEC-43) |
| C-06 | `upload_session` thiếu cột tiến độ chunk | `02A` §7.4: chỉ có `status`, `declared_size`, `detected_content_type`, `expires_at`, `error_detail` | `EP-145` cần `next_offset`/`received_bytes` để kiểm tra `Content-Range` và chống ghi chồng | `EP-143`..`EP-147`, §6.9, §6.10 | Cần thêm `received_bytes bigint`, `next_offset bigint`, `chunk_count int`, `chunk_size_bytes int` vào `upload_session`. File này **không** tự sửa 02A |
| C-07 | Trích dẫn nguồn cho quy tắc 404 | Đề bài: "AUD-42/P3" | `00-AUDIT-SPEC` `AUD-42` là phát hiện về **hạ tầng nghiệm thu NFR-09/NFR-10**, không liên quan 404/403 | §1.4 | File này bám `04-RBAC` §1 bước P3 và Nhóm 10. Đã ghi chú ngay tại §1.4 |
| C-08 | Tên audit action cho Nhóm 04..10 | `04-RBAC` §4/§5/§6 **có** cột "Audit action" | `04-RBAC` §7..§11 **không có** cột đó | Toàn bộ cột "Audit action" của §5.5..§5.16 (đánh dấu `(mới)`) | File này suy dẫn theo prefix ở `04-RBAC` §13. Các tên này **chưa frozen**; cần bổ sung cột "Audit action" vào §7..§11 của 04-RBAC trước khi đóng Phase 0 |
| C-09 | Quyền quản lý tag | `00-AUDIT-SPEC` `AUD-22`: tag quản lý ở cấp Workspace bởi WO/WA | `04-RBAC`: **không có** mã hành động `TAG_*` cụ thể, chỉ có prefix ở §13 | `EP-057`..`EP-060`, `EP-115`, `EP-116` (file này dùng `TAG_READ`/`TAG_MANAGE` đánh dấu `(mới)`) | Cần bổ sung mã hành động tag vào `04-RBAC` §6 |
| C-10 | Xung đột ký hiệu `C-xx` | `04-RBAC` §10: `C-01`..`C-06` là **Calendar** | Comment không có mã hành động nào, trong khi `EP-137`..`EP-141` cần mã | **ĐÃ GIẢI QUYẾT** — `04 §7.1` nay cấp dãy chính thức `CM-01..CM-05` (AUD-53a); §5.7 của file này đã đánh số lại theo đúng dãy đó, còn `EP-137` (đọc) dùng `TK-03` | Không cần đề xuất thêm. Hai điểm của bản draft trước của chính file này đã bị **bác** vì trái `04`: (i) sửa comment của người khác — `04 §7.1` bất biến (1); (ii) moderation bởi `WO` — `04 §12` và bất biến (3) |
| C-11 | Phạm vi `comment.target_type` | `02B` §1.1: CHECK chỉ cho `TASK`,`SUBTASK` | Đặc tả §17 gợi ý nhóm `/comments` chung, không giới hạn target | `EP-137`..`EP-141`: V1 **không có** comment cấp Project/Milestone/Workspace | Nếu muốn comment cấp Project, phải sửa CHECK ở `02B` §1.1 và thêm mã quyền; chưa tự sửa |
| C-12 | `attachment.target_type` rộng hơn danh sách của Nhóm 09 | `02A` §7.1: CHECK có `AVATAR`, `MILESTONE`, `REPORT_EXPORT` | Đặc tả Nhóm 09 và §17 không có endpoint avatar nào | `EP-143` với `target_type='AVATAR'`, `EP-014` cập nhật `app_user.avatar_attachment_id` | File này đề xuất tái dùng luồng upload chuẩn cho avatar thay vì thêm endpoint riêng. Cần xác nhận |
| C-13 | Cắt luồng file đang truyền khi thu hồi quyền | `05-WORKFLOW` §12 đề xuất `DEC-51` | `ARCH-07`/`FL-02`: "kiểm tra tại mỗi request" — không nói gì về request **đang chạy** | `EP-153`, `EP-154`, §5.8 quy tắc 6 | Chờ DEC-51. File này chưa chọn bên nào; cả hai hành vi đều phải test |
| C-14 | Định nghĩa "thực thi" của `PLANNING` | `04-RBAC` §16: `PLANNING` chặn `start` | Không tài liệu nào định nghĩa "thực thi" gồm những hành động nào | `EP-125` (`F-01` start; transition `05 T-01`), và các gate `PJ-writable` / `PJ-exec` trong §5 | File này giả định `PJ-exec` = chỉ `ACTIVE`, `PJ-writable` = `PLANNING` hoặc `ACTIVE`. Cần chốt |
| C-15 | Làm tròn `progress` | `00-AUDIT-SPEC` `DEC-20` chưa chốt | `02B` §3.4 chỉ cho công thức, không cho quy tắc làm tròn | §6.6: file này chọn làm tròn **xuống** 2 chữ số và `display` dạng `<1%`/`>99%` | Chờ DEC-20; nếu chọn làm tròn lên thì §6.6 phải sửa |
| C-16 | Các `setting_key` mới | `02B` §7.1: danh sách seed đóng, không có khoá nào cho chunk upload, resync realtime, quota dung lượng, trần kỳ báo cáo | §9.1, §9.3, §6.9, §7.5 của file này dùng `file.chunk_threshold_mb`, `file.chunk_min_bytes`, `file.chunk_max_bytes`, `realtime.resync_window_minutes`, `realtime.resync_max_events`, `report.max_period_days`, `storage.quota_bytes_per_workspace` | Cần bổ sung các khoá này vào `02B` §7.1 kèm giá trị seed | File này **không** tự sửa 02B |

### 11.2 Phụ thuộc quyết định mở (`DEC-nn`)

Chín quyết định dưới đây ảnh hưởng **trực tiếp** đến contract; nếu chốt khác hướng đang giả định thì phải sửa các endpoint tương ứng.

| `DEC` | Nội dung chưa chốt | Giả định hiện tại của file này | Endpoint/DTO phải sửa nếu chốt khác |
|---|---|---|---|
| **DEC-02** | "readonly" của `ON_HOLD`/`ARCHIVED` có chặn **workflow** không | Chặn toàn bộ mutation, kể cả approve/reject; `effective_state` phủ quyết | `EP-125`..`EP-130`, `EP-134`, `EP-135`, cột `State gate` của cả §5.6, `TaskResponse.effective_state` |
| **DEC-05** | Subtask có vòng nghiệm thu riêng không | **Không**: subtask không có round, approve chỉ ở top-level | `EP-093`, `EP-102`, `EP-126`..`EP-130`, `EP-131`..`EP-136`, `TaskResponse.current_round`, `ApprovalDecisionResponse` |
| **DEC-06** | Danh sách chính xác nội dung bị khoá ở `PENDING_APPROVAL` | Khoá: sửa nội dung Task, assignment, dependency, checklist, time log, file upload/delete, comment mới trên Task. Không khoá: đọc, approve/reject của APR, cancel của người có quyền | `exec-unlocked` gate ở `EP-093`, `EP-097`, `EP-100`, `EP-101`, `EP-104`..`EP-119`, `EP-143`, `EP-146`, `EP-151`, `EP-155`; `TASK_LOCKED_PENDING_APPROVAL` |
| **DEC-12** | Allowlist offline cache của Mobile | Dùng đúng allowlist đề xuất của `AUD-12` (§8.2) | Toàn bộ §8.2; nếu mở rộng allowlist thì phải thêm endpoint đồng bộ theo scope và thêm rủi ro thu hồi |
| **DEC-16** | Nơi lưu + TTL report export | Object Storage private, `attachment(target_type='REPORT_EXPORT')`, TTL 24h theo `report.export_ttl_hours` | `EP-188`, `EP-190`, `EP-192`, `ReportExportJobResponse.expires_at`, §6.10 |
| **DEC-38** | Envelope trạng thái số liệu | 5 trạng thái `OK`/`NOT_APPLICABLE`/`NO_PERMISSION`/`INSUFFICIENT_DATA`/`PARTIAL` theo `02B` §3.1 | `MetricEnvelope` (§6.7), `EP-181`..`EP-186`, `EP-193`, `progress` của `ProjectResponse` |
| **DEC-39** | Context Manifest cho AI, và PG có được dùng `NATURAL_SEARCH` | Manifest là danh sách đóng duyệt như code; PG **được** dùng `NATURAL_SEARCH` trong scope được cấp | `EP-194`, `EP-205`, `EP-206`, `AiSuggestionResponse.validation_result` |
| **DEC-43** | Bộ công nghệ: token, realtime, queue, storage, PK; và ngưỡng rate limit | Opaque access token 15 phút + refresh 30 ngày; WebSocket; `background_job` làm queue; MinIO S3 private; UUIDv7; ngưỡng theo `04-RBAC` §15 | §1.1, §1.2, §1.3, §4.2, §7.1, §9.1, §9.2. Nếu đổi sang JWT thì §1.2, §7.1 (`token` trong query) và toàn bộ quy tắc thu hồi tức thời phải viết lại |
| **DEC-46** | FCM project / Apple Developer / SMTP có sẵn không | Giả định **chưa có** ở V1: `EP-177` trả `503 PUSH_PROVIDER_UNAVAILABLE`, email không phải kênh đảm bảo | `EP-176`..`EP-178`, `NotificationResponse.channels[]`, `delivery_state`, §8.3 |

Các quyết định mở khác có ảnh hưởng hẹp hơn:

| `DEC` | Ảnh hưởng tới |
|---|---|
| DEC-01 (actor + transaction của cascade khi Project COMPLETED) | `EP-066`, `EP-129`, `acknowledge_cascade` của `WorkflowActionRequest` |
| DEC-03 (chặn cứng Manager cuối rời Workspace) | `EP-044`, `EP-075`, `LAST_MANAGER_*` |
| DEC-04 (Workspace Guest có được làm Project Member) | `EP-072`, `TARGET_IS_GUEST` |
| DEC-07 (ai được làm Designated Approver) | `EP-134`, `EP-135`, `DESIGNATED_USER_*` |
| DEC-08 (SLA có sinh hành động tự động) | `EP-077`, `EP-127`, `EP-128`; file này giả định **không** auto-decide (`SLA_AUTO_DECISION_FORBIDDEN`) |
| DEC-09 (phạm vi dependency) | `EP-107`..`EP-109`; file này giả định chỉ cùng Project, không áp dụng cho subtask |
| DEC-11 (luồng invite với tài khoản có sẵn) | `EP-048`, `EP-049`, `INVITE_EMAIL_ALREADY_PENDING` |
| DEC-13 (payload push tối thiểu) | §8.3; file này giả định push chỉ mang badge + `notification_id` |
| DEC-14 / DEC-45 (retention, disposal) | `EP-215`..`EP-218`, `retention_policy` |
| DEC-15 (cơ chế số liệu lịch sử) | `EP-186`; file này giả định chỉ đọc `metric_daily_snapshot` |
| DEC-18 (cơ chế bootstrap System Admin) | `EP-223`; file này giả định chỉ CLI, HTTP chỉ đọc cờ |
| DEC-19 (UX một phiên / đa thiết bị) | `EP-002`, `EP-005`, `SESSION_ALREADY_ACTIVE`, close code `4401` |
| DEC-22 (phạm vi tag) | `EP-057`..`EP-060`, `EP-116` |
| DEC-23 (quy tắc time log) | `EP-117`..`EP-119`, `TIMELOG_OVERLAP_DETECTED`, cửa sổ sửa |
| DEC-26 (chất lượng tìm kiếm tiếng Việt) | `EP-095`, `SEARCH_QUERY_TOO_SHORT`, `q` tối thiểu 2 ký tự |
| DEC-27 (cơ chế chống race approval) | §4.3, §5.6, `TASK_STATE_CHANGED`, `VERSION_CONFLICT` |
| DEC-29 (restore theo `delete_group_id`) | `EP-099`, `EP-156`, `RESTORE_PARTIAL_IRRECOVERABLE` |
| DEC-34 (danh sách thao tác nhạy cảm + thời hạn reauth) | cột `Reauth` của toàn §5, `EP-007`, `EP-008`, TTL 5 phút |
| DEC-35 (định nghĩa "ACTIVE hợp lệ") | `isValidActiveActor` ở P2, mọi cột `State gate` |
| DEC-36 (quyền đọc audit theo scope) | `EP-224`..`EP-226`, `AuditEventResponse.grant_path` |
| DEC-40 (chống prompt injection) | `EP-194` (`hint` ≤ 500 ký tự, không nhận chỉ dẫn hệ thống), `EP-206` |
| DEC-41 (hạ tầng Ollama) | `ai.enabled=false` ở seed → `EP-194`, `EP-200`, `EP-201` trả `409 AI_DISABLED` |
| DEC-47 (ràng buộc cấm AI mutate) | toàn bộ §5.13, `AiSuggestionResponse.next_action_endpoint` |
| DEC-48 (mô hình uỷ quyền cho background job) | `EP-227`..`EP-231`, `JOB_PRINCIPAL_PERMISSION_REVOKED` |
| DEC-49 (idempotency outbox) | §4.2, `EP-188`, `EP-194` |
| DEC-50 (định nghĩa participation + special grant) | `EP-054`..`EP-056`, `EP-079`..`EP-081`, `ProjectResponse.my_participation` |
| DEC-51 (cắt stream khi thu hồi quyền) | `EP-153`, `EP-154` |
| DEC-52 (chủ quản `timezone`/`locale`, phạm vi quiet hours) | `EP-014` vs `EP-167`; file này đặt `timezone`/`locale` ở `EP-014` (hồ sơ) và quiet hours ở `EP-167` |
| DEC-54 (quyền comment của Guest, sửa comment của mình) | `EP-138`..`EP-141`, `COMMENTING_DISABLED_FOR_GUEST`, `COMMENT_NOT_EDITABLE` |

### 11.3 Giả định do tài liệu này tự đặt ra

Những điểm dưới đây **không** có trong bất kỳ tài liệu gốc nào; chúng được đề xuất để contract đủ chi tiết để thi hành và cần được phê duyệt hoặc thay thế.

| # | Giả định | Lý do | Nếu không đồng ý thì ảnh hưởng |
|---|---|---|---|
| A-01 | ETag là chuỗi thập phân của cột `version`, đặt trong dấu nháy kép: `ETag: "8"` | `02A` §1 có `version` nhưng không định nghĩa ETag | §1.3, §4.3, mọi mutation có `If-Match` |
| A-02 | Body và header cùng mang `version`; lệch nhau → `400 IF_MATCH_MISMATCH`, server không tự chọn | Tránh mơ hồ khi client cache stale | §6.3, §6.4 |
| A-03 | Thiếu khoá trong response = "không được serialize cho bạn"; `null` = "giá trị rỗng và bạn có quyền đọc" | Chống rò rỉ quyền qua hình dạng JSON | Toàn bộ §6.0, `TaskResponse` |
| A-04 | `confirm_revoke_existing_session` trong body `EP-002` + mã `SESSION_ALREADY_ACTIVE` để thoả AUD-19(2) "cảnh báo trước khi đá phiên kia" mà không tạo oracle liệt kê thiết bị | AUD-19 yêu cầu cảnh báo; bất biến một phiên không cho phép hai phiên | `EP-002`, §1.2 |
| A-05 | Upload chunk dùng **một** tài nguyên `PUT .../bytes` với `Content-Range`, không dùng nhiều phần đánh số kiểu S3 multipart | Backend gateway (ARCH-07) phải thấy từng byte; multipart của S3 sẽ buộc phải presign | `EP-145`, §6.9, §6.10, C-06 |
| A-06 | Close code WebSocket `4401`/`4403`/`4408`/`4429`/`4000` (khoảng private RFC 6455) | Đặc tả không định nghĩa close code | §7.6, `EP-180` |
| A-07 | Health ở `/api/v1/system/health` và `/api/v1/system/health/ready`, không cần auth, trả tên check chung chung | Đặc tả không có endpoint health | `EP-221`, `EP-222`, §1.2 |
| A-08 | `EP-223` chỉ trả cờ `bootstrap_completed`; bootstrap thật chỉ qua CLI | AUD-18 yêu cầu CLI; vẫn cần một cách để client biết trạng thái | `EP-223`, §1.2 |
| A-09 | Các `setting_key` mới: `file.chunk_threshold_mb`, `file.chunk_min_bytes`, `file.chunk_max_bytes`, `realtime.resync_window_minutes`, `realtime.resync_max_events`, `report.max_period_days`, `storage.quota_bytes_per_workspace` | Cần để §6.9, §7.5, §9.3 thi hành được | C-16; nếu không thêm thì phải hardcode |
| A-10 | `AuditEventResponse.grant_path` là trường **suy dẫn**, không có cột tương ứng | Để chính việc đọc audit cũng khả kiểm (FL-09 có ba đường cấp quyền) | `EP-224`, `EP-225`, §6.14 |
| A-11 | `EP-192` (`download` export) là ngoại lệ 403 duy nhất của quy tắc 404 | Actor đã biết job là của mình nên 404 không bảo vệ thêm thông tin nào, mà lại phá khả năng giải thích | §1.4, §5.12 quy tắc 9, `EP-192` |
| A-12 | Ký hiệu `CM-01`..`CM-05` cho Comment **không còn là quy ước riêng của file này** — đã dùng đúng dãy chính thức của `04 §7.1` | `04-RBAC` đã dùng `C-xx` cho Calendar; AUD-53a cấp dãy `CM-xx` cho Comment | C-10, §5.7 |
| A-13 | `ip_label` / `user_agent_label` đã che trong `AuditEventResponse`; `notification.session_id_bound` được thay bằng `bound_to_current_session` | Giảm bề mặt rò rỉ thông tin phiên và hạ tầng | §6.11, §6.14 |
| A-14 | `EP-142` (`/workspaces/{id}/activity`) là dòng thời gian cấp Workspace; không có dòng thời gian cấp Project riêng vì `activity_event` đã có `target_type` | Đặc tả §17 gộp "activity timeline" vào một nhóm | `EP-121`, `EP-142` |
| A-15 | `sort` chỉ chấp nhận trường trong allowlist từng endpoint; `UNSUPPORTED_SORT_FIELD` là `400` chứ không phải `422` | Lỗi cú pháp tham số, không phải lỗi nghiệp vụ | §3.3 |
| A-16 | Không có endpoint nào cho `outbox_event`, `outbox_delivery`, `delivery_attempt`, `file_access_event` (trừ `EP-157` đọc theo attachment), `task_deadline_change` (trừ `EP-123`), `metric_daily_snapshot` (trừ `EP-186`) | Đây là bảng hạ tầng/append-only; mở HTTP cho chúng sẽ tạo bề mặt rò rỉ | §5, ARCH-04 |
| A-17 | `X-Client-Type` bắt buộc ở **mọi** request, không chỉ login | Cần cho AUD-19 và cho việc đóng đúng kết nối WebSocket khi có phiên mới | §1.3, §7.1 |
| A-18 | Forwarded-IP phải được cấu hình rõ số proxy tin cậy; nếu không thì rate limit theo IP sẽ gộp mọi user sau proxy | `04-RBAC` §15 khóa theo IP nhưng không nói về proxy | §9.2 |

### 11.4 Những gì tài liệu này cố ý KHÔNG làm

1. Không sinh OpenAPI/Swagger, không sinh client stub, không viết code mẫu Java/TypeScript/Dart. Chỉ có 6 khối minh hoạ `json`/`http` ngắn.
2. Không tạo bảng, không viết migration, không chạy DDL.
3. Không tuyên bố bất kỳ kết quả kiểm thử nào. Không có dòng nào viết "đã pass", "đã đạt", "đã xác minh bằng chạy thử".
4. Không sửa `00-AUDIT-SPEC.md`, `01-MODULES-BOUNDARIES.md`, `02A`, `02B`, `04-RBAC-MATRIX.md`, `05-WORKFLOW-STATE-MACHINES.md`. Mọi mâu thuẫn chỉ được ghi ở §11.1.
5. Không đề xuất endpoint vượt phạm vi V1: không có API cho xếp hạng năng suất cá nhân (`R-07` không tồn tại), không có API public/webhook ra ngoài, không có endpoint đọc trực tiếp Object Storage, không có endpoint cho AI tự ghi dữ liệu nghiệp vụ.
6. Không đặt tên mới cho bất kỳ thực thể nào đã có tên trong 02A/02B/04-RBAC. Các tên mới duy nhất là `EP-nnn` (định danh endpoint của riêng file này), các mã lỗi đánh dấu "(mới đề xuất)", các audit action đánh dấu "(mới)", và ký hiệu `CM-xx`.
