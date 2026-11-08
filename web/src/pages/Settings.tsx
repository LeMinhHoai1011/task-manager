import React, { useEffect, useState } from "react";
import { useAuth } from "../auth/AuthContext";
import { useTheme } from "../components/ThemeContext";
import { useToast } from "../components/ToastContext";
import { apiFetch } from "../api/client";
import AppIcon from "../components/AppIcon";

interface DeviceItem {
  id: string;
  device_label?: string;
  platform: string;
  status: string;
  token_hint: string;
  registered_at: string;
}

export default function Settings() {
  const { user } = useAuth();
  const { theme, setTheme } = useTheme();
  const { showToast } = useToast();

  const [activeTab, setActiveTab] = useState<
    "profile" | "appearance" | "language" | "notifications" | "devices" | "security" | "backup"
  >("profile");

  // Notifications preference state
  const [notifyPreferences, setNotifyPreferences] = useState<{ [type: string]: boolean }>({
    TASK_ASSIGNED: true,
    DEADLINE_WARNING: true,
    WORKFLOW_STATUS: true,
    COMMENT_NEW: true,
    PROJECT_UPDATE: true,
  });

  const [quietHoursStart, setQuietHoursStart] = useState("22:00");
  const [quietHoursEnd, setQuietHoursEnd] = useState("07:00");
  const [quietHoursEnabled, setQuietHoursEnabled] = useState(false);

  // Devices & Push
  const [devices, setDevices] = useState<DeviceItem[]>([]);
  const [pushSupported, setPushSupported] = useState(false);
  const [pushSubscribed, setPushSubscribed] = useState(false);
  const [subscribingPush, setSubscribingPush] = useState(false);

  // Security password change form
  const [currentPassword, setCurrentPassword] = useState("");
  const [newPassword, setNewPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [changingPass, setChangingPass] = useState(false);

  useEffect(() => {
    // Check if Service Worker & Push Notification is supported
    if (typeof window !== "undefined" && "serviceWorker" in navigator && "PushManager" in window) {
      setPushSupported(true);
      // Kiểm tra push subscription thực tế từ Service Worker (không chỉ dựa trên permission)
      navigator.serviceWorker.ready.then((reg) => {
        reg.pushManager.getSubscription().then((sub) => {
          setPushSubscribed(sub !== null);
        }).catch(() => setPushSubscribed(false));
      }).catch(() => setPushSubscribed(false));
    }
    loadDevices();
    loadPreferences();
  }, []);

  const loadDevices = async () => {
    try {
      const res = await apiFetch<any>(`/notifications/devices`);
      const items = (res?.data || res || []) as DeviceItem[];
      setDevices(items);
    } catch {}
  };

  const loadPreferences = async () => {
    try {
      const res = await apiFetch<any>(`/notifications/preferences`);
      const items = (res?.data || res || []) as Array<{ type: string; muted: boolean }>;
      const map: { [t: string]: boolean } = { ...notifyPreferences };
      items.forEach((p) => {
        map[p.type] = !p.muted;
      });
      setNotifyPreferences(map);
    } catch {}
  };

  const handleTogglePreference = async (type: string) => {
    const nextVal = !notifyPreferences[type];
    setNotifyPreferences((prev) => ({ ...prev, [type]: nextVal }));
    try {
      await apiFetch(`/notifications/preferences`, {
        method: "PUT",
        body: JSON.stringify({ type, muted: !nextVal }),
      });
      showToast({ type: "success", message: "Đã lưu cài đặt thông báo" });
    } catch {
      showToast({ type: "error", message: "Lỗi lưu cài đặt thông báo" });
    }
  };

  const handleSubscribeWebPush = async () => {
    if (!pushSupported) {
      showToast({ type: "error", message: "Trình duyệt không hỗ trợ Web Push Notification" });
      return;
    }

    setSubscribingPush(true);
    try {
      // 1. Xin quyền thông báo (chỉ khi người dùng nhấn nút, không popup tự động)
      const permission = await Notification.requestPermission();
      if (permission !== "granted") {
        showToast({
          type: "warning",
          message: "Bạn đã từ chối quyền thông báo trên trình duyệt.",
        });
        setPushSubscribed(false);
        return;
      }

      // 2. Lấy VAPID public key từ backend
      let vapidPublicKey: string | null = null;
      try {
        const vres = await apiFetch<any>(`/notifications/vapid/public-key`);
        vapidPublicKey = vres?.data?.publicKey ?? vres?.publicKey ?? null;
      } catch {
        // Nếu backend chưa có endpoint, vẫn tiếp कोशिश với vapid key null (sẽ lỗi nếu browser yêu cầu)
      }

      // 3. Đăng ký Service Worker (đã đăng ký ở main.tsx)
      const swRegistration = await navigator.serviceWorker.ready;

      // 4. Chuẩn bị applicationServerKey từ VAPID public key
      let applicationServerKey: Uint8Array | undefined;
      if (vapidPublicKey) {
        applicationServerKey = urlBase64ToUint8Array(vapidPublicKey);
      }

      // 5. Subscribe Push API
      const subscription = await swRegistration.pushManager.subscribe({
        userVisibleOnly: true,
        applicationServerKey,
      });

      const subJson = subscription.toJSON();
      const deviceLabel = `Trình duyệt ${navigator.userAgent.includes("Chrome") ? "Chrome" : "Browser"} (${window.innerWidth}px)`;

      // 6. Gửi subscription lên backend
      await apiFetch(`/notifications/devices`, {
        method: "POST",
        body: JSON.stringify({
          platform: "WEB_PUSH",
          push_token: JSON.stringify(subJson),
          device_label: deviceLabel,
          endpoint: subJson.endpoint ?? "",
          auth_key: subJson.keys?.auth ?? "",
          p256dh_key: subJson.keys?.p256dh ?? "",
        }),
      });

      setPushSubscribed(true);
      showToast({
        type: "success",
        message: "Kích hoạt Web Push thành công! Bạn sẽ nhận được thông báo về công việc.",
      });
      loadDevices();
      // Xác nhận trạng thái đăng ký từ backend
      try {
        const vres = await apiFetch<any>(`/notifications/vapid/subscription-status`);
        setPushSubscribed(vres?.data?.subscribed ?? vres?.subscribed ?? true);
      } catch {}
    } catch (e: any) {
      showToast({
        type: "error",
        message: e?.uiMessage || "Không thể đăng ký Web Push",
      });
    } finally {
      setSubscribingPush(false);
    }
  };

  // Chuyển base64url sang Uint8Array cho PushManager.subscribe
  function urlBase64ToUint8Array(base64String: string): Uint8Array {
    const padding = "=".repeat((4 - (base64String.length % 4)) % 4);
    const base64 = (base64String + padding).replace(/-/g, "+").replace(/_/g, "/");
    const raw = window.atob(base64);
    const output = new Uint8Array(raw.length);
    for (let i = 0; i < raw.length; i++) {
      output[i] = raw.charCodeAt(i);
    }
    return output;
  }

  const handleUnregisterDevice = async (id: string) => {
    try {
      await apiFetch(`/notifications/devices/${id}`, { method: "DELETE" });
      setDevices((prev) => prev.filter((d) => d.id !== id));
      showToast({ type: "info", message: "Đã hủy đăng ký thiết bị" });
    } catch {
      showToast({ type: "error", message: "Lỗi hủy thiết bị" });
    }
  };

  const handleChangePassword = async (e: React.FormEvent) => {
    e.preventDefault();
    if (newPassword !== confirmPassword) {
      showToast({ type: "error", message: "Mật khẩu xác nhận không khớp" });
      return;
    }
    setChangingPass(true);
    try {
      await apiFetch(`/auth/change-password`, {
        method: "POST",
        body: JSON.stringify({
          current_password: currentPassword,
          new_password: newPassword,
        }),
      });
      showToast({ type: "success", message: "Đổi mật khẩu thành công!" });
      setCurrentPassword("");
      setNewPassword("");
      setConfirmPassword("");
    } catch (e: any) {
      showToast({ type: "error", message: e?.uiMessage || "Không thể đổi mật khẩu" });
    } finally {
      setChangingPass(false);
    }
  };

  return (
    <div className="mx-auto max-w-5xl space-y-6">
      {/* Header */}
      <div>
        <h1 className="page-title text-2xl font-bold tracking-tight text-slate-900 dark:text-white flex items-center gap-2">
          <AppIcon name="settings" size={24} className="text-primary" />
          <span>Cài đặt hệ thống</span>
        </h1>
        <p className="page-subtitle text-xs text-slate-500 mt-1">
          Quản lý tài khoản, giao diện sáng/tối, thông báo đẩy Web Push, thiết bị và bảo mật.
        </p>
      </div>

      <div className="flex flex-col md:flex-row gap-6">
        {/* Navigation Tabs */}
        <div className="w-full md:w-56 shrink-0 flex md:flex-col gap-1 overflow-x-auto pb-2 md:pb-0 text-xs font-semibold">
          {[
            { id: "profile", label: "Hồ sơ cá nhân", icon: "overview" },
            { id: "appearance", label: "Giao diện (Theme)", icon: "report" },
            { id: "language", label: "Ngôn ngữ", icon: "roles" },
            { id: "notifications", label: "Thông báo & Giờ nghỉ", icon: "bell" },
            { id: "devices", label: "Thiết bị & Web Push", icon: "team" },
            { id: "security", label: "Bảo mật & Mật khẩu", icon: "settings" },
            { id: "backup", label: "Dữ liệu & Sao lưu", icon: "files" },
          ].map((tab) => (
            <button
              key={tab.id}
              onClick={() => setActiveTab(tab.id as any)}
              className={`flex items-center gap-2.5 px-3.5 py-2.5 rounded-xl transition text-left whitespace-nowrap ${
                activeTab === tab.id
                  ? "bg-primary text-white shadow-xs"
                  : "text-slate-600 dark:text-slate-400 hover:bg-slate-100 dark:hover:bg-slate-800"
              }`}
            >
              <AppIcon name={tab.icon} size={16} />
              <span>{tab.label}</span>
            </button>
          ))}
        </div>

        {/* Content Panel */}
        <div className="flex-1 rounded-2xl border border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-900 p-6 shadow-xs">
          {/* TAB 1: PROFILE */}
          {activeTab === "profile" && (
            <div className="space-y-4 max-w-lg text-sm">
              <h3 className="font-bold text-base text-slate-900 dark:text-white border-b border-slate-100 dark:border-slate-800 pb-2">
                Thông tin tài khoản
              </h3>
              <div>
                <label className="block text-xs font-semibold text-slate-500 mb-1">
                  Địa chỉ Email
                </label>
                <input
                  type="text"
                  disabled
                  value={user?.email || ""}
                  className="input text-xs bg-slate-50 dark:bg-slate-850 cursor-not-allowed"
                />
              </div>
              <div>
                <label className="block text-xs font-semibold text-slate-500 mb-1">
                  Vai trò hệ thống (RBAC)
                </label>
                <input
                  type="text"
                  disabled
                  value={user?.role || "MEMBER"}
                  className="input text-xs bg-slate-50 dark:bg-slate-850 cursor-not-allowed font-semibold text-primary"
                />
              </div>
            </div>
          )}

          {/* TAB 2: APPEARANCE */}
          {activeTab === "appearance" && (
            <div className="space-y-6 max-w-lg">
              <h3 className="font-bold text-base text-slate-900 dark:text-white border-b border-slate-100 dark:border-slate-800 pb-2">
                Chế độ giao diện (Appearance)
              </h3>
              <p className="text-xs text-slate-500">
                Tùy chỉnh phong cách hiển thị phù hợp với môi trường làm việc của bạn.
              </p>

              <div className="grid grid-cols-2 gap-4">
                <button
                  type="button"
                  onClick={() => setTheme("light")}
                  className={`p-4 rounded-xl border-2 text-center transition ${
                    theme === "light"
                      ? "border-primary bg-primary/5"
                      : "border-slate-200 dark:border-slate-800 hover:border-slate-300"
                  }`}
                >
                  <div className="text-2xl mb-1">☀️</div>
                  <div className="font-bold text-sm text-slate-900 dark:text-white">Giao diện Sáng</div>
                  <div className="text-[11px] text-slate-400 mt-1">Chuẩn Modern Productivity</div>
                </button>

                <button
                  type="button"
                  onClick={() => setTheme("dark")}
                  className={`p-4 rounded-xl border-2 text-center transition ${
                    theme === "dark"
                      ? "border-primary bg-primary/5"
                      : "border-slate-200 dark:border-slate-800 hover:border-slate-300"
                  }`}
                >
                  <div className="text-2xl mb-1">🌙</div>
                  <div className="font-bold text-sm text-slate-900 dark:text-white">Giao diện Tối</div>
                  <div className="text-[11px] text-slate-400 mt-1">Dễ chịu ban đêm</div>
                </button>
              </div>
            </div>
          )}

          {/* TAB 3: LANGUAGE */}
          {activeTab === "language" && (
            <div className="space-y-4 max-w-lg">
              <h3 className="font-bold text-base text-slate-900 dark:text-white border-b border-slate-100 dark:border-slate-800 pb-2">
                Ngôn ngữ hiển thị
              </h3>
              <div className="space-y-2">
                <label className="flex items-center gap-3 p-3 rounded-xl border border-slate-200 dark:border-slate-800 cursor-pointer">
                  <input
                    type="radio"
                    name="lang"
                    checked={localStorage.getItem("tm.locale") !== "en"}
                    onChange={() => {
                      localStorage.setItem("tm.locale", "vi");
                      window.location.reload();
                    }}
                    className="text-primary"
                  />
                  <div>
                    <div className="font-bold text-sm text-slate-900 dark:text-white">
                      🇻🇳 Tiếng Việt
                    </div>
                    <div className="text-xs text-slate-500">Mặc định hệ thống</div>
                  </div>
                </label>

                <label className="flex items-center gap-3 p-3 rounded-xl border border-slate-200 dark:border-slate-800 cursor-pointer">
                  <input
                    type="radio"
                    name="lang"
                    checked={localStorage.getItem("tm.locale") === "en"}
                    onChange={() => {
                      localStorage.setItem("tm.locale", "en");
                      window.location.reload();
                    }}
                    className="text-primary"
                  />
                  <div>
                    <div className="font-bold text-sm text-slate-900 dark:text-white">
                      🇬🇧 English
                    </div>
                    <div className="text-xs text-slate-500">Standard English interface</div>
                  </div>
                </label>
              </div>
            </div>
          )}

          {/* TAB 4: NOTIFICATIONS */}
          {activeTab === "notifications" && (
            <div className="space-y-6 max-w-lg">
              <h3 className="font-bold text-base text-slate-900 dark:text-white border-b border-slate-100 dark:border-slate-800 pb-2">
                Tùy chọn nhận thông báo
              </h3>

              <div className="space-y-3">
                {[
                  { id: "TASK_ASSIGNED", title: "Khi được giao công việc mới" },
                  { id: "DEADLINE_WARNING", title: "Cảnh báo sắp đến hạn (trong 24h) hoặc quá hạn" },
                  { id: "WORKFLOW_STATUS", title: "Khi công việc được duyệt hoặc yêu cầu sửa đổi" },
                  { id: "COMMENT_NEW", title: "Khi có bình luận mới trong công việc tham gia" },
                  { id: "PROJECT_UPDATE", title: "Khi có cập nhật tiến độ dự án mới" },
                ].map((item) => (
                  <label
                    key={item.id}
                    className="flex items-center justify-between p-3 rounded-xl border border-slate-100 dark:border-slate-800 hover:bg-slate-50 dark:hover:bg-slate-850 cursor-pointer"
                  >
                    <span className="text-xs font-semibold text-slate-800 dark:text-slate-200">
                      {item.title}
                    </span>
                    <input
                      type="checkbox"
                      checked={!!notifyPreferences[item.id]}
                      onChange={() => handleTogglePreference(item.id)}
                      className="h-4 w-4 rounded text-primary"
                    />
                  </label>
                ))}
              </div>

              {/* Quiet hours */}
              <div className="pt-4 border-t border-slate-100 dark:border-slate-800 space-y-3">
                <div className="flex items-center justify-between">
                  <div>
                    <h4 className="font-bold text-sm text-slate-900 dark:text-white">
                      Giờ yên lặng (Quiet Hours)
                    </h4>
                    <p className="text-xs text-slate-500">
                      Không làm phiền thông báo đẩy trong khung giờ nghỉ
                    </p>
                  </div>
                  <input
                    type="checkbox"
                    checked={quietHoursEnabled}
                    onChange={(e) => setQuietHoursEnabled(e.target.checked)}
                    className="h-4 w-4 rounded text-primary"
                  />
                </div>

                {quietHoursEnabled && (
                  <div className="grid grid-cols-2 gap-3 text-xs pt-1">
                    <div>
                      <label className="block text-slate-400 mb-1">Bắt đầu từ</label>
                      <input
                        type="time"
                        value={quietHoursStart}
                        onChange={(e) => setQuietHoursStart(e.target.value)}
                        className="input text-xs"
                      />
                    </div>
                    <div>
                      <label className="block text-slate-400 mb-1">Kết thúc lúc</label>
                      <input
                        type="time"
                        value={quietHoursEnd}
                        onChange={(e) => setQuietHoursEnd(e.target.value)}
                        className="input text-xs"
                      />
                    </div>
                  </div>
                )}
              </div>
            </div>
          )}

          {/* TAB 5: DEVICES & WEB PUSH */}
          {activeTab === "devices" && (
            <div className="space-y-6 max-w-xl">
              <div>
                <h3 className="font-bold text-base text-slate-900 dark:text-white border-b border-slate-100 dark:border-slate-800 pb-2">
                  Thiết bị & Web Push Notification
                </h3>
                <p className="text-xs text-slate-500 mt-1">
                  Nhận thông báo tức thì trên trình duyệt hoặc thiết bị di động ngay cả khi không mở tab SmartTask.
                </p>
              </div>

              {/* Web Push Action Box */}
              <div className="p-4 rounded-xl border border-primary/20 bg-primary/5 flex items-start justify-between gap-4">
                <div>
                  <h4 className="font-bold text-sm text-slate-900 dark:text-white">
                    Thông báo đẩy trình duyệt (Web Push)
                  </h4>
                  <p className="text-xs text-slate-600 dark:text-slate-300 mt-1">
                    {pushSubscribed
                      ? "✓ Đã cấp quyền thông báo trên trình duyệt này."
                      : "Nhấn nút bên dưới để cấp quyền nhận thông báo đẩy cho trình duyệt này (không popup tự động)."}
                  </p>
                </div>
                <button
                  onClick={handleSubscribeWebPush}
                  disabled={subscribingPush || pushSubscribed}
                  className="btn btn-primary text-xs shrink-0"
                >
                  {subscribingPush
                    ? "Đang kích hoạt..."
                    : pushSubscribed
                    ? "✓ Đã kích hoạt"
                    : "Kích hoạt Web Push"}
                </button>
              </div>

              {/* Registered Devices List */}
              <div className="space-y-3">
                <h4 className="font-bold text-sm text-slate-900 dark:text-white">
                  Danh sách thiết bị đã đăng ký ({devices.length})
                </h4>

                {devices.length === 0 ? (
                  <p className="text-xs text-slate-400 py-3">
                    Chưa có thiết bị nào được liên kết nhận push.
                  </p>
                ) : (
                  <div className="divide-y divide-slate-100 dark:divide-slate-800 border rounded-xl overflow-hidden">
                    {devices.map((d) => (
                      <div
                        key={d.id}
                        className="p-3 flex items-center justify-between text-xs hover:bg-slate-50 dark:hover:bg-slate-850"
                      >
                        <div>
                          <div className="font-semibold text-slate-800 dark:text-slate-200">
                            {d.device_label || d.platform}
                          </div>
                          <div className="text-[11px] text-slate-400">
                            Token: {d.token_hint} • Ngày đk:{" "}
                            {new Date(d.registered_at).toLocaleDateString("vi-VN")}
                          </div>
                        </div>
                        <button
                          onClick={() => handleUnregisterDevice(d.id)}
                          className="text-xs text-rose-500 hover:underline"
                        >
                          Hủy đăng ký
                        </button>
                      </div>
                    ))}
                  </div>
                )}
              </div>
            </div>
          )}

          {/* TAB 6: SECURITY */}
          {activeTab === "security" && (
            <div className="space-y-4 max-w-md">
              <h3 className="font-bold text-base text-slate-900 dark:text-white border-b border-slate-100 dark:border-slate-800 pb-2">
                Đổi mật khẩu
              </h3>
              <form onSubmit={handleChangePassword} className="space-y-3 text-xs">
                <div>
                  <label className="block font-semibold text-slate-600 dark:text-slate-400 mb-1">
                    Mật khẩu hiện tại
                  </label>
                  <input
                    type="password"
                    required
                    value={currentPassword}
                    onChange={(e) => setCurrentPassword(e.target.value)}
                    className="input text-xs"
                  />
                </div>
                <div>
                  <label className="block font-semibold text-slate-600 dark:text-slate-400 mb-1">
                    Mật khẩu mới (tối thiểu 8 ký tự)
                  </label>
                  <input
                    type="password"
                    required
                    minLength={8}
                    value={newPassword}
                    onChange={(e) => setNewPassword(e.target.value)}
                    className="input text-xs"
                  />
                </div>
                <div>
                  <label className="block font-semibold text-slate-600 dark:text-slate-400 mb-1">
                    Xác nhận mật khẩu mới
                  </label>
                  <input
                    type="password"
                    required
                    value={confirmPassword}
                    onChange={(e) => setConfirmPassword(e.target.value)}
                    className="input text-xs"
                  />
                </div>

                <button
                  type="submit"
                  disabled={changingPass}
                  className="btn btn-primary text-xs mt-2"
                >
                  {changingPass ? "Đang cập nhật..." : "Cập nhật mật khẩu"}
                </button>
              </form>
            </div>
          )}

          {/* TAB 7: BACKUP & DATA */}
          {activeTab === "backup" && (
            <div className="space-y-4 max-w-lg">
              <h3 className="font-bold text-base text-slate-900 dark:text-white border-b border-slate-100 dark:border-slate-800 pb-2">
                Dữ liệu & Sao lưu (Admin)
              </h3>
              <p className="text-xs text-slate-500">
                Chính sách sao lưu cơ sở dữ liệu định kỳ trên Ubuntu PostgreSQL và MinIO.
              </p>
              <div className="p-4 rounded-xl border border-slate-200 dark:border-slate-800 bg-slate-50 dark:bg-slate-850 text-xs space-y-2">
                <div>
                  Tình trạng lưu trữ: <span className="font-semibold text-emerald-600">Hoạt động bình thường</span>
                </div>
                <div>Cơ sở dữ liệu: PostgreSQL 16 (Flyway V1..V17)</div>
                <div>Hỗ trợ kịch bản: `deploy/backup.sh`, `deploy/restore.sh`</div>
              </div>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
