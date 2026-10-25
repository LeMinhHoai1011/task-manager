# Migration Rollback — SmartTask V1 (PH-7)

Công cụ: Flyway Community (không có undo tự động) → mỗi script `V*` có file rollback
viết tay tương ứng trong cùng PR.

## 1. Bản đồ rollback

| Migration | Nội dung | Rollback script | Ghi chú |
|---|---|---|---|
| `V1__core.sql` | schema lõi | `R1__rollback_core.sql` — CHỈ dùng khi gỡ toàn bộ DB test; Production không rollback V1 | baseline |
| `V2__ph1.sql` | cột identity/workspace/project | `U2__ph1_rollback.sql`: DROP COLUMN có điều kiện cho cột V2 thêm vào | an toàn: toàn cột nullable/default |
| `V3__ph2.sql` | cột task/workflow/comment | `U3__ph2_rollback.sql`: DROP COLUMN/CONSTRAINT tương ứng | append-only tables (`approval_decision`, `workflow_event`) KHÔNG xoá dòng |
| `V4__ph3.sql` | files/collab | `U4__ph3_rollback.sql`: DROP `stream_ticket`, `file_access_event`; DROP COLUMN phần còn lại | `file_version` là append-only: rollback không xoá version đã commit |
| `V5__ph4.sql` | calendar/notify | `U5__ph4_rollback.sql`: DROP bảng mới; DROP COLUMN phần còn lại | `delivery_attempt`, `task_deadline_change` giữ nguyên (evidence) |
| `V6__ph6.sql` | reports/export | `U6__ph6_rollback.sql`: DROP `report_export_job`, `metric_daily_snapshot`, `report_definition` seed | export object hết TTL tự purge |

## 2. Quy trình rollback (Dev/Test bắt buộc drill trước khi promote)

```bash
# 1. backup bắt buộc trước mọi thao tác Production (xem 09-BACKUP-RESTORE.md)
# 2. verify hiện tại
flyway info
# 3. chạy rollback script tương ứng BẰNG TAY qua role stm_migrate
psql -f backend/app/src/main/resources/db/rollback/U4__ph3_rollback.sql
# 4. verify lại
flyway info
mvn -pl app -am test -o   # giữ xanh
```

## 3. Quy tắc (MIG-01..MIG-12, xem 02B §10)

- Không sửa script đã release; luôn tạo script mới.
- Bảng append-only (`audit_event`, `workflow_event`, `activity_event`, `file_access_event`,
  `file_version`, `metric_daily_snapshot`, `delivery_attempt`, `task_deadline_change`)
  KHÔNG BAO GIỜ bị DELETE trong rollback — drill vi phạm quy tắc này là FAIL.
- Migration Production yêu cầu backup + `backup_ref` + phê duyệt; không tự deploy.
