# SmartTask MCP

MCP server chạy qua stdio bằng Node.js 22 trở lên. Chạy backend SmartTask trước, rồi cấu hình MCP client khởi chạy `node /duong-dan/task/mcp/smarttask.mjs`. Server gọi REST bằng phiên của tài khoản được cấp và backend quyết định quyền truy cập trên từng tài nguyên.

| Tool | Công dụng |
|---|---|
| `smarttask_list_projects` | Tìm dự án, lọc workspace và phân trang bằng cursor |
| `smarttask_list_tasks` | Tìm task cá nhân hoặc dự án theo từ khóa, trạng thái, ưu tiên và quá hạn |
| `smarttask_get_task` | Đọc chi tiết một task. Task cá nhân trả thêm checklist và nhãn |
| `smarttask_get_schedule` | Xem lịch ngày, tuần, tháng hoặc các mục chưa lên lịch, theo múi giờ và phạm vi |
| `smarttask_create_task` | Tạo task cá nhân hoặc dự án khi bật quyền ghi |

## Cấu hình client

| Biến môi trường của tiến trình MCP | Giá trị |
|---|---|
| `SMARTTASK_MCP_API_URL` | Mặc định `http://127.0.0.1:8081/api/v1`. Dùng URL HTTPS với máy chủ từ xa hoặc URL loopback cho local |
| `SMARTTASK_MCP_TOKEN` | `data.access_token` từ API đăng nhập SmartTask `POST /api/v1/auth/login` với JSON `username`, `password`, `client_type` là `WEB`. Cấp qua môi trường hoặc kho bí mật của client |
| `SMARTTASK_MCP_ENABLE_WRITES` | Đặt chính xác `true` để hiện tool tạo task. Mặc định chỉ đọc |

Ví dụ cho client dùng cấu hình JSON `mcpServers`. Thay đường dẫn bằng đường dẫn tuyệt đối. Dùng `D:/task/mcp/smarttask.mjs` trên Windows nếu checkout ở `D:/task`.

```json
{
  "mcpServers": {
    "smarttask": {
      "command": "node",
      "args": ["/duong-dan/task/mcp/smarttask.mjs"],
      "env": {
        "SMARTTASK_MCP_API_URL": "http://127.0.0.1:8081/api/v1"
      }
    }
  }
}
```

Client cần truyền `SMARTTASK_MCP_TOKEN` cho tiến trình con. Nếu token hết hạn, cấp token mới và khởi động lại kết nối MCP. Tránh lưu token trong repo hoặc chia sẻ cấu hình có token. SmartTask có chính sách phiên đơn, nên đăng nhập để lấy token có thể thay thế phiên hiện hành của tài khoản.

Với task cá nhân, dùng `page` từ 0. Với dự án và task dự án, dùng `next_cursor` từ kết quả làm `cursor` cho trang tiếp theo. Khi tạo task, client gửi một UUID `request_id` cho mỗi yêu cầu mới và giữ nguyên UUID cùng nội dung nếu thử lại.

Ví dụ yêu cầu sau khi kết nối client:

- “Xem lịch trình tuần này theo múi giờ Asia/Ho_Chi_Minh.”
- “Tìm các công việc dự án đang quá hạn.”
- “Tạo việc cá nhân tên Chuẩn bị lịch họp.” Cần bật quyền ghi.

Chạy kiểm thử stdio với API mô phỏng từ thư mục gốc trên Windows hoặc Ubuntu:

```sh
node --test mcp/smarttask.test.mjs
```
