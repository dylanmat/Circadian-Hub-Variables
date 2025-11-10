# Circadian-Hub-Variables

Circadian Hub Variables is a Hubitat automation app that writes dynamic dimmer and color-temperature targets into Hub Variables based on circadian lighting principles. The targets can then be consumed by Rule Machine, Room Lighting, or custom automations to keep fixtures aligned with daylight rhythms.

## Basic usage
1. **Create numeric Hub Variables.** In *Settings → Hub Variables* add integer or decimal variables for:
   - `dimmerVarName`: current circadian dimmer percentage.
   - `ctVarName`: current circadian color temperature in Kelvin.
   - `minDim`: minimum allowed dimmer level (1-100%).
   - `maxDim`: maximum allowed dimmer level (1-100%).
   - `minCT`: minimum allowed color temperature (1500-4000 K).
   - `maxCT`: maximum allowed color temperature (4500-8000 K).
2. **Install or update the app.** Import the latest `circadian_hub_variables_hubitat_app.groovy` into Hubitat (via Hubitat Package Manager or the Apps Code editor) and create an instance of the app.
3. **Select your Hub Variables.** On the main app page choose the Hub Variable for each selector. The pulldowns list every numeric Hub Variable so `minDim`, `maxDim`, `minCT`, and `maxCT` now offer the same selection experience as the output variables.
4. **Adjust the schedule and curves.** Configure the active window, morning/evening transitions, and optional plateau or exponent tuning to match your household routine.
5. **Save and monitor.** Tap *Done* to save. The app will immediately populate the Hub Variables and keep them refreshed at the chosen cadence. Enable debug logging temporarily if you need to trace the computed levels.

## Targets & limits via Hub Variables
The app now enforces all dimmer and color-temperature limits through Hub Variables. This allows external rules to adjust thresholds without reopening the app: just update the underlying Hub Variable and the next scheduled run will honor the new bounds (provided they stay within the documented ranges). Validation prevents writes when a value is missing, out of range, or when minimums exceed maximums.

## Changelog
### 2025-11-01 (v2.0.6)
- Fixed the Hub Variable pulldowns for `minDim`, `maxDim`, `minCT`, and `maxCT` so they populate consistently alongside the output selectors.
- Documented the consistent selector behaviour in the basic usage guide and refreshed metadata for v2.0.6.

### 2025-11-01 (v2.0.5)
- Switched minimum/maximum dimmer and color-temperature settings to Hub Variable selectors with range validation.
- Added safeguards that block updates when Hub Variables are missing, out of range, or inverted.
- Expanded documentation with basic setup instructions and refreshed changelog.

### 2025-10-29 (v2.0.4)
- Refined the schedule inputs and smoothing curves for more natural transitions throughout the day.
- Updated metadata and defaults to reflect the revised circadian window configuration.