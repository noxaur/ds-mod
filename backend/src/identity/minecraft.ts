import type { BetterAuthPlugin } from "better-auth";
import { APIError, createAuthEndpoint } from "better-auth/api";
import { setSessionCookie } from "better-auth/cookies";
import * as z from "zod";

const PROVIDER = "minecraft";
const CHALLENGE_SECONDS = 60;

/** The account Mojang says joined, with the UUID in its dashed form. */
export interface Joined {
  readonly uuid: string;
  readonly name: string;
}

/** Asks Mojang whether [name] joined with [serverId]. Null when it did not. */
export type HasJoined = (name: string, serverId: string) => Promise<Joined | null>;

export const mojangHasJoined: HasJoined = async (name, serverId) => {
  const url = new URL("https://sessionserver.mojang.com/session/minecraft/hasJoined");
  url.searchParams.set("username", name);
  url.searchParams.set("serverId", serverId);
  const response = await fetch(url);
  if (response.status !== 200) {
    return null;
  }
  const profile = z.object({ id: z.string().regex(/^[0-9a-f]{32}$/), name: z.string() }).parse(await response.json());
  return { uuid: profile.id.replace(/^(.{8})(.{4})(.{4})(.{4})(.{12})$/, "$1-$2-$3-$4-$5"), name: profile.name };
};

/**
 * Signs a player in as the Minecraft account they are playing on.
 *
 * Nothing the client sends is believed. The backend picks a random server id, the client tells Mojang it is joining
 * that server, which only the holder of the account's Minecraft session can do, and the backend asks Mojang who
 * joined. A modified client can therefore sign in as its own account and as no other.
 */
export const minecraft = (hasJoined: HasJoined = mojangHasJoined) =>
  ({
    id: PROVIDER,
    endpoints: {
      minecraftChallenge: createAuthEndpoint("/minecraft/challenge", { method: "POST" }, async (ctx) => {
        const serverId = Array.from(crypto.getRandomValues(new Uint8Array(20)), (byte) =>
          byte.toString(16).padStart(2, "0"),
        ).join("");
        // Nothing else removes a challenge nobody answered, so each new one clears the expired ones.
        await ctx.context.adapter.deleteMany({
          model: "verification",
          where: [
            { field: "identifier", operator: "starts_with", value: `${PROVIDER}:` },
            { field: "expiresAt", operator: "lt", value: new Date() },
          ],
        });
        await ctx.context.internalAdapter.createVerificationValue({
          identifier: `${PROVIDER}:${serverId}`,
          value: serverId,
          expiresAt: new Date(Date.now() + CHALLENGE_SECONDS * 1000),
        });
        return ctx.json({ serverId });
      }),
      minecraftVerify: createAuthEndpoint(
        "/minecraft/verify",
        {
          method: "POST",
          body: z.object({ name: z.string().regex(/^\w{1,16}$/), serverId: z.string().regex(/^[0-9a-f]{40}$/) }),
        },
        async (ctx) => {
          // Consumed before Mojang is asked, so one challenge buys one question.
          const challenge = await ctx.context.internalAdapter.consumeVerificationValue(
            `${PROVIDER}:${ctx.body.serverId}`,
          );
          if (!challenge) {
            throw new APIError("UNAUTHORIZED", { message: "The challenge is unknown, used or expired." });
          }
          const joined = await hasJoined(ctx.body.name, ctx.body.serverId);
          if (joined === null) {
            throw new APIError("UNAUTHORIZED", { message: "Mojang did not see this account join." });
          }
          const account = await ctx.context.adapter.findOne<{ userId: string }>({
            model: "account",
            where: [
              { field: "providerId", value: PROVIDER },
              { field: "accountId", value: joined.uuid },
            ],
          });
          let user = account === null ? null : await ctx.context.internalAdapter.findUserById(account.userId);
          if (user === null) {
            // Better Auth needs an email, and a player has none. This one can never receive mail.
            user = await ctx.context.internalAdapter.createUser(
              {
                name: joined.name,
                email: `${joined.uuid}@minecraft.invalid`,
                emailVerified: false,
              },
              { method: PROVIDER },
            );
            await ctx.context.internalAdapter.createAccount({
              userId: user.id,
              providerId: PROVIDER,
              accountId: joined.uuid,
            });
          } else if (user.name !== joined.name) {
            user = await ctx.context.internalAdapter.updateUser(user.id, { name: joined.name });
          }
          const session = await ctx.context.internalAdapter.createSession(user.id);
          await setSessionCookie(ctx, { session, user });
          return ctx.json({ token: session.token, uuid: joined.uuid, name: joined.name });
        },
      ),
    },
  }) satisfies BetterAuthPlugin;
