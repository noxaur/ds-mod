import { defineConfig } from "void/config";

export default defineConfig({
  worker: {
    compatibility_date: "2026-02-24",
  },
  cloudflare: {
    name: "dsmod-backend",
    // @keep The mod bakes this address into its jar, so changing it strands every player on an older release.
    routes: [{ pattern: "api.ds-mod.perwko.com", custom_domain: true }],
    // @keep Void deploys only when these match the live Worker.
    workers_dev: false,
    preview_urls: false,
  },
});
