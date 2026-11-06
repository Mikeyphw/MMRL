#!/usr/bin/env zsh
setopt ERR_EXIT PIPE_FAIL

if [[ "$#" -ne 1 || "$1" != --plan && "$1" != --apply ]]; then
  print -u2 'Usage: zsh scripts/sync-upstream-next.zsh --plan|--apply'
  exit 2
fi
mode="$1"
root="$(git -C "${0:A:h}" rev-parse --show-toplevel)"
cd "$root"
if [[ -n "$(git status --porcelain=v1 --untracked-files=all)" ]]; then
  print -u2 'UPSTREAM_REFUSED: dirty checkout'
  exit 2
fi
if [[ "$(git branch --show-current)" != 'next' ]]; then
  print -u2 'UPSTREAM_REFUSED: checkout must be next'
  exit 2
fi

if [[ -z "$(git remote)" ]]; then
  print -u2 'UPSTREAM_REFUSED: missing Git remotes'
  exit 2
fi
if ! git remote get-url upstream >/dev/null 2>&1; then
  git remote add upstream https://github.com/MMRLApp/MMRL.git
fi
upstream_url="$(git remote get-url upstream)"
case "$upstream_url" in
  https://github.com/MMRLApp/MMRL.git|https://github.com/MMRLApp/MMRL|git@github.com:MMRLApp/MMRL.git|git@github.com:MMRLApp/MMRL) ;;
  *) print -u2 "UPSTREAM_REFUSED: unexpected upstream remote: $upstream_url"; exit 2 ;;
esac

git fetch --no-tags upstream refs/heads/master:refs/remotes/upstream/master
if git merge-base --is-ancestor upstream/master next; then
  print 'UPSTREAM_ALREADY_SYNCED: next already contains upstream/master'
  exit 0
fi
print '\n=== New upstream commits ==='
git log --oneline --no-merges next..upstream/master | head -40 || true
print '\n=== Changed paths on upstream ==='
git diff --stat "$(git merge-base next upstream/master)"..upstream/master
if [[ "$mode" == --plan ]]; then
  print '\nUPSTREAM_PLAN_ONLY: No branch, merge or PR created.'
  exit 0
fi

sha="$(git rev-parse --short=12 upstream/master)"
branch="sync/upstream-${sha}"
if git show-ref --verify --quiet "refs/heads/$branch"; then
  print -u2 "UPSTREAM_REFUSED: $branch exists"
  exit 2
fi
git switch -c "$branch" next
if ! git merge --no-ff upstream/master -m "chore(next): merge upstream MMRL $sha"; then
  print -u2 'UPSTREAM_CONFLICT: branch remains for explicit conflict resolution. No reset or automatic push.'
  exit 1
fi
# No publication unless merge completed successfully.
git push -u origin "$branch"
if command -v gh >/dev/null 2>&1; then
  gh pr create -R Mikeyphw/MMRL -B next -H "$branch" \
    -t "chore(next): merge upstream MMRL $sha" \
    -b 'Review upstream changes and validate Android builds before merging into next.'
else
  print "Open a PR from $branch to next after reviewing the merge."
fi
