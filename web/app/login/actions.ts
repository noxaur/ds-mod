"use server";

import { Effect } from "effect";
import { cookies } from "next/headers";
import * as signIn from "./signIn";

// Next refuses a server action whose Origin is not this site, so another site cannot answer for the player.

export async function begin(token: string) {
  return Effect.runPromise(signIn.begin(typeof token === "string" ? token : "", await cookies()));
}

export async function finish(choice: string) {
  return Effect.runPromise(signIn.finish(choice, await cookies()));
}
