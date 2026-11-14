import { Effect } from "effect";

const backendUrl = process.env["BACKEND_URL"] ?? "http://localhost:8080";

export const backendIsUp = Effect.tryPromise(() => fetch(`${backendUrl}/healthz`, { cache: "no-store" })).pipe(
  Effect.map((response) => response.ok),
  Effect.timeout("2 seconds"),
  Effect.orElseSucceed(() => false),
);
