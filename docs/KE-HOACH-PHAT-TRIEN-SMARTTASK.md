# SmartTask Manager — sơ đồ hệ thống và kế hoạch phát triển

Cập nhật ngày 27/11/2026 từ cây làm việc `D:\task` trên nền commit `1b41059`. Sơ đồ phản ánh source đang có, kể cả các thay đổi địa phương liên quan. MCP được đánh dấu là thành phần có trong cây làm việc.

## Cách đọc bộ sơ đồ

Mở [SMARTTASK.drawio](SMARTTASK.drawio) trong VS Code bằng Draw.io Integration (`hediet.vscode-drawio`). Chọn tab ở cạnh dưới. Trong sơ đồ tuần tự, đọc từ trên xuống. Mũi tên nét đứt biểu thị phản hồi hoặc xử lý bất đồng bộ, theo nhãn trên mũi tên. Đường dẫn API trong sơ đồ có thể lược bỏ tiền tố `/api/v1`.

| Trang | Nội dung | Nguồn để cập nhật |
|---|---|---|
| 01–02 | Kiến trúc, client, hạ tầng và bản đồ chức năng | [Routes web](../web/src/App.tsx), [Flutter](../mobile/lib/screens/), [Compose](../deploy/docker-compose.yml), [MCP](../mcp/README.md) |
| 03, 08 | Việc riêng, checklist, nhãn, ghi chú và khôi phục | [Task](../backend/app/src/main/java/vn/smarttask/task/), [Notes](../backend/app/src/main/java/vn/smarttask/note/) |
| 04–05, 09–10 | Workspace, nhóm, dự án, phân công và duyệt | [Workspace](../backend/app/src/main/java/vn/smarttask/workspace/), [Project](../backend/app/src/main/java/vn/smarttask/project/), [Workflow](../backend/app/src/main/java/vn/smarttask/workflow/) |
| 06–07 | Chức năng hỗ trợ, xác thực và phiên | [Identity](../backend/app/src/main/java/vn/smarttask/identity/), [Security](../backend/app/src/main/java/vn/smarttask/security/) |
| 11–12 | Bình luận, chat, realtime, lịch và nhắc | [Chat](PROJECT-CHAT.md), [Calendar](../backend/app/src/main/java/vn/smarttask/calendar/), [Notify](../backend/app/src/main/java/vn/smarttask/notify/) |
| 13–16 | Tệp, dashboard, báo cáo, AI và xử lý nền | [Files](../backend/app/src/main/java/vn/smarttask/files/), [Reports](../backend/app/src/main/java/vn/smarttask/reports/), [AI](../backend/app/src/main/java/vn/smarttask/ai/), [Jobs](../backend/app/src/main/java/vn/smarttask/jobs/) |
| 17 | Thứ tự phát triển | Kế hoạch bên dưới |
| 18 | Hôm nay và Cần xử lý | [DashboardController](../backend/app/src/main/java/vn/smarttask/reports/DashboardController.java), [TaskService](../backend/app/src/main/java/vn/smarttask/task/service/TaskService.java) |
| 19 | Lịch lặp, tạo kỳ và dừng lặp | [PersonalTaskRecurrenceService](../backend/app/src/main/java/vn/smarttask/task/service/PersonalTaskRecurrenceService.java), [V19](../backend/app/src/main/resources/db/migration/V19__personal_task_recurrence.sql) |

Sơ đồ diễn giải ở cấp module và giao dịch chính. Dùng [API contract](03-API-CONTRACT.md), [ma trận quyền](04-RBAC-MATRIX.md) và source để đối chiếu payload, quyền và các nhánh lỗi cụ thể. Khi API, trạng thái hoặc phạm vi quyền thay đổi, cập nhật tab liên quan cùng thay đổi source.

## Kế hoạch phát triển

| Mốc | Phạm vi | Nghiệm thu |
|---|---|---|
| **P0 · Đường cơ sở** | Chạy các luồng cá nhân, dự án và kiểm thử quyền trên local. Ghi nhận khác biệt React/Flutter, đối chiếu dashboard, lịch và báo cáo với dữ liệu mẫu. | Có số liệu và kết quả cho các vai trò có quyền khác nhau. Kiểm tra hạn chỉ có ngày, hạn có giờ, duyệt bật/tắt và truy cập chéo phạm vi. |
| **P1 · Dashboard Hôm nay** | Tái sử dụng Dashboard và bộ truy vấn hiện có. Lấy việc riêng của người dùng và việc dự án được giao mà họ có quyền xem. Chia **Quá hạn**, **Hôm nay**, **Sắp tới**. | Việc đã kết thúc không xuất hiện trong danh sách đang xử lý. Hạn được nhóm theo múi giờ. Mỗi dòng mở đúng chi tiết việc và nhận diện được dự án. Việc riêng của người khác không xuất hiện. |
| **P2 · Tổng quan cần xử lý** | Thêm mục trên tổng quan dự án cho việc quá hạn, bị chặn, chờ duyệt và thiếu người phụ trách chính. Mở danh sách qua bộ lọc có sẵn hoặc mở rộng khi thực sự cần. | Số đếm khớp danh sách trong cùng phạm vi quyền và bộ lọc. Bảng phân công phân biệt người phụ trách chính và người phối hợp. Yêu cầu duyệt theo đúng quyền workflow hiện có. |
| **P3 · Việc cá nhân lặp lại** | Hỗ trợ hằng ngày và hằng tuần, cấu hình múi giờ, dừng lặp và hoàn thành theo từng kỳ. Tái sử dụng luồng tạo việc và cảnh báo. Đối chiếu hành vi trên Flutter. | Chạy lại hoặc chạy đồng thời không sinh trùng kỳ. Dừng lặp giữ lịch sử. Hạn và thông báo đúng qua đổi ngày/múi giờ; từng kỳ hoàn thành độc lập. |

P1 và P2 dùng P0 làm mốc kiểm chứng chung. P3 bổ sung lịch lặp theo chính sách bên dưới.

## Chính sách đã triển khai

- **Hôm nay** lấy việc mở theo ba nhóm hạn, 20 việc mỗi trang. Việc có ngày hạn dùng nguyên ngày lưu trên công việc. Việc có giờ mà đã quá hạn trong hôm nay vào nhóm **Quá hạn**. Phân công chính và phối hợp có nhãn riêng.
- **Cần xử lý** trên tổng quan dự án dùng chung bộ lọc `attention` với danh sách. Mỗi nhóm chỉ tính việc còn mở. Dữ liệu hiển thị theo quyền đọc công việc hiện có.
- **Lịch lặp** bắt đầu từ một việc cá nhân có hạn. Trên web, mở checklist hoặc chi tiết việc rồi chọn **Quản lý lịch lặp**. Trên Flutter, dùng nút lặp ở cuối dòng công việc và đặt ngày hạn nếu cần. Mỗi kỳ sinh vào đầu ngày tại múi giờ của hạn với bản chụp tiêu đề, mô tả, ghi chú, ưu tiên và giờ hạn. Khoảng lặp là 1 hoặc 7 ngày. Việc sinh ra có trạng thái và vòng đời riêng. Dừng lặp giữ các kỳ đã sinh. Muốn đổi lịch hoặc nội dung, dừng lặp rồi bật lại từ hạn đã cập nhật và kỳ tiếp theo hợp lệ.
- Migration V19 tạo bảng lịch lặp. Worker khóa lịch đang đến hạn và lưu kỳ mới cùng lịch kế tiếp trong một giao dịch. Lịch và nhắc hạn hiện có đọc công việc được sinh theo cơ chế thông thường.

## Kết quả kiểm chứng P0 và hồi quy P1–P3

- Backend qua 54 ca kiểm thử đã chọn trên PostgreSQL 18.6 cục bộ. Các ca bao gồm xác thực, workflow, lịch, báo cáo, quyền đọc Dashboard, số đếm Cần xử lý và sinh kỳ đồng thời. Flyway đã áp dụng và xác minh V19.
- Web qua TypeScript, ESLint, build và 88 ca qua bộ test hiện có cùng ca hồi quy mới cho Dashboard Hôm nay. Flutter qua 30 ca test. Phân tích hai tệp Flutter liên quan ghi nhận ba góp ý định dạng có sẵn.
- Đã kiểm tra cấu trúc 19 trang Draw.io và các liên kết trong tài liệu này. Phần kiểm chứng còn lại là chạy các kịch bản trình duyệt với stack đầy đủ và thao tác trên thiết bị thật.

### Phần việc mở đầu cho P0–P1

1. Dùng [bài kiểm thử việc cá nhân](../web/scripts/personal-tasks-e2e.mjs), [workflow](../web/scripts/v2-workflow-e2e.mjs) và [vai trò](../web/scripts/roles-browser.mjs) làm cơ sở. Chạy trong môi trường local theo [hướng dẫn dự án](LOCAL_DEVELOPMENT.md).
2. Chốt ý nghĩa “được giao” trên Hôm nay. Việc phụ trách chính nên hiển thị rõ. Nếu có việc phối hợp, gắn nhãn riêng để người dùng hiểu trách nhiệm.
3. Mở rộng bộ đọc của [TaskOverviewService](../backend/app/src/main/java/vn/smarttask/task/service/TaskOverviewService.java) khi cần để giữ điều kiện phạm vi ở backend. Giữ Dashboard hiện tại làm điểm vào.
4. Kiểm thử một tài khoản có cả việc riêng lẫn việc dự án, tài khoản không được xem dự án, và thời điểm gần nửa đêm. Đối chiếu kết quả với lịch và quyền trên chi tiết việc.

Theo dõi tỷ lệ người mở đúng việc từ Hôm nay, số thao tác để vào danh sách cần duyệt và chênh lệch giữa số tổng hợp với danh sách chi tiết. Ghi đường cơ sở trước khi đặt mục tiêu cải thiện.
