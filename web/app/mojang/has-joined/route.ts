import { Effect } from "effect";

import { sameSecret } from "../../../shared/secret";
import { joinUrl, readJoinQuery, relayKeyHeader, trustedKeyHeader } from "../../../shared/website";
import { relayKey, trustedKey } from "../../env";

const mojang = "https://sessionserver.mojang.com/session/minecraft/hasJoined";

export async function GET(request: Request) {
  // @keep A backend from before the relay key sends only the trusted key. Drop this once none is deployed.
  const legacy = sameSecret(request.headers.get(trustedKeyHeader), trustedKey());
  if (!sameSecret(request.headers.get(relayKeyHeader), relayKey()) && !legacy) {
    return new Response(null, { status: 404 });
  }
  const question = readJoinQuery(new URL(request.url).searchParams);
  if (question === undefined) {
    return new Response(null, { status: 400 });
  }
  const url = joinUrl(mojang, question);
  return Effect.runPromise(
    Effect.tryPromise(async (signal) => {
      const answer = await fetch(url, { cache: "no-store", signal });
      return new Response(answer.status === 204 ? null : await answer.text(), { status: answer.status });
    }).pipe(
      Effect.timeout("5 seconds"),
      Effect.orElseSucceed(() => new Response(null, { status: 502 })),
    ),
  );
}
