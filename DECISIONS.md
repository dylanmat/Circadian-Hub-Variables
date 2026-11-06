# Decision Records

## ADR-001: Adapt the framework to the existing Hubitat project

- Date: 2026-11-05
- Status: Accepted for documentation scope only.
- Decision owner and approval: Dylan, requesting maintainer, explicitly requested retrofitting READMEog.md and AGENTSog.md into the proper framework documents and updating related documentation in this chat.
- Context: Imported framework documents described a separate documentation-only project and carried its historical approval and verification claims.
- Decision: Retain the framework document map, authorization boundaries, sequential roles, and review gates; integrate actual app usage and implementation guidance; move original release history into CHANGELOG.md. Retain og files as provenance.
- Alternatives: Append legacy text without adapting other documents (leaves conflicting project descriptions); replace the framework with legacy files (loses requested workflow).
- Consequences: Canonical documentation reflects the app; owner/policy gaps remain explicit. No code, runtime behavior, or release changes.
- Evidence: READMEog.md, AGENTSog.md, source metadata and implementation, and the user's request. Earlier template ADR and verification records are not project approval evidence and have been replaced.
- Supersedes: Imported framework-specific ADR-001 in this working copy; no assertion that its historical approvals apply to this project.

## ADR-002: Scoped Now fixes with opt-in seasonal anchors

- Date: 2026-11-05
- Status: Accepted; implementation subsequently committed and deployed to app code #933 under explicit user authorization. GitHub publication is pending.
- Approval: Dylan requested committing the review then working on everything designated Now in ROADMAP.md in this conversation.
- Decision: Implement R1-R5 while retaining legacy curves by default. Offer solar anchors explicitly; reject overnight windows rather than introduce shift-worker semantics. On unavailable sun data, hold outputs and retry every five minutes instead of guessing a clock-based daylight schedule. Snapshot each distinct input once per run; prohibit output aliasing.
- Alternatives: Force migration to solar curves (unexpected household changes); implement the full four-phase redesign (Next scope and unresolved product choices); fabricate sun times on failure (could brighten at the wrong time).
- Consequences: Existing valid ordinary configurations preserve curves until opt-in. Invalid configurations are rejected; extreme anchors are constrained. Sunrise may cause an early daytime peak; configured evening deadlines can precede summer sunset. Hold-and-retry preserves last outputs even if stale; warning logs expose degraded operation. Broader scheduler, output transaction, and night behavior improvements remain backlog.
- Evidence: Actual-source Groovy regression suite and synthetic seasonal previews in tests. Live hub compilation/scheduling remains pending.

## Deployment verification - v2.1.6

The subsequent user request authorized committing and deploying this implementation. Commit 6fcb1ae contains the implementation and regression tests. Hubitat app code #933 compiled successfully at code revision 16; readback matched local source. Offline Groovy regressions passed again before deployment. Solar mode remains opt-in; no instance settings were changed. Full live lifecycle, event delivery, and all-day curve behavior remain unverified. Earlier statements about pending deployment describe the pre-deployment state. No GitHub push, tag, or release was performed for this task.
