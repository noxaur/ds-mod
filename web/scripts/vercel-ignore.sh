#!/bin/sh
# Vercel's Ignored Build Step, run in web/ on a clone ten commits deep: exit 0 skips the build, any other code builds.
# Any git failure exits non-zero, so a commit this cannot judge is built.

base=$VERCEL_GIT_PREVIOUS_SHA
[ -n "$base" ] || exit 1

# A diff needs only the two trees, so a base older than the clone is fetched alone.
git cat-file -e "$base^{commit}" 2>/dev/null || git fetch --quiet --depth=1 origin "$base" || exit 1

if git diff --quiet "$base" HEAD -- .; then
  echo "No change in web/ since $base, so the build is skipped."
  exit 0
fi
exit 1
