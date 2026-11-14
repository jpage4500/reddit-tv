---
name: release-notes
description: Create a new release of RedditTV — write release notes for the GitHub release from the branch's CHANGES.txt entries, commit and open the GitHub PR into main with those notes, and merge it once the user confirms. Use when the user asks to create/cut/prepare a new release, or just wants release notes, "what's new", store notes, or a changelog for the stores.
---

# Releasing a new version

1. **Check the branch** — must be a release branch, not `main`.
2. **Release notes** from the `CHANGES.txt` lines added on the branch.
3. **Commit** anything uncommitted and push the branch.
4. **GitHub PR** into `main`, titled and described with the release notes.
5. **Stop and ask.** Show the release notes and the PR link, and wait for the user to confirm.
   **Never merge without that confirmation, given in this conversation after the notes were shown.**
6. **Merge** the PR, then report the build runs it started.
7. *(user)* Run `scripts/build-ios.sh` locally to upload the iOS build — it's too expensive to run
   on GitHub Actions. Remind the user; don't run it unless asked.

If the user only pastes a list of changes and asks for release notes, do step 2 alone.

## 1. Check the branch

```
git rev-parse --abbrev-ref HEAD
git fetch -q
```

If the branch is `main` (or `HEAD`, a detached checkout), stop and tell the user — a release is
cut from a feature branch, and a PR from `main` has nothing to merge. Don't create a branch for
them.

## 2. Release notes

Get the lines added on this branch:

```
git diff origin/main...HEAD -- CHANGES.txt | grep '^+- '
```

Also look at uncommitted `CHANGES.txt` lines (`git diff -- CHANGES.txt`), since step 3 commits
them. If there are none at all, stop and say so — there's nothing to release.

Write **one** release-notes block, used for the GitHub release (Android APK and desktop).

- **Only one version.** Don't write a separate longer App Store version.
- **Under 500 characters total**, counting bullets, spaces and newlines (Play's limit). Count it
  with `wc -m` before replying. If it's over, merge or drop lines until it fits.
- Plain text in a fenced code block so it can be pasted straight in. No headers like "What's New" or
  "Fixes". Use `-` bullets, one per line.
- After the block, add a short note on what you left out, if anything, so the user can add it back.

### What to keep and how to write it

- **Lead with features, then fixes.** Fixes start with "Fixed …".
- **Keep what users notice**: new tiles or screens, visible behaviour changes, fixes for bugs users
  would have seen.
- **Drop internal changes**: logging, diagnostics, analytics, memory/telemetry notes, rate-limit or
  retry tuning, refactors. A privacy change, such as tokens no longer being logged, can usually be
  dropped too. Mention it in the left-out note.
- **Drop fixes to features new in this release** — nobody on the previous version could have hit
  them.
- **Merge related lines.** Several lines about one feature become one bullet. Several small fixes
  become one catch-all bullet, e.g. "Fixes for text sizing, pollen data, HTML tiles and image
  loading".
- **Keep platform-specific items** (Android Auto, iOS-only, desktop-only), since the same notes ship
  everywhere. Keep them short, and drop them first if space is tight. Name the platform as a whole
  word ("iOS", "Android", "Desktop") — `gen_release_notes.sh` drops the lines that name another
  platform from each platform's notes, so a line without the name ships to every platform. "Android
  TV" counts as Android.
- Plain, user-facing words. No file names, class names or technical detail. Leave out the
  `Area:` prefixes used in `CHANGES.txt` unless the bullet needs the area name to make sense.

### Example

Input (excerpt):
```
- Safety Monitor: the tile shows an active intrusion, smoke, water or rule alert
- Safety Monitor: fixed Armed Night showing as Disarmed
- Pollen Count: works in Europe, and is added by searching for a city or postal code
- Pollen Count: choose the location and pollen source from the tile's edit dialog
- Logs: hub access tokens are no longer written to the log file
- Cloud: fixed a hub outage triggering a burst of cloud requests that got rate limited
```

Output:
```
- Safety Monitor shows active intrusion, smoke, water and rule alerts
- Pollen Count works in Europe; pick location and source in the tile's edit dialog
- Fixed Armed Night showing as Disarmed
```
Left out: the log-token change and the cloud rate-limit fix (internal).

## 3. Commit and push

If `git status --short` shows changes, commit **all** of them, tracked and untracked — the user
has OK'd that for releases. Look at what's there first, and leave out and mention anything that
shouldn't ship (secrets, scratch files, build output).

- Message: the repo's style — the `CHANGES.txt` line(s) for the work being committed, or a short
  plain line if there's no such line — ending with the attribution trailer from the system prompt.
- Then `git push` (`git push -u origin <branch>` if it has no upstream).

## 4. GitHub PR

The CI builds run on a push to `main`, so the PR goes into **`main`** (there is no `develop`
branch). `scripts/gen_release_notes.sh` prints the merged PR's title and body as the store release
notes, so they are the notes themselves — see PR #2:

- **Title**: the first line of the release notes, `-` included.
- **Body**: the remaining lines, exactly as in the release-notes block. **Nothing else** — no
  summary, test plan or "Generated with Claude Code" line: anything extra ships to the stores.

First check `gh pr view --json number,title,body,state` — if the branch already has an open PR,
update it with `gh pr edit` when its title or body differ from the new notes. Otherwise:

```
gh pr create --base main --title "- first line" --body "$(cat <<'EOF'
- second line
- third line
EOF
)"
```

## 5. Confirm before merging

Show the user the release-notes block, the left-out note and the PR link, and ask whether to
merge. Stop there until they answer. If they ask for changes, edit the notes, update the PR with
`gh pr edit`, and show them again.

## 6. Merge

Only after the user confirms:

```
gh pr merge <number> --merge
```

`--merge`, not squash or rebase: `gen_release_notes.sh` finds the PR through its "Merge pull
request #N" commit, and the version counts that commit. Then show the runs it started
(`gh run list --branch main --limit 5`) with their links. Don't wait on them unless asked.
