import {Link} from "react-router-dom";
import {useAccess,roleLabel} from "../auth/AccessContext";
export default function RoleGuide(){
  const access=useAccess();
  return <section className="personal-tasks mx-auto max-w-5xl space-y-4"><h1 className="page-title">Vai trò và việc cần làm</h1>
    <p>Quyền được xác định riêng trong từng Workspace và Project. Công việc cá nhân chỉ thuộc bạn. Workspace cá nhân không mời thêm thành viên; chức năng mời cộng tác áp dụng cho Workspace nhóm.</p>
    {access.loading&&<p role="status">Đang tải quyền…</p>}{access.error&&<p role="alert">{access.error}<button onClick={access.reload}>Tải lại quyền</button></p>}
    <div className="overflow-x-auto"><table className="table"><thead><tr><th>Vai trò Workspace</th><th>Việc cần làm</th><th>Giới hạn</th></tr></thead><tbody>
      <tr><td>Owner</td><td>Tổ chức Workspace, tạo Project, mời thành viên, quản lý nhóm; chuyển quyền sở hữu khi bàn giao.</td><td>Chỉ Owner được xóa/chuyển sở hữu Workspace. Quyền Project phải được cấp riêng.</td></tr>
      <tr><td>Admin</td><td>Vận hành Workspace, mời thành viên, tạo Project và quản lý nhóm.</td><td>Không xóa/chuyển sở hữu Workspace; không có quyền quản trị hệ thống.</td></tr>
      <tr><td>Member</td><td>Mở việc được giao, bắt đầu, tick checklist, ghi thời gian, bình luận và nộp hoàn thành.</td><td>Không tự tạo Project, giao việc hoặc phê duyệt. Sửa nội dung/giao việc chỉ khi được cấp quyền riêng.</td></tr>
      <tr><td>Guest</td><td>Theo dõi Project tham gia và Task được chia sẻ/giao để xem.</td><td>Không sửa Task, tạo subtask, bình luận, ghi thời gian hay đổi trạng thái.</td></tr>
    </tbody></table></div>
    <article className="card"><h2>Quản lý dự án (Manager)</h2><p>Manager lập Task/subtask, thiết lập checklist và phụ thuộc, giao/chuyển giao việc, quản lý thành viên và quyền riêng, đặt hạn, duyệt hoặc yêu cầu sửa kết quả khi được chỉ định và đủ điều kiện. Không tự duyệt việc mình nộp hoặc được giao. Vai trò này chỉ áp dụng cho Project được giao; Owner/Admin Workspace không tự có quyền đọc mọi nội dung Project.</p><p>Project PLANNING: chuẩn bị công việc, chưa bắt đầu/nộp. ACTIVE: thực hiện và phê duyệt. ON_HOLD/COMPLETED/ARCHIVED hoặc Workspace lưu trữ: chỉ xem.</p></article>
    <article className="card"><h2>Workspace của bạn</h2><ul>{access.workspaces.map(w=><li key={w.id}><Link to={`/projects?workspace=${w.id}`}>{w.name}</Link> — {roleLabel(w.role)} · {w.status}<p>{w.canManage?"Cần làm: quản lý nhóm, lời mời và tổ chức Project.":"Cần làm: theo dõi các Project được cấp quyền; liên hệ Owner/Admin để thay đổi thành viên."}</p></li>)}</ul></article>
    <article className="card"><h2>Project của bạn</h2><ul>{access.projects.map(p=><li key={p.id}><Link to={`/projects/${p.id}?tab=tasks`}>{p.name}</Link> — {roleLabel(p.role)} · {p.status}<p>{p.canManage?"Cần làm: lập và giao việc, theo dõi tiến độ, xử lý kết quả chờ duyệt.":p.role==="MEMBER"?"Cần làm: xử lý Task được giao; báo tiến độ, vướng mắc và nộp kết quả.":"Cần làm: theo dõi dữ liệu được chia sẻ; liên hệ quản lý khi cần điều chỉnh."}{p.metadata?" Có quyền sửa thông tin công việc.":""}{p.assignment?" Có quyền giao/chuyển giao công việc.":""}</p></li>)}</ul></article>
    <p><Link to="/my-tasks">Việc cá nhân</Link> · <Link to="/tasks">Công việc dự án</Link></p>
  </section>;
}
