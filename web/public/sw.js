// Public shell only. API responses and credentials are never cached.
const CACHE_NAME = "smarttask-shell-v2";
const STATIC_ASSETS = ["/index.html", "/manifest.webmanifest"];
self.addEventListener("install",event=>{
  event.waitUntil(caches.open(CACHE_NAME).then(cache=>cache.addAll(STATIC_ASSETS)));
  // Existing tabs opt in through the update banner before activating a new shell.
});
self.addEventListener("message",event=>{if(event.data?.type==="SKIP_WAITING")void self.skipWaiting();});
self.addEventListener("activate",event=>{
  event.waitUntil(caches.keys().then(keys=>Promise.all(keys.filter(key=>key.startsWith("smarttask-")&&key!==CACHE_NAME).map(key=>caches.delete(key)))).then(()=>self.clients.claim()));
});
self.addEventListener("fetch",event=>{
  const url=new URL(event.request.url);
  if(event.request.method!=="GET"||url.origin!==self.location.origin||url.pathname.startsWith("/api/")||url.pathname.startsWith("/actuator/")||url.pathname.startsWith("/ws"))return;
  if(event.request.mode==="navigate"){
    event.respondWith(fetch(event.request).then(async response=>{
      if(response.ok&&response.headers.get("content-type")?.includes("text/html")){
        try{const cache=await caches.open(CACHE_NAME);await cache.put("/index.html",response.clone());}
        catch{/* A full cache must not replace a fresh online response with stale HTML. */}
      }
      return response;
    }).catch(async()=>await caches.match("/index.html")||Response.error()));
  }
});
function notificationTarget(value){
  try{const url=new URL(value||"/notifications",self.location.origin);return url.origin===self.location.origin?url.pathname+url.search+url.hash:"/notifications";}catch{return "/notifications";}
}

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
  const targetUrl = notificationTarget(event.notification.data?.url);

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
  const targetUrl = notificationTarget(event.notification.data?.url);
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
