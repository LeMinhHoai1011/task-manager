# 00 — AUDIT ĐẶC TẢ SMART TASK MANAGEMENT

**Artifact:** Báo cáo audit baseline yêu cầu + khảo sát hiện trạng
**Nguồn audit:** `D:\SMART_TASK_MANAGEMENT_FINAL_SPEC.md` (v1.0, 622 dòng, 11 nhóm FROZEN, ARCH-01..10 FROZEN, NFR-01..24 FROZEN)
**Ngày audit:** 11/10/2026
**Trạng thái:** Phase 0 — CHỈ PHÂN TÍCH. Chưa viết code, chưa tạo database, chưa commit, chưa deploy.
**Quy ước ID:** `AUD-nn` = phát hiện audit. Mỗi phát hiện có `Type` ∈ {CONFLICT, GAP, RISK, FACT} và `Gate` ∈ {BLOCKER, MAJOR, MINOR}. Phát hiện cần người dùng quyết định sẽ trỏ tới `DEC-nn` trong `08-DECISION-REGISTER.md`.


**Chú giải ký hiệu dùng trong các bảng:** `Y` = có / được phép / cơ chế giữ đầy đủ bất biến; `N` = không / không được phép / không thể dùng cơ chế đó; `!` = một phần (phần còn lại phải xử lý ở service layer hoặc đang chờ quyết định); `—` = không áp dụng.

---

## A. Phạm vi và phương pháp audit

Audit được thực hiện theo 5 bước:

1. Đọc toàn bộ đặc tả, tách bạch nội dung **FROZEN** (mục 2–13), **CHƯA FROZEN** (mục 15, 16, 17, 21, 22) và **điều kiện nghiệm thu** (mục 12, 19).
2. Đối chiếu chéo từng cặp nhóm yêu cầu để tìm mâu thuẫn ngữ nghĩa (đặc biệt Nhóm 03 ↔ 04 ↔ 05, Nhóm 02 ↔ 03, Nhóm 09 ↔ 10, NFR-14 ↔ mục 14.6).
3. Kiểm tra từng bất biến ở mục 15.2 xem có thể hiện thực hoá bằng constraint + transaction hay không; ghi nhận những bất biến **không thể** ép bằng schema.
4. Khảo sát repository và công cụ thực tế trên máy để đánh giá tính khả thi của NFR-09/10/11/21/23.
5. Liệt kê khoảng trống thiết kế bắt buộc phải chốt trước khi viết code, kèm phương án và khuyến nghị.

**Nguyên tắc áp dụng:** mọi kết luận ở đây KHÔNG tự sửa baseline. Nếu phát hiện mâu thuẫn, tôi ghi nhận và đề xuất, chờ xác nhận (đúng mục 1.2 khoản 3 của đặc tả).

---

## B. Hiện trạng repository và môi trường (FACT — đo thực tế, không suy diễn)

### B.1. Repository

| Mục | Kết quả kiểm tra |
|---|---|
| Thư mục làm việc | `D:\task` — **rỗng hoàn toàn**, không có file nào |
| Git | `fatal: not a git repository` — chưa khởi tạo repo |
| Code Spring Boot / React / Flutter có sẵn | **Không có** — greenfield 100% |
| Database script / migration có sẵn | **Không có** |
| Tài liệu thiết kế chi tiết (ERD, OpenAPI) | **Không có** — đúng như đặc tả mục 9 tuyên bố |

→ Kết luận: không có ràng buộc kế thừa (legacy) nào. Toàn bộ thiết kế chi tiết được bắt đầu từ baseline FROZEN.

### B.2. Công cụ trên máy (đo bằng lệnh, 11/10/2026)

| Thành phần | Trạng thái | Ghi chú cho kế hoạch |
|---|---|---|
| Java | Y 21.0.9 LTS | Đạt yêu cầu Spring Boot 3.x |
| Maven | N NOT FOUND | Phải cài Maven hoặc dùng Maven Wrapper (cần bootstrap wrapper thủ công) |
| Gradle | N NOT FOUND | Nếu chọn Gradle phải tải wrapper |
| Node.js | Y v26.10.0 | Đạt cho React + Vite |
| npm / pnpm | Y 11.19.1 / 9.15.4 | Nên chốt 1 package manager duy nhất |
| Dart SDK | Y 3.11.1 stable | Đạt cho Flutter |
| Flutter CLI | ! có trong PATH nhưng `flutter --version` không trả output | Cần chạy `flutter doctor` để xác nhận SDK hoàn chỉnh |
| PostgreSQL client (`psql`) | N NOT FOUND | Chưa có PostgreSQL local |
| Docker | Y 29.8.1 + Compose v5.5.1 | **Nhưng daemon ĐANG TẮT** (`failed to connect to npipe dockerDesktopLinuxEngine`) |
| Ollama | N NOT FOUND | Chưa có hạ tầng AI local |

### B.3. Tài nguyên máy (đo bằng PowerShell/CIM)

| Chỉ số | Giá trị đo được | Đối chiếu đặc tả |
|---|---|---|
| RAM | **15.7 GB** | Đặc tả mục 18.2: 16 GB "phù hợp phát triển và demo chức năng có giới hạn, **không mặc định đạt 50–100 concurrent users cùng AI**" |
| Logical CPU | **8** | Đủ cho dev, không đủ để load test 100 user + AI đồng thời |
| GPU | **Intel Iris Xe (integrated), ~1 GB VRAM** | Ollama sẽ chạy **CPU-only**. Đây là ràng buộc nặng cho Nhóm 07 và NFR-21 |
| Ổ D: còn trống | **28 GB / 196 GB (đã dùng 86%)** | Đủ cho source + DB dev, **rủi ro** nếu dựng MinIO + nhiều Docker image + dataset load test |
| OS | Windows 11 Home Single Language | Không phải môi trường server; deployment production cần Linux VM riêng |

---

## C. Đánh giá baseline FROZEN — điểm mạnh cần bảo toàn

Audit xác nhận baseline có chất lượng cao ở các mặt sau; thiết kế chi tiết **phải giữ nguyên**, không được làm yếu đi:

1. **Mô hình quyền chống đặc quyền ngầm rất chặt.** Việc System Admin và Workspace Owner/Admin *không* mặc nhiên đọc nội dung riêng tư (Nhóm 01, 02, 10) là quyết định hiếm gặp và đúng đắn. Nó buộc Authorization Policy Layer (ARCH-06) phải xét `scope membership` thật, không shortcut theo role global.
2. **Chống self-approval được định nghĩa theo *vai trò trong vòng* chứ không theo *chức danh*.** "Cấm tự duyệt nếu là assignee **hoặc** người submit; riêng creator không bị cấm chỉ vì là creator" (Nhóm 05) là quy tắc có thể kiểm thử được và không mơ hồ.
3. **Không auto-approve khi thiếu approver** (Nhóm 05) loại bỏ được lớp lỗi nghiêm trọng nhất của các hệ approval thực tế.
4. **Tách `BLOCKED` thành cờ độc lập, không phải trạng thái workflow** (Nhóm 04) giúp state machine nhỏ và đóng kín.
5. **Transactional Outbox + Durable Job + reauthorize trước bước nhạy cảm** (ARCH-04, ARCH-05, NFR-24) là nền đúng cho bài toán "revoke quyền giữa lúc job đang chạy".
6. **Realtime chỉ là tín hiệu, API là nguồn dữ liệu** (ARCH-09) loại bỏ class lỗi "client tin payload cũ sau khi mất quyền".
7. **Điều kiện nghiệm thu NFR mục 12 và 19.3 chống "tuyên bố PASS khống"** — yêu cầu công bố phần cứng, dataset, tần suất, tỷ lệ endpoint, thời lượng, cách tính P95. Điều này khớp với quy tắc người dùng đặt ra: không tự tạo kết quả kiểm thử.

---

## D. Phát hiện audit — Mâu thuẫn / chưa rõ trong nội dung FROZEN

> Đây là những điểm **cần người dùng xác nhận**, không được tự quyết. Mỗi mục nêu: trích dẫn, vấn đề, ảnh hưởng nếu hiểu sai, đề xuất.

### AUD-01 — Actor của việc tự động CANCELLED khi Project COMPLETED chưa xác định
`Type: CONFLICT | Gate: BLOCKER | DEC-01`

- **Trích dẫn:** Nhóm 03: "Project COMPLETED … các Task chưa hoàn tất (kể cả `PENDING_APPROVAL`) và Subtask chưa hoàn tất thành `CANCELLED`, các approval mở đóng theo cơ chế hủy". Nhóm 05: "cancel khi đang pending đóng vòng approval với kết quả hủy, **không giả mạo approve/reject**". Mục 15.2: "Audit mọi chuyển trạng thái: **actor**, thời gian, trước/sau, lý do".
- **Vấn đề:** Đặc tả không nói *ai* là actor của các chuyển trạng thái hàng loạt này — Manager người bấm COMPLETED, hay một system actor. Cũng không nói việc huỷ diễn ra **đồng bộ trong cùng transaction** với chuyển Project → COMPLETED hay qua job async.
- **Ảnh hưởng nếu hiểu sai:** (a) Nếu dùng job async, sẽ có cửa sổ Project đã COMPLETED nhưng Task còn IN_PROGRESS → vi phạm bất biến progress và báo cáo; (b) nếu ghi actor = Manager nhưng Manager không thực sự quyết định từng Task → audit sai bản chất; (c) nếu ghi actor = SYSTEM mà không định nghĩa SYSTEM trong mô hình session → vi phạm "mọi request đều có session hợp lệ".
- **Đề xuất:** Thực hiện **đồng bộ trong một transaction** với chuyển trạng thái Project. Audit ghi `actor_type = SYSTEM_RULE`, `actor_user_id = <Manager bấm COMPLETED>`, `reason_code = PROJECT_COMPLETED_CASCADE`, `triggered_by_audit_event_id = <event chuyển Project>`. Không tạo approval decision giả; ApprovalRound đóng với `round_outcome = CANCELLED`.

### AUD-02 — Ngữ nghĩa "readonly" của Project ON_HOLD / Workspace ARCHIVED chưa định nghĩa tới hành động workflow
`Type: CONFLICT | Gate: BLOCKER | DEC-02`

- **Trích dẫn:** Nhóm 03: "ON_HOLD readonly"; "Workspace hiệu lực readonly/archived ưu tiên hơn trạng thái Project". Nhóm 05 cho phép approve/reject/cancel/reopen. Nhóm 06: "Project ON_HOLD/ARCHIVED … tạm dừng deadline reminders".
- **Vấn đề:** "readonly" có bao gồm **các chuyển trạng thái workflow** không? Nếu có: một Task đang `PENDING_APPROVAL` sẽ bị **treo vô thời hạn** khi Project chuyển ON_HOLD — approver không thể approve cũng không thể reject. Nếu không: "readonly" chỉ áp cho nội dung.
- **Ảnh hưởng:** Quyết định này ảnh hưởng trực tiếp ma trận RBAC (mục 04), state machine (mục 05), và test case "Project ON_HOLD chặn thao tác".
- **Đề xuất:** `readonly` = chặn **mọi** mutation nội dung **và** workflow, **ngoại trừ** hành động của Manager ở mức metadata Project (chuyển trạng thái Project, unarchive) và hành động khôi phục Manager khẩn cấp của Workspace Owner. Task `PENDING_APPROVAL` giữ nguyên trạng thái, approver thấy thông báo "đang tạm hoãn", reminder bị pause, không auto-decide. Khi Project về ACTIVE, vòng approval tiếp tục và chỉ phát **một** thông báo tồn đọng (không gửi bù hàng loạt — đúng Nhóm 06).

### AUD-03 — Thành viên rời Workspace khi đang là Manager cuối của một Project
`Type: CONFLICT | Gate: BLOCKER | DEC-03`

- **Trích dẫn:** Nhóm 02: "Member được rời Workspace"; "Nếu người đó là Manager, cần thay thế hợp lệ". Nhóm 03: "Project chưa DELETED phải có ít nhất một Manager ACTIVE hợp lệ". Nhóm 02: "Owner phải chuyển quyền trước khi rời".
- **Vấn đề:** Với Owner thì đặc tả nói rõ "phải chuyển quyền trước khi rời" (chặn). Với Manager thì chỉ nói "cần thay thế hợp lệ" — **không rõ** là *chặn thao tác rời* hay *tự động hạ cấp và để Project mồ côi*. Tự động hạ cấp sẽ vi phạm bất biến "≥1 Manager".
- **Đề xuất:** Chặn cứng, trả `409 CONFLICT` với mã lỗi `LAST_PROJECT_MANAGER`, buộc chỉ định Manager thay thế trước. Áp dụng cho cả: tự rời, bị Owner/Admin loại, bị soft-delete tài khoản, và chuyển Workspace membership sang trạng thái không ACTIVE.

### AUD-04 — Workspace Guest có được làm Project Member (có quyền sửa) không?
`Type: GAP | Gate: MAJOR | DEC-04`

- **Trích dẫn:** Nhóm 03: "Manager phải là thành viên ACTIVE của Workspace và **không là Workspace Guest**". Nhóm 02: "Guest không tạo Project", "Guest không mời". Nhóm 10: "Guest chỉ xem trong phạm vi được cấp".
- **Vấn đề:** Đặc tả chỉ cấm Workspace Guest làm **Manager**. Không nói rõ Workspace Guest có thể là **Project Member** (tức có quyền tạo/sửa Task) hay bắt buộc chỉ là **Project Guest** (view-only).
- **Rủi ro:** Nếu cho phép, một Workspace Guest có thể có quyền ghi nội dung Project — mâu thuẫn tinh thần Nhóm 02/10.
- **Đề xuất:** Workspace Guest chỉ được gắn vai trò **Project Guest** (view-only). Backend từ chối mọi lời mời/thêm Workspace Guest vào Project với role Manager/Member (`403 GUEST_CANNOT_HAVE_WRITE_ROLE`).

### AUD-05 — Subtask có vòng nghiệm thu riêng không?
`Type: GAP | Gate: BLOCKER | DEC-05`

- **Trích dẫn:** Nhóm 04: "Subtask có tối đa một cấp; có assignee/deadline riêng. Parent chỉ hoàn tất khi mọi Subtask đã `COMPLETED` hoặc `CANCELLED`". Nhóm 03: nghiệm thu bật/tắt **theo Project**. Nhóm 05 mô tả approval round cho "Task".
- **Vấn đề:** Không nói Subtask có đi qua `PENDING_APPROVAL` hay không. Nếu có, số vòng duyệt tăng theo cấp số task và chi phí nghiệp vụ rất lớn; nếu không, cần nêu rõ.
- **Đề xuất:** V1: **Subtask KHÔNG có approval round.** State machine Subtask = `TODO → IN_PROGRESS → COMPLETED` (+ `CANCELLED`, reopen). Approval chỉ áp cho top-level Task. Lý do: (a) đặc tả Nhóm 03 nói "Task COMPLETED giữ nguyên" và tách bạch "Task chưa hoàn tất … và Subtask chưa hoàn tất"; (b) giảm đáng kể số ca race; (c) parent đã là điểm nghiệm thu. **Cần người dùng xác nhận vì đây là quyết định phạm vi.**

### AUD-06 — Nội dung nào bị khoá khi Task ở `PENDING_APPROVAL`
`Type: GAP | Gate: BLOCKER | DEC-06`

- **Trích dẫn:** Nhóm 04: "`PENDING_APPROVAL` khóa nội dung thực thi Task; không được vòng qua bằng sửa comment/file hoặc API khác". Nhóm 09: "`PENDING_APPROVAL` khóa thêm/thay/xóa/restore file thực thi Task, không vòng qua comment/Project file". Test 19.2: "File PENDING_APPROVAL không thể bị sửa bằng đường API/comment khác".
- **Vấn đề:** "Nội dung thực thi" chưa được liệt kê. Cần chốt từng loại: description, attachment, checklist item, time log, comment, assignment, deadline, priority, tag, dependency.
- **Đề xuất (bảng khoá):**

| Đối tượng khi Task = PENDING_APPROVAL | Đề xuất | Lý do |
|---|---|---|
| Attachment (add/replace/delete/restore) | CHẶN | Đặc tả nói rõ |
| Checklist item (add/edit/check/delete) | CHẶN | Là bằng chứng hoàn thành, sửa sẽ làm mất ý nghĩa duyệt |
| Description / title | CHẶN | Là nội dung được duyệt |
| Time log | CHẶN | Ảnh hưởng số liệu báo cáo của vòng duyệt |
| Assignment (thêm/bớt assignee) | CHẶN | Đổi assignee làm đổi tập "cấm tự duyệt" giữa vòng → phá integrity |
| Comment | Y CHO PHÉP (có audit) | Thảo luận trong lúc duyệt là nghiệp vụ bình thường; comment không phải "nội dung thực thi" |
| Deadline / priority / tag / milestone | Y Manager được sửa (có audit) | Metadata điều phối, không phải nội dung được duyệt |
| Dependency | CHẶN | Tránh đổi đồ thị phụ thuộc giữa vòng duyệt |

  → Cần xác nhận, đặc biệt 2 dòng Comment và Deadline.

### AUD-07 — Ai được chỉ định làm Designated Approver
`Type: GAP | Gate: MAJOR | DEC-07`

- **Trích dẫn:** Nhóm 05: "Approver chỉ định theo Task bởi Manager; mặc định là Manager đủ điều kiện". Mục 16: "Designated Approver — Duyệt vòng hợp lệ khi hiện còn quyền và độc lập".
- **Vấn đề:** Không nói designated approver có bắt buộc là Manager hay không. Nếu bắt buộc là Manager, thì Project chỉ có 1 Manager mà Manager đó lại là submitter → **deadlock vĩnh viễn** (đặc tả đã dự liệu "giữ PENDING_APPROVAL + cảnh báo", nhưng không có đường ra).
- **Đề xuất:** Designated approver = **bất kỳ Project participant ACTIVE, không phải Project Guest, không phải Workspace Guest**, và thoả điều kiện độc lập (không là assignee của Task, không là người submit round này). Cho phép Project Member làm approver để phá deadlock. Nếu người dùng muốn siết chặt hơn (chỉ Manager), phải chấp nhận deadlock có kiểm soát và cần quy trình khẩn cấp bổ sung.

### AUD-08 — "Hạn duyệt có thể cấu hình" có sinh ra quyết định tự động không?
`Type: CONFLICT | Gate: MAJOR | DEC-08`

- **Trích dẫn:** Nhóm 05: "Task pending quá hạn vẫn pending, có cảnh báo quá hạn, **không tự reject**. Có thể cấu hình hạn duyệt và nhắc việc."
- **Vấn đề:** "Hạn duyệt" (approval SLA) có thể bị hiểu là auto-escalate hoặc auto-approve khi quá hạn. Cụm "không tự reject" chỉ cấm reject, không nói tới approve/escalate.
- **Đề xuất:** SLA chỉ sinh **notification/escalation** (nhắc approver, nhắc Manager, ghi nhận vào báo cáo "thời gian chờ"). **Tuyệt đối không** sinh quyết định nghiệp vụ tự động. Không tồn tại `round_outcome = EXPIRED` như một *quyết định*; quá hạn chỉ là **cờ tính toán** `sla_breached` trên round.

### AUD-09 — Dependency có được bắc cầu qua Project/Workspace khác không?
`Type: GAP | Gate: MAJOR | DEC-09`

- **Trích dẫn:** Nhóm 04: "Dependency chỉ cảnh báo, không hard-block, **không được tạo chu trình**".
- **Vấn đề:** (a) Không nói dependency có vượt Project hay không; (b) không nói loại dependency (finish-to-start hay chỉ "liên quan"); (c) mâu thuẫn nội tại: "không hard-block" nhưng "không được tạo chu trình" → chu trình **phải** hard-block. Cần diễn đạt lại cho rõ.
- **Đề xuất:** V1: dependency **chỉ trong cùng một Project**, chỉ giữa **top-level Task với nhau** (không cho Subtask), loại duy nhất `RELATED_BLOCKS` mang tính cảnh báo; **chu trình bị hard-block** bằng kiểm tra đồ thị (DFS/`WITH RECURSIVE`) trong transaction. Dependency tới Task đã COMPLETED/CANCELLED vẫn lưu được nhưng hiển thị cảnh báo.

### AUD-10 — Personal Workspace có được chứa Project và Project đó có participant không?
`Type: GAP | Gate: MAJOR | DEC-10`

- **Trích dẫn:** Nhóm 02: "Mỗi tài khoản ACTIVE có đúng một Personal Workspace riêng tư; **không mời thành viên**". Nhóm 03: "Project … có nhiều Manager", "thành viên Project được thêm tường minh".
- **Vấn đề:** Không nói Personal Workspace có Project hay không, và nếu có thì có được thêm participant (điều này sẽ biến nó thành Team Workspace "bằng đường vòng" — vi phạm bất biến Nhóm 02).
- **Đề xuất:** Personal Workspace **có** Project (đây là giá trị chính của "quản lý công việc cá nhân"), nhưng Project trong Personal Workspace **chỉ có đúng 1 participant** là chủ tài khoản, vai trò Manager. Backend từ chối mọi lời mời/thêm participant vào Project thuộc Personal Workspace. Gợi ý assignee của AI trong ngữ cảnh này phải trả về rỗng.

### AUD-11 — Invite tới email đã có tài khoản ACTIVE vs tài khoản PENDING_APPROVAL
`Type: GAP | Gate: MAJOR | DEC-11`

- **Trích dẫn:** Nhóm 01: "Lời mời gắn email cụ thể, có hạn, dùng một lần. Tài khoản mới phải ACTIVE trước khi có hiệu lực tham gia". Nhóm 02: "ACTIVE mới được tham gia".
- **Vấn đề:** Chưa mô tả 3 nhánh: (a) email chưa có tài khoản → đăng ký mới; (b) email đã có tài khoản ACTIVE → tham gia ngay hay cần người được mời chấp nhận?; (c) email có tài khoản PENDING_APPROVAL/SUSPENDED → xử lý thế nào.
- **Đề xuất:** Tạo `WorkspaceInvitation` ở trạng thái `PENDING_ACCEPT`. Nhánh (a): invite tạo sẵn "pre-approved registration token" gắn email; tài khoản sau khi được duyệt ACTIVE thì membership tự kích hoạt. Nhánh (b): người được mời phải **chấp nhận** mời (accept) để membership có hiệu lực — tránh bị gán vào Workspace ngoài ý muốn. Nhánh (c): membership giữ `PENDING_ACTIVATION` tới khi tài khoản ACTIVE. Invite đã dùng một lần → đánh dấu `REDEEMED`, không xoá (giữ audit).

### AUD-12 — Offline Mobile: "dữ liệu ít nhạy cảm" là dữ liệu nào?
`Type: CONFLICT | Gate: BLOCKER | DEC-12`

- **Trích dẫn:** NFR-14: "Mobile đọc cache an toàn; thay đổi dữ liệu phải online". NFR-22: "offline chỉ xem dữ liệu ít nhạy cảm còn hạn; nội dung Project/Task/file riêng tư phải xác minh lại quyền". Mục 14.6: "không cho mở nội dung Project/Task/file riêng tư offline nếu chưa xác minh lại quyền". Test 19.2: "Offline Mobile không mở nội dung Task/Project/file riêng tư nếu chưa reauthorize".
- **Vấn đề:** Ba chỗ trên gần như loại bỏ toàn bộ nội dung khỏi offline cache, nhưng NFR-14 vẫn yêu cầu "đọc cache an toàn". Chưa định nghĩa **allowlist** offline. Đây là quyết định phạm vi lớn cho Flutter.
- **Đề xuất allowlist V1 (offline chỉ đọc):** hồ sơ tài khoản của chính mình (tên, username, avatar, timezone), danh sách **tên** Workspace/Project mà user là thành viên, cấu hình UI (ngôn ngữ, chủ đề, quiet hours), timestamp lần sync cuối, và các notification **đã redact**. **Không cache:** title/description Task, checklist, comment, attachment, số liệu dashboard, time log. Ngoài allowlist → màn hình "Cần kết nối để xem" + nút Reauthorize.
- **Ghi chú:** nếu người dùng muốn offline hữu ích hơn (ví dụ cache title Task), phải chấp nhận rủi ro đã nêu ở mục 20 của đặc tả ("Revocation khi Mobile offline — Cao") và cần cơ chế xoá cache từ xa tốt nhất có thể (chỉ có thể xoá khi thiết bị online lại).

### AUD-13 — Redaction notification sau khi đã push ra thiết bị
`Type: RISK | Gate: MAJOR | DEC-13`

- **Trích dẫn:** Nhóm 06: "Khi mất quyền, redaction nội dung notification nhạy cảm và chặn deep link; audit giữ lại".
- **Vấn đề:** Push đã hiển thị trên lock screen của thiết bị thì **không thể thu hồi**. Redaction chỉ áp dụng được cho in-app notification và cho lần fetch tiếp theo.
- **Đề xuất:** (a) Chấp nhận đây là **residual risk có chủ đích** và ghi vào risk register; (b) giảm thiểu bằng data-minimization ngay từ payload push: push chỉ chứa `"Bạn có cập nhật mới"` + badge, **không** chứa tiêu đề Task/tên Project; nội dung chỉ được render khi client gọi API (đã reauthorize); (c) khi phát hiện mất quyền, server đánh dấu notification `REDACTED` và client buộc xoá khỏi local store ở lần online kế tiếp.
- **Hệ quả thiết kế:** quyết định (b) làm thay đổi contract push → phải chốt trước Phase 4.

### AUD-14 — Audit "bất biến" vs "retention/disposal policy"
`Type: CONFLICT | Gate: MAJOR | DEC-14`

- **Trích dẫn:** Nhóm 09: "Audit bất biến từ UI, có retention/disposal policy". Mục 15.2: "Audit lịch sử không bị sửa".
- **Vấn đề:** Bất biến và "disposal" mâu thuẫn nếu không định nghĩa ai được xoá và xoá thế nào.
- **Đề xuất:** Audit **append-only ở tầng ứng dụng và tầng DB**: role PostgreSQL của application **không có quyền UPDATE/DELETE** trên bảng audit (chỉ INSERT/SELECT). Disposal là một **tiến trình vận hành riêng** chạy bằng DB role đặc quyền, chỉ thực hiện theo retention policy đã cấu hình, và **bản thân thao tác disposal cũng được audit** ở một bảng riêng (`audit_maintenance_log`) với hash-chain của khối audit bị huỷ. V1 khuyến nghị: retention mặc định **không xoá** (chỉ archive theo partition tháng), vì chưa có yêu cầu GDPR cụ thể.

### AUD-15 — Số liệu lịch sử: event-derived hay snapshot?
`Type: GAP | Gate: BLOCKER | DEC-15`

- **Trích dẫn:** Nhóm 08: "tiến độ lịch sử theo event/audit tại thời điểm xảy ra, **reopen không viết lại quá khứ**". Mục 15: "HistoricalEvent/Snapshot (nếu cần)". Mục 15.2: "Audit lịch sử không bị sửa khi Task reopen".
- **Vấn đề:** Chưa chốt cơ chế. Nếu chỉ dựa vào AuditEvent (dạng before/after JSON) thì truy vấn "progress tại ngày X" rất chậm và khó đúng. Nếu dùng snapshot thì phải định nghĩa thời điểm chụp.
- **Đề xuất:** **Hai tầng.** (1) `workflow_event` bất biến, chuẩn hoá, có `occurred_at` — nguồn chân lý để tái dựng trạng thái tại bất kỳ thời điểm nào; (2) `metric_daily_snapshot` chụp cuối ngày theo scope (Project/Workspace/User) để dashboard lịch sử nhanh. Snapshot **không bao giờ được cập nhật ngược**; ngày đã chốt là bất biến. Reopen chỉ tạo event mới, không sửa event/snapshot cũ.

### AUD-16 — Report export lưu ở đâu và thu hồi thế nào
`Type: GAP | Gate: MAJOR | DEC-16`

- **Trích dẫn:** Nhóm 08: "Xuất PDF/XLSX/CSV V1; kiểm tra quyền ở thời điểm tạo và **giao file**". ARCH-07, Nhóm 09: "mỗi lượt preview/download … kiểm tra quyền tại request; không cấp URL không thể thu hồi". NFR-24: "reauthorize … trước trả kết quả".
- **Vấn đề:** File export là dữ liệu nhạy cảm đã tổng hợp. Chưa nói nơi lưu, TTL, và ai được tải lại.
- **Đề xuất:** Export là **Durable Job**; kết quả ghi Object Storage private với prefix `reports/`, metadata ở DB; **TTL mặc định 24h** sau đó bị job dọn dẹp; chỉ **người yêu cầu** được tải (không chia sẻ), mỗi lượt tải reauthorize + ghi `FileAccessEvent`; job reauthorize ngay trước khi upload kết quả. Nếu mất quyền giữa chừng → huỷ job, xoá object dở, ghi audit.

### AUD-17 — Trùng lặp giữa "mật khẩu tạm 24 giờ" và "mã khôi phục offline một lần"
`Type: GAP | Gate: MINOR | DEC-17`

- **Trích dẫn:** Nhóm 01: "mật khẩu tạm do Admin cấp có hạn 24 giờ **hoặc** mã khôi phục offline dùng một lần".
- **Vấn đề:** Chưa rõ hai cơ chế này thay thế hay song song, ai sinh mã offline, sinh lúc nào, lưu thế nào.
- **Đề xuất:** Song song, dùng cho 2 tình huống khác nhau. (a) Admin reset → mật khẩu tạm 24h, buộc đổi ở lần đăng nhập đầu (`must_change_password = true`). (b) Người dùng tự in **8 mã khôi phục offline** (one-time, hash trong DB, chỉ hiển thị đúng 1 lần khi tạo), dùng khi không có email/Admin — mỗi mã chỉ dùng 1 lần và sinh ra một phiên yêu cầu reauthentication + đổi mật khẩu. Admin có thể thu hồi toàn bộ mã.

### AUD-18 — Bootstrap System Admin "quy trình local một lần" chưa cụ thể
`Type: GAP | Gate: MAJOR | DEC-18`

- **Trích dẫn:** Nhóm 01: "Bootstrap System Admin ban đầu theo quy trình local một lần và an toàn".
- **Vấn đề:** Chưa chốt cơ chế; đây là điểm tấn công nguy hiểm nhất nếu làm sai (endpoint public tạo admin).
- **Đề xuất:** Chỉ thực hiện qua **CLI trên máy chủ**: `java -jar app.jar bootstrap-admin --username ... --email ...` đọc secret từ file quyền 0600 hoặc stdin, **không có HTTP endpoint**. Điều kiện: bảng `app_user` chưa có System Admin nào, và cờ `system.bootstrap_completed = false` trong DB. Sau khi chạy, cờ lật `true` vĩnh viễn; mọi lần chạy lại đều bị từ chối + ghi security event. Mật khẩu ban đầu buộc đổi ở lần đăng nhập đầu.

### AUD-19 — "Mỗi tài khoản chỉ một phiên" và hệ quả đa thiết bị
`Type: CONFLICT | Gate: MAJOR | DEC-19`

- **Trích dẫn:** Nhóm 01: "**mỗi tài khoản chỉ có một phiên hoạt động**. Đăng nhập mới vô hiệu hóa cả access token, refresh token và phiên cũ; hủy đăng ký push gắn phiên cũ".
- **Vấn đề:** Đây là quyết định FROZEN, không bàn lại. Nhưng **hệ quả** chưa được đặc tả: (a) người dùng dùng Web + Mobile song song là **không thể** — mỗi lần mở app mobile sẽ đá văng web; (b) cần UX thông báo "bạn bị đăng xuất vì tài khoản đăng nhập ở nơi khác"; (c) cần phân biệt "đăng nhập lại trên cùng thiết bị" để không tạo cảm giác bị đá.
- **Đề xuất:** Giữ nguyên FROZEN. Bổ sung: (1) session có `device_label` + `client_type` (WEB/ANDROID/IOS) do client gửi lên, hiển thị ở màn hình "phiên hiện tại"; (2) trước khi login, API trả cảnh báo "sẽ kết thúc phiên trên <device_label>" và client xác nhận; (3) push tới phiên cũ là `SESSION_REVOKED` (không chứa nội dung); (4) websocket của phiên cũ bị đóng với code riêng.

### AUD-20 — Công thức Progress: quy tắc làm tròn và hành vi khi reopen
`Type: GAP | Gate: MINOR | DEC-20`

- **Trích dẫn:** Nhóm 03: "Progress Project = top-level COMPLETED / (top-level tổng − top-level CANCELLED) × 100%; mẫu số 0 → `N/A`".
- **Vấn đề:** Chưa nói làm tròn mấy chữ số, và progress có giảm khi Manager reopen Task COMPLETED không.
- **Đề xuất:** Trả về **1 chữ số thập phân**, làm tròn half-up; API trả cả `numerator`, `denominator`, `percent` (hoặc `percent = null` + `display = "N/A"`) để client không tự tính. Progress **tính lại trực tiếp từ trạng thái hiện tại**, nên reopen làm giảm progress — nhưng **số liệu lịch sử** (snapshot/event) không đổi (đúng "reopen không viết lại quá khứ").

### AUD-21 — Trạng thái Milestone: thủ công hay suy diễn?
`Type: GAP | Gate: MINOR | DEC-21`

- **Trích dẫn:** Nhóm 03: "Milestone tùy chọn; Manager tạo". Nhóm 08: "Milestone: hoàn thành/chưa hoàn thành/hủy/quá hạn nếu có deadline". Nhóm 03: Project COMPLETED → "Milestone chưa xong thành `CANCELLED`".
- **Vấn đề:** Không nói Milestone COMPLETED do Manager bấm hay tự suy ra từ Task.
- **Đề xuất:** **Thủ công** bởi Manager (`status = OPEN | COMPLETED | CANCELLED`), kèm **chỉ báo suy diễn** `progress` và `overdue` tính từ Task thuộc Milestone. Không tự chuyển COMPLETED. Lý do: Milestone thường mang ý nghĩa cam kết, không phải tổng cơ học.

### AUD-22 — Phạm vi của Tag
`Type: GAP | Gate: MINOR | DEC-22`

- **Trích dẫn:** Nhóm 04: "hỗ trợ comment, attachment, checklist, **tag** và time log". Mục 15: `Tag`, `TaskTag`.
- **Vấn đề:** Tag thuộc Workspace, Project hay global? Ai tạo được?
- **Đề xuất:** Tag thuộc **Workspace**, tên duy nhất trong Workspace (unique `(workspace_id, lower(name))`), màu + mô tả ngắn. Manager/Member được gắn tag có sẵn; chỉ Workspace Owner/Admin được tạo/sửa/xoá tag definition. Không có tag cá nhân ở V1.

### AUD-23 — Time log: ai ghi cho ai, có được sửa/xoá không?
`Type: GAP | Gate: MAJOR | DEC-23`

- **Trích dẫn:** Nhóm 04: "time log nhập thủ công". Nhóm 08: "time log theo người ghi". Mục 15: `TimeLog`.
- **Vấn đề:** Chưa rõ: Manager có được ghi hộ thành viên không? Có giới hạn ngày trong quá khứ/tương lai không? Sửa/xoá time log đã ghi bởi ai?
- **Đề xuất:** Mỗi người **chỉ ghi cho chính mình** (`user_id` luôn = actor, không nhận từ request body). Khoá sổ theo kỳ: không cho ghi/sửa time log của ngày đã có snapshot đóng (hoặc cấu hình `timelog_edit_window_days` mặc định 7 ngày). Manager được **soft-delete** time log của thành viên khi có lý do + audit (phục vụ sửa sai), không được sửa số giờ. Không có time log cho Task `PENDING_APPROVAL` (AUD-06).

### AUD-24 — Reminder dùng chung do Manager đặt: ai nhận?
`Type: GAP | Gate: MINOR | DEC-24`

- **Trích dẫn:** Nhóm 06: "Reminder mặc định trước deadline 1 ngày và 1 giờ, có thể cấu hình theo user; **Manager có thể đặt reminder dùng chung theo quyền**".
- **Vấn đề:** Không nói danh sách người nhận của reminder dùng chung, và tương tác với mute/quiet hours của từng người.
- **Đề xuất:** Reminder dùng chung gắn vào Task hoặc Milestone, người nhận = **toàn bộ assignee ACTIVE + Manager của Project**. Vẫn tôn trọng mute/quiet hours từng người **trừ** khi reminder được đánh dấu `escalation = true` (do Manager đặt) thì bỏ qua quiet hours nhưng **không** bỏ qua mute loại bảo mật. Mỗi reminder dùng chung chỉ nổ **một lần** cho mỗi người nhận (chống trùng khi job retry).

### AUD-25 — Hành vi resume sau pause: digest hay từng thông báo?
`Type: GAP | Gate: MINOR | DEC-25`

- **Trích dẫn:** Nhóm 06: "resume không gửi bù hàng loạt, tính lại và **có thể phát một thông báo quá hạn hiện tại**".
- **Vốn đề:** "một thông báo" cho mỗi Task hay một thông báo tổng hợp cho mỗi người?
- **Đề xuất:** **Một digest duy nhất cho mỗi người nhận** mỗi lần resume: "Project X hoạt động lại — bạn có N task quá hạn, M task chờ duyệt". Không gửi theo từng Task. Digest là một notification loại `RESUME_DIGEST`, idempotent theo `(workspace_id|project_id, resume_event_id, user_id)`.

### AUD-26 — Ngôn ngữ tìm kiếm tiếng Việt và full-text search
`Type: RISK | Gate: MAJOR | DEC-26`

- **Trích dẫn:** Nhóm 04: "search"; Nhóm 07: "Chat/tìm kiếm tự nhiên"; Nhóm 11 không nói về search engine.
- **Vấn đề:** PostgreSQL không có text search configuration cho tiếng Việt. `unaccent` không xử lý được dấu thanh đúng cách cho tiếng Việt (unaccent sẽ **bỏ dấu thanh**, gây sai nghĩa: "xa" ≠ "xã"). Kết quả tìm kiếm tiếng Việt bằng `to_tsvector('simple')` sẽ kém.
- **Đề xuất V1:** kết hợp (a) `pg_trgm` GIN index cho fuzzy/LIKE, (b) cột `search_text` chuẩn hoá (lowercase, bỏ dấu **chỉ để matching**, giữ nguyên bản gốc để hiển thị) + `to_tsvector('simple', search_text)`, (c) tìm kiếm AI (semantic) ở Nhóm 07 dùng embedding local nếu Ollama có model embedding. **Không** đưa Elasticsearch/OpenSearch vào V1 (xung đột NFR-01 một máy chủ). Cần người dùng chấp nhận chất lượng tìm kiếm tiếng Việt ở mức "đủ dùng", không phải hoàn hảo.

### AUD-27 — Kiểm soát cạnh tranh khi 2 approver quyết định đồng thời
`Type: GAP | Gate: BLOCKER | DEC-27`

- **Trích dẫn:** Nhóm 05: "Nhiều approver hợp lệ nhưng chỉ cần một quyết định APPROVE hợp lệ để kết thúc vòng"; "Chỉ **một kết quả terminal** mỗi approval round". Test 19.1: "Hai approver approve đồng thời: chỉ một terminal decision".
- **Vấn đề:** Đặc tả nêu yêu cầu nhưng chưa nêu cơ chế. Đây là điểm bắt buộc phải thiết kế đúng.
- **Đề xuất (bắt buộc):** kết hợp **3 lớp**: (1) `SELECT … FOR UPDATE` trên dòng `approval_round` ở đầu transaction; (2) **partial unique index** `ON approval_decision(round_id) WHERE decision_outcome IN ('APPROVED','REJECTED','CANCELLED')` — DB tự chặn quyết định terminal thứ hai; (3) optimistic lock `version` trên `task`. Request thua cuộc nhận `409 APPROVAL_ALREADY_DECIDED` và trả về quyết định đã thắng (không lộ thông tin ngoài quyền).

### AUD-28 — Chuyển Owner Workspace: cơ chế nguyên tử
`Type: GAP | Gate: MAJOR | DEC-28`

- **Trích dẫn:** Nhóm 02: "Chuyển Owner **nguyên tử** sang thành viên ACTIVE hợp lệ; Owner cũ thành Member, vai trò Project không tự thay đổi". Mục 20: "Race condition approval/owner transfer — Cao".
- **Đề xuất:** Một transaction: lock `workspace` (`FOR UPDATE`) → kiểm tra điều kiện (target ACTIVE, là membership hợp lệ, không phải Guest) → update 2 dòng membership trong cùng statement set → ghi audit → ghi outbox event `WORKSPACE_OWNER_TRANSFERRED`. Partial unique index `ON workspace_membership(workspace_id) WHERE role='OWNER' AND status='ACTIVE' AND deleted_at IS NULL` bảo đảm **luôn đúng 1 Owner** ở tầng DB, kể cả khi code sai.

### AUD-29 — "Không tự hồi sinh quyền bị thu hồi" khi restore
`Type: GAP | Gate: MAJOR | DEC-29`

- **Trích dẫn:** Nhóm 02: "Xóa/khôi phục theo quyền đã chốt, **không tự hồi sinh quyền bị thu hồi**". Nhóm 04: "restore chỉ phục hồi những đối tượng bị xóa theo cùng thao tác và vẫn hợp lệ, **không khôi phục những mục đã bị xóa độc lập**".
- **Vấn đề:** Cần cơ chế kỹ thuật để phân biệt "bị xoá theo cascade" với "bị xoá độc lập".
- **Đề xuất:** Mỗi soft-delete ghi `deleted_at`, `deleted_by`, và **`delete_group_id`** (UUID sinh cho mỗi thao tác xoá). Restore nhận `delete_group_id` → chỉ phục hồi các dòng cùng group, và chỉ khi cha của chúng còn sống (không `deleted_at`) và các ràng buộc quyền hiện tại còn thoả. Membership **không bao giờ** được restore tự động theo Workspace/Project restore — phải thêm lại tường minh.

### AUD-30 — Bất biến "đúng một Personal Workspace" không thể ép bằng unique index thuần
`Type: GAP | Gate: MAJOR | DEC-30`

- **Trích dẫn:** Mục 15.2: "Mỗi ACTIVE account đúng một Personal Workspace". Nhóm 02: "Personal Workspace không được biến thành Team Workspace bằng đường vòng".
- **Vấn đề:** Quan hệ 1-1 nằm giữa `app_user` và `workspace(type=PERSONAL)`; unique index thông thường không diễn đạt được "mỗi user đúng 1".
- **Đề xuất:** Cột **`workspace.personal_owner_user_id`** (NULL với Team Workspace) + **partial unique index** `ON workspace(personal_owner_user_id) WHERE type='PERSONAL' AND deleted_at IS NULL`. Thêm **CHECK constraint** `(type='PERSONAL') = (personal_owner_user_id IS NOT NULL)`. Chống "biến thành Team": `type` **không thể UPDATE** (thu hồi quyền UPDATE cột này ở DB role application, hoặc trigger chặn).

### AUD-31 — Username/email đã dùng phải "giữ dành riêng" → ảnh hưởng unique index
`Type: GAP | Gate: MAJOR | DEC-31`

- **Trích dẫn:** Nhóm 01: "Username/email đã dùng được giữ dành riêng, **không cấp lại**"; "Soft-delete tài khoản, giữ audit".
- **Vấn đề:** Nếu dùng partial unique index chỉ trên bản ghi chưa xoá (`WHERE deleted_at IS NULL`) thì email của tài khoản đã soft-delete sẽ **được cấp lại** → vi phạm.
- **Đề xuất:** Unique index **toàn phần** (không có `WHERE deleted_at IS NULL`) trên `lower(username)` và `lower(email)`. Email đổi bằng cách thêm bản ghi vào `user_email_history` và cập nhật email hiện tại sang giá trị mới **đã được giữ dành riêng** (không tái sử dụng). Tài khoản `DELETED` vẫn chiếm email.

### AUD-32 — Khoá tạm thời (lockout) là "điều kiện bảo mật riêng", không phải trạng thái
`Type: GAP | Gate: MINOR | DEC-32`

- **Trích dẫn:** Nhóm 01: "khóa tạm thời là trạng thái/điều kiện bảo mật riêng". Nhóm 10: "rate limit, lockout tạm, security log".
- **Đề xuất:** Mô hình hoá bằng các cột trên `app_user`: `failed_login_count`, `locked_until`, và bảng `security_event` ghi mọi lần khoá/mở. Lockout **không** đổi `status` (vẫn ACTIVE) → API trả `423 LOCKED` khác với `403 SUSPENDED`. Ngưỡng mặc định đề xuất: khoá 15 phút sau 5 lần sai; rate limit login theo IP + theo username.

### AUD-33 — `ACCOUNT_APPROVE_GLOBAL` là quyền rời, không phải role
`Type: GAP | Gate: MINOR | DEC-33`

- **Trích dẫn:** Nhóm 01: "System Admin hoặc Workspace Admin được cấp `ACCOUNT_APPROVE_GLOBAL` có thể duyệt; quyền duyệt **không kéo theo quyền đọc dữ liệu riêng tư**".
- **Đề xuất:** Thêm bảng `global_permission_grant(user_id, permission_code, granted_by, granted_at, expires_at, revoked_at, reason)`. Role chỉ là tập quyền mặc định; `ACCOUNT_APPROVE_GLOBAL` là **grant rời** có thể cấp/thu hồi và audit. Policy layer phải đọc cả role **và** grant.

### AUD-34 — Reauthentication: phạm vi thao tác và thời hạn
`Type: GAP | Gate: MAJOR | DEC-34`

- **Trích dẫn:** Nhóm 01: "Có cơ chế xác thực lại cho thao tác nhạy cảm". Nhóm 10: "Thao tác nhạy cảm cần reauthentication". Nhóm 02: System Admin chuyển Owner khẩn cấp "khi có xác thực lại". Nhóm 10: "Thay đổi quyền/cấu hình có xác thực lại".
- **Vấn đề:** Chưa liệt kê **danh sách thao tác nhạy cảm** và thời hạn hiệu lực của lần reauth.
- **Đề xuất:** Danh sách thao tác nhạy cảm V1 (mã hoá thành `SensitiveAction` enum): đổi mật khẩu, đổi email, System Admin emergency owner transfer, cấp/thu hồi `ACCOUNT_APPROVE_GLOBAL`, đổi cấu hình toàn cục, xoá/khôi phục tài khoản, suspend/unsuspend, quản trị Ollama, đổi retention/backup, purge audit, dùng mã khôi phục offline, disable 2FA (nếu có sau này). Thời hạn reauth: **5 phút**, single-use cho đúng action đó (`reauth_token` hash, gắn với action + target). Reauth **không** tạo phiên mới (giữ "một phiên").

### AUD-35 — Định nghĩa "ACTIVE hợp lệ" lặp lại nhưng chưa có một predicate duy nhất
`Type: GAP | Gate: MAJOR | DEC-35`

- **Trích dẫn:** Xuất hiện ≥8 lần: "Manager ACTIVE hợp lệ", "thành viên ACTIVE hợp lệ", "approver còn hợp lệ", "chuyển Owner … thành viên ACTIVE hợp lệ".
- **Vấn đề:** "hợp lệ" chưa được định nghĩa một lần. Mỗi chỗ hiểu khác nhau sẽ sinh lỗi phân quyền.
- **Đề xuất:** Định nghĩa **một** predicate `isValidActiveActor(user, workspace, project)`:
  `user.status = ACTIVE` AND `user.locked_until` đã hết AND session hiện tại còn hiệu lực AND `workspace.status = ACTIVE` AND (nếu xét ở phạm vi Project) `workspace_membership.status = ACTIVE AND deleted_at IS NULL AND role <> 'GUEST'` AND `project.status NOT IN ('DELETED')` AND `project_membership.status = ACTIVE AND deleted_at IS NULL`.
  Predicate này cài đặt **một lần** trong Authorization Policy Layer (ARCH-06) và dùng lại ở mọi use case, job, realtime, file gateway.

### AUD-36 — "Quyền đọc audit đặc biệt theo scope" chưa có mô hình
`Type: GAP | Gate: MAJOR | DEC-36`

- **Trích dẫn:** Nhóm 09: "quyền đọc audit đặc biệt theo scope, System Admin không mặc nhiên đọc nội dung riêng tư". Nhóm 10: "Audit read đặc biệt theo scope".
- **Đề xuất:** Mỗi `audit_event` mang `scope_type` ∈ {SYSTEM, WORKSPACE, PROJECT, TASK, USER} + `scope_id` + `sensitivity` ∈ {NORMAL, SENSITIVE_CONTENT}. Quyền đọc = grant `AUDIT_READ` trên đúng scope, **không** suy ra từ role. Audit chứa before/after của nội dung riêng tư được đánh dấu `SENSITIVE_CONTENT` và chỉ đọc được bởi người **có quyền đọc chính đối tượng đó tại thời điểm truy vấn** (đánh giá động, không lưu grant tĩnh). System Admin chỉ đọc được audit `scope_type = SYSTEM` và `sensitivity = NORMAL` mặc định.

### AUD-37 — Một Task nhiều assignee: số liệu "unique task" vs "assignment count"
`Type: GAP | Gate: MINOR | DEC-37`

- **Trích dẫn:** Nhóm 08: "tách **unique Task count** và **assignment count**".
- **Đề xuất:** Chuẩn hoá trong metric dictionary: `unique_task_count = COUNT(DISTINCT task_id)`, `assignment_count = COUNT(*) FROM task_assignment`. Mọi API báo cáo trả **cả hai** kèm tên trường rõ ràng; dashboard phải hiển thị nhãn phân biệt. Không bao giờ trả một con số "số task" mơ hồ.

### AUD-38 — Phân biệt `0`, `N/A`, không có quyền, thiếu dữ liệu
`Type: GAP | Gate: MAJOR | DEC-38`

- **Trích dẫn:** Nhóm 08: "phân biệt `0`, `N/A`, không có quyền và thiếu dữ liệu; không tiết lộ số liệu của đối tượng ẩn".
- **Đề xuất:** Mọi số liệu trong response dùng envelope:
  `{ "value": <number|null>, "state": "OK" | "NOT_APPLICABLE" | "NO_PERMISSION" | "INSUFFICIENT_DATA" | "PARTIAL" }`.
  `PARTIAL` dùng khi user chỉ thấy một phần scope (ví dụ Workspace có 10 Project nhưng user chỉ tham gia 3) — **không** tiết lộ tổng số Project ẩn, chỉ nói "dữ liệu một phần". Quy ước này phải áp dụng nhất quán ở Web và Flutter.

### AUD-39 — AI: ngữ cảnh tối thiểu nhưng vẫn phải hữu ích
`Type: RISK | Gate: MAJOR | DEC-39`

- **Trích dẫn:** Nhóm 07: "Context gửi cho AI phải tối thiểu, đã lọc quyền và giảm dữ liệu nhạy cảm"; "gợi ý … assignee dựa trên quyền và workload"; "AI history có kiểm soát quyền".
- **Vấn đề:** "Tối thiểu" chưa định lượng; nếu cắt quá tay AI vô dụng, nếu rộng quá thì rò rỉ.
- **Đề xuất:** Định nghĩa **Context Manifest** khai báo cho từng tính năng AI: danh sách trường được phép, giới hạn số bản ghi, giới hạn ký tự, và mức redaction. Ví dụ `TASK_DRAFT` context = {project title, milestone titles, danh sách participant (tên + workload summary dạng số, không kèm nội dung task), 5 task title gần nhất **cùng Project**}. Cấm đưa vào context: nội dung comment, nội dung file, task của Project khác, thông tin tài khoản (email/phone). Manifest được audit và review như code.

### AUD-40 — Prompt injection từ file/comment
`Type: RISK | Gate: MAJOR | DEC-40`

- **Trích dẫn:** Nhóm 07: "file/comment là input không đáng tin, phải phòng prompt injection".
- **Đề xuất:** (a) Mọi nội dung không đáng tin được bọc trong delimiter + khai báo rõ "đây là dữ liệu, không phải chỉ thị"; (b) **structured output** (JSON schema) bắt buộc, validate bằng schema, từ chối output không hợp lệ; (c) không bao giờ cho output của AI đi thẳng vào mutation — luôn qua bước "người dùng xác nhận" rồi gọi use case chuẩn có kiểm tra quyền (đúng Nhóm 07); (d) chặn các trường đề xuất vượt phạm vi scope của context đã cấp (ví dụ AI đề xuất assignee không nằm trong danh sách participant → loại bỏ + ghi log); (e) giới hạn độ dài input/output, timeout, concurrency.

### AUD-41 — Ollama trên máy hiện tại không đáp ứng được trải nghiệm V1
`Type: RISK | Gate: BLOCKER | DEC-41`

- **Căn cứ đo được:** GPU Intel Iris Xe ~1 GB VRAM, 15.7 GB RAM, 8 CPU, Ollama **chưa cài**.
- **Vấn đề:** Với CPU-only, model 7B quantized Q4 cho prompt ~2–4k token context sẽ mất **hàng chục giây đến vài phút** cho mỗi lần sinh; Daily Planner/Weekly Review (ngữ cảnh lớn) có thể **vài phút đến timeout**. NFR-11 cho phép async + timeout nên **không phá vỡ kiến trúc**, nhưng NFR-21 yêu cầu "AI có chỉ tiêu riêng" thì phải công bố chỉ tiêu thực tế đo được, không thể hứa.
- **Đề xuất:** Chọn 1 trong 3 phương án, **cần người dùng quyết định trước Phase 5**:
  - **P-A (khuyến nghị cho pilot):** có **một máy chủ GPU trong LAN** chạy Ollama (đúng Nhóm 07 "Ollama Local/LAN"), Backend gọi qua LAN. Máy dev hiện tại chỉ dùng để code + test với mock Ollama.
  - **P-B:** chấp nhận **CPU-only với model nhỏ** (≤3B, quantized Q4), concurrency = 1, timeout 120s, giới hạn context ~2k token, và **ghi rõ trong tài liệu nghiệm thu** rằng chỉ tiêu AI là "chức năng đúng + privacy đúng", không cam kết độ trễ.
  - **P-C:** hoãn Nhóm 07 sang giai đoạn sau, làm Phase 1–4 + 6 + 7 trước. Đặc tả mục 21 đã tách Phase 8 (Advanced AI) nhưng Phase 5 (AI Basic) vẫn nằm trong V1 → **hoãn Phase 5 là thay đổi phạm vi V1, bắt buộc phải được người dùng phê duyệt**, không được tự quyết.

### AUD-42 — NFR-09/NFR-10 không thể nghiệm thu trên máy hiện tại
`Type: RISK | Gate: BLOCKER | DEC-42`

- **Căn cứ:** 15.7 GB RAM / 8 CPU / D: còn 28 GB; chính đặc tả mục 18.2 đã nói 16 GB "không mặc định đạt 50–100 concurrent users cùng AI"; mục 12 nói đây là **mục tiêu**, không phải kết quả đã đạt.
- **Đề xuất:** Chia nghiệm thu NFR thành 2 bậc, ghi rõ trong kế hoạch:
  - **Bậc 1 — trên máy dev hiện tại (có thể làm ngay):** benchmark **tham chiếu** với 10–20 concurrent users, dataset công bố, đo P50/P95/P99 cho các endpoint chính; kết quả ghi là "baseline trên phần cứng X", **không** tuyên bố đạt NFR-10.
  - **Bậc 2 — nghiệm thu NFR-09/10:** cần máy chủ/VM riêng (đề xuất tối thiểu 8 vCPU / 32 GB RAM / SSD NVMe, PostgreSQL + Backend + MinIO tách tiến trình, và **không** chạy Ollama trên cùng máy khi load test). Không có hạ tầng này thì **không được tuyên bố đạt**.

### AUD-43 — Chưa có quyết định về công cụ build/migration/queue/storage/realtime/token
`Type: GAP | Gate: BLOCKER | DEC-43`

- **Trích dẫn:** Mục 22.1 liệt kê chính xác những điểm chưa được phép tự suy diễn.
- **Đề xuất (tất cả đều CHỜ XÁC NHẬN, xem `08-DECISION-REGISTER.md`):**

| Hạng mục | Phương án khuyến nghị | Lý do bám baseline |
|---|---|---|
| Build tool Backend | **Maven** + Maven Wrapper | Java 21 có sẵn; Maven ổn định cho modular monolith nhiều module; chưa cài Maven → cần bước cài |
| Migration | **Flyway**, versioned, có undo-script thủ công | NFR-18 "migration/rollback kiểm soát" |
| Job queue | **PostgreSQL-backed queue** (`SKIP LOCKED`) + Outbox cùng DB | ARCH-04/05; NFR-01 một máy chủ; tránh thêm hạ tầng |
| Object Storage | **MinIO** (S3-compatible, versioning, private) chạy Docker/VM riêng | NFR-07, ARCH-07 |
| Realtime | **WebSocket** (1 kết nối/phiên) + resync cursor; fallback polling | ARCH-09; Flutter hỗ trợ WebSocket tốt hơn SSE |
| Token/Session | **Opaque session token** lưu server-side + access token ngắn hạn gắn `session_id` | "Một phiên hoạt động" + "thu hồi có hiệu lực ngay" (Nhóm 10) |
| PK | **UUIDv7** | Không lộ số lượng, index thân thiện theo thời gian |
| API versioning | Prefix `/api/v1` | Ổn định contract cho 2 client |

### AUD-44 — Thiếu đặc tả UX/UI, i18n, chuẩn hiển thị
`Type: GAP | Gate: MAJOR | DEC-44`

- **Trích dẫn:** Mục 22.9: "UX/UI specification — sitemap Web/Mobile, màn hình theo role, offline states, error/retry UX" — CHƯA FROZEN.
- **Vấn đề:** Không thể build Web/Mobile mà chưa có sitemap + wireframe. Cũng chưa chốt ngôn ngữ UI (tiếng Việt / song ngữ), định dạng ngày (`dd/MM/yyyy` vs ISO), định dạng giờ theo timezone, định dạng số.
- **Đề xuất:** Thêm một deliverable **trước** Phase 1 cho phần client: `docs/09-UX-SITEMAP.md` (sitemap Web/Mobile, danh sách màn hình theo role, empty/loading/error/offline states). Chuẩn hiển thị đề xuất: UI **song ngữ vi-VN (mặc định) + en**, ngày `dd/MM/yyyy`, giờ `HH:mm` theo timezone người xem, lưu trữ luôn ISO-8601 UTC. Cần người dùng xác nhận.

### AUD-45 — Chưa có quy ước về data lifecycle / retention
`Type: GAP | Gate: MAJOR | DEC-45`

- **Trích dẫn:** Mục 22.5: "Data lifecycle: retention, xóa mềm/cứng, GDPR/quyền riêng tư … backup retention" — CHƯA FROZEN.
- **Đề xuất V1:** soft-delete là mặc định cho mọi entity nghiệp vụ; **không có hard-delete tự động** ở V1; bảng retention policy cấu hình được (`retention_policy`) nhưng mặc định `keep_forever`; object storage file bị soft-delete vẫn giữ binary (versioning) và chỉ dọn khi có lệnh purge được reauth + audit; backup retention đề xuất 7 bản daily / 4 bản weekly / 3 bản monthly, **bản sao độc lập** ngoài máy chủ chính (NFR-15, 23).

### AUD-46 — Push notification cần nhà cung cấp và tài khoản trả phí (iOS)
`Type: RISK | Gate: BLOCKER | DEC-46`

- **Trích dẫn:** Nhóm 06: "In-app realtime, **Mobile push**, Email"; NFR-05 Flutter Android/iOS.
- **Vấn đề:** Android push cần **Firebase/FCM project**; iOS push cần **Apple Developer Program (99 USD/năm)** + APNs key. Email cần **SMTP server/relay**. Đây là **prerequisite bên ngoài**, không thể tự giải quyết bằng code, và không có trong đặc tả mục 22.1.
- **Đề xuất:** Ghi thành prerequisite của Phase 4. Nếu chưa có Apple Developer account → V1 mobile pilot chỉ chạy **Android + in-app notification**, iOS push đưa vào hạng mục chờ. Nếu chưa có SMTP → dùng mail server local (MailHog cho Dev/Test) và **không** tuyên bố email hoạt động ở Production.

### AUD-47 — "Không cho AI tự approve/complete/cancel" cần cơ chế kỹ thuật, không chỉ quy ước
`Type: GAP | Gate: MAJOR | DEC-47`

- **Trích dẫn:** Nhóm 07: "Không tự approve, complete, cancel hoặc sửa Task". Quy tắc người dùng: "Không cho AI tự approve, complete hoặc cancel Task".
- **Đề xuất:** Ép bằng kiến trúc, không bằng comment: module AI **không được** depend vào use case mutation của module Workflow/Task. Chỉ expose `AiSuggestion` (DTO bất biến, `status = DRAFT`). Mutation chỉ xảy ra khi client gọi endpoint nghiệp vụ chuẩn với **actor là user thật** và có `Idempotency-Key`. Thêm **ArchUnit test** cấm `ai..` import `workflow..`/`task..application.command.*` → vi phạm sẽ fail build.

### AUD-48 — "Không để job nền sử dụng quyền đã bị thu hồi" cần mô hình uỷ quyền rõ ràng
`Type: GAP | Gate: BLOCKER | DEC-48`

- **Trích dẫn:** NFR-24: "Job có trạng thái, **reauthorize ở bước nhạy cảm và trước trả kết quả**; mất quyền hủy/ẩn, không retry trái phép, chống xử lý trùng". ARCH-05: "Retry idempotent, **quyền hiện tại**".
- **Vấn đề:** Chưa nói job lưu "quyền đã snapshot" hay "identity để tự đánh giá lại quyền". Nếu job lưu permission snapshot → vi phạm. Nếu job chạy dưới service account toàn quyền → vi phạm nặng hơn.
- **Đề xuất:** Job **chỉ lưu `principal_user_id` + `scope`**, **không bao giờ** lưu permission đã đánh giá. Tại mỗi bước nhạy cảm và trước khi giao kết quả, job gọi lại Policy Layer với `principal_user_id` để **đánh giá quyền tại thời điểm đó** (`AuthorizationContext.ofJob`). Nếu mất quyền → job chuyển `CANCELLED_REVOKED`, kết quả bị huỷ/ẩn, ghi audit, **không retry**. Mọi job mang `idempotency_key` + `dedup_key`; worker dùng lease (`locked_by`, `locked_until`) để chống xử lý trùng.

### AUD-49 — Outbox consumer nhận sự kiện lặp: ranh giới "idempotent" chưa cụ thể
`Type: GAP | Gate: MAJOR | DEC-49`

- **Trích dẫn:** Mục 13.3: "Outbox consumer có thể nhận sự kiện lặp; phải idempotent và đánh dấu trạng thái xử lý". Test 19.2: "Outbox delivery lặp không gửi lặp tác động nghiệp vụ".
- **Đề xuất:** Mỗi consumer khai báo `handler_key`; bảng `outbox_delivery(outbox_event_id, handler_key, status, delivered_at, attempt_count)` với **unique `(outbox_event_id, handler_key)`** → consumer đã xử lý thì insert conflict và bỏ qua. Notification gửi ra ngoài mang `dedup_token = hash(outbox_event_id, handler_key, recipient_id)` để nhà cung cấp push/email có thể khử trùng.

### AUD-50 — Thiếu định nghĩa "Project tham gia" (participation) cho mục đích quyền đọc
`Type: GAP | Gate: MAJOR | DEC-50`

- **Trích dẫn:** Nhóm 02: "quyền xem nội dung cần **Project participation** hoặc quyền đặc biệt được cấp rõ ràng". Nhóm 10: "Workspace Owner/Admin cũng không tự đọc Project chưa tham gia".
- **Vấn đề:** "Participation" và "quyền đặc biệt được cấp rõ ràng" chưa được mô hình hoá. Nếu không định nghĩa, sẽ có chỗ code suy diễn Owner được đọc.
- **Đề xuất:** `participation` = có dòng `project_membership(status=ACTIVE, deleted_at IS NULL)` **hoặc** là assignee của ≥1 Task trong Project **hoặc** là creator của Project/Task trong Project (cần chốt có tính creator hay không). "Quyền đặc biệt" = grant rời `PROJECT_CONTENT_READ` trong bảng `special_grant(scope=PROJECT, scope_id, grantee_user_id, granted_by, reason, expires_at)` + reauthentication khi cấp + audit; grant này **không** suy ra từ bất kỳ role nào. Đề xuất V1: **có** tính creator là participation (vì creator mặc định là Manager theo Nhóm 03).

### AUD-51 — Tuỳ chọn reminder/quiet hours bị định nghĩa ở hai bảng: hai nguồn sự thật
`Type: CONTRADICTION | Gate: MAJOR | DEC-52 | Phát hiện ở bước rà soát chéo ERD (không phải từ đặc tả)`

- **Trích dẫn:** Nhóm 01: "Hồ sơ gồm tên, username, email, avatar, điện thoại, chức danh, bio, **timezone**." Nhóm 06: "Reminder mặc định trước deadline 1 ngày và 1 giờ, **có thể cấu hình theo user**"; "Overdue: nhắc một lần khi quá hạn, **sau đó theo cấu hình**"; "User được **mute theo loại**… **Quiet hours theo timezone user**."
- **Vấn đề:** Ba lỗi chồng nhau trong bản draft ERD.
  1. Bảng `user_profile_preference` có tên trong danh mục 65 bảng (`02B §11`, dòng 9) và trong danh sách bảng MOD-01 sở hữu (`01 §6`) nhưng **không hề có định nghĩa cột** → MOD-01 sở hữu một bảng chưa được thiết kế.
  2. Khi bổ sung định nghĩa (`02A §2.9`), nó **trùng** với `notification_preference` (`02B §2.2`): cả hai đều có `quiet_hours_enabled` + cặp giờ quiet, và `notification_preference` còn có cả cột `timezone`. Hai nguồn sự thật cho cùng một dữ liệu, không có trigger đồng bộ → worker reminder sẽ đọc một trong hai và hành vi trở nên không xác định.
  3. Cột `notification_preference.timezone` **mâu thuẫn trực tiếp Nhóm 01** (timezone thuộc hồ sơ, một giá trị duy nhất) và **mâu thuẫn Nhóm 06** ("quiet hours theo timezone **user**" — số ít). Nó cũng cho phép tới N dòng `notification_preference` (một dòng mỗi `type`) mang N múi giờ khác nhau cho cùng một user.
  4. Cơ chế overdue bị đặc tả hai lần, hai kiểu dữ liệu khác nhau: `notification_preference.overdue_reminder_policy text CHECK IN ('ONCE','DAILY','WEEKLY','OFF')` và `user_profile_preference.overdue_reminder_interval_hours smallint CHECK 1..168`. Chưa có tài liệu nào nói cái nào thắng.
- **Hệ quả nếu không xử lý:** đây đúng loại lỗi mà đặc tả mục 20 xếp vào nhóm rủi ro "không nhất quán dữ liệu"; ở tầng nghiệp vụ nó làm reminder gửi sai giờ hoặc gửi hai lần, và làm Test 19.4 ("Pass timezone/reconnect/quiet hours") không có hành vi chuẩn để đối chiếu.
- **Đề xuất:** (a) `timezone` và `locale` **chỉ** ở `app_user`; xoá khỏi `notification_preference`. (b) Quiet hours là tuỳ chọn **cấp user** → ở `user_profile_preference`; `notification_preference` chỉ giữ `muted` + ba cờ kênh, đúng chữ "mute theo loại" của Nhóm 06. Bằng chứng ủng hộ cấp user: `system_setting` đã có **một** khoá `notification.quiet_hours_default_enabled` (`02B §7.1`), tức là một giá trị mặc định duy nhất cho toàn user, không phải một giá trị mỗi loại thông báo. (c) Overdue giữ **một** cột; đề xuất dùng enum `overdue_reminder_policy` vì khớp từ vựng Nhóm 06 ("nhắc một lần" → `ONCE`; "sau đó theo cấu hình" → `DAILY`/`WEEKLY`/`OFF`) và vì chặn được cấu hình spam mà dạng interval 1 giờ cho phép. **Cần người dùng xác nhận (a), (b) và chọn (c) enum hay interval** — xem DEC-52.

### AUD-52 — Trùng prefix ID: `T-nn` vừa là action RBAC vừa là transition state machine
`Type: CONTRADICTION | Gate: MINOR | DEC-53 | Phát hiện ở bước rà soát chéo ID`

- **Trích dẫn:** Đặc tả mục 23 yêu cầu truy vết được từ yêu cầu → thiết kế → test. Mục 16 và 17 **không** đặt tên cho các họ ID này; cả hai họ đều do phase thiết kế này tự đặt.
- **Vấn đề:** Hai tài liệu dùng cùng một prefix cho hai khái niệm khác nhau. `04-RBAC-MATRIX.md §7` đánh số action trên Task là `T-01..T-16` (`T-04` = `TASK_UPDATE_CONTENT`), còn `05-WORKFLOW-STATE-MACHINES.md §1.1` đánh số transition là `T-01..T-12` (`T-04` = `PENDING_APPROVAL → COMPLETED`). Xung đột này **đã** lan sang tài liệu hạ nguồn: `07-TEST-PLAN.md` dòng 132 liệt kê `T-13` trong nhóm "mã action RBAC" và `T-05` trong nhóm "mã transition" **trong cùng một câu**; `03-API-CONTRACT.md` dòng 8 khai báo `T-*` là tên bắt buộc dùng lại từ **cả hai** file. Một ID mà phải đoán nghĩa theo ngữ cảnh thì không dùng làm khoá truy vết được, và khi sinh `@RequiresPermission` từ mã action sẽ dễ ánh xạ sai quyền.
- **Đã sửa (không cần chờ quyết định, vì đây là lỗi đặt tên của chính bản draft):** đổi họ action Task trong `04 §7` thành **`TK-01..TK-16`**, sau đó **rút còn `TK-01..TK-14`** khi AUD-53b xoá hai mã trùng thao tác (`T-15 TASK_READ_TIMELINE` = `FL-08`, `T-16 TASK_EXPORT_ICS` = `C-06`). Phạm vi đã áp dụng: 26 chỗ trong `04`, 3 trích dẫn "RBAC T-nn" trong `05 §1.1`/`§12`, 1 dãy trong `08 Phần A`. Từ nay **`TK-nn` = action RBAC trên Task** (dãy hợp lệ **01..14**), **`T-nn` = transition state machine** (dãy hợp lệ **01..12**), đã ghi quy ước thành chú thích đầu `04 §7` và `05 §1.1` và thành hàng riêng trong bảng tra cứu `07 §2.7`. Chọn đổi họ của `04` thay vì của `05` vì rẻ hơn nhiều: `T-01..T-12` (transition) đã được `03`, `06` và `07` dùng làm khoá trace ở hàng chục chỗ, trong khi họ action của `04` chỉ được trích dẫn chéo ở 4 dòng.
- **Đã sửa xong ở hai file hạ nguồn (cập nhật 11/10/2026, trước đây ghi là "còn phải sửa"):** `03-API-CONTRACT.md` dòng 8 (`T-*` → `TK-*` trong danh sách họ action của `04`) và dòng 295 (`(T-14)` → `(TK-14)`) — cùng 35 chỗ `TK-nn` trong backtick và 4 chỗ `T-15` → `TK-03`; `07-TEST-PLAN.md` dòng 3 (danh sách action `TK-xx`), dòng 132 (`T-13` → `TK-13`), dòng 236 ("theo `TK-09` trong `04 §7`"). Quét lại bằng script: **không còn** `T-13..T-16` ở `03`; 22 ký hiệu `T-nn` còn lại trong `07` đều là transition hợp lệ của `05`. **DEC-53** chỉ để bạn phủ quyết việc đổi tên này nếu bạn muốn giữ `T-*` cho action và đổi họ transition sang `TR-*` thay vào đó.

### AUD-53 — Ma trận RBAC thiếu toàn bộ action cho Comment, và có hai mã trùng thao tác
`Type: GAP + CONTRADICTION | Gate: MAJOR | DEC-54 | Phát hiện ở bước rà soát chéo 04 ↔ 03 ↔ 01 ↔ 02B`

- **Trích dẫn:** Nhóm 04: "Task/Subtask một cấp, nhiều assignee, dependency cảnh báo, checklist, **comment**, attachment, time log"; "`PENDING_APPROVAL` khóa nội dung thực thi Task; không được vòng qua bằng sửa **comment**/file hoặc API khác". Nhóm 09: "Activity Timeline ghi chỉnh sửa, assignment, trạng thái, deadline, **comment**, file, approval". Đặc tả mục 16: mọi hành động phải qua pipeline authorization.
- **Vấn đề (a) — thiếu:** `04-RBAC-MATRIX.md` **không có một mã action nào cho comment**. Các họ đã có là `A-*`, `W-*`, `P-*`, `TK-*`, `F-*`, `FL-*`, `C-*`, `N-*`, `AI-*`, `R-*`, `S-*`; không họ nào phủ thao tác tạo/sửa/xoá comment, trong khi `/comments` đã là resource chính thức (`03 §1.1`), `comment` là bảng MOD-06 sở hữu (`02B §1.1`), và `01 §6` cấp quyền đọc bảng đó cho MOD-04/07/10/11. Hệ quả trực tiếp: bước **P5** của pipeline không có `action_code` để kiểm tra, nên endpoint comment chỉ còn được chặn bởi P3/P4 (quyền đọc) — nghĩa là **bất kỳ ai đọc được Task thì ghi được comment**, kể cả Project Guest vốn là vai trò chỉ-xem. Đây đúng là loại lỗ hổng mà quy tắc "Không bỏ qua kiểm tra quyền tại Backend" của người dùng cấm.
- **Vấn đề (b) — trùng:** hai mã `T-15 TASK_READ_TIMELINE` và `T-16 TASK_EXPORT_ICS` của họ action Task (sau AUD-52 là `TK-15`/`TK-16`) mô tả lại **đúng** hai thao tác đã có mã riêng: `FL-08 ACTIVITY_TIMELINE_READ` (`04 §9`) và `C-06 ICS_EXPORT` (`04 §10`), với cùng điều kiện và cùng side effect (`redaction_level = MINIMAL`, ghi `calendar_export_record`). Hai mã cho một thao tác làm `@RequiresPermission` mơ hồ và làm test âm phủ trùng.
- **Đã sửa:** (b) **xoá hẳn** hai mã đó (không đổi tên, vì thao tác đã có mã đúng chỗ); họ action Task vì vậy dừng ở **`TK-01..TK-14`**, kèm chú thích ở `04 §7` trỏ sang `FL-08`/`C-06`. (a) thêm họ mới **`CM-01..CM-05`** ở `04 §7.1` (`COMMENT_CREATE`, `COMMENT_REPLY`, `COMMENT_EDIT_OWN`, `COMMENT_DELETE_OWN`, `COMMENT_DELETE_OTHER`) + thêm prefix `COMMENT_*`/`ACTIVITY_*` vào bảng ánh xạ `04 §13` + bổ sung bất biến (4)(5) cho MOD-06 ở `01 §2`. Đọc comment **không** có mã riêng — đi kèm `TK-03 TASK_READ`.
- **Điểm đặc tả im lặng → cần bạn chốt (DEC-54):** (i) Project Guest có được comment không — đề xuất **không**, vì Nhóm 03 định nghĩa Guest là chỉ-xem; (ii) có cho phép **sửa** comment của chính mình không, và có giới hạn cửa sổ sửa không — đặc tả chỉ nói timeline phải ghi nhận chỉnh sửa, ngụ ý là có sửa được; (iii) ai được **xoá comment của người khác** — đề xuất chỉ PM kèm `reason` bắt buộc và audit mức `SENSITIVE_CONTENT`, còn WO/WA/SA không có quyền này trên Project họ không tham gia; (iv) comment trong lúc Task `PENDING_APPROVAL` — AUD-06 đã kết luận **cho phép** (comment không phải "nội dung thực thi") nhưng vẫn treo theo DEC-06.
- **Hệ quả nếu bỏ qua:** Test 19.2 ("không thể vòng qua bằng comment") sẽ không có mã quyền để đối chiếu, và kịch bản Guest ghi comment sẽ lọt qua review vì không có dòng ma trận nào nói Guest bị cấm.

---

### AUD-54 — Hai mã "action không tồn tại" lại nằm trong cột Action của ma trận
`Type: PRESENTATION | Gate: MINOR | Không sinh DEC mới | ĐÃ SỬA | Nguồn: X-04 của 07 §8.2`

- **Vấn đề:** `04 §10` liệt kê `R-07 PRODUCTIVITY_RANKING_INDIVIDUAL` và `04 §11` liệt kê `S-09 SELF_ESCALATION` ngay trong cột **Action**, với "Ai được = — không ai —" và ghi chú "action này không tồn tại trong hệ thống". Một mã vừa xuất hiện trong bảng action vừa được tuyên bố là không tồn tại rất dễ bị triển khai hiểu thành "action hợp lệ nhưng chưa gán quyền" → đưa vào enum `permission_code` hoặc vào một `@RequiresPermission` chết.
- **Đã sửa:** xoá hai dòng khỏi bảng action; gom vào mục mới **`04 §10.1` "Hai mã KHÔNG phải action — giữ ID chỉ để trace"** kèm cột "Hệ quả bắt buộc khi triển khai". **Giữ nguyên số hiệu R-07/S-09** vì `03`, `06 §PH-6` và `07 §1.1` đang trích dẫn. Không phải sửa DB: CHECK `permission_code` ở `02A §2.7` (grant hệ thống) và `§4.4` (`special_grant`) vốn **không** chứa hai mã này.
- **Kiểm chứng:** `07 §1.1` yêu cầu cả hai phải có **test âm** (TC-F-036 bước 4, TC-WEB-009 bước 2) — đúng nguyên tắc DENY-by-default hai chiều.

### AUD-55 — Phạm vi approver: predicate cho phép MEMBER, nhưng designation mặc định chỉ cấp cho PM
`Type: AMBIGUITY | Gate: MINOR | Không sinh DEC mới — làm rõ DEC-07 | ĐÃ SỬA | Nguồn: X-08 của 07 §8.2`

- **Trích dẫn:** Nhóm 05: "Manager có thể chỉ định người duyệt"; AUD-07/DEC-07.
- **Vấn đề:** `04 §8.1` `isEligibleApprover` chấp nhận `role IN ('MANAGER','MEMBER')`, trong khi `04` F-03 và `05 §1.1` T-02 nói designation **mặc định** chỉ tạo cho "mọi PM đủ điều kiện". Đọc rời hai chỗ thì tưởng mâu thuẫn về việc một PR (MEMBER) có được làm approver hay không, và `07` buộc phải tự đặt fixture `FX-U6`/`FX-U7` là PR có designation tường minh.
- **Đã sửa:** thêm vào `04 §8.1` một bảng tách **hai đường vào `approval_designation`**: (i) **mặc định khi T-02 chạy → chỉ PM**, không phụ thuộc DEC-07; (ii) **chỉ định thủ công → theo DEC-07**, phương án (a) `role IN ('MANAGER','MEMBER')`, phương án (b) `role = 'MANAGER'`. Kèm chỉ dẫn đúng một chỗ phải sửa nếu chốt (b) (thu hẹp clause + đổi fixture + cập nhật TC-R01/TC-I13/TC-W-008/TC-W-022) và test âm phải thêm nếu chốt (a) (tự chỉ định mình).

### AUD-56 — `05 §12` trỏ sai race khi nói về hệ quả của DEC-02
`Type: CROSS-REF | Gate: MINOR | DEC-02 (đã có, BLOCKER) | ĐÃ SỬA | Nguồn: X-03 của 07 §8.2`

- **Vấn đề:** dòng DEC-02 của `05 §12` viết "nếu không chặn → … phải bỏ `PROJECT_READONLY` khỏi **RACE-03**", nhưng `RACE-03` (`05 §10`) là race giữa submit và Project **COMPLETED** (cascade PJ-05), hoàn toàn độc lập với ON_HOLD. Trích dẫn chéo sai đối tượng sẽ làm người triển khai sửa nhầm cơ chế chống race.
- **Đã sửa:** thay bằng danh sách **ba** chỗ thật sự phải sửa nếu DEC-02 chốt "ON_HOLD không chặn workflow" — (1) `04 §3` dòng `ON_HOLD_READONLY`, (2) P6 ngừng từ chối T-02..T-07, (3) mã lỗi `PROJECT_READONLY` không còn trả cho workflow transition — và khẳng định rõ **RACE-03 không bị ảnh hưởng**.

### AUD-57 — "escalation" của Approval SLA chưa được định nghĩa, có thể xung đột với I-20
`Type: AMBIGUITY | Gate: MINOR | DEC-08 (đã có, MAJOR) | ĐÃ SỬA theo nghĩa hẹp | Nguồn: X-06 của 07 §8.2`

- **Vấn đề:** `02A §4.5` mô tả `project_setting.approval_sla_hours` là "**chỉ** dùng cho notify/**escalation**", trong khi `04 §8` F-10 giới hạn `SYS` ở "chỉ set `sla_breached = true` + notify; **không bao giờ** tạo `approval_decision`" và `02A §9` I-20 có trigger `guard_no_auto_approve`. Nếu hiểu "escalation" là tự động chuyển người duyệt hoặc tự động duyệt thì **xung đột trực tiếp** với cả hai, và vi phạm quy tắc "Không cho AI/hệ thống tự approve" của người dùng.
- **Đã sửa:** `02A §4.5` nay ghi rõ "chỉ dùng để set cờ `sla_breached` rồi notify/escalate **cho con người** — không bao giờ sinh quyết định tự động nào trên Task", kèm trỏ tới F-10 và I-20. **DEC-08 vẫn mở:** nếu bạn chốt có escalation thật (tự đổi approver), phải thêm transition, nới I-20 và mở một họ test mới.

### AUD-58 — "đổi setting" trong RACE-12 dễ dẫn tới đặt nhầm cột `approval_enabled`
`Type: CROSS-REF | Gate: MINOR | Không sinh DEC mới | ĐÃ SỬA | Nguồn: X-09 của 07 §8.2`

- **Vấn đề:** `02A §4.1` đặt `approval_enabled` (kèm `approval_enabled_changed_at/by/reason`) trên bảng **`project`**, còn `project_setting` (`02A §4.5`) **không** có cột này; nhưng `05 §10` RACE-12 lại mô tả thao tác là "`FOR UPDATE` project khi đổi **setting**". Cách dùng từ này khiến triển khai có thể đặt cột vào `project_setting` và làm test mức `DB` của TC-I22/TC-F-024 trỏ sai bảng.
- **Đã sửa:** RACE-12 nay nêu đích danh "`FOR UPDATE` trên **dòng `project`** khi đổi `project.approval_enabled` (cột nằm ở `02A §4.1`, **không** phải `project_setting`)".

### AUD-59 — Ba phát hiện chéo còn lại không cần sửa tài liệu thiết kế
`Type: NOTE | Gate: không gate | Không sinh DEC mới | Nguồn: X-01, X-02, X-05, X-07 của 07 §8.2`

| X | Nội dung | Vì sao **không** sửa tài liệu | Đã được theo dõi ở đâu |
|---|---|---|---|
| X-01 | Đặc tả §19.3 bắt buộc load test 50–100 user, §18.2 nói máy 16 GB không mặc định đạt | Đây là **mâu thuẫn nội tại của baseline FROZEN**, phase này không được tự sửa yêu cầu | AUD-42, DEC-42, `07 §4.2`(a) |
| X-02 | Yêu cầu nêu 6 bất biến không enforce được, `02A §9` đánh dấu 11 "N" + 3 "một phần" | `02A §9` là bản **đầy đủ hơn**; 6 bất biến của đề bài là mức tối thiểu, không phải giới hạn | `07 §1.6` lấy cả 11 + 3 làm phạm vi test âm |
| X-05 | Ranh giới "xem" vs "thay đổi" khi offline chưa định nghĩa | Đặc tả im lặng → thuộc DEC-12 (BLOCKER), không được tự quyết | DEC-12, `07` TC-M-004/005/006/009 viết theo hướng chặt |
| X-07 | Docker/Compose đã cài nhưng daemon tắt; `psql` NOT FOUND → không chạy được test cần DB thật | Là **ràng buộc môi trường**, không phải lỗi tài liệu | AUD-41, `07 §4.3` PR-05, bước "chuẩn bị môi trường" trước PH-1 |

> **Kết luận của nhóm phát hiện này:** AUD-54..AUD-58 **không sinh DEC mới** — chúng hoặc là lỗi trình bày/trích dẫn chéo đã sửa ngay (AUD-54, 56, 58), hoặc là điểm mơ hồ đã được làm rõ và vẫn treo theo một DEC đã có (AUD-55 → DEC-07, AUD-57 → DEC-08). Vì vậy tổng số DEC cần bạn xác nhận vẫn là **54**.

---

## E. Bất biến mục 15.2 — khả năng hiện thực hoá bằng schema

**Chú giải ký hiệu:** `Y` = ép được bằng constraint/index; `Y (một phần)` = constraint giữ được một nửa điều kiện, phần còn lại phải ép ở service layer; `N` = không thể ép bằng constraint thuần, bắt buộc transaction + test âm.

| Bất biến (trích đặc tả) | Ép bằng DB constraint? | Cơ chế đề xuất |
|---|---|---|
| Mỗi ACTIVE account đúng một Personal Workspace | Y (một phần) | Partial unique index trên `workspace.personal_owner_user_id` (AUD-30). Phần "ACTIVE" phải ép ở service layer |
| Team Workspace đúng một Owner | Y | Partial unique index `workspace_membership(workspace_id) WHERE role='OWNER' AND status='ACTIVE' AND deleted_at IS NULL` (AUD-28) |
| Project chưa DELETED có ≥1 Manager ACTIVE | N | Transaction + lock Project + kiểm tra trước khi commit; test âm bắt buộc (AUD-03) |
| Membership Project gắn người thuộc Workspace ACTIVE | Y (một phần) | FK + trigger kiểm tra chéo; trạng thái ACTIVE xét ở service layer |
| Workspace Guest không thành Manager | Y | CHECK constraint hoặc trigger trên `project_membership` kết hợp `workspace_membership.role` (cần trigger vì cross-table) |
| Assignee/submitter không tự approve | N | Service layer trong transaction approve; unique index chỉ bảo đảm "một terminal decision" |
| Subtask tối đa một cấp | Y | CHECK `(parent_task_id IS NULL) OR (parent của nó có parent_task_id IS NULL)` → cần trigger; hoặc cột `depth` + CHECK `depth IN (0,1)` |
| Parent completion đòi mọi child COMPLETED/CANCELLED | N | Transaction submit + đếm child chưa xong; test âm bắt buộc |
| Dependency không chu trình | N | Kiểm tra đồ thị trong transaction (`WITH RECURSIVE`); hard-block (AUD-09) |
| Mỗi ApprovalRound ≤1 quyết định terminal | Y | Partial unique index (AUD-27) |
| Soft-delete/restore không hồi sinh quyền | Y (một phần) | `delete_group_id` (AUD-29) + không restore membership tự động |
| File version không ghi đè | Y | `file_version.version_no` unique theo `(attachment_id, version_no)`; object key immutable |
| Audit không bị sửa khi reopen | Y | DB role application không có UPDATE/DELETE trên bảng audit (AUD-14) |

→ **Kết luận audit quan trọng:** trong 13 bất biến gốc của đặc tả, chỉ **6** ép được hoàn toàn bằng constraint, **3** ép được một phần và **4 không thể** ép bằng constraint thuần (≥1 Manager ACTIVE, cấm tự duyệt, parent completion, dependency không chu trình). Ở mức thiết kế chi tiết, `02A §9` mở rộng thành 23 bất biến (I-01..I-23): 9 ép hoàn toàn bằng constraint/index, 3 một phần, 11 không thể bằng constraint thuần (trong đó 16 có trigger giữ đầy đủ, 2 dùng constraint trigger DEFERRED) và **18/23 vẫn cần transaction/service logic** vì điều kiện phụ thuộc trạng thái runtime. Chúng bắt buộc phải có (a) transaction design rõ ràng, (b) test âm trong CI. Đây là lý do `05-WORKFLOW-STATE-MACHINES.md` và `07-TEST-PLAN.md` là deliverable bắt buộc của Phase 0, không phải tài liệu phụ.

---

## F. Tổng hợp câu hỏi cần người dùng xác nhận trước khi đóng Phase 0

| Mức | ID | Câu hỏi tóm tắt | Nếu không chốt thì chặn gì |
|---|---|---|---|
| BLOCKER | DEC-01 | Actor + transaction của cascade huỷ khi Project COMPLETED | ERD audit, workflow, Phase 2 |
| BLOCKER | DEC-02 | "readonly" của ON_HOLD/ARCHIVED có chặn workflow không | Ma trận RBAC, Phase 2 |
| BLOCKER | DEC-03 | Chặn cứng việc rời Workspace của Manager cuối? | Phase 1 (Workspace) |
| BLOCKER | DEC-05 | Subtask có vòng nghiệm thu riêng không | ERD workflow, Phase 2 |
| BLOCKER | DEC-06 | Danh sách chính xác nội dung bị khoá ở PENDING_APPROVAL | Phase 2, Phase 3 (Files) |
| BLOCKER | DEC-12 | Allowlist offline cache của Mobile | Kiến trúc Flutter, Phase 1 mobile |
| BLOCKER | DEC-15 | Cơ chế số liệu lịch sử (event / snapshot / cả hai) | ERD reports, Phase 6 |
| BLOCKER | DEC-27 | Cơ chế chống race approval | Phase 2 |
| BLOCKER | DEC-41 | Phương án hạ tầng Ollama (GPU LAN / CPU nhỏ / hoãn) | Phase 5, phạm vi V1 |
| BLOCKER | DEC-42 | Hạ tầng load test nghiệm thu NFR-09/10 | Phase 7 |
| BLOCKER | DEC-43 | Bộ công nghệ: build tool, migration, queue, storage, realtime, token, PK | Toàn bộ Phase 1 |
| BLOCKER | DEC-46 | FCM project / Apple Developer / SMTP có sẵn không | Phase 4 |
| BLOCKER | DEC-48 | Mô hình uỷ quyền cho background job | Phase 1 (nền job) |
| MAJOR | DEC-04 | Workspace Guest có được làm Project Member | Phase 1 |
| MAJOR | DEC-07 | Ai được làm Designated Approver | Phase 2 |
| MAJOR | DEC-08 | Approval SLA có sinh hành động tự động | Phase 2, 4 |
| MAJOR | DEC-09 | Phạm vi dependency (cùng Project? Subtask?) | Phase 2 |
| MAJOR | DEC-10 | Project trong Personal Workspace có participant | Phase 1 |
| MAJOR | DEC-11 | Luồng invite với tài khoản có sẵn | Phase 1 |
| MAJOR | DEC-13 | Payload push tối thiểu (chấp nhận residual risk) | Phase 4 |
| MAJOR | DEC-14 | Retention/disposal audit | Phase 1 |
| MAJOR | DEC-16 | Nơi lưu + TTL report export | Phase 6 |
| MAJOR | DEC-18 | Cơ chế bootstrap System Admin | Phase 1 |
| MAJOR | DEC-19 | UX một phiên / đa thiết bị | Phase 1, client |
| MAJOR | DEC-23 | Quy tắc time log | Phase 2 |
| MAJOR | DEC-26 | Chất lượng tìm kiếm tiếng Việt chấp nhận được | Phase 2 |
| MAJOR | DEC-28 | Cơ chế owner transfer nguyên tử | Phase 1 |
| MAJOR | DEC-29 | Cơ chế restore theo delete_group | Phase 1, 3 |
| MAJOR | DEC-30/31 | Personal workspace + giữ email/username | Phase 1 (ERD) |
| MAJOR | DEC-34 | Danh sách thao tác nhạy cảm + thời hạn reauth | Phase 1 |
| MAJOR | DEC-35 | Định nghĩa thống nhất "ACTIVE hợp lệ" | Phase 1 (policy layer) |
| MAJOR | DEC-36 | Mô hình quyền đọc audit theo scope | Phase 1, 7 |
| MAJOR | DEC-38 | Envelope trạng thái số liệu (0/N-A/không quyền) | Phase 6, client |
| MAJOR | DEC-39 | Context Manifest cho AI | Phase 5 |
| MAJOR | DEC-40 | Biện pháp chống prompt injection | Phase 5 |
| MAJOR | DEC-44 | UX sitemap + i18n + chuẩn hiển thị | Phase 1 client |
| MAJOR | DEC-45 | Data lifecycle / retention | Phase 7 |
| MAJOR | DEC-47 | Ràng buộc kiến trúc cấm AI mutate | Phase 5 |
| MAJOR | DEC-49 | Cơ chế idempotency outbox | Phase 1 |
| MAJOR | DEC-50 | Định nghĩa participation + special grant | Phase 1 |
| MAJOR | DEC-51 | Stream file đang chạy khi quyền bị thu hồi: cắt ngay hay cho hoàn tất | Phase 3 |
| MAJOR | DEC-52 | Chủ quản `timezone`/`locale`, phạm vi quiet hours, và kiểu dữ liệu cấu hình overdue | Phase 1 (ERD), 4 |
| MAJOR | DEC-54 | Quyền comment: Guest có được ghi, có cho sửa comment của mình, ai được xoá của người khác | Phase 2 (MOD-06) |
| MINOR | DEC-17 | Mật khẩu tạm vs mã khôi phục offline | Phase 1 |
| MINOR | DEC-20 | Làm tròn progress | Phase 6 |
| MINOR | DEC-21 | Milestone thủ công hay suy diễn | Phase 2 |
| MINOR | DEC-22 | Phạm vi Tag | Phase 2 |
| MINOR | DEC-24 | Người nhận reminder dùng chung | Phase 4 |
| MINOR | DEC-25 | Digest khi resume | Phase 4 |
| MINOR | DEC-32 | Ngưỡng lockout | Phase 1 |
| MINOR | DEC-33 | Mô hình grant quyền rời | Phase 1 |
| MINOR | DEC-37 | Metric unique task vs assignment | Phase 6 |
| MINOR | DEC-53 | Xác nhận việc đổi họ action Task `T-01..T-16` → **`TK-01..TK-14`** (đã áp dụng; hai mã `T-15`/`T-16` bị xoá vì trùng `FL-08`/`C-06` — AUD-53b) | Phase 1, trước khi sinh `@RequiresPermission` |

**Tổng cộng: 58 phát hiện audit (AUD-01..AUD-58) + 1 mục ghi chú gộp không gate (AUD-59) → 54 quyết định cần xác nhận (DEC-01..DEC-54): 13 BLOCKER, 31 MAJOR, 10 MINOR.**

Dòng `DEC-30/31` gộp hai quyết định liên quan (Personal Workspace và giữ email/username) nên số dòng bảng là 53 trong khi số ID là 54. Nguồn tổng hợp đầy đủ kèm phương án A/B/C và khuyến nghị nằm ở `08-DECISION-REGISTER.md` Phần C.

**Phân biệt nguồn gốc phát hiện:** AUD-01..AUD-50 là phát hiện **trên đặc tả FROZEN** (thiếu chi tiết thi hành hoặc mâu thuẫn nội tại của yêu cầu). AUD-51, AUD-52 và AUD-53 khác loại: chúng là phát hiện **trên chính bản draft do phase này soạn ra** (ERD, hệ thống ID, ma trận RBAC), tìm thấy khi rà soát chéo `01 §6` ↔ `02A` ↔ `02B §11`, đối chiếu prefix ID giữa `04` ↔ `05` ↔ `03` ↔ `07`, và đối chiếu resource của `03` ↔ họ action của `04`. **AUD-54..AUD-59** là lớp thứ ba: chúng đến từ bảng X-01..X-09 do bước lập `07-TEST-PLAN.md` phát hiện khi đối chiếu `04` ↔ `05` ↔ `02A` ↔ `03`, và **năm trong số đó đã được sửa ngay trong tài liệu thiết kế** (AUD-54 → `04 §10.1`; AUD-55 → `04 §8.1`; AUD-56 → `05 §12`; AUD-57 → `02A §4.5`; AUD-58 → `05 §10` RACE-12). Ghi chung vào một sổ để không mất vết, nhưng chúng **không** làm thay đổi kết luận ở mục G.1 về chất lượng baseline.

---

## G. Kết luận audit

1. **Baseline FROZEN đủ chặt để bắt đầu thiết kế chi tiết**, không có mâu thuẫn nào khiến phải viết lại yêu cầu. Các mâu thuẫn tìm thấy (AUD-01, 02, 08, 12, 14, 19) đều là **thiếu chi tiết thi hành**, không phải xung đột chủ trương.
2. **Không được tạo database, không được viết code** cho tới khi 13 DEC mức BLOCKER được xác nhận, đúng theo mục 15 của đặc tả ("Không nên tạo database trước khi hoàn tất các quyết định này").
3. **Rủi ro khả thi lớn nhất không nằm ở code mà ở hạ tầng:** máy hiện tại 15.7 GB RAM + Iris Xe 1 GB VRAM + Docker daemon tắt + chưa có Ollama + chưa có Maven. Ba hệ quả trực tiếp: (a) NFR-09/10 không thể nghiệm thu tại đây (AUD-42), (b) Nhóm 07 cần quyết định hạ tầng AI (AUD-41), (c) cần một bước "chuẩn bị môi trường" trước Phase 1.
4. **4/13 bất biến cốt lõi không thể ép bằng DB constraint thuần** (≥1 Manager ACTIVE, cấm tự duyệt, parent completion, dependency không chu trình); ở mức mô hình chi tiết 23 bất biến thì **18/23 vẫn cần transaction/service logic** → bắt buộc thiết kế transaction + test âm, nếu không hệ thống sẽ "chạy được" nhưng sai invariant.
5. Deliverable tiếp theo của Phase 0 đã được soạn song song: `01-MODULES-BOUNDARIES.md`, `02A-DATA-MODEL-CORE.md` + `02B-DATA-MODEL-PLATFORM.md`, `03-API-CONTRACT.md`, `04-RBAC-MATRIX.md`, `05-WORKFLOW-STATE-MACHINES.md`, `06-IMPLEMENTATION-PLAN.md`, `07-TEST-PLAN.md`, `08-DECISION-REGISTER.md`. Tất cả đều ở trạng thái **ĐỀ XUẤT**, chưa phải quyết định đã duyệt.
