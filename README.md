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
3. **Select your Hub Variables.** Use two distinct numeric outputs and separate variables for the four limits.
4. **Set Solar Mode.** On uses sunrise and sunset for color temperature. Off uses the active window start and end. Color temperature reaches its maximum at solar noon in both modes. In Off mode the active window must contain solar noon.
5. **Set the household schedule.** Enter active window start, daytime peak, evening transition deadline, and active window end in that order on the same day. Dimmer rises from minimum to maximum, holds for the configured plateau, fades to the evening percentage at the deadline, then fades to minimum at the end. Solar Mode does not change this schedule.
6. **Set Targets & Limits and save.** Enter the evening transition dimmer percentage directly. It defaults to 85% and is constrained to the configured dimmer bounds. The two morning exponents affect their own curves. Higher values move more of the rise earlier, and 1 uses the standard smooth curve. Tap *Done*.

## Targets & limits via Hub Variables
The four minimum and maximum limits come from Hub Variables. This allows external rules to adjust thresholds without reopening the app: just update the underlying Hub Variable and the next scheduled run will honor the new bounds (provided they stay within the documented ranges). Validation prevents writes when a value is missing, out of range, or when minimums exceed maximums.


## Development and project status

The single-file Hubitat Groovy app has v2.1.7 changes in development. Hubitat app code #933 is on v2.1.6. An offline Groovy regression suite is available; the app runtime remains Hubitat. Follow [AGENTS.md](AGENTS.md) and [STANDARDS.md](STANDARDS.md) for contribution and validation requirements. Release history is in [CHANGELOG.md](CHANGELOG.md).

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

## Upgrading from v2.1.6

Existing clock times now control the household dimmer schedule. Check their order and verify the new evening percentage before saving. The app shortens the plateau when needed to leave time for the fade. Saved lag, head-start and window-gating settings are obsolete. Existing morning exponent values are retained, with both using the same direction for adjustment. Color temperature holds its minimum outside its curve. If the hub cannot supply valid solar times, the app holds color temperature and retries while the dimmer schedule continues.

## Deployment verification - v2.1.6

The subsequent user request authorized committing and deploying this implementation. Commit 6fcb1ae contains the implementation and regression tests. Hubitat app code #933 compiled successfully at code revision 16; readback matched local source. Offline Groovy regressions passed again before deployment. Solar mode remains opt-in; no instance settings were changed. Full live lifecycle, event delivery, and all-day curve behavior remain unverified. Earlier statements about pending deployment describe the pre-deployment state. No GitHub push, tag, or release was performed for this task.
