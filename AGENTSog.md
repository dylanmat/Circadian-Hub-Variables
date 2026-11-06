# AGENTS.md

## Project snapshot
- Repo purpose: Hubitat automation app that writes circadian dimmer and CT targets to Hub Variables.
- Primary code: circadian_hub_variables_hubitat_app.groovy.
- Docs: README.md with usage + changelog.

## Guardrails
- Keep code Hubitat Groovy compatible; avoid APIs not available on Hubitat.
- Prefer ASCII in all edits (this repo already has some mojibake; do not add more non-ASCII).
- Preserve the single-file app structure unless a change clearly needs new files.
- Do not reformat unrelated sections; keep diffs minimal.

## Versioning and docs
- When behavior or UI changes, bump `APP_VERSION` and `APP_UPDATED` and update README changelog.
- Keep `APP_BRANCH` and `importUrl` consistent with the default branch.
- If inputs/labels change, update the README ?Basic usage? steps to match.

## Hubitat app conventions
- Inputs belong in `preferences { page(name: "mainPage") }` and `mainPage()`.
- Wrap Hubitat calls that can throw (e.g., global vars) in `try/catch` to avoid crashes.
- Use `BigDecimal` for numeric math; clamp values and validate ranges before writing.
- Keep scheduling/unscheduling in `initialize()`/`updated()` predictable and idempotent.
- Debug logging should remain optional and auto-disable.

## Testing / validation
- There are no automated tests. Validate changes by:
  - Reviewing UI labels and ranges for inputs.
  - Ensuring `updateNow()` and scheduling logic still run without missing variables.

## Suggested workflow
- Read `circadian_hub_variables_hubitat_app.groovy` first, then `README.md`.
- Use `rg` for search when needed.
- If touching UI text, scan for mojibake and keep labels readable ASCII.