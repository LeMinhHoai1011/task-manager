// SmartTask V3.0 Service Worker (PWA, Offline Caching, Web Push)
const CACHE_NAME = "smarttask-daylight-v1";
const STATIC_ASSETS = [
  "/",
  "/index.html",
  "/manifest.webmanifest"
];

self.addEventListener("install", (event) => {
  event.waitUntil(
    caches.open(CACHE_NAME).then((cache) => {
      return cache.addAll(STATIC_ASSETS);
    })
  );
  self.skipWaiting();
});

self.addEventListener("activate", (event) => {
  event.waitUntil(
    caches.keys().then((keys) => {
      return Promise.all(
        keys.filter((key) => key !== CACHE_NAME).map((key) => caches.delete(key))
      );
    })
  );
  self.clients.claim();
});

self.addEventListener("fetch", (event) => {
  // Bỏ qua các API và SSE requests để không can thiệp vào dynamic data
  if (event.request.url.includes("/api/") || event.request.url.includes("/ws")) {
    return;
  }
  event.respondWith(
    caches.match(event.request).then((cached) => {
      return (
        cached ||
        fetch(event.request).catch(() => {
          if (event.request.mode === "navigate") {
            return caches.match("/index.html");
          }
        })
      );
    })
  );
});

// Push notification listener
self.addEventListener("push", (event) => {
  let data = {
    title: "SmartTask Thông Báo",
    body: "Bạn có cập nhật mới.",
    url: "/notifications",
    taskId: null,
    projectId: null,
  };
  if (event.data) {
    try {
      data = { ...data, ...event.data.json() };
    } catch (e) {
      data.body = event.data.text();
    }
  }

  const options = {
    body: data.body,
    icon: "/favicon.ico",
    badge: "/favicon.ico",
    data: {
      url: data.url || "/notifications",
      taskId: data.taskId || null,
      projectId: data.projectId || null,
    },
    vibrate: [100, 50, 100],
    actions: [
      { action: "open", title: "Mở chi tiết" },
      { action: "close", title: "Đóng" },
    ],
    requireInteraction: false,
    tag: data.taskId || data.projectId || "smarttask-general",
  };

  event.waitUntil(self.registration.showNotification(data.title, options));
});

// Handle clicking on push notification
self.addEventListener("notificationclick", (event) => {
  event.notification.close();
  const targetUrl = event.notification.data?.url || "/dashboard";

  event.waitUntil(
    clients.matchAll({ type: "window", includeUncontrolled: true }).then((clientList) => {
      for (const client of clientList) {
        if (client.url.includes(targetUrl) && "focus" in client) {
          return client.focus();
        }
      }
      if (clients.openWindow) {
        return clients.openWindow(targetUrl);
      }
    })
  );
});

// Handle notification actions
self.addEventListener("notificationaction", (event) => {
  if (event.action === "close") {
    event.notification.close();
    return;
  }
  // "open" action - same as notificationclick
  const targetUrl = event.notification.data?.url || "/dashboard";
  event.waitUntil(
    clients.matchAll({ type: "window", includeUncontrolled: true }).then((clientList) => {
      for (const client of clientList) {
        if (client.url.includes(targetUrl) && "focus" in client) {
          return client.focus();
        }
      }
      if (clients.openWindow) {
        return clients.openWindow(targetUrl);
      }
    })
  );
});
