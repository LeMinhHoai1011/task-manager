import React from "react";
import ReactDOM from "react-dom/client";
import App from "./App";

// Đăng ký Service Worker cho PWA (offline cache + Web Push)
if (import.meta.env.PROD && "serviceWorker" in navigator) {
  window.addEventListener("load", () => {
    navigator.serviceWorker
      .register("/sw.js")
      .then((registration) => {
        const announce=()=>window.dispatchEvent(new CustomEvent("smarttask:update",{detail:registration}));
        if(registration.waiting)announce();
        registration.addEventListener("updatefound",()=>{
          const worker=registration.installing;
          worker?.addEventListener("statechange",()=>{if(worker.state==="installed"&&navigator.serviceWorker.controller)announce();});
        });
      })
      .catch((registrationError) => {
        console.log("SW registration failed: ", registrationError);
      });
  });
}

// Vite must serve the current UI instead of a previous PWA shell.
if (import.meta.env.DEV && "serviceWorker" in navigator) {
  void navigator.serviceWorker.getRegistrations().then((registrations) => {
    for (const registration of registrations) {
      const worker = registration.active ?? registration.waiting ?? registration.installing;
      if (worker && new URL(worker.scriptURL).pathname === "/sw.js") void registration.unregister();
    }
  });
  if ("caches" in window) void caches.keys().then((keys) => {
    for (const key of keys) if (key.startsWith("smarttask-")) void caches.delete(key);
  });
}

ReactDOM.createRoot(document.getElementById("root")!).render(
  <React.StrictMode>
    <App />
  </React.StrictMode>
);
