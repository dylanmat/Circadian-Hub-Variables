# Project Standards

## Scope

These standards apply to the Hubitat app and its documentation. Adoption approvals remain recorded in [CONTEXT.md](CONTEXT.md).

## Baseline rules

### Changes and documentation

- Keep changes within approved scope and make interfaces and failure behavior understandable.
- Update affected documents in the same reviewed change, using the ownership and update triggers in [README.md](README.md).
- Record major design or policy tradeoffs in [DECISIONS.md](DECISIONS.md).
- Keep guidance, unresolved project fields, proposals, and approved policy distinguishable. Do not invent approvals, test results, release dates, or milestone completion.
- Version prompts and relevant configuration so verification evidence can identify what was evaluated.

### Verification and evaluation evidence

- Define intended behavior and acceptance criteria before implementing a behavior change.
- Cover representative successful inputs and relevant failure cases, including invalid outputs and unavailable dependencies.
- For AI behavior changes, include cases for applicable untrusted-content, permission, and sensitive-data boundaries. Compare against the previous behavior when it exists.
- Prefer reproducible checks; when outputs vary, record the evaluation method, repetitions, and acceptance thresholds rather than claiming certainty from one sample.
- Record the changed revision, prompt/model/configuration identifiers where applicable, cases, method or commands, expected outcome, actual results, and limitations.
- Protect evaluation fixtures and reports according to [SECURITY.md](SECURITY.md). Offline checks are preferred when external calls are unnecessary.
- Documentation-only changes require link, consistency, completion-state, and diff review. They do not require artificial application tests.

### Review and release gates

- Use `vMAJOR.MINOR.PATCH` for release labels and Git tags, for example `v0.0.0` or `v0.1.0`. This format does not assign the current project a version.
- A reviewed change includes scope, rationale, affected documentation, verification evidence, and unresolved risks.
- A **blocking** finding is an unmet acceptance criterion, relevant policy violation, defect preventing intended behavior, or missing evidence needed to establish readiness. Resolve it and recheck the affected behavior before approval or release.
- A **nonblocking** finding is an improvement that does not prevent readiness. Record its rationale and, if deferred, its owner and follow-up location.
- Review outcomes are changes requested or ready within the documented scope. A ready result is not authorization to publish or deploy.
- Before release, confirm required checks passed, documentation is current, and no blocking findings remain. Finalize release notes and obtain authorization for the intended release action.

## Project conventions and verification

- Use Hubitat-compatible Groovy, BigDecimal calculations, guarded Hubitat API calls, and validated/clamped writes. Preserve the single-file structure and avoid unrelated formatting. Prefer ASCII for new text.
- Keep preferences/mainPage inputs consistent with README. Behavior or UI changes update APP_VERSION, APP_UPDATED (actual change date), and CHANGELOG.md. Keep APP_BRANCH and importUrl aligned with the default branch. Documentation-only changes do not bump app metadata.
- No automated suite exists. Before behavior changes, define expected values and failure outcomes. On an authorized test hub, check numeric selectors, valid and missing/wrong-type/out-of-range/inverted bounds, updateNow, morning/plateau/evening transitions, window gating, integer output, next-step scheduling, location events, repeated saves, uninstall cleanup, and debug auto-disable.
- Record revision, settings, expected/actual outputs, logs with private details removed, and limitations. A static inspection cannot substitute for live Hubitat verification of runtime changes.
- Documentation-only checks: relative links, source consistency, unresolved fields, historical provenance, and diff review. Runtime checks are not applicable when app behavior is unchanged.
- Rollout: administrator saves prior source/settings, imports the reviewed source through Apps Code, saves the app instance, and monitors outputs/logs. Rollback restores prior source/settings and checks scheduling and downstream rules. Hub deployment requires explicit authorization.
- Review ownership: [REQUIRED: maintainer to assign reviewers and decide when independent review is required]. Operational owner: [REQUIRED: designated hub administrator].
