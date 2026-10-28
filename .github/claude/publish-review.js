// Publishes Claude's structured review on the pull request and decides the claude-review check.
// Run by actions/github-script from .github/workflows/claude-code-review.yml, which passes the
// earlier steps' outcomes and the action's structured_output through the environment.
//
// The check fails in exactly two cases, and the message always says which:
//   - the pipeline itself failed (no credential, API outage, timeout, no structured output), which
//     is not a verdict on the code and must never pass silently;
//   - the review contains at least one CRITICAL finding.

const crypto = require('crypto');

const SUMMARY_MARKER = '<!-- claude-code-review:summary -->';
const FINDING_MARKER_PREFIX = '<!-- claude-code-review:finding=';
const COMMENT_LIMIT = 60000; // GitHub rejects comment bodies over 65,536 characters.

const SEVERITIES = [
  { key: 'CRITICAL', label: '🔴 Critical', inline: true },
  { key: 'MEDIUM', label: '🟡 Medium', inline: true },
  { key: 'SUGGESTION', label: '🟢 Suggestion', inline: false },
];

module.exports = async ({ github, context, core }) => {
  const env = process.env;
  const { owner, repo } = context.repo;
  const pullNumber = context.payload.pull_request.number;
  const headSha = env.HEAD_SHA;
  const runUrl = `${env.GITHUB_SERVER_URL}/${owner}/${repo}/actions/runs/${env.GITHUB_RUN_ID}`;
  const target = { github, owner, repo, pullNumber };

  let review;
  try {
    review = parseReview(env);
  } catch (error) {
    await upsertSummary(target, renderPipelineError(error.message, headSha, runUrl));
    core.setFailed(`Pipeline error, not a verdict on the code: ${error.message}`);
    return;
  }

  const groups = groupBySeverity(review.findings);
  const counts = Object.fromEntries(SEVERITIES.map(({ key }) => [key, groups[key].length]));
  if (
    review.counts.critical !== counts.CRITICAL ||
    review.counts.medium !== counts.MEDIUM ||
    review.counts.suggestion !== counts.SUGGESTION
  ) {
    core.warning('The counts Claude reported disagree with its findings; using the findings.');
  }

  const inline = await postInlineComments(target, review.findings, headSha, core);
  await upsertSummary(target, renderSummary(review.summary, groups, counts, inline, headSha, runUrl));

  await core.summary
    .addHeading('Claude code review')
    .addRaw(`${countLine(counts)}\n\n`)
    .addLink('Summary comment on the pull request', `${env.GITHUB_SERVER_URL}/${owner}/${repo}/pull/${pullNumber}`)
    .write();

  if (counts.CRITICAL > 0) {
    core.setFailed(
      `${counts.CRITICAL} critical finding(s): merging into main is blocked until they are fixed ` +
        '(or a repository admin bypasses the check). See the summary comment on the pull request.',
    );
  } else {
    core.info(`No critical findings. ${countLine(counts)}`);
  }
};

/** Throws with a human-readable reason whenever the run did not produce a usable review. */
function parseReview(env) {
  if (env.PREFLIGHT_OUTCOME !== 'success') {
    throw new Error(
      'no Anthropic credential is configured. Set the ANTHROPIC_API_KEY or ' +
        'CLAUDE_CODE_OAUTH_TOKEN repository secret (see docs/code-review.md).',
    );
  }
  if (env.CONTEXT_OUTCOME !== 'success') {
    throw new Error('the review prompt could not be built; see the "Build the review prompt" step log.');
  }
  if (env.REVIEW_OUTCOME !== 'success' || env.REVIEW_CONCLUSION !== 'success') {
    throw new Error(
      'the Claude Code action did not finish (API outage, rate limit, timeout or an invalid ' +
        'credential); see the "Review the diff with Claude" step log, then re-run the job.',
    );
  }
  if (!env.STRUCTURED_OUTPUT) {
    throw new Error('Claude finished without returning the structured review.');
  }

  let review;
  try {
    review = JSON.parse(env.STRUCTURED_OUTPUT);
  } catch {
    throw new Error('the structured review is not valid JSON.');
  }
  const known = new Set(SEVERITIES.map(({ key }) => key));
  if (
    !review ||
    !Array.isArray(review.findings) ||
    !review.counts ||
    review.findings.some((finding) => !known.has(finding.severity))
  ) {
    throw new Error('the structured review does not match .github/claude/review-schema.json.');
  }
  return review;
}

function groupBySeverity(findings) {
  const groups = Object.fromEntries(SEVERITIES.map(({ key }) => [key, []]));
  for (const finding of findings) groups[finding.severity].push(finding);
  return groups;
}

/**
 * Posts CRITICAL and MEDIUM findings as one review with inline comments. Only lines that appear in
 * the diff can carry a comment, so the rest stay in the summary alone, as does every finding already
 * commented on by an earlier run. Returns the findings that got a comment, keyed by fingerprint.
 */
async function postInlineComments({ github, owner, repo, pullNumber }, findings, headSha, core) {
  const posted = new Set();
  const { data: pull } = await github.rest.pulls.get({ owner, repo, pull_number: pullNumber });
  if (pull.head.sha !== headSha) {
    core.info('A newer commit was pushed during the review; skipping inline comments for this one.');
    return posted;
  }

  const files = await github.paginate(github.rest.pulls.listFiles, {
    owner,
    repo,
    pull_number: pullNumber,
    per_page: 100,
  });
  const commentable = new Map(files.map((file) => [file.filename, commentableLines(file.patch)]));

  const existing = await github.paginate(github.rest.pulls.listReviewComments, {
    owner,
    repo,
    pull_number: pullNumber,
    per_page: 100,
  });
  const alreadyCommented = new Set(existing.map((comment) => markerIn(comment.body)).filter(Boolean));

  const comments = [];
  for (const finding of findings) {
    const severity = SEVERITIES.find(({ key }) => key === finding.severity);
    const fingerprint = fingerprintOf(finding);
    if (!severity.inline || !finding.file || !(finding.line > 0)) continue;
    if (!commentable.get(finding.file)?.has(finding.line)) continue;
    if (alreadyCommented.has(fingerprint)) {
      posted.add(fingerprint);
      continue;
    }
    comments.push({
      path: finding.file,
      line: finding.line,
      side: 'RIGHT',
      body: `${FINDING_MARKER_PREFIX}${fingerprint} -->\n${renderFinding(finding, severity.label)}`,
    });
    posted.add(fingerprint);
  }

  if (comments.length === 0) return posted;
  try {
    await github.rest.pulls.createReview({
      owner,
      repo,
      pull_number: pullNumber,
      commit_id: headSha,
      event: 'COMMENT',
      body: `Claude code review: ${comments.length} inline comment(s). The summary comment lists every finding.`,
      comments,
    });
  } catch (error) {
    // One invalid position rejects the whole review. The summary still carries every finding.
    core.warning(`Inline comments could not be posted (${error.message}); the summary has them all.`);
    for (const comment of comments) posted.delete(markerIn(comment.body));
  }
  return posted;
}

/** The new-file line numbers a review comment may point at: added and context lines of the patch. */
function commentableLines(patch) {
  const lines = new Set();
  if (!patch) return lines; // Binary or too-large files carry no patch.
  let right = 0;
  for (const row of patch.split('\n')) {
    const hunk = /^@@ -\d+(?:,\d+)? \+(\d+)(?:,\d+)? @@/.exec(row);
    if (hunk) {
      right = Number(hunk[1]);
    } else if (row.startsWith('+') || row.startsWith(' ')) {
      lines.add(right);
      right += 1;
    }
  }
  return lines;
}

/** Stable across runs and line shifts, so a re-run does not repeat a comment already made. */
function fingerprintOf(finding) {
  const key = [finding.severity, finding.category, finding.file, finding.title].join('\u0000');
  return crypto.createHash('sha256').update(key).digest('hex').slice(0, 16);
}

function markerIn(body) {
  const start = (body || '').indexOf(FINDING_MARKER_PREFIX);
  if (start < 0) return null;
  return body.slice(start + FINDING_MARKER_PREFIX.length).split(' ')[0];
}

async function upsertSummary({ github, owner, repo, pullNumber }, body) {
  const comments = await github.paginate(github.rest.issues.listComments, {
    owner,
    repo,
    issue_number: pullNumber,
    per_page: 100,
  });
  const previous = comments.find(
    (comment) => comment.user?.type === 'Bot' && comment.body?.includes(SUMMARY_MARKER),
  );
  if (previous) {
    await github.rest.issues.updateComment({ owner, repo, comment_id: previous.id, body });
  } else {
    await github.rest.issues.createComment({ owner, repo, issue_number: pullNumber, body });
  }
}

function renderSummary(summary, groups, counts, inline, headSha, runUrl) {
  const verdict =
    counts.CRITICAL > 0
      ? '> ❌ **Merge blocked.** Fix the critical findings and push; the review runs again on every push.'
      : '> ✅ **No critical findings.** The review does not block this merge.';

  const sections = SEVERITIES.filter(({ key }) => groups[key].length > 0).map(({ key, label }) => {
    const items = groups[key].map((finding) => {
      const note = inline.has(fingerprintOf(finding)) ? '' : inlineNote(finding, key);
      return `${renderFinding(finding, label)}${note}`;
    });
    return `### ${label} (${groups[key].length})\n\n${items.join('\n\n---\n\n')}`;
  });

  const body = [
    SUMMARY_MARKER,
    '## Claude code review',
    `**${countLine(counts)}**`,
    verdict,
    neutralizeMentions(summary || ''),
    ...sections,
    `<sub>Reviewed \`${headSha.slice(0, 7)}\` · [workflow run](${runUrl}) · ` +
      'criteria in `.github/claude/review-prompt.md`</sub>',
  ].join('\n\n');
  return truncate(body, runUrl);
}

function renderPipelineError(reason, headSha, runUrl) {
  return [
    SUMMARY_MARKER,
    '## Claude code review',
    '> ⚠️ **Pipeline error. This is not a verdict on the code.**',
    `The review did not run to completion: ${reason}`,
    'The `claude-review` check fails until a review succeeds, so this pull request cannot merge ' +
      'unreviewed. Fix the cause and re-run the job, or have a repository admin bypass the check.',
    `<sub>Commit \`${(headSha || '').slice(0, 7)}\` · [workflow run](${runUrl})</sub>`,
  ].join('\n\n');
}

function renderFinding(finding, label) {
  const location = finding.file
    ? `\`${finding.file}${finding.line > 0 ? `:${finding.line}` : ''}\``
    : 'branch, title or commits';
  return neutralizeMentions(
    [
      `**${label} · \`${finding.category}\` · ${finding.title}**`,
      `📍 ${location}`,
      finding.description,
      `**Why it matters:** ${finding.why_it_matters}`,
      `**Suggestion:** ${finding.suggestion}`,
    ].join('\n\n'),
  );
}

function inlineNote(finding, severityKey) {
  if (severityKey === 'SUGGESTION' || !finding.file || !(finding.line > 0)) return '';
  return '\n\n<sub>No inline comment: this line is not part of the diff.</sub>';
}

function countLine(counts) {
  return SEVERITIES.map(({ key, label }) => `${label}: ${counts[key]}`).join(' · ');
}

/** Claude's text must not ping people: "@name" becomes "@​name". */
function neutralizeMentions(text) {
  return String(text).replace(/@(?=[A-Za-z0-9-])/g, '@​');
}

function truncate(body, runUrl) {
  if (body.length <= COMMENT_LIMIT) return body;
  return (
    `${body.slice(0, COMMENT_LIMIT)}\n\n…\n\n**Truncated:** the remaining findings did not fit in one ` +
    `comment. The counts above are complete. [Workflow run](${runUrl})`
  );
}
