# Tài khoản và dữ liệu kiểm thử local

Tạo ngày 13/11/2026 trong PostgreSQL của **smarttask-dev**, cổng **55432**. Đăng nhập tại [http://localhost:5173/login](http://localhost:5173/login). Đây là dữ liệu demo local được tạo theo yêu cầu, không phải account production hay database audit 55439 trước đó.

**Mật khẩu chung của 4 tài khoản: `DemoLocal@2026!`**

| Username | Vai trò Workspace | Vai trò trong Project demo |
|---|---|---|
| `demo_owner` | OWNER | MANAGER |
| `demo_admin` | ADMIN | MANAGER |
| `demo_member` | MEMBER | MEMBER |
| `demo_guest` | GUEST | GUEST |

`demo_admin` là Admin của Workspace, không được cấp SYSTEM_ADMIN hoặc quyền duyệt tài khoản toàn hệ thống. Tài khoản demo được tạo ACTIVE qua IdentityService, không cần duyệt lại hoặc dùng mật khẩu của tài khoản đang có.

Workspace: **[DEMO LOCAL] Kiểm thử 4 vai trò**. Project: **[DEMO LOCAL] Xây dựng website nhóm**.

Phân quyền sau rà soát: xem [ma trận giao diện và trách nhiệm](ROLE-INTERFACE-MATRIX.md) hoặc mở [Vai trò và việc cần làm](http://localhost:5173/my-role). Vai trò luôn theo từng Workspace/Project; Workspace cá nhân riêng của tài khoản vẫn có Owner riêng, không làm tăng quyền trong Workspace demo.

- [Mở Project và danh sách công việc](http://localhost:5173/projects/01a1d499-7eb4-7b32-8fa4-2d66e2532997?tab=tasks).
- [Mở Việc của tôi — công việc cá nhân ngoài Project](http://localhost:5173/my-tasks).

## Dữ liệu mẫu

| Công việc dự án | Quy mô | Ưu tiên | Người được giao ngoài Manager |
|---|---|---|---|
| Kiểm tra nội dung trang chủ | Nhỏ | LOW | Member và Guest |
| Rà soát giao diện mobile | Nhỏ | MEDIUM | Chỉ Manager xem/quản lý |
| Xây dựng chức năng quản lý công việc | Lớn, 2 việc con | HIGH | Member |
| Chuẩn bị nghiệm thu website | Lớn, 2 việc con | URGENT | Chỉ Manager xem/quản lý |

Tổng dữ liệu seed: **4 Task gốc + 4 subtask**; mỗi tài khoản có **4 Task cá nhân riêng** (2 nhỏ, 2 lớn), gồm TODO/IN_PROGRESS/COMPLETED và một hạn quá hạn để kiểm tra bộ lọc/Calendar. Các thời hạn được tính theo thời điểm seed. Task cá nhân không gắn Project.

Các mục có tiền tố `[UI DEMO]` được tạo thêm bằng trình duyệt thật để xác minh thao tác và được giữ lại cho kiểm tra, không xóa dữ liệu sau test. Lượt test phân quyền có thêm nhóm và ghi chú Project để kiểm tra quyền sửa/chỉ đọc. Member có thể đã bắt đầu Task “Kiểm tra nội dung trang chủ” trong lượt browser test nên trạng thái IN_PROGRESS là kết quả kiểm thử có chủ đích.

## Cách kiểm tra

1. Đăng nhập Owner, mở Project → tab Công việc → **Tạo công việc dự án**: Project được lấy từ trang đang mở, không có bộ chọn Project nữa.
2. Nhấn **Tạo mới → Việc dự án mới** từ Project: màn hình Công việc giữ Project hiện tại và mở form tạo.
3. Mở một Task lớn → nhập **Tên việc con** → **Thêm việc con**. API lấy Project từ Task cha; không yêu cầu chọn lại Project. Refresh để kiểm tra dữ liệu còn; có thể nhấn tên việc con để mở chi tiết. Hệ thống hiện hỗ trợ subtask một cấp.
4. Owner/Admin nhìn được mọi Task trong Project demo. Member chỉ thấy Task được giao, có thể bắt đầu công việc; sửa metadata khi chưa có custom grant bị từ chối. Guest chỉ đọc Task được giao, backend từ chối tạo subtask/chuyển trạng thái.
5. Với mỗi tài khoản, mở **Việc của tôi**: có 4 việc riêng. Không dùng UUID Task cá nhân của người khác để đọc được dữ liệu.
6. Đổi tài khoản bằng Đăng xuất rồi Đăng nhập; hoặc dùng các browser profile riêng để thử đồng thời. Các tab cùng browser dùng chung localStorage phiên đăng nhập.

## Seed lại và kiểm thử tự động

```powershell
cd D:\task
$env:JAVA_HOME='C:\Program Files\Java\jdk-21'
powershell -ExecutionPolicy Bypass -File .\scripts\seed-local-demo.ps1
node .\web\scripts\demo-role-e2e.mjs
```

Script seed xác minh container dev và cổng loopback trước khi chạy, không tạo endpoint seed trong ứng dụng và không tự chạy khi khởi động backend. Nó dùng một transaction, tái sử dụng fixture đúng marker/email/role, không reset mật khẩu, không ghi đè account trùng tên khác, không phục hồi dữ liệu đã xóa hoặc reset trạng thái bạn đã kiểm thử. Nếu fixture đã bị đổi role/mật khẩu, script dừng để tránh tự thay đổi lại.

Mật khẩu trên chỉ dùng cho các tài khoản demo này. Không chạy seed trên server có dữ liệu thật. Script không cấp global grants, không xóa database/volume, không sửa Flyway và không thay cấu hình tài khoản đang có.

Fixture IDs nằm trong `D:\task\.run\local-demo\fixtures.json`; log seed, test và kết quả browser trong cùng thư mục. Ảnh chụp thực ở `docs/screenshots/local-demo/`. Kết quả trình duyệt được ghi vào `ui-results.json`; các lần lỗi trước được giữ riêng.

## Kết quả kiểm tra ngày 13/11/2026

- Frontend: **36/36 tests đạt**; typecheck, lint và build đều thành công sau khi ràng buộc giao diện theo quyền.
- Backend: **35/35 kiểm thử liên quan đạt**, gồm chính sách Task, tạo Project theo vai trò, sở hữu nhật ký tiến độ và các kiểm tra hồi quy Project.
- Edge headless trên ứng dụng local thật: **42/42 bước đạt** cho 4 tài khoản; không có lỗi JavaScript hoặc HTTP ngoài các kiểm tra từ chối quyền có chủ đích.
- Đã kiểm tra tạo Task trong Project, Tạo nhanh giữ Project, subtask kế thừa Project và còn sau refresh, quyền Member/Guest, dữ liệu cá nhân riêng và màn hình mobile 390px không tràn ngang. Đã xem ảnh chụp desktop/mobile thực.
- Seed chạy lại giữ nguyên số tài khoản và công việc; không reset trạng thái đã kiểm thử.
- Đã sửa mất thông tin người đăng nhập sau refresh và chuyển tải danh sách chuyển giao sang khi người dùng mở thao tác, tránh gọi API quản lý khi chỉ xem Task.
- Đã xác nhận quyền sửa/chỉ đọc ghi chú Project, quyền tạo nhóm/sửa thành viên, trang trách nhiệm riêng, chặn URL Cài đặt và quản trị hệ thống. Bài kiểm thử giãn nhịp request, không đổi giới hạn bảo vệ API. Các lượt lỗi do selector, chờ tải quyền và chạy liên tiếp chạm giới hạn được lưu riêng.

Bằng chứng mới nhất: `.run/local-demo/ui-results.json`, `role-ui-final.log`, `role-backend-final.log`, `role-web-tests-final.log` và `role-web-results-final.json`.

Kết quả này xác nhận phạm vi demo local; không thay thế các điều kiện chấp nhận production trong báo cáo kiểm toán.
