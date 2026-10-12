# Mailcal

A mailbox.org (CalDAV) calendar: agenda, add, edit, delete.

A regular Android app for the Light Phone III (sideloaded, not a Light SDK tool). Styled to match LightOS.

## Rules

- Kotlin + Jetpack Compose, `foundation` only (no Material). Black background, white text, Akkurat when present, LightOS sizes (see `LightStyle.kt`).
- Ask for as few permissions as possible. No internet permission unless the app truly needs it, and say so in the README.
- Keep code comments short.
- Versions are MAJOR.MINOR.PATCH (`versionName`) plus `versionCode` +1 per release, in `app/build.gradle.kts`. Tag releases `vX.Y.Z`.
- Commit as you go. Ask before pushing. Never force-push yourself.
- If this should later become an official Light tool, it needs to be rebuilt on a fork of lightphone/light-sdk. Start it with `/new-tool` as a Light tool instead.

## Build and install

```
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat :app:assembleDebug
adb -s <serial> install -r app\build\outputs\apk\debug\app-debug.apk
```

Use made-up data for screenshots.

## Project notes

- Needs INTERNET (CalDAV), plus notifications/exact alarms/boot for reminders (`Reminders.kt`); WorkManager background refresh every 30 min (`SyncWorker.kt`). No DAVx5 / CalendarContract: the server is the only copy, edits PUT with If-Match.
- `VCal.kt` edits raw iCalendar so unknown properties survive; cover changes with `app/src/test/.../VCalTest.kt` (`gradlew :app:testDebugUnitTest`).
- `Ics.kt` (parser + recurrence expansion) came from SubcalScriber.
- Test end to end on the emulator with `tools/MockCalDav.java` (debug build allows http to 10.0.2.2 only). Never test with the user's real account.
