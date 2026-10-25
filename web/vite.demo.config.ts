import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";
import tailwindcss from "@tailwindcss/vite";

// Demo-only config: the default dev server (vite.config.ts) proxies to :8080,
// which is occupied by Apache on this machine. This one proxies to the demo
// backend on :8081 and serves the UI on :5174.
export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: {
    port: 5174,
    strictPort: true,
    proxy: {
      "/api/v1": {
        target: "http://localhost:8081",
        changeOrigin: true,
      },
    },
  },
  build: { outDir: "dist", sourcemap: false },
});
