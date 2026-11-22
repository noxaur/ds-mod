import { expect, test } from "vite-plus/test";

import { joinUrl, readJoinQuery } from "./website";

const serverId = "ab".repeat(20);

test("a question survives the trip through a query, beside what the address already asks", () => {
  const url = joinUrl("https://website.test/relay?bypass=1", { username: "Notch", serverId });
  expect(readJoinQuery(url.searchParams)).toEqual({ username: "Notch", serverId });
  expect(url.searchParams.get("bypass")).toBe("1");
});

test.each([
  ["no name", `serverId=${serverId}`],
  ["a name too long", `username=${"a".repeat(17)}&serverId=${serverId}`],
  ["a name with a space", `username=No%20tch&serverId=${serverId}`],
  ["no server id", "username=Notch"],
  ["a server id in capitals", `username=Notch&serverId=${serverId.toUpperCase()}`],
  ["a short server id", `username=Notch&serverId=${serverId.slice(1)}`],
])("a query with %s is no question", (_name, query) => {
  expect(readJoinQuery(new URLSearchParams(query))).toBeUndefined();
});
