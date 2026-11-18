# Ponytail trong SmartTask Manager

## Trạng thái cài đặt — 14/11/2026

Đã cài `ponytail@ponytail` phiên bản **5.3.3** bằng hệ thống plugin của Codex.
`codex plugin list` xác nhận `installed, enabled`. Người dùng đã đồng ý cài cấp
người dùng và yêu cầu **chưa tin cậy hook**. Không chạy hook hoặc sửa thiết lập trust.

Nguồn đã đọc:

- [README chính thức](https://github.com/DietrichGebert/ponytail/blob/96608e078b2d5ea607c5c3b8b5a54d1c1fba6846/README.md)
- [INSTALL chính thức](https://github.com/DietrichGebert/ponytail/blob/96608e078b2d5ea607c5c3b8b5a54d1c1fba6846/INSTALL.md)
- [Manifest Codex](https://github.com/DietrichGebert/ponytail/blob/96608e078b2d5ea607c5c3b8b5a54d1c1fba6846/.codex-plugin/plugin.json)
- [Hướng dẫn marketplace của OpenAI](https://developers.openai.com/plugins/build/plugins)

Commit marketplace tại thời điểm cài: `96608e078b2d5ea607c5c3b8b5a54d1c1fba6846`.
Đã kiểm tra nội dung manifest, hook và các module nội bộ liên quan trước khi cài;
đây không phải chứng nhận bảo mật toàn bộ source hoặc các phiên bản tương lai.

## Môi trường và phương thức

| Mục | Giá trị kiểm tra |
| --- | --- |
| Repository | `D:\task` |
| Branch | `codex/readiness-hardening-20261113` |
| Codex CLI | `0.160.0` |
| Node.js | `v26.10.0`, có trên PATH |
| Git | `2.49.0.windows.1` |
| Thay đổi có sẵn | 46 file thay đổi/chưa được theo dõi, đã lưu hash đối chiếu |
| AGENTS/config project trước cài | Không tìm thấy `AGENTS.md`, `AGENTS.override.md`, `.agents` hoặc `.codex` trong repo; không có AGENTS tại `D:\` hoặc Codex home được kiểm tra |
| Skills riêng trước cài | `.system`, `frontend-design`; skills từ các plugin vẫn giữ nguyên |

Đã dùng đúng các lệnh chính thức, không chạy `npx`, npm install hoặc script cài bên ngoài:

```powershell
codex plugin marketplace add DietrichGebert/ponytail --json
codex plugin add ponytail@ponytail --json
```

## Phạm vi và file

- Cấp người dùng: `C:\Users\PC\.codex\config.toml` chỉ thêm marketplace Ponytail
  và mục bật plugin; cấu hình model, MCP, notify, project trust và plugin cũ giữ nguyên.
- Marketplace: `C:\Users\PC\.codex\.tmp\marketplaces\ponytail`.
- Plugin: `C:\Users\PC\.codex\plugins\cache\ponytail\ponytail\5.3.3`.
- Repository: thêm `AGENTS.md` với nguyên tắc đơn giản, tái sử dụng và bảo toàn hành vi;
  thêm tài liệu này. Không ghi đè AGENTS hay cấu hình project đã tồn tại.
- Snapshot cục bộ không đưa vào Git: `.tools/ponytail/baseline.json`,
  `.tools/ponytail/codex-config.before.toml`. Bản sao config có thể chứa thông tin
  cấu hình riêng tư; không chia sẻ/commit. `.tools/` đã được Git ignore từ trước.
- Không thay đổi dependency, lockfile, code nghiệp vụ, migration, database hoặc
  cấu hình production. Không commit/push/reset Git.

Cài plugin cấp người dùng có thể áp dụng cho các project khác sau khi client nạp lại.
`AGENTS.md` mới chỉ áp dụng cho repository này. Tắt mode/plugin không tắt các quy tắc
bảo toàn dữ liệu và hành vi được ghi riêng trong AGENTS.

## Sử dụng

Khởi động lại ứng dụng Codex và mở chat mới trong repository này để nạp plugin.
Chat đang thực hiện cài đặt chưa xác minh trực tiếp skill picker sau reload.
Codex CLI/IDE dùng tên skill có namespace theo README chính thức:

| Skill | Mục đích |
| --- | --- |
| `$ponytail:ponytail` | Áp dụng nguyên tắc Ponytail; mức `lite`, `full`, `ultra`, `off` theo hỗ trợ của host |
| `$ponytail:ponytail-review` | Review diff, staged/uncommitted, branch hoặc PR; chỉ báo cáo |
| `$ponytail:ponytail-audit` | Audit repo hoặc thư mục được chỉ định; chỉ báo cáo |
| `$ponytail:ponytail-debt` | Tổng hợp các comment `shortcut:` đã ghi nhận |
| `$ponytail:ponytail-gain` | Trình bày số liệu benchmark của Ponytail, không phải phép đo riêng project này |
| `$ponytail:ponytail-help` | Hướng dẫn lệnh |

Ví dụ sau khi reload:

```text
$ponytail:ponytail-review uncommitted
Chỉ lập báo cáo bằng tiếng Việt. Không sửa file. Đánh giá cả caller ngoài diff.

$ponytail:ponytail-audit
Ưu tiên backend task/workflow và web Tasks; nêu rõ phần chưa kiểm tra.
Chỉ đề xuất tối ưu có bằng chứng, không refactor hoặc thay đổi database.
```

Chọn từng finding để yêu cầu sửa ở bước tiếp theo. Không dùng audit làm ủy quyền
cho việc xóa code hoặc tái cấu trúc hàng loạt. Không chạy review/audit toàn bộ trong
lượt cài đặt này; chỉ xác minh file skill hợp lệ và nội dung chỉ lập báo cáo.

## Hook, xung đột và giới hạn

- Theo INSTALL, cần mở `/hooks` để xem và tự trust hook, sau đó bắt đầu chat mới.
  Lượt này cố ý không làm bước trust. Kích hoạt tự động và chuyển mode bền qua phiên
  chưa được kiểm thử; các workflow skill độc lập có thể dùng sau khi host nạp chúng.
- Tài liệu nói hai lifecycle hook cho Codex. Manifest dùng chung Claude/Codex khai báo
  ba event: `SessionStart`, `UserPromptSubmit`, `SubagentStart`. Không giả định Codex
  hỗ trợ tất cả event chỉ vì manifest có khai báo; xem các event thực tế trong `/hooks`.
- Hook dùng Node built-in, có đọc source để tạo codebase map và ghi mode vào
  `PLUGIN_DATA` trong Codex. Lệnh đổi default có thể ghi cấu hình Ponytail trong
  `%APPDATA%\ponytail\config.json`. Các nhánh dành cho host khác có thêm side effect;
  không chạy hook thủ công thiếu môi trường Codex, không tự cấu hình statusline/band.
- `band`/`board` trong README dành cho Claude Code, không coi là tính năng Codex.
- Đã đối chiếu config trước/sau: chỉ thêm `[marketplaces.ponytail]` và
  `[plugins."ponytail@ponytail"]`; không thấy xung đột mục cấu hình. Plugin không
  khai báo MCP tại `.mcp.json` và không cần thêm dependency runtime của SmartTask.
- Không có `.exe`/`.dll` trong thư mục plugin đã cài. Chỉ lấy package từ GitHub
  chính thức; chưa chạy mã lifecycle tải về.
- Marketplace và source plugin đang trỏ `main` theo upstream, không pin commit.
  Trước lần cập nhật sau cần xem lại diff, đặc biệt các hook; không tự động upgrade.
- Trong sandbox, CLI báo lỗi tạo PATH alias và không truy cập được remote catalog
  ChatGPT. Local catalog vẫn xác nhận Ponytail installed/enabled. Đừng diễn giải
  trạng thái `not installed` của remote-curated listing là plugin/app desktop bị mất;
  môi trường desktop còn cung cấp plugin qua runtime riêng.

## Kiểm tra và gỡ cài đặt

Kiểm tra metadata:

```powershell
codex plugin marketplace list
codex plugin list
```

Đã xác minh manifest JSON và đủ sáu `SKILL.md`, bao gồm review và audit. Web build
(`pnpm build`, gồm TypeScript) pass sau cài. Đối chiếu hash để bảo toàn các thay đổi
có sẵn. Không chạy backend/database, full E2E hoặc smoke test ứng dụng đang phục vụ;
đây là tích hợp hướng dẫn, không phải chứng nhận production readiness.

Nếu muốn gỡ, yêu cầu riêng trước khi thực hiện:

```powershell
codex plugin remove ponytail@ponytail
codex plugin marketplace remove ponytail
```

Không khôi phục nguyên bản config bằng cách ghi đè từ snapshot vì có thể làm mất
các thay đổi Codex mới hơn. Chỉ gỡ các mục Ponytail bằng lệnh chính thức. `AGENTS.md`
và tài liệu project được quản lý độc lập; không tự xóa các file này khi gỡ plugin.
