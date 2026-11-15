"use server";

import { Effect } from "effect";
import { cookies } from "next/headers";
import * as signIn from "./signIn";

// Next refuses a server action whose Origin names another host, so another site cannot answer for the player. It lets
// a request with no Origin through, which a browser does not send for a cross-site POST.
// A server action's arguments are whatever the caller sent, so each is checked to be a string.

const text = (value: unknown) => (typeof value === "string" ? value : "");

export async function begin(token: string) {
  return Effect.runPromise(signIn.begin(text(token), await cookies()));
}

export async function finish(choice: string, user: string) {
  return Effect.runPromise(signIn.finish(text(choice), text(user), await cookies()));
}
