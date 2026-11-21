import type { BetterAuthOptions } from "better-auth";

export const trustedKeyHeader = "x-auth-trusted-key";

const websiteCalls = ["/one-time-token/verify", "/get-session"];

type CustomRules = NonNullable<NonNullable<BetterAuthOptions["rateLimit"]>["customRules"]>;

export const websiteRules = (trustedKey: unknown): CustomRules =>
  Object.fromEntries(
    websiteCalls.map((path) => [
      path,
      (request: Request, rule: { window: number; max: number }) =>
        typeof trustedKey === "string" && trustedKey !== "" && request.headers.get(trustedKeyHeader) === trustedKey
          ? false
          : rule,
    ]),
  );
