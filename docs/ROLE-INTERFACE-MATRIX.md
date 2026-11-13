# Phân cấp vai trò và ràng buộc giao diện

Áp dụng cho web/API local sau lần rà soát 13/11/2026. Mở **Vai trò và việc cần làm** (`/my-role`) để xem vai trò thực tế của tài khoản theo từng phạm vi.

## Phạm vi và cấp quyền

- **Tài khoản hệ thống**: đăng nhập, bảo mật và dữ liệu cá nhân. Quản trị hệ thống yêu cầu live grant `SYSTEM_ADMIN`; tên vai trò Owner/Admin Workspace không thay thế grant này.
- **Workspace**: Owner > Admin > Member > Guest/Viewer. Owner/Admin tạo Project, tổ chức nhóm và mời thành viên. Member/Guest không tự tạo Project trong Workspace đang tham gia. Mọi tài khoản đang hoạt động vẫn có thể tạo Workspace riêng và trở thành Owner của Workspace đó.
- **Project**: Manager quản lý Project; Member thực hiện; Guest chỉ xem. Workspace Owner/Admin không tự kế thừa quyền đọc nội dung mọi Project. Người tạo Project được cấp Manager trong Project mới; các Project khác cần membership riêng.
- **Task**: Manager xem toàn Project. Member/Guest chỉ thấy Task được giao; Member có quyền riêng có thể được mở rộng phạm vi đọc để thực hiện quyền đó. Thiếu quyền đọc trả 404; đọc được nhưng thao tác vượt quyền trả 403.
- **Subtask**: một cấp, lấy Project từ Task cha; không có bộ chọn Project khác. Quyền đọc vẫn được xét trên từng Task, không mặc nhiên kế thừa quyền được giao từ cha.
- **Việc cá nhân**: `/me/tasks` luôn giới hạn chủ sở hữu. Owner/Admin/Guest đều có việc cá nhân riêng; vai trò cao không được đọc việc riêng người khác.

## Trách nhiệm của bốn vai trò Workspace

| Vai trò | Trách nhiệm | Không được tự làm |
|---|---|---|
| Owner | Tổ chức Workspace, bổ nhiệm/vận hành quản trị, tạo Project, quản lý nhóm, mời thành viên, bàn giao quyền sở hữu | Truy cập nội dung Project chưa tham gia; quản trị hệ thống khi thiếu grant |
| Admin | Vận hành Workspace, mời thành viên, tạo Project, quản lý nhóm | Xóa/chuyển quyền sở hữu Workspace; tự có quyền hệ thống |
| Member | Thực hiện Task được giao, cập nhật tiến độ, báo vướng mắc và nộp kết quả | Tạo Project; tự giao việc, sửa hạn/nội dung, duyệt kết quả hoặc cấp quyền |
| Guest | Theo dõi Project tham gia và Task được giao/chia sẻ | Thay đổi Task, tạo subtask/checklist, tick checklist, ghi thời gian, bình luận, đổi trạng thái |

Owner/Admin demo là **Manager trong Project demo** vì đã được cấp membership đó. Không dùng vai trò Workspace để suy ra quyền thao tác Task.

## Màn hình và thao tác

| Giao diện | Người được xem | Thao tác và ràng buộc |
|---|---|---|
| Tổng quan, lịch, thông báo, tìm kiếm | Tài khoản đăng nhập | Chỉ dữ liệu trong phạm vi được backend cho đọc; thông báo của chính người nhận |
| Việc của tôi | Mọi tài khoản | Tạo/sửa/bắt đầu/hoàn thành/xóa/khôi phục việc riêng; không gắn Project |
| Workspace | Thành viên Workspace | Hiện vai trò riêng trên mỗi thẻ. Form gửi lời mời chỉ chọn Workspace TEAM có quyền Owner/Admin; Workspace PERSONAL không mời thành viên. Nhận lời mời của chính tài khoản |
| Danh sách Project | Người tham gia Project | Nút tạo chỉ xuất hiện khi có Workspace được tạo Project; bộ chọn tạo chỉ gồm Workspace có quyền. Kích hoạt/bật tắt duyệt chỉ hiện trong Project quản lý |
| Chi tiết Project → Công việc | Thành viên Project | Hiện vai trò và việc cần làm. Manager tạo Task trong đúng Project hiện tại; Member/Guest chỉ thấy các Task có quyền đọc |
| Chi tiết Project → Tiến độ | Thành viên Project | Manager/Member ghi tiến độ khi Project cho phép ghi; Guest chỉ đọc. Member chỉ sửa/xóa bản ghi do mình tạo, Manager quản lý mọi bản ghi |
| Chi tiết Project → Thành viên | Thành viên Project | Xem danh sách. Quyền quản lý thành viên vẫn do Manager và backend kiểm tra |
| Chi tiết Project → Cài đặt | Manager được ghi | Ẩn tab với người khác; mở URL trực tiếp hiển thị từ chối, không dựng form. Lưu chế độ duyệt dùng version hiện tại. Bỏ công tắc cho phép Member tạo Task vì API tạo Task yêu cầu Manager |
| Chi tiết Task → Nội dung/hạn | Manager hoặc Member được cấp `TASK_METADATA_WRITE` | Hiện editor khi được phép; khóa khi Task chờ duyệt/kết thúc hoặc Project/Workspace chỉ đọc |
| Chi tiết Task → Subtask/checklist/phụ thuộc | Manager hoặc Member được cấp quyền metadata | Thêm subtask chỉ ở Task gốc. Member thường không thấy form thiết kế công việc |
| Chi tiết Task → Tick checklist/ghi thời gian | Manager hoặc Member được giao | Guest chỉ xem; khóa nội dung thực hiện khi chờ duyệt/kết thúc. Chuyển trạng thái còn xét trạng thái Project và Task |
| Chi tiết Task → Bình luận | Manager hoặc Member được giao | Guest không có form gửi; vẫn xem bình luận được phép đọc |
| Chi tiết Task → Chuyển giao | Manager hoặc Member được cấp `TASK_ASSIGNMENT_WRITE` | Người khác chỉ xem người phụ trách; không gọi API danh sách ứng viên khi mở trang; ứng viên do server kiểm tra |
| Phân quyền dự án | Manager | Chỉ chọn Project quản lý; chỉ cấp hai quyền riêng cho Member, không tự cấp cho mình; không cấp quyền duyệt hoặc quyền hệ thống |
| Nhóm | Thành viên Workspace | Mọi người xem nhóm. Owner/Admin tạo nhóm. Owner/Admin hoặc Manager của nhóm sửa thành viên; liên kết Task vẫn phải có quyền giao việc của Task đó |
| Ghi chú | Chủ ghi chú cá nhân hoặc người có quyền đọc phạm vi chia sẻ | Ghi chú cá nhân chỉ chủ sửa. Ghi chú Project cần Manager; ghi chú Task cần quyền metadata; ghi chú Workspace cần quyền ghi và chủ sở hữu hoặc quản trị Workspace. Người khác xem bằng editor chỉ đọc; vô hiệu hóa ghim/xóa/thuộc tính |
| Tệp | Người đọc được Task | Chỉ Manager được tải lên/khôi phục phiên bản theo chính sách tệp; người chỉ đọc không thấy form. Task chờ duyệt/kết thúc không hiện thao tác ghi |
| Báo cáo | Tài khoản có quyền đọc phạm vi báo cáo | Số liệu/khả năng xuất do backend lọc theo Task được đọc; không tự mở rộng sang Task của người khác |
| Cài đặt cá nhân | Chính tài khoản | Bảo mật, mật khẩu và tùy chọn riêng; không thay đổi role Workspace/Project |
| Quản trị hệ thống (`/admin`) | Live grant `SYSTEM_ADMIN` | UI mặc định từ chối khi đang tải hoặc không tải được quyền; Owner/Admin Workspace bị chặn cả khi nhập URL trực tiếp |
| Vai trò và việc cần làm (`/my-role`) | Mọi tài khoản | Ma trận trách nhiệm và danh sách Workspace/Project của chính người đang đăng nhập |

## Luồng Task

1. Manager tạo Task/subtask, đặt nội dung, hạn, checklist và người thực hiện trong Project.
2. Project **PLANNING**: chuẩn bị công việc; chưa được bắt đầu hoặc nộp kết quả.
3. Project **ACTIVE**: người thực hiện bắt đầu TODO → IN_PROGRESS, cập nhật checklist/thời gian và nộp hoàn thành.
4. Nếu cần duyệt: Task → PENDING_APPROVAL; khóa nội dung thực hiện. Chỉ người quản lý đủ điều kiện và được chỉ định trong vòng duyệt mới có nút duyệt/yêu cầu sửa. Không tự duyệt việc mình nộp hoặc được giao.
5. Khi hoàn thành/hủy, mở lại cần Manager, Project cho phép ghi và lý do tối thiểu 3 ký tự. Guest không có nút chuyển trạng thái.
6. Project ON_HOLD/COMPLETED/ARCHIVED hoặc Workspace ARCHIVED: chỉ đọc. Đổi quyền/trạng thái khi trang đang mở vẫn được backend kiểm tra lại trên mỗi mutation.

## Cơ chế kiểm tra

`GET /me/access` lấy quyền từ membership ACTIVE, trạng thái Workspace/Project và live grants. `GET /tasks/{id}/access` kiểm tra quyền đọc trước rồi trả quyền của riêng Task, trạng thái khóa và các workflow action thực sự được phép. Client mặc định ẩn thao tác khi quyền chưa tải được, làm mới khi đổi tài khoản, focus cửa sổ hoặc có sự kiện đổi Workspace/Project. API ghi tiếp tục kiểm tra quyền, version và trạng thái; capability không phải token cấp quyền.

Bộ kiểm thử và bằng chứng local nằm tại `.run/local-demo/role-*`, ảnh trình duyệt tại `docs/screenshots/local-demo/`. Kết quả demo không thay thế các gate production trong báo cáo kiểm toán.

Kết quả cuối ngày 13/11/2026: **35 kiểm thử backend, 36 kiểm thử frontend và 42 bước trình duyệt đều đạt**. Typecheck/lint/build thành công; không có lỗi JavaScript hoặc HTTP ngoài các trường hợp từ chối quyền được kiểm tra có chủ đích. Đã xem ảnh chụp thực của trang vai trò và giao diện quản lý/chỉ đọc Ghi chú, Nhóm.
