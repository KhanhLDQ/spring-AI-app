import react from "@vitejs/plugin-react";
import { defineConfig } from "vite";

// In dev the UI runs on :5173 and forwards API calls to Spring Boot; the packaged jar serves the build itself.
export default defineConfig({
  plugins: [react()],
  server: { proxy: { "/api": "http://localhost:8080" } },
});
