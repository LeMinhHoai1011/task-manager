# Báo cáo thực hiện cải tiến giao diện SmartTask

Ngày: 18/11/2026.

## 1. Mục tiêu và phạm vi

Giảm số lựa chọn phải đọc cùng lúc, chọn thông tin bằng tên, kế thừa ngữ cảnh đang mở và dùng tiếng Việt thống nhất. Chỉnh các màn hình hiện có; không xây lại ứng dụng hoặc thay đổi mô hình dữ liệu.

Đã lập [quy trình và tiêu chí nghiệm thu](QUY-TRINH-CAI-TIEN-GIAO-DIEN-2026-11-18.md) và [bản giao diện dự kiến](giao-dien-du-kien.html) trước khi sửa sản phẩm. Bản dự kiến dùng dữ liệu minh họa; ảnh trong [báo cáo giao diện](bao-cao-giao-dien-2026-11-18.html) là ảnh chụp ứng dụng sau khi chỉnh với API giả lập.

## 2. Quy trình thực hiện chi tiết

| Bước | Công việc đã làm | Kết quả và nơi kiểm tra |
| --- | --- | --- |
| 1. Khảo sát | Đọc định tuyến, các trang, bộ lọc, bộ chọn, API thành viên, phân công, tệp và kiểm thử liên quan. | Xác định các ô UUID, bộ lọc dày, danh sách chỉ tải trang đầu và nhãn còn tiếng Anh. |
| 2. Thiết kế trước | Viết luồng sử dụng, quy tắc hiển thị và bản xem trước có tương tác. | Hai tài liệu được tạo trước khi chỉnh source sản phẩm. |
| 3. Bộ chọn dùng chung | Tạo bộ chọn tên có tìm không dấu, tên phụ, bàn phím, trạng thái tải/lỗi/rỗng, thử lại và tải thêm. | `SearchSelect.tsx`, `ResourcePicker.tsx`, `MemberPicker.tsx`; không thêm dependency. |
| 4. Bộ lọc công việc | Tận dụng `FilterBar` đã có; giữ ba lựa chọn chính, chuyển điều kiện khác vào phần chi tiết. | Có nhãn điều kiện đang áp dụng, bỏ từng điều kiện, xóa tất cả; thêm Giao cho tôi, Quá hạn và Chờ duyệt. |
| 5. Tạo và phân công | Thu gọn form tạo; giữ dự án từ trang đang mở; chọn người theo tên thay UUID. | Mô tả/ưu tiên trong “Thông tin thêm”; giao hàng loạt lấy giao của các danh sách người hợp lệ. |
| 6. Ngữ cảnh và dữ liệu chậm | Xóa lựa chọn phụ thuộc khi đổi dự án; chặn phản hồi cũ ghi đè dữ liệu ngữ cảnh mới. | Bộ chọn tài nguyên, thành viên, phân công chính và danh sách nhóm trong bộ lọc phạm vi. |
| 7. Tệp | Đặt lịch sử tại từng tệp, lấy các phiên bản có thật, chọn rồi xác nhận khôi phục thành phiên bản mới. | Không nhập attachment ID hoặc số phiên bản thủ công; quyền đọc/ghi vẫn kiểm tra ở máy chủ. |
| 8. Màn hình liên quan | Áp dụng chọn tên vào lịch, nhóm, không gian, dự án, phân quyền, ghi chú, báo cáo và trợ lý. | Giữ phân trang/tải thêm cho dữ liệu chưa được tải hết; kế thừa công việc khi mở Tệp/Lịch từ chi tiết. |
| 9. Việt hóa | Chuẩn hóa vai trò, trạng thái, ưu tiên, nhãn, trợ giúp, thông báo, tiêu đề trang và ngày hiển thị. | Giao diện mặc định luôn tiếng Việt kể cả máy từng lưu lựa chọn tiếng Anh; nội dung người dùng nhập giữ nguyên. |
| 10. Xác minh | Kiểm tra kiểu, lint, kiểm thử web/backend tập trung, bản dựng và trình duyệt. | Số liệu cuối cùng được ghi ở mục 6; có ảnh máy tính và điện thoại. |

## 3. Quy trình sử dụng sau khi chỉnh

### Tìm và lọc công việc

1. Mở Công việc hoặc mở công việc từ dự án đang xem.
2. Chọn dự án theo tên. Chọn trạng thái nếu cần.
3. Mở Người phụ trách, gõ tên hoặc tên đăng nhập. Gõ không dấu vẫn tìm được tên tiếng Việt; tên đăng nhập giúp phân biệt người trùng tên.
4. Nếu cần điều kiện khác, mở Lọc chi tiết: ưu tiên, nhãn, trở ngại, loại công việc, thời hạn hoặc sắp xếp.
5. Xem các điều kiện đang áp dụng. Bỏ từng điều kiện hoặc bấm Xóa tất cả để trở về mặc định.
6. Nếu còn kết quả hoặc lựa chọn ở trang sau, dùng Xem thêm/Tải thêm. Tìm trong bộ chọn là tìm các lựa chọn đã tải, có lời nhắc rõ khi còn trang tiếp theo.

### Tạo việc và phân công

1. Bấm Tạo công việc dự án.
2. Nếu mở từ dự án, dự án đã được điền. Nếu mở toàn bộ công việc, tìm và chọn dự án được phép tạo việc.
3. Nhập tên; đặt thời hạn nếu cần. Mô tả và ưu tiên trong Thông tin thêm.
4. Bấm Tạo công việc; mở chi tiết từ thông báo thành công.
5. Để chuyển người chịu trách nhiệm chính, mở phần phân công, tải danh sách hợp lệ, tìm người và xác nhận chuyển giao.
6. Để thêm người cộng tác cho nhiều việc, chọn các việc, chọn Thêm người thực hiện, tìm người phù hợp cho mọi việc rồi bấm Áp dụng. Kết quả thành công và thất bại vẫn báo riêng.

Lưu ý nghiệp vụ: cộng tác viên và người chịu trách nhiệm chính có điều kiện khác nhau. Khách đủ điều kiện vẫn có thể được thêm để cộng tác/xem theo nghiệp vụ cũ; họ không được đưa vào danh sách người thực hiện chính.

### Tệp và lịch

1. Từ chi tiết công việc, bấm Tệp đính kèm hoặc Đặt nhắc việc. Công việc được mang sang màn hình đích.
2. Tại Tệp, xem trước/tải xuống ở đúng hàng của tệp. Bấm Lịch sử phiên bản để xem dữ liệu đã lưu.
3. Chọn phiên bản nguồn và bấm Xác nhận khôi phục thành phiên bản mới. Không ghi đè phiên bản cũ.
4. Tại Lịch, dùng Nhắc công việc với công việc đã chọn hoặc tìm công việc khác. Hẹn cá nhân vẫn dùng luồng riêng.

## 4. Những thay đổi API cần triển khai cùng web

Các thay đổi có tính bổ sung, không xóa trường hoặc endpoint cũ, không đổi schema:

- `GET /projects/{id}/members`: giữ `user_id`, `role`; bổ sung `display_name`, `username`. Lấy hồ sơ theo một truy vấn nhóm, không trả email/mật khẩu.
- `GET /tasks/{id}/primary-assignee`: bổ sung `primaryDisplayName` để người chỉ đọc cũng thấy tên mà không cần tải danh sách quản lý phân công.
- `GET /tasks/{id}/assignment-candidates`: bổ sung tên đăng nhập; vẫn giữ điều kiện người chịu trách nhiệm chính.
- `GET /tasks/{id}/collaboration-candidates`: danh sách người có thể thêm cộng tác, kiểm tra quyền quản lý phân công và thành viên nhóm liên kết. Tách điều kiện này để không làm mất nghiệp vụ cộng tác của khách.
- `GET /files/{id}/versions`: trả số phiên bản, ghi chú, thời điểm tạo và dung lượng. Kiểm tra quyền đọc công việc; chặn tệp đã xóa; không lộ khóa lưu trữ hoặc thông tin đăng nhập.

## 5. Việt hóa và khả năng truy cập

- Không dịch tên người, tên công việc/dự án, nội dung ghi chú, tên tệp hoặc dữ liệu người dùng đã nhập.
- Không đổi mã enum, khóa API, đường dẫn, quyền hoặc nội dung kỹ thuật lưu trong lịch sử. Chi tiết sự kiện vẫn có thể mở xem khi cần.
- Giữ tên sản phẩm và các tên định dạng quen thuộc như PDF, CSV, XLSX, Markdown và phím tắt.
- Bộ chọn tìm được không dấu, có nhãn, danh sách lựa chọn, phím lên/xuống, Enter, Escape, đóng khi chuyển tiêu điểm và trả tiêu điểm về nút mở.
- Trên điện thoại, bộ lọc chính xếp một cột, vùng bấm rõ; bảng giữ cuộn ngang trong vùng của nó.
- Điều khiển ngày giờ gốc của trình duyệt vẫn phụ thuộc ngôn ngữ trình duyệt/hệ điều hành; ngày dạng văn bản trong các trang công việc dùng định dạng Việt Nam.

## 6. Kết quả xác minh

| Hạng mục | Kết quả | Bằng chứng/phạm vi |
| --- | --- | --- |
| Kiểm tra kiểu TypeScript | Đạt | `pnpm typecheck` |
| Kiểm tra mã nguồn | Đạt, không cảnh báo lint | `pnpm lint` |
| Kiểm thử web | 72/72 đạt; 27 tệp kiểm thử | `pnpm exec vitest run`; gồm tìm không dấu, bàn phím, tên trùng, tải thêm, phản hồi chậm, phân công, khôi phục và Việt hóa. |
| Backend tập trung | 24 đạt; không lỗi, không bỏ qua | `WorkspaceProjectRealReadTest`, `FileVersionHistoryTest`, `AssignmentCandidateTest`, `TaskBatchServiceTest`, `TaskControllerRealReadTest`. |
| Bản dựng production | Đạt | `pnpm build`; còn cảnh báo gói JavaScript trên 500 kB, khoảng 150 kB sau nén gzip. |
| Trình duyệt | 7 nhóm nghiệm thu đạt; không lỗi JavaScript | Edge ẩn, API giả lập; màn hình 1440 × 1000 và 390 × 844. Mở thêm 14 đường dẫn để kiểm tra không vỡ màn hình. |
| Rà bản báo cáo | Đạt | Sáu ảnh tải được, chuyển màn hình hoạt động, không tràn ngang tại 390 px; bản dự kiến mở form được. |

Lệnh backend thực tế: `mvnw.cmd -B -o -pl app -Dtest=WorkspaceProjectRealReadTest,FileVersionHistoryTest,AssignmentCandidateTest,TaskBatchServiceTest,TaskControllerRealReadTest test`, dùng Java 21. Đây là kiểm thử mock, không chạm database.

[Bằng chứng trình duyệt](ux-2026-11-18/ket-qua.json) và [tóm tắt kiểm thử](ux-2026-11-18/kiem-thu.json) được lưu cùng báo cáo.

## 7. Giới hạn và bước triển khai

- Kiểm tra trình duyệt dùng API giả lập, không chứng minh kết nối thành công với PostgreSQL, MinIO hoặc máy chủ thật. Không chạy bộ kiểm thử tích hợp cần database trong đợt này.
- Chưa kiểm thử thủ công bằng trình đọc màn hình hoặc trên thiết bị điện thoại thật. Đã kiểm tra bàn phím và kích thước điện thoại qua trình duyệt tự động.
- Không đo tải lớn. Danh sách thành viên chỉ tải khi mở; giao nhiều việc lấy danh sách tuần tự để tránh bắn đồng thời nhiều yêu cầu.
- Web mới cần backend tương ứng để đọc tên thành viên và lịch sử tệp. Nên triển khai backend trước hoặc cùng lúc với web, sau đó kiểm thử tài khoản thật theo từng vai trò trên môi trường thử nghiệm.
- Không thay đổi cấu hình production, database, dependency, không tự commit/push các thay đổi của đợt này.
