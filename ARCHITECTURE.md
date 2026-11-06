# Architecture

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
