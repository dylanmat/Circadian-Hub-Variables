# Architecture

## v2.1.7 schedule design (unreleased)

Dimmer uses four ordered local times and a bounded plateau after the daytime peak. Color temperature uses sunrise/sunset in Solar Mode and the household endpoints otherwise. Both color curves peak at the midpoint of sunrise and sunset. A shared update callback chooses the earliest next step across both curves and refreshes solar data at local midnight. When solar data fails or solar noon lies outside the clock window, the callback holds color temperature and retries within five minutes while maintaining dimmer updates.

## Current design

The runtime is [circadian_hub_variables_hubitat_app.groovy](circadian_hub_variables_hubitat_app.groovy), installed as a user Hubitat app. Its preferences/mainPage select six numeric Hub Variables and configure time anchors, curves, integer output, window gating, and logging.

## Data flow and boundaries

1. installed/updated initialize variable usage tracking, location subscriptions, and an initial update. updated clears previous schedules and subscriptions.
2. updateNow validates selected variables and bounds. Invalid configuration skips writes and schedules a retry after 300 seconds.
3. Location sun/time data and configured anchors drive BigDecimal curve calculations for dimmer and CT targets.
4. writeHubVar writes the two selected outputs, converting for variable type and catching failures. The writes are separate operations, not an atomic pair; a failed write can leave outputs inconsistent temporarily.
5. The next update is scheduled from the next dimmer or CT step. With onlyWithinWindow enabled, writes pause outside the window until its next start.

Hubitat APIs supply variable access, location events, and scheduling. Downstream Rule Machine, Room Lighting, or custom rules consume outputs and own fixture control. The source import URL references the main branch on GitHub; importing code is an administrator action.

## Failure handling and operations

Variable access failures are caught where implemented; invalid selections/types/ranges produce warnings. Write failures are logged. Optional debug logging auto-disables after 30 minutes; install/update logs include settings, so logs may expose variable names and household schedules. Administrators install and recover the app through Hubitat; rollback and manual verification are described in STANDARDS.md.

## Exclusions and verification

No model/provider integration, prompts, retrieval, memory index, separate database, or credential store is implemented. These AI template components are not applicable. App settings and Hub Variables persist on the hub. Validate selectors, bounds, transitions, retry behavior, partial writes, and lifecycle scheduling using STANDARDS.md. Live verification remains pending. Permissions and unresolved retention decisions are in SECURITY.md.

## v2.1.6 timing changes

updateNow catches calculation/time/solar failures and schedules a five-minute retry while holding outputs. readValidatedSnapshot reads each distinct selected variable once, rejects output aliasing and output/limit overlap, and checks type/value/ranges before calculation. This is a consistent per-run snapshot of sequential reads, not a hub transaction; external changes can still occur between reads.

localTimeOnDay extracts the configured wall-clock hour/minute and builds that date with Calendar in the hub timezone. nextWindowStart advances the calendar date rather than adding elapsed 24-hour periods. Calendar's gap-forward/fold-standard-time policy is explicit. Existing location subscriptions trigger recalculation; platform event delivery and clock-change behavior require hub verification.

buildAnchors constrains ordered segments within a same-day window of at least four minutes. Legacy mode retains ordinary existing curves. Opt-in solarAnchors uses sunrise/peak-deadline and sunset/evening-deadline minima, with a constrained plateau and existing head-start/lag offsets. Invalid solar data (including absent, reversed, or wrong-day events) causes hold-and-retry. No astronomical fallback is guessed. A failed output write can still produce partial outputs; that remains R7 backlog.

## Deployment verification - v2.1.6

The subsequent user request authorized committing and deploying this implementation. Commit 6fcb1ae contains the implementation and regression tests. Hubitat app code #933 compiled successfully at code revision 16; readback matched local source. Offline Groovy regressions passed again before deployment. Solar mode remains opt-in; no instance settings were changed. Full live lifecycle, event delivery, and all-day curve behavior remain unverified. Earlier statements about pending deployment describe the pre-deployment state. No GitHub push, tag, or release was performed for this task.
