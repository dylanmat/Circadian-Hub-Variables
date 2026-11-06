# Offline regression checks

Run from the repository root with Java and the Apache Groovy 2.4.21 groovy-all runtime:

```powershell
java -cp <absolute-path-to-groovy-all-2.4.21.jar> groovy.ui.GroovyMain tests/circadian_regression.groovy
```

Obtain the runtime from Maven Central (`org.codehaus.groovy:groovy-all:2.4.21`) outside the tracked source tree. Do not commit downloaded binaries. The test runner parses the actual app and stubs Hubitat APIs; it does not connect to the hub or command devices. Assertions cover calendar/DST behavior, time parsing, variable snapshot validation, aliases, ranges, solar/legacy anchors, curve bounds/continuity, narrow windows, and hold/retry behavior. Printed winter/summer previews use synthetic sun times rather than location observations.

This is a local Groovy compatibility check, not the Hubitat compiler. Hubitat API availability, sandbox restrictions, real sunrise events, lifecycle/subscription delivery, and live scheduling require separate authorized hub checks before deployment. Remaining roadmap Next/Later behavior is not claimed as fixed by these tests.
