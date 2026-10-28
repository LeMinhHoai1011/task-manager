# 08 — DECISION REGISTER: FROZEN / GIẢ ĐỊNH / CẦN XÁC NHẬN

**Mục đích:** tách bạch **ba loại nội dung** theo đúng yêu cầu của đặc tả (mục 1.2) và của người dùng:
- **Phần A — FROZEN:** quyết định bắt buộc, tôi **không** được sửa, không được thay thế.
- **Phần B — ASM (giả định kỹ thuật):** do tôi tự đặt ra để có thể thiết kế; **chưa được duyệt**; có thể bị bác.
- **Phần C — DEC (cần người dùng xác nhận):** câu hỏi mở, kèm phương án và khuyến nghị; **không tự quyết định thay người dùng**.

**Cách dùng:** điền câu trả lời vào **Phần D — Phiếu trả lời** ở cuối file. Với DEC mức BLOCKER, phase tương ứng **không được bắt đầu** cho tới khi có câu trả lời.


**Chú giải ký hiệu dùng trong các bảng:** `Y` = có / được phép / cơ chế giữ đầy đủ bất biến; `N` = không / không được phép / không thể dùng cơ chế đó; `!` = một phần (phần còn lại phải xử lý ở service layer hoặc đang chờ quyết định); `—` = không áp dụng.

---

## PHẦN A — BASELINE ĐÃ FROZEN (KHÔNG ĐƯỢC SỬA)

### A.1. 11 nhóm yêu cầu

| Nhóm | Đặc tả | Nội dung cốt lõi đã chốt | Nơi hiện thực hoá trong thiết kế |
|---|---|---|---|
| **01** Account & Auth | mục 2 | 6 trạng thái tài khoản; tự đăng ký có duyệt; **một phiên duy nhất**; mật khẩu tạm 24h / mã khôi phục offline; invite gắn email-hạn-một lần; soft-delete + giữ email; bootstrap local một lần | `02A §2` (app_user, user_session, recovery, grant), `04 §4` (A-01..A-20), `05 §6` |
| **02** Workspace | mục 3 | Personal duy nhất, không mời; Team **một Owner**; 4 role; ma trận mời; thu hồi quyền tức thời + gỡ assignment; owner transfer nguyên tử; 3 trạng thái | `02A §3`, `04 §5` (W-01..W-16), `05 §5` |
| **03** Project & Milestone | mục 4 | Nhiều Manager, không Project Owner; ≥1 Manager ACTIVE; 6 trạng thái; COMPLETED **cascade huỷ**; COMPLETED→ACTIVE không tự reopen; nghiệm thu bật/tắt theo Project; công thức progress top-level | `02A §4`, `04 §6` (P-01..P-23), `05 §4` (PJ-01..PJ-10) |
| **04** Task & Subtask | mục 5 | 0..n assignee, **một** assignee submit được; subtask **một cấp**; parent cần mọi child COMPLETED/CANCELLED; dependency cảnh báo nhưng **không chu trình**; không recurring; `BLOCKED` là cờ; PENDING_APPROVAL khoá nội dung; progress chỉ top-level | `02A §5`, `04 §7` (TK-01..TK-16), `05 §1–2` |
| **05** Workflow & Approval | mục 6 | Start/submit/approve/reject/cancel/reopen; **cấm tự duyệt** theo assignee ∪ submitter (creator thì không); thiếu approver → giữ pending + cảnh báo, **không auto-approve**; reject bắt buộc lý do; không có `REQUEST_CHANGES`; reopen cần lý do, giữ lịch sử; cancel khi pending → đóng round bằng **huỷ**, không giả mạo; **một terminal result** mỗi round | `02A §6`, `04 §8` (F-01..F-10), `05 §1, §3, §8.2` |
| **06** Calendar & Notifications | mục 7 | Start/deadline tuỳ chọn; unscheduled list; reminder mặc định 1 ngày + 1 giờ; overdue nhắc 1 lần; pending approval đổi đối tượng nhận reminder; pause/resume **không gửi bù**; realtime + push + email; read-state cấp tài khoản; mute + quiet hours nhưng **bảo mật vẫn gửi**; deadline = instant + tz gốc, date-only = 23:59:59; ICS export tối thiểu hoá | `02A §8`, `02B §2`, `04 §10` (C/N), `05 §11` |
| **07** AI & Ollama | mục 8 | 2 giai đoạn; **chỉ đề xuất**; không tự approve/complete/cancel/sửa; chat chỉ đọc dữ liệu có quyền; file/comment là input không đáng tin; draft Task/subtask/priority/deadline/assignee; Daily Planner + Weekly Review; AI history có quyền + redaction; Ollama **qua Backend**; offline/timeout không hỏng core; context tối thiểu + validate output + reauthorize | `02B §4`, `04 §10` (AI-01..05), `05 §8`, `01 DEP-04` |
| **08** Dashboard & Reports | mục 9 | 4 dashboard; Personal tách giao/tạo/quản lý, tránh cộng trùng; chỉ tổng hợp đối tượng có quyền; progress theo công thức; phân bố 5 trạng thái + BLOCKED riêng; overdue phân biệt chờ duyệt; **unique task vs assignment count**; báo cáo theo kỳ; **lịch sử theo event, reopen không viết lại quá khứ**; milestone + approval stats (nhiều vòng tính riêng); PDF/XLSX/CSV; kiểm tra quyền lúc tạo **và** lúc giao; phân biệt 0/N-A/không quyền/thiếu dữ liệu; **không xếp hạng năng suất cá nhân** | `02B §3` (kèm metric dictionary), `04 §10` (R-01..R-07) |
| **09** Files & Activity | mục 10 | Attachment cho Task/Subtask/Project/Comment; allowlist + giới hạn dung lượng; binary ở Object Storage, metadata ở DB; versioning không ghi đè; soft-delete + restore; preview an toàn; scan **khi có công cụ**; **mỗi lượt preview/download kiểm tra quyền tại request**, không URL không thu hồi được; AI đọc file theo quyền; Activity Timeline; audit actor/action/target/time/before-after/reason/outcome/scope; audit bất biến từ UI + retention | `02A §7`, `02B §1, §6`, `04 §9` (FL-01..FL-09), `05 §7` |
| **10** Security & RBAC | mục 11 | RBAC theo scope + state; Backend kiểm tra **mọi** request; SA không tự đọc riêng tư; WO/WA không tự đọc Project chưa tham gia; Guest view-only; thu hồi hiệu lực ngay (kể cả job, realtime, file, export); reauth cho thao tác nhạy cảm; rate limit + lockout; hash mật khẩu; token ngắn hạn; không plaintext secret; không cấu hình nào vượt invariant; audit read theo scope; không lộ thông tin đối tượng ẩn; TLS/validation/IDOR/injection/CSRF/CORS; không self-escalation; emergency actions **danh sách đóng**; MFA/SSO chưa bắt buộc | `04` toàn bộ (đặc biệt §1 pipeline P1..P9, §12 bảng "không suy diễn", §14 reauth), `02B §6–§7` |
| **11** NFR | mục 12 | NFR-01..NFR-24 + điều kiện nghiệm thu (phải công bố phần cứng/dataset/tần suất/tỷ lệ endpoint/thời lượng/cách tính P95; RPO/RTO chỉ tuyên bố khi có backup độc lập + restore drill) | `06` (PH-7), `07-TEST-PLAN` (evidence template) |

### A.2. ARCH-01..ARCH-10

| Mã | Quyết định FROZEN | Hiện thực hoá |
|---|---|---|
| ARCH-01 | Modular Monolith theo miền nghiệp vụ | `01 §5` Maven 21 module; `01 §2` MOD-01..15 |
| ARCH-02 | REST API chung React + Flutter; client không truy cập DB/Ollama/storage | `03-API-CONTRACT`; `04 §12` (bảng "frontend ẩn nút ≠ backend bỏ qua") |
| ARCH-03 | PostgreSQL là nguồn chân lý; binary ở Object Storage | `02A/02B`; `file_object` chỉ lưu metadata |
| ARCH-04 | Transactional Outbox | `02B §5.1`; `05 §9`; outbox INSERT **trong cùng transaction** |
| ARCH-05 | Durable Background Jobs, retry idempotent, quyền hiện tại | `02B §5.3`; `05 §9`; `AUD-48` |
| ARCH-06 | Authorization Policy Layer | `04 §1` pipeline P1..P9; `01 ENF-04`; `AUD-35` predicate thống nhất |
| ARCH-07 | Private Object Storage qua Backend/gateway, authz mỗi request | `02A §7`; `04 FL-02`; `05 §7` |
| ARCH-08 | AI Orchestration qua Ollama, context có quyền, output validate, chỉ đề xuất | `02B §4`; `05 §8`; `AUD-39/40/47`; `DEP-04` |
| ARCH-09 | Realtime là tín hiệu, API là nguồn dữ liệu; reconnect/resync | `02B §2.5` (`realtime_cursor`, `notification.seq`); `03-API-CONTRACT §7` |
| ARCH-10 | Module ranh giới rõ để tách dịch vụ; không truy cập chéo bảng để vượt policy | `01 §3` DEP-01..10, `01 §4` ENF-01..07, `01 §6` ma trận sở hữu bảng, `01 §7` điểm tách dịch vụ |

### A.3. NFR-01..NFR-24 (giữ nguyên, chỉ đối chiếu hiện trạng)

| Mã | FROZEN | Hiện trạng máy dev (FACT) | Ghi chú kế hoạch |
|---|---|---|---|
| NFR-01 | Một máy chủ/VM chính V1 | Windows 11 Home, 15.7 GB RAM | Production cần Linux VM riêng |
| NFR-02 | HTTPS, xác thực | Chưa có reverse proxy | PH-0.5 dựng Nginx/Caddy mẫu |
| NFR-03 | Spring Boot / Java | Java 21 Y, Maven N | PH-0.5 cài Maven/Wrapper |
| NFR-04 | React + TypeScript | Node 26 Y | — |
| NFR-05 | Flutter Android/iOS | Dart 3.11.1 Y, cần `flutter doctor` | iOS push chờ DEC-46 |
| NFR-06 | PostgreSQL | N chưa có, Docker daemon tắt | PH-0.5 |
| NFR-07 | Private S3 qua Backend | N chưa có MinIO | PH-0.5 |
| NFR-08 | Ollama Local/LAN qua Backend | N chưa cài, GPU 1 GB VRAM | **DEC-41** |
| NFR-09 | 50–100 concurrent users | **Không thể nghiệm thu tại đây** | **DEC-42**, PH-7 |
| NFR-10 | P95 ≤ 500 ms | Chưa đo | Chỉ đo bậc 1 (10–20 user) tại dev |
| NFR-11 | AI/nặng chạy async job | Y thiết kế sẵn | `02B §5.3`, `05 §8` |
| NFR-12 | Ollama lỗi không gián đoạn core | Y thiết kế sẵn | `05 §8` FAILED_OLLAMA/TIMEOUT |
| NFR-13 | Realtime + đồng bộ lại khi mất kết nối | Y thiết kế sẵn | `realtime_cursor` + resync |
| NFR-14 | Mobile đọc cache an toàn, ghi phải online | ! phụ thuộc **DEC-12** | Allowlist offline rất hẹp |
| NFR-15 | Backup DB + Object Storage + config, thử restore | Chưa có | PH-1 (drill lần 1), PH-7 (độc lập) |
| NFR-16 | RPO ≤ 24h, RTO ≤ 8h | **Chưa có backup độc lập** → chưa tuyên bố | PH-7 |
| NFR-17 | Health, structured logs, metrics, cảnh báo | Y thiết kế sẵn | MOD-15 |
| NFR-18 | Dev/Test/Prod tách biệt, migration/rollback kiểm soát | Y thiết kế sẵn | `02B §10` MIG-01..12 |
| NFR-19 | Modular Monolith, tách worker/AI khi cần | Y thiết kế sẵn | `01 §5, §7` |
| NFR-20 | Kiểm thử chức năng/RBAC/workflow/perf/backup/lỗi AI | Y kế hoạch sẵn | `07-TEST-PLAN` |
| NFR-21 | Hiệu năng đo bằng kịch bản tải xác định; AI có chỉ tiêu riêng | Chưa đo | PH-7 |
| NFR-22 | Cache tối thiểu, mã hoá, gắn phiên; offline chỉ dữ liệu ít nhạy cảm | ! **DEC-12** | — |
| NFR-23 | Backup nhất quán, bản sao độc lập, diễn tập restore | Chưa có | PH-7 |
| NFR-24 | Job có trạng thái, reauthorize, mất quyền huỷ/ẩn, chống trùng | Y thiết kế sẵn | **DEC-48**, `AUD-48/49` |

---

## PHẦN B — GIẢ ĐỊNH KỸ THUẬT (ASM) — **CHƯA ĐƯỢC DUYỆT**

> Tôi đã dùng các giả định sau để viết ERD/API/RBAC. Nếu bạn bác bất kỳ mục nào, tài liệu tương ứng phải sửa lại. Đánh dấu Y/N ở Phần D.

### B.1. Công nghệ và thư viện

| ID | Giả định | Ảnh hưởng nếu bác |
|---|---|---|
| ASM-01 | Spring Boot **3.x** trên Java 21, không dùng Kotlin | Đổi toàn bộ skeleton |
| ASM-02 | PostgreSQL **16** (cần cho `pg_trgm`, partition declarative, `SKIP LOCKED`) | < 12 thì nhiều thiết kế index/partition phải đổi |
| ASM-03 | **MinIO** làm Object Storage S3-compatible (NFR-07 chưa nêu nhà cung cấp) | Đổi adapter; MinIO hỗ trợ versioning + private |
| ASM-04 | **Flyway Community** + SQL thuần (MIG-01..12) | Liquibase thì viết changelog XML/YAML khác |
| ASM-05 | **Maven** multi-module 21 module, package gốc `vn.smarttask` | Gradle → đổi build script và wrapper |
| ASM-06 | Web: **Vite + React 18/19 + TypeScript**, data layer TanStack Query, UI một design system chưa chốt | Ảnh hưởng `PH-0.5` Web skeleton |
| ASM-07 | Mobile: **Flutter** + `dio` + state management chưa chốt (Riverpod hoặc Bloc) + `flutter_secure_storage` | Ảnh hưởng cấu trúc app |
| ASM-08 | PDF: openhtmltopdf/Flying Saucer; XLSX: Apache POI; CSV: tự viết | Nhóm 08 yêu cầu PDF/XLSX/CSV nhưng chưa chốt thư viện |
| ASM-09 | Argon2id cho password hash (BCrypt là phương án dự phòng) | Nhóm 10 chỉ nói "hash an toàn" |
| ASM-10 | Observability: Micrometer + Prometheus + Grafana + Loki (hoặc chỉ Actuator + log file ở V1) | Ảnh hưởng PH-7 |
| ASM-11 | Reverse proxy: **Caddy** (tự cấp TLS nội bộ) hoặc Nginx | NFR-02 |
| ASM-12 | CI: chưa chốt (GitHub Actions / GitLab CI / chạy local) | Ảnh hưởng ENF-02 (ArchUnit phải chạy trong CI) |

### B.2. Quyết định thiết kế dữ liệu

| ID | Giả định | Ảnh hưởng nếu bác |
|---|---|---|
| ASM-13 | PK **UUIDv7** cho mọi bảng | UUIDv4 → index phân mảnh hơn; bigserial → lộ số lượng (rủi ro IDOR cao hơn) |
| ASM-14 | Enum dùng `text` + CHECK, **không** dùng PostgreSQL `ENUM TYPE` | Đổi cách migration thêm giá trị |
| ASM-15 | Soft-delete bằng `deleted_at` + `delete_group_id`; unique index **partial** cho hầu hết bảng, nhưng **toàn phần** cho `app_user.email/username` (AUD-31) | Nếu dùng hard-delete → mất yêu cầu "giữ dành riêng" |
| ASM-16 | Optimistic lock `version bigint` trên entity có tranh chấp | Nếu bỏ → RACE-02/03/11 khó bảo đảm |
| ASM-17 | Attachment **polymorphic** (`target_type` + `target_id`) thay vì FK riêng cho từng loại | Nếu dùng bảng nối riêng → thêm 6 bảng, policy layer phức tạp hơn |
| ASM-18 | Partition theo **tháng** cho 7 bảng append-only; theo **năm** cho snapshot | Volume thấp thì có thể bỏ partition ở V1 |
| ASM-19 | **65 bảng** là đủ cho V1 (chưa kể partition con và bảng archive) | Nếu gộp/tách → ERD đổi |
| ASM-20 | Audit dùng **hash-chain SHA-256** (`prev_hash`, `entry_hash`) | Nếu bỏ → chỉ còn bảo vệ bằng DB role |
| ASM-21 | Tìm kiếm tiếng Việt: `search_text` chuẩn hoá + `to_tsvector('simple')` + `pg_trgm`; **không** dùng `unaccent` (bỏ dấu thanh làm sai nghĩa tiếng Việt) | AUD-26 |
| ASM-22 | Dung lượng DB tăng ~1–3 GB/năm ở 50–100 user (**chưa đo**) | Ảnh hưởng sizing và yêu cầu 28 GB ổ D: |
| ASM-37 | `comment.target_type` chỉ có `TASK` và `SUBTASK` ở V1 — **không** có comment trên Project, Workspace hay Milestone. Suy ra từ Nhóm 04 (đặc tả mục 5 liệt kê comment như một tính năng của Task/Subtask), không phải từ một câu khẳng định tường minh nào | Nếu bạn muốn comment cấp Project/Workspace → thêm giá trị vào CHECK của `comment.target_type`, thêm nhánh quyền trong `04 §9` (họ `C-*` hiện chỉ phủ Calendar/Notification, chưa có mã action cho comment Project), thêm policy redaction theo scope mới, và `02B §1.1` phải bỏ ràng buộc `project_id NOT NULL` cho comment không thuộc Project nào. Chi phí: sửa 4 tài liệu + thêm test |

### B.3. Quyết định thiết kế giao tiếp

| ID | Giả định | Ảnh hưởng nếu bác |
|---|---|---|
| ASM-23 | Base path `/api/v1`, JSON `camelCase`, i18n qua `message_key` (không trả text đã render) | Nếu trả text server-side → client mất khả năng đa ngôn ngữ |
| ASM-24 | Pagination **cursor-based** mặc định, `limit ≤ 100` | Web table cần số trang → phải thêm offset cho tập nhỏ |
| ASM-25 | Access token opaque **15 phút**, refresh **30 ngày**, session gắn 1 thiết bị | Nhóm 10 chỉ nói "token ngắn hạn" |
| ASM-26 | Realtime = **WebSocket** 1 kết nối/phiên + `seq` resync | SSE thì Flutter phải tự xử lý stream |
| ASM-27 | `StreamTicket` file single-use TTL **30 giây** | Ảnh hưởng UX preview (PDF nhiều trang cần tải lại ticket) |
| ASM-28 | `Idempotency-Key` TTL **24 giờ**; report export TTL **24 giờ** | AUD-16 |
| ASM-29 | Retention V1: **giữ vĩnh viễn** (không hard-delete tự động), chỉ archive | AUD-45 |
| ASM-30 | Rate limit theo bảng `04 §15` | Nhóm 10 chưa chốt ngưỡng |

### B.4. Giả định vận hành & dự án

| ID | Giả định | Ảnh hưởng nếu bác |
|---|---|---|
| ASM-31 | **Monorepo** 1 repo cho Backend + Web + Mobile | 3 repo → đổi CI và versioning |
| ASM-32 | Dev dùng **Docker Compose** (Postgres + MinIO + MailHog) | Nếu Docker không bật được → cài native, tài liệu ENV khác đi |
| ASM-33 | Team giả định cho ước lượng: 1 senior backend + 1 full-stack + 1 mobile (+ QA bán thời gian) | EST ở `06 §4` sai hoàn toàn nếu team khác |
| ASM-34 | Chưa có deadline; kế hoạch xếp theo **dependency**, không theo ngày tháng | Nếu có deadline → phải cắt phạm vi, và cắt phạm vi V1 là thay đổi baseline, cần phê duyệt |
| ASM-35 | Dữ liệu test là **fixture tổng hợp**, không có dữ liệu người dùng thật | MIG-01 |
| ASM-36 | Chưa có engine anti-malware ở V1 → `file_object.scan_status = 'UNSUPPORTED'` và **phải công bố** là chưa có bảo vệ malware | Nhóm 09: "kiểm tra malware **khi có công cụ**" |

---

## PHẦN C — QUYẾT ĐỊNH CẦN NGƯỜI DÙNG XÁC NHẬN (DEC)

### C.1. Mức  BLOCKER — **chặn phase, phải trả lời trước**

---

#### DEC-01 — Ai là actor của cascade huỷ khi Project COMPLETED, và chạy đồng bộ hay async?
*Nguồn: AUD-01 | Chặn: PH-2 | Baseline liên quan: Nhóm 03, 05, 15.2*

| Phương án | Nội dung | Ưu | Nhược |
|---|---|---|---|
| **A (khuyến nghị)** | Đồng bộ trong **một transaction** với chuyển Project → COMPLETED. `actor_type = SYSTEM_RULE`, `actor_user_id = Manager bấm nút`, `reason_code = PROJECT_COMPLETED_CASCADE` | Bất biến luôn đúng, không có cửa sổ sai; audit rõ | Transaction lớn nếu Project có nhiều Task |
| B | Async qua job sau commit | Transaction nhỏ | Có cửa sổ Project COMPLETED nhưng Task còn IN_PROGRESS → sai progress/report, khó test |
| C | Đồng bộ nhưng yêu cầu Manager xác nhận danh sách Task sẽ huỷ | Minh bạch hơn với người dùng | Thêm 1 bước UI; đặc tả nói "không bắt buộc lý do" |

**Khuyến nghị:** **A**, kèm màn hình xác nhận tóm tắt ("sẽ huỷ N task, M subtask, K milestone") nhưng **không** bắt buộc lý do — vẫn đúng Nhóm 03.

---

#### DEC-02 — "readonly" của Project ON_HOLD / ARCHIVED có chặn cả hành động workflow không?
*Nguồn: AUD-02 | Chặn: PH-2 | Baseline: Nhóm 03, 05, 06*

| Phương án | Nội dung | Hệ quả |
|---|---|---|
| **A (khuyến nghị)** | Chặn **mọi** mutation nội dung **và** workflow; chỉ Manager được đổi trạng thái Project | Task PENDING_APPROVAL bị treo khi ON_HOLD; reminder pause; khi ACTIVE lại thì tiếp tục + 1 thông báo tồn đọng |
| B | Chỉ chặn nội dung, **cho phép** approve/reject/cancel | Vòng duyệt vẫn chạy khi Project "tạm dừng" — mâu thuẫn ngữ nghĩa ON_HOLD |
| C | Chặn nội dung + approve/reject, nhưng **cho phép cancel** | Trung gian; phức tạp hơn khi test |

**Khuyến nghị:** **A** — đơn giản, dễ kiểm thử, đúng tinh thần "readonly".

---

#### DEC-03 — Có chặn cứng việc thành viên rời/bị loại khi là Manager cuối của Project chưa DELETED?
*Nguồn: AUD-03 | Chặn: PH-1 | Baseline: Nhóm 02, 03, bất biến I-04*

| Phương án | Nội dung | Hệ quả nếu chọn |
|---|---|---|
| **A (khuyến nghị)** | **Chặn cứng**, trả `409 LAST_PROJECT_MANAGER`; buộc chỉ định Manager thay thế trước. Áp dụng cho: tự rời, bị loại, soft-delete tài khoản, membership mất ACTIVE | Không phải sửa baseline; đúng bất biến I-04. UX gắt hơn với Project chỉ có một Manager |
| B | Cho phép, tự động hạ Project về trạng thái đặc biệt "chờ Manager" | Vi phạm bất biến I-04; phải thêm trạng thái Project (thay đổi baseline Nhóm 03) |
| C | Cho phép, Workspace Owner phải chỉ định Manager trong vòng X ngày | Cần thêm job + trạng thái trung gian; rủi ro Project mồ côi |

**Khuyến nghị:** **A** — giống hệt cách đặc tả đã xử lý với Owner ("Owner phải chuyển quyền trước khi rời").

---

#### DEC-05 — Subtask có vòng nghiệm thu riêng không?
*Nguồn: AUD-05 | Chặn: PH-2 | Baseline: Nhóm 03, 04, 05*

| Phương án | Nội dung | Chi phí |
|---|---|---|
| **A (khuyến nghị)** | **Không.** Subtask: `TODO → IN_PROGRESS → COMPLETED/CANCELLED`. Approval chỉ ở top-level Task | Thấp; parent là điểm nghiệm thu duy nhất |
| B | Có, subtask cũng đi qua PENDING_APPROVAL khi Project bật nghiệm thu | Cao: số round tăng mạnh, parent phải chờ approve từng child, RACE tăng gấp đôi, UI phức tạp |

**Khuyến nghị:** **A**. Đây là **quyết định phạm vi**, cần bạn xác nhận rõ vì đặc tả không nói.

---

#### DEC-06 — Chính xác những gì bị khoá khi Task ở PENDING_APPROVAL?
*Nguồn: AUD-06 | Chặn: PH-2, PH-3 | Baseline: Nhóm 04, 09, test 19.2*

Bảng đề xuất (Y = cho phép,  = chặn):

| Đối tượng | Đề xuất | Bạn chọn |
|---|---|---|
| Attachment add/replace/delete/restore | | |
| Checklist item | | |
| Title / description | | |
| Time log | | |
| Assignment (thêm/bớt assignee) | (để giữ nguyên tập "cấm tự duyệt") | |
| Dependency | | |
| **Comment** | Y (có audit) — thảo luận khi duyệt là nghiệp vụ thường | |
| **Deadline / priority / tag / milestone** (Manager) | Y (có audit) | |
| Blocked flag | Y | |

**Nếu bạn muốn khoá luôn comment** → cần bổ sung cơ chế "approver trao đổi với submitter" khác (ví dụ chỉ approver mới comment được), nếu không sẽ mất kênh giao tiếp trong lúc duyệt.

---

#### DEC-12 — Allowlist offline cache của Mobile là gì?
*Nguồn: AUD-12 | Chặn: PH-0.5/PH-1 (kiến trúc Flutter) | Baseline: NFR-14, NFR-22, mục 14.6, test 19.2*

| Phương án | Nội dung offline | Rủi ro |
|---|---|---|
| **A (khuyến nghị, an toàn nhất)** | Chỉ: hồ sơ của chính mình, **tên** Workspace/Project mình là thành viên, cấu hình UI, thời điểm sync cuối, notification đã redact. **Không** cache Task title/description/file/số liệu | Offline gần như chỉ hiển thị "cần kết nối" |
| B | A + danh sách Task **title** + deadline của mình, mã hoá, TTL 24h, chỉ đọc | Rò rỉ tiêu đề Task sau khi mất quyền (rủi ro mục 20 đã xếp mức **Cao**) |
| C | B + description + attachment đã tải | Rò rỉ nội dung đầy đủ — **trái** NFR-22 và mục 14.6 |

**Khuyến nghị:** **A** cho V1. Nếu chọn B, phải ghi rõ vào risk register là **chấp nhận rủi ro có chủ đích**.

---

#### DEC-15 — Số liệu lịch sử dùng event-derived, snapshot, hay cả hai?
*Nguồn: AUD-15 | Chặn: PH-1 (ERD), PH-6 | Baseline: Nhóm 08, 15.2*

| Phương án | Nội dung | Ưu / nhược |
|---|---|---|
| A | Chỉ `workflow_event` (tái dựng as-of) | Đúng nhất về nguyên tắc; query chậm, khó cache |
| B | Chỉ `metric_daily_snapshot` | Nhanh; nhưng không tái dựng được thời điểm trong ngày |
| **C (khuyến nghị)** | **Cả hai**: `workflow_event` = nguồn chân lý bất biến; `metric_daily_snapshot` = bản chốt cuối ngày cho dashboard | Nhanh + đúng; snapshot không bao giờ ghi ngược (thoả "reopen không viết lại quá khứ") |

**Khuyến nghị:** **C**.

---

#### DEC-27 — Cơ chế chống race khi nhiều approver quyết định đồng thời?
*Nguồn: AUD-27 | Chặn: PH-2 | Baseline: Nhóm 05, test 19.1*

| Phương án | Nội dung | Hệ quả nếu chọn |
|---|---|---|
| **A (khuyến nghị)** | 3 lớp: `SELECT … FOR UPDATE` trên `approval_round` + **partial unique index** `uq_decision_single_terminal` + `task.version` optimistic lock | Đã thiết kế sẵn trong `02A §6.3` và `05 §10 RACE-01`; thông báo lỗi thân thiện, deadlock tránh được bằng quy tắc lock theo `id` tăng dần |
| B | Chỉ `SERIALIZABLE` isolation | Retry nhiều ở 50–100 user, khó dự đoán |
| C | Chỉ unique index, không lock | Vẫn đúng nhưng thông báo lỗi khó thân thiện, dễ deadlock hơn |

**Khuyến nghị:** **A** (đúng như đã thiết kế trong `02A §6.3` và `05 §10 RACE-01`). Đây là câu hỏi **xác nhận thiết kế**, không phải chọn hướng mới.

---

#### DEC-41 — (Đã loại bỏ)
*Nguồn: AUD-41 | Chặn: PH-5 | Baseline: Nhóm 07, NFR-08/11/12/21*

Quyết định hạ tầng Ollama đã được loại bỏ khỏi đăng ký DEC. AI (Nhóm 07) giữ nguyên theo đặc tả FROZEN: chỉ chạy qua Backend, chỉ đề xuất, không tự thay đổi nghiệp vụ; Ollama không bắt buộc cho V1 core, `ai.enabled` mặc định `false` cho đến khi có hạ tầng Ollama thật.

---

#### DEC-42 — Hạ tầng để nghiệm thu NFR-09 (50–100 user) và NFR-10 (P95 ≤ 500 ms)?
*Nguồn: AUD-42 | Chặn: PH-7 | Baseline: Nhóm 11 + điều kiện nghiệm thu mục 12*

| Phương án | Nội dung | Hệ quả nếu chọn |
|---|---|---|
| **A (khuyến nghị)** | Thuê/dựng VM riêng tối thiểu **8 vCPU / 32 GB RAM / SSD NVMe** cho load test bậc 2; **không** chạy Ollama trên cùng máy khi load test. Máy dev chỉ chạy bậc 1 (10–20 user) và ghi rõ là "baseline tham chiếu trên phần cứng X" | Cần chi phí hạ tầng ngoài; đổi lại NFR-09/NFR-10 có thể được tuyên bố **đạt** một cách hợp lệ theo đặc tả mục 12 |
| B | Chỉ chạy bậc 1 trên máy dev | **Không được tuyên bố đạt NFR-09/10**; báo cáo nghiệm thu phải ghi "chưa kiểm chứng" |
| C | Hoãn PH-7 | Kéo dài V1 |

**Cần bạn trả lời:** có cấp hạ tầng load test không? Nếu không, tôi sẽ ghi rõ trong báo cáo là NFR-09/10 **chưa được kiểm chứng**.

---

#### DEC-43 — Bộ công nghệ và thông số nền (chốt một lần)
*Nguồn: AUD-43, đặc tả mục 22.1 | Chặn: PH-0.5, PH-1 | Baseline: Nhóm 11, ARCH-01..10*

| Hạng mục | Khuyến nghị | Phương án khác | Bạn chọn |
|---|---|---|---|
| Build tool | **Maven** + Wrapper | Gradle | |
| Migration | **Flyway** + SQL thuần | Liquibase | |
| Job queue | **PostgreSQL-backed** (`SKIP LOCKED`) + Outbox cùng DB | RabbitMQ / Redis | |
| Object Storage | **MinIO** | Ceph / local FS adapter | |
| Realtime | **WebSocket** + resync `seq` | SSE / polling | |
| Token | **Opaque session token** server-side + access token ngắn hạn | JWT + revocation list | |
| PK | **UUIDv7** | UUIDv4 / bigserial | |
| API version | Prefix **`/api/v1`** | Header versioning | |
| DB role tách theo module | Làm **ngay từ PH-0.5** (Production), Dev dùng 1 role | Hoãn tới PH-7 | |
| Access token TTL | **15 phút** | 5 / 30 / 60 phút | |
| Refresh token TTL | **30 ngày** | 7 / 90 ngày | |
| Rate limit login | **10/phút/IP, 5/phút/username** | khác | |
| Lockout | **5 lần sai → khoá 15 phút** | khác | |
| Repo | **Monorepo** | 3 repo | |
| Package Java | **`vn.smarttask`** | khác | |

> Lựa chọn **PostgreSQL-backed queue** là quan trọng nhất: nó thoả ARCH-04/05 + NFR-01 (một máy chủ) mà không thêm hạ tầng. Nếu bạn muốn RabbitMQ/Redis, phải chấp nhận thêm 1–2 tiến trình và chi phí vận hành trên 1 VM.

---

#### DEC-46 — Prerequisite push & email: đã có gì?
*Nguồn: AUD-46 | Chặn: PH-4 | Baseline: Nhóm 06, NFR-05*

| Hạng mục | Đã có? | Nếu chưa |
|---|---|---|
| Firebase project + service account (FCM, Android) | [ ] Có [ ] Không | Android push chỉ test được bằng fake provider; **không** tuyên bố hoạt động thật |
| Apple Developer Program + APNs key (iOS) | [ ] Có [ ] Không | iOS push **dời** khỏi V1; chỉ cam kết Android + in-app |
| SMTP server / relay production | [ ] Có [ ] Không | Dev/Test dùng MailHog; email production **chưa kiểm chứng** |

**Cần bạn trả lời rõ từng dòng** — đây là prerequisite bên ngoài, tôi không tự giải quyết được.

---

#### DEC-48 — Background job dùng quyền snapshot hay identity để đánh giá lại?
*Nguồn: AUD-48 | Chặn: PH-1 (nền job) | Baseline: NFR-24, ARCH-05, quy tắc "không để job nền sử dụng quyền đã bị thu hồi"*

| Phương án | Nội dung | Đánh giá |
|---|---|---|
| **A (khuyến nghị)** | Job chỉ lưu `principal_user_id` + `scope`; **không** lưu permission đã đánh giá. Tại mỗi bước nhạy cảm và trước khi giao kết quả → gọi lại Policy Layer với quyền **tại thời điểm đó**. Mất quyền → `CANCELLED_REVOKED`, huỷ/ẩn kết quả, **không retry** | Thoả đúng NFR-24 và quy tắc của bạn |
| B | Lưu permission snapshot lúc enqueue | **Vi phạm** NFR-24 — không được chọn |
| C | Chạy job dưới service account toàn quyền | **Vi phạm nặng** — không được chọn |

**Khuyến nghị:** **A** (đã thiết kế trong `02B §5.3`, `05 §8–9`).

---

### C.2. Mức  MAJOR — cần trả lời trước phase tương ứng

| DEC | Câu hỏi | Phương án | Khuyến nghị | Chặn |
|---|---|---|---|---|
| **04** | Workspace Guest có được làm Project **Member** (có quyền ghi)? | (a) Chỉ Project Guest; (b) Được làm Member | **(a)** — AUD-04 | PH-1 |
| **07** | Ai được làm Designated Approver? | (a) Participant ACTIVE bất kỳ ≠ Guest; (b) Chỉ Manager | **(a)** — (b) gây deadlock khi Manager duy nhất là submitter (AUD-07) | PH-2 |
| **08** | Approval SLA quá hạn có sinh hành động tự động? | (a) Chỉ notify/escalate; (b) Auto-assign approver khác; (c) Auto-approve | **(a)** — (c) vi phạm "không tự approve"; (b) cần định nghĩa thêm | PH-2, PH-4 |
| **09** | Phạm vi dependency? | (a) Cùng Project, chỉ top-level, hard-block chu trình; (b) Cho phép xuyên Project; (c) Cho cả subtask | **(a)** — AUD-09 | PH-2 |
| **10** | Project trong Personal Workspace có participant? | (a) Không, chỉ chủ tài khoản; (b) Có | **(a)** — nếu (b) thì vi phạm "không mời thành viên" của Nhóm 02 | PH-1 |
| **11** | Invite tới email đã có tài khoản ACTIVE? | (a) Bắt buộc accept; (b) Tự động thêm | **(a)** — tránh gán người dùng vào Workspace ngoài ý muốn | PH-1 |
| **13** | Payload push có chứa tiêu đề Task/tên Project? | (a) Không — chỉ "có cập nhật mới", nội dung render khi gọi API; (b) Có | **(a)** — giảm thiểu rủi ro AUD-13; nếu (b) phải chấp nhận residual risk bằng văn bản | PH-4 |
| **14** | Audit retention/disposal V1? | (a) Giữ vĩnh viễn, chỉ archive partition; (b) Xoá sau N tháng | **(a)** — AUD-14/45; chưa có yêu cầu GDPR cụ thể | PH-1 |
| **16** | Report export lưu ở đâu, TTL bao lâu, ai tải được? | (a) Object Storage private, TTL 24h, chỉ requester; (b) TTL 7 ngày; (c) Cho phép chia sẻ link | **(a)** — AUD-16; (c) vi phạm Nhóm 09 | PH-6 |
| **18** | Cơ chế bootstrap System Admin? | (a) CLI local, không HTTP endpoint, cờ một lần; (b) HTTP endpoint localhost; (c) Seed trong migration | **(a)** — AUD-18; (c) nguy hiểm vì seed chạy lại được | PH-0.5 |
| **19** | UX cho "một phiên duy nhất" trên đa thiết bị? | (a) Cảnh báo trước khi login + hiển thị device_label + push SESSION_REVOKED; (b) Im lặng đá phiên | **(a)** — AUD-19 | PH-1 |
| **23** | Quy tắc time log? | (a) Chỉ tự ghi, edit window 7 ngày, Manager chỉ soft-delete kèm lý do; (b) Manager ghi hộ được; (c) Tự do sửa | **(a)** — AUD-23 | PH-2 |
| **26** | Chấp nhận chất lượng tìm kiếm tiếng Việt ở mức nào? | (a) pg_trgm + tsvector 'simple' (đủ dùng); (b) Thêm embedding AI; (c) Thêm search engine riêng | **(a)** cho V1; (c) xung đột NFR-01 | PH-2 |
| **28** | Owner transfer: xác nhận cơ chế lock + partial unique index? | (a) Như `05 §5`; (b) Khác | **(a)** — AUD-28 | PH-1 |
| **29** | Restore theo `delete_group_id`, không hồi sinh membership? | (a) Như AUD-29; (b) Restore tất cả | **(a)** — đúng Nhóm 02/04 | PH-1, PH-3 |
| **30** | Personal Workspace: dùng `personal_owner_user_id` + type immutable? | (a) Như AUD-30; (b) Khác | **(a)** | PH-1 |
| **31** | Email/username unique **toàn phần** (kể cả tài khoản đã xoá)? | (a) Có — đúng "không cấp lại"; (b) Chỉ bản ghi chưa xoá | **(a)** — (b) vi phạm Nhóm 01 | PH-1 |
| **34** | Danh sách thao tác nhạy cảm cần reauth + TTL 5 phút? | (a) Như `04 §14`; (b) Rộng hơn; (c) Hẹp hơn | **(a)** | PH-1 |
| **35** | Dùng **một** predicate `isValidActiveActor` thống nhất? | (a) Có; (b) Mỗi module tự xét | **(a)** — AUD-35; (b) là nguồn lỗi phân quyền | PH-1 |
| **36** | Mô hình quyền đọc audit theo scope + sensitivity? | (a) Như AUD-36; (b) SA đọc hết | **(a)** — (b) vi phạm Nhóm 09/10 | PH-1 |
| **38** | Dùng `MetricEnvelope` với `state ∈ {OK, NOT_APPLICABLE, NO_PERMISSION, INSUFFICIENT_DATA, PARTIAL}`? | (a) Có; (b) Trả number + null | **(a)** — AUD-38, Nhóm 08 | PH-6 |
| **39** | Duyệt `ai_context_manifest` cho từng tính năng AI? | (a) Như AUD-39 (manifest khai báo trường + giới hạn, review như code); (b) Để AI tự chọn context | **(a)** — (b) vi phạm Nhóm 07 | PH-5 |
| **40** | Biện pháp chống prompt injection? | (a) Delimiter + structured output + validate + loại trường ngoài scope + giới hạn độ dài; (b) Chỉ validate output | **(a)** — AUD-40 | PH-5 |
| **44** | UX/UI: làm `10-UX-SITEMAP.md` trước khi code Web/Mobile? Ngôn ngữ UI? Chuẩn ngày giờ? | (a) Song ngữ vi-VN mặc định + `dd/MM/yyyy` + `HH:mm` theo tz người xem; (b) Chỉ tiếng Việt; (c) Chuẩn khác | Cần bạn chọn — AUD-44 | PH-0.5/PH-1 |
| **45** | Data lifecycle/retention V1? | (a) Không hard-delete tự động; retention_policy cấu hình được, mặc định giữ vĩnh viễn; (b) Có chu kỳ xoá | **(a)** — AUD-45 | PH-7 |
| **47** | Cấm module AI mutate bằng ArchUnit test (fail build)? | (a) Có; (b) Chỉ quy ước | **(a)** — AUD-47, DEP-04 | PH-0.5 |
| **49** | Outbox idempotency bằng `UNIQUE(outbox_event_id, handler_key)` + `dedup_token`? | (a) Có; (b) Khác | **(a)** — AUD-49 | PH-1 |
| **50** | Định nghĩa `hasParticipation` (gồm creator và assignee) + `special_grant` cho quyền đọc đặc biệt? | (a) Như AUD-50; (b) Không có special_grant (WO/SA hoàn toàn không đọc được) | **(a)** nếu muốn có đường khẩn cấp có kiểm soát; **(b)** nếu muốn tuyệt đối — cả hai đều hợp baseline, nhưng ảnh hưởng vận hành | PH-1 |
| **51** | Stream file đang chạy khi quyền bị thu hồi: cắt ngay hay cho hoàn tất? | (a) Cho hoàn tất chunk hiện tại, chặn request mới (đơn giản); (b) Kiểm tra lại quyền theo chu kỳ trong lúc stream và cắt | **(a)** cho V1, ghi residual risk; **(b)** an toàn hơn nhưng tốn hiệu năng | PH-3 |
| **52** | Ba câu hỏi gộp về tuỳ chọn reminder (AUD-51 — phát hiện khi rà soát chéo ERD): **(i)** `timezone`/`locale` đặt ở đâu? **(ii)** quiet hours là cấp user hay cấp loại thông báo? **(iii)** cấu hình overdue dùng enum hay interval? | (a) `timezone`+`locale` **chỉ** ở `app_user`; quiet hours **cấp user** ở `user_profile_preference`; overdue = enum `overdue_reminder_policy ∈ {ONCE, DAILY, WEEKLY, OFF}`; (b) Như (a) nhưng overdue = interval giờ `overdue_reminder_interval_hours 1..168`; (c) Quiet hours cấu hình được **theo loại thông báo** (giữ cột ở `notification_preference`) | **(a)** — (i) Nhóm 01 xếp timezone vào hồ sơ, `app_user` đã giữ các trường hồ sơ còn lại; (ii) Nhóm 06 viết "quiet hours theo timezone **user**" (số ít) và cơ chế theo loại của Nhóm 06 là **mute**, không phải quiet hours; `system_setting` cũng chỉ có **một** khoá `notification.quiet_hours_default_enabled`; (iii) enum khớp từ vựng "nhắc một lần… sau đó theo cấu hình" và chặn được cấu hình spam mà interval 1 giờ cho phép. (c) chỉ nên chọn nếu bạn thực sự muốn quiet hours khác nhau cho từng loại thông báo — khi đó phải định nghĩa thứ tự ưu tiên và sửa `02B §2.2` | PH-1 (ERD), PH-4 |
| **54** | Quyền comment (AUD-53 — `04` trước đây không có mã action nào cho comment; đã bổ sung họ `CM-01..CM-05` ở `04 §7.1`). Bốn điểm đặc tả im lặng: **(i)** Project Guest có được comment? **(ii)** Có cho sửa comment của chính mình, có cửa sổ thời gian? **(iii)** Ai được xoá comment của người khác? **(iv)** Comment khi Task `PENDING_APPROVAL`? | (a) Guest **không**; tác giả được sửa comment của mình **không giới hạn thời gian** nhưng mọi lần sửa ghi `activity_event` + `audit_event`; chỉ **PM** được xoá của người khác, kèm `reason` bắt buộc + audit `SENSITIVE_CONTENT`; PENDING_APPROVAL **vẫn cho phép** comment; (b) Như (a) nhưng cấm sửa sau 15 phút (chỉ được xoá rồi viết lại); (c) Như (a) nhưng cho phép cả PR xoá comment trong Task mình là ASG/CRT | **(a)** — (i) Nhóm 03 định nghĩa Guest là chỉ-xem; (ii) đặc tả mục 16 yêu cầu timeline ghi "chỉnh sửa", ngụ ý sửa được và phải để lại dấu vết, còn giới hạn thời gian là ràng buộc UX chưa có căn cứ; (iii) mở rộng cho PR sẽ cho phép một người tham gia xoá phát ngôn của người khác, rủi ro lạm dụng cao hơn lợi ích; (iv) AUD-06 đã kết luận comment không phải "nội dung thực thi", nhưng vẫn treo theo DEC-06. Chọn (b) nếu bạn ưu tiên tính bất biến của thảo luận đã được duyệt | PH-2 (MOD-06) |

### C.3. Mức  MINOR — có thể trả lời gộp

| DEC | Câu hỏi | Khuyến nghị |
|---|---|---|
| **17** | Mật khẩu tạm 24h và 8 mã khôi phục offline chạy song song? | Có — AUD-17 |
| **20** | Progress làm tròn 1 chữ số thập phân, API trả `numerator/denominator/percent/display`? | Có — AUD-20 |
| **21** | Milestone COMPLETED do Manager bấm (thủ công), không tự suy diễn? | Có — AUD-21 |
| **22** | Tag thuộc Workspace, unique `(workspace_id, lower(name))`, chỉ Owner/Admin tạo? | Có — AUD-22 |
| **24** | Reminder dùng chung: người nhận = ASG ACTIVE + PM; nổ một lần/người? | Có — AUD-24 |
| **25** | Resume sau pause: một `RESUME_DIGEST` duy nhất cho mỗi người? | Có — AUD-25 |
| **32** | Lockout: 5 lần sai → 15 phút; `locked_until` không đổi `status`; trả `423`? | Có — AUD-32 (ngưỡng có thể đổi) |
| **33** | Quyền rời (`global_permission_grant`) thay vì nhét vào role? | Có — AUD-33 |
| **37** | Metric luôn trả **cả** `unique_task_count` và `assignment_count`? | Có — AUD-37 |
| **53** | Xác nhận đổi họ mã action Task của `04 §7` từ `T-01..T-16` thành **`TK-*`**, đồng thời **xoá** hai mã trùng thao tác (`TASK_READ_TIMELINE` = `FL-08`, `TASK_EXPORT_ICS` = `C-06`) để họ này dừng ở **`TK-01..TK-14`**, nhằm hết trùng prefix với transition `T-01..T-12` của `05 §1.1`? (Đã áp dụng trong `04`, `05`, `08`; còn `03` và `07` sẽ sửa khi hai file đó viết xong) | Có — AUD-52, AUD-53b. Nếu bạn muốn giữ `T-*` cho action thì phải đổi họ transition sang `TR-01..TR-12`, chi phí cao hơn nhiều vì `03`/`06`/`07` đã dùng `T-01..T-12` làm khoá trace ở hàng chục chỗ |

---

## PHẦN D — PHIẾU TRẢ LỜI (điền và gửi lại)

### D.1. BLOCKER — bắt buộc

```
DEC-01 : [A / B / C / khác]  ghi chú: ..........................................
DEC-02 : [A / B / C / khác]  ghi chú: ..........................................
DEC-03 : [A / B / khác]      ghi chú: ..........................................
DEC-05 : [A / B]             ghi chú: ..........................................
DEC-06 : [đồng ý bảng đề xuất / sửa: .........................................]
DEC-12 : [A / B / C]         ghi chú: ..........................................
DEC-15 : [A / B / C]         ghi chú: ..........................................
DEC-27 : [A / B / C]         ghi chú: ..........................................
DEC-41 : [P-A / P-B / P-C]   có máy chủ GPU LAN? [có / không]  cấu hình: .......
DEC-42 : [A / B / C]         có cấp VM load test? [có / không] cấu hình: .......
DEC-43 : Build tool ......... Migration ........ Queue .......... Storage .......
         Realtime ........... Token ............ PK ............. API version ...
         DB role ............ Access TTL ....... Refresh TTL .... Rate limit ....
         Lockout ............ Repo ............. Package ........
DEC-46 : FCM [có/không]  APNs [có/không]  SMTP production [có/không]
DEC-48 : [A / B / C]         ghi chú: ..........................................
```

### D.2. MAJOR — trả lời gộp được

```
DEC-04 [a/b]   DEC-07 [a/b]   DEC-08 [a/b/c]  DEC-09 [a/b/c]  DEC-10 [a/b]
DEC-11 [a/b]   DEC-13 [a/b]   DEC-14 [a/b]    DEC-16 [a/b/c]  DEC-18 [a/b/c]
DEC-19 [a/b]   DEC-23 [a/b/c] DEC-26 [a/b/c]  DEC-28 [a/b]    DEC-29 [a/b]
DEC-30 [a/b]   DEC-31 [a/b]   DEC-34 [a/b/c]  DEC-35 [a/b]    DEC-36 [a/b]
DEC-38 [a/b]   DEC-39 [a/b]   DEC-40 [a/b]    DEC-44 [a/b/c + chi tiết]
DEC-45 [a/b]   DEC-47 [a/b]   DEC-49 [a/b]    DEC-50 [a/b]    DEC-51 [a/b]
DEC-52 [a/b/c] DEC-54 [a/b/c]
```

### D.3. MINOR

```
DEC-17 [Y/N]  DEC-20 [Y/N]  DEC-21 [Y/N]  DEC-22 [Y/N]  DEC-24 [Y/N]
DEC-25 [Y/N]  DEC-32 [Y/N + ngưỡng]  DEC-33 [Y/N]  DEC-37 [Y/N]
DEC-53 [Y/N]
```

### D.4. Giả định kỹ thuật (Phần B)

```
Chấp nhận toàn bộ ASM-01..ASM-37 : [CÓ / KHÔNG]
Nếu KHÔNG, liệt kê mục bác bỏ và phương án thay thế:
............................................................................
```

### D.5. Phê duyệt phase

```
Phê duyệt PH-0.5 (Môi trường & Skeleton)  : [CÓ / KHÔNG / chờ]
Phê duyệt PH-1 (Foundation)               : [CÓ / KHÔNG / chờ]
Cho phép `git init` tại D:\task            : [CÓ / KHÔNG]
Cho phép chạy Docker Compose local         : [CÓ / KHÔNG]
```

---

## PHẦN E — Thống kê

| Loại | Số lượng | Ghi chú |
|---|---|---|
| Quyết định FROZEN (Phần A) | 11 nhóm + 10 ARCH + 24 NFR = **45** mục | Không được sửa |
| Giả định kỹ thuật ASM (Phần B) | **37** | Chưa được duyệt, có thể bị bác |
| Quyết định cần xác nhận DEC (Phần C) | **54** (13 BLOCKER + 31 MAJOR + 10 MINOR) | Không tự quyết |
| Phát hiện audit AUD (`00-AUDIT-SPEC.md`) | **58** + 1 mục ghi chú gộp (AUD-01..50 trên đặc tả FROZEN; AUD-51..53 trên chính bản draft ERD/ID/RBAC; AUD-54..58 từ rà soát chéo X-01..X-09 khi lập `07-TEST-PLAN.md`; AUD-59 ghi chú 4 mục X không cần sửa tài liệu) | Mỗi phát hiện trỏ tới ≥1 DEC, **ngoại trừ** AUD-54, AUD-56, AUD-58, AUD-59 (đã sửa ngay, không sinh DEC) |
| Bất biến dữ liệu I-nn (`02A §9`) | **23** (9 ép hoàn toàn bằng constraint/index, 3 một phần, 11 không thể bằng constraint thuần; 16 có trigger đầy đủ, 2 trigger DEFERRED) | **18/23 vẫn cần transaction/service logic** → bắt buộc test âm |
| Race case (`05 §10`) | **15** | Đều đã có cơ chế chặn và test tương ứng |
| Recovery scenario (`05 §9`) | **8** | — |

**Nguyên tắc áp dụng:** chừng nào Phần D chưa được điền, tôi **chỉ** tiếp tục công việc phân tích/thiết kế tài liệu; **không** viết code, **không** tạo database, **không** `git init`, **không** cài công cụ, **không** commit/push/deploy.
