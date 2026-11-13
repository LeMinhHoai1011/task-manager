# Smart Task Manager V2 — Báo cáo tổng thể

Ngày kiểm tra: 02/11/2026, múi giờ Asia/Bangkok. Repository hiện tại: D:\task.

Branch: `main`. Commit trước/sau vẫn là `0e486ae2fc8f7f052e8d4e591de454c5558fe317`; thay đổi nằm trong working tree, chưa tạo commit hoặc push.

## Kết luận nghiệm thu

Đã triển khai các luồng chính của Personal Tasks, Teams/giao việc, quyền tùy chỉnh theo Project, thông báo/nhắc hạn, Dashboard, Kanban và Calendar trên source hiện có. Backend cuối trên PostgreSQL 16.6 đã chạy toàn bộ bộ test được chọn bởi `*Test,*IT`: **478 tests, 0 failures, 0 errors, 36 skipped; BUILD SUCCESS**. Có **442 test thực chạy**; không coi 36 test skip là đã nghiệm thu. Lượt trước trên PostgreSQL18: 477/0/0/36; test thứ478 được bổ sung sau đó. Frontend **23/23 tests, 10 suites**, production build PASS. Ba script browser PASS trên PG16; workflow hai tài khoản chạy lại PASS sau bản sửa cuối.

Chưa ký nghiệm thu production toàn bộ V2: Docker Linux engine không kết nối được, MinIO thật và backup/restore object storage chưa được diễn tập; PostgreSQL 16 đã kiểm thử thành công. Các giới hạn chức năng/UX và phạm vi kiểm thử còn lại được ghi riêng bên dưới. Đây là báo cáo trạng thái thực tế, không phải tuyên bố mọi tiêu chí production đã hoàn thành.

## Những phần đã hoàn thành

| Phân hệ | Kết quả trên source hiện tại | Bằng chứng chính |
| --- | --- | --- |
| Baseline và lỗi quyền | Sửa AccountController lock và pending queue bị NPE với locked_until=null; Member được giao có thể start/submit và bình luận; Guest không được thực hiện; membership/assignment bị thu hồi không còn cấp quyền đọc | Unit + AuthFilterChainIT, RbacIT, WorkflowIT, F4RealtimeIT, ProjectMemberAccessIT |
| Personal Tasks | CRUD không Project, owner/scope, soft delete/restore, notes, checklist, labels, priority, lịch, date-only/IANA timezone, tìm kiếm/lọc/sắp xếp/phân trang/summary, completion/start, If-Match và idempotent create | PersonalTaskApiIT, PersonalTaskServiceTest, kiểm tra DB/DST, script personal-tasks-e2e.mjs và báo cáo riêng |
| Teams | Team trong Workspace, thành viên và vai trò, version/row lock, Team Manager quản lý thành viên trong chính nhóm; chỉ Workspace Owner/Admin bổ nhiệm quản lý; bộ chọn Workspace/thành viên và phân trang nhóm/thành viên | TeamApiIT 3 test, browser tạo nhóm |
| Onboarding và Project | Mời tài khoản theo email, danh sách lời mời của chính người nhận, accept có khóa và kiểm tra email/hạn/trạng thái; không tái dùng lời mời để khôi phục membership bị thu hồi; kích hoạt Project PLANNING → ACTIVE bằng If-Match, cấu hình duyệt qua UI | V2LifecycleIT và workflow E2E |
| Task Project và checklist | Form tạo task có deadline trong cùng transaction; sửa nội dung/deadline có row lock trước kiểm tra version và chặn task đã xóa; dùng idempotency_record và advisory lock chống retry/concurrent create; form sửa nội dung/deadline; checklist tick thực với version/lock và rollback UI khi bị từ chối; Member được giao thao tác checklist nhưng không sửa metadata; danh sách subtask tồn tại sau reload và phân trang | V2LifecycleIT, CustomRoleIT, TaskChecklist frontend test, workflow browser |
| Giao/chuyển giao | Primary duy nhất, collaborators, chuyển giao cùng transaction/audit/activity/outbox; liên kết Team–Task; phải chuyển giao việc primary chưa hoàn tất trước khi rời nhóm; thu hồi assignment đúng nhóm; API assign/unassign cũ có row lock, version và quyền quản lý ở service | TaskResponsibilityIT, TeamApiIT, ConcurrencyIT, hồi quy toàn bộ |
| Custom roles | Vai trò là tên và bundle grant PROJECT trong global_permission_grant hiện có. Hỗ trợ TASK_METADATA_WRITE và TASK_ASSIGNMENT_WRITE; có API đọc/thay/thu hồi và màn hình /roles. Không tự cấp quyền cho bản thân, không nâng Guest, không cấp kèm quyền duyệt hay quản lý vai trò; grant scoped không được coi là quyền GLOBAL | CustomRoleIT và GrantIT; kiểm tra role ngoài Project, sau revoke và global scope |
| Thông báo | Tái sử dụng outbox/relay/processor; assignment, chuyển giao, deadline, workflow, bình luận, nhắc hạn, Team membership và lời mời Workspace tới tài khoản đã tồn tại; persist/dedup; kiểm tra quyền tại delivery và khi mở task; chuông/unread/read/read-all; mute và quiet-hours UI | F3NotificationIT, F3OutboxRelayIT, NotificationInboxIT, TaskResponsibilityIT |
| Hộp thư | SQL scope theo actor, cursor UUID được resolve thành seq, lọc tại DB, since_seq thực, bounded page; read-all bằng bulk update DB; frontend bỏ phản hồi cũ và hiển thị thông báo tiếng Việt | 105-record integration + frontend stale-response test |
| Nhắc hạn | Worker có transaction/advisory lock/batch; ONE_SHOT chuyển FIRED cùng outbox; tự phát sắp đến hạn trong 24h/quá hạn; dedup theo task và deadline; bỏ event cũ khi đổi hạn hoặc task terminal; resume đồng thời gộp một digest qua outbox; giờ yên lặng giữ thông báo khỏi inbox/unread/realtime đến khi worker giải phóng, tăng seq để client đồng bộ lại; múi giờ IANA được lưu và queue cập nhật theo cài đặt mới | ReminderDispatchIT, DeadlineDstTest, F3NotificationIT, NotificationInboxIT, F4EndpointsIT |
| Dashboard | /api/v1/dashboard tổng hợp DB trên toàn phạm vi; TODO/IN_PROGRESS/PENDING_APPROVAL/COMPLETED/overdue/due soon/today/rate; workload, focus; scope cá nhân/dự án/nhóm/khoảng deadline và timezone | TaskOverviewIT với 105 task; browser |
| Kanban | Kéo thả và select thay thế cho thao tác bàn phím/mobile; gửi If-Match/Idempotency-Key tới workflow; dùng to_state/version thật từ backend; hoàn tác khi từ chối; lọc assignee/priority/overdue và mở detail | Frontend Kanban tests; browser PLANNING bị từ chối và rollback; WorkflowIT kiểm tra lifecycle duyệt |
| Calendar | Ngày/tuần/tháng/chưa lên lịch, ngày xem, nhãn Personal/Project, lọc Project/Team/scope, kiểm tra từng task, khoảng ngày theo IANA zone; SQL giới hạn 200/trang; refetch khi deadline/workflow đổi; picker task và reminder có tải tiếp, đặt/pause/resume nhắc riêng | TaskOverviewIT DST/revoke/Team filter/paging, CalendarReportRealReadTest, PerfIT, browser |
| Backup/restore DB | Backup custom-format từ smarttask_itg và restore sang database mới riêng; đọc lại được 148 task tại thời điểm snapshot và Flyway V16 thành công | .tools/v2-acceptance.backup và .tools/verify-restore.sql |

## API và migration

- Giữ Java 21/Spring Boot, React/Vite, opaque Bearer sessions và `/api/v1`; không đổi workflow COMPLETED hoặc thay auth bằng JWT.
- V13: personal scope/owner và project_id có điều kiện. V14: workspace_team/team_member. V15: primary assignments/partial unique index. V16: task_team. Không sửa nội dung migration V1–V12.
- Database E2E mới đã áp dụng sạch 16 migrations và Hibernate validate thành công. DB integration hiện có tiếp tục chạy trên schema V16.
- API mới/được mở rộng: `/me/tasks`, `/workspaces/{id}/teams`, `/workspaces/{id}/team-candidates`, `/teams/{id}/members`, `/teams/{id}/members/{userId}/tasks`, `/tasks/{id}/team`, `/tasks/{id}/primary-assignee`, `/tasks/{id}/assignment-candidates`, `/projects/{projectId}/members/{userId}/custom-role`, `/notifications/preferences`, `/notifications/unread-count`, `/notifications/{id}/target`, `/dashboard`.
- `/calendar`, task list DTO và notification list được mở rộng tương thích; task DTO có thêm priority/due_at, notification item có seq. Custom role dùng grant có scope, không thay CHECK role membership cũ.
- Bổ sung `/me/invitations` và `/{id}/accept`, `/projects/{id}/activate`, `/projects/{id}/settings`, GET `/tasks/{id}/subtasks`, PATCH `/tasks/{id}/checklist/{itemId}`. Task DTO có description; `/reminders` hỗ trợ page/size tối đa 100; preferences cho phép timezone tùy chọn và giữ tương thích payload cũ.
- `RequiresPermission` vẫn là metadata, không có generic aspect tự thực thi. Quyền của các luồng đã sửa được kiểm tra tường minh ở PolicyEnforcer/service/controller; không coi annotation là hàng rào bảo mật độc lập.
- Inventory mapping/annotation được xuất ở [V2-API-INVENTORY.md](V2-API-INVENTORY.md); đây là inventory hỗ trợ audit, không phải chứng nhận đã kiểm thủ công mọi tổ hợp DTO/quyền của toàn repository.

## Kiểm thử và môi trường

| Kiểm tra | Kết quả | Phạm vi/giới hạn |
| --- | --- | --- |
| Backend toàn bộ unit + integration trên PG18 | PASS: 477 total, 441 executed, 36 skipped | `.tools/v2-release-validation.log`; Maven `-Dtest=*Test,*IT` |
| Skips do Docker | 34 | 30 test tự skip khi Docker không có, 4 test/placeholder stream/auth đã disabled từ trước |
| Skips do Ollama | 2 | Live client và latency benchmark; không gọi là đã test AI live |
| Frontend | PASS: 23 tests, 10 suites; production build PASS | Offline/online ticket mới, Kanban rollback, checklist rollback/khóa workflow, inbox stale response; .tools/v2-web-test-final.log |
| Integration V2 | PASS trong bộ hồi quy đầy đủ | Custom roles, reminder dispatch, overview, Team, primary, inbox, Personal HTTP và lifecycle |
| S3 Storage/Export | PASS trong lượt toàn bộ | S3Mock 4.10.0 thực thi S3 API; không phải nghiệm thu MinIO production |
| Migration mới hoàn toàn | PASS V1–V16 | PostgreSQL 16.6 và 18.6 riêng cho E2E; không production |
| PostgreSQL 16.6 native — lượt chốt | PASS: 478 total, 442 executed, 36 skipped, 0 failures/errors | `.tools/v2-pg16-validation-final.log`; kết thúc 16:43:23 +07, V2LifecycleIT 5/5 |
| Docker build/healthcheck | NOT_RUN | Pipe dockerDesktopLinuxEngine không tồn tại |
| Backup/restore PostgreSQL | PASS cả PG18 và PG16 trên DB test | PG16 restore sang DB mới: 5 task DEMO, Flyway16 success; không bao gồm object storage |
| Browser desktop/mobile | PASS cả ba script trên PG16, gồm UI cấp/thu hồi role và sửa nội dung task | Xem phần chốt và ảnh bên dưới; không coi setup API là thao tác UI |

Các lỗi tìm thấy khi mở rộng lượt test đã được xử lý, không che bằng nới quyền production: expectation Flyway V12 cập nhật V16 + validate; fixture Member được giao task trước khi xem/comment/file; fixture PENDING được tạo rõ ràng vì self-registration hiện là ACTIVE; AccountController.pending đổi Map.of sang map cho phép giá trị null. Lượt chạy thiếu S3 ban đầu và lượt có fixture cũ thất bại được giữ trong `.tools` để đối chiếu; chỉ dùng lượt acceptance cuối làm kết quả chốt.

## Công cụ bổ sung và mục đích

- Maven 3.9.11 portable: build/test Java khi máy chưa có mvn trong PATH.
- PostgreSQL 18.6 có sẵn: cluster test riêng bind localhost:5433, các database smarttask_f21/smarttask_itg/smarttask_e2e_v2 và database restore riêng. Trust local chỉ dùng test.
- Playwright trong `.tools/browser`: chạy Edge headless, kiểm tra luồng và chụp ảnh desktop/mobile.
- S3Mock 4.10.0 executable từ Maven Central trong `.tools`: phục vụ StorageIT/F3ExportIT tại localhost:9090, phù hợp cấu hình test hiện có của repo. Không thay dependency hoặc gateway production.
- PostgreSQL 16.6 portable từ EDB: kiểm chứng đúng major version yêu cầu, cluster riêng localhost:5434; không thay cluster PG18.
- Không cài skill mới trong đợt này. `.tools` và bộ công cụ không đưa vào source Git.

## Ảnh và script nghiệm thu

Script: `web/scripts/personal-tasks-e2e.mjs`, `web/scripts/v2-acceptance-e2e.mjs`, `web/scripts/v2-workflow-e2e.mjs`. Chỉ tạo tài khoản/dữ liệu DEMO trong database E2E. Giữ dữ liệu để có thể đối chiếu; không in token/password vào log. Luồng workflow dùng API để chuẩn bị Workspace/invitation/Team/Project, rồi UI để tạo/giao task, nhận thông báo, tick/start/submit/approve và đăng nhập lại. Fixture dùng X-Forwarded-For test tách theo actor/chặng như ItgBase; đây là kiểm tra chức năng, không phải benchmark rate limit hay tải lớn. Giữ nguyên rate limiter production.

Ảnh trong `docs/screenshots/v2`: personal-tasks desktop/mobile; dashboard desktop/mobile; teams desktop/mobile; kanban desktop; calendar desktop/mobile; notifications desktop/mobile; roles desktop. Đã xem ảnh Dashboard, Teams, Calendar và form sửa task để đánh giá layout; không dùng ảnh làm bằng chứng cho workflow không thực hiện trong browser.

## Những phần chưa nghiệm thu hoặc còn giới hạn

1. **Môi trường production:** Docker/MinIO thật, volume/restart/healthcheck và diễn tập restore cả DB + object storage vẫn cần chạy khi engine hoạt động. Không tự push/deploy production.
2. **Provider ngoài ứng dụng:** Push/email gateway vẫn là stub có sẵn. Không có SMTP/FCM/APNs credentials; không tuyên bố email/push thật đã gửi. Lời mời email tới người chưa có tài khoản chưa được xác nhận qua provider.
3. **Custom roles:** Bundle hiện giới hạn ở sửa task và quản lý assignment trong Project. Không phải trình tạo mọi permission tùy ý hay thay thế toàn bộ membership roles; quyền duyệt vẫn theo designation/workflow cũ.
4. **Dữ liệu lớn:** Task list/Kanban cũ vẫn tải candidates trong phạm vi rồi lọc/phân trang trong bộ nhớ, khác với Dashboard/inbox/Calendar đã aggregate/page tại DB. Chưa có benchmark tải lớn cho đường này. Teams/Roles và bộ chọn reminder đã có tải tiếp, Dashboard/Calendar có bộ chọn Project/Team thay nhập ID.
5. **Calendar:** Các mode là danh sách theo khoảng ngày, chưa có lưới lịch tháng và kéo thả đổi hạn. Có thể sửa deadline tại Task Detail; Calendar cập nhật qua realtime. Đây là giới hạn hình thức tương tác, không phải thiếu lọc tháng/tuần/ngày.
6. **Nhắc hạn:** Có ONE_SHOT và tự nhắc 24h/quá hạn; chưa có UI cấu hình nhiều offset theo từng người. Resume đồng thời đã kiểm chứng một digest qua outbox, không còn gửi trực tiếp qua NotificationService.
7. **Nghiệm thu bổ sung:** 36 test skip, trình duyệt cho chuyển primary khi rời Team, autocomplete/@mention đích danh, phê duyệt bằng thao tác kéo chuột và kiểm tra thủ công toàn bộ trang cũ chưa được coi là hoàn thành chỉ nhờ smoke. Luồng giao/nhận/thực hiện/nộp/duyệt bằng UI hai tài khoản đã chạy; sự kiện bình luận đã tạo notification nhưng chưa có bộ soạn @mention riêng.
8. **Lời mời email chưa đăng ký:** Vẫn lưu được lời mời như API cũ, nhưng không gửi email thật khi chưa có provider. In-app accept chỉ áp dụng cho tài khoản đã tồn tại khi phát lời mời; nếu đăng ký sau đó, cần phát lại lời mời. Điều này tránh tự nhận quyền từ một email vừa được khai báo mà chưa có bước xác minh email.

Vì các mục này, **không đánh dấu mọi tiêu chí V2/production hoàn tất**. Phần backend/frontend đã triển khai và kiểm thử có thể review trên workspace; các mục còn lại được giữ rõ ràng thay vì gom vào PASS chung.

## Deploy/rollback và dữ liệu

- Chưa commit/push/deploy production. Giữ nguyên các log/dữ liệu có trước trong working tree.
- Trước production: backup DB/object storage, thử restore riêng, chạy Docker/MinIO thật, sau đó mới chấp thuận deploy.
- Migration là mở rộng. Không drop V13–V16 hoặc đặt lại project_id NOT NULL khi đã có Personal Tasks. Nếu quay ứng dụng cũ, phải ẩn tính năng mới và đánh giá tương thích schema/dữ liệu; ưu tiên roll-forward fix.
- Dịch vụ test dùng cổng 5433 (PG), 8181 (backend), 5174 (Vite), 9090/9191 (S3Mock). Không dùng cấu hình trust/secrets demo cho production.

## Chốt kết quả cuối

Đã chốt: backend PostgreSQL16.6 **478 total, 442 thực chạy, 36 skip, 0 failures/errors, BUILD SUCCESS** (16:43:23 +07). V2LifecycleIT 5/5 gồm hai lượt sửa cùng version chỉ một lượt thành công. Nội dung/deadline có row lock và chặn task đã soft-delete. Frontend23/23 và production build PASS. Ba script browser PASS; workflow chạy lại trên runtime mới nhất PASS tại `.tools/v2-workflow-browser-pg16-final.log`. Backup PG16 đã restore sang database riêng, đọc lại 5 task DEMO và Flyway16 success=true.

Danh sách file: [V2-CHANGED-FILES.md](V2-CHANGED-FILES.md). Inventory API: [V2-API-INVENTORY.md](V2-API-INVENTORY.md). Kết quả máy đọc được: [V2-VALIDATION-RESULTS.json](V2-VALIDATION-RESULTS.json).


## Lệnh kiểm chứng và chạy lại

Các lệnh dưới dùng dữ liệu test, tài khoản DB demo; không áp dụng cấu hình trust/password này cho production.

Windows (cmd, tại D:\task; PostgreSQL16 tại 5434 và S3Mock tại 9090 đã chạy):

~~~bat
set JAVA_HOME=C:\Program Files\Java\jdk-21
set SMARTTASK_IT_DATABASE_URL=jdbc:postgresql://localhost:5434/smarttask_itg
set SMARTTASK_F21_DATABASE_URL=jdbc:postgresql://localhost:5434/smarttask_f21
backend\mvnw.cmd -B -f backend/pom.xml -Dtest=*Test,*IT test
cd web
npm ci
npm test -- --run
npm run build
node scripts/personal-tasks-e2e.mjs
node scripts/v2-acceptance-e2e.mjs
node scripts/v2-workflow-e2e.mjs
~~~

Các script browser yêu cầu backend localhost:8181, frontend localhost:5174 proxy tới 8181, self-registration bật và workers bật trong database DEMO riêng. Script dùng Edge và Playwright tại .tools/browser; không chạy trên production.

Log chốt: .tools/v2-pg16-validation-final.log, .tools/v2-web-test-final.log, .tools/v2-web-build-final.log, .tools/v2-personal-browser-pg16.log, .tools/v2-browser-pg16.log, .tools/v2-workflow-browser-pg16-final.log, .tools/v2-pg16-restore.log. Các log .tools là artifact tại máy hiện tại, bị gitignore; kết quả tóm tắt được giữ trong báo cáo này.

### Build lại trên Ubuntu

Chưa chạy Ubuntu VM/container tại máy này. Đây là hướng dẫn tái lập từ Dockerfile/Compose hiện có, không phải kết quả đã chạy Ubuntu:

1. Cài Git, Docker Engine và Compose plugin; lấy đúng commit/source đã review. Nếu build ngoài Docker, cần Java21, Maven và Node20+.
2. Tại deploy, sao chép .env.example sang .env, điền domain, mật khẩu DB, MinIO root/application, CORS và các secrets riêng. Không đưa .env vào Git.
3. Kiểm tra cấu hình rồi build/chạy:

~~~sh
cd deploy
cp .env.example .env
# Điền cấu hình riêng trước khi thực hiện bước dưới.
docker compose config --quiet
docker compose build
docker compose up -d
docker compose ps
docker compose logs --tail=100 backend
~~~

Compose production dùng PostgreSQL16, backend healthcheck /actuator/health, Caddy public ports, DB/MinIO mạng nội bộ và volumes bền vững. Chỉ coi deploy PASS khi service healthy, migration V16 thành công, đăng nhập và upload/download file thật hoạt động sau restart. Docker engine hiện không hoạt động nên những bước này vẫn NOT_RUN.

### Backup và rollback

- Script deploy/backup.sh có sẵn sao lưu DB custom-format, MinIO volume và local fallback files. Chạy từ máy chủ có Docker, kiểm tra file đầu ra và restore thử trước phát hành.
- Đã thử pg_dump -Fc / pg_restore --exit-on-error trên PG16 vào database mới hoàn toàn; không ghi đè database nguồn. Restore thử được 5 task DEMO và Flyway16 success=true. Đây không phải chứng nhận restore MinIO.
- Rollback ứng dụng: giữ image/tag trước phát hành; dừng ghi, xác định tương thích V13–V16 rồi quay image. Không tự viết migration ngược xóa dữ liệu Personal/Team. Nếu không tương thích, restore snapshot DB + object storage cùng thời điểm vào môi trường riêng, kiểm tra trước khi chuyển lưu lượng.
- Không chạy restore đè production trong đợt này. Bản backup test nằm tại .tools/v2-pg16-acceptance.backup.

## Giao diện và cấu trúc source

Trang mới: MyTasks, Teams, ProjectRoles. Trang nâng cấp: Dashboard, Tasks/Kanban, Calendar, Notifications, TaskDetail, Projects và Workspaces. Component mới: NotificationBell, CreateProjectTask, ProjectTaskEditor, ScopeFilters, WorkspaceInvitations, TaskResponsibility và PersonalChecklist. Realtime giữ cơ chế ticket riêng; browser kiểm tra không đặt Bearer trong URL.

Backend mới/sửa được liệt kê đầy đủ theo file trong V2-CHANGED-FILES.md. Các nhóm chính: PersonalTaskService/CreationService/Controller; TeamService/Controller; TaskResponsibilityService/Controller; ProjectRoleController/GrantService/PolicyEnforcer; ReminderDispatcher/CalendarService; NotificationService/Processor/outbox handlers; TaskOverviewService/DashboardController; TaskCreationService và InvitationController. Không thay toàn bộ mô hình auth/workflow hoặc migration cũ.

Kiểm tra dữ liệu cũ: integration tiếp tục chạy trên database đã có dữ liệu và migrations1–16; database mới E2E được migrate từ rỗng. Không đưa ra cam kết đã audit toàn bộ dữ liệu production vì không truy cập production. Flyway hiện có cảnh báo phiên bản hỗ trợ PG16; migrate/validate thực tế PASS, nâng thư viện cần đánh giá riêng.

## Ảnh runtime nhúng trực tiếp

Ảnh thực chụp từ Edge, dữ liệu DEMO, nằm trong docs/screenshots/v2. Thao tác chuẩn bị Workspace/Project trong workflow dùng API; ảnh không hàm ý mọi bước setup được làm bằng UI.

### Dashboard trên desktop

![Dashboard trên desktop](screenshots/v2/dashboard-desktop.png)

### Danh sách Personal Tasks

![Danh sách Personal Tasks](screenshots/v2/personal-tasks-desktop.png)

### Form tạo/sửa Personal Task

![Form tạo/sửa Personal Task](screenshots/v2/personal-task-edit.png)

### Team và thành viên

![Team và thành viên](screenshots/v2/teams-members.png)

### Vai trò tùy chỉnh đã cấp

![Vai trò tùy chỉnh đã cấp](screenshots/v2/roles-assigned.png)

### Tạo task dự án

![Tạo task dự án](screenshots/v2/project-task-create.png)

### Sửa task dự án

![Sửa task dự án](screenshots/v2/project-task-edit.png)

### Chỉ định người chịu trách nhiệm

![Chỉ định người chịu trách nhiệm](screenshots/v2/assignment-desktop.png)

### Chi tiết task và checklist

![Chi tiết task và checklist](screenshots/v2/task-detail-checklist.png)

### Member nộp duyệt

![Member nộp duyệt](screenshots/v2/workflow-pending-approval.png)

### Manager phê duyệt

![Manager phê duyệt](screenshots/v2/workflow-approved.png)

### Thông báo giao việc của Member

![Thông báo giao việc của Member](screenshots/v2/member-assignment-notification.png)

### Kanban sau phê duyệt

![Kanban sau phê duyệt](screenshots/v2/kanban-after-approval.png)

### Calendar sau phê duyệt

![Calendar sau phê duyệt](screenshots/v2/calendar-after-approval.png)

### Dashboard mobile

![Dashboard mobile](screenshots/v2/dashboard-mobile.png)

### Personal Tasks mobile

![Personal Tasks mobile](screenshots/v2/personal-tasks-mobile.png)

### Teams mobile

![Teams mobile](screenshots/v2/teams-mobile.png)

### Thông báo mobile

![Thông báo mobile](screenshots/v2/notifications-mobile.png)

### Calendar mobile

![Calendar mobile](screenshots/v2/calendar-mobile.png)

Danh sách toàn bộ ảnh runtime:

- [assignment-desktop.png](screenshots/v2/assignment-desktop.png)
- [calendar-after-approval.png](screenshots/v2/calendar-after-approval.png)
- [calendar-desktop.png](screenshots/v2/calendar-desktop.png)
- [calendar-mobile.png](screenshots/v2/calendar-mobile.png)
- [dashboard-after-approval.png](screenshots/v2/dashboard-after-approval.png)
- [dashboard-desktop.png](screenshots/v2/dashboard-desktop.png)
- [dashboard-mobile.png](screenshots/v2/dashboard-mobile.png)
- [kanban-after-approval.png](screenshots/v2/kanban-after-approval.png)
- [kanban-desktop.png](screenshots/v2/kanban-desktop.png)
- [member-assignment-notification.png](screenshots/v2/member-assignment-notification.png)
- [notifications-desktop.png](screenshots/v2/notifications-desktop.png)
- [notifications-mobile.png](screenshots/v2/notifications-mobile.png)
- [personal-task-edit.png](screenshots/v2/personal-task-edit.png)
- [personal-tasks-desktop.png](screenshots/v2/personal-tasks-desktop.png)
- [personal-tasks-mobile.png](screenshots/v2/personal-tasks-mobile.png)
- [project-task-create.png](screenshots/v2/project-task-create.png)
- [project-task-edit.png](screenshots/v2/project-task-edit.png)
- [roles-assigned.png](screenshots/v2/roles-assigned.png)
- [roles-desktop.png](screenshots/v2/roles-desktop.png)
- [task-detail-checklist.png](screenshots/v2/task-detail-checklist.png)
- [teams-desktop.png](screenshots/v2/teams-desktop.png)
- [teams-members.png](screenshots/v2/teams-members.png)
- [teams-mobile.png](screenshots/v2/teams-mobile.png)
- [workflow-approved.png](screenshots/v2/workflow-approved.png)
- [workflow-pending-approval.png](screenshots/v2/workflow-pending-approval.png)


## Cập nhật giao diện sau bàn giao V2

Đợt thiết kế lại 02/11/2026 có báo cáo riêng: [V2-UI-REDESIGN-REPORT.md](V2-UI-REDESIGN-REPORT.md). Frontend hiện24/24 test PASS, production build PASS; ảnh trong screenshots/v2 đã cập nhật từ runtime mới. Kết quả backend/migration và các giới hạn production ở trên là lịch sử nghiệm thu V2, không bị thay bằng kết quả kiểm thử giao diện.
