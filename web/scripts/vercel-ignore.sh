#!/bin/sh
# Vercel's Ignored Build Step, run in web/ on a clone ten commits deep: exit 0 skips the build, any other code builds.
# Any git failure exits non-zero, so a commit this cannot judge is built.

# The branch's last built commit, or, on its first build, where it left main.
base=$VERCEL_GIT_PREVIOUS_SHA
if [ -z "$base" ]; then
  git fetch --quiet --depth=100 origin main || exit 1
  base=$(git merge-base FETCH_HEAD HEAD) || exit 1
  # main's own first build, which has nothing to compare with.
  [ "$base" = "$(git rev-parse HEAD)" ] && exit 1
fi

# A diff needs only the two trees, so a base older than the clone is fetched alone.
git cat-file -e "$base^{commit}" 2>/dev/null || git fetch --quiet --depth=1 origin "$base" || exit 1

if git diff --quiet "$base" HEAD -- .; then
  echo "No change in web/ since $base, so the build is skipped."
  exit 0
fi
exit 1
