# Project Context

## Purpose and users

Circadian Hub Variables computes dimmer percentages and color temperatures for Hubitat Hub Variables. Household Hubitat administrators consume these targets through Rule Machine, Room Lighting, or custom automations. The app itself does not command fixtures.

## Runtime and current state

The existing single-file Groovy app is v2.1.5, metadata dated 2026-11-05. It uses Hubitat location time/sun information, numeric Hub Variables, adaptive scheduling, and configurable curves. An offline Groovy regression suite was added during subsequent Now-item work. Hubitat execution has not been verified during this documentation retrofit.

## Owners and adoption

- Dylan is the requesting project maintainer; source metadata credits Dylan M as author.
- Accountable assignments: [REQUIRED: maintainer to confirm project, technical, security, quality, release, and workflow owners from README]. Naming the requester does not assign every policy role.
- Adoption scope: the user's 2026-11-05 request authorizes this documentation retrofit. It does not approve deployment or all template security policies.
- Policy approval record: [REQUIRED: accountable owners to record approved document revisions, scope, approver, and date].

## Constraints and invariants

Preserve Hubitat-compatible Groovy and the single-file app structure. Validate numeric variables and limits before writes. Preserve optional debug logging and adaptive scheduling; keep changes scoped. No AI model, retrieval system, or cloud inference service exists in the runtime.

## Vocabulary and success signals

CT means color temperature in Kelvin. Output variables hold computed targets; limit variables hold externally adjustable bounds. The active window controls when writes occur if onlyWithinWindow is enabled. Success means valid bounded targets, predictable scheduling, readable settings, and downstream rules receiving usable values. See STANDARDS.md for checks and SECURITY.md for permissions.

## Known limitations

Downstream lighting rules and physical fixtures are outside this repository. Live hub validation, owner assignments, and security approval remain outstanding; documentation consistency alone does not establish operational readiness.

## v2.1.5 release authorization

On 2026-11-05 Dylan explicitly requested release of the unreleased changes as v2.1.5 and deployment to Hubitat app code #933. This authorizes the scoped release and source update, superseding the prior documentation-only action boundary for this task. Broader policy adoption remains unresolved.

## Current implementation work

The user subsequently authorized implementing all roadmap Now items (R1-R5). Local source metadata is prepared as v2.1.6, unreleased; v2.1.5 remains the deployed release. Runtime changes use an opt-in solar mode to avoid forcing the unresolved broader seasonal/household redesign on existing instances. Local Groovy regression tests exist under tests; Hubitat compilation and lifecycle checks remain pending. No further deployment or publication was requested for this scope.
