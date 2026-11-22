import type { BetterAuthOptions } from "better-auth";

import { sameSecret } from "../../../web/shared/secret.ts";
import { trustedKeyHeader } from "../../../web/shared/website.ts";

const websiteCalls = ["/one-time-token/verify", "/get-session"];

type CustomRules = NonNullable<NonNullable<BetterAuthOptions["rateLimit"]>["customRules"]>;

export const websiteRules = (trustedKey: string | undefined): CustomRules =>
  Object.fromEntries(
    websiteCalls.map((path) => [
      path,
      (request: Request, rule: { window: number; max: number }) =>
        sameSecret(request.headers.get(trustedKeyHeader), trustedKey) ? false : rule,
    ]),
  );
