# SmartTask Manager

SmartTask là hệ thống quản lý công việc cá nhân và nhóm gồm React web, Spring Boot API, PostgreSQL, Flutter mobile và Docker Compose production.

- Phát triển Windows với Vite/HMR: [docs/LOCAL_DEVELOPMENT.md](docs/LOCAL_DEVELOPMENT.md)
- Triển khai Docker/Ubuntu, backup và restore: [DEPLOY.md](DEPLOY.md)
- Báo cáo tổng quan hiện trạng: [PROJECT-REPORT-2026-11-09.md](PROJECT-REPORT-2026-11-09.md)
- Audit/nghiệm thu 13/11/2026 — **NOT READY**: [báo cáo và các gate chặn phát hành](docs/PRODUCTION-ACCEPTANCE-2026-11-13.md)
- Runbook sau audit: [vận hành, kiểm thử, backup/restore và rollback](docs/PRODUCTION-OPERATIONS-2026-11-13.md)
- Kiểm thử giao diện local: [4 tài khoản demo, Task mẫu và tạo subtask theo Project](docs/LOCAL-DEMO-ACCOUNTS.md)
- Phân quyền: [vai trò, trách nhiệm và ràng buộc từng giao diện](docs/ROLE-INTERFACE-MATRIX.md)

Không commit `infra/.env`, `deploy/.env`, password, token hoặc dữ liệu backup.
