import groovy.transform.Field

@Field final String APP_NAME    = "Circadian Hub Variables"
@Field final String APP_VERSION = "1.3.3"
@Field final String APP_BRANCH  = "main"          // or "feature-dimmer-rise-tuning"
@Field final String APP_UPDATED = "2025-10-25"    // ISO date is clean

definition(
    name: APP_NAME,
    namespace: "dylanm.chv.${APP_BRANCH}",
    author: "Dylan M",
    description: "Calculates hub variables (dimmer %, color temp K) with smooth circadian mapping.",
    category: "Convenience",
    version: "${APP_VERSION}",
    importUrl: "https://raw.githubusercontent.com/dylanmat/Circadian-Hub-Variables/refs/heads/${APP_BRANCH}/circadian_hub_variables_hubitat_app.groovy",
    documentationLink: "https://github.com/dylanmat/Circadian-Hub-Variables",
    iconUrl: "",
    iconX2Url: "",
    iconX3Url: "",
    singleInstance: false
)

preferences {
    page(name: "mainPage")
}

Map mainPage() {
    dynamicPage(name: "mainPage", title: APP_NAME, uninstall: true, install: true) {
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
    log.info "${APP_NAME} v${APP_VERSION} (${APP_BRANCH}) installed ${APP_UPDATED} with settings: ${settings}"
    initialize()
}

def updated() {
    log.info "${APP_NAME} v${APP_VERSION} (${APP_BRANCH}) updated ${APP_UPDATED} with settings: ${settings}"
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

/********************** Scheduling ************************/ 

private void scheduleUpdaters() {
    if (updateEveryMin) {
        runEvery1Minute("updateNow")
    } else {
        Integer s = Math.max(10, (updateSecs ?: 60) as Integer)
        schedule("*/${s} * * * * ?", updateNow)
    }
    // Recompute on mode/sunrise/sunset changes
    subscribe(location, "position", locationHandler) // covers TZ/lat/long changes
    subscribe(location, "sunriseTime",  locationHandler)
    subscribe(location, "sunsetTime",   locationHandler)
}

def locationHandler(evt) {
    if (logDebug) log.debug "Location event ${evt?.name} → ${evt?.value}"
    runIn(5, "updateNow")
}

/********************** Core Logic ************************/ 

def updateNow() {
    if (!validateVars()) return

    Map sun = getTodaySunTimes()
    Date now = new Date()

    Date winStart = timeToday(startTimeStr)
    Date winEnd   = timeToday(endTimeStr)

    boolean inWindow = isBetween(now, winStart, winEnd)
    if (onlyWithinWindow && !inWindow) {
        if (logDebug) log.debug "Outside active window; skipping write."
        return
    }

    // Build eased targets
    BigDecimal dimmer = computeDimmer(now, sun, winStart, winEnd)
    BigDecimal ctemp  = computeCT(now, sun, winStart, winEnd)

    if (snapToInt) {
        dimmer = (dimmer as BigDecimal).setScale(0, BigDecimal.ROUND_HALF_UP)
        ctemp  = (ctemp  as BigDecimal).setScale(0, BigDecimal.ROUND_HALF_UP)
    } else {
        dimmer = (dimmer as BigDecimal).setScale(1, BigDecimal.ROUND_HALF_UP)
        ctemp  = (ctemp  as BigDecimal).setScale(0, BigDecimal.ROUND_HALF_UP) // CT should be int for devices
    }

    writeHubVar(dimmerVarName, dimmer)
    writeHubVar(ctVarName,     ctemp)

    if (logDebug) log.debug "Wrote variables → ${dimmerVarName}: ${dimmer} | ${ctVarName}: ${ctemp}K  | sun=${sunriseSunsetSummary(sun)}"
}

private boolean validateVars() {
    String[] names = [dimmerVarName, ctVarName]
    for (String n : names) {
        if (!n) { log.warn "Hub Variable name is missing."; return false }
        def gv = null
        try { gv = getGlobalVar(n) } catch (ignored) {}
        if (!gv) { log.warn "Hub Variable '${n}' not found. Create it first in Settings → Hub Variables."; return false }
        if (!(gv.type in ["integer", "bigdecimal"])) {
            log.warn "Hub Variable '${n}' should be type integer or bigdecimal; found '${gv.type}'."
        }
    }
    return true
}

private void writeHubVar(String name, BigDecimal val) {
    try {
        def gv = getGlobalVar(name)
        def out = val
        if (gv?.type == "integer") {
            out = (val as BigDecimal).setScale(0, BigDecimal.ROUND_HALF_UP).intValue()
        } else if (gv?.type == "bigdecimal") {
            out = (val as BigDecimal)
        }
        Boolean ok = setGlobalVar(name, out)
        if (!ok) log.warn "setGlobalVar(${name}, ${out}) returned false"
    } catch (e) {
        log.warn "Failed to write Hub Variable '${name}': ${e}"
    }
}

/********************** Computations ************************/ 

private Map getTodaySunTimes() {
    Map s = getSunriseAndSunset()
    Date sunrise = s.sunrise instanceof Date ? s.sunrise : new Date(s.sunrise.time)
    Date sunset  = s.sunset  instanceof Date ? s.sunset  : new Date(s.sunset.time)

    Integer mRamp = (morningRampMins ?: 90) as Integer
    Integer eRamp = (eveningRampMins ?: 120) as Integer

    Date morningEnd   = new Date(sunrise.time + (mRamp * 60 * 1000L))
    Date eveningStart = new Date(sunset.time  - (eRamp * 60 * 1000L))

    Long noonMillis = (sunrise.time + sunset.time) / 2L
    Date solarNoon = new Date(noonMillis)

    [sunrise: sunrise, morningEnd: morningEnd, eveningStart: eveningStart, sunset: sunset, noon: solarNoon]
}

private String sunriseSunsetSummary(Map s) {
    ["rise ${fmtTime(s.sunrise)}", "noon ${fmtTime(s.noon)}", "eveStart ${fmtTime(s.eveningStart)}", "set ${fmtTime(s.sunset)}"].join(", ")
}

private String fmtTime(Date d) { d?.format("HH:mm") }

private boolean isBetween(Date t, Date a, Date b) {
    long x=t.time, u=a.time, v=b.time
    return (x>=u && x<=v)
}

// Cosine ease (smooth, zero slope at ends): 0..1 → 0..1
private BigDecimal ease(BigDecimal x) {
    if (x <= 0) return 0.0G
    if (x >= 1) return 1.0G
    BigDecimal r = 0.5G - 0.5G * Math.cos(Math.PI * x as BigDecimal)
    return r
}

// Map 0..1 to [a,b]
private BigDecimal lerp(BigDecimal a, BigDecimal b, BigDecimal t) { a + (b - a) * t }

private BigDecimal clamp(BigDecimal v, BigDecimal a, BigDecimal b) { [a, v, b].sort()[1] as BigDecimal }

/** Dimmer logic */
private BigDecimal computeDimmer(Date now, Map sun, Date winStart, Date winEnd) {
    BigDecimal minP = (minDim ?: 20) as BigDecimal
    BigDecimal maxP = (maxDim ?: 100) as BigDecimal

    BigDecimal midStart = lerp(minP, maxP, 0.60G)

    if (now.before(sun.noon)) {
        BigDecimal t = frac(now, winStart, sun.noon)
        return lerp(midStart, maxP, ease(t))
    } else if (now.before(sun.eveningStart)) {
        BigDecimal t = frac(now, sun.noon, sun.eveningStart)
        BigDecimal dayHold = lerp(maxP, lerp(minP, maxP, 0.85G), ease(t))
        return dayHold
    } else if (now.before(winEnd)) {
        BigDecimal t = frac(now, sun.eveningStart, winEnd)
        return lerp(lerp(minP, maxP, 0.85G), minP, ease(t))
    } else {
        return minP
    }
}

/** Color Temp logic (Kelvin) */
private BigDecimal computeCT(Date now, Map sun, Date winStart, Date winEnd) {
    BigDecimal lo = (minCT ?: 2000) as BigDecimal
    BigDecimal hi = (maxCT ?: 6500) as BigDecimal

    BigDecimal startCT = lerp(lo, hi, 0.75G)

    if (now.before(sun.morningEnd)) {
        BigDecimal t = frac(now, winStart, sun.morningEnd)
        return lerp(startCT, hi, ease(t))
    } else if (now.before(sun.eveningStart)) {
        BigDecimal t = frac(now, sun.morningEnd, sun.eveningStart)
        BigDecimal dayHi = hi
        BigDecimal dayLo = clamp(hi - 300G, lo, hi)
        return lerp(dayHi, dayLo, ease(t))
    } else if (now.before(winEnd)) {
        BigDecimal t = frac(now, sun.eveningStart, winEnd)
        return lerp(hi - 300G, lo, ease(t))
    } else {
        return lo
    }
}

// Returns 0..1 fraction between two Dates (clamped)
private BigDecimal frac(Date now, Date a, Date b) {
    BigDecimal A = a.time, B = b.time, N = now.time
    if (B <= A) return 1.0G
    BigDecimal t = (N - A) / (B - A)
    return clamp(t, 0G, 1G)
}
