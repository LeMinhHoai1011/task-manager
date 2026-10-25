// Duong dan endpoint REST (base /api/v1 da nam trong ApiClient).
//
// Trang thai doi chieu backend F4 (ProjectController, TaskController,
// WorkspaceProjectController, ProjectTaskController, DeviceController,
// RealtimeController, ...):
// - Nested `/workspaces/{id}/projects` va `/projects/{id}/tasks` DA co (F4,
//   additive) nhung tra DTO rut gon (khong co due_at/priority/assignee...).
//   Mobile van dung QUERY `/projects?workspace_id=` va `/tasks?project_id=`
//   lam primary vi response day du truong; nested la alias cho client moi.
// - Devices endpoints (`GET/POST/DELETE /notifications/devices`, EP-176..178)
//   DA co tren backend. PushRegistrar van degrade 404 (tuong thich backend cu).
// - Realtime: transport la SSE `GET /realtime/stream?ticket=...` (ticket
//   single-use TTL 60s, khong access token trong query). RealtimeClient dung SSE.
// - AI: `/ai/request` (body camelCase theo record AiRequest: feature,
//   projectId, inputHint, requestedFields, idempotencyKey), `/ai/jobs/{id}`,
//   `/ai/jobs/{id}/suggestion`, `/ai/jobs/{id}/accept`, `/ai/jobs/{id}/reject`.
// - Export download `GET /export-jobs/{id}/download` tra BYTES tran (khong
//   phai JSON) — dung downloadBytes + luu file, khong parse JSON.
library;

/// Tai lieu tham khao nhanh cac path chinh. Khong chua logic goi HTTP.
abstract final class ApiPaths {
  static const login = '/auth/login';
  static const logout = '/auth/logout';
  static const refresh = '/auth/refresh';
  static const reauthChallenge = '/auth/reauth/challenge';
  static const reauthVerify = '/auth/reauth/verify';

  static String workspaces({String? cursor, int limit = 20}) =>
      '/workspaces?limit=$limit${cursor == null ? '' : '&cursor=$cursor'}';
  static String workspace(String id) => '/workspaces/$id';

  /// Query la primary (day du truong); nested da co (F4) nhung DTO rut gon.
  /// `/workspaces/{id}/projects` giu lam alias cho client moi.
  static String projects(String workspaceId) =>
      '/projects?workspace_id=$workspaceId';
  static String project(String id) => '/projects/$id';

  /// Query la primary (day du truong); nested da co (F4) nhung DTO rut gon.
  /// `/projects/{id}/tasks` giu lam alias cho client moi.
  static String tasks(String projectId) => '/tasks?project_id=$projectId';
  static String task(String id) => '/tasks/$id';
  static String taskWorkflow(String id, String verb) =>
      '/tasks/$id/workflow/$verb';
  static String taskComments(String taskId) => '/tasks/$taskId/comments';
  // PH-3 files: upload_session -> staging -> commit; gateway tickets single-use TTL 30s.
  static const fileUploadInit = '/files/upload';
  static String fileUploadComplete(String sessionId) =>
      '/files/upload/$sessionId/complete';
  static String fileUploadCommit(String sessionId) =>
      '/files/upload/$sessionId/commit';
  static String filePreviewTicket(String attachmentId) =>
      '/files/$attachmentId/preview';
  static String fileDownloadTicket(String attachmentId) =>
      '/files/$attachmentId/download';
  static String fileStream(String ticket) => '/files/stream/$ticket';
  static String fileRestoreVersion(String attachmentId) =>
      '/files/$attachmentId/restore';
  static String attachments(String taskId) => '/files/tasks/$taskId/attachments';
  static String attachmentDownload(String attachmentId) =>
      '/files/$attachmentId/download';
  // PH-4 calendar & reminders (views: day/week/month/unscheduled).
  static const calendar = '/calendar';
  static const reminders = '/reminders';
  static String reminderPause(String id) => '/reminders/$id/pause';
  static String reminderResume(String id) => '/reminders/$id/resume';
  static const notifications = '/notifications';
  static const notificationReadAll = '/notifications/read-all';
  static String notificationRead(String id) => '/notifications/$id/read';

  /// Devices endpoints DA co tren backend (EP-176..178). PushRegistrar van
  /// degrade 404 de tuong thich backend cu; KHONG dang ky token fake.
  /// 404 khong phai loi nghiem trong.
  static const devices = '/notifications/devices';
  static String device(String registrationId) =>
      '/notifications/devices/$registrationId';

  // PH-6 reports & export (TTL 24h, requester-only). Download tra bytes tran.
  static const reportsDashboard = '/reports/dashboard';
  static const reportExportJobs = '/reports/export-jobs';
  static String exportDownload(String jobId) => '/export-jobs/$jobId/download';
  static const reportsSummary = '/reports/dashboard';

  // AI toi thieu: request draft + status + confirm/reject qua API.
  // Khong inference local (mo ta package).
  static const aiRequest = '/ai/request';
  static String aiJob(String id) => '/ai/jobs/$id';
  static String aiSuggestion(String id) => '/ai/jobs/$id/suggestion';
  static String aiAccept(String id) => '/ai/jobs/$id/accept';
  static String aiReject(String id) => '/ai/jobs/$id/reject';

  // F0 WS ticket: cap qua REST da auth session (POST), handshake chi mang
  // ticket single-use TTL 60s — khong gui access_token qua query/frame.
  static const realtimeTicket = '/realtime/ticket';
}
