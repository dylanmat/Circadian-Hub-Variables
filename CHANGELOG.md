# Changelog

## [Unreleased]

### Added

- Source-review findings, DST reproduction evidence, impact assessment, and a prioritized seasonal-lighting simplification proposal in ROADMAP.md. App behavior is unchanged.

## [v2.1.5] - 2026-11-05

### Changed

- Adapted the project framework to Circadian Hub Variables; integrated original usage and agent guidance into canonical documents.
- Documented actual runtime, verification, security boundaries, adoption gaps, and project priorities.
- Moved the original release history here from READMEog.md; dates and versions below are preserved historical records, not newly verified releases.

## 2026-01-08 (v2.1.4)
- Smoothed adaptive scheduling by reacting to the next color temperature step in addition to dimmer changes.

## 2025-12-29 (v2.1.3)
- Fixed the adaptive scheduler wait calculation to avoid Groovy Math.max overload errors.

## 2025-12-29 (v2.1.2)
- Corrected changelog dates to reflect the original request timeline.

## 2025-12-29 (v2.1.1)
- Fixed adaptive scheduling to trigger throughout the active window by targeting the next 1% dimmer change using raw dimmer values.

## 2025-12-28 (v2.1.0)
- Switched update scheduling to an adaptive cadence based on the next 1% dimmer change, removing the manual interval setting.
- Ensured the app pauses updates outside the active window until the next window starts.

## 2025-11-01 (v2.0.6)
- Fixed the Hub Variable pulldowns for `minDim`, `maxDim`, `minCT`, and `maxCT` so they populate consistently alongside the output selectors.
- Documented the consistent selector behaviour in the basic usage guide and refreshed metadata for v2.0.6.

## 2025-11-01 (v2.0.5)
- Switched minimum/maximum dimmer and color-temperature settings to Hub Variable selectors with range validation.
- Added safeguards that block updates when Hub Variables are missing, out of range, or inverted.
- Expanded documentation with basic setup instructions and refreshed changelog.

## 2025-10-29 (v2.0.4)
- Refined the schedule inputs and smoothing curves for more natural transitions throughout the day.
- Updated metadata and defaults to reflect the revised circadian window configuration.
