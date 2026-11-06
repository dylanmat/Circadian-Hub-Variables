import groovy.transform.Field

@Field final String APP_NAME    = "Circadian Hub Variables"
@Field final String APP_VERSION = "2.1.6"
@Field final String APP_BRANCH  = "main"
@Field final String APP_UPDATED = "2026-11-05"    // ISO date is clean

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
            input name: "solarAnchors", type: "bool", title: "Use sunrise/sunset anchors (opt in; existing curves stay unchanged until enabled)", defaultValue: false
            paragraph "Use a same-day window of at least 4 minutes. Overnight windows are not supported. Solar mode reaches daytime levels by sunrise or the peak deadline, whichever is earlier, and starts evening transition by sunset or the evening deadline, whichever is earlier."

            input name: "startTimeStr", type: "time", title: "Active window start (HH:MM — earliest adjustments begin)", required: true, defaultValue: "05:30"
            input name: "endTimeStr",   type: "time", title: "Active window end (HH:MM — final targets reached)", required: true, defaultValue: "22:30"
            input name: "workdayPeakTimeStr", type: "time", title: "Daytime peak deadline (solar mode only; legacy peaks near solar noon)", required: true, defaultValue: "08:00"
            input name: "eveningTransitionTimeStr", type: "time", title: "Evening transition deadline (solar mode) / fixed anchor (legacy)", required: true, defaultValue: "18:30"
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
            input name: "ctEveningHeadStartMins", type: "number", range: "0..240", title: "CT evening head start (mins) — larger = earlier warm shift", defaultValue: 60, required: true
            input name: "dimEveningLagMins", type: "number", range: "0..240", title: "Dimmer evening lag (mins) — larger = later dimming", defaultValue: 30, required: true
            input name: "middayPlateauMins", type: "number", range: "0..360", title: "Midday plateau length (mins) — 0 disables the flat top", defaultValue: 60, required: true
        }
        section("Update & Behavior") {
            paragraph "Updates are scheduled dynamically based on the next dimmer or color temperature step to balance hub load with smooth transitions."
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
    Date winStart = localTimeOnDay(settings.startTimeStr, now)
    Date winEnd = localTimeOnDay(settings.endTimeStr, now)
    if (winEnd.time - winStart.time < 240000L) {
        throw new IllegalArgumentException("Use a same-day window of at least 4 minutes")
    }
    validateTuning()

    boolean inWindow = isBetween(now, winStart, winEnd)
    if (onlyWithinWindow && !inWindow) {
        if (logDebug) log.debug "Outside active window; skipping write."
        scheduleNextWindowStart(now, winStart)
        return
    }

    Map sun = getTodaySunTimes(now, winStart, winEnd)
    Map anchors = buildAnchors(sun, winStart, winEnd)
    BigDecimal minDimBD = snapshot.minDim
    BigDecimal maxDimBD = snapshot.maxDim
    BigDecimal minCTBD = snapshot.minCT
    BigDecimal maxCTBD = snapshot.maxCT
    BigDecimal dimMorningExp = settingDecimal("dimMorningExponent", 0.7G)
    BigDecimal ctMorningExp = settingDecimal("ctMorningExponent", 1.6G)

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

    scheduleNextUpdate(now, winStart, winEnd, anchors, minDimBD, maxDimBD, dimMorningExp, minCTBD, maxCTBD, ctMorningExp)
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
     ctEveningHeadStartMins: [0G, 240G, 60G], dimEveningLagMins: [0G, 240G, 30G],
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

private Map getTodaySunTimes(Date now, Date winStart, Date winEnd) {
    Map s = getSunriseAndSunset()
    if (!(s?.sunrise instanceof Date) || !(s?.sunset instanceof Date) ||
        !s.sunset.after(s.sunrise) ||
        s.sunrise.format("yyyy-MM-dd", location.timeZone) != now.format("yyyy-MM-dd", location.timeZone) ||
        s.sunset.format("yyyy-MM-dd", location.timeZone) != now.format("yyyy-MM-dd", location.timeZone)) {
        throw new IllegalStateException("Current-day sunrise/sunset unavailable")
    }
    Date workdayPeak = localTimeOnDay(settings.workdayPeakTimeStr ?: "08:00", now)
    Date eveningStart = localTimeOnDay(settings.eveningTransitionTimeStr ?: "18:30", now)
    if (workdayPeak.before(winStart)) workdayPeak = winStart
    if (workdayPeak.after(winEnd)) workdayPeak = winEnd
    if (eveningStart.before(workdayPeak)) eveningStart = workdayPeak
    if (eveningStart.after(winEnd)) eveningStart = winEnd
    Date noon = new Date(s.sunrise.time + ((s.sunset.time - s.sunrise.time) / 2L as Long))
    [sunrise: s.sunrise, sunset: s.sunset, morningEnd: workdayPeak, eveningStart: eveningStart, noon: noon]
}

private Map buildAnchors(Map sun, Date winStart, Date winEnd) {
    long halfPlateau = minutesToMillis(settings.middayPlateauMins, 60G) / 2L as Long
    long peak = sun.noon.time - halfPlateau
    long evening = sun.eveningStart.time
    if (settings.solarAnchors == true) {
        peak = Math.min(sun.sunrise.time, sun.morningEnd.time)
        evening = Math.min(sun.sunset.time, evening)
    }
    // Reserve nonzero morning/evening segments even in narrow windows.
    long a = Math.max(winStart.time + 60000L, Math.min(peak, winEnd.time - 180000L))
    long b = Math.max(a, Math.min(sun.noon.time + halfPlateau, winEnd.time - 120000L))
    long ct = Math.max(b + 60000L, Math.min(evening - minutesToMillis(settings.ctEveningHeadStartMins, 60G), winEnd.time - 60000L))
    // Keep the plateau from delaying an early seasonal evening anchor.
    if (settings.solarAnchors == true) {
        b = Math.max(a, Math.min(b, Math.min(evening - minutesToMillis(settings.ctEveningHeadStartMins, 60G) - 60000L, winEnd.time - 120000L)))
        ct = Math.max(b + 60000L, Math.min(evening - minutesToMillis(settings.ctEveningHeadStartMins, 60G), winEnd.time - 60000L))
    }
    long dim = Math.max(ct, Math.min(evening + minutesToMillis(settings.dimEveningLagMins, 30G), winEnd.time - 60000L))
    [noonA: new Date(a), noonB: new Date(b), ctEveStart: new Date(ct), dimEveStart: new Date(dim)]
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

private Date nextWindowStart(Date now) {
    Date candidate = localTimeOnDay(settings.startTimeStr, now)
    if (!candidate.after(now)) {
        Calendar tomorrow = Calendar.getInstance(location.timeZone)
        tomorrow.time = now
        tomorrow.add(Calendar.DAY_OF_MONTH, 1)
        candidate = localTimeOnDay(settings.startTimeStr, tomorrow.time)
    }
    return candidate
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
    Date nextStart = nextWindowStart(now)
    long delayMs = Math.max(1000L, nextStart.time - now.time)
    int delaySeconds = Math.max(1, ((delayMs + 999L) / 1000L) as Integer)
    if (logDebug) log.debug "Scheduling next update at window start in ${delaySeconds}s"
    unschedule("updateNow")
    runIn(delaySeconds, "updateNow")
}

private void scheduleNextUpdate(Date now, Date winStart, Date winEnd, Map anchors,
                                BigDecimal minP, BigDecimal maxP, BigDecimal morningExponent,
                                BigDecimal minCT, BigDecimal maxCT, BigDecimal ctMorningExponent) {
    if (onlyWithinWindow && !isBetween(now, winStart, winEnd)) {
        scheduleNextWindowStart(now, winStart)
        return
    }

    Long dimmerDelay = secondsUntilNextDimmerStep(now, winStart, winEnd, anchors, minP, maxP, morningExponent)
    Long ctDelay = secondsUntilNextCtStep(now, winStart, winEnd, anchors, minCT, maxCT, ctMorningExponent)
    Long delaySeconds = minNonNull(dimmerDelay, ctDelay)
    if (delaySeconds == null) {
        scheduleNextWindowStart(now, winStart)
        return
    }
    long safeSeconds = Math.max(1L, Math.min(delaySeconds, 86400L))
    if (logDebug) log.debug "Scheduling next update in ${safeSeconds}s"
    unschedule("updateNow")
    runIn(safeSeconds as Integer, "updateNow")
}

private Long minNonNull(Long a, Long b) {
    if (a == null) return b
    if (b == null) return a
    return Math.min(a, b)
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
    List<Date> boundaries = [anchors.noonA, anchors.noonB, anchors.ctEveStart, winEnd]
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
