// Run: java -cp <groovy-all-2.4.21.jar> groovy.ui.GroovyMain tests/circadian_regression.groovy
import java.text.SimpleDateFormat

def zone = TimeZone.getTimeZone("America/Los_Angeles")
def settings = [startTimeStr: "05:30", endTimeStr: "22:30", workdayPeakTimeStr: "08:00", eveningTransitionTimeStr: "18:30", dimmerVarName: "dim", ctVarName: "ct", minDim: "lo", maxDim: "hi", minCT: "warm", maxCT: "cool", onlyWithinWindow: false, snapToInt: true]
def variables = [dim: [type: "integer", value: 20], ct: [type: "integer", value: 2000], lo: [type: "integer", value: 20], hi: [type: "integer", value: 100], warm: [type: "integer", value: 2000], cool: [type: "integer", value: 6500]]
def warnings = [], calls = [], writes = [], reads = [:]
def binding = new Binding([settings: settings, location: [timeZone: zone], logDebug: false,
    log: [warn: { warnings << it }, debug: {}, info: {}],
    definition: { Map ignored -> }, preferences: { Closure ignored -> },
    getGlobalVar: { String name -> reads[name] = (reads[name] ?: 0) + 1; variables[name] },
    setGlobalVar: { String name, value -> writes << [name, value]; true },
    unschedule: { String name -> }, runIn: { seconds, name -> calls << [seconds, name] }])
settings.each { k,v -> binding.setVariable(k,v) }
def app = new GroovyShell(binding).parse(new File("circadian_hub_variables_hubitat_app.groovy"))
app.run()
def date = { String text -> def f = new SimpleDateFormat("yyyy-MM-dd HH:mm"); f.timeZone = zone; f.parse(text) }
def fmt = { Date d -> d.format("yyyy-MM-dd HH:mm", zone) }
def call = { String name, Object... args -> app.invokeMethod(name, args) }

// Real app methods, no mirrored date or curve implementation.
assert fmt(call('nextLocalTime', settings.startTimeStr, date('2026-10-31 23:00'))) == '2026-11-01 05:30'
assert fmt(call('nextLocalTime', settings.startTimeStr, date('2026-03-07 23:00'))) == '2026-03-08 05:30'
assert fmt(call('nextLocalTime', settings.startTimeStr, date('2026-12-31 23:00'))) == '2027-01-01 05:30'
assert fmt(call('nextLocalTime', settings.startTimeStr, date('2026-11-02 05:30'))) == '2026-11-03 05:30'
assert fmt(call('nextLocalTime', settings.startTimeStr, date('2026-11-02 05:29'))) == '2026-11-02 05:30'
settings.startTimeStr = '02:30'
assert fmt(call('nextLocalTime', settings.startTimeStr, date('2026-03-07 23:00'))) == '2026-03-08 03:30'
settings.startTimeStr = '01:30'
def folded = call('nextLocalTime', settings.startTimeStr, date('2026-10-31 23:00'))
assert folded.format('yyyy-MM-dd HH:mm Z', zone) == '2026-11-01 01:30 -0800'
settings.startTimeStr = '2020-01-01T05:30:00.000-0800'
assert fmt(call('nextLocalTime', settings.startTimeStr, date('2026-07-01 23:00'))) == '2026-07-02 05:30'
binding.getVariable('location').timeZone = TimeZone.getTimeZone('UTC')
assert call('nextLocalTime', settings.startTimeStr, new Date(0)).format('yyyy-MM-dd HH:mm', TimeZone.getTimeZone('UTC')) == '1970-01-01 05:30'
binding.getVariable('location').timeZone = zone
settings.startTimeStr = '05:30'
assert call('readValidatedSnapshot').minDim == 20
assert reads.values().every { it == 1 }
settings.ctVarName = 'dim'; assert call('readValidatedSnapshot') == null
settings.ctVarName = 'ct'; settings.minDim = 'dim'; assert call('readValidatedSnapshot') == null
settings.minDim = 'lo'; variables.lo.value = 101; assert call('readValidatedSnapshot') == null
variables.lo.value = 20; variables.ct.type = 'string'; assert call('readValidatedSnapshot') == null
variables.ct.type = 'integer'; variables.ct.value = null; assert call('readValidatedSnapshot') == null
variables.ct.value = 2000; variables.lo.value = 90; variables.hi.value = 80; assert call('readValidatedSnapshot') == null
variables.lo.value = 20; variables.hi.value = 100
settings.middayPlateauMins = 361
try { call('validateTuning'); assert false } catch (IllegalArgumentException expected) {}
settings.middayPlateauMins = 60

def limits = call('readValidatedSnapshot')
settings.eveningDimPct = 75
def today = new Date()
def rise = call('localTimeOnDay', '07:00', today)
def sunset = call('localTimeOnDay', '17:00', today)
binding.setVariable('getSunriseAndSunset', { [sunrise: rise, sunset: sunset] })
def dim = call('buildDimmerSchedule', today, limits)
assert dim.noonB.time - dim.noonA.time == 3600000L
for (boolean solar : [false, true]) {
    settings.solarAnchors = solar
    def ct = call('buildColorSchedule', today, dim)
    assert ct.start == (solar ? rise : dim.start)
    assert ct.end == (solar ? sunset : dim.end)
    assert ct.noonA == call('localTimeOnDay', '12:00', today)
    assert call('buildDimmerSchedule', today, limits) == dim
    assert call('computeCT', ct.noonA, null, ct.start, ct.end, ct, 2000G, 6500G, 1.6G) == 6500G
    assert call('computeCT', ct.end, null, ct.start, ct.end, ct, 2000G, 6500G, 1.6G) == 2000G
}
[[dim.start, 20G], [dim.noonA, 100G], [dim.noonB, 100G], [dim.dimEveStart, 75G], [dim.end, 20G]].each { time, expected ->
    assert call('computeDimmer', time, null, dim.start, dim.end, dim, 20G, 100G, 0.7G) == expected
}
call('updateNow')
assert writes*.getAt(0) == ['dim', 'ct']
assert calls.last()[0] > 0
writes.clear()
binding.setVariable('getSunriseAndSunset', { throw new IllegalStateException('unavailable') })
call('updateNow')
assert writes*.getAt(0) == ['dim']
assert calls.last()[0] in 1..300
writes.clear()
settings.endTimeStr = '04:00'
call('updateNow')
assert writes.empty && calls.last() == [300, 'updateNow']
println 'PASS: DST, input validation, independent schedules, curve endpoints and solar recovery'
