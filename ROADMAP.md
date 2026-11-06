# Roadmap

## Documentation retrofit

- Priority: Now
- Status: Complete for the documentation retrofit; policy adoption and live validation remain pending.
- Owner: Agent for this authorized scope; maintainer receives the result.
- Target: Unscheduled
- Dependencies: Original project docs, imported framework, and source inspection.
- Acceptance: Canonical usage and agent guidance preserved; actual architecture and standards documented; historical releases retained; template project claims removed; links and diff reviewed. All relative Markdown links resolved; app source unchanged.
- Evidence: ADR-001 in DECISIONS.md and the verification/handoff report in this conversation.

## Complete policy adoption and runtime evidence

- Priority: Next
- Status: Planned
- Owner: [REQUIRED: maintainer to assign accountable policy and hub owners].
- Target: Unscheduled
- Dependencies: Owner assignments and explicit authorization for any live hub work.
- Acceptance: Resolve applicable CONTEXT/SECURITY/STANDARDS fields; record approvals; perform and record representative Hubitat checks before claiming runtime readiness.
- Evidence: Pending. No new product features or release dates are committed.

## App review and seasonal-lighting direction - 2026-11-05

Review baseline: v2.1.5, commit 1539f35. Scope: full static source review and research; no runtime changes, live hub access, or deployment. Findings below distinguish confirmed code behavior from the unverified cause of the reported household symptom. All new work is Planned, target Unscheduled, implementation owner pending maintainer assignment. The user authorized recording recommendations, not implementing them.

### Product direction and evidence

Goal: support a consistent morning/day/evening light routine throughout the year, using natural sunrise/sunset cues while accommodating wake and sleep times. The recommended design is a solar-informed household schedule with explicit wake/bedtime limits. This is a proposed engineering interpretation, not a validated therapeutic protocol. Strict winter sunset tracking and maintaining a longer useful winter day are different requirements; the maintainer must choose the balance before implementation.

- [Huberman Lab: Toolkit for Sleep](https://www.hubermanlab.com/newsletter/toolkit-for-sleep) emphasizes outdoor morning light, late-afternoon outdoor light, consistent waking, and reduced bright evening/night lighting. Warm CT alone does not address brightness.
- [Huberman Lab: Daily Blueprint](https://www.hubermanlab.com/daily-blueprint) supports morning/daytime light and dim, warm evenings. Neither source establishes a specific Kelvin curve, dimmer percentage, exponent, or household-bulb treatment dose. Those settings must be described as engineering choices.
- [NIMH: Seasonal Affective Disorder](https://www.nimh.nih.gov/health/publications/seasonal-affective-disorder) describes clinical light-box treatment (10,000 lux, generally 30-45 minutes in the morning) and individualized treatment decisions. This app cannot infer eye-level exposure, spectrum, duration, occupancy, or clinical benefit from percent/Kelvin outputs. It should support lighting routines without claiming to treat or prevent SAD or replace outdoor light or clinical care.
- Hubitat firmware documentation consulted: timeTodayAfter(String, String, TimeZone), published id shared-date-time--timeTodayAfter-String-String-TimeZone, firmware 2.5.2.139. It returns a matching local time today/tomorrow; use verified timezone-aware calendar operations for scheduling rather than assuming every day is 24 hours.

### Prioritized work and impact assessment

| ID | Priority | Finding and source | Proposed improvement | Benefit, cost, and compatibility impact |
| --- | --- | --- | --- | --- |
| R1 | Now / P1 | scheduleNextWindowStart (416-425) adds 86400000 ms to today's start. This is elapsed time, not next local calendar day. | Compute the next strictly future local start using hub timezone/calendar semantics; define behavior for missing/repeated local times and recompute after time/location changes. | Fixes a confirmed DST defect with low conceptual scope. Avoid parsing stored time inputs as fixed-offset dates; validate firmware semantics. |
| R2 | Now / P1 | getTodaySunTimes (247-267), updateNow (151-170), computeDimmer/computeCT (568-618): eveningStart is a fixed clock setting. Sunrise/sunset only influence the midpoint; morningEnd/workdayPeak is never used by curves. | Make sunrise/sunset real anchors, or rename current settings truthfully during migration. Replace the ineffective morning target with an anchor actually used. | Addresses seasonal mismatch and misleading controls. Material behavior change: compare old/new schedules before opting existing instances in. |
| R3 | Now / P1 | isBetween (273-276) assumes same-day ordered endpoints. Plateau anchors are not constrained to the window; offsets/plateau have no practical upper bounds. | Validate start/end and anchor ordering, clamp or reject impossible configurations, explicitly support overnight windows or clearly reject them. Handle equal/collapsed segments continuously. | Prevents all-day skipped writes and discontinuities in narrow/extreme windows. More validation may reject formerly accepted settings; explain corrections in UI. |
| R4 | Now / P1 | updateNow (128-194) has no recovery boundary around sun/time conversion and scheduling. Null/exceptional sun data can abort before scheduling the next run. | Validate solar data, implement a documented fallback schedule, and ensure bounded retry/recovery scheduling after failures. | Improves resilience, including high-latitude/no-event cases and transient API faults. Fallback must be visible and must not silently produce unexpected bright night targets. |
| R5 | Now / P1 | validateVars/writeHubVar (197-242) allow both outputs to use the same variable, or outputs to reuse limit variables. | Require distinct output variables and prohibit output/input-limit overlap; read and validate one consistent input snapshot for each calculation. | Prevents CT overwriting dimmer or outputs altering their own bounds. Existing invalid configurations will require correction. Sharing read-only limits can remain allowed where valid. |
| R6 | Next / P2 | scheduleNextUpdate and step searches (428-518) may sleep to a distant boundary when limits change, since input variables are not subscribed. Repeated identical writes also create unnecessary downstream work. | Compare a simple periodic refresh plus exact boundary scheduling against repaired adaptive scheduling; subscribe to supported variable-change events or use bounded polling; skip unchanged successful writes. | Simpler maintenance and prompt external-limit response, balanced against hub load and missed variable recreation/reset if caching is naive. Measure before choosing cadence. |
| R7 | Next / P2 | writeHubVar catches failure but returns no status; updateNow always logs success and proceeds. The two outputs cannot be assumed atomic. | Return per-output success, log actual outcomes, retry failures with bounds, and document transient partial updates. | Makes failures actionable without claiming transactional writes. Retrying must not flood downstream rules or overwrite a newer calculation. |
| R8 | Next / P2 | computeDimmer begins at 60% of the min/max span (68% with 20/100 bounds); morning dim exponent label says higher=faster, but t^exponent makes a larger exponent slower for 0<t<1. | Adopt a shared smooth interpolation with meaningful morning/day/evening endpoints; remove misleading exponent controls from basic settings. | Gentler, understandable transitions; changes wake brightness and requires explicit legacy migration. CT and dimmer currently use different exponent semantics. |
| R9 | Next / P2 | onlyWithinWindow freezes last written values; a delayed final callback can miss minimum targets. Clamping before rounding can round beyond fractional bounds. | Define explicit end-of-window behavior (hold or write night targets), schedule endpoint application, then enforce representable bounds after output-type quantization. | Predictable nights and strict bounds. Preserve downstream autonomy: writing 0% is not equivalent to an authorized fixture-off command; decide whether 0% is supported. |
| R10 | Next / P2 | updated clears subscriptions but only registers new variable references; prior selections are not explicitly deregistered. Logs include the full settings map; fmtTime lacks explicit hub timezone. | Track/release old variable references using verified Hubitat semantics, limit routine logs, and format diagnostics in hub timezone. | Cleaner lifecycle, less household detail in logs, clearer DST diagnosis. Confirm whether Hubitat already cleans references automatically before adding bookkeeping. |
| R11 | Later / P3 | sineWave and ease are mathematically equivalent cosine easing; separate raw/CT step searches duplicate logic. sun parameters are unused in compute methods and morningEnd is diagnostic only. | Consolidate pure schedule/curve functions and remove unused parameters after timing correctness is established. | Reduces maintenance and makes deterministic testing easier. Avoid a broad cleanup mixed into the DST patch; preserve single-file Hubitat deployment. |

### R1: DST evidence and acceptance

Confirmed offline reproduction using Windows Pacific Standard Time rules and UTC elapsed arithmetic: 2026-10-31 05:30 plus 24 hours becomes 2026-11-01 04:30 local; 2026-03-07 05:30 becomes 2026-03-08 06:30. On fall-back, an early callback before the active window may then reschedule to 05:30; on spring-forward the first update can be an hour late unless another event wakes it. Thus the arithmetic is defective, but it does not alone prove the cause of the reported evening behavior. Earlier winter sunset being ignored by evening anchors is a separate confirmed mismatch. Obtain a sanitized dated symptom example and hub timezone/settings before claiming a full diagnosis.

Acceptance: deterministic tests for spring/fall transitions, non-DST zones, year rollover, exact start, late callbacks, and ambiguous/nonexistent configured times. Always schedule a strictly future intended local occurrence; no tight loop, skipped morning, or dependence on incidental sunrise events. Do not compensate by hardcoding +/- one hour.

### R2/R8: Proposed simpler lighting model

Start with four understandable phases: wake ramp, daytime, evening wind-down, night. Retain the two output variables and externally adjustable bounds. Use sunrise/sunset plus optional offsets as environmental cues; use wake time and bedtime as household constraints. Offer a small basic settings surface and one smooth curve rather than several independent exponents, head starts, lags, and plateau controls. Keep advanced tuning only where users can demonstrate a need.

Before implementation, the maintainer decides: strict solar tracking versus a bounded winter day extension; how quickly morning brightness rises; whether evening decline follows sunset or bedtime on very short/long days; night hold versus explicit night targets; and overnight/shift-worker support. A reasonable candidate is morning light tied to wake time when sunrise is late, a useful daytime plateau, and evening dimming bounded by bedtime even when summer sunset is late. This is a proposal to evaluate, not a sourced clinical prescription.

Acceptance: preview old/new targets for winter and summer solstices, equinoxes, both DST changes, late sunrise, early/late sunset, and narrow windows. Demonstrate ordered anchors, bounded/continuous outputs, a meaningful morning target, and no bright restart at midnight. Document migration and preserve existing configuration until explicitly converted. No specific CT or brightness values should be presented as Huberman-prescribed or as a SAD treatment dose.

### R3-R11: Verification and delivery sequence

1. Add focused deterministic date/curve regression coverage for R1-R5 before behavior changes. Exercise missing sun events, variable aliasing, malformed settings, extreme offsets, zero/equal spans, rounding, and delayed endpoint callbacks. A small offline harness may stub Hubitat APIs; confirm compilation and scheduling on an authorized test instance afterward.
2. Ship the DST correction and validation/recovery work as separately reviewable scoped changes before redesigning the curves. Fix misleading controls with explicit migration notes.
3. Compare fixed refresh and adaptive scheduling using callbacks/day, writes/day, maximum output error and input-change latency. A candidate periodic cadence is an engineering tradeoff to measure, not a protocol recommendation. Guarantee exact phase endpoints and bounded retry rates whichever approach wins.
4. Implement the approved simple model with preview evidence and update README, ARCHITECTURE, CONTEXT, STANDARDS, and CHANGELOG in the same reviewed change; record the chosen tradeoffs in DECISIONS. Bump app metadata for actual behavior/UI releases.
5. Validate install/save/repeated-save/uninstall, variable changes, partial write recovery, overnight behavior if supported, and debug auto-disable on a test hub before any production deployment. Do not manipulate household timing or limits solely to manufacture a test without authorization.

### Review evidence and handoff

- Planner -> Implementer/Docs: user request authorizes source review, impact assessment, research, and roadmap edits only. Proposed runtime changes remain pending approval.
- Implementer/Docs -> Reviewer: added this assessment to ROADMAP and an unreleased changelog note. Reviewed the entire 628-line app, source call paths, published Hubitat time API, and primary light-guidance sources. Offline DST arithmetic reproduced with Windows timezone rules; Python zoneinfo was unavailable, so the successful check used .NET instead.
- Reviewer -> Release readiness: documentation link/diff checks recorded in the task conversation. App source unchanged. Runtime regression tests and clinical efficacy tests were not performed; neither is implied by this documentation review. Existing app defects are implementation backlog, not resolved findings.
- Next owner/action: maintainer selects the seasonal-versus-household schedule tradeoff and approves a first implementation scope. No publication or deployment is part of this task.

## Now implementation status - 2026-11-05

User approval: commit the review, then work on all Now items. Review committed as 88b81c8; the following implementation is local and unreleased, source metadata v2.1.6. No push or deployment is included in this instruction.

| Item | Status | Evidence and remaining gate |
| --- | --- | --- |
| R1 | Implemented locally | Calendar-based next local occurrence, stored offset ignored, explicit gap/fold policy; real-method Groovy regressions pass spring/fall, year rollover, exact start and late calls. Hub time-change event behavior still needs live checks. |
| R2 | Implemented locally | Accurate legacy labels plus opt-in sunrise/peak-deadline and sunset/evening-deadline anchors. Synthetic winter/summer legacy-versus-solar previews and continuity checks pass. Existing instances remain legacy until enabled. |
| R3 | Implemented locally | Reject same-day spans under four minutes and overnight windows; validate tuning limits; constrain anchors with nonzero transition segments. Narrow/extreme-window tests pass. |
| R4 | Implemented locally | Missing/invalid solar data and calculation failures hold targets and establish a five-minute retry. Throwing/null solar stub tests pass. Fallback policy is explicit hold, not fabricated daylight. |
| R5 | Implemented locally | Reject output/output and output/limit aliasing; read each distinct input once and validate that snapshot. Alias, range, type, missing value, and read-count tests pass. Hub reads are sequential, not atomic. |

Verification: `tests/circadian_regression.groovy` passed against Apache Groovy 2.4.21 with stubbed Hubitat APIs. Source parses and executes locally. Synthetic previews: winter warming moves from 17:30 legacy to 16:00 solar for 17:00 sunset; summer evening stays limited by the 18:30 deadline. Tests are not evidence of Hubitat compiler/runtime approval. Live test-instance compilation, UI, scheduling, and lifecycle checks remain deployment blockers; R6-R11 and broader protocol redesign remain planned.

Handoffs: Planner -> Implementer under the user's Now approval; Implementer -> Docs updated source, test coverage and affected docs; Docs -> Reviewer checked diff, links and local regression evidence; Reviewer -> Release readiness is pending live hub validation. Maintainer owns the next deployment/test-instance authorization and remaining product decisions. No claim of clinical efficacy is made.
