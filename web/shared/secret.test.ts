import { expect, test } from "vite-plus/test";

import { sameSecret } from "./secret";

test.each([
  ["the same secret", "the-key", "the-key", true],
  ["a different secret", "the-kez", "the-key", false],
  ["a prefix", "the-ke", "the-key", false],
  ["a longer secret", "the-keyy", "the-key", false],
  ["a repeat that wraps around", "the-keythe-key", "the-key", false],
  ["nothing", null, "the-key", false],
  ["an empty secret", "", "the-key", false],
  ["an empty secret to an empty setting", "", "", false],
  ["anything to no setting", "the-key", undefined, false],
])("%s matches: %j", (_name, given, expected, matches) => {
  expect(sameSecret(given, expected)).toBe(matches);
});
