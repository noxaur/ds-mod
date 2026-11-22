import type { BetterAuthPlugin } from "better-auth";
import { APIError, createAuthEndpoint } from "better-auth/api";
import { setSessionCookie } from "better-auth/cookies";
import { Schema } from "effect";

import { trustedKeyHeader } from "./trusted.ts";

const PROVIDER = "minecraft";
const CHALLENGE_SECONDS = 60;

/** The account Mojang says joined, with the UUID in its dashed form. */
export interface Joined {
  readonly uuid: string;
  readonly name: string;
}

/** Asks Mojang whether [name] joined with [serverId]. Null when it did not, and an error when Mojang cannot say. */
export type HasJoined = (name: string, serverId: string) => Promise<Joined | null>;

const Profile = Schema.Struct({ id: Schema.String.check(Schema.isPattern(/^[0-9a-f]{32}$/u)), name: Schema.String });
const Verify = Schema.toStandardSchemaV1(
  Schema.Struct({
    name: Schema.String.check(Schema.isPattern(/^\w{1,16}$/u)),
    serverId: Schema.String.check(Schema.isPattern(/^[0-9a-f]{40}$/u)),
  }),
);

const unavailable = (why: string) => new APIError("SERVICE_UNAVAILABLE", { message: why });

export interface MojangRelay {
  readonly url: string;
  readonly key: string;
}

export const mojangHasJoined =
  (relay?: MojangRelay): HasJoined =>
  async (name, serverId) => {
    const url = new URL(relay?.url ?? "https://sessionserver.mojang.com/session/minecraft/hasJoined");
    url.searchParams.set("username", name);
    url.searchParams.set("serverId", serverId);
    // Only a 204 means nobody joined. Anything else is Mojang failing, which the player can retry, not a refusal.
    const response = await fetch(url, { headers: relay ? { [trustedKeyHeader]: relay.key } : {} }).catch(() => {
      throw unavailable("Mojang did not answer.");
    });
    if (response.status === 204) {
      return null;
    }
    if (response.status !== 200) {
      throw unavailable(`Mojang answered ${String(response.status)}.`);
    }
    const profile = Schema.decodeUnknownSync(Profile)(await response.json());
    return { uuid: profile.id.replace(/^(.{8})(.{4})(.{4})(.{4})(.{12})$/, "$1-$2-$3-$4-$5"), name: profile.name };
  };

/**
 * Signs a player in as the Minecraft account they are playing on.
 *
 * Nothing the client sends is believed. The backend picks a random server id, the client tells Mojang it is joining
 * that server, which only the holder of the account's Minecraft session can do, and the backend asks Mojang who
 * joined. A modified client can therefore sign in as its own account and as no other.
 */
export const minecraft = (hasJoined: HasJoined) =>
  ({
    id: PROVIDER,
    endpoints: {
      minecraftChallenge: createAuthEndpoint("/minecraft/challenge", { method: "POST" }, async (ctx) => {
        const serverId = Array.from(crypto.getRandomValues(new Uint8Array(20)), (byte) =>
          byte.toString(16).padStart(2, "0"),
        ).join("");
        // Nothing else removes a challenge or a login link nobody used, so each new challenge clears the expired ones.
        await ctx.context.adapter.deleteMany({
          model: "verification",
          where: [{ field: "expiresAt", operator: "lt", value: new Date() }],
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
          body: Verify,
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
            const email = `${joined.uuid}@minecraft.invalid`;
            // D1 has no transactions, so a sign-in can make the user and fail before the link. Only this plugin makes
            // users, so the next sign-in links the user its email names instead of failing on that email forever.
            user =
              (await ctx.context.internalAdapter.findUserByEmail(email))?.user ??
              (await ctx.context.internalAdapter.createUser(
                { name: joined.name, email, emailVerified: false },
                { method: PROVIDER },
              ));
            await ctx.context.internalAdapter.createAccount({
              userId: user.id,
              providerId: PROVIDER,
              accountId: joined.uuid,
            });
          }
          if (user.name !== joined.name) {
            user = await ctx.context.internalAdapter.updateUser(user.id, { name: joined.name });
          }
          const session = await ctx.context.internalAdapter.createSession(user.id);
          await setSessionCookie(ctx, { session, user });
          return ctx.json({ token: session.token, uuid: joined.uuid, name: joined.name });
        },
      ),
    },
  }) satisfies BetterAuthPlugin;
