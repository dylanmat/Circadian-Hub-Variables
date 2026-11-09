import groovy.transform.Field

@Field final String APP_NAME    = "Circadian Hub Variables"
@Field final String APP_VERSION = "2.1.7"
@Field final String APP_BRANCH  = "main"
@Field final String APP_UPDATED = "2026-11-08"    // ISO date is clean

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
        Map allGV = [:]
        try {
            allGV = getAllGlobalVars() ?: [:]
        } catch (ignored) {}
        List<String> numericVarNames = allGV.findAll { k, v -> v?.type in ["integer", "bigdecimal"] }
            ?.collect { it.key }
            ?.sort() ?: []

        section("Outputs: Hub Variable selectors (numeric types)") {
            if (!numericVarNames) {
                paragraph "No numeric Hub Variables found. Create them first in Settings → Hub Variables (type integer or bigdecimal)."
            }
            input name: "dimmerVarName", type: "enum", options: numericVarNames, title: "Dimmer % Hub Variable (integer/bigdecimal)", required: true, submitOnChange: true
            input name: "ctVarName",     type: "enum", options: numericVarNames, title: "Color Temp (K) Hub Variable (integer/bigdecimal)", required: true, submitOnChange: true
        }
        section("Solar Mode") {
            input name: "solarAnchors", type: "bool", title: "Solar Mode", defaultValue: false
            paragraph "On: color temperature follows sunrise and sunset. Off: it follows the active window. Both peak at solar noon. Dimmer always follows the household schedule."
        }
        section("Household Schedule") {
            input name: "startTimeStr", type: "time", title: "Active window start", required: true, defaultValue: "05:30"
            input name: "workdayPeakTimeStr", type: "time", title: "Daytime peak time", required: true, defaultValue: "08:00"
            input name: "eveningTransitionTimeStr", type: "time", title: "Evening transition deadline", required: true, defaultValue: "18:30"
            input name: "endTimeStr", type: "time", title: "Active window end", required: true, defaultValue: "22:30"
        }
        section("Targets & Limits") {
            input name: "maxDim", type: "enum", options: numericVarNames, title: "Daytime dimmer max (%)", required: true
            input name: "maxCT", type: "enum", options: numericVarNames, title: "Daytime color temp max (K)", required: true
            input name: "eveningDimPct", type: "decimal", range: "1..100", title: "Evening transition dimmer (%)", defaultValue: 85, required: true
            input name: "minDim", type: "enum", options: numericVarNames, title: "Evening dimmer min (%)", required: true
            input name: "minCT", type: "enum", options: numericVarNames, title: "Evening color temp min (K)", required: true
        }
        section("Wellness Tuning") {
            input name: "middayPlateauMins", type: "number", range: "0..360", title: "Dimmer plateau after daytime peak (minutes)", defaultValue: 60, required: true
            input name: "dimMorningExponent", type: "decimal", range: "0.2..5.0", title: "Dimmer morning curve exponent", defaultValue: 0.7, required: true
            input name: "ctMorningExponent", type: "decimal", range: "0.2..5.0", title: "Color temperature morning curve exponent", defaultValue: 1.6, required: true
            paragraph "For both curves, higher values bring more of the rise earlier. 1 uses the standard smooth curve."
        }
        section("Update & Behavior") {
            paragraph "Updates are scheduled dynamically based on the next dimmer or color temperature step to balance hub load with smooth transitions."
            input name: "snapToInt",      type: "bool",   title: "Write integers (no decimals) — best for Hub Variables", defaultValue: true
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
    try { if(minDim) removeInUseGlobalVar(minDim) } catch (ignored) {}
    try { if(maxDim) removeInUseGlobalVar(maxDim) } catch (ignored) {}
    try { if(minCT)  removeInUseGlobalVar(minCT)  } catch (ignored) {}
    try { if(maxCT)  removeInUseGlobalVar(maxCT)  } catch (ignored) {}
}

def initialize() {
    if (logDebug) runIn(1800, "logsOff")
    try { if(dimmerVarName) addInUseGlobalVar(dimmerVarName) } catch (ignored) {}
    try { if(ctVarName) addInUseGlobalVar(ctVarName) } catch (ignored) {}
    try { if(minDim) addInUseGlobalVar(minDim) } catch (ignored) {}
    try { if(maxDim) addInUseGlobalVar(maxDim) } catch (ignored) {}
    try { if(minCT)  addInUseGlobalVar(minCT)  } catch (ignored) {}
    try { if(maxCT)  addInUseGlobalVar(maxCT)  } catch (ignored) {}
    scheduleUpdaters()
    runIn(2, "updateNow")
}

def logsOff() { log.warn "Debug logging disabled"; app.updateSetting("logDebug", [value:"false", type:"bool"]) }

/********************** Scheduling ************************/ 

private void scheduleUpdaters() {
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
    try {
        updateTargets()
    } catch (Exception e) {
        log.warn "Circadian update paused; holding outputs and retrying in 5 minutes (${e.class.simpleName}). Check time settings and hub sun data."
        scheduleRetry(300)
    }
}

private void updateTargets() {
    Map snapshot = readValidatedSnapshot()
    if (snapshot == null) {
        scheduleRetry(300)
        return
    }

    Date now = new Date()
    validateTuning()
    Map dim = buildDimmerSchedule(now, snapshot)
    Map ct
    try {
        ct = buildColorSchedule(now, dim)
    } catch (Exception e) {
        log.warn "Color temperature schedule unavailable: ${e.message}. Retrying in 5 minutes."
    }
    BigDecimal minDimBD = snapshot.minDim
    BigDecimal maxDimBD = snapshot.maxDim
    BigDecimal minCTBD = snapshot.minCT
    BigDecimal maxCTBD = snapshot.maxCT
    BigDecimal dimMorningExp = settingDecimal("dimMorningExponent", 0.7G)
    BigDecimal ctMorningExp = settingDecimal("ctMorningExponent", 1.6G)
    BigDecimal dimmer = computeDimmer(now, null, dim.start, dim.end, dim, minDimBD, maxDimBD, dimMorningExp)
    BigDecimal ctemp = ct == null ? null : computeCT(now, null, ct.start, ct.end, ct, minCTBD, maxCTBD, ctMorningExp)

    dimmer = clamp(dimmer, minDimBD, maxDimBD)
    if (ctemp != null) ctemp = clamp(ctemp, minCTBD, maxCTBD)

    if (snapToInt) {
        dimmer = (dimmer as BigDecimal).setScale(0, BigDecimal.ROUND_HALF_UP)
        ctemp  = ctemp?.setScale(0, BigDecimal.ROUND_HALF_UP)
    } else {
        dimmer = (dimmer as BigDecimal).setScale(1, BigDecimal.ROUND_HALF_UP)
        ctemp  = ctemp?.setScale(0, BigDecimal.ROUND_HALF_UP) // CT should be int for devices
    }

    writeHubVar(dimmerVarName, dimmer)
    if (ctemp != null) writeHubVar(ctVarName, ctemp)
    if (logDebug) log.debug "Dimmer target: ${dimmer}%; color temperature target: ${ctemp} K"

    Long dimDelay = secondsUntilNextDimmerStep(now, dim.start, dim.end, dim, minDimBD, maxDimBD, dimMorningExp)
    Long ctDelay = ct == null ? 300L : secondsUntilNextCtStep(now, ct.start, ct.end, ct, minCTBD, maxCTBD, ctMorningExp)
    Date midnight = nextLocalTime("00:00", now)
    long dayDelay = (midnight.time - now.time + 999L) / 1000L as Long
    scheduleRetry(Math.max(1L, Math.min(dayDelay, minNonNull(dimDelay, ctDelay) ?: dayDelay)) as Integer)
}

private Map readValidatedSnapshot() {
    List<String> outputs = [settings.dimmerVarName, settings.ctVarName]
    List<String> limits = ["minDim", "maxDim", "minCT", "maxCT"]
    if (!outputs.every { it } || outputs[0] == outputs[1] ||
        limits.any { outputs.contains(settings[it]) }) {
        log.warn "Select distinct output variables that are not also used for limits."
        return null
    }
    Map cache = [:]
    Map result = [:]
    Map ranges = [minDim: [1G, 100G], maxDim: [1G, 100G], minCT: [1500G, 4000G], maxCT: [4500G, 8000G]]
    for (String key : ["dimmerVarName", "ctVarName"] + limits) {
        String name = settings[key]
        if (!name) { log.warn "Select a numeric Hub Variable for ${key}."; return null }
        if (!cache.containsKey(name)) {
            def gv = safeGetGlobalVar(name)
            if (!gv || !(gv.type in ["integer", "bigdecimal"])) {
                log.warn "Select an existing integer/bigdecimal Hub Variable for ${key}."
                return null
            }
            try { cache[name] = gv.value as BigDecimal } catch (ignored) { return null }
            if (cache[name] == null) return null
        }
        BigDecimal value = cache[name]
        if (ranges[key] && (value < ranges[key][0] || value > ranges[key][1])) {
            log.warn "Hub Variable for ${key} is outside its supported range."
            return null
        }
        result[key] = value
    }
    if (result.minDim > result.maxDim || result.minCT > result.maxCT) {
        log.warn "Minimum limits must not exceed maximum limits."
        return null
    }
    return result
}

private void validateTuning() {
    [ctMorningExponent: [0.2G, 5G, 1.6G], dimMorningExponent: [0.2G, 5G, 0.7G],
     eveningDimPct: [1G, 100G, 85G],
     middayPlateauMins: [0G, 360G, 60G]].each { key, bounds ->
        BigDecimal value = settings[key] == null ? bounds[2] : new BigDecimal(settings[key].toString())
        if (value < bounds[0] || value > bounds[1]) throw new IllegalArgumentException("Invalid tuning: ${key}")
    }
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

private Map getTodaySunTimes(Date now) {
    Map s = getSunriseAndSunset()
    if (!(s?.sunrise instanceof Date) || !(s?.sunset instanceof Date) ||
        !s.sunset.after(s.sunrise) ||
        s.sunrise.format("yyyy-MM-dd", location.timeZone) != now.format("yyyy-MM-dd", location.timeZone) ||
        s.sunset.format("yyyy-MM-dd", location.timeZone) != now.format("yyyy-MM-dd", location.timeZone)) {
        throw new IllegalStateException("Current-day sunrise/sunset unavailable")
    }
    Date noon = new Date(s.sunrise.time + ((s.sunset.time - s.sunrise.time) / 2L as Long))
    [sunrise: s.sunrise, sunset: s.sunset, noon: noon]
}

private Map buildDimmerSchedule(Date now, Map limits) {
    Date start = localTimeOnDay(settings.startTimeStr, now)
    Date peak = localTimeOnDay(settings.workdayPeakTimeStr ?: "08:00", now)
    Date evening = localTimeOnDay(settings.eveningTransitionTimeStr ?: "18:30", now)
    Date end = localTimeOnDay(settings.endTimeStr, now)
    if (!(start < peak && peak < evening && evening < end)) {
        throw new IllegalArgumentException("Set household times in order: start, peak, evening, end.")
    }
    long plateauEnd = Math.min(peak.time + minutesToMillis(settings.middayPlateauMins, 60G),
        Math.max(peak.time, evening.time - 60000L))
    BigDecimal eveningLevel = clamp(settingDecimal("eveningDimPct", 85G), limits.minDim, limits.maxDim)
    [start: start, end: end, noonA: peak, noonB: new Date(plateauEnd), dimEveStart: evening, eveningLevel: eveningLevel]
}

private Map buildColorSchedule(Date now, Map household) {
    Map sun = getTodaySunTimes(now)
    boolean solar = settings.solarAnchors == true
    Date start = solar ? sun.sunrise : household.start
    Date end = solar ? sun.sunset : household.end
    if (!(start < sun.noon && sun.noon < end)) {
        throw new IllegalArgumentException("The color temperature window must contain solar noon.")
    }
    [start: start, end: end, noonA: sun.noon]
}

// Hubitat time inputs can contain an old date/UTC offset; retain their local HH:mm.
// Calendar resolves a spring gap forward and an autumn fold to standard time.
private Date localTimeOnDay(String value, Date day) {
    def match = (value ?: "") =~ /^(?:\d{4}-\d{2}-\d{2}T)?(\d{2}):(\d{2})(?::\d{2}(?:\.\d+)?)?(?:Z|[+-]\d{2}:?\d{2})?$/
    if (!match.matches()) throw new IllegalArgumentException("Invalid local time")
    int hour = match[0][1] as Integer
    int minute = match[0][2] as Integer
    if (hour > 23 || minute > 59) throw new IllegalArgumentException("Invalid local time")
    Calendar base = Calendar.getInstance(location.timeZone)
    base.time = day
    Calendar target = Calendar.getInstance(location.timeZone)
    target.clear()
    target.set(base.get(Calendar.YEAR), base.get(Calendar.MONTH), base.get(Calendar.DAY_OF_MONTH), hour, minute, 0)
    return target.time
}

private Date nextLocalTime(String clock, Date now) {
    Date candidate = localTimeOnDay(clock, now)
    if (!candidate.after(now)) {
        Calendar tomorrow = Calendar.getInstance(location.timeZone)
        tomorrow.time = now
        tomorrow.add(Calendar.DAY_OF_MONTH, 1)
        candidate = localTimeOnDay(clock, tomorrow.time)
    }
    return candidate
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

private BigDecimal powFraction(BigDecimal t, BigDecimal exponent) {
    BigDecimal clamped = clamp(t ?: 0G, 0G, 1G)
    BigDecimal exp = (exponent && exponent > 0G) ? exponent : 1G
    double result = Math.pow(clamped.doubleValue(), exp.doubleValue())
    return BigDecimal.valueOf(result)
}

// Re-map exponent so larger values accelerate the morning rise (fixes v2.0.3 color temp curve)
private BigDecimal morningExponentAdjusted(BigDecimal exponent) {
    if (!(exponent && exponent > 0G)) {
        return 1G
    }
    double adjusted = 1d / exponent.doubleValue()
    return BigDecimal.valueOf(adjusted)
}

private boolean validateNumericVar(String varName, String description) {
    if (!varName) {
        log.warn "${description} Hub Variable is not selected."
        return false
    }

    def gv = safeGetGlobalVar(varName)
    if (!gv) {
        log.warn "Hub Variable '${varName}' for ${description} not found. Create it first in Settings → Hub Variables."
        return false
    }

    if (!(gv.type in ["integer", "bigdecimal"])) {
        log.warn "Hub Variable '${varName}' for ${description} must be type integer or bigdecimal; found '${gv.type}'."
        return false
    }

    BigDecimal value = readGlobalVarDecimal(varName)
    if (value == null) {
        log.warn "Hub Variable '${varName}' for ${description} does not contain a numeric value."
        return false
    }

    return true
}

private boolean validateSettingVarWithinRange(String settingKey, String description, BigDecimal min, BigDecimal max) {
    String varName = settings[settingKey]
    if (!validateNumericVar(varName, description)) {
        return false
    }

    BigDecimal value = readGlobalVarDecimal(varName)
    if (value == null) {
        return false
    }

    if (value < min || value > max) {
        log.warn "Hub Variable '${varName}' for ${description} should be between ${min} and ${max}; found ${value}."
        return false
    }

    return true
}

private BigDecimal hubVarDecimal(String settingName, BigDecimal defaultVal) {
    String varName = settings[settingName]
    BigDecimal value = readGlobalVarDecimal(varName)
    return value != null ? value : defaultVal
}

private BigDecimal readGlobalVarDecimal(String varName) {
    if (!varName) return null
    def gv = safeGetGlobalVar(varName)
    def raw = gv?.value
    if (raw == null) return null
    try {
        return raw as BigDecimal
    } catch (ignored) {
        return null
    }
}

private def safeGetGlobalVar(String name) {
    if (!name) return null
    try {
        return getGlobalVar(name)
    } catch (ignored) {
        return null
    }
}

private BigDecimal settingDecimal(String name, BigDecimal defaultVal) {
    def raw = settings[name]
    if (raw == null) return defaultVal
    try {
        return raw as BigDecimal
    } catch (ignored) {
        return defaultVal
    }
}

private Long minutesToMillis(def minutesVal, BigDecimal defaultVal) {
    BigDecimal mins
    if (minutesVal == null) {
        mins = defaultVal
    } else {
        mins = minutesVal as BigDecimal
    }
    if (mins < 0G) mins = 0G
    BigDecimal millis = mins * 60000G
    return millis.setScale(0, BigDecimal.ROUND_HALF_UP).longValue()
}

private void scheduleRetry(int seconds) {
    if (seconds < 1) return
    unschedule("updateNow")
    runIn(seconds, "updateNow")
}

private Long minNonNull(Long a, Long b) {
    if (a == null) return b
    if (b == null) return a
    return Math.min(a, b)
}

private Long secondsUntilNextDimmerStep(Date now, Date winStart, Date winEnd, Map anchors,
                                        BigDecimal minP, BigDecimal maxP, BigDecimal morningExponent) {
    if (now.after(winEnd)) return null
    List<Date> boundaries = [winStart, anchors.noonA, anchors.noonB, anchors.dimEveStart, winEnd]
    Date cursor = now
    BigDecimal startVal = dimmerRaw(cursor, winStart, winEnd, anchors, minP, maxP, morningExponent)

    for (Date boundary : boundaries) {
        if (boundary.before(cursor)) continue
        BigDecimal endVal = dimmerRaw(boundary, winStart, winEnd, anchors, minP, maxP, morningExponent)
        BigDecimal delta = endVal - startVal
        if (delta.abs() < 1G) {
            if (boundary.after(now)) {
                long waitMs = boundary.time - now.time
                long waitSeconds = (waitMs / 1000L) as Long
                return waitSeconds < 1L ? 1L : waitSeconds
            }
            cursor = boundary
            startVal = endVal
            continue
        }
        boolean increasing = delta > 0G
        BigDecimal target = startVal + (increasing ? 1G : -1G)
        Date next = findNextChangeTime(cursor, boundary, target, increasing, winStart, winEnd, anchors, minP, maxP, morningExponent)
        long waitMs = next.time - now.time
        long waitSeconds = (waitMs / 1000L) as Long
        return waitSeconds < 1L ? 1L : waitSeconds
    }

    return null
}

private Long secondsUntilNextCtStep(Date now, Date winStart, Date winEnd, Map anchors,
                                    BigDecimal minCT, BigDecimal maxCT, BigDecimal morningExponent) {
    if (now.after(winEnd)) return null
    List<Date> boundaries = [winStart, anchors.noonA, winEnd]
    Date cursor = now
    BigDecimal startVal = ctRaw(cursor, winStart, winEnd, anchors, minCT, maxCT, morningExponent)
    BigDecimal step = ((maxCT - minCT).abs() / 100G).setScale(0, BigDecimal.ROUND_HALF_UP)
    if (step < 1G) step = 1G

    for (Date boundary : boundaries) {
        if (boundary.before(cursor)) continue
        BigDecimal endVal = ctRaw(boundary, winStart, winEnd, anchors, minCT, maxCT, morningExponent)
        BigDecimal delta = endVal - startVal
        if (delta.abs() < step) {
            if (boundary.after(now)) {
                long waitMs = boundary.time - now.time
                long waitSeconds = (waitMs / 1000L) as Long
                return waitSeconds < 1L ? 1L : waitSeconds
            }
            cursor = boundary
            startVal = endVal
            continue
        }
        boolean increasing = delta > 0G
        BigDecimal target = startVal + (increasing ? step : -step)
        Date next = findNextCtChangeTime(cursor, boundary, target, increasing, winStart, winEnd, anchors, minCT, maxCT, morningExponent)
        long waitMs = next.time - now.time
        long waitSeconds = (waitMs / 1000L) as Long
        return waitSeconds < 1L ? 1L : waitSeconds
    }

    return null
}

private Date findNextChangeTime(Date start, Date end, BigDecimal target, boolean increasing, Date winStart, Date winEnd, Map anchors,
                                BigDecimal minP, BigDecimal maxP, BigDecimal morningExponent) {
    long low = start.time
    long high = end.time
    for (int i = 0; i < 24; i++) {
        long mid = (low + high) / 2L
        BigDecimal val = dimmerRaw(new Date(mid), winStart, winEnd, anchors, minP, maxP, morningExponent)
        boolean reached = increasing ? (val >= target) : (val <= target)
        if (reached) {
            high = mid
        } else {
            low = mid + 1L
        }
    }
    return new Date(high)
}

private Date findNextCtChangeTime(Date start, Date end, BigDecimal target, boolean increasing, Date winStart, Date winEnd, Map anchors,
                                  BigDecimal minCT, BigDecimal maxCT, BigDecimal morningExponent) {
    long low = start.time
    long high = end.time
    for (int i = 0; i < 24; i++) {
        long mid = (low + high) / 2L
        BigDecimal val = ctRaw(new Date(mid), winStart, winEnd, anchors, minCT, maxCT, morningExponent)
        boolean reached = increasing ? (val >= target) : (val <= target)
        if (reached) {
            high = mid
        } else {
            low = mid + 1L
        }
    }
    return new Date(high)
}

private BigDecimal dimmerRaw(Date now, Date winStart, Date winEnd, Map anchors,
                             BigDecimal minP, BigDecimal maxP, BigDecimal morningExponent) {
    BigDecimal dimmer = computeDimmer(now, null, winStart, winEnd, anchors, minP, maxP, morningExponent)
    return clamp(dimmer, minP, maxP)
}

private BigDecimal ctRaw(Date now, Date winStart, Date winEnd, Map anchors,
                         BigDecimal minCT, BigDecimal maxCT, BigDecimal morningExponent) {
    BigDecimal ctemp = computeCT(now, null, winStart, winEnd, anchors, minCT, maxCT, morningExponent)
    return clamp(ctemp, minCT, maxCT)
}

/** Dimmer logic */
private BigDecimal computeDimmer(Date now, Map sun, Date winStart, Date winEnd, Map anchors,
                                 BigDecimal minP, BigDecimal maxP, BigDecimal morningExponent) {
    BigDecimal startLevel = minP
    BigDecimal lateDay = anchors.eveningLevel

    if (now.before(anchors.noonA)) {
        BigDecimal t = frac(now, winStart, anchors.noonA)
        BigDecimal shaped = powFraction(ease(t), morningExponentAdjusted(morningExponent))
        return lerp(startLevel, maxP, shaped)
    } else if (now.before(anchors.noonB)) {
        return maxP
    } else if (now.before(anchors.dimEveStart)) {
        BigDecimal t = frac(now, anchors.noonB, anchors.dimEveStart)
        BigDecimal eased = ease(t)
        return lerp(maxP, lateDay, eased)
    } else if (now.before(winEnd)) {
        BigDecimal t = frac(now, anchors.dimEveStart, winEnd)
        BigDecimal eased = ease(t)
        return lerp(lateDay, minP, eased)
    } else {
        return minP
    }
}

/** Color Temp logic (Kelvin) */
private BigDecimal computeCT(Date now, Map sun, Date winStart, Date winEnd, Map anchors,
                             BigDecimal lo, BigDecimal hi, BigDecimal morningExponent) {
    if (now.before(anchors.noonA)) {
        BigDecimal progress = ease(frac(now, winStart, anchors.noonA))
        return lerp(lo, hi, powFraction(progress, morningExponentAdjusted(morningExponent)))
    }
    return lerp(hi, lo, ease(frac(now, anchors.noonA, winEnd)))
}

// Returns 0..1 fraction between two Dates (clamped)
private BigDecimal frac(Date now, Date a, Date b) {
    BigDecimal A = a.time, B = b.time, N = now.time
    if (B <= A) return 1.0G
    BigDecimal t = (N - A) / (B - A)
    return clamp(t, 0G, 1G)
}
