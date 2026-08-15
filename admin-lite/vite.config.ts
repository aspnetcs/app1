import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";
import tailwindcss from "@tailwindcss/vite";

const adminLiteBase = process.env.NODE_ENV === "production" ? "./" : "/";

export default defineConfig({
  base: adminLiteBase,
  plugins: [react(), tailwindcss()],
  server: {
    port: 5175,
    host: "0.0.0.0",
    proxy: {
      "/api/v1/auth": {
        target: "http://localhost:8080",
        changeOrigin: true,
      },
      "/api/v1/risk": {
        target: "http://localhost:8080",
        changeOrigin: true,
      },
      "/api": {
        target: "http://localhost:8081",
        changeOrigin: true,
      },
    },
  },
  preview: {
    port: 4175,
    host: "0.0.0.0",
    proxy: {
      "/api/v1/auth": {
        target: "http://localhost:8080",
        changeOrigin: true,
      },
      "/api/v1/risk": {
        target: "http://localhost:8080",
        changeOrigin: true,
      },
      "/api": {
        target: "http://localhost:8081",
        changeOrigin: true,
      },
    },
  },
  build: {
    outDir: "dist",
    emptyOutDir: true,
  },
});
