import {statusLabel} from "../i18n/labels";
import {Link} from "react-router-dom";
import {useAccess,roleLabel,interfaceProfile} from "../auth/AccessContext";
export default function RoleGuide(){
  const access=useAccess();
  const profile=interfaceProfile(access);
  return <section className="personal-tasks mx-auto max-w-5xl space-y-4"><h1 className="page-title">Vai trò và việc cần làm</h1>
    <p>Quyền được xác định riêng trong từng không gian làm việc và dự án. Công việc cá nhân chỉ thuộc bạn. Không gian làm việc cá nhân không mời thêm thành viên; chức năng mời cộng tác áp dụng cho không gian làm việc nhóm.</p>
    {!access.loading&&!access.error&&<article className="card"><h2>Giao diện của bạn: {profile.label}</h2><p>{profile.description}</p><Link className="btn btn-primary" to={profile.home}>Mở giao diện theo vai trò</Link></article>}
    {access.loading&&<p role="status">Đang tải quyền…</p>}{access.error&&<p role="alert">{access.error}<button onClick={access.reload}>Tải lại quyền</button></p>}
    <div className="overflow-x-auto"><table className="table"><thead><tr><th>Vai trò không gian làm việc</th><th>Việc cần làm</th><th>Giới hạn</th></tr></thead><tbody>
      <tr><td>Chủ sở hữu</td><td>Tổ chức không gian làm việc, tạo dự án, mời thành viên, quản lý nhóm; chuyển quyền sở hữu khi bàn giao.</td><td>Chỉ chủ sở hữu được xóa/chuyển sở hữu không gian làm việc. Quyền dự án phải được cấp riêng.</td></tr>
      <tr><td>Quản trị viên</td><td>Vận hành không gian làm việc, mời thành viên, tạo dự án và quản lý nhóm.</td><td>Không xóa/chuyển sở hữu không gian làm việc; không có quyền quản trị hệ thống.</td></tr>
      <tr><td>Thành viên</td><td>Mở việc được giao, bắt đầu, đánh dấu danh sách kiểm tra, ghi thời gian, bình luận và nộp hoàn thành.</td><td>Không tự tạo dự án, giao việc hoặc phê duyệt. Sửa nội dung/giao việc chỉ khi được cấp quyền riêng.</td></tr>
      <tr><td>Khách</td><td>Theo dõi dự án tham gia và công việc được chia sẻ/giao để xem.</td><td>Không sửa công việc, tạo việc con, bình luận, ghi thời gian hay đổi trạng thái.</td></tr>
    </tbody></table></div>
    <article className="card"><h2>Quản lý dự án</h2><p>Người quản lý lập công việc/việc con, thiết lập danh sách kiểm tra và phụ thuộc, giao/chuyển giao việc, quản lý thành viên và quyền riêng, đặt hạn, duyệt hoặc yêu cầu sửa kết quả khi được chỉ định và đủ điều kiện. Không tự duyệt việc mình nộp hoặc được giao. Vai trò này chỉ áp dụng cho dự án được giao; chủ sở hữu/quản trị viên không gian làm việc không tự có quyền đọc mọi nội dung dự án.</p><p>Dự án đang lập kế hoạch: chuẩn bị công việc, chưa bắt đầu hoặc nộp kết quả. Dự án đang hoạt động: thực hiện và phê duyệt. Dự án tạm dừng, hoàn thành, lưu trữ hoặc thuộc không gian đã lưu trữ: chỉ xem.</p></article>
    <article className="card"><h2>không gian làm việc của bạn</h2><ul>{access.workspaces.map(w=><li key={w.id}><Link to={`/projects?workspace=${w.id}`}>{w.name}</Link> — {roleLabel(w.role)} · {statusLabel(w.status)}<p>{w.canManage?"Cần làm: quản lý nhóm, lời mời và tổ chức dự án.":"Cần làm: theo dõi các dự án được cấp quyền; liên hệ chủ sở hữu/quản trị viên để thay đổi thành viên."}</p></li>)}</ul></article>
    <article className="card"><h2>dự án của bạn</h2><ul>{access.projects.map(p=><li key={p.id}><Link to={`/projects/${p.id}?tab=tasks`}>{p.name}</Link> — {roleLabel(p.role)} · {statusLabel(p.status)}<p>{p.canManage?"Cần làm: lập và giao việc, theo dõi tiến độ, xử lý kết quả chờ duyệt.":p.role==="MEMBER"?"Cần làm: xử lý công việc được giao; báo tiến độ, vướng mắc và nộp kết quả.":"Cần làm: theo dõi dữ liệu được chia sẻ; liên hệ quản lý khi cần điều chỉnh."}{p.metadata?" Có quyền sửa thông tin công việc.":""}{p.assignment?" Có quyền giao/chuyển giao công việc.":""}</p></li>)}</ul></article>
    <p><Link to="/my-tasks">Việc cá nhân</Link> · <Link to="/tasks">Công việc dự án</Link></p>
  </section>;
}
