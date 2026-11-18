# Phân cấp giao diện và sửa sáu finding audit — 14/11/2026

## Phạm vi hoàn tất

Triển khai theo Ponytail: tái sử dụng service/repository/quyền hiện có, không thêm
dependency, migration hoặc kiến trúc mới. Giữ nguyên các thay đổi auth, deploy và
web có sẵn ngoài phạm vi này; không commit/push/reset Git.

### Giao diện và tài khoản thật trong database dev

| Tài khoản | Giao diện mặc định | Quyền được cấp |
| --- | --- | --- |
| `admin` | `/admin` | SYSTEM_ADMIN, ACCOUNT_APPROVE_GLOBAL; không tự có quyền Project |
| `manager` | `/dashboard` | Owner của Workspace test, Manager Project test |
| `employee` | `/tasks` | Member; chỉ đọc/thực hiện task được giao |
| `user` | `/my-tasks` | Dữ liệu cá nhân; không tham gia Project test |
| `guest` | `/projects` | Guest; task được giao chỉ để xem |
| `workspace_admin` | `/dashboard` | Admin Workspace test; không tham gia Project test |

Mật khẩu chung là giá trị người dùng chỉ định khi seed, được BCrypt trong database.
Không lưu plaintext trong script, config hoặc fixture JSON. Không sửa mật khẩu bộ
`demo_*` trước đó. Seeder đã chạy hai lần thành công, lần sau tái sử dụng cùng account
và fixture, không tạo trùng. Nếu trùng một tài khoản không thuộc marker/email test,
seeder từ chối và rollback cả transaction.

Runtime local được giữ để kiểm thử:

- Web: `http://localhost:5173`, API: `http://localhost:8081/api/v1`.
- Database dev: `127.0.0.1:15432`, database `smarttask`, volume Docker dev cũ.
- Cổng 55432 không bind được trên Windows; không chuyển sang database/service khác ở
  5432 hoặc server Apache đang chiếm 8080.
- Chạy lại: `scripts/dev.ps1 -DatabasePort 15432 -BackendPort 8081 -SkipInstall`.

Sidebar chia rõ Cá nhân, Thực hiện/Theo dõi/Quản lý dự án, Quản lý Workspace và Quản trị
hệ thống. Trang sau đăng nhập đợi quyền của đúng danh tính trước khi chọn giao diện.
Owner của Workspace PERSONAL không bị xếp nhầm thành Quản lý. API `/me/access` thêm
trường `workspaces[].type`, giữ các trường cũ. URL `/roles` và `/admin` chặn truy cập
trực tiếp ngoài quyền; batch toolbar chỉ hiện action phù hợp với tất cả task đã chọn.

### Các finding đã sửa

1. **Blocked lost update:** dùng row lock hiện có trước khi đọc/so phiên bản.
   Regression có hai request cùng version, bắt buộc một 200 và một 409.
2. **Dependency race:** khóa project trước khi kiểm tra và ghi cạnh. Regression cho
   A→B và B→A đồng thời, bắt buộc chỉ một cạnh được lưu và một request trả 422.
3. **Cursor sai khóa sort:** cursor v2 lưu mọi giá trị sort, null, độ chính xác thời
   gian và ID; seek predicate và SQL order dùng cùng biểu thức/thứ hạng priority.
   ID UUID cũ và cursor v1 vẫn được nhận khi task mốc còn trong phạm vi đọc. Sort
   nhiều trường dùng ID tiebreak sau tất cả trường, không làm mất trường phụ.
4. **Reopen CANCELLED:** frontend gửi `kind: cancelled`; COMPLETED gửi `kind: completed`.
5. **Assignee đã gỡ:** query chỉ nhận assignment ACTIVE, không tính row REMOVED.
6. **Query theo tổng task:** lọc quyền/scope/filter/sort và phân trang tại DB trong
   `TaskService.search`, controller chỉ chuyển DTO. SQL visibility giữ membership,
   manager/assignee và custom grant còn hiệu lực. Regression thêm 20 task nhưng số
   statement của cùng trang không tăng, tổng query request được giới hạn dưới 20.

## Xác minh

| Kiểm tra cuối | Kết quả |
| --- | --- |
| Backend toàn bộ, 127 class | 536 selected: **530 PASS / 0 FAIL / 0 ERROR / 6 SKIPPED** |
| TaskAuditRegressionIT | 4 PASS: blocked race, dependency race, mọi sort/chiều/composite, assignee và query budget |
| Architecture, custom role, cross-account, batch | PASS trong suite trên; không bỏ rule kiến trúc để cho test qua |
| StorageIT / F3ExportIT | 15 PASS với MinIO thật, đã tính trong tổng backend |
| Web unit/component | **53 PASS**, 21 file |
| Web lint, TypeScript và build | PASS |
| Browser sáu tài khoản | Home/menu/API scope và chặn URL trực tiếp PASS; không pageerror |
| Runtime dev health | 6 probe PASS: Docker, Postgres, backend, Vite, API và proxy |
| Bảo toàn thay đổi có sẵn | Hash đối chiếu không có thay đổi ngoài các file đích; diff whitespace PASS |

Các lỗi trung gian được giữ trong log: architecture test đã phát hiện Criteria code
ở controller và được xử lý bằng chuyển sang service; browser đã phát hiện điều hướng
trước khi tải access và thiếu grant account-approval trong fixture admin. Đã sửa nguồn
vấn đề, không nới authorization hoặc giảm assertion.

Hạ tầng regression tách biệt: PostgreSQL audit cổng 15435 và MinIO test cổng 19090,
không dùng database dev/production. Đã dừng hạ tầng audit sau suite; runtime dev vẫn
chạy. 6 SKIPPED là 4 placeholder cố định và 2 test AI live, không tính là PASS.

## File và bằng chứng

- Hướng dẫn vai trò: `docs/ROLE-INTERFACE-MATRIX.md`, tài khoản: `docs/LOCAL-DEMO-ACCOUNTS.md`.
- Seeder: `scripts/demo/SeedRoleAccounts.java`, `scripts/seed-local-demo.ps1 -RoleAccounts`.
- Browser: `web/scripts/roles-browser.mjs`; `.run/role-interfaces/results.json` và PNG theo username.
- Backend: `.tools/ponytail/fix-backend-all-final.log`, `fix-backend-summary.json`.
- Web: `.tools/ponytail/fix-web-tests-final.log`; `.run/local-demo/role-fixtures.json` chứa ID test, không có password.

Chưa nghiệm thu staging/production, tải đại diện, mobile E2E hoặc Ollama live. Các
warning/info Flutter đã nêu trong audit trước không nằm trong sáu finding này và
chưa được sửa. Không coi lượt kiểm thử local là xác nhận production-ready.
