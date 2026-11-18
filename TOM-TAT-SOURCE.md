# Tổng quan source SmartTask Manager — không bao gồm mobile

Ngày tổng hợp: **14/11/2026**. Phạm vi: source hiện có trong workspace, gồm cả thay đổi chưa commit; bỏ qua ứng dụng mobile. Nội dung được đối chiếu từ router web, controller/service backend, cấu hình và tài liệu vận hành. Đây là bản mô tả chức năng và kiến trúc, không phải kết quả chạy kiểm thử hay chứng nhận production mới.

## 1. Hệ thống dùng để làm gì?

**SmartTask Manager** là ứng dụng quản lý công việc cá nhân và cộng tác nhóm. Người dùng có thể tổ chức workspace, lập dự án, giao việc, theo dõi tiến độ, duyệt hoàn thành, trao đổi, đính kèm tệp, xem lịch và xuất báo cáo. Hệ thống có phân quyền theo phạm vi, nhật ký hoạt động và AI hỗ trợ theo cấu hình.

Các khái niệm chính:

| Khái niệm | Ý nghĩa |
|---|---|
| Tài khoản | Người dùng đăng nhập, có phiên làm việc và quyền truy cập |
| Workspace | Không gian tổ chức dự án, nhóm và thành viên |
| Team | Nhóm thành viên trong workspace, có thể liên kết với công việc |
| Project | Dự án chứa công việc, thành viên, mốc và cấu hình duyệt |
| Task/Subtask | Công việc và công việc con, có người phụ trách, hạn, trạng thái và checklist |
| Personal task | Công việc riêng của từng người, có API và luồng xử lý riêng |
| Approval | Vòng nộp và duyệt kết quả công việc dự án |

## 2. Source có những phần nào?

| Vị trí | Nội dung và vai trò |
|---|---|
| [web/](web/) | Giao diện web React/TypeScript, các trang nghiệp vụ, gọi API, xử lý phiên, phân quyền giao diện và cập nhật thời gian thực |
| [backend/](backend/) | Backend Java/Spring Boot; Maven parent và một module ứng dụng `app` |
| [infra/](infra/) | Docker Compose cho phát triển/kiểm thử; PostgreSQL, MinIO và MailHog theo profile; SQL tạo role/quyền database |
| [deploy/](deploy/) | Docker Compose production, cấu hình mẫu, khởi động, cập nhật, smoke test, backup, restore và kéo bản sao lưu |
| [scripts/](scripts/) | Chạy/dừng môi trường local, kiểm tra môi trường, chạy test, seed demo và kiểm tra advisory Maven |
| [docs/](docs/) | Đặc tả module, dữ liệu, API, RBAC, workflow, kế hoạch test, hướng dẫn local và báo cáo nghiệm thu/vận hành |
| [_recovery/](./_recovery/) | Nhóm tài liệu đặc tả và kế hoạch được lưu riêng; không phải một dịch vụ nghiệp vụ |
| [.github/workflows/](.github/workflows/) | CI kiểm tra web, backend, dependency, secret và build image |
| [README.md](README.md), [DEPLOY.md](DEPLOY.md) | Điểm vào tài liệu và hướng dẫn triển khai |
| [SMART_TASK_MANAGEMENT_FINAL_SPEC.md](SMART_TASK_MANAGEMENT_FINAL_SPEC.md) | Đặc tả tổng thể để tham chiếu yêu cầu; cần đối chiếu source khi xác định tính năng thực tế |
| [run-all.ps1](run-all.ps1), [run-smarttask.ps1](run-smarttask.ps1), [run_test.bat](run_test.bat) | Script tiện ích tại thư mục gốc để khởi chạy/kiểm thử |

Các thư mục runtime như `.run`, log, kết quả build, cache, ảnh chụp và tệp xuất báo cáo là dữ liệu hỗ trợ vận hành/kiểm chứng, không phải module sản phẩm riêng.

## 3. Công nghệ và cách các phần kết nối

| Lớp | Công nghệ trong source |
|---|---|
| Web | React 18, TypeScript 5.6, React Router 6 |
| Build/giao diện | Vite 6, Tailwind CSS 4 và CSS riêng; pnpm 9.15.4 |
| API | Java 21, Spring Boot 3.2.5, Spring MVC, Validation, Security |
| Truy cập dữ liệu | Spring Data JPA/Hibernate, PostgreSQL 16 |
| Migration | Flyway với các migration V1–V17 |
| Tệp | Lưu local hoặc MinIO qua giao diện S3 |
| Xuất báo cáo | Apache POI cho Excel, PDFBox cho PDF, CSV |
| AI | Ollama; có client HTTP và client mock phục vụ kiểm thử |
| Realtime | HTTP event stream/SSE, ticket truy cập và cơ chế kết nối lại phía web |
| Triển khai | Docker Compose, Caddy phục vụ web và reverse proxy API/HTTPS |
| Kiểm thử | JUnit/Spring Boot Test, ArchUnit, Testcontainers; Vitest/Testing Library; script trình duyệt E2E |

Luồng chính: **Trình duyệt → Caddy khi triển khai → Spring Boot API → PostgreSQL**. Backend truy cập local/MinIO cho tệp và Ollama nếu dùng AI. Các worker xử lý job và outbox ở nền.

Backend là **modular monolith**: nghiệp vụ chia theo package nhưng cùng một ứng dụng triển khai, không phải mỗi package là một microservice.

Nguồn cấu hình: [backend/pom.xml](backend/pom.xml), [backend/app/pom.xml](backend/app/pom.xml), [web/package.json](web/package.json), [application.yml](backend/app/src/main/resources/application.yml).

## 4. Các nhóm chức năng

### 4.1. Tài khoản, đăng nhập và bảo mật

- Đăng ký khi cấu hình cho phép, đăng nhập, đăng xuất, làm mới phiên.
- Đổi mật khẩu, xử lý yêu cầu buộc đổi mật khẩu, xác thực lại cho thao tác nhạy cảm.
- Quản trị tài khoản: danh sách, duyệt/từ chối, khóa/mở khóa, reset mật khẩu và tạo tài khoản theo quyền.
- Thu hồi phiên; kiểm tra phiên và trạng thái tài khoản tại backend.
- Có cơ chế browser session tùy chọn: refresh token trong cookie HttpOnly, SameSite Strict, kiểm tra Origin và CSRF; phải bật đồng bộ backend/frontend.

Source: [identity/](backend/app/src/main/java/vn/smarttask/identity/), [security/](backend/app/src/main/java/vn/smarttask/security/), [AuthContext.tsx](web/src/auth/AuthContext.tsx), [browserSession.ts](web/src/api/browserSession.ts).

### 4.2. Workspace và lời mời

- Xem/tạo workspace, xem chi tiết, cập nhật và xóa theo quyền.
- Mời thành viên, xem lời mời của chính mình và chấp nhận lời mời.
- Chuyển quyền sở hữu workspace.
- Liệt kê dự án thuộc workspace; áp dụng quyền và trạng thái của workspace xuống nghiệp vụ bên trong.

Source: [workspace/](backend/app/src/main/java/vn/smarttask/workspace/), [Workspaces.tsx](web/src/pages/Workspaces.tsx), [WorkspaceInvitations.tsx](web/src/components/WorkspaceInvitations.tsx).

### 4.3. Nhóm làm việc

- Xem và tạo team trong workspace.
- Xem, thêm, xóa thành viên; gắn vai trò thành viên nhóm.
- Liên kết task với team, xem công việc của thành viên trong nhóm.
- Kiểm tra quyền quản lý nhóm và quyền giao việc của task riêng biệt.

Source: [TeamController.java](backend/app/src/main/java/vn/smarttask/workspace/TeamController.java), [TeamService.java](backend/app/src/main/java/vn/smarttask/workspace/TeamService.java), [Teams.tsx](web/src/pages/Teams.tsx).

### 4.4. Dự án

- Danh sách, tạo và sửa dự án; xem tổng quan và chi tiết.
- Quản lý thành viên dự án và vai trò trong dự án.
- Xem cài đặt, kích hoạt dự án, bật/tắt chế độ duyệt theo điều kiện backend.
- Tạo/xem/xóa milestone; tạo, sửa, xóa nhật ký tiến độ.
- Kiểm soát thao tác theo trạng thái: đang lập kế hoạch, đang chạy, tạm dừng, hoàn thành hoặc lưu trữ.

Source: [project/](backend/app/src/main/java/vn/smarttask/project/), [Projects.tsx](web/src/pages/Projects.tsx), [ProjectDetail.tsx](web/src/pages/ProjectDetail.tsx).

### 4.5. Công việc dự án

- Tạo, xem, chỉnh sửa, xóa mềm và khôi phục task.
- Danh sách, bảng Kanban có kéo thả chuyển trạng thái và chế độ xem lịch.
- Lọc theo dự án, người thực hiện, trạng thái, ưu tiên, nhãn, bị chặn, quá hạn và cấp công việc; có sắp xếp/phân trang.
- Tạo subtask; source giới hạn cấu trúc task gốc → subtask, không mở cây nhiều cấp tùy ý.
- Giao việc và chuyển người chịu trách nhiệm chính; backend xác định danh sách ứng viên hợp lệ.
- Checklist và đánh dấu thực hiện, ghi nhận thời gian làm việc.
- Quan hệ phụ thuộc, kiểm tra vòng lặp; cờ bị chặn và cập nhật deadline.
- Xem lịch sử, bình luận, tệp và workflow trong chi tiết task.
- Version/If-Match và idempotency hỗ trợ ngăn ghi đè hoặc xử lý lặp ở các API áp dụng.

Source: [task/](backend/app/src/main/java/vn/smarttask/task/), [Tasks.tsx](web/src/pages/Tasks.tsx), [TaskDetail.tsx](web/src/pages/TaskDetail.tsx), [CreateProjectTask.tsx](web/src/components/CreateProjectTask.tsx), [TaskResponsibility.tsx](web/src/pages/TaskResponsibility.tsx).

### 4.6. Công việc cá nhân

- Danh sách và thống kê công việc riêng qua nhóm API `/api/v1/me/tasks`.
- Tạo/sửa task, bắt đầu, đánh dấu hoàn thành, xóa và khôi phục.
- Checklist và nhãn cá nhân.
- Dữ liệu gắn với chủ sở hữu, không dùng quyền quản lý dự án để sửa công việc cá nhân của người khác.

Source: [PersonalTaskController.java](backend/app/src/main/java/vn/smarttask/task/PersonalTaskController.java), [PersonalTaskService.java](backend/app/src/main/java/vn/smarttask/task/service/PersonalTaskService.java), [MyTasks.tsx](web/src/pages/MyTasks.tsx).

### 4.7. Workflow và duyệt hoàn thành

Các trạng thái task: `TODO`, `IN_PROGRESS`, `PENDING_APPROVAL`, `COMPLETED`, `CANCELLED`. `blocked` là cờ riêng, không phải trạng thái thứ sáu.

- Bắt đầu → nộp kết quả → duyệt hoặc yêu cầu sửa; có hủy và mở lại theo quyền/điều kiện.
- Khi cần duyệt, kết quả nộp chuyển sang `PENDING_APPROVAL`; nếu không cần duyệt, luồng hoàn thành tuân theo cấu hình dự án.
- Lưu vòng duyệt, người được chỉ định, quyết định và lịch sử chuyển trạng thái.
- Không tự duyệt việc mình nộp hoặc được giao; kiểm tra người duyệt hợp lệ.
- Khóa sửa nội dung thực hiện khi đang chờ duyệt/kết thúc; mở lại cần điều kiện và lý do.
- Dự án `PLANNING` cho chuẩn bị nhưng chưa cho bắt đầu/nộp việc; trạng thái chỉ đọc của dự án/workspace chặn thao tác ghi.

Source: [workflow/](backend/app/src/main/java/vn/smarttask/workflow/), [WorkflowPanel.tsx](web/src/pages/WorkflowPanel.tsx).

### 4.8. Bình luận, hoạt động và ghi chú

- Bình luận theo task; xem dòng hoạt động và lịch sử thay đổi.
- Ghi chú: danh sách, chi tiết, tạo/sửa, ghim, xóa mềm và khôi phục.
- Có phạm vi cá nhân và phạm vi chia sẻ workspace/project/task; đọc và sửa phụ thuộc chủ sở hữu/quyền phạm vi.

Source: [collab/](backend/app/src/main/java/vn/smarttask/collab/), [note/](backend/app/src/main/java/vn/smarttask/note/), [Notes.tsx](web/src/pages/Notes.tsx).

### 4.9. Tệp đính kèm

- Upload qua session: khởi tạo → gửi nội dung → hoàn tất/commit.
- Gắn tệp với task, quản lý phiên bản và khôi phục phiên bản/attachment.
- Xóa, preview và download sau khi kiểm tra quyền; nội dung được truy cập qua stream ticket có hạn.
- Có chính sách loại/kích thước tệp, ghi nhận truy cập, job quét và đối soát lưu trữ.
- Backend hỗ trợ local và S3/MinIO. Sự tồn tại của job quét không đồng nghĩa đã tích hợp một antivirus production hoàn chỉnh.

Source: [files/](backend/app/src/main/java/vn/smarttask/files/), [Files.tsx](web/src/pages/Files.tsx).

### 4.10. Lịch và nhắc hạn

- Lịch công việc theo phạm vi và thời gian; xuất lịch ICS.
- Tạo/xem quy tắc nhắc việc, tạm dừng và tiếp tục nhắc.
- Tùy chọn giờ yên lặng; xử lý deadline/múi giờ và worker phát thông báo nhắc hạn.

Source: [calendar/](backend/app/src/main/java/vn/smarttask/calendar/), [Calendar.tsx](web/src/pages/Calendar.tsx).

### 4.11. Thông báo và realtime

- Hộp thư thông báo trong ứng dụng, đếm chưa đọc, đánh dấu từng mục hoặc tất cả đã đọc.
- Mở tài nguyên đích từ thông báo sau khi backend kiểm tra quyền.
- Tùy chọn tắt tiếng theo loại; quản lý đăng ký thiết bị/subscription và khóa công khai VAPID.
- Cấp ticket và SSE để gửi tín hiệu làm mới dữ liệu; phía web có xử lý mất kết nối/kết nối lại.
- **Push ngoài trình duyệt và email chưa có provider gửi thật**: gateway/provider hiện là stub hoặc DISABLED; không nên coi đăng ký subscription là bằng chứng gửi thành công.

Source: [notify/](backend/app/src/main/java/vn/smarttask/notify/), [realtime/](backend/app/src/main/java/vn/smarttask/realtime/), [Notifications.tsx](web/src/pages/Notifications.tsx), [RealtimeContext.tsx](web/src/realtime/RealtimeContext.tsx).

### 4.12. Dashboard và báo cáo

- Dashboard tổng hợp công việc và tiến độ trong phạm vi người dùng được đọc.
- Báo cáo cá nhân hoặc dự án; thống kê tổng số, trạng thái, hoàn thành/quá hạn theo dữ liệu trả về.
- Xuất PDF/XLSX/CSV qua job nền và tải kết quả.
- Trang web còn có xuất Markdown và in/lưu PDF qua trình duyệt.
- Báo cáo, lịch sử và tệp xuất vẫn chịu kiểm tra quyền; không mặc nhiên thấy dữ liệu toàn hệ thống.

Source: [reports/](backend/app/src/main/java/vn/smarttask/reports/), [Dashboard.tsx](web/src/pages/Dashboard.tsx), [Reports.tsx](web/src/pages/Reports.tsx).

### 4.13. Phân quyền và quản trị

| Phạm vi | Vai trò/chức năng chính |
|---|---|
| Hệ thống | `SYSTEM_ADMIN` qua grant riêng; quản trị tài khoản, audit, job, chỉ số và xem thiết lập |
| Workspace | Owner/Admin quản lý phạm vi workspace; Member/Guest chịu giới hạn tương ứng |
| Project | Manager lập kế hoạch/giao việc/quản lý; Member thực hiện việc được giao; Guest xem dữ liệu được phép |
| Quyền riêng của Member | Có thể cấp `TASK_METADATA_WRITE` và `TASK_ASSIGNMENT_WRITE` theo dự án; không đồng nghĩa được duyệt hay quản trị hệ thống |
| Cá nhân | Chủ sở hữu quản lý tài nguyên riêng của mình |

- API capability `/me/access` và `/tasks/{id}/access` giúp web hiển thị thao tác theo quyền thực tế.
- Backend tiếp tục kiểm tra membership, quyền, chủ sở hữu và trạng thái trên mỗi thao tác.
- Owner/Admin workspace không tự động là quản trị hệ thống.
- Có trang giải thích vai trò/trách nhiệm; trang admin có audit, background job/retry, thiết lập và metrics/health theo API.

Source: [security/](backend/app/src/main/java/vn/smarttask/security/), [admin/](backend/app/src/main/java/vn/smarttask/admin/), [ProjectRoles.tsx](web/src/pages/ProjectRoles.tsx), [RoleGuide.tsx](web/src/pages/RoleGuide.tsx), [ROLE-INTERFACE-MATRIX.md](docs/ROLE-INTERFACE-MATRIX.md).

### 4.14. AI hỗ trợ

- Các lựa chọn hiện có trên trang AI: nháp task, gợi ý subtask, ưu tiên, deadline, người thực hiện, lập kế hoạch ngày và tổng kết tuần.
- Enum backend còn có `NATURAL_SEARCH`; chưa nằm trong danh sách lựa chọn của trang AI, không nên coi đây là chức năng web hoàn thiện.
- Gửi yêu cầu → chạy job → xem gợi ý → chấp nhận/từ chối hoặc thử lại; có lịch sử và API cấu hình model.
- Có kiểm tra context, làm sạch prompt, kiểm tra đầu ra và kiểm tra lại quyền trước khi cung cấp kết quả.
- AI không tự chuyển workflow hay tự duyệt. Đường `accept` hiện thực hóa bản nháp hợp lệ thành task qua service tạo task chuẩn và yêu cầu phạm vi project; không nên hiểu mọi loại gợi ý đã có cơ chế sửa trực tiếp trường tương ứng trên task cũ.
- Phụ thuộc cấu hình/model Ollama. Web có thể ẩn AI bằng `VITE_ENABLE_AI=false`; cấu hình triển khai hiện không tự dựng một container Ollama.

Source: [ai/](backend/app/src/main/java/vn/smarttask/ai/), [Ai.tsx](web/src/pages/Ai.tsx), [config.ts](web/src/config.ts).

### 4.15. Tiện ích giao diện web

- Layout dùng chung, bộ lọc phạm vi, toast, hộp thoại xác thực lại và chuông thông báo.
- Theme sáng/tối và bộ chuỗi tiếng Việt/Anh.
- Hộp tìm kiếm công việc/dự án/ghi chú. Tìm task/project hiện lấy một tập kết quả giới hạn để lọc, không nên coi là tìm kiếm toàn bộ dữ liệu không giới hạn.
- Manifest/service worker, thông báo cập nhật và trạng thái offline; đây không phải bằng chứng có đồng bộ CRUD offline hoàn chỉnh.

Source: [components/](web/src/components/), [i18n/](web/src/i18n/), [public/](web/public/), [main.tsx](web/src/main.tsx).

## 5. Danh sách màn hình web

Router khai báo tại [App.tsx](web/src/App.tsx).

| URL | Màn hình |
|---|---|
| `/login`, `/register` | Đăng nhập, đăng ký |
| `/dashboard` | Tổng quan |
| `/workspaces` | Workspace và lời mời |
| `/teams` | Nhóm và thành viên |
| `/projects`, `/projects/:id` | Dự án và chi tiết dự án |
| `/tasks`, `/tasks/:id` | Danh sách/Kanban/lịch công việc và chi tiết |
| `/my-tasks` | Công việc cá nhân |
| `/notes` | Ghi chú |
| `/roles`, `/my-role` | Quyền Member trong dự án và hướng dẫn vai trò |
| `/files` | Tệp |
| `/calendar` | Lịch và nhắc việc |
| `/notifications` | Thông báo |
| `/reports` | Báo cáo/xuất dữ liệu |
| `/settings`, `/change-password` | Cài đặt cá nhân và mật khẩu |
| `/ai` | AI, phụ thuộc feature flag |
| `/admin` | Quản trị hệ thống, yêu cầu grant phù hợp |

Các trang nghiệp vụ yêu cầu đăng nhập. Có route không đồng nghĩa mọi tài khoản đều được sử dụng mọi thao tác.

## 6. Backend bên trong được tổ chức ra sao?

Mã chính nằm tại [vn/smarttask/](backend/app/src/main/java/vn/smarttask/).

| Package | Trách nhiệm |
|---|---|
| `identity` | Tài khoản, mật khẩu, phiên, refresh token, reauth và recovery code |
| `workspace`, `project`, `task`, `workflow` | Tổ chức và xử lý vòng đời công việc |
| `collab`, `note` | Trao đổi và ghi chú |
| `files`, `calendar`, `notify`, `realtime` | Tệp, lịch, thông báo và tín hiệu cập nhật |
| `reports`, `ai`, `admin` | Tổng hợp/xuất dữ liệu, gợi ý AI và quản trị |
| `security` | Xác thực, policy, grant và kiểm tra quyền |
| `common` | Response/lỗi chuẩn, phân trang, UUID, correlation ID, rate limit, idempotency, version, audit và tiện ích chung |
| `jobs` | Runner, scheduler, outbox relay, retry/backoff và handler tác vụ nền |
| `config` | CORS, security và cấu hình storage |

Phần lớn module có `Controller → Service → Repository → Entity`. Dữ liệu chính gồm user/session; workspace/membership/invitation/team; project/milestone/progress log; task/assignment/dependency/checklist/time log; approval; note/comment/activity; file/version/upload; reminder/notification; report export; AI job/suggestion/context; audit/outbox/background job.

Migration tại [db/migration/](backend/app/src/main/resources/db/migration/) gồm V1–V17. Các thay đổi sau bổ sung ràng buộc/index/worker, personal task, team, người phụ trách chính, scope task theo team và nhóm tính năng V3. Có script rollback cho một số giai đoạn tại [db/rollback/](backend/app/src/main/resources/db/rollback/), không phải mọi migration đều có rollback tương ứng.

## 7. Chạy local, triển khai và kiểm thử

- Local: [scripts/dev.ps1](scripts/dev.ps1) khởi động hạ tầng và ứng dụng; có tùy chọn storage/mail, mặc định tham số backend 8081 và database 55432. Cấu hình Spring Boot cơ sở dùng port 8080; port thực tế phụ thuộc cách chạy.
- Hạ tầng dev/test: [docker-compose.dev.yml](infra/docker-compose.dev.yml), [docker-compose.test.yml](infra/docker-compose.test.yml).
- Production: [docker-compose.yml](deploy/docker-compose.yml) dựng PostgreSQL, MinIO, bước khởi tạo bucket/user, backend và web/Caddy; chỉ web công bố cổng HTTP/HTTPS ra host.
- Vận hành: có health/readiness/liveness, metrics, restart policy, volume dữ liệu, giới hạn tài nguyên, backup/restore/update/smoke script.
- Backend test: kiểm tra xác thực/phiên, RBAC và cách ly dữ liệu, workflow, concurrency, migration, storage, báo cáo, AI và worker; có integration test dùng database/container.
- Web test: component/hook, route guard, đăng nhập, quyền, checklist, Kanban, workflow, phiên trình duyệt và realtime; script trình duyệt kiểm tra demo, personal task và luồng nghiệp vụ.
- CI tại [production-validation.yml](.github/workflows/production-validation.yml) chạy typecheck/lint/test/build/audit web, test backend, kiểm tra dependency, secret và build Docker image.

Hướng dẫn chi tiết: [LOCAL_DEVELOPMENT.md](docs/LOCAL_DEVELOPMENT.md), [DEPLOY.md](DEPLOY.md), [09-BACKUP-RESTORE.md](docs/09-BACKUP-RESTORE.md).

## 8. Giới hạn và hiện trạng cần hiểu đúng

1. **Có source/API/giao diện không đồng nghĩa đã nghiệm thu production.** Báo cáo ngày 13/11/2026 kết luận **NOT READY**, còn thiếu bằng chứng về MinIO thật, restore đầy đủ dữ liệu, HTTPS/staging Ubuntu, tải đại diện và xử lý advisory backend. Đây là trạng thái ghi trong báo cáo lịch sử; bản tóm tắt này không chạy lại để xác nhận các gate đã được đóng hay chưa.
2. Push/email ngoài ứng dụng chưa có provider gửi thật; thông báo nội bộ và quản lý subscription là các phần riêng.
3. AI phụ thuộc model/runtime và feature flag; các tên chức năng gợi ý không bảo đảm có đường áp dụng chuyên biệt cho từng loại.
4. Một số comment/metadata cũ không phản ánh source mới: ví dụ mô tả trong Maven app còn ghi “No AI code” trong khi package AI đã tồn tại. Khi có khác biệt, ưu tiên đọc implementation và cấu hình hiện tại.
5. Workspace đang có các thay đổi chưa commit ở cả backend/web/deploy/test. Tài liệu này mô tả working tree lúc rà soát, không đại diện riêng một commit đã phát hành.
6. Lần tổng hợp này chỉ đọc source/cấu hình/tài liệu và kiểm tra file Markdown; không chạy lại build, test hay triển khai.

Tham chiếu trạng thái: [PRODUCTION-ACCEPTANCE-2026-11-13.md](docs/PRODUCTION-ACCEPTANCE-2026-11-13.md), [PRODUCTION-OPERATIONS-2026-11-13.md](docs/PRODUCTION-OPERATIONS-2026-11-13.md).
