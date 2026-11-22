const encoder = new TextEncoder();

/** @keep Takes the same time wherever the two differ, so a secret cannot be guessed a letter at a time. An unset
 * secret matches nothing, so a missing setting refuses every caller. */
export const sameSecret = (given: string | null | undefined, expected: string | undefined): boolean => {
  if (expected === undefined || expected === "" || given === null || given === undefined) {
    return false;
  }
  const sent = encoder.encode(given);
  const held = encoder.encode(expected);
  let difference = sent.length ^ held.length;
  for (const [index, byte] of sent.entries()) {
    difference |= byte ^ (held[index % held.length] ?? 0);
  }
  return difference === 0;
};
