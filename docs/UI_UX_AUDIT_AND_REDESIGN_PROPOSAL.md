# SmartTask — Audit UI/UX và đề xuất thiết kế

**Phạm vi:** audit mã nguồn đọc-chỉ-đọc tại `D:\task`, không gọi hệ thống tại `192.168.1.14`, không thay đổi database, API contract, dữ liệu hay mã nguồn production.  
**Ngày audit:** 26-10-2026 (ICT).

## 1. Kết luận nhanh

Web hiện tại là React 18 + TypeScript + Vite, dùng React Router 6, Tailwind CSS 4 và CSS cục bộ. Backend là Spring Boot/Maven, REST dưới `/api/v1`, có SSE realtime. Không có thư viện component, biểu đồ, calendar hoặc drag-and-drop trong `web/package.json`.

Toàn bộ nhóm màn hình chính đã có route và phần lớn đã gọi endpoint backend thực. Vấn đề chính không phải thiếu route, mà là UI còn ở mức vận hành kỹ thuật: lộ UUID, dùng input tự do để tham chiếu đối tượng, nội dung Việt/Anh lẫn lộn, thiếu hierarchy/metadata và chưa có mô hình hiển thị trực quan cho Kanban, lịch, file version, KPI/biểu đồ.

Figma chưa được kết nối trong phiên audit này nên chưa tạo file/design library trên Figma. Đề xuất dưới đây là blueprint để duyệt trước; chỉ tạo Figma/design-to-code sau phê duyệt.

## 2. Kiến trúc và contract đã xác minh

| Phần | Đã xác minh |
|---|---|
| Web | `web/`: React 18.3, TypeScript, Vite 6, React Router 6.28; Tailwind 4 qua Vite, `styles.css`; không có bộ UI thứ ba |
| Điều hướng | `App.tsx` và `components/Layout.tsx`: `/workspaces`, `/projects`, `/tasks`, `/tasks/:id`, `/calendar`, `/files`, `/notifications`, `/reports`, `/ai`, `/admin`; login/register riêng; AI do feature flag, Admin có guard phía client và quyền vẫn do backend quyết định |
| API client | `api/client.ts`: bearer token/refresh, correlation ID, idempotency, `If-Match`, error envelope trung tính, pagination và download |
| Backend | `backend/app`: Java/Spring Boot, controller REST, permission annotation/policy, PostgreSQL/Flyway, MinIO file, SSE realtime |
| Realtime | `POST /api/v1/realtime/ticket`, `GET /api/v1/realtime/stream`; web refetch dữ liệu theo event thay vì tin payload |
| Ngôn ngữ | Có `i18n/vi.ts` và `en.ts`, nhưng nhiều text/enum/placeholder đang hard-code bằng tiếng Anh |

## 3. Màn hình hiện có: trạng thái và vấn đề UI/UX

| Màn hình | Route + endpoint đã dùng | Trạng thái | Vấn đề đã quan sát |
|---|---|---|---|
| Đăng nhập/đăng ký | `/login`, `/register`; auth API | Có UI/API | Không thuộc phạm vi shell nghiệp vụ, cần giữ nguyên flow bảo mật |
| Workspaces | `/workspaces`; `GET/POST /workspaces` | Hoạt động cơ bản | Card hiện UUID; empty/search tiếng Anh; chưa có switcher/role/member/project summary |
| Projects | `/projects`; `GET/POST /projects` | Hoạt động cơ bản | Lọc và tạo Project yêu cầu nhập `workspace_id`; card chỉ tên + UUID/status rất ít; chưa hiện tiến độ, thành viên, milestone, trạng thái |
| Tasks | `/tasks`; `GET /tasks` | Hoạt động cơ bản | Có nhóm theo state nhưng không có List/Kanban switch, không tạo task, filter project/assignee là input UUID, enum/ascending/descending chưa Việt hóa |
| Task detail | `/tasks/:id`; task, checklist, time log, comment, activity, workflow | Hoạt động một phần | Nhiều action thật; assignment/dependency vẫn thao tác bằng ID; checklist đọc/ghi nhưng checkbox chỉ readonly; subtask không load lại vì backend chưa có list endpoint |
| Calendar | `/calendar`; reminders | Hoạt động một phần | Chỉ là danh sách card theo mode, chưa phải lưới lịch trực quan; fallback hiển thị task ID nếu thiếu title; tạo/pause reminder bằng UUID |
| Files | `/files`; attachment theo task + upload/preview/download/version action | Hoạt động một phần | Bắt đầu bằng task ID, hành động file qua attachment ID; không có file explorer theo project/task, version history, metadata/preview UI hoàn chỉnh |
| Notifications | `/notifications`; preferences | Hoạt động | Có read/read-all, cursor, quiet hours, realtime; text/nhãn read/unread còn Anh, thiếu nhóm theo ngày/điều hướng đối tượng |
| Reports | `/reports`; dashboard/export | Hoạt động một phần | Có dashboard/export download nhưng không có visualization chart rõ ràng; chọn scope/project đang thiên về ID; cần trình bày KPI/trend/trạng thái metric |
| AI Assistant | `/ai`; request/job/suggestion/accept/reject/retry | Hoạt động một phần | Có job và accept/reject thật; UI chưa là hội thoại dẫn dắt, chưa tách draft preview/impact/confirm thành pattern rõ ràng |
| Administration | `/admin`; accounts/settings/audit | Hoạt động một phần | Có duyệt account, read-only settings/audit; account fallback legacy; bảng thiếu filter/pagination/action feedback đầy đủ; không suy luận role ở client |

### Phân loại đối chiếu frontend–backend

- **Đã kết nối và có cơ sở để dùng:** Workspace, Project CRUD cơ bản, Task query/detail và workflow, Calendar read/reminder, file upload/stream, notifications, report dashboard/export, AI job/draft/accept-reject, admin account/settings/audit.
- **Thiếu UI dù API nền tảng đã có:** project member/milestone/approval (`/projects/{id}/members|milestones|approval`), task assignment/deadline/block/delete/restore, session/device, task history/report export jobs.
- **Thiếu API cho UX yêu cầu hoặc DTO chưa đủ:** list subtasks; lookup/search có label cho Workspace/Project/User/Task phục vụ Select/Combobox; calendar item có `title`/project label; file browse/list theo Project/Workspace và version history detail; report series theo thời gian cho biểu đồ. Đây là đề xuất contract mới, **không được tự triển khai trong giai đoạn này**.
- **Cần kiểm thử tích hợp trước khi kết luận hoạt động trên server:** endpoint reminder preferences, đúng shape response cho calendar/files/reports, quyền từng vai trò, Figma không liên quan vận hành; không có kết quả chạy production trong audit này.

## 4. Thiết kế thông tin và UX đề xuất

### App shell chung

- Sidebar desktop 264 px: logo SmartTask, workspace switcher theo **tên**, nhóm `Công việc` (Tổng quan, Dự án, Công việc, Lịch, Tệp), `Phân tích` (Báo cáo), `Hỗ trợ` (Trợ lý AI), `Quản trị` chỉ khi server cho phép.
- Header: breadcrumb, tìm kiếm nhanh, nút “Tạo”, notification bell, trạng thái realtime kín đáo, avatar/menu phiên.
- Mobile/tablet: sidebar thành Drawer; header cố định; bảng có chế độ card/scroll ngang có nhãn.
- Mọi selector đối tượng dùng Combobox hiển thị tên + ngữ cảnh (ví dụ `Website 2026 · Marketing`), lưu ID ngầm. Chỉ hiển thị UUID trong “Sao chép mã kỹ thuật” khi thật sự cần hỗ trợ.
- Quyền: dùng response 403/404 trung tính và capability đã xác minh từ server để disable/ẩn action; frontend không tự quyết định quyền thay backend.

### Nội dung từng trang

| Trang | Bố cục đề xuất | Dữ liệu/API cần dùng |
|---|---|---|
| Workspaces | Grid card: tên, vai trò của tôi, số dự án, hoạt động gần đây; dropdown chuyển workspace | `GET /workspaces`, cần summary hoặc request tổng hợp đã duyệt |
| Projects | Toolbar: Workspace Select, search, status filter, “Dự án mới”; ProjectCard: status, progress, deadline, avatars, milestone gần nhất | `GET /projects`, members/milestones; cần DTO summary/progress nếu danh sách chưa trả |
| Tasks | Segmented `Danh sách / Kanban`; filter drawer; List DataTable; Kanban 5 cột state, card có priority/assignee/due/checklist, drag chỉ mở sau khi workflow mapping được phê duyệt | `GET /tasks`, `GET /projects/{id}/tasks`, workflow; lookup entities |
| Task detail | Header status + action, tabs `Tổng quan / Công việc con / Hoạt động / Tệp`; side panel assignee/deadline/dependency; edit drawer có optimistic concurrency | API task hiện có; list subtask và lookup cần được bổ sung/định rõ |
| Calendar | Month/week/day grid, event chip, ngày hiện tại, unscheduled rail; click mở task drawer; reminder form chọn Task theo tên | `GET /calendar`, reminders; calendar DTO cần title/project color/timezone |
| Files | Explorer filter Workspace > Project > Task, bảng tên/type/size/version/owner/updated; preview drawer; Versions tab timeline + restore confirm | Files API hiện tại task-scope; cần browse/version metadata read APIs |
| Reports | Bộ lọc scope/khoảng thời gian; KPI cards, trend chart, state/priority distribution, risk list, export center theo trạng thái | dashboard, history, export-jobs/download; cần time-series chart DTO nếu chưa có |
| AI | Chat stream giao diện 2 cột: hội thoại/history và `Bản nháp`; preview field diff, warning scope, `Chấp nhận`/`Từ chối`/`Thử lại`, confirm mutation rõ ràng | API AI hiện có; không mô tả AI tự ghi dữ liệu |
| Admin | Tabs `Tài khoản chờ duyệt / Nhật ký / Cài đặt / Jobs`; table filter/cursor, confirmation dialog và audit link | accounts/admin audit/settings/jobs; giữ server là nguồn quyền |

## 5. Design system đề xuất

### Tokens

| Nhóm | Token nền tảng |
|---|---|
| Màu | `primary #2563EB`, `primary-strong #1D4ED8`, `surface #FFFFFF`, `canvas #F8FAFC`, `text #0F172A`, `muted #64748B`, `border #E2E8F0`; semantic success `#16A34A`, warning `#D97706`, danger `#DC2626`, info `#0284C7` |
| Typography | Inter hoặc system sans; 12/14/16 body, 20 page title, 24 section, 32 display; weight 400/500/600/700; line-height 1.4–1.5 |
| Spacing | Base 4 px; scale 4, 8, 12, 16, 20, 24, 32, 40, 48 |
| Radius/shadow | 6 control, 10 card, 14 modal; shadow-sm card, shadow-md floating, shadow-lg modal |
| Focus/motion | focus ring 2 px primary + 2 px canvas; 150 ms controls, 200 ms panel; tôn trọng `prefers-reduced-motion` |
| Trạng thái | status badge semantic, không chỉ dựa màu; ưu tiên/icon/text nhất quán cho TODO, IN_PROGRESS, PENDING_APPROVAL, COMPLETED, CANCELLED, BLOCKED |

### Thư viện component

`AppShell`, `Sidebar`, `Header`, `Button`, `Input`, `Select/Combobox`, `Modal`, `Drawer`, `Card`, `DataTable`, `StatusBadge`, `TaskCard`, `ProjectCard`, `EmptyState`, `LoadingSkeleton`, `Toast`, `ConfirmDialog`.

Thành phần hiện có có thể là điểm khởi đầu: `Button`, `Card`, `TextInput`, `Select`, `Badge`, `EmptyState`, `ErrorAlert`, `Spinner`. Cần chuẩn hóa API component và bổ sung accessibility (focus trap modal/drawer, keyboard navigation combobox, live region toast, label/description/error liên kết).

## 6. States bắt buộc

- **Loading:** skeleton theo layout cuối cùng (card/table/calendar/kanban); không chỉ spinner toàn trang.
- **Empty:** nêu nguyên nhân + CTA có quyền; ví dụ “Chưa có công việc trong dự án này” + “Tạo công việc”.
- **Error:** thông điệp trung tính từ `ApiError`, Retry; giữ dữ liệu cũ khi refetch lỗi.
- **Success:** toast ngắn sau mutation; xem lại entity vừa thay đổi hoặc row optimistic có undo khi contract cho phép.
- **Concurrency/permission:** dialog reload–compare khi `409`; `403/404` không làm lộ đối tượng; reauth theo flow sẵn có.

## 7. Backend/API gap cần quyết định (không tự đổi)

1. `GET /tasks/{id}/subtasks` hoặc mở rộng Task detail DTO để hiển thị subtask sau khi reload.
2. Lookup/picker phân quyền: Workspace, Project trong workspace, thành viên Project/User, Task trong phạm vi cho phép; response phải có `id`, `label`, contextual label và cursor/search. Đây là điều kiện để loại bỏ nhập UUID mà không tải toàn bộ dữ liệu.
3. Calendar DTO: task title, project label/màu, state/priority, deadline/due semantics và timezone hiển thị.
4. File browse/read model: list theo scope được phép, metadata version/owner/size/mime/updated, lịch sử version; giữ stream ticket/private storage hiện có.
5. Report chart DTO: series timestamp/value và metric status; không tạo KPI giả hay ranking cá nhân bị cấm.
6. Project list/detail summary: progress quy ước, member preview/count, milestone gần nhất và status; chỉ hiển thị trường server cấp quyền.
7. Capability/profile endpoint (hoặc trường capabilities trong response) nếu cần frontend render action có quyền mà không suy luận role.

## 8. Danh sách file dự kiến sửa sau phê duyệt

- Shell/tokens: `web/src/styles.css`, `web/src/components/Layout.tsx`, `web/src/components/ui.tsx`, thêm `web/src/components/*` theo thư viện.
- Data hooks/types: `web/src/api/types.ts`, thêm `web/src/api/*` query hooks/lookup mỏng; giữ `client.ts` là gateway chung.
- Pages: `web/src/pages/{Workspaces,Projects,Tasks,TaskDetail,Calendar,Files,Notifications,Reports,Ai,Admin}.tsx`.
- Routing/i18n: `web/src/App.tsx`, `web/src/i18n/vi.ts` (và `en.ts` nếu vẫn duy trì song ngữ).
- Backend **chỉ nếu duyệt gap API riêng**: controller/service/DTO/test liên quan, migration chỉ khi có quyết định contract/schema được duyệt.
- Test: bổ sung test component/page/accessibility và integration contract tương ứng; không sửa dữ liệu production.

## 9. Lộ trình đề xuất

1. **Phê duyệt blueprint:** chốt navigation, tokens, scope API gaps, mapping state–Kanban và có/không dùng Figma.
2. **Design system + shell:** Figma library (khi kết nối), tokens, responsive AppShell, primitive/accessibility; không đổi nghiệp vụ.
3. **Core work:** Workspaces/Projects/Tasks/Task detail + lookup picker; cần API contract được duyệt trước khi code endpoint mới.
4. **Operations:** Calendar, Files, Notifications, Reports; ưu tiên DTO gap đã phê duyệt.
5. **AI/Admin + hardening:** draft confirmation, permission states, empty/loading/error, responsive, accessibility, regression.
6. **UAT/staging:** chỉ deploy sau review; không chạm production trước phê duyệt tách biệt.

## 10. Tiêu chí kiểm thử và nghiệm thu

- Route và role: mọi màn hình chính truy cập được/không được đúng quyền server trả về; không lộ ID/tên khi 404/403.
- Contract: request body/header (`Idempotency-Key`, `If-Match`) và envelope/error đúng; không gọi endpoint giả.
- Tasks: List/Kanban thể hiện cùng nguồn dữ liệu/filter; workflow hợp lệ; keyboard và mobile usable.
- Picker: tạo/filter/chọn đối tượng bằng tên, lưu ID nội bộ; không bắt người dùng cuối dán UUID.
- Calendar/Files/Reports/AI: render từ dữ liệu thật, có empty/error/loading/success; AI luôn preview + xác nhận trước mutation; file không lộ direct object-store URL.
- Accessibility: WCAG 2.1 AA mục tiêu cho focus, contrast, label, dialog, table responsive; kiểm thử keyboard/screen reader cơ bản.
- Regression: web unit tests, backend contract/integration tests hiện có và smoke test staging; xác minh bằng môi trường staging trước production.

