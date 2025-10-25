/**
 * Circadian Hub Variables (Hubitat App)
 *
 * Calculates and writes two Hub Variables (directly via Hub Variable API)
 * that smoothly track the sun across the day:
 *   - Dimmer (percent): peaks at 100% mid‑day, down to 20% by night
 *   - Color Temperature (Kelvin): 6500K during morning/day, 2000K by night
 *
 * Author: ChatGPT (GPT‑5 Thinking)
 * Version: 1.2.0 (2025‑08‑18)
 */

definition(
    name:        "Circadian Hub Variables",
    namespace:   "chatgpt.tools",
    author:      "ChatGPT (GPT‑5 Thinking)",
    description: "Calculates hub variables (dimmer %, color temp K) with smooth circadian mapping.",
    category:    "Convenience",
    importUrl:   "",
    iconUrl:     "https://raw.githubusercontent.com/hubitat/community-app-icons/main/circadian-48.png",
    iconX2Url:   "https://raw.githubusercontent.com/hubitat/community-app-icons/main/circadian-96.png"
)

preferences {
    page(name: "mainPage")
}

Map mainPage() {
    dynamicPage(name: "mainPage", title: "Circadian Hub Variables", uninstall: true, install: true) {
        section("Outputs: Hub Variable selectors (numeric types)") {
            def allGV = [:]
            try { allGV = getAllGlobalVars() ?: [:] } catch (ignored) {}
            def numNames = allGV.findAll { k,v -> v?.type in ["integer","bigdecimal"] }?.collect { it.key }?.sort() ?: []
            if (!numNames) {
                paragraph "No numeric Hub Variables found. Create them first in Settings → Hub Variables (type integer or bigdecimal)."
            }
            input name: "dimmerVarName", type: "enum", options: numNames, title: "Dimmer % Hub Variable", required: true, submitOnChange: true
            input name: "ctVarName",     type: "enum", options: numNames, title: "Color Temp (K) Hub Variable", required: true, submitOnChange: true
        }
        section("Time Window & Curves") {
            input name: "startTimeStr", type: "time", title: "Active window start", required: true, defaultValue: "05:30"
            input name: "endTimeStr",   type: "time", title: "Active window end", required: true, defaultValue: "22:30"
            input name: "morningRampMins", type: "number", title: "Minutes after sunrise to finish reaching daytime values", range: "0..360", defaultValue: 90, required: true
            input name: "eveningRampMins", type: "number", title: "Minutes before sunset to begin transitioning to evening values", range: "0..360", defaultValue: 120, required: true
        }
        section("Targets & Limits") {
            input name: "minDim", type: "number", title: "Evening dimmer minimum (%)", range: "1..100", defaultValue: 20, required: true
            input name: "maxDim", type: "number", title: "Daytime dimmer maximum (%)", range: "1..100", defaultValue: 100, required: true
            input name: "minCT",  type: "number", title: "Evening color temp minimum (K)", range: "1500..4000", defaultValue: 2000, required: true
            input name: "maxCT",  type: "number", title: "Daytime color temp maximum (K)", range: "4500..8000", defaultValue: 6500, required: true
        }
        section("Update & Behavior") {
            input name: "updateEveryMin", type: "bool",   title: "Update every minute (recommended)", defaultValue: true
            input name: "updateSecs",     type: "number", title: "If not every minute, update every N seconds (min 10)", range: "10..3600", defaultValue: 60, required: false
            input name: "onlyWithinWindow", type: "bool", title: "Only write variables within active window (leave values untouched outside)", defaultValue: true
            input name: "snapToInt",      type: "bool",   title: "Write integers (no decimals)", defaultValue: true
            input name: "logDebug",       type: "bool",   title: "Enable debug logging (auto‑disables in 30m)", defaultValue: false
        }
    }
}

/********************** Lifecycle ************************/ 

def installed() {
    log.info "Installed with settings: ${settings}"
    initialize()
}

def updated() {
    log.info "Updated with settings: ${settings}"
    unschedule()
    unsubscribe()
    initialize()
}

def uninstalled() {
    try { if(dimmerVarName) removeInUseGlobalVar(dimmerVarName) } catch (ignored) {}
    try { if(ctVarName) removeInUseGlobalVar(ctVarName) } catch (ignored) {}
}

def initialize() {
    if (logDebug) runIn(1800, "logsOff")
    try { if(dimmerVarName) addInUseGlobalVar(dimmerVarName) } catch (ignored) {}
    try { if(ctVarName) addInUseGlobalVar(ctVarName) } catch (ignored) {}
    scheduleUpdaters()
    runIn(2, "updateNow")
}

def logsOff() { log.warn "Debug logging disabled"; app.updateSetting("logDebug", [value:"false", type:"bool"]) }

// rest of the code unchanged...
