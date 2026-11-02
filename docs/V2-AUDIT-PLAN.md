# V2 — Audit bước đầu và kế hoạch theo đợt

Ngày: 01/11/2026. Chỉ audit, chưa sửa nghiệp vụ, migration, commit, push hoặc deploy.
Phương án phân đợt đã được người dùng phê duyệt; tiếp tục 0B và sửa theo các cổng nghiệm thu, không cần xin chốt lại kế hoạch.
Đây là checkpoint đầu tiên, chưa phải nghiệm thu audit toàn hệ thống.

## Baseline thực tế

| Kiểm tra | Kết quả | Bằng chứng / giới hạn |
| --- | --- | --- |
| Frontend build | PASS | `cd web; npm.cmd run build`: TypeScript và Vite thành công |
| Frontend test | PASS | `cd web; npm.cmd test` ngoài sandbox: 4 file, 15 test passed |
| Frontend test trong sandbox | ERROR môi trường | 3 suite lỗi ENOENT file tạm; chạy lại ngoài sandbox thành công |
| Backend build/test | NOT_RUN | Không tìm thấy mvn trong PATH; chưa có Maven wrapper trong kết quả tìm kiếm |
| Docker integration/E2E | NOT_RUN | docker info không kết nối được pipe dockerDesktopLinuxEngine |
| Responsive, offline, backup/restore | NOT_RUN | Chưa chạy ứng dụng và dịch vụ phụ thuộc |

Không dùng báo cáo AUDIT-REPORT.md ngày 28/10 làm bằng chứng test cho lần này.

## Kiến trúc đã đối chiếu

- backend/pom.xml: Java 21, Spring Boot 3.2.5; backend/app là modular monolith.
- backend/app/pom.xml: JPA, Security, Validation, Flyway, PostgreSQL, MinIO; Testcontainers trong test.
- web/package.json: React 18, TypeScript, Vite; Vitest và Testing Library.
- backend/app/src/main/resources/db/migration: V1–V12; giữ nguyên migration cũ.
- deploy/docker-compose.yml: PostgreSQL 16, MinIO, backend, volume, restart policy và healthcheck. Có script backup/restore nhưng chưa diễn tập.
- web/src/App.tsx: dashboard, workspace, project, task/detail, calendar, notifications, reports, admin; AI có feature flag. Chưa có route /my-tasks.

## Ma trận phát hiện

Trạng thái PARTIAL/MISSING dựa trên kiểm tra tĩnh, chưa chứng minh hoạt động runtime.

| Phạm vi | Trạng thái | Ưu tiên | File và nhận xét | Hướng xử lý / test nghiệm thu |
| --- | --- | --- | --- | --- |
| Task cá nhân không Project | MISSING | P1 | task/entity/Task.java: project_id nullable=false; PolicyEnforcer tra Project cho Task | Thiết kế scope/owner và migration mở rộng; test chủ sở hữu, người khác, task cũ |
| Workflow thành viên | BUG (tĩnh) | P1 | workflow/WorkflowController.java start/submit gọi requireTaskWrite; security/PolicyEnforcer.java chỉ cho MANAGER/OWNER/ADMIN ghi | Tách quyền thực hiện task được giao khỏi quyền quản trị; test Member start/submit và Guest bị chặn |
| RBAC/custom roles | PARTIAL | P1 | PolicyEnforcer dùng vai trò chuỗi; GrantService có global grants | Đối chiếu toàn bộ đường kiểm tra grant, aspect và service trước khi thay đổi |
| Dashboard | PARTIAL | P1 | web/src/pages/Dashboard.tsx tính từ tối đa 100 task | API tổng hợp toàn phạm vi; test >100 task, thời gian, archived, subtask |
| Checklist/nhãn/deadline | PARTIAL | P1 | Task có priority/start/due/completed; repository checklist/tag đã có | Tái sử dụng, kiểm tra API và quyền chi tiết trước bổ sung |
| Teams độc lập | MISSING trong inventory hiện tại | P1 | Chưa thấy module Team trong danh sách source | Kiểm tra schema đầy đủ, bổ sung Team/member và chính sách chuyển giao |
| Notification/outbox | PARTIAL | P1 | notify/NotificationController.java; jobs/OutboxRelay.java, NotificationOutboxHandler.java; common/service/OutboxService.java | Mở rộng thay vì tạo outbox thứ hai; test transaction/retry/dedup/revoke |
| Notification pagination | PARTIAL | P2 | NotificationController.list gọi notifications.list(actor), lọc và phân trang trong bộ nhớ | Đẩy giới hạn/cursor xuống repository; test dữ liệu lớn |
| Kanban | PARTIAL | P2 | web/src/pages/Tasks.tsx có chế độ kanban | Chưa xác minh kéo thả; test rollback UI khi backend từ chối |
| Calendar/reminders | PARTIAL | P1 | calendar/CalendarController.java, ReminderEngine.java, ReminderPreferenceController.java | Đối chiếu timezone/date-only/dedup với đặc tả; test DST và đổi deadline |

## RBAC thực tế đã đọc

Chỉ mô tả PolicyEnforcer; quyền cuối cùng còn phụ thuộc grant/aspect/service, chưa audit hết.

| Phạm vi | Đọc | Ghi |
| --- | --- | --- |
| Workspace | Có membership | OWNER, ADMIN, MEMBER |
| Project | Có membership | OWNER, ADMIN, MANAGER |
| Task | Project writer hoặc được giao | OWNER, ADMIN, MANAGER trong Project |

Resource không đọc được trả 404; ghi bị từ chối trên resource thấy được trả 403. Có kiểm tra trạng thái DELETED và state guard cho mutation.

## API và schema: quyết định tương thích

- API đang dùng /api/v1, không đổi đồng loạt sang /api theo ví dụ trong đặc tả.
- Task CRUD: /api/v1/tasks. Workflow: /api/v1/tasks/{id}/workflow/start, submit, approve, reject, cancel, reopen.
- Notifications: GET /api/v1/notifications, POST /{id}/read, POST /read-all. Calendar: /api/v1/calendar và /reminders.
- Giữ If-Match/version và Idempotency-Key hiện có; không tự chuyển tên trạng thái COMPLETED sang DONE trên API cũ.
- Task đã có description, priority, start_at, due_at, due_timezone, due_is_date_only, completed_at, deleted_at; không thêm cột tương đương lần nữa.
- Cần thiết kế owner/scope và nới project_id có điều kiện; chưa chốt SQL trước khi audit FK/constraint và dữ liệu cũ.
- Chưa chốt bảng Team, preference hay reminder mới vì có module tương đương cần đối chiếu đầy đủ.

## Các đợt và cổng kiểm tra

0A (đã làm): inventory ban đầu, baseline frontend, phát hiện rủi ro, ghi báo cáo này.

0B (chưa làm): inventory đầy đủ HTTP/DTO/validation/quyền; đọc authentication/session/logout/refresh; schema/entity/FK/index; toàn bộ policy/aspect/custom roles. Chuẩn bị Maven, chạy backend unit/integration, kiểm tra Docker và diễn tập migration trên DB riêng. Chỉ kết thúc audit khi có bằng chứng hoặc NOT_RUN rõ ràng từng mục.

0C: sửa lỗi ưu tiên đã xác nhận, trước hết workflow Member; test allow/deny ở controller và service, regression API cũ. Chỉ nghiệm thu khi không mở quyền quản trị cho Member/Guest.

1A: scope/owner và CRUD cá nhân không Project, test truy cập chéo. 1B: giao diện /my-tasks, hoàn thành/bỏ hoàn thành, notes/checklist/labels và lọc/phân trang/thống kê. Kiểm tra lại sau mỗi nhánh nhỏ.

2A: Team/member và chuyển giao có transaction. 2B: primary assignee/collaborator, workflow/review/activity; RBAC và concurrency test.

3A: tái sử dụng notification/outbox, persistence/retry/dedup. 3B: realtime tương thích JWT hiện tại, inbox/unread/preferences. 3C: reminder theo UTC, quiet hours, offline/reconnect/revoke test. Không đưa token vào URL.

4A: dashboard aggregation có scope. 4B: Kanban mutation có workflow và hoàn tác. 4C: Calendar tháng/tuần/ngày, timezone/date-only, đồng bộ nhắc hạn và E2E.

Mỗi đợt ghi file/API/migration thay đổi, build/test thực tế, lỗi còn lại, hướng dẫn kiểm thử thủ công, deploy/rollback nếu có. Không chuyển đợt khi tiêu chí nghiệm thu chưa đạt.

## Tiến độ bàn giao lần này

### 02/11/2026 — Hộp thư phân trang và bộ lọc tại database

- Đã đối chiếu bản báo cáo người dùng gửi lại; đây là checkpoint tiếp nối, không phải yêu cầu quay về trạng thái chỉ audit.
- NotificationRepository dùng JpaSpecificationExecutor; NotificationService.inbox truy vấn theo owner, redacted=false, bộ lọc và seq tại database. Mỗi truy vấn lấy tối đa limit+1 (limit tối đa 100), không tải toàn bộ hộp thư. Total được COUNT theo scope/filter, không theo số dòng trang hiện tại.
- Giữ cursor UUID tương thích API cũ; resolve cursor chỉ trong hộp thư của actor, sau đó phân trang seq giảm dần. Tin mới đến giữa các trang không làm trùng hoặc dịch chuyển trang cũ. Cursor sai định dạng trả 400, cursor ngoài quyền hoặc đã redacted trả 404.
- since_seq nay so với cột sequence thực, thay cho lỗi so sánh epoch-second. Mỗi item có thêm seq để client lấy mốc đồng bộ; giá trị âm trả 400.
- GET /api/v1/notifications nhận filter=all|unread|task|reminder|system; filter lạ trả 400. Quy tắc phân loại kind giữ tương thích bộ lọc giao diện cũ. Frontend gửi filter tới API, bỏ lọc trên riêng trang đã tải, chặn phản hồi request cũ ghi đè khi đổi filter/realtime refresh. Badge ghi rõ số chưa đọc trong danh sách đang tải; global unread-count vẫn do NotificationBell đảm nhiệm.
- NotificationInboxIT: 105 bản ghi, actor isolation/redaction, toàn bộ trang không trùng sau khi chèn tin mới, since_seq, cursor ngoài quyền/sai định dạng, lọc server-side và read-state. Nhóm NotificationInboxIT + TaskResponsibilityIT + NotificationCenterTest: **9/9 PASS**, BUILD SUCCESS. Test target sau khi thu hồi Project membership trong TaskResponsibilityIT đã được compile/chạy lại và PASS, đóng điểm chưa xác nhận ở checkpoint trước.
- Frontend: **19/19 PASS (7 suites)**, production build PASS; thêm test đổi bộ lọc khi phản hồi cũ đến trễ. git diff --check PASS (chỉ cảnh báo chuẩn hóa LF/CRLF). Không migration mới, không cài thêm kỹ năng/công cụ trong đợt này.
- Lượt integration đầu lỗi môi trường connection refused do cluster test đang tắt; đã khởi động lại cluster riêng cổng 5433 rồi chạy lại thành công. Database test là PostgreSQL 18, chưa phải nghiệm thu PG16/Docker.
- Chưa hoàn tất toàn bộ V2: custom roles/Team manager delegation, concurrency assign cũ, Team UI picker/paging và E2E, outbox retry/offline E2E và reminder policy, Dashboard aggregation, Kanban/Calendar và deployment/backup-restore còn mở. Hộp thư mới chưa có browser E2E/screenshot. Mark-all-read hiện vẫn duyệt dữ liệu trong bộ nhớ; tối ưu này chưa nằm trong kết quả phân trang đã nghiệm thu.

### Bổ sung checkpoint 0B

### Đợt build và sửa đầu tiên sau khi được ủy quyền tự xử lý

### Checkpoint triển khai theo yêu cầu FULL IMPLEMENTATION

### Checkpoint baseline tiếp theo

### Personal Tasks — lát cắt đầu tiên

### Personal Tasks — sửa chi tiết và thùng rác

### Personal Tasks — checklist, nhãn và summary

### Personal Tasks — trạng thái và bộ lọc

### Personal Tasks — HTTP và retry đồng thời

### Personal Tasks — kiểm thử vòng đời đầy đủ

### Personal Tasks — version header và thứ tự ưu tiên

### Bàn giao Personal Tasks

### Teams — cấu trúc và quản lý thành viên ban đầu

### Assignment — người chịu trách nhiệm chính và chuyển giao

### Chốt chuyển giao đồng thời và bắt đầu Notifications

### Xử lý phần còn vướng — Team–Task, rời nhóm và đích thông báo

Kết quả hồi quy cuối đợt: backend default BUILD SUCCESS, không failures/errors, 2 skipped AI. Nhóm integration/security đã chạy 11/11 PASS; frontend 18/18 và production build PASS. Migration V16 đã áp dụng trên database test PostgreSQL 18; chưa áp dụng production/PG16. API deep-link kiểm tra lại quyền được bổ sung; test thu hồi quyền đích đã thêm trong TaskResponsibilityIT, cần đối chiếu lượt compile/test tiếp theo nếu được thêm sau testCompile của lượt nhóm. Không tuyên bố E2E Teams hoặc toàn bộ Notifications đã PASS.

- V16__team_task_scope.sql thêm task_team, một team cho task, không thay đổi Project membership. Link yêu cầu cả Workspace management và Project task management, cùng Workspace, If-Match, task không bị khóa; assignee ACTIVE hiện có phải là thành viên Team.
- TeamService kiểm tra unfinished primary trước remove hoặc downgrade Guest. Người dùng chuyển giao qua API hiện có rồi mới remove; khi remove thu hồi assignment trên các task gắn đúng Team, clear primary, tăng Task version và audit. Project role và assignment ngoài Team không bị đổi.
- PUT /tasks/{id}/team và GET /teams/{id}/members/{userId}/tasks bổ sung luồng liên kết/kiểm tra việc cần chuyển giao. Danh sách task phải qua task read policy; không trả title ngoài quyền. Giao diện Teams có form link và preview; còn dùng UUID/version thủ công.
- Transfer/candidate API lọc thêm Team executor; API assign cũ cũng chặn target không thuộc Team đã liên kết. PolicyEnforcer không coi assignment REMOVED là quyền đọc còn hiệu lực.
- Notification target GET /notifications/{id}/target kiểm tra owner/redaction, deep-link task dạng whitelist, Project membership và task read tại thời điểm nhấn. Notifications UI chỉ điều hướng sau kiểm tra này. Không nhận URL tùy ý từ client.
- Test TeamApiIT primaryMustTransferBeforeLeavingTeamWithoutChangingProjectRole chứng minh remove trước transfer bị chặn, transfer/remove thành công, role Project vẫn MEMBER và assignment bị REMOVED, task read trả 404.
- Một test thông báo ban đầu kỳ vọng chỉ 1 unread nhưng scheduler hợp lệ có thể phát cả hai sự kiện giao và chuyển giao. Test đã tách điều kiện dedup theo từng event/recipient khỏi tổng unread (1–2 theo worker xử lý), không bỏ kiểm tra dedup.
- Lượt nhóm cuối TeamApiIT + TaskResponsibilityIT + Policy404vs403Test + TaskHappyPathTest: 11/11 PASS. Frontend 18/18 và production build PASS. Hồi quy backend default đang chạy lại sau các thay đổi bảo mật cuối.
- Chưa hoàn tất toàn bộ đặc tả: custom-role enforcement/Team manager delegation, UI picker/paging hoàn chỉnh, Team E2E/screenshots, nhắc hạn đầy đủ, inbox/filter server-side, Dashboard/Kanban/Calendar V2 và deployment PG16/Docker. Không đánh dấu những phần này PASS.

- GET /tasks/{id}/primary-assignee hiển thị primary/version; GET /tasks/{id}/assignment-candidates chỉ trả executor/account ACTIVE cho người có quyền quản lý. TaskDetail dùng picker backend thay UUID thủ công; Member không nhận candidate list quản lý.
- Test concurrency thực đã phát hiện hai request cùng version đều 200: policy đọc Task trước row lock giữ entity stale trong persistence context. Đã sửa transfer lấy row lock trước policy; test chạy lại cho đúng một 200 và một 409, chỉ một primary ACTIVE.
- TaskAssignmentNotificationHandler xử lý task.assigned/task.reassigned qua OutboxRelay hiện có, gọi NotificationProcessor để persist/dedup/preferences/quiet-hour logic; không tạo hệ thống outbox thứ hai. Check active Project membership và task read tại delivery; gửi tín hiệu realtime sau commit. Người cũ còn collaborator được thông báo, người mất membership bị bỏ qua.
- NotificationController bổ sung GET /notifications/unread-count actor-scoped; count tại DB, bỏ redacted/read. NotificationBell trên Topbar tự refetch khi notification event; mark-all-read phát signal đồng bộ thiết bị.
- TaskResponsibilityIT mở rộng kiểm tra candidates/current, chạy lại handler hai lần chỉ có một notification/dedup key/recipient; kiểm tra unread count qua session người nhận.
- Kết quả: sau sửa race, TaskResponsibilityIT 2/2 PASS; nhóm cuối TaskResponsibilityIT + NotificationCenterTest 8/8 PASS. Backend default trước unread-count cuối BUILD SUCCESS, 0 failures/errors; chưa tuyên bố full suite sau thay đổi unread đã chạy. Frontend 18/18 PASS và build PASS.
- Không migration mới trong lát cắt này. Thay đổi dùng schema V15.
- Còn mở Teams/Assignment: Team–Task association, chuyển giao khi rời Team, Team manager delegation và custom roles, UI paging/picker Workspace, E2E/screenshot Teams. Không đánh dấu toàn bộ đợt 2 complete.
- Notifications đang PARTIAL: đã có assignment persistence/dedup/unread signal; còn worker relay end-to-end/offline/revoke/retry test, inbox chi tiết/link an toàn, preference UI, reminder policy và screenshot/E2E. Dashboard/Kanban/Calendar nâng cấp V2 chưa nghiệm thu.

- V15__primary_assignments.sql bổ sung is_primary trên task_assignment, backfill một assignee ACTIVE đủ quyền Project/account theo assigned_at/id; giữ các assignee khác. Partial unique index bảo đảm không có hai primary ACTIVE cho một task. Không tự tạo primary nếu task chưa có người đủ điều kiện.
- TaskResponsibilityService/Controller: POST /api/v1/tasks/{id}/primary-assignee, body userId và If-Match. PolicyEnforcer yêu cầu Project manager, lock Task row và kiểm tra version, chặn PENDING_APPROVAL/COMPLETED/CANCELLED; target phải là executor Project ACTIVE và account ACTIVE. Giữ người cũ làm collaborator, tăng version.
- Audit + ActivityService timeline + OutboxService ghi cùng transaction. Tái sử dụng GET /tasks/{id}/activity hiện có, không tạo endpoint trùng. Event task.reassigned mới đã emit; delivery notification cho event này chưa nghiệm thu.
- TaskDetail thêm form chuyển giao bằng User ID. Chưa có picker/candidate API hoặc hiển thị primary hiện tại; backend vẫn là nguồn kiểm tra quyền.
- API unassign cũ chặn PRIMARY_TRANSFER_REQUIRED khi gỡ primary mà còn assignee khác; reset flag khi remove/reactivate để không tái kích hoạt primary cũ ngoài ý muốn. Endpoint assign/unassign cũ chưa được tái cấu trúc toàn diện cho concurrency.
- Nhóm F21IntegrityTest 26 + TaskResponsibilityIT 1: 27/27 PASS, migration V15 áp dụng trên DB test/integration V14. Sau sửa legacy assignment: TaskResponsibilityIT + ProjectMemberAccessIT + TaskHappyPathTest 9/9 PASS. Frontend 17/17 và production build PASS.
- Test integration: manager chuyển giao được, Member đã được giao không được chuyển (403), stale version (409), primary unique sau chuyển, người cũ vẫn collaborator, timeline có lịch sử.
- Còn mở: concurrency transfer hai request, integration unassign primary, current-primary/candidate UI, Team/task liên kết và chuyển giao khi rời nhóm, custom roles, notification delivery và E2E/screenshots. Chưa nghiệm thu toàn bộ Assignment/Teams.

- V14__workspace_teams.sql thêm workspace_team và team_member, FK Workspace/User, unique tên không phân biệt hoa thường theo Workspace, PK team/user, CHECK role/status và index scope/user. Không đổi dữ liệu Task/Project cũ.
- TeamService dùng JdbcTemplate cho dữ liệu Team và repository membership/user hiện có; DTO records riêng; transaction + team row lock + version cho membership mutation. Chưa tạo hệ thống auth độc lập.
- PolicyEnforcer.requireWorkspaceManagement tập trung quyền OWNER/ADMIN và state guard Workspace. MEMBER/GUEST không tự tạo/quản lý nhóm. Role Team MANAGER hiện là dữ liệu, chưa cấp quyền quản trị riêng ngoài Workspace Owner/Admin.
- API /api/v1/workspaces/{id}/teams GET/POST; /api/v1/teams/{id}/members GET/POST; DELETE /members/{userId}. List hỗ trợ page/size giới hạn 100; membership mutation If-Match.
- Chỉ thêm user ACTIVE có Workspace membership ACTIVE; Workspace GUEST/VIEWER không nâng thành Team MEMBER/MANAGER. Remove là status REMOVED và không thay quyền Project.
- /teams có form chọn Workspace ID, tạo nhóm, xem/thêm/đổi role/xóa thành viên. Hiện dùng UUID thủ công và tối đa 100 mục; chưa có picker thân thiện/pager UI.
- F21IntegrityTest 26 + TeamApiIT 1 = 27/27 PASS; V14 áp dụng trên DB test V13 và DB integration V13. Test chứng minh Member create 403, outsider list 404, owner create/add/remove, stale version 409. Frontend 17/17 PASS và build PASS trước thay đổi query size=100 nhỏ cuối.
- Còn thiếu đợt 2: team manager delegation/custom roles, chuyển giao team/member, liên kết task/team, primary assignee/collaborators, chuyển giao công việc khi rời nhóm, concurrency rộng, UI/E2E/screenshot Teams. Chưa nghiệm thu Teams đầy đủ.

Đã bổ sung DTO TaskView, date-only theo IANA timezone và test DST; chạy E2E Edge thật, chụp desktop/mobile và sửa sidebar responsive sau xem ảnh. Kết quả cuối: backend default 326 tests, 0 failures/errors, 2 skipped; integration/service/version 10/10; frontend 17/17 và build PASS; browser lifecycle PASS. Báo cáo phạm vi, giới hạn môi trường và ảnh tại docs/PERSONAL-TASKS-ACCEPTANCE.md. Không coi bàn giao module này là toàn bộ V2 hoàn tất.

- TaskVersion chuyển đổi If-Match cho toàn bộ mutation cá nhân: số nguyên dương, quoted entity tag và W/quoted theo khả năng tương thích API cũ. Test từ chối wildcard/âm/0/danh sách/chuỗi không hợp lệ. Thiếu header hiện do Spring xử lý; chưa chuẩn hóa 428 cho mọi endpoint.
- Integration lifecycle đã đổi completion sang If-Match quoted để xác minh Spring chuyển đổi thực tế. Nhóm F21IntegrityTest (25), PersonalTaskApiIT (3), TaskVersionTest (1): 29/29 PASS.
- SQL sort priority dùng CASE URGENT/HIGH/MEDIUM/LOW và id làm tie-breaker, không dùng alphabet. Test SQL riêng với page size 2 xác nhận hai trang lần lượt URGENT/HIGH và MEDIUM/LOW: 1/1 PASS.
- MyTasks thêm nút ưu tiên cao trước; remount checklist theo version Task để nhận bản mới sau mutation; checklist có nút tải lại khi xung đột. Chưa test UI interaction riêng cho remount này.
- Frontend 17/17 PASS trước nút sort nhỏ cuối cùng; build sau toàn bộ thay đổi PASS. Không migration mới, chưa chạy lại default backend toàn bộ trong lát cắt này.
- Còn mở Personal Tasks: DTO ổn định, date-only/DST, HTTP/ETag response header chuẩn hóa, E2E/screenshot và PG16. Chưa nghiệm thu toàn bộ V2; Teams và các phân hệ sau vẫn còn việc.

- Mở rộng PersonalTaskApiIT: HTTP checklist add/tick, actor khác không tick được; Task vẫn TODO sau checklist; PUT labels loại trùng; edit title/notes/priority; DELETE ẩn detail và loại khỏi summary; người khác không restore; owner restore giữ notes và tìm lại bằng tag/q. 3/3 integration tests PASS (gồm lifecycle/idempotency/concurrency đã có).
- MyTasks: sequence guard cho list và summary, bỏ qua phản hồi cũ và cleanup khi đổi filter/unmount; summary thành công không xóa lỗi thao tác khác.
- Test frontend điều khiển response cũ trả sau response lọc mới, chờ Promise/React update thực sự rồi xác nhận dữ liệu mới vẫn hiển thị. Frontend 17/17 PASS, production build PASS.
- Chạy backend mặc định `mvn -B -f backend/pom.xml test`: BUILD SUCCESS, 0 failures/errors; hai test AI live/benchmark vẫn skipped. IT được chạy riêng như trên, không gộp mập mờ vào default suite.
- Còn mở trước nghiệm thu toàn bộ V2: date-only/DST cá nhân, DTO/version header chuẩn hóa, priority ordering, UI checklist stale version khi thay task ở nơi khác, E2E/screenshots/PG16; Teams, notification, dashboard/view các đợt sau chưa triển khai.

- PersonalTaskCreationService tái sử dụng idempotency_record, key namespace theo actor và endpoint; PostgreSQL advisory transaction lock serialize retry; task và record cùng transaction. Cùng key khác title trả 409. POST /me/tasks bắt buộc Idempotency-Key (1–200 ký tự).
- Frontend giữ pending key khi request thất bại và gửi lại cùng title, bỏ key khi thành công hoặc nội dung thay đổi. Key hiện chỉ giữ trong vòng đời component, không lưu qua reload trình duyệt.
- Replay trả cùng Task ID và entity hiện tại, chưa phải byte-for-byte snapshot response ban đầu; không nên suy rộng thành cơ chế replay chung cho mọi API cũ.
- Tạo smarttask_itg riêng trên PostgreSQL test; full Spring context + MockMvc + session opaque thật. PersonalTaskApiIT 2/2 PASS: thiếu token 401, chủ sở hữu CRUD completion, actor khác detail 404, stale version 409, key khác payload 409, list đúng tổng, hai thread cùng key chỉ tạo một task.
- Frontend build PASS và 16/16 tests PASS. Không migration mới; V1–V13 đã áp dụng trên database integration sạch khi khởi động.
- Chưa nghiệm thu toàn bộ: HTTP checklist/labels/edit/delete/restore và concurrency chuyển trạng thái; response DTO; request race UI; date-only/DST; E2E/screenshot/PG16. Teams, notifications và dashboard/view toàn hệ thống còn mở.

- POST /api/v1/me/tasks/{id}/start: chỉ TODO → IN_PROGRESS, kiểm tra owner/version, row lock, tăng version và audit; không áp dụng vào workflow Project.
- GET /me/tasks bổ sung tag (EXISTS subquery), overdue (deadline trước hiện tại, state TODO/IN_PROGRESS), cùng priority/state/search/page đã có. MyTasks có điều khiển priority/tag/overdue và nút bắt đầu.
- MyTasks tự gọi summary sau khi tải danh sách, bao gồm sau mutation; vẫn có nút refresh thủ công. Thống kê toàn phạm vi cá nhân, không theo bộ lọc danh sách.
- Test database mới với 105 task của owner và task của người khác: tổng/overdue đúng 105, trang 20 vẫn báo tổng 105, filter tag đúng một task và không lộ cho owner khác.
- Test start chứng minh owner khác 404, version cũ 409, TODO chuyển đúng và không được bắt đầu lại IN_PROGRESS.
- Kết quả thực tế: F21IntegrityTest 25 + PersonalTaskServiceTest 5 = 30/30 PASS trên PG18; frontend production build PASS; frontend 16/16 PASS.
- Chưa hoàn tất: HTTP authentication/API integration cá nhân, idempotency create, DTO ổn định, date-only/DST, ưu tiên sort đúng thứ tự nghiệp vụ, race request UI, E2E/screenshot. Teams và các module tiếp theo chưa triển khai. Không coi test nhóm là toàn bộ hệ thống đã nghiệm thu.

- Tái sử dụng checklist_item và task_tag, không tạo migration mới. PersonalTaskService bổ sung detail/checklist/labels, mỗi mutation owner-scoped dưới row lock, If-Match/version, audit; giới hạn title checklist 200 ký tự, tối đa 20 nhãn (50 ký tự), trim và dedup nhãn.
- GET /api/v1/me/tasks/{id}; POST /{id}/checklist; PATCH /{id}/checklist/{itemId}; PUT /{id}/labels; GET /summary. Đều dưới /api/v1/me/tasks. Checklist item phải thuộc đúng task, completion checklist không thay state Task.
- Summary GROUP BY state và COUNT overdue tại database theo scope PERSONAL + owner + chưa xóa; không tính từ trang 20/100 task.
- Giao diện PersonalChecklist thêm/tick mục và thay nhãn, hiển thị tiến độ. MyTasks có nút cập nhật thống kê từ API; thống kê hiện cập nhật theo yêu cầu, chưa tự làm mới sau mọi mutation.
- Nhóm F21IntegrityTest + PersonalTaskServiceTest trước test checklist mới: 27/27 PASS; chạy lại PersonalTaskServiceTest với test checklist chéo: 4/4 PASS. Frontend suite 16/16 PASS trước bổ sung nút thống kê; production build tiếp tục kiểm tra sau bổ sung.
- Còn mở: HTTP integration checklist/label/summary, test SQL tổng hợp dữ liệu >100 task, frontend interaction checklist và nhãn, tự refresh summary, IN_PROGRESS mutation, filter tag/overdue, date-only/DST, idempotency create, DTO ổn định, screenshot/E2E. Chưa nghiệm thu toàn bộ Personal Tasks và chưa chuyển sang triển khai Teams.

- PersonalTaskService/Controller: PATCH /me/tasks/{id} cập nhật tiêu đề, mô tả, notes, priority, startAt/dueAt; DELETE /me/tasks/{id} xóa mềm; POST /me/tasks/{id}/restore khôi phục. Các mutation kiểm tra owner, scope và If-Match dưới row lock, tăng version và ghi audit.
- GET /me/tasks bổ sung q (title/description), state, priority, deleted, sort allowlist, giới hạn page size tại database qua JpaSpecificationExecutor. Tìm kiếm escape ký tự wildcard. Không scan toàn bộ task trong bộ nhớ.
- MyTasks có form sửa chi tiết, tìm kiếm, lọc trạng thái, sort deadline/title, thùng rác và khôi phục; datetime-local chuyển UTC khi gửi, hiển thị lại theo giờ máy người dùng. Date-only/DST edge-case chưa nghiệm thu.
- Test backend mới editsAndTrashRespectOwnerAndVersion: owner khác bị 404, stale version 409, xóa mềm chặn completion, khôi phục kiểm tra owner và tăng version. PersonalTaskServiceTest 3/3 PASS.
- F21IntegrityTest 24/24 PASS trong lượt nhóm trước; frontend production build PASS; frontend 5 suites, 16/16 tests PASS (có test MyTasks sửa notes gửi PATCH/If-Match đúng).
- Không thêm migration trong lát cắt này. Chưa chạy toàn bộ backend sau thay đổi; chưa test HTTP thực tế search/edit/restore; chưa có screenshot/E2E.
- Còn thiếu Personal Tasks: checklist/tag UI+API owner scoped, summary, chuyển IN_PROGRESS, filter quá hạn và priority UI, idempotency create và response DTO ổn định; chống request stale ở UI. Các module Teams/notification/dashboard theo kế hoạch vẫn mở.

- V13__personal_tasks.sql: scope PROJECT mặc định cho task cũ, owner_user_id, notes; project_id nullable với CHECK phân biệt PROJECT/PERSONAL; index theo owner. Giữ FK/link project cũ, không chuyển task cũ sang cá nhân.
- Task.personal factory và PersonalTaskService/Controller mới: GET/POST /api/v1/me/tasks; PATCH /api/v1/me/tasks/{id}/completion. List phân trang tối đa 100, create kiểm tra title, completion lock row + If-Match/version, owner check trả 404 khi ngoài phạm vi, audit completion khi tick/bỏ tick.
- Trang /my-tasks và sidebar: tạo nhanh, checkbox, phân trang, trạng thái lỗi/loading; không cần Project.
- Frontend production build PASS. Migration V13 nâng từ DB test V12 thành công; F21IntegrityTest + PersonalTaskServiceTest: 26 tests, 0 failures/errors/skips, BUILD SUCCESS trên PG18. Test DB mới chứng minh task PERSONAL có owner không cần project và không chấp nhận thiếu owner.
- PolicyEnforcer.taskScope bổ sung guard projectId null để API team cũ không truy vấn Project với null; thay đổi nhỏ này được thêm sau lượt test 26 và cần đưa vào lượt regression tiếp theo.
- Chưa nghiệm thu Personal Tasks đầy đủ: còn edit/detail/delete/restore, notes UI, checklist/tag, lọc/search/sort/summary, idempotency create, DTO response ổn định, HTTP integration và UI interaction/E2E. Chưa có screenshot. Không coi trang hiện tại là hoàn thành toàn bộ đợt 1.
- V13 chưa áp dụng production; trước deploy backup và thử restore. Rollback ứng dụng cũ chỉ phù hợp nếu ẩn Personal Tasks; không drop cột/bảng hoặc ép project_id NOT NULL khi đã có dữ liệu cá nhân.

Kết quả chốt baseline: `mvn -B -f backend/pom.xml test` chạy ngày 01/11/2026 với database test riêng: **315 tests, 0 failures, 0 errors, 2 skipped; BUILD SUCCESS**. Hai test bỏ qua thuộc AI live/benchmark, không coi là đã chạy. Báo cáo XML/TXT trong backend/app/target/surefire-reports. Không suy rộng kết quả này thành toàn bộ IT/E2E đã PASS; PostgreSQL 16/Docker vẫn chưa nghiệm thu. PostgreSQL test riêng đang chạy ở 127.0.0.1:5433 để phục vụ các đợt tiếp theo.

- ProjectService.addMember chặn cấp MANAGER cho Workspace GUEST ACTIVE; bổ sung target ACTIVE vào fixture GuestGuardTest để test đi tới đúng nhánh quyền.
- Policy404vs403Test: fixture VIEWER được giao task để kiểm tra 403 trên tài nguyên thấy được; Member được giao đọc được nhưng metadata write bị 403 theo đặc tả V2. Không mở quyền production để chiều kỳ vọng test cũ.
- Nhóm GuestGuardTest, ProjectMemberAccessIT, Policy404vs403Test, TaskExecutionPolicyTest: 15/15 PASS.
- Docker Desktop đã thử khởi động, engine vẫn không kết nối. Tìm thấy PostgreSQL 18 cục bộ; tạo cluster riêng .tools/pgdata-v2, bind loopback 127.0.0.1:5433, database smarttask_f21. Chỉ dùng dữ liệu test, không đụng database hiện có. Cluster dùng trust trên loopback; không dùng cấu hình này cho production.
- F21IntegrityTest: 22/22 PASS trên PostgreSQL 18.6; Flyway V1–V12 áp dụng thành công, Hibernate validate thành công. Đây chưa phải nghiệm thu PostgreSQL 16; Flyway có cảnh báo phiên bản PG18 mới hơn hỗ trợ đã kiểm chứng.
- Lệnh dừng cluster test: `"C:\Program Files\PostgreSQL\18\bin\pg_ctl.exe" -D D:\task\.tools\pgdata-v2 stop` (sau khi không còn test đang chạy).
- Lượt mở rộng `-Dtest=*,!F21IntegrityTest` có gọi cả IT và thất bại do DB integration chưa có; không dùng kết quả đó để khẳng định default suite đã PASS. Đang chạy lại default suite với database F21 hoạt động.

- Sửa AccountController.lock: thay Map.of bằng LinkedHashMap để giữ locked_until=null khi khóa vô thời hạn, tránh 500 sau khi đã khóa và thu hồi phiên.
- Sửa PolicyEnforcer/WorkflowController: start/submit dùng quyền thực hiện riêng. Member ACTIVE có assignment ACTIVE được thực hiện; Guest, membership REMOVED và assignment REMOVED bị chặn. Quyền sửa metadata/assignment vẫn giữ riêng.
- TaskExecutionPolicyTest chứng minh Member thực hiện được nhưng không ghi metadata; Guest bị 403; assignment/membership thu hồi bị 404.
- CalendarReportRealReadTest bổ sung fixture assignment để task thực sự thuộc phạm vi actor; không nới quyền đọc production để làm test pass.
- AccountControllerTest bỏ mock session/refresh không còn được controller sử dụng vì đã ủy nhiệm IdentityService; giữ verify revokeAllSessions.
- Kiểm thử thực chạy: Maven test với TaskExecutionPolicyTest, AccountControllerTest, CalendarReportRealReadTest, PlanningWorkflowPolicyTest: 12 tests, 0 failures, 0 errors, 0 skipped; BUILD SUCCESS.
- F21IntegrityTest: nguyên nhân root là PostgreSQL localhost:5433 connection refused (Flyway không kết nối); chưa coi là lỗi migration đã được xác minh.
- Các phần còn lại vẫn đang mở, không coi checkpoint này là báo cáo nghiệm thu V2. Chưa tạo screenshot và chưa kiểm thử E2E.

- Công cụ thêm: Maven 3.9.11 portable trong .tools, tải từ Maven Central; dùng Java 21 hiện có để build/test backend. Chưa cài kỹ năng mới. .gitignore bổ sung .tools và ZIP Maven để không đưa bộ công cụ vào source.
- Backend đã compile thành công 226 file nguồn và 102 file test baseline. Maven test baseline: 313 tests, 4 failures, 23 errors, 2 skipped; BUILD FAILURE. Không phải đã nghiệm thu backend.
- Nhóm lỗi baseline: CalendarReportRealReadTest (1 failure), GuestGuardTest (1), Policy404vs403Test (2); F21IntegrityTest (22 errors), AccountControllerTest (1 error). ProjectMemberAccessIT có report lỗi riêng trong thư mục output, không cộng thêm vào con số baseline 313 nếu chưa xác minh thời điểm report.
- Sửa AuthorizationPolicy.java: PLANNING chặn cả workflow.start/workflow.submit đúng action mà controller gửi, vẫn hỗ trợ task.start/task.submit cũ. Không đổi API/schema.
- Thêm PlanningWorkflowPolicyTest.java: test mới PASS; test kiểm tra cả action cũ/mới, vẫn cho phép cập nhật metadata trong PLANNING và start trong ACTIVE.
- Chạy nhóm PlanningWorkflowPolicyTest, Policy404vs403Test, PolicyDeletedScopeTest: lệnh tổng vẫn FAILURE vì 2 failure có từ baseline ở Policy404vs403Test. Chưa sửa các failure đó bằng cách nới quyền hoặc thay kỳ vọng test tùy tiện.
- Lỗi AccountController.lock được xác định có NullPointerException tại Map.of dòng 106; cần kiểm tra giá trị nullable và hợp đồng response trước sửa.
- Chưa hoàn thành: xử lý hết baseline failures/errors, integration với database/Docker, workflow Member, Personal Tasks và các đợt tiếp theo. Chưa commit/push/deploy.

- Authentication thực tế là opaque Bearer token, SHA-256 tra user_session; không phải JWT. Bằng chứng: security/SessionAuthenticationFilter.java và identity/service/IdentityService.java. Giữ cơ chế phiên hiện tại, không thay thành JWT chỉ vì stack dự kiến trong đặc tả.
- Filter trả 401 cho token thiếu/không tồn tại/hết hạn; 403 tài khoản không ACTIVE và 423 khi khóa. Đây là quan sát code, chưa PASS runtime.
- AuthController có register/login/logout/refresh và trả role SYSTEM_ADMIN/USER. Refresh trong transaction thu hồi token cũ, kiểm tra session liên kết, thu hồi session cũ và tạo token opaque mới; cần test replay/concurrency/rollback trước nghiệm thu.
- RequiresPermission là annotation khai báo. Tìm kiếm chưa thấy interceptor/aspect thực thi annotation; không được coi annotation riêng lẻ là bằng chứng quyền đã được kiểm tra. Cần truy vết policy/controller/service từng endpoint, ưu tiên admin và thao tác ghi.
- V2__ph1.sql có CHECK role workspace/project; custom role mới không thể chỉ thêm tên tùy ý nếu chưa xử lý constraint và đường resolve quyền.
- V3__ph2.sql đã có task_tag, time_log, priority CHECK, trường completion và unique approval_decision(round_id). Tái sử dụng các cấu trúc này.
- Inventory tìm được 131 dòng mapping/RequestMapping; con số này gồm prefix class, không phải 131 endpoint độc lập. Inventory method + path + DTO + permission chưa hoàn tất.
- Chưa tìm thấy Maven cục bộ qua PATH và các vị trí kiểm tra. run_test.bat cũ chạy Maven bằng Docker, nhưng Docker daemon hiện không hoạt động. Backend vẫn NOT_RUN; log cũ không thay bằng chứng mới.
- Còn mở: inventory chi tiết, kiểm thử backend, kiểm tra toàn bộ constraint/schema/entity, runtime authentication/RBAC, backup/restore. Chưa đủ điều kiện đóng đợt 0B hoặc bắt đầu migration lớn.

- Hoàn thành: baseline frontend và audit tĩnh ban đầu, kế hoạch phân đợt.
- Chưa hoàn thành: audit toàn bộ, baseline backend, runtime/E2E, triển khai các đợt 1–4.
- File thêm: docs/V2-AUDIT-PLAN.md. Không thay đổi API/database/nghiệp vụ.
- Trước migration: backup PostgreSQL/MinIO và thử restore trên môi trường riêng; không chạy rollback phá dữ liệu trực tiếp. Ưu tiên rollback application tương thích schema mở rộng.


## Checkpoint chốt 02/11/2026 — PostgreSQL16 và browser

Đã chạy native PostgreSQL16.6, migrate V1–V16, ba script browser PASS: Personal Tasks, smoke desktop/mobile/offline và workflow hai tài khoản. Workflow có thêm UI tạo/sửa task, cấp/thu hồi custom role, giao việc, thông báo, checklist/start/submit/approve và relogin. Backup PG16 restore vào database test riêng thành công. Bổ sung row lock cho updateContent/setDeadline và test cạnh tranh cùng version.

Báo cáo bàn giao duy nhất: [SMART-TASK-MANAGER-V2-FINAL-REPORT.md](SMART-TASK-MANAGER-V2-FINAL-REPORT.md), gồm file thay đổi, API, ảnh runtime và giới hạn chưa nghiệm thu. Các checkpoint cũ trong tài liệu này là lịch sử, không thay thế kết quả chốt. Docker engine/MinIO thật, object-store restore và provider email/push vẫn chưa nghiệm thu. Không cài skill mới, không commit/push/deploy.

Kết quả cuối: PG16 backend 478 total / 442 executed / 36 skipped / 0 failures / 0 errors, BUILD SUCCESS (16:43:23 +07). Frontend23/23,10suites; production build PASS. Workflow browser chạy lại trên runtime mới nhất PASS. Kết quả máy đọc được: V2-VALIDATION-RESULTS.json.
