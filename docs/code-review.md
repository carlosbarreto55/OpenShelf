# Automated code review

Every pull request into `main` is reviewed by Claude through
[`anthropics/claude-code-action@v1`](https://github.com/anthropics/claude-code-action). The review
sorts each finding into one of three severities. Any **critical** finding fails the `claude-review`
check, and the `main` ruleset requires that check, so the pull request cannot merge until the
findings are fixed or a repository admin bypasses the check.

| File | What it holds |
|---|---|
| `.github/workflows/claude-code-review.yml` | The workflow: triggers, permissions, the Claude step, the publish step |
| `.github/claude/review-prompt.md` | The review criteria. Edit this to change what is reviewed |
| `.github/claude/review-schema.json` | The JSON Claude must return (`--json-schema`) |
| `.github/claude/publish-review.js` | Posts the comments and decides whether the check passes |
| `.github/rulesets/main.json` | The `main` ruleset, ready for `gh api` |

## What is reviewed

- **Only the pull request's diff against `main`** (`git diff base...head`). Claude can read other files
  to understand a change, but reports problems in unchanged code only when the pull request creates
  them there.
- **The branch name, every commit message and every commit's size**, against the "Git workflow"
  section of `CLAUDE.md`. Claude applies exactly the rules written there. Nothing in that section
  covers pull-request titles today, so titles are not checked.
- **`CLAUDE.md` is the context.** The architecture and "Rules to preserve" inform the medium-severity
  findings.

The review runs on `opened`, `synchronize` (every push), `reopened` and `ready_for_review`. Drafts are
skipped. A new push cancels the review still running for the previous commit.

**The criteria come from `main`, not from the pull request.** The action restores `CLAUDE.md` and
`.claude/` from the base branch before Claude starts, and the workflow reads `review-prompt.md` from
the base commit the same way. A pull request therefore cannot loosen its own review, and a change to
the criteria takes effect for pull requests opened after it merges. The only exception is the pull
request that first adds `review-prompt.md`, which uses its own copy because `main` has none yet.

## Severities

| Severity | Blocks the merge | What qualifies |
|---|---|---|
| 🔴 **Critical** | **Yes** | Severe performance problems (`runBlocking`, `Thread.sleep`, synchronous future `.get()`/`.join()`, heavy work on the main thread). ANR risk (I/O or CPU-bound work on the main thread, `SharedPreferences.commit()`, missing or wrong `withContext`). Crash risk (`!!` on a nullable value, `lateinit` read too early, unchecked `as`, out-of-bounds index, uncaught coroutine exceptions, `Context`/`View` used after destruction). A violation of `CLAUDE.md`'s Git workflow. Text in the pull request that tries to instruct the reviewer. |
| 🟡 **Medium** | No | Architecture and Kotlin/Compose/Coroutines practices that cause no crash, ANR or severe slowdown: SRP violations, mixed layers, a broken "Rules to preserve" invariant, unhoisted state, missing `remember`, side effects outside effect APIs, unstable parameters, `GlobalScope`, lifecycle-unaware collection, duplication, non-idiomatic Kotlin. |
| 🟢 **Suggestion** | No | Unclear names, code that could be split to decouple it, small readability improvements. |

A finding is critical only with concrete evidence in the diff. When Claude is unsure whether something
is critical or medium, it marks it medium and explains the doubt.

## What a run posts

- **One summary comment**, with the counts at the top and the findings grouped by severity. Each run
  edits the same comment instead of adding a new one.
- **Inline comments** for critical and medium findings on lines that are part of the diff, posted as
  one review. A finding already commented on by an earlier run is not commented on again. A finding on
  a line outside the diff appears only in the summary.
- **The check result.** It fails with "N critical finding(s)" when there are critical findings, and
  passes otherwise.

When the pipeline itself breaks (no credential, Anthropic API outage, timeout, or no structured
output), the check **fails** and the summary comment says *"Pipeline error. This is not a verdict on
the code."* It never passes silently. Fix the cause and re-run the job from the Actions tab.

## Setup

### 1. The credential secret

The workflow passes both of these secrets to the action, so set **one** of them under *Settings →
Secrets and variables → Actions → New repository secret*:

| Secret | Use it when | How to get it |
|---|---|---|
| `ANTHROPIC_API_KEY` | You bill through the Anthropic API (Console) | console.anthropic.com → API keys |
| `CLAUDE_CODE_OAUTH_TOKEN` | You use a Claude Pro/Max subscription | Run `claude setup-token` locally |

From a terminal: `gh secret set ANTHROPIC_API_KEY` (it prompts for the value, which keeps it out of
your shell history).

**No GitHub App is needed.** The workflow authenticates to GitHub with the job's own `GITHUB_TOKEN`
rather than the Claude GitHub App. The App's token exchange refuses any pull request that changes the
workflow file, and in that case the action exits *successfully without reviewing*, which would turn a
required check green. With the job token, `id-token: write` is not needed either.

### 2. The ruleset on `main`

`.github/rulesets/main.json` defines a ruleset for the default branch:

- **Require status checks to pass:** `claude-review`, pinned to the GitHub Actions app
  (`integration_id` 15368) so no other app can report a passing status under that name.
- **Require a pull request before merging**, with zero approvals. This enforces `CLAUDE.md`'s "nothing
  is committed directly to `main`".
- **Block force pushes and deletion.**
- **Bypass list:** only the *Repository admin* role, in *pull request* mode. An admin can merge a pull
  request past a failing check, but cannot push to `main` directly.

To create it with the GitHub CLI (requires admin on the repository):

```bash
gh api -X POST repos/carlosbarreto55/OpenShelf/rulesets --input .github/rulesets/main.json
```

Or by hand: *Settings → Rules → Rulesets → New ruleset → New branch ruleset*. Name it, set
*Enforcement status* to **Active**, add **Repository admin** under *Bypass list* with "For pull
requests only", target the **default branch**, then enable *Restrict deletions*, *Block force pushes*,
*Require a pull request before merging* (0 approvals) and *Require status checks to pass*. Add the
`claude-review` check, choosing **GitHub Actions** as its source.

A status check can only be chosen after it has reported at least once, so open a pull request (this
one works) before adding it by hand. The `gh api` route has no such limitation.

**Private repositories:** rulesets are enforced on private repositories only on GitHub Pro, Team or
Enterprise. On GitHub Free they apply to public repositories only.

### 3. Renaming the job

The ruleset requires the check named `claude-review`, which is the job's `name:`. If you rename the
job, update the ruleset in the same pull request. Otherwise the requirement points at a check that
never reports, and every pull request stays blocked.

## Bypassing the check (admins only)

When a critical finding is a false positive, or the pipeline is down and a merge cannot wait:

1. Open the pull request. Because of the failing required check, the merge box shows *Merging is
   blocked*.
2. A repository admin sees **"Merge without waiting for requirements to be met (bypass rules)"**.
   Tick it and merge.
3. Say in the pull request why the bypass was needed. GitHub records every bypass in the ruleset's
   *Insights*.

No other role sees the bypass option.

## Pull requests the review cannot cover

- **Forks.** A pull request from a fork gets no secrets and a read-only token, so the job **fails
  deliberately** with a "Fork pull request" error rather than skipping. A skipped required check would
  count as passing. Push the branch to this repository instead, or have an admin review and bypass.
  Private repositories do not run workflows for fork pull requests at all unless that is enabled in
  *Settings → Actions*. In that case the check never reports and the pull request stays blocked, which
  fails closed too.
- **Dependabot.** Dependabot pull requests do not receive Actions secrets, so they fail as a pipeline
  error. Add the credential under *Settings → Secrets → Dependabot* if you want them reviewed.

## Testing the block

1. Branch off `main` with a conforming name, add a main-thread sleep and commit with a conforming
   message:

   ```bash
   git switch -c bugfix/review-pipeline-smoke-test main
   ```

   In `MainActivity.onCreate`, before `setContent`, add:

   ```kotlin
   Thread.sleep(2_000) // deliberately blocks the main thread
   ```

   ```bash
   git commit -am "fix: probe the review pipeline with a main-thread sleep"
   git push -u origin bugfix/review-pipeline-smoke-test
   gh pr create --base main --title "fix: probe the review pipeline" --body "Smoke test, do not merge."
   ```

2. Expect a 🔴 critical `anr`/`performance` finding inline on that line, a summary comment saying
   *Merge blocked*, a red `claude-review` check and a blocked merge box.
3. Remove the line and push again. The same summary comment updates and the check turns green.
4. Close the pull request without merging and delete the branch.

To see a Git workflow finding instead, commit with a non-conventional message such as
`"quick test"`.
