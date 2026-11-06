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
