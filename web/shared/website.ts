export const trustedKeyHeader = "x-auth-trusted-key";

export const relayKeyHeader = "x-mojang-relay-key";

export const minecraftName = /^\w{1,16}$/u;

export const serverIdPattern = /^[0-9a-f]{40}$/u;

export interface JoinQuestion {
  readonly username: string;
  readonly serverId: string;
}

export const joinUrl = (base: string, { username, serverId }: JoinQuestion) => {
  const url = new URL(base);
  url.searchParams.set("username", username);
  url.searchParams.set("serverId", serverId);
  return url;
};

export const readJoinQuery = (query: URLSearchParams): JoinQuestion | undefined => {
  const username = query.get("username") ?? "";
  const serverId = query.get("serverId") ?? "";
  return minecraftName.test(username) && serverIdPattern.test(serverId) ? { username, serverId } : undefined;
};
