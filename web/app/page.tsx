import { Effect } from "effect";
import { cookies } from "next/headers";
import { backendIsUp, sessionOf, sessionCookie } from "./backend";

export default async function Home() {
  const sessionToken = (await cookies()).get(sessionCookie)?.value;
  const [up, session] = await Effect.runPromise(
    Effect.all([
      backendIsUp,
      sessionToken === undefined
        ? Effect.succeed(null)
        : sessionOf(sessionToken).pipe(Effect.orElseSucceed(() => null)),
    ]),
  );
  return (
    <main>
      <h1>Dragen</h1>
      <p>Backend: {up ? "up" : "down"}</p>
      <p>{session === null ? "Type /dragen login in game to sign in." : `Signed in as ${session.name}`}</p>
    </main>
  );
}
