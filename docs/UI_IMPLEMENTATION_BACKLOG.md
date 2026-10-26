# UI implementation backlog

| ID | Ưu tiên | Màn hình | Trạng thái | Phụ thuộc |
|---|---:|---|---|---|
| UI-001 | P0 | AppShell + tokens | Đang thực hiện | Không |
| UI-002 | P0 | Dashboard dữ liệu thật | Đang thực hiện | `GET /tasks` |
| UI-003 | P0 | Tasks List/Kanban + create | Chưa bắt đầu | Task lookup/Project lookup cho picker hoàn chỉnh |
| UI-004 | P0 | Task detail hành động thật | Chưa bắt đầu | List subtasks/checklist update xác minh API |
| UI-005 | P1 | Projects/Workspaces không UUID | Chưa bắt đầu | Lookup/summary API |
| UI-006 | P1 | Calendar trực quan | Chưa bắt đầu | Calendar metadata title/project |
| UI-007 | P1 | Files explorer/version | Đang thực hiện | Browse/version API; UI hiện chỉ xác nhận thao tác trong phiên, không thay thế lịch sử backend |
| UI-008 | P1 | Reports charts | Chưa bắt đầu | Report time-series |
| UI-009 | P1 | AI/Admin/Notifications polish | Chưa bắt đầu | API hiện có |
| UI-010 | P0 | Typecheck/build/test/accessibility | Chưa bắt đầu | Mỗi phase |
