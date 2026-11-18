# Quy trình cải tiến trải nghiệm SmartTask

Ngày lập: 18/11/2026. Phạm vi: toàn bộ giao diện web hiện có; giữ nguyên dữ liệu, quy tắc nghiệp vụ và phân quyền. Không tự triển khai production.

## 1. Quy trình người dùng

1. Đăng nhập → mở trang phù hợp vai trò.
2. Chọn không gian hoặc dự án bằng tên. Khi mở từ dự án/công việc, kế thừa lựa chọn đó.
3. Tìm công việc → lọc theo dự án, trạng thái, người phụ trách. Các điều kiện ít dùng nằm trong “Lọc chi tiết”. Điều kiện đang áp dụng có nút bỏ riêng và “Xóa tất cả”.
4. Tạo công việc → nhập tên và chọn dự án; hạn hoàn thành là tùy chọn. Mô tả và ưu tiên mở trong “Thông tin thêm”.
5. Giao việc → tìm người theo tên/tên đăng nhập trong phạm vi được phép. Chuyển giao vẫn giữ xác minh phiên bản và quyền phía máy chủ. Giao nhiều việc chỉ chọn người hợp lệ cho mọi việc đã chọn.
6. Thực hiện → bắt đầu, cập nhật danh sách kiểm tra, ghi thời gian, bình luận, nộp hoàn thành. Người quản lý duyệt theo quy tắc hiện có.
7. Tệp → chọn công việc bằng tên, thao tác tại đúng tệp, chọn phiên bản để khôi phục thành phiên bản mới.
8. Lịch và nhắc việc → chọn công việc bằng tìm kiếm hoặc dùng công việc đã mở; xem rõ ngày giờ và múi giờ.
9. Khi lỗi → giữ dữ liệu đã nhập, nêu cách thử lại; khi rỗng → phân biệt chưa có dữ liệu với không có kết quả lọc.

## 2. Thiết kế thống nhất

- Giữ bảng màu và phông hiện tại; dùng xanh chính cho thao tác chính, bề mặt trung tính và khoảng cách nhất quán.
- Mỗi màn hình có tiêu đề, một câu hướng dẫn, thao tác chính và vùng nội dung.
- Bộ chọn dài có tìm kiếm, tên rõ nghĩa, thông tin phụ phân biệt tên trùng, trạng thái tải/lỗi/rỗng và tải thêm dữ liệu.
- Danh sách ngắn như trạng thái/ưu tiên dùng lựa chọn đơn giản. Không bắt nhập mã định danh nội bộ.
- Bộ lọc nâng cao thu gọn; hiển thị lựa chọn đang áp dụng ngay cả khi thu gọn.
- Toàn bộ nội dung hệ thống bằng tiếng Việt; giữ nguyên tên người dùng tự đặt, tên sản phẩm, mã nghiệp vụ gửi API, định dạng tệp và phím tắt.
- Máy tính và điện thoại dùng chung luồng; nhãn hiện rõ, điều khiển dùng bàn phím được và có trạng thái tập trung.

## 3. Thứ tự thực hiện

1. Kiểm kê màn hình, bộ chọn, API và kiểm thử hiện có; ghi lại điểm cần giữ tương thích.
2. Tạo bản xem trước giao diện bằng dữ liệu minh họa trước khi chỉnh sản phẩm.
3. Chuẩn hóa nhãn tiếng Việt và bộ chọn tìm kiếm dùng chung bằng React hiện có; không thêm thư viện.
4. Chỉnh trang công việc: bộ lọc chính/chi tiết, điều kiện đang chọn, chọn người và tạo việc.
5. Chỉnh lịch, tệp, phân quyền, nhóm, trợ lý và báo cáo để chọn theo tên và xử lý danh sách phân trang.
6. Bổ sung tên thành viên vào dữ liệu phản hồi nếu cần; giữ nguyên trường API cũ, kiểm tra quyền trước khi đọc, không trả thông tin đăng nhập nhạy cảm.
7. Rà nội dung còn tiếng Anh trên các màn hình, thông báo, trạng thái, trợ giúp và thành phần dùng chung.
8. Chạy kiểm tra kiểu, mã nguồn, kiểm thử hồi quy và bản dựng. Kiểm tra trình duyệt bằng dữ liệu giả lập, cả máy tính và điện thoại; ghi rõ phần chưa kiểm chứng với máy chủ thật.
9. Bàn giao báo cáo chi tiết: thay đổi thực tế, kết quả kiểm tra, giới hạn; kèm báo cáo giao diện và ảnh màn hình thực tế.

## 4. Điều kiện nghiệm thu

- Không nhập UUID trong các luồng chọn người/công việc/tệp thông thường.
- Không mất chức năng bộ lọc cũ; bỏ và xóa điều kiện hoạt động.
- Danh sách dài có cách tiếp cận các trang sau; không coi trang đầu là toàn bộ dữ liệu.
- Đổi ngữ cảnh không để lựa chọn cũ gây thao tác sai; phản hồi chậm không ghi đè ngữ cảnh mới.
- Không thay đổi quyền, vòng đời công việc hoặc cách khôi phục dữ liệu.
- Kiểm thử tình huống lựa chọn, lỗi tải, dữ liệu trùng tên, thay đổi ngữ cảnh và người không đủ quyền.
- Phân biệt rõ bản minh họa, kiểm thử giả lập và kết quả thực tế.
