# Backup / Restore — SmartTask V1 (PH-7, phạm vi code + docs)

> Không code AI. Không hạ tầng thật trong repo này; tài liệu này mô tả quy trình
> chuẩn để vận hành ngoài môi trường dev.

## 1. Phạm vi backup

| Thành phần | Công cụ | Tần suất đề xuất |
|---|---|---|
| PostgreSQL (toàn bộ schema public) | `pg_dump --format=custom` | daily 02:00 + trước mọi migration Production |
| Object Storage (MinIO bucket `smarttask`) | `mc mirror` sang bucket `smarttask-backup-<date>` | daily, sau pg_dump |
| `system_setting` + `report_definition` seed | kèm trong pg_dump; export JSON riêng để diff | mỗi lần đổi cấu hình |

Giữ `retention_daily=7 / weekly=4 / monthly=3` (seed `backup.retention_*`).

## 2. Lệnh mẫu (chạy trên host backup, KHÔNG trên máy dev ổ D:)

```bash
STAMP=$(date -u +%Y%m%dT%H%M%SZ)
pg_dump -h $PGHOST -U stm_backup -Fc smarttask > /backup/pg/smarttask-$STAMP.dump
mc mirror --overwrite minio/smarttask minio-backup/smarttask-$STAMP
echo "$STAMP $(sha256sum /backup/pg/smarttask-$STAMP.dump)" >> /backup/manifest.log
```

## 3. Restore drill checklist (chạy ở môi trường Test, KHÔNG dùng DB/file production)

- [ ] 1. Chuẩn bị VM/DB Test trống, xác nhận không trỏ production (`SELECT current_database()`).
- [ ] 2. `pg_restore -d smarttask_test /backup/pg/<stamp>.dump` — ghi thời gian chạy.
- [ ] 3. `flyway info` trên DB vừa restore: version phải khớp Production.
- [ ] 4. Mirror object test từ `smarttask-<stamp>`, chạy `STORAGE_RECONCILE` stub job, đối chiếu `file_object` count.
- [ ] 5. Khởi động app trỏ Test DB, gọi `GET /api/v1/health/readiness` → `ready=true`.
- [ ] 6. Đăng nhập 1 tài khoản test, mở dashboard Project + 1 export CSV (TTL 24h) để xác nhận đọc được.
- [ ] 7. Ghi `audit_maintenance_log`-tương đương (file evidence): stamp, người chạy, kết quả từng bước, thời gian RTO đo được.
- [ ] 8. Xoá dữ liệu drill khỏi Test sau khi ký evidence (không để rò rỉ sang lần khác).

## 4. Điều KHÔNG làm

- Không restore đè Production khi chưa có phê duyệt + backup mới nhất ngay trước thao tác.
- Không dùng dữ liệu production cho test hiệu năng.
