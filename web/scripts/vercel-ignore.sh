#!/bin/sh
# @keep Vercel's Ignored Build Step: exit 0 skips the build, any other code builds, so a git failure builds the commit.

base=$VERCEL_GIT_PREVIOUS_SHA
[ -n "$base" ] || exit 1

git cat-file -e "$base^{commit}" 2>/dev/null || git fetch --quiet --depth=1 origin "$base" || exit 1

if git diff --quiet "$base" HEAD -- .; then
  echo "No change in web/ since $base, so the build is skipped."
  exit 0
fi
exit 1
