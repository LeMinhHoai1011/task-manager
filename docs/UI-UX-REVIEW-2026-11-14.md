# UI/UX, tài khoản và lịch nhắc — 14/11/2026

What this change does: Bổ sung màn hình quản lý tài khoản đầy đủ và thiết kế lại luồng lịch hẹn, thông tin đã lưu, cài đặt nhắc việc. Giữ cơ chế phân quyền theo dự án/không gian, API cũ và schema hiện có; các API mới được bổ sung riêng.

## Review: các lỗi đã sửa

1. **Admin chỉ nhìn thấy hàng đợi chờ duyệt.**
   - What this is: `AccountController.pending()` và `Admin.tsx` chỉ đọc `GET /accounts`.
   - Problem: tài khoản ACTIVE không xuất hiện; danh sách trống khiến người quản trị tưởng hệ thống không có quản lý tài khoản.
   - Fix: thêm `/accounts/directory` phân trang và tìm theo tên/email/tên đăng nhập, lọc trạng thái; giữ `/accounts` cũ. UI chỉ hiện duyệt/từ chối cho PENDING; duyệt lặp ACTIVE vẫn an toàn. Từ chối ACTIVE trả 409.
   - If we skip it: quản trị viên không thể tra cứu và khóa tài khoản đang hoạt động từ giao diện.

2. **Đặt lại mật khẩu báo thành công nhưng không chuyển được mật khẩu.**
   - What this is: UI gọi `/accounts/{id}/reset-password`; backend tạo chuỗi ngẫu nhiên mà không có kênh giao nhận hoạt động.
   - Problem: người dùng mất khả năng đăng nhập dù UI báo thành công.
   - Fix: UI dùng `PUT /accounts/{id}/password`, nhập mật khẩu tạm; lưu hash, buộc đổi mật khẩu, thu hồi phiên. Không trả plaintext trong response. Có xác nhận, chặn tự khóa/tự đặt lại và tài khoản được bảo vệ; khóa và thu hồi phiên cùng transaction.
   - If we skip it: người quản trị không thể bàn giao mật khẩu và người dùng bị khóa ngoài tài khoản. Endpoint reset cũ giữ tương thích nhưng UI mới không gọi nó.

3. **Giờ yên lặng không được lưu; một số tùy chọn dùng sai loại thông báo.**
   - What this is: `Settings.tsx` giữ giờ nghỉ trong state; các tên chung như DEADLINE_WARNING không khớp loại sự kiện thực tế.
   - Problem: tải lại trang mất lựa chọn; bật/tắt loại thông báo không ảnh hưởng sự kiện mong muốn.
   - Fix: tải/lưu `/reminders/preferences`, chọn múi giờ; dùng TASK_REMINDER_FIRE, TASK_DEADLINE_SOON/OVERDUE và loại workflow/comment thực. Chỉ đổi checkbox sau khi API lưu thành công, khóa thao tác khi chưa tải xong.
   - If we skip it: người dùng tin đã cấu hình giờ nghỉ hoặc tắt nhắc nhưng hệ thống vẫn hoạt động theo cấu hình cũ.

4. **Phản hồi lưu ghi chú có thể thay thế bản nháp mới hơn.**
   - What this is: effect đồng bộ toàn bộ editor mỗi khi đối tượng ghi chú đổi; lưu sau 800ms dùng version đóng trong callback.
   - Problem: đang lưu bản A, gõ tiếp bản B, phản hồi A làm editor quay về A; thay ghi chú rồi gõ tiếp có thể hủy timer của ghi chú trước.
   - Fix: bản nháp và timer riêng theo ghi chú, hàng đợi ghi tuần tự, lấy version mới nhất trước mỗi lần PUT; chỉ khởi tạo editor khi đổi ghi chú. Có trạng thái chưa lưu, nút Lưu ngay, cảnh báo đóng tab và giữ bản nháp khi lỗi.
   - If we skip it: người dùng thấy mất chữ hoặc lưu thất bại do version cũ. Khi xung đột với một phiên khác, không tự ghi đè dữ liệu của phiên đó.

5. **Đổi mật khẩu gửi sai tên trường.**
   - What this is: Settings gửi current_password, AuthController nhận old_password.
   - Problem: mật khẩu hiện tại không được truyền vào đúng hợp đồng API.
   - Fix: gửi old_password; giữ xác nhận mật khẩu và xử lý lỗi hiện có.
   - If we skip it: thao tác đổi mật khẩu từ cài đặt không hoạt động đúng.

## Yêu cầu và kết quả trong phạm vi lần này

| Luồng | Yêu cầu hiển thị / hành vi | Trạng thái |
|---|---|---|
| Điều hướng | Công việc cá nhân, thông tin đã lưu, lịch & nhắc hẹn, chức năng dự án theo quyền, mục quản trị riêng | Đã tích hợp |
| Vai trò | Lấy từ quyền đang có; không suy từ username và không tự cấp quyền dự án cho Admin | Giữ và đồng bộ vào Cài đặt |
| Tài khoản | Tìm kiếm, trạng thái, phân trang, tạo tài khoản, duyệt/từ chối đúng trạng thái, khóa/mở khóa, mật khẩu tạm | Đã tích hợp |
| Gán vai trò | Thực hiện qua thành viên Workspace/Project hiện có, không thêm một dropdown vai trò toàn cục gây sai quyền | Giữ cơ chế hiện tại, giải thích trên UI |
| Nhật ký | Xem metadata được phép, không đưa nội dung riêng tư vào màn hình Admin; lỗi tải có thông báo | Đã giữ trong tab riêng |
| Công việc | Danh sách/Kanban, deadline, ưu tiên, lọc và thao tác theo quyền | Giữ, đồng bộ diện mạo và nội dung hướng dẫn |
| Thông tin đã lưu | Phân biệt ghi chú cá nhân/dự án/chia sẻ; có trạng thái lưu và thử lưu lại | Đã bổ sung |
| Lịch | Ngày/tuần/tháng/chưa lên lịch, chọn ngày, agenda theo ngày và phạm vi được phép | Đã thiết kế lại |
| Hẹn cá nhân | Tiêu đề, nội dung cần nhớ, giờ hẹn, giờ nhắc; lưu task cá nhân + reminder trong một transaction | Đã bổ sung |
| Chống tạo trùng hẹn | Idempotency key theo actor; cùng key và payload trả kết quả cũ, payload khác trả 409; rollback khi reminder không hợp lệ | Đã bổ sung |
| Nhắc công việc | Chọn công việc được đọc, lịch nhắc chỉ thuộc người đặt | Giữ, cải thiện giao diện |
| Quản lý nhắc | Hiển thị tên công việc, thời gian, trạng thái; đổi giờ, tạm dừng/tiếp tục, hủy nhắc; giữ nội dung công việc khi hủy | Đã bổ sung |
| Quyền riêng tư | Không lộ tiêu đề task đã mất quyền; không sửa nhắc của người khác; tài khoản thường không đọc directory | Có kiểm tra API |
| Múi giờ | Nhập giờ theo múi giờ trình duyệt, gửi UTC có offset; lưu múi giờ của hẹn và giờ yên lặng | Đã tích hợp |
| Thông báo | Chỉ hứa nhắc trong ứng dụng; FIRED hiển thị “Đã xử lý”, không khẳng định email/push đã gửi | Đã tích hợp |
| Responsive | Tài khoản Admin xếp thành từng dòng đầy đủ thao tác trên mobile; lịch co theo màn hình, form và agenda xếp dọc | Đã kiểm tra 1440px và 390px |

Hẹn cá nhân là task PERSONAL có deadline và description, đi kèm reminder của chính người dùng. Người dùng chỉnh sửa nội dung/giờ hẹn trong Việc của tôi; đổi giờ nhắc trong Lịch & nhắc hẹn. Đổi deadline không tự dời một lời nhắc được đặt thủ công. Hủy lời nhắc không xóa lịch hẹn hay nội dung task.

Lịch phân trang tối đa 200 task/trang; nhắc việc 100/trang có Tải thêm. Số lượng hiển thị chỉ là dữ liệu trong trang, không giả lập tổng số toàn hệ thống. Task ngoài trang chọn hiện tại có thể tải thêm để đặt nhắc.

## Yêu cầu tiếp theo, chưa triển khai

- Lịch lặp cần định nghĩa rõ RRULE, múi giờ/DST, sửa một lần hay cả chuỗi và tránh gửi trùng trước khi thêm UI.
- Nhắc qua email/Web Push cần gateway gửi thực, cấu hình môi trường, chính sách thử lại và trạng thái giao nhận. Gateway hiện tại chưa gửi thực.
- Giao diện quản lý cấp/thu hồi global grant cần quy tắc người cấp, chống tự nâng quyền và bảo vệ quản trị viên cuối cùng; không gộp vào tạo tài khoản thông thường.
- Đồng bộ lịch ngoài và xuất ICS đầy đủ cho PERSONAL cần mở rộng hợp đồng hiện tại. Nút/luồng hiện tại không hứa có đồng bộ hai chiều.
- Phân tích tải lớn và kiểm thử accessibility tự động toàn hệ thống nằm ngoài lần này. Directory dùng truy vấn phân trang; title reminder lấy theo lô, kiểm tra đọc theo từng task có giới hạn 100 dòng/trang.

## Thay đổi và kiểm chứng

Không thêm dependency hoặc migration; không sửa cấu hình production. Không commit/push/reset Git. Các tài khoản kiểm thử đã có được dùng để kiểm tra local; không thay mật khẩu các tài khoản đang có. Browser tạo hẹn thử có tên “Kiểm thử lịch hẹn …” trong tài khoản user; giữ lại để người dùng xem, không xóa dữ liệu tự động.

Các file chính: `web/src/pages/{Admin,Calendar,Notes,Settings,MyTasks,Tasks}.tsx`, `web/src/components/Layout.tsx`, `web/src/styles.css`; `AccountController`, `IdentityService`, `AppUserRepository`, `CalendarController`, `CalendarService`, `ReminderRuleRepository`. Test thêm: `AccountCalendarUxIT`, `WorkspaceUx.test.tsx`; điều chỉnh test controller và fixture pending trong `F3ContractIT` theo đường đi thực.

Ảnh và kết quả Edge: `.run/workspace-ux/`. Log: `.tools/ponytail/ui-*.log`. Kết quả cuối:

- Backend: 538 tests, 532 đạt, 6 skipped, 0 failures/errors (128 lớp); `.tools/ponytail/ui-backend-summary.json`. Toàn bộ `*Test,*IT` chạy trên DB kiểm thử và MinIO riêng; đã dừng hai dịch vụ kiểm thử sau khi xong.
- Web: 58/58 tests, 22 file đạt; `pnpm build` và `pnpm lint` đạt. Vitest chạy một worker; lần chạy song song bị thiếu RAM máy local, đã chạy lại tuần tự thành công. JVM kiểm thử giới hạn 768MB, không thay cấu hình production.
- Edge: Admin directory gồm ACTIVE, tìm tài khoản, form xác nhận nhận focus; trên mobile các nút vẫn nằm trong màn hình. Hẹn lưu thật, tồn tại sau reload, có trong lịch và Việc của tôi; đổi giờ/tạm dừng được; cài đặt tải được. Desktop 1440px và mobile 390px, không tràn ngang và không có lỗi JavaScript. `.run/workspace-ux/results.json`, `.tools/ponytail/ui-final-preview.log`.
- API local đã khởi động lại bằng mã cuối: web `http://localhost:5173`, API `http://localhost:8081/api/v1`. Giữ DB dev trên cổng 15432 và dữ liệu người dùng.
- `git diff --check` không có lỗi whitespace. Không kiểm toán lại toàn bộ thay đổi cũ ngoài các luồng liên quan.


Verdict: Ship. Đạt trong phạm vi các luồng local đã kiểm thử; chưa xác nhận triển khai production.
Not checked: production, email/Web Push thực, lịch lặp, mobile native, benchmark tải lớn và kiểm toán toàn bộ thay đổi có sẵn của repo.
