# 04 — MA TRẬN RBAC: ACTION × OBJECT × SCOPE × STATE

**Trạng thái:** ĐỀ XUẤT — **CHƯA FROZEN** ở mức endpoint (đặc tả mục 16: "CHƯA FROZEN ma trận endpoint").
**Bám baseline:** Nhóm 01–03, Nhóm 10 (FROZEN) + công thức authorization đặc tả mục 16.
**Nguyên tắc tối cao:** **DENY-by-default.** Bất kỳ (actor, action, resource, state) nào **không xuất hiện** trong ma trận này với giá trị Y đều bị **từ chối**. Không có quyền nào được suy diễn.


**Chú giải ký hiệu dùng trong các bảng:** `Y` = có / được phép / cơ chế giữ đầy đủ bất biến; `N` = không / không được phép / không thể dùng cơ chế đó; `!` = một phần (phần còn lại phải xử lý ở service layer hoặc đang chờ quyết định); `—` = không áp dụng.

---

## 1. Công thức authorization (mở rộng từ đặc tả mục 16)

Đặc tả: `ALLOW = valid account/session AND scope membership AND object permission AND effective resource state AND action-specific invariants AND (reauthentication if sensitive)`

Pipeline bắt buộc, thực thi **theo thứ tự**, dừng ngay khi một bước fail:

| Bước | Kiểm tra | Fail → HTTP | Fail → `audit_event.outcome` |
|---|---|---|---|
| P1 | Access token hợp lệ, chưa hết hạn, gắn `session_id` **ACTIVE** | 401 `INVALID_SESSION` | (không ghi audit nội dung, ghi `security_event`) |
| P2 | `app_user.status = ACTIVE` AND (`locked_until IS NULL OR locked_until < now()`) | 403 `ACCOUNT_NOT_ACTIVE` / 423 `ACCOUNT_LOCKED` | `DENIED_FORBIDDEN` |
| P3 | Xác định resource + scope; **nếu actor không có quyền READ metadata của resource** → trả **404**, không phải 403 | 404 `RESOURCE_NOT_FOUND` | `DENIED_NOT_FOUND` |
| P4 | Scope membership: `workspace_membership` ACTIVE (và `project_membership` ACTIVE nếu action ở phạm vi Project) — **hoặc** `special_grant` còn hiệu lực | 404 (như P3) | `DENIED_NOT_FOUND` |
| P5 | Object permission theo role (ma trận mục 4–13) | 403 `FORBIDDEN` | `DENIED_FORBIDDEN` |
| P6 | Effective resource state (mục 3) cho phép action | 409 `STATE_CONFLICT` | `DENIED_STATE` |
| P7 | Action-specific invariants (self-approval, last manager/owner, single session, cycle, one-level subtask, version…) | 409/422 với mã riêng | mã riêng (`DENIED_SELF_APPROVAL`, `DENIED_LAST_MANAGER`, `DENIED_VERSION_CONFLICT`) |
| P8 | Nếu action ∈ tập nhạy cảm (mục 14) → `reauth_ticket` hợp lệ, chưa dùng, đúng action+target, còn hạn | 428 `REAUTH_REQUIRED` / 403 `REAUTH_INVALID` | `DENIED_FORBIDDEN` |
| P9 | Rate limit / idempotency | 429 / 409 | `security_event` |

> **P3/P4 trả 404 chứ không phải 403** là **bắt buộc** — Nhóm 10: "xử lý lỗi không lộ thông tin đối tượng ẩn". Test bảo mật phải kiểm tra điều này (một Workspace Owner gọi `GET /api/v1/projects/{id}` của Project chưa tham gia phải nhận **404**, không phải 403).

### 1.1. Định nghĩa predicate thống nhất (AUD-35)

```
isValidActiveActor(actor, workspaceId?, projectId?) :=
     actor.user.status = 'ACTIVE'
 AND (actor.user.locked_until IS NULL OR actor.user.locked_until < now())
 AND actor.session.status = 'ACTIVE' AND actor.session.expires_at > now()
 AND (workspaceId IS NULL OR (
        workspace.status IN ('ACTIVE')                       -- ARCHIVED ⇒ readonly, DELETED ⇒ deny
     ))
 AND (projectId IS NULL OR (
        project.status <> 'DELETED'
     ))
 AND (workspaceId IS NULL OR EXISTS workspace_membership(ws, actor, status='ACTIVE', deleted_at IS NULL))
 AND (projectId  IS NULL OR EXISTS project_membership (pj, actor, status='ACTIVE', deleted_at IS NULL))
```

Predicate này cài đặt **một lần** trong `platform-security` và dùng lại ở: REST controller, WebSocket handler, file gateway, job worker (AUD-48), report export (AUD-16), ICS export.

### 1.2. "Participation" (AUD-50)

```
hasParticipation(projectId, userId) :=
     project_membership(projectId, userId, status='ACTIVE', deleted_at IS NULL)      -- đường chính
  OR project.created_by = userId                                                     -- creator là Manager mặc định
  OR EXISTS task_assignment(task ∈ project, user, status='ACTIVE', deleted_at IS NULL)  -- assignee
  OR special_grant(PROJECT, projectId, userId, 'PROJECT_CONTENT_READ', revoked_at IS NULL, expires_at > now())
```

---

## 2. Ký hiệu vai trò

| Ký hiệu | Vai trò | Ghi chú |
|---|---|---|
| `SA` | System Admin | Không mặc nhiên đọc nội dung riêng tư (Nhóm 01, 10) |
| `WO` | Workspace Owner | Mỗi Team WS đúng 1 (Nhóm 02) |
| `WA` | Workspace Admin | |
| `WM` | Workspace Member | |
| `WG` | Workspace Guest | Chỉ xem trong phạm vi được cấp (Nhóm 02, 10) |
| `PM` | Project Manager | Nhiều Manager (Nhóm 03) |
| `PR` | Project Member | |
| `PG` | Project Guest | View-only (Nhóm 03) |
| `ASG` | Task assignee ACTIVE | |
| `SUB` | Người submit round hiện tại | |
| `APR` | Approver có designation ACTIVE | |
| `CRT` | Người tạo Task | |
| `SELF` | Chính chủ tài khoản | |
| `SYS` | `SYSTEM_RULE` (actor hệ thống) | Chỉ dùng trong transaction có `reason_code` |
| `—` | Không ai được phép | |

**Không suy diễn (đặc tả mục 16, cột phải):** `SA ⊄ WO ⊄ WA ⊄ WM` về **quyền đọc nội dung**. Quan hệ kế thừa chỉ áp dụng cho **quản trị metadata**. Cụ thể:
- `WO` **không** tự đọc Project chưa tham gia.
- `SA` **không** tự đọc Personal Workspace / Project riêng tư.
- `WM` **không** truy cập Project chỉ vì là Workspace Member.
- `WA` **không** vượt quyền Owner.
- Quản trị Ollama **không** kéo theo quyền đọc Project.
- `ACCOUNT_APPROVE_GLOBAL` **không** kéo theo quyền đọc dữ liệu riêng tư.

---

## 3. Effective Resource State (P6)

Trạng thái hiệu lực = trạng thái **hạn chế nhất** trong chuỗi Account → Session → Workspace → Project → Object. Thứ tự ưu tiên (mạnh trước):

```
DELETED  >  SUSPENDED/DISABLED  >  ARCHIVED  >  ON_HOLD  >  COMPLETED  >  PLANNING  >  ACTIVE
```

| Effective state | Cho phép | Chặn |
|---|---|---|
| `ACTIVE` | Toàn bộ theo ma trận | — |
| `PLANNING` (Project) | Tạo/sửa Task, thêm thành viên, milestone | Action "thực thi": start/submit/approve/reject (Nhóm 03: "chưa thực thi") |
| `ON_HOLD_READONLY` (Project) | **Chỉ đọc** + Manager đổi trạng thái Project | Mọi mutation nội dung **và workflow** (AUD-02, chờ DEC-02) |
| `COMPLETED_READONLY` (Project) | Chỉ đọc | Mọi mutation; Task đã bị cascade CANCELLED (AUD-01) |
| `ARCHIVED_READONLY` (Workspace/Project) | Chỉ đọc | Mọi mutation; reminders paused |
| `DELETED` | — | Tất cả, kể cả đọc (trừ restore theo quyền) |
| `TASK_PENDING_APPROVAL` | Đọc, comment (chờ DEC-06), approve/reject/cancel | Sửa nội dung thực thi, attachment, checklist, timelog, assignment, dependency (AUD-06) |
| `TASK_COMPLETED` / `TASK_CANCELLED` | Đọc, reopen (Manager + reason) | Sửa nội dung; "Task COMPLETED/CANCELLED không bị đổi ngược" khi đổi approval setting (Nhóm 03) |
| `ACCOUNT_PENDING_APPROVAL` | — | Mọi API nghiệp vụ; chỉ được gọi endpoint recovery/approval status |
| `SESSION_REVOKED` | — | Tất cả, gồm WebSocket, file gateway, job result (Nhóm 01 bất biến) |

**Quy tắc phủ quyết (Nhóm 03):** "Workspace hiệu lực readonly/archived **ưu tiên hơn** trạng thái Project" → nếu Workspace ARCHIVED thì dù Project ACTIVE vẫn là `ARCHIVED_READONLY`.

---

## 4. Nhóm 01 — Account, Session, Recovery

| # | Action | SA | WO/WA | WM/WG | SELF | Điều kiện state / invariant | Reauth | Audit action |
|---|---|---|---|---|---|---|---|---|
| A-01 | `ACCOUNT_REGISTER_SELF` | — | — | Y (anonymous) | — | Chỉ khi `account.self_registration_enabled = true`; tạo `PENDING_APPROVAL`; personal workspace tạo **sau khi** ACTIVE | — | `ACCOUNT_REGISTER` |
| A-02 | `ACCOUNT_APPROVE` | Y | Y nếu có grant `ACCOUNT_APPROVE_GLOBAL` | — | — | Không kéo theo quyền đọc dữ liệu riêng tư (Nhóm 01) | với SA: không; với WA: Y | `ACCOUNT_APPROVE` |
| A-03 | `ACCOUNT_REJECT` | Y | Y (grant) | — | — | reason bắt buộc | như A-02 | `ACCOUNT_REJECT` |
| A-04 | `ACCOUNT_CREATE_BY_ADMIN` | Y → ACTIVE ngay | WA → tạo tài khoản **thường**, `PENDING_APPROVAL`, trong phạm vi quyền | — | — | "Tài khoản do Admin hợp lệ tạo được ACTIVE ngay" chỉ áp cho SA | SA: Y | `ACCOUNT_CREATE` |
| A-05 | `ACCOUNT_SUSPEND` / `UNSUSPEND` | Y | — | — | — | reason bắt buộc; thu hồi session ngay | Y | `ACCOUNT_SUSPEND` |
| A-06 | `ACCOUNT_DISABLE` | Y | — | — | — | reason; thu hồi session | Y | `ACCOUNT_DISABLE` |
| A-07 | `ACCOUNT_SOFT_DELETE` | Y | — | — | — | **Bất biến:** phải chuyển WO/PM hợp lệ trước; không thể xoá SA cuối cùng (`protected_flag`) | Y | `ACCOUNT_DELETE` |
| A-08 | `ACCOUNT_RESTORE` | Y **chỉ SA** | — | — | — | Không hồi sinh membership đã bị gỡ (AUD-29) | Y | `ACCOUNT_RESTORE` |
| A-09 | `ACCOUNT_READ_PROFILE_ANY` | Y (metadata: id, username, email, status, workspace count) | Y trong WS của mình | Y trong WS của mình | Y của mình | **Không** kèm nội dung Task/Project | — | `ACCOUNT_READ` |
| A-10 | `PROFILE_UPDATE_SELF` | — | — | — | Y | Đổi **email** → reauth + ghi `user_email_history` | email: Y | `PROFILE_UPDATE` |
| A-11 | `PASSWORD_CHANGE_SELF` | — | — | — | Y | Bắt buộc khi `must_change_password = true`; revoke **mọi** phiên khác (thực tế: phiên duy nhất) | Y | `PASSWORD_CHANGE` |
| A-12 | `PASSWORD_RESET_BY_ADMIN` | Y | — | — | — | Tạo mật khẩu tạm TTL **24h** (AUD-17) | Y | `PASSWORD_RESET_ISSUE` |
| A-13 | `RECOVERY_CODE_REGENERATE` | — | — | — | Y | 8 mã, hiển thị **1 lần**, hash trong DB | Y | `RECOVERY_CODE_REGEN` |
| A-14 | `RECOVERY_CODE_REDEEM` | — | — | — | Y (offline) | one-time; tạo phiên + buộc đổi mật khẩu | — | `RECOVERY_CODE_USED` |
| A-15 | `SESSION_LOGIN` | Y | Y | Y | Y | **Một phiên duy nhất**: revoke phiên cũ + refresh + push registration (AUD-19); lockout theo ngưỡng | — | `LOGIN` |
| A-16 | `SESSION_LOGOUT_SELF` | — | — | — | Y | — | — | `LOGOUT` |
| A-17 | `SESSION_FORCE_REVOKE` | Y | — | — | Y (của mình) | reason bắt buộc khi SA làm cho người khác | SA làm cho người khác: Y | `SESSION_REVOKE` |
| A-18 | `REAUTH_ISSUE` / `REAUTH_VERIFY` | Y | Y | Y | Y | TTL 5 phút, single-use, gắn action+target (AUD-34) | — | `REAUTH_*` |
| A-19 | `GRANT_GLOBAL_PERMISSION` / `REVOKE` | Y | — | — | — | reason bắt buộc; **không self-escalation** (`actor ≠ target`) | Y | `PRIVILEGE_GRANT` / `PRIVILEGE_REVOKE` |
| A-20 | `BOOTSTRAP_SYSTEM_ADMIN` | — (chỉ CLI local) | — | — | — | Chỉ khi chưa có SA nào AND `system.bootstrap_completed = false`; lật cờ vĩnh viễn (AUD-18) | n/a (local) | `BOOTSTRAP_ADMIN_RUN` |

**Bất biến A:** Người dùng mất phiên hoặc không ACTIVE không thể tiếp tục truy cập API, job, file hoặc nội dung realtime được bảo vệ → áp dụng ở **mọi** bước P1/P2, kể cả trong job worker (AUD-48) và file gateway (ARCH-07).

---

## 5. Nhóm 02 — Workspace

| # | Action | SA | WO | WA | WM | WG | Điều kiện / invariant | Reauth | Audit |
|---|---|---|---|---|---|---|---|---|---|
| W-01 | `WORKSPACE_CREATE_TEAM` | — | Y (bất kỳ user ACTIVE) | Y | Y | — | Người tạo trở thành OWNER của WS mới | — | `WORKSPACE_CREATE` |
| W-02 | `WORKSPACE_CREATE_PERSONAL` | — | — | — | — | — | **Tự động** khi account ACTIVE; không qua API tạo | — | `WORKSPACE_PERSONAL_CREATED` |
| W-03 | `WORKSPACE_READ_METADATA` | Y (id, name, type, status, member count) | Y | Y | Y | Y (WS mình thuộc) | Metadata ≠ nội dung | — | `WORKSPACE_READ` |
| W-04 | `WORKSPACE_READ_CONTENT` | N **mặc định** | Y | Y | Y | Y theo phạm vi được cấp | SA chỉ khi có `special_grant(WORKSPACE, …, WORKSPACE_CONTENT_READ)` + reauth (AUD-50) | SA: Y | `WORKSPACE_CONTENT_READ` |
| W-05 | `WORKSPACE_UPDATE` | N | Y | Y | N | N | — | — | `WORKSPACE_UPDATE` |
| W-06 | `WORKSPACE_ARCHIVE` / `UNARCHIVE` | N | Y | N | N | N | Archive được **dù còn Task dở dang** → readonly + pause reminders | Y | `WORKSPACE_ARCHIVE` |
| W-07 | `WORKSPACE_SOFT_DELETE` / `RESTORE` | N | Y (Team) | N | N | N | **Personal Workspace không thể xoá độc lập**; restore không hồi sinh membership (AUD-29) | Y | `WORKSPACE_DELETE` |
| W-08 | `WORKSPACE_INVITE` | N | Y → ADMIN/MEMBER/GUEST | Y → ADMIN/MEMBER/GUEST | Y → **MEMBER/GUEST** | N | Email cụ thể + hạn + một lần (AUD-11); target account phải ACTIVE để membership có hiệu lực | — | `WORKSPACE_INVITE` |
| W-09 | `WORKSPACE_INVITE_REVOKE` | N | Y | Y (mời do mình/từ Admin) | Y (mời do mình) | N | — | — | `WORKSPACE_INVITE_REVOKE` |
| W-10 | `WORKSPACE_INVITE_ACCEPT` | — | — | — | — | — | Chỉ người đúng email; tài khoản ACTIVE | — | `WORKSPACE_INVITE_ACCEPT` |
| W-11 | `WORKSPACE_MEMBER_REMOVE` | N | Y → Admin/Member/Guest | Y → **Member/Guest** | N | N | **Chặn** nếu là Manager cuối của Project chưa DELETED (AUD-03) → 409 `LAST_PROJECT_MANAGER` | — | `WORKSPACE_MEMBER_REMOVE` |
| W-12 | `WORKSPACE_LEAVE` | N | N (phải transfer trước) | Y | Y | N (không phải thành viên chính thức) | Chặn như W-11 | — | `WORKSPACE_LEAVE` |
| W-13 | `WORKSPACE_MEMBER_ROLE_CHANGE` | N | Y | Y (không nâng lên OWNER, không sửa ADMIN khác) | N | N | — | — | `WORKSPACE_ROLE_CHANGE` |
| W-14 | `WORKSPACE_OWNER_TRANSFER` | N | Y | N | N | N | **Nguyên tử**: lock WS, target ACTIVE + không GUEST, Owner cũ → MEMBER, **vai trò Project không đổi** (AUD-28) | Y | `WORKSPACE_OWNER_TRANSFER` |
| W-15 | `WORKSPACE_OWNER_TRANSFER_EMERGENCY` | Y | — | — | — | — | Chỉ trong tình huống khẩn; **không đọc nội dung**; ghi `emergency_action_log` với `content_read = false` | Y **bắt buộc** | `EMERGENCY_OWNER_TRANSFER` |
| W-16 | `WORKSPACE_SETTING_UPDATE` | N | Y | Y | N | N | Không cấu hình nào vượt invariant bảo mật (Nhóm 10) | Y | `WORKSPACE_SETTING_UPDATE` |

**Thu hồi quyền (W-11/W-12) — hậu quả bắt buộc (Nhóm 02):**
1. Thu hồi quyền **ngay** với yêu cầu mới (P4 fail).
2. Gỡ `task_assignment` của Task **chưa hoàn tất** → `status = REMOVED`, giữ lịch sử, `removal_reason = MEMBERSHIP_REVOKED`.
3. Giữ lịch sử (audit/activity/comment/decision đã ghi **không** bị sửa).
4. Thông báo Manager của các Project liên quan.
5. **Không tự approve** bất kỳ Task nào của người rời.
6. Nếu là Manager → **chặn** thao tác cho tới khi có Manager thay thế (AUD-03).
7. Revoke mọi `approval_designation` ACTIVE (Nhóm 05).
8. Redact notification theo scope + chặn deep link (AUD-13).
9. Huỷ/ẩn job đang chạy dưới `principal_user_id` đó (AUD-48).

---

## 6. Nhóm 03 — Project & Milestone

| # | Action | SA | WO | WA | WM/WG | PM | PR | PG | Điều kiện / invariant | Reauth | Audit |
|---|---|---|---|---|---|---|---|---|---|---|---|
| P-01 | `PROJECT_CREATE` | N | Y | Y | WM: Y **nếu** `workspace_setting.allow_member_create_project`; WG: N | — | — | — | Creator → PM mặc định; WS phải ACTIVE | — | `PROJECT_CREATE` |
| P-02 | `PROJECT_READ_METADATA` | Y | Y (mọi Project trong WS) | Y | Y (mọi Project trong WS) | Y | Y | Y | Metadata: id, name, status, manager count, task count **dạng số tổng**? → **không**: số lượng cũng là thông tin nội dung với WG. WG chỉ thấy Project được cấp rõ | — | `PROJECT_READ` |
| P-03 | `PROJECT_READ_CONTENT` | N mặc định | N **nếu chưa tham gia** | N **nếu chưa tham gia** | N **nếu chưa tham gia** | Y | Y | Y | Đây là ràng buộc quan trọng nhất (Nhóm 02/03/10). Đường duy nhất cho WO/WA/SA: `special_grant` + reauth (AUD-50) | grant: Y | `PROJECT_CONTENT_READ` |
| P-04 | `PROJECT_UPDATE` | N | N (nếu chưa tham gia) | N | N | Y | N | N | State ≠ ON_HOLD/ARCHIVED/COMPLETED/DELETED | — | `PROJECT_UPDATE` |
| P-05 | `PROJECT_STATUS_TO_PLANNING` | N | N | N | N | Y | N | N | Chỉ từ ACTIVE/ON_HOLD | — | `PROJECT_STATUS_CHANGE` |
| P-06 | `PROJECT_STATUS_TO_ACTIVE` | N | N | N | N | Y | N | N | Từ PLANNING/ON_HOLD. Từ COMPLETED → **không tự reopen Task CANCELLED** (Nhóm 03) | — | `PROJECT_STATUS_CHANGE` |
| P-07 | `PROJECT_STATUS_TO_ON_HOLD` | N | N | N | N | Y | N | N | → readonly; pause reminders | — | `PROJECT_STATUS_CHANGE` |
| P-08 | `PROJECT_STATUS_TO_COMPLETED` | N | N | N | N | Y | N | N | **Cascade đồng bộ 1 transaction** (AUD-01): Task chưa xong → CANCELLED; approval mở → round `CANCELLED` (`round_outcome=CANCELLED`, `cancelled_reason_code='PROJECT_COMPLETED_CASCADE'`); Milestone chưa xong → CANCELLED; Task COMPLETED **giữ nguyên**. **Được phép** còn Task chưa xong, **không bắt buộc lý do** | — | `PROJECT_COMPLETED` |
| P-09 | `PROJECT_ARCHIVE` / `UNARCHIVE` | N | N | N | N | Y | N | N | Unarchive khôi phục `previous_status` | — | `PROJECT_ARCHIVE` |
| P-10 | `PROJECT_SOFT_DELETE` / `RESTORE` | N | N | N | N | Y | N | N | Restore theo `delete_group_id`, không hồi sinh membership (AUD-29) | delete: Y | `PROJECT_DELETE` |
| P-11 | `PROJECT_MEMBER_ADD` | N | N | N | N | Y **chỉ Manager** | N | N | Target phải là WS member ACTIVE; WG chỉ được gắn PG (AUD-04) | — | `PROJECT_MEMBER_ADD` |
| P-12 | `PROJECT_MEMBER_REMOVE` | N | N | N | N | Y | N (tự rời: Y) | N | **Chặn** nếu Manager cuối (AUD-03); gỡ assignment Task chưa xong; revoke designation | — | `PROJECT_MEMBER_REMOVE` |
| P-13 | `PROJECT_MEMBER_ROLE_CHANGE` | N | N | N | N | Y | N | N | Bất biến: luôn ≥1 PM ACTIVE; WG không thành PM | — | `PROJECT_ROLE_CHANGE` |
| P-14 | `PROJECT_MANAGER_RESTORE_EMERGENCY` | N | Y **metadata khẩn cấp** | N | N | — | — | — | "không mặc nhiên đọc Task" → chỉ restore membership/role, không trả về nội dung | Y | `EMERGENCY_MANAGER_RESTORE` |
| P-15 | `PROJECT_APPROVAL_ENABLE` | N | N | N | N | Y | N | N | Áp dụng cho Task **đang thực hiện** (Nhóm 03) | — | `APPROVAL_SETTING_CHANGE` |
| P-16 | `PROJECT_APPROVAL_DISABLE` | N | N | N | N | Y | N | N | **Chặn** nếu còn approval pending → 409 `PENDING_APPROVALS_EXIST` (I-22). Task COMPLETED/CANCELLED **không** bị đổi ngược | — | `APPROVAL_SETTING_CHANGE` |
| P-17 | `PROJECT_SETTING_UPDATE` (allow member create task, SLA, reminder) | N | N | N | N | Y | N | N | SLA **không** sinh quyết định tự động (AUD-08) | — | `PROJECT_SETTING_UPDATE` |
| P-18 | `MILESTONE_CREATE/UPDATE` | N | N | N | N | Y | N | N | Milestone **tùy chọn**; Task có thể không thuộc Milestone | — | `MILESTONE_CHANGE` |
| P-19 | `MILESTONE_DELETE` | N | N | N | N | Y | N | N | **Chỉ bỏ liên kết Task** (`milestone_id → NULL`), không xoá Task | — | `MILESTONE_DELETE` |
| P-20 | `MILESTONE_STATUS_COMPLETE/CANCEL` | N | N | N | N | Y | N | N | Thủ công (AUD-21, chờ DEC-21) | — | `MILESTONE_CHANGE` |
| P-21 | `APPROVER_DESIGNATE` | N | N | N | N | Y | N | N | Target ∈ participant ACTIVE, ≠ PG, ≠ WG (AUD-07); có thể chỉ định nhiều | — | `APPROVER_DESIGNATED` |
| P-22 | `APPROVER_REVOKE_DESIGNATION` | N | N | N | N | Y | N | N | Round OPEN vẫn giữ OPEN + cảnh báo Manager (Nhóm 05) | — | `APPROVER_REVOKED` |
| P-23 | `SPECIAL_GRANT_ISSUE` / `REVOKE` | Y (scope SYSTEM/WS/PROJECT) | Y (scope WS/PROJECT của mình) | N | N | N | N | N | reason + expires_at bắt buộc; **không self-grant** | Y **bắt buộc** | `SPECIAL_GRANT_ISSUE` |

---

## 7. Nhóm 04 — Task & Subtask

| # | Action | SA | WO/WA (chưa tham gia) | PM | PR | PG | ASG | CRT (không phải ASG) | Điều kiện state / invariant |
|---|---|---|---|---|---|---|---|---|---|
| T-01 | `TASK_CREATE` | N | N | Y | Y **nếu** `project.allow_member_create_task` | N | — | — | Project ∈ {PLANNING, ACTIVE}; WS ACTIVE |
| T-02 | `SUBTASK_CREATE` | N | N | Y | Y (như T-01) | N | Y (trên Task mình được giao) | Y | Parent `depth = 0`; **subtask của subtask bị chặn** (I-07) |
| T-03 | `TASK_READ` | N | N | Y | Y | Y | Y | Y | P3/P4: không có participation → **404** |
| T-04 | `TASK_UPDATE_CONTENT` (title, description) | N | N | Y | Y (Manager/creator/assignee — Nhóm 04) | N | Y | Y | **Chặn** khi `PENDING_APPROVAL` (AUD-06); state ≠ COMPLETED/CANCELLED; Project writable |
| T-05 | `TASK_UPDATE_METADATA` (priority, milestone, tag) | N | N | Y | N | N | N | N | PM sửa được cả khi PENDING_APPROVAL (chờ DEC-06) |
| T-06 | `TASK_SET_DEADLINE` / `START_DATE` | N | N | Y | Y (ASG/CRT theo T-04) | N | Y | Y | Lưu instant + tz gốc (Nhóm 06); **không tự gán deadline**; đổi deadline → cập nhật reminder + notify assignee |
| T-07 | `TASK_ASSIGN_ADD` / `REMOVE` | N | N | Y | N | N | N (tự gỡ mình: Y) | N | 0..n assignee; **chặn** khi PENDING_APPROVAL (AUD-06); target phải có participation Project |
| T-08 | `TASK_BLOCKED_SET` / `CLEAR` | N | N | Y | Y | N | Y | Y | `BLOCKED` là **cờ**, không đổi state |
| T-09 | `CHECKLIST_ADD/EDIT/DELETE/CHECK` | N | N | Y | Y | N | Y | Y | **Chặn** khi PENDING_APPROVAL |
| T-10 | `DEPENDENCY_ADD/REMOVE` | N | N | Y | Y | N | N | N | Cùng Project, top-level only (DEC-09); **chu trình hard-block** (I-09) |
| T-11 | `TIMELOG_CREATE_SELF` | N | N | Y (của mình) | Y (của mình) | N | Y | Y | `user_id` luôn = actor (AUD-23); trong edit window; **chặn** khi PENDING_APPROVAL |
| T-12 | `TIMELOG_REMOVE_OTHER` | N | N | Y + reason + audit | N | N | N | N | Không được **sửa** số giờ của người khác |
| T-13 | `TASK_SOFT_DELETE` | N | N | Y | Y chỉ khi là **CRT** và state cho phép | N | N | Y (là CRT) | Nhóm 04: "soft-delete bởi Manager/creator nếu **trạng thái và quyền** cho phép". Cascade subtask theo `delete_group_id` |
| T-14 | `TASK_RESTORE` | N | N | Y | Y (CRT) | N | N | Y | Chỉ các dòng cùng `delete_group_id` **và** còn hợp lệ (AUD-29); **không** tự reopen |
| T-15 | `TASK_READ_TIMELINE` | N | N | Y | Y | Y | Y | Y | Nhóm 09: "chỉ người có quyền xem đối tượng được đọc timeline" |
| T-16 | `TASK_EXPORT_ICS` | N | N | Y | Y | Y | Y | Y | `redaction_level = MINIMAL`; ghi `calendar_export_record` |

---

## 8. Nhóm 05 — Workflow (ma trận quyết định)

| # | Action | PM | PR | ASG | CRT | SUB | APR | Điều kiện bắt buộc (P7) | Audit outcome khi fail |
|---|---|---|---|---|---|---|---|---|---|
| F-01 | `TASK_START` (TODO → IN_PROGRESS) | Y | N | Y | Y | — | — | `isValidActiveActor`; Project ∈ {ACTIVE}; **PLANNING chặn** (Nhóm 03: "chưa thực thi"); Task không bị soft-delete | `DENIED_STATE` |
| F-02 | `TASK_SUBMIT` (IN_PROGRESS → COMPLETED **nếu** approval OFF) | Y **chỉ khi** Task không có ASG | Y **chỉ khi** không có ASG | Y | Y **chỉ khi** không có ASG | — | — | Mọi subtask ∈ {COMPLETED, CANCELLED} (I-08); Project approval **OFF** | `DENIED_STATE` / `SUBTASKS_INCOMPLETE` |
| F-03 | `TASK_SUBMIT_FOR_APPROVAL` (IN_PROGRESS → PENDING_APPROVAL) | như F-02 | như F-02 | Y | như F-02 | — | — | Project approval **ON**; tạo `approval_round` mới (`round_no = max+1`), `submitted_by = actor`; designation mặc định = PM đủ điều kiện; **nếu không có approver độc lập** → vẫn PENDING_APPROVAL + notify Manager + `APPROVAL_NO_APPROVER` (Nhóm 05) | `SUBTASKS_INCOMPLETE` |
| F-04 | `TASK_APPROVE` | Y **nếu** là APR hợp lệ | Y **nếu** là APR hợp lệ | N | Y **nếu** là APR hợp lệ | N | Y | **CẤM** nếu actor ∈ ASG(task) ∪ {SUB} (I-11, I-12); designation ACTIVE; round OPEN; **một terminal decision** (I-13); `FOR UPDATE` trên round | `DENIED_SELF_APPROVAL` / `APPROVAL_ALREADY_DECIDED` |
| F-05 | `TASK_REJECT` | như F-04 | như F-04 | N | như F-04 | N | Y | **reason bắt buộc** (≥3 ký tự) → Task về `IN_PROGRESS`; **không** có trạng thái `REQUEST_CHANGES` | `REJECT_REASON_REQUIRED` |
| F-06 | `TASK_CANCEL` (từ TODO / IN_PROGRESS / PENDING_APPROVAL) | Y | N | N | Y | — | — | Nếu đang PENDING_APPROVAL → đóng round bằng `round_outcome = CANCELLED`, `decision_outcome = CANCELLED`, **không giả mạo** approve/reject (Nhóm 05); cascade subtask chưa xong → CANCELLED | `DENIED_STATE` |
| F-07 | `TASK_REOPEN_FROM_COMPLETED` (COMPLETED → IN_PROGRESS) | Y **chỉ PM** | N | N | N | — | — | **reason bắt buộc** + audit; **lịch sử approval giữ nguyên**; round cũ bất biến (I-14); vòng mới tạo ở lần submit kế | `REOPEN_REASON_REQUIRED` |
| F-08 | `TASK_REOPEN_FROM_CANCELLED` (CANCELLED → IN_PROGRESS) | Y **chỉ PM** | N | N | N | — | — | Thủ công; **không tự reopen Subtask**; nếu Task bị cascade bởi Project COMPLETED → chỉ reopen được khi Project đã về ACTIVE | `DENIED_STATE` |
| F-09 | `SYSTEM_CASCADE_CANCEL` | — | — | — | — | — | — | Chỉ chạy **trong transaction** P-08, `actor_type = SYSTEM_RULE`, `reason_code = PROJECT_COMPLETED_CASCADE` (AUD-01) | — |
| F-10 | `APPROVAL_SLA_FLAG` | — | — | — | — | — | — | `SYS` chỉ set `sla_breached = true` + notify; **không bao giờ** tạo `approval_decision` (AUD-08) | — |

### 8.1. Eligible approver (AUD-07, chờ DEC-07)

```
isEligibleApprover(task, round, user) :=
     isValidActiveActor(user, task.workspace_id, task.project_id)
 AND project_membership(task.project_id, user, status='ACTIVE', role IN ('MANAGER','MEMBER'))   -- PG và WG bị loại
 AND approval_designation(round, user, revoked_at IS NULL) EXISTS
 AND user ∉ { task_assignment(task, status='ACTIVE') }              -- cấm self-approval theo assignee
 AND user ≠ round.submitted_by                                      -- cấm self-approval theo submitter
 AND round.status = 'OPEN'
 -- KHÔNG kiểm tra task.created_by  → đúng Nhóm 05: "riêng creator không bị cấm chỉ vì là creator"
```

### 8.2. Transaction approve (đặc tả mục 14.3 — bắt buộc tuân thủ)

```
BEGIN;
  1. P1..P8 (policy pipeline)
  2. SELECT * FROM task           WHERE id = $taskId FOR UPDATE;   -- optimistic version check
  3. SELECT * FROM approval_round WHERE id = $roundId FOR UPDATE;  -- AUD-27 lớp 1
  4. guard_no_self_approval()      (trigger)                       -- I-11, I-12
  5. INSERT approval_decision      → uq_decision_single_terminal   -- AUD-27 lớp 2
  6. UPDATE approval_round SET status/round_outcome/decided_*
  7. UPDATE task SET state = 'COMPLETED' | 'IN_PROGRESS', version = version+1
  8. INSERT workflow_event (append-only)
  9. INSERT audit_event
 10. INSERT outbox_event (notification, reminder recompute, redaction…)
COMMIT;   -- 14.3 bước 4: "Commit rồi mới phát thông báo; nếu thông báo lỗi, retry notification, KHÔNG lặp approve"
```

---

## 9. Nhóm 09 — Files & Activity

| # | Action | PM | PR | PG | ASG | CRT | Điều kiện state |
|---|---|---|---|---|---|---|---|
| FL-01 | `FILE_UPLOAD` | Y | Y | N | Y | Y | Có quyền **sửa** đối tượng + state cho phép; **chặn** khi Task PENDING_APPROVAL (AUD-06); allowlist content_type + max size từ `system_setting` |
| FL-02 | `FILE_PREVIEW` / `FILE_DOWNLOAD` | Y | Y | Y | Y | Y | Chỉ cần quyền **xem nội dung**; **kiểm tra tại mỗi request** (ARCH-07); single-use `StreamTicket` TTL ≤ 30s; **không** trả URL S3 trực tiếp |
| FL-03 | `FILE_DELETE` (soft) | Y | Y (người upload hoặc PM) | N | Y (của mình) | Y | Chặn khi PENDING_APPROVAL |
| FL-04 | `FILE_RESTORE` | Y | Y | N | N | Y | Chỉ khi parent còn hợp lệ; theo `delete_group_id` |
| FL-05 | `FILE_VERSION_ADD` | Y | Y | N | Y | Y | Không ghi đè; `version_no` tăng |
| FL-06 | `FILE_VERSION_RESTORE_AS_NEW` | Y | Y | N | N | Y | Tạo phiên bản **mới** trỏ object cũ (Nhóm 09) |
| FL-07 | `FILE_AI_READ` | theo quyền đối tượng | | | | | Chỉ khi user **hiện** có quyền xem; ghi `file_access_event(access_type='AI_READ')` |
| FL-08 | `ACTIVITY_TIMELINE_READ` | Y | Y | Y | Y | Y | Cùng điều kiện với `TASK_READ` |
| FL-09 | `AUDIT_READ` | N (mặc định) | N | N | N | N | Chỉ khi: (a) `global_permission_grant('AUDIT_READ_SYSTEM')` cho scope SYSTEM + sensitivity NORMAL, hoặc (b) `special_grant('AUDIT_READ_SCOPE')`, hoặc (c) **có quyền đọc chính đối tượng tại thời điểm truy vấn** với `sensitivity = SENSITIVE_CONTENT` (AUD-36). SA **không** mặc nhiên đọc |

---

## 10. Nhóm 06/07/08 — Calendar, Notification, AI, Reports

| # | Action | Ai được | Điều kiện |
|---|---|---|---|
| C-01 | `CALENDAR_READ_PERSONAL` | SELF | Task mình là ASG/CRT/PM, đã lọc quyền |
| C-02 | `CALENDAR_READ_PROJECT` | PM/PR/PG | Chỉ Project có participation |
| C-03 | `CALENDAR_READ_WORKSPACE` | WO/WA/WM/WG | **Chỉ** tổng hợp Project mà người xem có participation; không tiết lộ Project ẩn (AUD-38 → `PARTIAL`) |
| C-04 | `REMINDER_PREFERENCE_SET_SELF` | SELF | Không tắt được `SECURITY_CRITICAL` |
| C-05 | `REMINDER_SHARED_SET` | PM | AUD-24: người nhận = ASG ACTIVE + PM |
| C-06 | `ICS_EXPORT` | như C-01..03 | `MINIMAL` redaction; ghi `calendar_export_record` |
| N-01 | `NOTIFICATION_LIST/READ/MARK_ALL_READ` | SELF | Đồng bộ cấp tài khoản |
| N-02 | `PUSH_REGISTER` / `UNREGISTER` | SELF | Gắn `session_id`; phiên cũ bị cascade huỷ (AUD-19) |
| AI-01 | `AI_REQUEST_SUGGESTION` | PM/PR (theo scope yêu cầu) | `ai.enabled = true`; context theo Manifest; **không** dùng cho WG/PG (không có quyền ghi) — ngoại trừ `NATURAL_SEARCH` cho PG trong scope được cấp |
| AI-02 | `AI_JOB_STATUS_READ` | chính `principal_user_id` | Người khác (kể cả SA) **không** đọc được |
| AI-03 | `AI_SUGGESTION_ACCEPT` | chính `principal_user_id` | Chỉ tạo **draft**; mutation thật đi qua T-01/F-xx với actor = user |
| AI-04 | `AI_MODEL_CONFIG_UPDATE` | SA + grant `OLLAMA_CONFIG_MANAGE` | reauth + audit; **không** kéo theo quyền đọc Project (Nhóm 07) |
| AI-05 | `AI_HISTORY_READ` | chính user; hoặc scoped grant | redact khi mất quyền |
| R-01 | `DASHBOARD_PERSONAL_READ` | SELF | Tách ASSIGNED / CREATED / MANAGED, **không** cộng trùng (AUD-37) |
| R-02 | `DASHBOARD_PROJECT_READ` | PM/PR/PG | PG: chỉ số liệu tổng, không drill-down nội dung |
| R-03 | `DASHBOARD_WORKSPACE_READ` | WO/WA/WM | Chỉ Project có participation → `PARTIAL` |
| R-04 | `DASHBOARD_SYSTEM_READ` | SA | Vận hành/tài khoản/AI status; **không** nội dung Task riêng tư |
| R-05 | `REPORT_EXPORT_REQUEST` | theo R-01..R-04 | Job + reauthorize trước khi giao (AUD-16) |
| R-06 | `REPORT_EXPORT_DOWNLOAD` | **chỉ** `requested_by` | Mỗi lượt tải re-check quyền + TTL 24h |
| R-07 | `PRODUCTIVITY_RANKING_INDIVIDUAL` | **— không ai —** | Nhóm 08: "Không xếp hạng/điểm năng suất cá nhân" → **action này không tồn tại** trong hệ thống |

---

## 11. Nhóm 10 — System Configuration & Administration

| # | Action | Ai | Điều kiện |
|---|---|---|---|
| S-01 | `SYSTEM_SETTING_READ` | SA (grant `SYSTEM_CONFIG_MANAGE`) | Không trả secret plaintext |
| S-02 | `SYSTEM_SETTING_UPDATE` | SA (grant) | reauth + `configuration_change_log` before/after; **CHECK bảo mật**: không setting nào vượt invariant (Nhóm 10) |
| S-03 | `SECURITY_EVENT_READ` | SA | Chỉ metadata, không nội dung |
| S-04 | `RATE_LIMIT_CONFIG` | SA | reauth |
| S-05 | `BACKUP_TRIGGER` / `RESTORE_DRILL` | SA (grant) | reauth + audit; **không** tuyên bố đạt RPO/RTO nếu chưa có bằng chứng |
| S-06 | `RETENTION_UPDATE` / `AUDIT_PURGE` | SA (grant `RETENTION_MANAGE`) | reauth; chỉ role `stm_maintenance`; ghi `audit_maintenance_log` (AUD-14) |
| S-07 | `JOB_STATUS_READ` / `JOB_CANCEL` / `JOB_RETRY` | chính `principal_user_id`; SA chỉ cho job `principal_type='SYSTEM'` | NFR-24; retry **phải** re-evaluate quyền |
| S-08 | `EMERGENCY_ACTION` | SA / WO | **Danh sách đóng** bằng CHECK: `SYSTEM_ADMIN_OWNER_TRANSFER`, `WORKSPACE_OWNER_RESTORE_MANAGER`, `SYSTEM_ADMIN_ACCOUNT_RESTORE`, `SYSTEM_ADMIN_SUSPEND`, `SYSTEM_ADMIN_FORCE_LOGOUT`. Bắt buộc: reason ≥10 ký tự + reauth + audit + `content_read = false` |
| S-09 | `SELF_ESCALATION` | **— không ai —** | Nhóm 10: "không self-escalation" → `actor_user_id ≠ target_user_id` cho A-19, P-23, S-02 |

---

## 12. Ma trận "KHÔNG được suy diễn" (bảng bắt buộc trong code review)

| Suy diễn sai | Thực tế theo baseline | Trích dẫn |
|---|---|---|
| SA đọc được mọi Task | N SA không đọc nội dung riêng tư; chỉ metadata + emergency | Nhóm 01, 10 |
| WO đọc được mọi Project trong WS | N Cần participation hoặc `special_grant` | Nhóm 02, 03 |
| WA có quyền như WO | N Không vượt Owner | Nhóm 02 |
| WM truy cập Project vì là thành viên WS | N Cần `project_membership` | Nhóm 02, 10 |
| WG sửa/mời/quản lý | N View-only | Nhóm 02, 10 |
| PM bỏ qua readonly | N State phủ quyết | Nhóm 03 |
| PM tự duyệt Task mình được giao/submit | N Cấm tuyệt đối | Nhóm 05 |
| Creator bị cấm duyệt chỉ vì là creator | N Chỉ cấm nếu là ASG hoặc SUB | Nhóm 05 |
| Approver được cấp = tự động có quyền đọc nội dung khác | N Chỉ trong scope Task/round | Nhóm 05 |
| Quản trị Ollama ⇒ đọc Project | N | Nhóm 07, 10 |
| `ACCOUNT_APPROVE_GLOBAL` ⇒ đọc dữ liệu riêng tư | N | Nhóm 01 |
| Frontend ẩn nút ⇒ backend không cần kiểm tra | N Backend kiểm tra **mọi** request | Nhóm 10, ARCH-06 |
| Job nền dùng quyền lúc enqueue | N Reauthorize tại bước nhạy cảm + trước khi giao kết quả | NFR-24 |
| Link file đã cấp ⇒ tải được mãi | N Mỗi request kiểm tra lại | ARCH-07, Nhóm 09 |
| AI suggestion accepted ⇒ Task tự đổi | N Phải qua use case chuẩn với actor = user | Nhóm 07 |

---

## 13. Ánh xạ action → mã quyền (dùng cho `@RequiresPermission`)

| Nhóm action | Prefix mã quyền | Ví dụ |
|---|---|---|
| Account/Session | `ACCOUNT_*`, `SESSION_*`, `PROFILE_*`, `PASSWORD_*`, `RECOVERY_*`, `GRANT_*` | `ACCOUNT_APPROVE`, `SESSION_FORCE_REVOKE` |
| Workspace | `WORKSPACE_*` | `WORKSPACE_OWNER_TRANSFER` |
| Project | `PROJECT_*`, `MILESTONE_*`, `APPROVAL_SETTING_*`, `APPROVER_*`, `SPECIAL_GRANT_*` | `PROJECT_STATUS_TO_COMPLETED` |
| Task | `TASK_*`, `SUBTASK_*`, `CHECKLIST_*`, `DEPENDENCY_*`, `TIMELOG_*`, `TAG_*` | `TASK_UPDATE_CONTENT` |
| Workflow | `TASK_START`, `TASK_SUBMIT`, `TASK_APPROVE`, `TASK_REJECT`, `TASK_CANCEL`, `TASK_REOPEN_*` | — |
| Files | `FILE_*`, `ACTIVITY_*`, `AUDIT_READ` | `FILE_DOWNLOAD` |
| Calendar/Notify | `CALENDAR_*`, `REMINDER_*`, `ICS_*`, `NOTIFICATION_*`, `PUSH_*` | — |
| AI | `AI_*` | `AI_MODEL_CONFIG_UPDATE` |
| Reports | `DASHBOARD_*`, `REPORT_*` | `REPORT_EXPORT_DOWNLOAD` |
| Admin | `SYSTEM_*`, `SECURITY_*`, `BACKUP_*`, `RETENTION_*`, `JOB_*`, `EMERGENCY_*` | — |

Mỗi action trong ma trận trên **bắt buộc** có đúng 1 mã quyền, và mỗi mã quyền được kiểm tra ở đúng 1 nơi (`AuthorizationPolicy`). Không được hardcode `if (role == MANAGER)` rải rác trong service.

---

## 14. Danh sách thao tác nhạy cảm bắt buộc reauthentication (AUD-34, chờ DEC-34)

| Mã action | Áp dụng cho |
|---|---|
| `PASSWORD_CHANGE_SELF` | mọi user |
| `PROFILE_UPDATE` (chỉ khi đổi **email**) | mọi user |
| `RECOVERY_CODE_REGEN` | mọi user |
| `RECOVERY_CODE_REDEEM` | offline flow |
| `ACCOUNT_SUSPEND` / `UNSUSPEND` / `DISABLE` / `DELETE` / `RESTORE` | SA |
| `ACCOUNT_CREATE_BY_ADMIN` | SA |
| `PASSWORD_RESET_ISSUE` | SA |
| `SESSION_FORCE_REVOKE` (cho người khác) | SA |
| `GRANT_GLOBAL_PERMISSION` / `REVOKE` | SA |
| `WORKSPACE_OWNER_TRANSFER` | WO |
| `WORKSPACE_OWNER_TRANSFER_EMERGENCY` | SA |
| `WORKSPACE_ARCHIVE` / `WORKSPACE_SOFT_DELETE` / `RESTORE` | WO |
| `WORKSPACE_SETTING_UPDATE` | WO/WA |
| `PROJECT_MANAGER_RESTORE_EMERGENCY` | WO |
| `PROJECT_SOFT_DELETE` / `RESTORE` | PM |
| `SPECIAL_GRANT_ISSUE` / `REVOKE` | SA/WO |
| `SYSTEM_SETTING_UPDATE` | SA |
| `AI_MODEL_CONFIG_UPDATE` | SA |
| `RETENTION_UPDATE` / `AUDIT_PURGE` | SA |
| `BACKUP_TRIGGER` / `RESTORE_DRILL` | SA |
| Mọi `EMERGENCY_ACTION` | SA/WO |

**Quy tắc:** TTL 5 phút, single-use, gắn `(action_code, target_type, target_id)`. Reauth **không** tạo session mới (giữ bất biến một phiên). Thiếu reauth → `428 REAUTH_REQUIRED` (không phải 401) để client phân biệt được.

---

## 15. Rate limit đề xuất (chưa chốt — DEC-43)

| Endpoint group | Ngưỡng đề xuất | Khóa theo |
|---|---|---|
| `POST /auth/login` | 10/phút/IP, 5/phút/username | IP + username |
| `POST /auth/reauth` | 5/phút/user | user |
| `POST /auth/recovery/*` | 3/giờ/email | email |
| `POST /files/*/upload` | 30/phút/user, tổng 500 MB/ngày/user | user |
| `GET /files/*/download` | 120/phút/user | user |
| `POST /ai/*` | 10/phút/user, concurrency toàn cục = `ai.max_concurrency` | user + global |
| `POST /reports/*/export` | 5/phút/user, 50/ngày/user | user |
| Mọi API khác | 300/phút/user | user |
| WebSocket | 60 msg/phút/phiên | session |

Vượt ngưỡng → `429` + `security_event(RATE_LIMIT_HIT)`; **không** tiết lộ ngưỡng trong response body.

---

## 16. Điểm CHƯA chốt của ma trận (phụ thuộc quyết định mở)

| Vùng | DEC | Ảnh hưởng lên ma trận |
|---|---|---|
| P-03: WO/WA/SA có đường `special_grant` để đọc Project không | **DEC-50** | Nếu "không" → xoá P-23 và mọi đường đọc khẩn cấp; audit cũng không đọc được nội dung |
| F-02/F-03: ai submit khi Task không có ASG | Nhóm 05 đã rõ, nhưng cần xác nhận "creator hợp lệ" có bao gồm creator đã rời Project | Thêm điều kiện `isValidActiveActor` cho CRT |
| T-04/T-09/T-11: danh sách khoá ở PENDING_APPROVAL | **DEC-06** | Đổi cột "Điều kiện state" của T-04, T-07, T-09, T-11, FL-01, FL-03 |
| F-01: PLANNING có cho start Task không | Đặc tả: "PLANNING cho tạo/sửa Task nhưng chưa thực thi" → **chặn start**. Cần xác nhận vì "thực thi" không được định nghĩa | Nếu cho start → đổi P6 của `PLANNING` |
| F-04: APR có được là PR thường không | **DEC-07** | Nếu chỉ PM → deadlock khi PM duy nhất là SUB (AUD-07) |
| FL-02: TTL `StreamTicket` | DEC-43 | Ảnh hưởng UX preview + bảo mật |
| AI-01: PG có được dùng `NATURAL_SEARCH` không | **DEC-39** | Ảnh hưởng Context Manifest |
| R-02: PG xem được số liệu gì | DEC-38 | Ảnh hưởng envelope `PARTIAL` |
| Rate limit | DEC-43 | Bảng mục 15 chỉ là đề xuất |
