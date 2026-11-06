# Circadian Hub Variables

Circadian Hub Variables is a Hubitat automation app that writes dynamic dimmer and color-temperature targets into Hub Variables based on circadian lighting principles. The targets can then be consumed by Rule Machine, Room Lighting, or custom automations to keep fixtures aligned with daylight rhythms.

## Basic usage
1. **Create numeric Hub Variables.** In *Settings â†’ Hub Variables* add integer or decimal variables for:
   - `dimmerVarName`: current circadian dimmer percentage.
   - `ctVarName`: current circadian color temperature in Kelvin.
   - `minDim`: minimum allowed dimmer level (1-100%).
   - `maxDim`: maximum allowed dimmer level (1-100%).
   - `minCT`: minimum allowed color temperature (1500-4000 K).
   - `maxCT`: maximum allowed color temperature (4500-8000 K).
2. **Install or update the app.** Import the latest `circadian_hub_variables_hubitat_app.groovy` into Hubitat using the Apps Code editor and create an instance of the app.
3. **Select your Hub Variables.** On the main app page choose the Hub Variable for each selector. The pulldowns list every numeric Hub Variable so `minDim`, `maxDim`, `minCT`, and `maxCT` now offer the same selection experience as the output variables.
4. **Adjust the schedule and curves.** Configure the active window, morning/evening transitions, and optional plateau or exponent tuning to match your household routine. Update cadence is automatic based on the next 1% dimmer or color temperature step.
5. **Save and monitor.** Tap *Done* to save. Within the active window, the app will populate the Hub Variables and keep them refreshed at the automatically calculated cadence. Enable debug logging temporarily if you need to trace the computed levels.

## Targets & limits via Hub Variables
The app now enforces all dimmer and color-temperature limits through Hub Variables. This allows external rules to adjust thresholds without reopening the app: just update the underlying Hub Variable and the next scheduled run will honor the new bounds (provided they stay within the documented ranges). Validation prevents writes when a value is missing, out of range, or when minimums exceed maximums.


## Development and project status

The single-file Hubitat Groovy app is prepared as v2.1.6 (unreleased; deployed release remains v2.1.5) (metadata date 2026-11-05). An offline Groovy regression suite is available; the app runtime remains Hubitat. Follow [AGENTS.md](AGENTS.md) and [STANDARDS.md](STANDARDS.md) for contribution and validation requirements. Release history is in [CHANGELOG.md](CHANGELOG.md).

## Documentation map and ownership

This table is the central inventory of required root documents and their update triggers. Owners are responsibility categories; assign actual people or teams in [CONTEXT.md](CONTEXT.md).

| Document | Accountable owner | Authoritative subject | Update when |
| --- | --- | --- | --- |
| [README.md](README.md) | Project owner | Purpose, audience, setup, document inventory | Scope, setup, or document ownership changes |
| [CONTEXT.md](CONTEXT.md) | Project owner | Stakeholders, assumptions, constraints, current state, outcomes | Users, owners, assumptions, constraints, or outcomes change |
| [ARCHITECTURE.md](ARCHITECTURE.md) | Technical owner | Components, data flow, integrations, operational design | Boundaries, providers, data flow, or failure handling change |
| [SECURITY.md](SECURITY.md) | Security owner | Credentials, data permissions, AI restrictions, incident policy | Access, data handling, tool permissions, or security controls change |
| [STANDARDS.md](STANDARDS.md) | Quality owner | Conventions, evaluation evidence, review and release gates | Tooling, acceptance criteria, review, or operational procedures change |
| [DECISIONS.md](DECISIONS.md) | Technical owner | Decision history, rationale, approval evidence | A major design or policy tradeoff is proposed, accepted, or superseded |
| [ROADMAP.md](ROADMAP.md) | Project owner | Priorities, dependencies, milestone status | Priority, sequencing, ownership, dates, or delivery status change |
| [CHANGELOG.md](CHANGELOG.md) | Release owner | Notable changes and actual releases | A notable change is reviewed or a release is prepared |
| [AGENTS.md](AGENTS.md) | Workflow owner | Agent responsibilities, permissions, handoffs | Roles, authorization boundaries, or handoff rules change |
| [CLAUDE.md](CLAUDE.md) | Workflow owner | Pointer to shared agent instructions | The shared instructions location changes |

## Framework adoption status

This framework is adapted to the existing Hubitat app, not a documentation-only product. Unresolved decisions use `[REQUIRED: description]`; they do not grant permission. Owner assignments and broader policy approvals remain pending in [CONTEXT.md](CONTEXT.md) and [SECURITY.md](SECURITY.md). The original import sources [READMEog.md](READMEog.md) and [AGENTSog.md](AGENTSog.md) are retained for provenance; the canonical instructions are README.md and AGENTS.md.

## Unreleased timing and validation changes

Version 2.1.6 is prepared locally and has not been deployed. Existing installations retain legacy curves because `solarAnchors` is off unless explicitly enabled. The peak deadline is now labeled as solar-mode-only; legacy peaks remain near solar noon.

When enabled, solar mode reaches the daytime peak at the earlier of sunrise and the configured peak deadline, constrained inside the active window. The evening anchor is the earlier of sunset and the evening deadline; CT head start and dimmer lag apply around it. The end of the window still controls final targets. This supports a household routine; it is not strict dawn-to-dusk replication or a validated SAD treatment. Preview the effect before enabling it: an early summer sunrise can reach peak shortly after window start.

Use a same-day active window of at least four minutes. Overnight/equal/reversed windows are rejected. CT head start and dimmer lag accept 0-240 minutes; plateau accepts 0-360 minutes. Anchors are constrained to reserve continuous morning/evening segments; extreme settings may be shortened to fit. Select two different output variables and never reuse either as a limit variable.

Times retain their configured local hour/minute even when Hubitat stores an old date or UTC offset. Next-day scheduling uses the hub timezone's calendar date. Missing spring times move forward through the gap; repeated autumn times use the standard-time occurrence. Invalid settings or unavailable/current-day-invalid solar data hold existing outputs and retry after five minutes. Review hub warnings to correct configuration; no fabricated solar schedule is used.

Offline regression instructions are in [tests/README.md](tests/README.md). Live Hubitat verification is still required before deployment.
