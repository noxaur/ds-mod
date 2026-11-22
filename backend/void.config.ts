import { defineConfig } from "void/config";

/** @keep Calls a signed-in player may make in a minute. The mod sends items once in 20 minutes and asks for one profile
 * at a time, so a real player stays far under each. A namespace id names the count in the Cloudflare account, so changing
 * one starts it afresh. */
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
