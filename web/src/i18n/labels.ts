/** Chỉ đổi nhãn hiển thị; giữ nguyên mã gửi và nhận qua API. */
const labels:Record<string,string>={
  TODO:"Cần làm",IN_PROGRESS:"Đang thực hiện",PENDING_APPROVAL:"Chờ duyệt",COMPLETED:"Hoàn thành",CANCELLED:"Đã hủy",
  PLANNING:"Lập kế hoạch",ACTIVE:"Đang hoạt động",ON_HOLD:"Tạm dừng",ARCHIVED:"Đã lưu trữ",PENDING:"Đang chờ",APPROVED:"Đã duyệt",REJECTED:"Đã từ chối",LOCKED:"Đã khóa",DISABLED:"Đã tắt",ENABLED:"Đã bật",
  AVAILABLE:"Sẵn sàng",UPLOADING:"Đang tải lên",STAGED:"Đang xử lý",DELETED:"Đã xóa",FAILED:"Thất bại",QUEUED:"Đang chờ xử lý",RUNNING:"Đang xử lý",SUCCEEDED:"Thành công",DONE:"Hoàn tất",ACCEPTED:"Đã chấp nhận",APPLIED:"Đã áp dụng",EXPIRED:"Đã hết hạn",REVOKED:"Đã thu hồi",VALID:"Hợp lệ",OK:"Bình thường",PARTIAL:"Có một phần dữ liệu",EMPTY:"Chưa có dữ liệu",NOT_AVAILABLE:"Chưa có dữ liệu",NA:"Chưa có dữ liệu",
  OWNER:"Chủ sở hữu",ADMIN:"Quản trị viên",MANAGER:"Quản lý",MEMBER:"Thành viên",GUEST:"Khách",VIEWER:"Người xem",PERSONAL:"Cá nhân",PROJECT:"Dự án",TEAM:"Nhóm",SHARED:"Được chia sẻ",WEB:"Trình duyệt",
  TASK_DRAFT:"Soạn công việc",SUBTASK_SUGGEST:"Gợi ý việc con",PRIORITY_SUGGEST:"Gợi ý ưu tiên",DEADLINE_SUGGEST:"Gợi ý thời hạn",ASSIGNEE_SUGGEST:"Gợi ý người phụ trách",DAILY_PLANNER:"Lập kế hoạch trong ngày",WEEKLY_REVIEW:"Tổng kết tuần",
  title:"Tiêu đề",description:"Mô tả",priority:"Ưu tiên",due_at:"Hạn hoàn thành",assignee_id:"Người phụ trách",subtasks:"Việc con",reason:"Lý do",summary:"Tóm tắt",recommendation:"Đề xuất",LOW:"Thấp",MEDIUM:"Trung bình",HIGH:"Cao",URGENT:"Khẩn cấp",
};
export function statusLabel(value?:string|null):string {return value?labels[value]??"Chưa xác định":"Chưa có dữ liệu";}

const activityLabels:Record<string,string>={
  "task.created":"Đã tạo công việc","task.updated":"Đã cập nhật công việc","task.assign":"Đã giao việc","task.assigned":"Đã giao việc","task.deadline":"Đã cập nhật thời hạn",
  "workflow.start":"Đã bắt đầu","workflow.submit":"Đã nộp hoàn thành","workflow.approve":"Đã duyệt","workflow.reject":"Đã yêu cầu chỉnh sửa","workflow.cancel":"Đã hủy","workflow.reopen":"Đã mở lại",
  TASK_CREATED:"Đã tạo công việc",TASK_UPDATED:"Đã cập nhật công việc",TASK_ASSIGNED:"Đã giao việc",COMMENT_ADDED:"Đã thêm bình luận",CHECKLIST_UPDATED:"Đã cập nhật danh sách kiểm tra",
};
export function activityLabel(value:string):string{return activityLabels[value]??"Cập nhật công việc";}

export function formatDate(value?:string|null):string {
  if(!value)return "Chưa đặt";
  const date=new Date(/^\d{4}-\d{2}-\d{2}$/.test(value)?`${value}T12:00:00`:value);
  return Number.isNaN(date.getTime())?"Ngày không hợp lệ":date.toLocaleDateString("vi-VN");
}
