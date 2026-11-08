import { useEffect, useState } from "react";
import AppIcon from "./AppIcon";

export default function OfflineBanner() {
  const [isOnline, setIsOnline] = useState<boolean>(() => {
    return typeof navigator !== "undefined" ? navigator.onLine : true;
  });

  useEffect(() => {
    const handleOnline = () => setIsOnline(true);
    const handleOffline = () => setIsOnline(false);

    window.addEventListener("online", handleOnline);
    window.addEventListener("offline", handleOffline);

    return () => {
      window.removeEventListener("online", handleOnline);
      window.removeEventListener("offline", handleOffline);
    };
  }, []);

  if (isOnline) return null;

  return (
    <div
      role="status"
      className="bg-amber-500 text-slate-950 font-medium px-4 py-2 flex items-center justify-center gap-2 text-sm shadow-sm sticky top-0 z-50 animate-fade-in"
    >
      <AppIcon name="warning" size={16} />
      <span>Bạn đang ngoại tuyến (Offline). Các thay đổi sẽ được lưu cục bộ và đồng bộ khi có mạng trở lại.</span>
    </div>
  );
}
