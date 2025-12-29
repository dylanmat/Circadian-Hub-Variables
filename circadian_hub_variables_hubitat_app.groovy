import groovy.transform.Field

@Field final String APP_NAME    = "Circadian Hub Variables"
@Field final String APP_VERSION = "2.1.1"
@Field final String APP_BRANCH  = "main"
@Field final String APP_UPDATED = "2025-11-02"    // ISO date is clean

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
        section("Time Window & Curves") {
            input name: "startTimeStr", type: "time", title: "Active window start (HH:MM — earliest adjustments begin)", required: true, defaultValue: "05:30"
            input name: "endTimeStr",   type: "time", title: "Active window end (HH:MM — final targets reached)", required: true, defaultValue: "22:30"
            input name: "workdayPeakTimeStr", type: "time", title: "Workday daylight target time (HH:MM — reach daytime levels by this time)", required: true, defaultValue: "08:00"
            input name: "eveningTransitionTimeStr", type: "time", title: "Evening wind‑down start (HH:MM — begin warming + dimming)", required: true, defaultValue: "18:30"
        }
        section("Targets & Limits") {
            if (!numericVarNames) {
                paragraph "Targets & limits require numeric Hub Variables. Add them under Settings → Hub Variables first."
            }
            input name: "minDim", type: "enum", options: numericVarNames, title: "Evening dimmer minimum (%) Hub Variable — expected value 1-100", required: true, submitOnChange: true
            input name: "maxDim", type: "enum", options: numericVarNames, title: "Daytime dimmer maximum (%) Hub Variable — expected value 1-100", required: true, submitOnChange: true
            input name: "minCT",  type: "enum", options: numericVarNames, title: "Evening color temp minimum (K) Hub Variable — expected value 1500-4000", required: true, submitOnChange: true
            input name: "maxCT",  type: "enum", options: numericVarNames, title: "Daytime color temp maximum (K) Hub Variable — expected value 4500-8000", required: true, submitOnChange: true
        }
        section("Wellness Tuning") {
            input name: "ctMorningExponent", type: "decimal", title: "CT morning exponent — higher = faster ramp, lower = gentler", range: "0.2..5.0", defaultValue: 1.6, required: true
            input name: "dimMorningExponent", type: "decimal", title: "Dimmer morning exponent — higher = faster ramp", range: "0.2..5.0", defaultValue: 0.7, required: true
            input name: "ctEveningHeadStartMins", type: "number", title: "CT evening head start (mins) — larger = earlier warm shift", defaultValue: 60, required: true
            input name: "dimEveningLagMins", type: "number", title: "Dimmer evening lag (mins) — larger = later dimming", defaultValue: 30, required: true
            input name: "middayPlateauMins", type: "number", title: "Midday plateau length (mins) — 0 disables the flat top", defaultValue: 60, required: true
        }
        section("Update & Behavior") {
            paragraph "Updates are scheduled dynamically based on the next 1% dimmer change to balance hub load with smooth transitions."
            input name: "onlyWithinWindow", type: "bool", title: "Only write variables within active window (values stay put outside)", defaultValue: true
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
    if (!validateVars()) {
        scheduleRetry(300)
        return
    }

    Map sun = getTodaySunTimes()
    Date now = new Date()

    Date winStart = timeToday(startTimeStr)
    Date winEnd   = timeToday(endTimeStr)

    boolean inWindow = isBetween(now, winStart, winEnd)
    if (onlyWithinWindow && !inWindow) {
        if (logDebug) log.debug "Outside active window; skipping write."
        scheduleNextWindowStart(now, winStart)
        return
    }

    BigDecimal minDimBD = hubVarDecimal("minDim", 20G)
    BigDecimal maxDimBD = hubVarDecimal("maxDim", 100G)
    BigDecimal minCTBD  = hubVarDecimal("minCT", 2000G)
    BigDecimal maxCTBD  = hubVarDecimal("maxCT", 6500G)

    BigDecimal dimMorningExp = settingDecimal("dimMorningExponent", 0.7G)
    BigDecimal ctMorningExp  = settingDecimal("ctMorningExponent", 1.6G)

    Long plateauMillis = minutesToMillis(settings.middayPlateauMins, 60G)
    Long plateauHalf   = (plateauMillis != null && plateauMillis > 0L) ? (plateauMillis.longValue() / 2L) : 0L
    Long ctHeadStartMs = minutesToMillis(settings.ctEveningHeadStartMins, 60G)
    Long dimLagMs      = minutesToMillis(settings.dimEveningLagMins, 30G)

    long noonTime = sun.noon.time
    Date noonA       = new Date(noonTime - plateauHalf.longValue())
    Date noonB       = new Date(noonTime + plateauHalf.longValue())
    long eveningStartTime = sun.eveningStart.time
    Date ctEveStart  = new Date(eveningStartTime - ctHeadStartMs.longValue())
    Date dimEveStart = new Date(eveningStartTime + dimLagMs.longValue())

    if (ctEveStart.before(noonB)) ctEveStart = noonB
    if (ctEveStart.after(winEnd)) ctEveStart = winEnd
    if (dimEveStart.before(ctEveStart)) dimEveStart = ctEveStart
    if (dimEveStart.after(winEnd)) dimEveStart = winEnd

    Map anchors = [noonA: noonA, noonB: noonB, ctEveStart: ctEveStart, dimEveStart: dimEveStart]

    // Build eased targets
    BigDecimal dimmer = computeDimmer(now, sun, winStart, winEnd, anchors, minDimBD, maxDimBD, dimMorningExp)
    BigDecimal ctemp  = computeCT(now, sun, winStart, winEnd, anchors, minCTBD, maxCTBD, ctMorningExp)

    dimmer = clamp(dimmer, minDimBD, maxDimBD)
    ctemp  = clamp(ctemp,  minCTBD,  maxCTBD)

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

    scheduleNextUpdate(now, winStart, winEnd, anchors, minDimBD, maxDimBD, dimMorningExp)
}

private boolean validateVars() {
    boolean ok = true

    if (!validateNumericVar(dimmerVarName, "Dimmer % output")) ok = false
    if (!validateNumericVar(ctVarName, "Color Temp (K) output")) ok = false
    if (!validateSettingVarWithinRange("minDim", "Evening dimmer minimum (%)", 1G, 100G)) ok = false
    if (!validateSettingVarWithinRange("maxDim", "Daytime dimmer maximum (%)", 1G, 100G)) ok = false
    if (!validateSettingVarWithinRange("minCT",  "Evening color temp minimum (K)", 1500G, 4000G)) ok = false
    if (!validateSettingVarWithinRange("maxCT",  "Daytime color temp maximum (K)", 4500G, 8000G)) ok = false

    if (!ok) {
        return false
    }

    BigDecimal minDimVal = readGlobalVarDecimal(settings.minDim)
    BigDecimal maxDimVal = readGlobalVarDecimal(settings.maxDim)
    if (minDimVal != null && maxDimVal != null && minDimVal > maxDimVal) {
        log.warn "Evening dimmer minimum (%) should be less than or equal to daytime maximum."
        ok = false
    }

    BigDecimal minCTVal = readGlobalVarDecimal(settings.minCT)
    BigDecimal maxCTVal = readGlobalVarDecimal(settings.maxCT)
    if (minCTVal != null && maxCTVal != null && minCTVal > maxCTVal) {
        log.warn "Evening color temp minimum (K) should be less than or equal to daytime maximum."
        ok = false
    }

    return ok
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

    Date winStart = timeToday(startTimeStr ?: "05:30")
    Date winEnd   = timeToday(endTimeStr   ?: "22:30")

    Date workdayPeak = timeToday(settings.workdayPeakTimeStr ?: "08:00")
    Date eveningStart = timeToday(settings.eveningTransitionTimeStr ?: "18:30")

    if (workdayPeak.before(winStart)) workdayPeak = winStart
    if (workdayPeak.after(winEnd))   workdayPeak = winEnd
    if (eveningStart.before(workdayPeak)) eveningStart = workdayPeak
    if (eveningStart.after(winEnd))       eveningStart = winEnd

    Long noonMillis = (sunrise.time + sunset.time) / 2L
    Date solarNoon = new Date(noonMillis)

    [sunrise: sunrise, morningEnd: workdayPeak, eveningStart: eveningStart, sunset: sunset, noon: solarNoon]
}

private String sunriseSunsetSummary(Map s) {
    ["rise ${fmtTime(s.sunrise)}", "dayPeak ${fmtTime(s.morningEnd)}", "windDown ${fmtTime(s.eveningStart)}", "set ${fmtTime(s.sunset)}"].join(", ")
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

private BigDecimal powFraction(BigDecimal t, BigDecimal exponent) {
    BigDecimal clamped = clamp(t ?: 0G, 0G, 1G)
    BigDecimal exp = (exponent && exponent > 0G) ? exponent : 1G
    double result = Math.pow(clamped.doubleValue(), exp.doubleValue())
    return BigDecimal.valueOf(result)
}

// Half-period sine wave (start/end slope ≈ 0) mapping 0..1 → 0..1
private BigDecimal sineWave(BigDecimal t) {
    BigDecimal clamped = clamp(t ?: 0G, 0G, 1G)
    double theta = (-Math.PI / 2d) + (Math.PI * clamped.doubleValue())
    double result = (Math.sin(theta) + 1d) / 2d
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

private void scheduleNextWindowStart(Date now, Date winStart) {
    Date nextStart = winStart
    if (!now.before(winStart)) {
        nextStart = new Date(winStart.time + 86400000L)
    }
    long delayMs = Math.max(1000L, nextStart.time - now.time)
    int delaySeconds = Math.max(1, (delayMs / 1000L) as Integer)
    if (logDebug) log.debug "Scheduling next update at window start in ${delaySeconds}s"
    unschedule("updateNow")
    runIn(delaySeconds, "updateNow")
}

private void scheduleNextUpdate(Date now, Date winStart, Date winEnd, Map anchors,
                                BigDecimal minP, BigDecimal maxP, BigDecimal morningExponent) {
    if (onlyWithinWindow && !isBetween(now, winStart, winEnd)) {
        scheduleNextWindowStart(now, winStart)
        return
    }

    Long delaySeconds = secondsUntilNextDimmerStep(now, winStart, winEnd, anchors, minP, maxP, morningExponent)
    if (delaySeconds == null) {
        scheduleNextWindowStart(now, winStart)
        return
    }
    long safeSeconds = Math.max(1L, Math.min(delaySeconds, 86400L))
    if (logDebug) log.debug "Scheduling next update in ${safeSeconds}s"
    unschedule("updateNow")
    runIn(safeSeconds as Integer, "updateNow")
}

private Long secondsUntilNextDimmerStep(Date now, Date winStart, Date winEnd, Map anchors,
                                        BigDecimal minP, BigDecimal maxP, BigDecimal morningExponent) {
    if (now.after(winEnd)) return null
    List<Date> boundaries = [anchors.noonA, anchors.noonB, anchors.dimEveStart, winEnd]
    Date cursor = now
    BigDecimal startVal = dimmerRaw(cursor, winStart, winEnd, anchors, minP, maxP, morningExponent)

    for (Date boundary : boundaries) {
        if (boundary.before(cursor)) continue
        BigDecimal endVal = dimmerRaw(boundary, winStart, winEnd, anchors, minP, maxP, morningExponent)
        BigDecimal delta = endVal - startVal
        if (delta.abs() < 1G) {
            if (boundary.after(now)) {
                long waitMs = boundary.time - now.time
                return Math.max(1L, waitMs / 1000L)
            }
            cursor = boundary
            startVal = endVal
            continue
        }
        boolean increasing = delta > 0G
        BigDecimal target = startVal + (increasing ? 1G : -1G)
        Date next = findNextChangeTime(cursor, boundary, target, increasing, winStart, winEnd, anchors, minP, maxP, morningExponent)
        long waitMs = next.time - now.time
        return Math.max(1L, waitMs / 1000L)
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

private BigDecimal dimmerRaw(Date now, Date winStart, Date winEnd, Map anchors,
                             BigDecimal minP, BigDecimal maxP, BigDecimal morningExponent) {
    BigDecimal dimmer = computeDimmer(now, null, winStart, winEnd, anchors, minP, maxP, morningExponent)
    return clamp(dimmer, minP, maxP)
}

/** Dimmer logic */
private BigDecimal computeDimmer(Date now, Map sun, Date winStart, Date winEnd, Map anchors,
                                 BigDecimal minP, BigDecimal maxP, BigDecimal morningExponent) {
    BigDecimal startLevel = lerp(minP, maxP, 0.60G)
    BigDecimal lateDay    = lerp(minP, maxP, 0.85G)

    if (now.before(anchors.noonA)) {
        BigDecimal t = frac(now, winStart, anchors.noonA)
        BigDecimal shaped = powFraction(t, morningExponent)
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
    BigDecimal daySlightWarm = lerp(lo, hi, 0.92G)

    BigDecimal result
    if (now.before(anchors.noonA)) {
        BigDecimal t = frac(now, winStart, anchors.noonA)
        BigDecimal morningBase = sineWave(t)
        BigDecimal curveExp = morningExponentAdjusted(morningExponent)
        BigDecimal shaped = powFraction(morningBase, curveExp)
        result = lerp(lo, hi, shaped)
    } else if (now.before(anchors.noonB)) {
        result = hi
    } else if (now.before(anchors.ctEveStart)) {
        BigDecimal t = frac(now, anchors.noonB, anchors.ctEveStart)
        BigDecimal eased = sineWave(t)
        result = lerp(hi, daySlightWarm, eased)
    } else if (now.before(winEnd)) {
        BigDecimal t = frac(now, anchors.ctEveStart, winEnd)
        BigDecimal eased = sineWave(t)
        result = lerp(daySlightWarm, lo, eased)
    } else {
        result = lo
    }

    return result
}

// Returns 0..1 fraction between two Dates (clamped)
private BigDecimal frac(Date now, Date a, Date b) {
    BigDecimal A = a.time, B = b.time, N = now.time
    if (B <= A) return 1.0G
    BigDecimal t = (N - A) / (B - A)
    return clamp(t, 0G, 1G)
}
