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
assert fmt(call('nextWindowStart', date('2026-10-31 23:00'))) == '2026-11-01 05:30'
assert fmt(call('nextWindowStart', date('2026-03-07 23:00'))) == '2026-03-08 05:30'
assert fmt(call('nextWindowStart', date('2026-12-31 23:00'))) == '2027-01-01 05:30'
assert fmt(call('nextWindowStart', date('2026-11-02 05:30'))) == '2026-11-03 05:30'
assert fmt(call('nextWindowStart', date('2026-11-02 05:29'))) == '2026-11-02 05:30'
settings.startTimeStr = '02:30'
assert fmt(call('nextWindowStart', date('2026-03-07 23:00'))) == '2026-03-08 03:30'
settings.startTimeStr = '01:30'
def folded = call('nextWindowStart', date('2026-10-31 23:00'))
assert folded.format('yyyy-MM-dd HH:mm Z', zone) == '2026-11-01 01:30 -0800'
settings.startTimeStr = '2020-01-01T05:30:00.000-0800'
assert fmt(call('nextWindowStart', date('2026-07-01 23:00'))) == '2026-07-02 05:30'
binding.getVariable('location').timeZone = TimeZone.getTimeZone('UTC')
assert call('nextWindowStart', new Date(0)).format('yyyy-MM-dd HH:mm', TimeZone.getTimeZone('UTC')) == '1970-01-01 05:30'
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

for (String season : ['2026-12-21','2026-06-21','2026-03-20','2026-09-22','2026-03-08','2026-11-01']) {
    def start = date(season + ' 05:30'), end = date(season + ' 22:30')
    def sun = [sunrise: date(season + (season.endsWith('06-21') ? ' 05:00' : ' 07:00')), sunset: date(season + (season.endsWith('06-21') ? ' 21:00' : ' 17:00')), morningEnd: date(season + ' 08:00'), eveningStart: date(season + ' 18:30'), noon: date(season + ' 12:00')]
    for (boolean solar : [false,true]) {
        settings.solarAnchors = solar
        def anchors = call('buildAnchors',sun,start,end)
        assert start.before(anchors.noonA)
        assert anchors.noonA <= anchors.noonB && anchors.noonB < anchors.ctEveStart && anchors.ctEveStart <= anchors.dimEveStart && anchors.dimEveStart < end
        if (solar) {
            assert anchors.noonA.time == Math.max(start.time + 60000L, sun.sunrise.time)
            assert anchors.ctEveStart == date(season + (season.endsWith('06-21') ? ' 17:30' : ' 16:00'))
        }
        if (season.endsWith('12-21') || season.endsWith('06-21')) println "Preview ${season} solar=${solar}: " + anchors.collect { k,v -> "${k}=${v.format('HH:mm',zone)}" }.join(', ')
        for (long t = start.time; t <= end.time; t += 60000L) {
            def dim = call('computeDimmer',new Date(t),sun,start,end,anchors,20G,100G,0.7G)
            def ct = call('computeCT',new Date(t),sun,start,end,anchors,2000G,6500G,1.6G)
            assert dim >= 20G && dim <= 100G && ct >= 2000G && ct <= 6500G
        }
        for (Date edge : anchors.values()) {
            assert (call('computeDimmer',new Date(edge.time-1),sun,start,end,anchors,20G,100G,0.7G) - call('computeDimmer',edge,sun,start,end,anchors,20G,100G,0.7G)).abs() < 0.1G
            assert (call('computeCT',new Date(edge.time-1),sun,start,end,anchors,2000G,6500G,1.6G) - call('computeCT',edge,sun,start,end,anchors,2000G,6500G,1.6G)).abs() < 0.1G
        }
    }
}
// Extremes retain ordered continuous segments rather than creating negative spans.
settings.middayPlateauMins = 360; settings.ctEveningHeadStartMins = 240; settings.dimEveningLagMins = 240
for (boolean solar : [false,true]) {
    settings.solarAnchors = solar
    def start = date('2026-12-21 12:00'), end = date('2026-12-21 12:04')
    def sun = [sunrise: date('2026-12-21 10:00'), sunset: date('2026-12-21 13:00'), morningEnd: start, eveningStart: end, noon: date('2026-12-21 11:30')]
    def a = call('buildAnchors',sun,start,end)
    assert start < a.noonA && a.noonA <= a.noonB && a.noonB < a.ctEveStart && a.ctEveStart <= a.dimEveStart && a.dimEveStart < end
    for (Date edge : a.values()) {
        assert (call('computeCT',new Date(edge.time-1),sun,start,end,a,2000G,6500G,1.6G) - call('computeCT',edge,sun,start,end,a,2000G,6500G,1.6G)).abs() < 0.2G
    }
}
settings.middayPlateauMins = 60; settings.ctEveningHeadStartMins = 60; settings.dimEveningLagMins = 30
// Failures hold targets and establish a retry, rather than ending the update chain.
binding.setVariable('getSunriseAndSunset', { throw new IllegalStateException('unavailable') })
call('updateNow'); assert writes.empty && calls.last() == [300,'updateNow']
binding.setVariable('getSunriseAndSunset', { [sunrise: null,sunset: null] })
call('updateNow'); assert writes.empty && calls.last() == [300,'updateNow']
settings.endTimeStr = '04:00'
call('updateNow'); assert writes.empty && calls.last() == [300,'updateNow']
settings.endTimeStr = '05:32'
call('updateNow'); assert writes.empty && calls.last() == [300,'updateNow']
settings.endTimeStr = '22:30'
def today = new Date()
def rise = call('localTimeOnDay','07:00',today), set = call('localTimeOnDay','17:00',today)
binding.setVariable('getSunriseAndSunset', { [sunrise: rise, sunset: set] })
call('updateNow')
assert writes.size() == 2 && writes[0][0] == 'dim' && writes[1][0] == 'ct'
assert calls.last()[0] > 0 && calls.last()[1] == 'updateNow'
writes.clear()
binding.setVariable('getSunriseAndSunset', { [sunrise: new Date(0), sunset: new Date(3600000)] })
call('updateNow'); assert writes.empty && calls.last() == [300,'updateNow']
settings.dimEveningLagMins = 'bad'
call('updateNow'); assert writes.empty && calls.last() == [300,'updateNow']
println 'PASS: DST, time parsing, aliases, snapshot reads, bounds, curves, anchors and recovery'
