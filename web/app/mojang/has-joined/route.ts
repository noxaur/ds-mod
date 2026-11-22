import { Effect } from "effect";

import { trustedKeyHeader } from "../../backend";
import { trustedKey } from "../../env";

const mojang = "https://sessionserver.mojang.com/session/minecraft/hasJoined";

export async function GET(request: Request) {
  const key = trustedKey();
  if (key === undefined || key === "" || request.headers.get(trustedKeyHeader) !== key) {
    return new Response(null, { status: 404 });
  }
  const asked = new URL(request.url).searchParams;
  const url = new URL(mojang);
  for (const param of ["username", "serverId"]) {
    url.searchParams.set(param, asked.get(param) ?? "");
  }
  return Effect.runPromise(
    Effect.tryPromise((signal) => fetch(url, { cache: "no-store", signal })).pipe(
      Effect.map((answer) => new Response(answer.body, { status: answer.status })),
      Effect.timeout("5 seconds"),
      Effect.orElseSucceed(() => new Response(null, { status: 502 })),
    ),
  );
}
