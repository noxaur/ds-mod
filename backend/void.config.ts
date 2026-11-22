import { defineConfig } from "void/config";

/** @keep Void loads this file on its own, so the limits are written out here, and a test holds them to `RATES` in
 * src/limiter.ts. A namespace id names the count in the Cloudflare account, so changing one starts it afresh. */
const rate = (name: string, namespace_id: string, limit: number) => ({
  name,
  namespace_id,
  simple: { limit, period: 60 },
});

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
    ratelimits: [rate("RATE_PLAYER", "2101", 120), rate("RATE_PROFILE", "2102", 30), rate("RATE_ITEMS", "2103", 5)],
  },
});
