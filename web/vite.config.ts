import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

export default defineConfig({
  plugins: [react()],
  base: "/",
  build: {
    outDir: "../src/main/resources/copypaster/web",
    emptyOutDir: true,
    assetsDir: "assets",
  },
});
