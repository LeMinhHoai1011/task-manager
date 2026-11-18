# Trao đổi trong dự án

Triển khai ngày 18/11/2026. Bản đầu dùng PostgreSQL hiện có, API để lưu/đọc và SSE hiện có để báo thay đổi. Không thêm database, thư viện hoặc dịch vụ bên ngoài.

## Cách dùng

1. Mở dự án → **Trao đổi**. Không cần chọn lại dự án hoặc người nhận.
2. Nhập nội dung → **Gửi tin nhắn**. Máy tính dùng Enter để gửi, Shift + Enter xuống dòng; đang gõ tổ hợp ký tự không tự gửi. Điện thoại có nút gửi.
3. Dùng **Liên kết công việc** để chọn một việc trong dự án theo tên. Người nhận chỉ thấy tên/liên kết nếu có quyền xem công việc đó; quyền được kiểm tra lại khi mở.
4. Tin đang gửi có trạng thái riêng. Lỗi mạng giữ bản tin để **Thử lại tin nhắn** bằng cùng định danh; không tự tạo bản sao. Có thể **Sửa bản gửi** khi khung soạn trống; giữ định danh nếu nội dung/liên kết không đổi, dùng lần gửi mới khi đã chỉnh nội dung. Lỗi một liên kết không tự đóng phòng còn quyền xem. **Đã gửi** nghĩa là máy chủ đã lưu, không khẳng định mọi người đã đọc.
5. Dùng **Xem tin trước** để tải lịch sử cũ. Khi đang đọc ở trên, tin mới có nút **Có tin mới**, không tự kéo xuống cuối.
6. Bấm **Xóa** ở tin của mình, sau đó xác nhận. Tin được thay bằng **Tin nhắn đã được xóa**. Quản lý xóa tin người khác phải nhập lý do từ 3 đến 500 ký tự.
7. Dấu chưa đọc được lưu theo người dùng/dự án, không tính tin tự gửi hoặc tin đã xóa. Chỉ đánh dấu đến vị trí mới khi phần cuối đang hiện trong trang trình duyệt đang hiển thị.

Bản nháp chỉ nằm trong bộ nhớ của phiên trang, tách theo tài khoản và dự án; không lưu vào localStorage, sessionStorage hoặc service worker. Có thể giữ khi đổi tab/dự án trong ứng dụng; tải lại/đóng trang có thể mất bản nháp chưa gửi. Đăng xuất hoặc mất quyền sẽ xóa bản nháp liên quan.

## Quyền

| Đối tượng | Xem | Gửi/xóa |
| --- | --- | --- |
| OWNER/ADMIN/MANAGER/MEMBER đang hoạt động trong dự án | Có, gồm lịch sử trước khi tham gia | Gửi khi dự án PLANNING/ACTIVE và không gian ACTIVE; xóa tin của mình |
| OWNER/ADMIN/MANAGER trong dự án | Có | Có thể xóa tin người khác với lý do |
| GUEST/VIEWER | Không có quyền phòng chung ở bản đầu | Không |
| Quản trị hệ thống hoặc quản trị không gian nhưng không thuộc dự án | Không được tự động đọc chat | Không |
| Thành viên đã bị gỡ, tài khoản bị khóa/ngưng hoạt động hoặc hết phiên | Không | Không |

Dự án tạm dừng/hoàn thành/lưu trữ hoặc không gian lưu trữ chỉ cho đọc. Dự án/không gian đã xóa không truy cập được. Quyền xem một công việc được chia sẻ không mở rộng thành quyền xem phòng trao đổi chung.

Gỡ thành viên dự án phát thêm tín hiệu riêng tới người bị gỡ để đóng phòng và tải lại quyền. Backend vẫn kiểm tra quyền cho mọi request; dữ liệu đã xem hoặc chụp ngoài hệ thống không thể thu hồi.

## Cơ chế lưu và đồng bộ

Migration bổ sung **V18__project_chat.sql**, không sửa migration đã có hoặc bảng công việc/bình luận:

- `project_chat_state`: mốc thay đổi của dự án.
- `project_message`: tin nhắn, thứ tự tạo `seq`, mốc thay đổi `changed_seq`, tác giả, định danh gửi lại, nội dung, liên kết việc và thông tin xóa mềm.
- `project_message_read`: mốc đã đọc đơn điệu của từng người/dự án.

Mỗi lần tạo/xóa được cấp số thứ tự dưới khóa hàng trạng thái trong cùng transaction, bảo đảm thứ tự commit. `seq` dùng sắp xếp hội thoại; `changed_seq` dùng tải bù cả tin cũ bị xóa. Gửi lại được chặn bởi unique constraint `(project_id, sender_user_id, client_message_id)` và hash nội dung chuẩn hóa. Dùng định danh cũ với nội dung khác báo xung đột; gửi lại một tin đã xóa không phục hồi nội dung.

Nội dung là `TEXT` Unicode, tối đa 4.000 code point sau chuẩn hóa xuống dòng và bỏ khoảng trắng đầu/cuối; từ chối rỗng, ký tự NUL và surrogate không hợp lệ. Nội dung hiển thị bằng văn bản, không render HTML. Xóa mềm giữ bản ghi trong database nhưng API trả `body: null`, không trả liên kết hay nội dung đã xóa. Chưa áp chính sách tự xóa dữ liệu lưu trữ.

Hạn chế gửi: 30 tin mới/phút/người/dự án, kiểm tra trên database dưới cùng khóa transaction; gửi lại hợp lệ không tính là tin mới. Bộ giới hạn request chung hiện có vẫn áp dụng. Cần đo lại khi nhiều người dùng chung IP.

SSE chỉ mang sáu khóa định tuyến hiện có, không mang body/tên công việc. Phạm vi mới dùng **CHAT**, tránh kích hoạt các hook theo tiền tố `project` trên toàn ứng dụng. Phát sau commit; transaction rollback không phát sự kiện. Sự kiện là tín hiệu gợi ý, không phải kho lịch sử.

Web tải bù khi mở phòng, kết nối lại, trở về trang và mỗi 15 giây khi trang đang hiển thị. Snapshot và cursor giúp đọc hết các trang thay đổi, hợp nhất theo ID/mốc phiên bản và không để lịch sử cũ ghi đè dấu xóa. Xác nhận POST không tự tăng mốc đồng bộ, tránh bỏ qua tin của người khác. Request cũ bị bỏ qua khi đổi dự án/tài khoản hoặc mất quyền.

## API

Tiền tố: `/api/v1/projects/{projectId}`. Mọi endpoint yêu cầu phiên hợp lệ và quyền chat, trả envelope hiện có.

| Phương thức | Đường dẫn | Dữ liệu |
| --- | --- | --- |
| GET | `/chat/access` | Quyền đọc/gửi/quản trị xóa, lý do chỉ đọc |
| GET | `/messages?before=…&limit=50` | Trang lịch sử cũ theo `seq`, mặc định 50, tối đa 100 |
| GET | `/messages?after=…&until=…&limit=100` | Trang thay đổi theo `changed_seq`; `until` cố định snapshot của lần đồng bộ |
| POST | `/messages` | `{client_message_id, body, linked_task_id?}`; tác giả lấy từ phiên |
| DELETE | `/messages/{messageId}` | Header `If-Match` = `changed_seq`; body `{reason}`; có kiểm tra tác giả/quản lý |
| GET | `/chat/unread` | `{count, last_read_seq}` |
| PUT | `/chat/read` | `{last_read_seq}`; không cho lùi mốc hoặc trỏ tới tin không tồn tại trong dự án |

Trang tin trả `items`, `revision`, `next_before`, `next_after`, `has_more`, `access`, `unread`. Không dùng đồng thời `before` và `after`; `until` chỉ dùng với `after`. `next_after` là mốc an toàn để tiếp tục đồng bộ, không phải số thứ tự SSE của phiên. Tin đã xóa vẫn có dấu thay thế trong lịch sử.

Không nhận `sender_id` do trình duyệt tự khai, không đưa nội dung chat vào log/audit/SSE. API không trả request hash, lý do quản trị hoặc nội dung đã xóa trong DTO tin nhắn.

## Kiểm thử và bằng chứng

Kết quả cuối được ghi trong [báo cáo triển khai](BAO-CAO-NHAN-TIN-DU-AN-2026-11-18.md). Bằng chứng trình duyệt: [ket-qua.json](screenshots/project-chat/ket-qua.json).

- `ProjectChatIT`: PostgreSQL thật, concurrent retry, xung đột payload, quyền thành viên/khách/người ngoài, thu hồi quyền, chỉ đọc, rollback, sự kiện sau commit, lịch sử/delta, xóa mềm, đã đọc, liên kết và giới hạn gửi.
- `ProjectChatValidationTest`: Unicode, xuống dòng, giới hạn và dữ liệu không hợp lệ.
- `ProjectChat.test.tsx`: gửi lại cùng định danh, chọn liên kết, xác nhận xóa, sự kiện đúng phòng, không tự đánh dấu đọc, phản hồi chậm, bản nháp theo tài khoản và hiển thị văn bản an toàn.
- `project-chat-browser.mjs`: hai tài khoản/trình duyệt thật, backend thật, PostgreSQL kiểm thử riêng; gửi/nhận SSE, liên kết, mất mạng, thử lại, xóa, chưa đọc, điện thoại và gỡ thành viên.

Kịch bản trình duyệt cần bộ phiên do `ProjectChatIT#twoAuthenticatedMembersReadPersistedMessagesOverHttp` tạo ra với `SMARTTASK_CHAT_FIXTURE_FILE` trỏ tới `.run/chat/browser-fixture.json`. Việc xuất fixture chỉ cho phép database mang tên `smarttask_chat_itg`. Không commit fixture/token. Mỗi lượt nghiệm thu cần fixture mới vì bài cuối sẽ gỡ thành viên kiểm thử khỏi dự án.

## Triển khai và giới hạn

1. Sao lưu database và xác minh phục hồi trước khi nâng cấp môi trường đang dùng. Git tag chỉ giữ source, không sao lưu tin nhắn.
2. Triển khai backend có V18 trước hoặc cùng web; Flyway sẽ tạo ba bảng mới. Không chạy SQL thủ công lên production để bỏ qua migration.
3. Kiểm tra quyền bằng hai tài khoản trên môi trường thử nghiệm rồi mới mở dùng. Giữ volume PostgreSQL bền vững và kế hoạch backup/retention.
4. Nếu rollback ứng dụng, giữ nguyên các bảng chat và dữ liệu; không xóa bảng hoặc sửa lịch sử Flyway để quay lại giao diện cũ.

`RealtimeRegistry` hiện nằm trong bộ nhớ một backend. Chưa bổ sung fan-out cho nhiều backend, chưa đo tải lớn hoặc cam kết p95. API tải bù bảo vệ lịch sử khi mất tín hiệu, nhưng không biến nhiều tiến trình thành một hệ thống realtime dùng chung.

Chưa làm nhắn riêng, trả lời theo nhánh, nhắc tên, biểu cảm, tệp chat, đang gõ, gọi thoại/video hoặc danh sách ai đã đọc. Bình luận công việc hiện có vẫn giữ nguyên.
