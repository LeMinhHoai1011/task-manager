# Sửa 4 phát hiện review và review lại — 15/11/2026

Phạm vi: web và API calendar liên quan; không thay đổi mã Flutter/mobile, schema, cấu hình production hoặc dependency. Giữ các thay đổi chưa commit có sẵn. Không commit/push/reset Git.

## Bản sửa

1. **Bảo toàn bản nháp ghi chú khi điều hướng.** Dùng blocker của React Router đang cài. Chuyển router web sang data router với nguyên các URL và guard. Khi còn bản nháp, liên kết trong ứng dụng và Back phải chờ lựa chọn Lưu và rời trang / Ở lại / Bỏ thay đổi. Lưu lỗi giữ màn hình và bản nháp; chỉ tự rời sau khi toàn bộ bản nháp đã lưu. Cảnh báo beforeunload khi đóng/tải lại trang vẫn giữ.
2. **Khóa nhắc hẹn thống nhất.** Pause dùng khóa dòng như đổi giờ/hủy. Resume khóa tập bản ghi của actor theo thứ tự ID trước khi tính digest, tránh ghi đè trạng thái CANCELLED của lời nhắc khác. Không thêm bảng/version/schema. Dispatcher tiếp tục dùng khóa PostgreSQL hiện có.
3. **Refresh nhiều tab.** Dùng Web Locks để tuần tự hóa việc dùng refresh cookie. BroadcastChannel chia sẻ access token trong bộ nhớ; tab chờ tái sử dụng kết quả đã refresh. Mã lượt refresh không chứa bí mật nằm trong localStorage để loại thông báo đến muộn. Không lưu access/refresh token vào storage trong secure browser mode, giữ kiểm tra CSRF, cookie HttpOnly và kiểm tra logout khi request đang chạy. Chế độ cookie đang opt-in cần trình duyệt hỗ trợ Web Locks và BroadcastChannel (đã kiểm tra Edge); chế độ bearer/native giữ nguyên.
4. **Ngày cả ngày.** API lọc date-only theo ngày tại due_timezone gốc và trả thêm due_date_only. Calendar dùng ngày đó cho ô lịch; deadline có giờ tiếp tục dùng instant và múi giờ xem. Bộ lọc ngày/tuần/tháng và quyền truy cập được giữ riêng, không sửa instant đã lưu.

## Kiểm chứng

- Toàn bộ web: 61 tests, 22 file đạt. Chạy lại 10 test trọng tâm sau khi bổ sung kiểm tra loại thông báo refresh cũ: đạt.
- Backend liên quan: 17 tests đạt, gồm 3 test PostgreSQL mới trong ReminderReviewFixIT. Hai test giữ khóa dòng thật, cho yêu cầu pause/resume chạy và chờ khóa trước khi hủy; sau nhả khóa trạng thái CANCELLED vẫn giữ. Test còn lại kiểm tra ngày-only qua ranh giới ngày/tuần/tháng UTC → Bangkok và giữ hành vi deadline có giờ.
- `pnpm build`, `pnpm lint`: đạt. Bundle JS khoảng 522 KB / 147 KB gzip; Vite có cảnh báo vượt ngưỡng 500 KB sau khi dùng data router. Không đổi ngưỡng để che cảnh báo và chưa thêm code splitting ngoài phạm vi sửa lỗi.
- Edge thật: đạt. Hai tab reload/khởi động đồng thời chỉ có một request refresh, cả hai access token đều gọi API được; không có token trong localStorage. Notes chặn liên kết và native Back trong lịch sử SPA, giữ draft khi PUT thất bại; Lưu và rời trang lưu thành công và mở lại thấy đúng nội dung. Không có lỗi JavaScript. Kết quả `.run/review-fixes/results.json`, ảnh `.run/review-fixes/notes-navigation-blocked.png`, log `.tools/ponytail/review-fixes-browser.log`. Cổng 5174 chỉ dùng kiểm thử cookie mode, đã dừng; các cờ cookie/CORS tạm được bỏ và dev khởi động lại ở 5173/8081. Không thay .env hoặc production. Các ghi chú test được giữ lại trong tài khoản user, không tự xóa dữ liệu.

## Review lại

What this change does: sửa mất bản nháp khi rời Notes, tranh chấp cập nhật reminder, refresh cookie nhiều tab và ngày date-only lệch múi giờ. Dùng thư viện và dịch vụ hiện có.

Đã lần theo App/Notes và thao tác lưu/xóa, AuthContext/client/CSRF/rotation, CalendarService/dispatcher/repository, bộ lọc SQL calendar và nhóm ngày trên web. Các test mới kiểm tra hành vi lỗi và tranh chấp thực, không chỉ kiểm tra hình dạng implementation. Không phát hiện lỗi chặn mới trong phần đã kiểm chứng.

Verdict: Ship trong phạm vi 4 bản sửa đã kiểm thử.

Not checked: chưa chạy lại toàn bộ backend, chưa test tải lớn/production, chưa build mobile theo yêu cầu. Nếu người dùng chủ động chọn bỏ thay đổi hoặc đóng tab sau khi chấp nhận cảnh báo, phần chưa lưu sẽ không được giữ; không lưu bản nháp nhạy cảm ra localStorage.
