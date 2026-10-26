# Tiến độ triển khai UI SmartTask

## G0/G1 — đang thực hiện (26-10-2026 ICT)

- Baseline commit: `9344d7a new`.
- Thay đổi có trước phiên: `docs/UI_UX_AUDIT_AND_REDESIGN_PROPOSAL.md` (untracked, được giữ nguyên).
- Đã thêm route Dashboard, triển khai Dashboard không dùng dữ liệu giả; chỉ tính KPI từ `GET /tasks` trong phạm vi quyền API trả về.
- Đã chuẩn hóa AppShell theo hướng SaaS: sidebar có navigation theo nhóm, header và trạng thái account/realtime.
- Đã mở rộng primitives với `Skeleton`, `StatusBadge`, error retry.
- `npm run build` đã PASS: TypeScript và Vite production build thành công.
- `npm test` FAIL do Vitest trong sandbox không đọc được các artefact mock tạm ở `C:\Users\PC\AppData\Local\Packages\sandbox...\Temp\...`; 3 suite bị lỗi import/mock (`Guards`, `Tasks`, `WorkflowPanel`), 1 suite/2 tests pass. Đây chưa phải lỗi assertion từ thay đổi UI.
- `npm run lint` FAIL ở baseline: `eslint.config.js` không khai báo TypeScript parser, trong khi project lint `*.ts,*.tsx`; toàn bộ source (cả file không thay đổi) bị parse error. `@typescript-eslint/parser`/`typescript-eslint` không có trong dependencies, nên chưa tự ý thêm/cài đặt dependency trong checkpoint này.
- Tasks đã có toggle Danh sách/Kanban. Kanban chỉ là trình bày từ workflow state thật, không kéo-thả hay mutation trực tiếp.
- Projects dùng `GET /workspaces` để render Workspace select theo tên cho filter và tạo dự án. ID chỉ còn state nội bộ gửi đúng contract API.
- Calendar reminder và Files task scope dùng `GET /tasks` để chọn công việc theo tên. Calendar vẫn dùng API reminder thật; input datetime-local được gửi theo contract hiện có và backend tiếp tục là nơi xác minh timezone/format.
- Files giữ nguyên chức năng restore-version hiện có; nhãn version client-only được làm rõ là thao tác trong phiên, không được diễn giải như lịch sử version đầy đủ của backend.
- AI Assistant và Reports dùng `GET /projects` để chọn scope dự án theo tên, không yêu cầu nhập UUID.
- Workspaces được Việt hóa lại, không còn hiển thị UUID như nội dung card chính, và có empty/error retry state rõ ràng.
- Notification Center có filter client-side theo dữ liệu `kind` thực tế (tất cả/chưa đọc/công việc/nhắc việc/hệ thống); không gửi query chưa được backend công bố.

## Tiếp theo

1. Quyết định/cài dependency ESLint TypeScript hoặc thay chiến lược lint hợp lệ, rồi khôi phục lint CI.
2. Refactor Workspaces và Task filters/forms bằng lookup API phân quyền; hiện Projects đã hoàn thành picker Workspace từ API thật.
3. Hoàn thiện Tasks create/detail, Calendar, Files, Reports, AI, Admin; không bịa API chưa có.
