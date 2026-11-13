import { Effect } from "effect";
import { backendIsUp } from "./backend";

export default async function Home() {
  const up = await Effect.runPromise(backendIsUp);
  return (
    <main>
      <h1>ds-mod</h1>
      <p>Backend: {up ? "up" : "down"}</p>
    </main>
  );
}
