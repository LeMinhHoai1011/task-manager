# Kế hoạch nhắn tin thời gian thực trong dự án

Ngày: 18/11/2026. Trạng thái: đề xuất để triển khai sau; chưa thêm chức năng nhắn tin, migration hoặc thay đổi phân quyền.

**Cập nhật triển khai:** Bản đầu đã được viết theo yêu cầu tiếp theo, có thêm liên kết công việc và xóa tin gửi nhầm. Xem [hướng dẫn/API thực tế](PROJECT-CHAT.md) và [báo cáo nghiệm thu](BAO-CAO-NHAN-TIN-DU-AN-2026-11-18.md). Phần bên dưới được giữ làm bản kế hoạch ban đầu.

## 1. Mốc source để quay lại

- Commit giao diện đã kiểm thử: `508f392`.
- Tag đã push: `checkpoint/ui-viet-hoa-20261118`.
- Nhánh đích: `origin/main`.
- Bằng chứng của mốc này: 72 kiểm thử web, 24 kiểm thử backend tập trung; kiểm tra kiểu, lint và build đạt. Chưa có kiểm thử tích hợp database thật trong đợt giao diện.

Để mở lại source của mốc trên trong một nhánh riêng, khi working tree sạch:

```powershell
git fetch origin --tags
git switch -c codex/restore-ui-20261118 checkpoint/ui-viet-hoa-20261118
```

Lệnh này tạo nhánh từ mốc đã lưu, không tua ngược lịch sử `main`. Nếu muốn hoàn tác trên `main`, cần chọn phạm vi và tạo commit hoàn tác riêng. Tag không phải bản sao database; thay đổi schema sau này cần kế hoạch phục hồi riêng.

## 2. Những gì source đã có

| Thành phần | Thực tế hiện tại | Cách tận dụng |
| --- | --- | --- |
| Realtime máy chủ | `RealtimeRegistry` giữ các kết nối SSE trong bộ nhớ một tiến trình. | Dùng kết nối có sẵn để báo có tin mới; chưa cần thêm WebSocket cho bản nhắn văn bản. |
| Phát sự kiện | `RealtimePublisher` hoãn phát đến sau khi transaction commit; chỉ gửi khóa định tuyến, không gửi nội dung. | Giữ nguyên nguyên tắc này với tin nhắn. Danh sách người nhận chat phải theo quyền chat, không dùng mù quáng danh sách mọi thành viên dự án. |
| Kết nối web | `socket.ts` lấy ticket dùng một lần, kết nối lại bằng ticket mới; `RealtimeContext` chia sẻ kết nối. | Không mở thêm một stream cho mỗi phòng chat. Thêm logic đồng bộ khi kết nối lại và khi quay về tab. |
| Thành viên | `ProjectMembershipRepository.findMembership` chỉ lấy membership đang hoạt động. | Kiểm tra lại ở mọi lần đọc, gửi và đánh dấu đã đọc. |
| Bình luận | `CommentService` đã có bình luận theo công việc và chặn khách gửi bình luận. | Tham khảo quy tắc/validation, không biến bảng bình luận công việc thành phòng chat dự án. |
| Outbox | Có bản ghi outbox và các handler; fallback có thể chỉ ghi nhận dispatch. | Không coi việc ghi outbox hiện tại là bằng chứng tin chat đã được gửi tới trình duyệt. Nếu dùng để phát lại, phải bổ sung handler chat và kiểm thử riêng. |
| Giao diện dự án | `ProjectDetail.tsx` đã có các tab nội dung. | Thêm tab **Trao đổi**, giữ ngữ cảnh dự án; không thêm nhiều bộ chọn hoặc menu chính. |

Tài liệu API cũ có đoạn mô tả WebSocket, nhưng triển khai đang dùng SSE và ticket. Khi triển khai chat phải cập nhật hợp đồng theo code thực tế; không đưa access token vào URL theo đoạn tài liệu cũ.

## 3. Phạm vi bản đầu được đề xuất

Mỗi dự án có một phòng trao đổi chung, gắn trực tiếp với quyền dự án.

- Gửi văn bản, xuống dòng, xem tác giả và giờ gửi bằng tiếng Việt.
- Xem 50 tin gần nhất, tải thêm lịch sử cũ; giữ vị trí khi đang đọc tin cũ.
- Tin gửi có trạng thái **Đang gửi / Đã gửi / Chưa gửi được — Thử lại**. “Đã gửi” chỉ có nghĩa máy chủ đã lưu, không phải mọi người đã nhận hoặc đọc.
- Mất mạng không làm mất ngay nội dung đang soạn trong phiên trang; thử lại không tạo tin trùng.
- Đếm tin chưa đọc theo người dùng và dự án; đồng bộ dấu đã đọc giữa các tab.
- Hiển thị **Đang kết nối lại…**; khi có tin mới lúc đang đọc lịch sử, hiện **Có tin mới** thay vì kéo người dùng xuống cuối.
- Giới hạn đề xuất: tối đa 4.000 ký tự/tin sau chuẩn hóa; điều chỉnh sau kiểm thử thực tế. API từ chối tin rỗng, quá dài hoặc chứa trường không được phép.

Chưa đưa vào bản đầu: nhắn riêng, tạo nhiều kênh, tệp đính kèm, ghi âm, gọi thoại/video, biểu cảm, ghim, trả lời theo nhánh, trạng thái đang gõ, chấm trực tuyến, AI đọc nội dung chat, thông báo đẩy cho từng tin và danh sách “ai đã đọc”. Sửa/xóa tin và quản trị nội dung là giai đoạn tiếp theo; nêu rõ giới hạn này khi chạy thử nhóm nhỏ.

## 4. Quyền và quy tắc cần chốt trước khi lập trình

Đây là chính sách đề xuất, chưa phải thay đổi đã áp dụng:

| Đối tượng | Xem phòng chat | Gửi tin |
| --- | --- | --- |
| Người quản lý/thành viên đang hoạt động trong dự án | Có | Có khi dự án cho phép trao đổi |
| Chủ sở hữu/quản trị không gian nhưng không thuộc dự án | Không | Không |
| Quản trị hệ thống nhưng không thuộc dự án | Không tự động có quyền | Không tự động có quyền |
| Khách/người xem | Chưa mở ở bản đầu | Không |
| Đã bị gỡ khỏi dự án, bị khóa hoặc hết phiên | Không | Không |

Lý do tách quyền chat: quyền đọc một công việc được chia sẻ không đồng nghĩa được đọc toàn bộ trao đổi chung của dự án. Nếu cần khách trao đổi, thiết kế phạm vi và quyền riêng trước khi mở.

- Đề xuất lịch sử: thành viên hiện tại có quyền chat được xem lịch sử chung, kể cả trước lúc họ tham gia. Cần xác nhận quy tắc này trước triển khai vì ảnh hưởng bảo mật nội dung cũ.
- Đề xuất trạng thái dự án: được gửi khi đang lập kế hoạch/đang hoạt động; tạm dừng, hoàn thành, lưu trữ hoặc không gian lưu trữ thì chỉ đọc. Dự án đã xóa không truy cập được. Không thay đổi quy tắc trạng thái công việc.
- Khi mất quyền: dừng tải dữ liệu, xóa nội dung phòng khỏi bộ nhớ hiển thị và bỏ bản nháp liên quan; máy chủ từ chối mọi yêu cầu tiếp theo. Nội dung đã được xem trước đó không thể thu hồi khỏi trí nhớ, ảnh chụp hoặc bản sao ngoài hệ thống.
- Nội dung hiển thị dưới dạng văn bản, không render HTML người gửi nhập. Không ghi toàn bộ nội dung tin vào log, audit hoặc sự kiện realtime.
- Thêm giới hạn gửi theo người dùng và dự án; không chỉ dựa vào bucket theo IP hiện có. Mức giới hạn phải được kiểm thử và cấu hình phù hợp tải thực tế.

## 5. Luồng giao diện

```text
Dự án: Website khách hàng
[Tổng quan] [Công việc] [Trao đổi · 3] [Ghi chú] …

Trao đổi trong dự án                     Đã kết nối
Chỉ những thành viên có quyền trao đổi được xem mục này.

                  [Xem tin trước]
                        Hôm nay
Nguyễn An · 09:10
Mình đã hoàn thiện trang đăng nhập.

Trần Bình · 09:12
Mình sẽ kiểm tra giao diện trên điện thoại.

                         [Có 2 tin mới ↓]
┌────────────────────────────────────────────────────┐
│ Nhập tin nhắn cho dự án…                            │
└────────────────────────────────────────────────────┘
Shift + Enter để xuống dòng                    [Gửi]
```

Quy trình:

1. Mở dự án → tab Trao đổi. Không phải chọn lại dự án hoặc nhập mã phòng.
2. Tải lịch sử gần nhất và quyền chat. Không đủ quyền thì không hiện khung soạn.
3. Soạn và gửi. Trên máy tính: Enter gửi, Shift + Enter xuống dòng; không gửi khi đang trong quá trình gõ tổ hợp ký tự. Trên điện thoại dùng nút Gửi rõ ràng.
4. Giữ bản tin đang gửi trong giao diện. Máy chủ xác nhận mới đổi thành Đã gửi.
5. Lỗi mạng: giữ nguyên bản tin và định danh gửi; người dùng bấm Thử lại. Không tự gửi một bản tin đã đổi nội dung với định danh cũ.
6. Nếu đang ở cuối cuộc trò chuyện thì cuộn đến tin mới. Nếu đang đọc ở trên thì giữ vị trí và hiện nút Có tin mới.
7. Chỉ đánh dấu đã đọc khi tab trình duyệt đang hiển thị và người dùng đã xem đến vị trí đó. Không đánh dấu mọi tin chỉ vì mở route.
8. Bản nháp trong bộ nhớ được tách theo tài khoản/dự án, xóa khi đăng xuất hoặc mất quyền. Bản đầu chưa bảo đảm giữ bản nháp sau khi tải lại/đóng trang; không lưu nội dung nhạy cảm vào cache service worker.

## 6. Thiết kế kỹ thuật đề xuất

### Luồng gửi và nhận

```text
Người gửi → API có xác thực → kiểm tra quyền và nội dung
          → lưu tin trong transaction → commit
          → phát tín hiệu SSE cho người có quyền chat
Người nhận → nhận tín hiệu → tải phần tin còn thiếu qua API
          → kiểm tra quyền lại → hợp nhất theo ID → hiển thị
```

REST thực hiện việc ghi/đọc; SSE báo dữ liệu đã đổi. Dữ liệu chuẩn nằm trong database. Mất một sự kiện hoặc nhận trùng không được làm mất/trùng tin trong lịch sử.

### Bảng mới dự kiến

| Bảng | Trường chính | Ràng buộc quan trọng |
| --- | --- | --- |
| `project_chat_state` | `project_id`, `last_seq` | Một hàng/dự án; tăng số thứ tự trong cùng transaction ghi tin. |
| `project_message` | `id`, `project_id`, `seq`, `sender_user_id`, `client_message_id`, `body`, `created_at` | Duy nhất `(project_id, seq)` và `(project_id, sender_user_id, client_message_id)`; chỉ mục lịch sử `(project_id, seq)`. |
| `project_message_read` | `project_id`, `user_id`, `last_read_seq`, `updated_at` | Duy nhất theo dự án/người; chỉ cho phép tiến về phía trước, không vượt tin đã tồn tại. |

`seq` phải được cấp bằng khóa hàng trạng thái dự án trong transaction để thứ tự hiển thị phù hợp thứ tự commit. Không dùng số thứ tự SSE của phiên, timestamp hoặc UUID như bằng chứng đã nhận đủ mọi tin. Điểm nghẽn tiềm năng của khóa hàng phải đo trong bài kiểm tra gửi đồng thời.

Migration bổ sung bảng mới, không đổi/xóa bảng công việc hay bình luận. Chọn số phiên bản migration tiếp theo tại thời điểm triển khai, không mặc định một số còn trống hôm nay sẽ vẫn trống.

### API dự kiến

| Phương thức | Đường dẫn | Mục đích |
| --- | --- | --- |
| GET | `/projects/{id}/chat/access` | Trả quyền đọc/gửi và lý do chỉ đọc; không xem đây là bằng chứng quyền cho request sau. |
| GET | `/projects/{id}/messages?before=…&limit=50` | Lịch sử cũ, phân trang bằng cursor. |
| GET | `/projects/{id}/messages?after=…&limit=100` | Bù tin mới sau số thứ tự đã đồng bộ. |
| POST | `/projects/{id}/messages` | Gửi `{client_message_id, body}`; người gửi lấy từ phiên đăng nhập. |
| PUT | `/projects/{id}/chat/read` | Cập nhật `last_read_seq` đơn điệu và có kiểm tra quyền. |
| GET | `/projects/{id}/chat/unread` | Số chưa đọc và điểm đọc đã lưu để hiển thị trong tab dự án. |

Tất cả nằm dưới tiền tố `/api/v1`, dùng envelope, lỗi trung tính và quy tắc idempotency sẵn có. Hai chế độ `before`/`after` không nhận đồng thời; cursor hợp lệ chỉ dùng trong đúng dự án, có giới hạn kích thước trang.

Gửi lại cùng `client_message_id` và cùng nội dung trả cùng tin đã lưu. Dùng lại định danh với nội dung khác phải báo xung đột; kiểm tra cả khi hai yêu cầu tới đồng thời bằng ràng buộc database. Nếu dùng thêm `Idempotency-Key`, định danh và payload phải được ràng buộc cùng nhau.

### Đồng bộ khi sự kiện bị mất

- SSE chỉ mang `kind`, phạm vi, mã dự án/tin và metadata định tuyến; không mang `body` hoặc tên phòng.
- Sự kiện chat được lọc đúng dự án ở phía giao diện. Hook hiện có khớp cả tiền tố phạm vi/kind, nên không được dùng một đăng ký `project` rộng rồi tải lại mọi trang mỗi lần có tin chat.
- Tải bù khi mở chat, khi kết nối lại, khi trình duyệt trở về foreground và theo nhịp dự phòng đề xuất 15 giây khi phòng chat đang hiển thị. Nhịp này là đề xuất cần đo, không quét toàn bộ lịch sử.
- API trả trang tin và mốc snapshot/cursor rõ ràng. Chỉ tăng mốc đồng bộ sau khi đã hợp nhất đủ phần liên tục từ server; POST xác nhận một tin ở số cao không được tự làm bỏ qua các tin số thấp của người khác.
- Hợp nhất theo ID/số thứ tự; tín hiệu đến trùng hoặc đảo thứ tự không tạo bản sao. Đổi dự án phải hủy/bỏ qua phản hồi của phòng cũ.
- Thu hồi quyền phải xóa UI qua sự kiện membership/access và từ chối lại trên API; không phát nội dung qua SSE cho các kết nối đã mất quyền.

## 7. Trình tự triển khai và thời gian dự kiến

Ước lượng cho một lập trình viên, sau khi thống nhất quyền và có môi trường thử nghiệm; không phải cam kết tiến độ.

| Giai đoạn | Thời gian dự kiến | Kết quả cần nghiệm thu |
| --- | --- | --- |
| 1. Chốt quyền, phạm vi và giao diện | 0,5–1 ngày | Ma trận quyền, lịch sử trước khi tham gia, trạng thái dự án, khung giao diện máy tính/điện thoại. |
| 2. Lưu trữ và API | 1–1,5 ngày | Migration, gửi không trùng, lịch sử phân trang, tên tác giả, giới hạn nội dung và kiểm tra quyền. |
| 3. Đồng bộ realtime | 1 ngày | Phát sau commit, bù tin, reconnect, sự kiện đúng phòng, thu hồi quyền và kiểm thử hai người gửi đồng thời. |
| 4. Giao diện trao đổi | 1–1,5 ngày | Tab Trao đổi, trạng thái gửi/thử lại, bản nháp tách phòng, cuộn và bộ đếm chưa đọc. |
| 5. Kiểm thử thật và bàn giao | 1–2 ngày | Hai trình duyệt/tài khoản thật, database thử nghiệm, mất mạng, quyền bị thu hồi, kiểm tra điện thoại và hướng dẫn vận hành. |

Tổng dự kiến khoảng **5–7 ngày làm việc**. Sau giai đoạn 1 nên có một mốc review giao diện. Mỗi giai đoạn triển khai dùng commit riêng để dễ xem và hoàn tác; không chạy migration trên production khi chưa có kế hoạch triển khai được duyệt.

Giai đoạn tiếp theo: sửa/xóa mềm có phiên bản và lý do quản trị; đồng bộ thay đổi của tin cũ bằng cơ chế revision/delta, không chỉ cursor tin mới; sau đó mới đánh giá nhắc tên, trả lời, tệp và tìm kiếm lịch sử. Không thêm toàn bộ các tính năng này cùng bản đầu.

## 8. Kiểm thử bắt buộc trước khi chạy thật

1. Hai thành viên hợp lệ trao đổi được, không phải tải lại trang; tác giả lấy từ phiên, không nhận tác giả tự khai trong request.
2. Người ngoài dự án, khách, quản trị không tham gia và thành viên bị gỡ không đọc/gửi được bằng cách gọi API trực tiếp.
3. Mất quyền khi trang đang mở không tiếp tục nhận nội dung mới; cache và bản nháp liên quan được xóa khỏi UI.
4. Gửi lại sau timeout và hai request cùng định danh chỉ tạo một hàng; đổi nội dung với định danh cũ bị từ chối.
5. Gửi đồng thời, tải lịch sử trong lúc có tin mới và nhận sự kiện đảo thứ tự không bỏ sót hoặc nhân đôi tin.
6. Transaction rollback không phát tin mới; lỗi phát SSE sau commit không làm mất bản tin đã lưu.
7. Mất mạng/khởi động lại backend/kết nối lại bù đủ tin; đánh dấu đã đọc không bị lùi hoặc vượt dữ liệu thực.
8. Đổi dự án khi request cũ đang chậm không hiện tin của phòng cũ; nhiều tab không làm tăng sai số chưa đọc.
9. Nội dung HTML/script được hiển thị an toàn dưới dạng chữ; kiểm tra tin quá dài, chỉ khoảng trắng và giới hạn gửi.
10. Máy tính/điện thoại: soạn tiếng Việt, gõ tổ hợp ký tự không vô tình gửi, bàn phím, màn hình nhỏ và không tự kéo xuống khi đọc lịch sử.
11. Caddy/proxy, timeout SSE và tái kết nối được kiểm thử trong môi trường triển khai thử nghiệm, không chỉ localhost.

Đề xuất mục tiêu nghiệm thu ban đầu: 95% tin xuất hiện trong 2 giây khi kết nối khỏe, tại tải thử được thống nhất; tin mất sự kiện được bù theo nhịp dự phòng. Đây là mục tiêu phải đo, chưa phải hiệu năng đã có. Tải thí điểm gợi ý: 50 kết nối, 10 dự án, tổng 1 tin/giây và một đợt gửi đồng thời; điều chỉnh theo số người dùng thực tế trước triển khai.

## 9. Vận hành và mở rộng

- Bản đầu giả định một backend phục vụ realtime. `RealtimeRegistry` hiện chỉ nằm trong bộ nhớ từng tiến trình; sticky session không tự giải quyết phát sự kiện giữa nhiều backend.
- Nếu chạy từ hai backend, cần thiết kế fan-out liên tiến trình trước khi gọi đây là realtime ổn định cho mọi người. Chỉ khi có nhu cầu đó mới chọn Redis/broker hoặc cơ chế phù hợp hạ tầng; vẫn giữ lịch sử database và API bù tin.
- Theo dõi độ trễ lưu/nhận, tỷ lệ lỗi gửi, số reconnect, số lần gửi bị giới hạn và độ chậm API lịch sử; log không chứa body.
- Giữ bản ghi tin nếu rollback ứng dụng. Migration của chat cần tương thích với bản ứng dụng cũ trong cửa sổ rollback; không xóa bảng để hoàn tác UI.
- Trước rollout rộng cần thống nhất thời hạn lưu trữ, sao lưu và khả năng sửa/xóa nội dung. Không áp chính sách tự xóa ngầm.

## 10. Đề xuất quyết định

Bắt đầu với **một tab Trao đổi theo dự án, văn bản, lịch sử, gửi lại an toàn và chưa đọc**, dùng REST + SSE đang có. Chốt trước quyền của khách, lịch sử trước ngày tham gia và phạm vi dự án chỉ đọc. Chưa viết code chat trong đợt lập kế hoạch này.
