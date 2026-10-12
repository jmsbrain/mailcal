# Mailcal

A mailbox.org calendar for the Light Phone III: see your agenda, add, edit and delete events. Black and white, styled like LightOS.

A regular Android app (sideloaded, not a Light SDK tool). It still shows up in LightOS's tool list.

## Download

Get the APK from [Releases](https://github.com/jmsbrain/mailcal/releases) and install it from a computer:

```
adb install -r mailcal-1.0.0-beta.apk
```

## How it works

Mailcal talks to mailbox.org directly over CalDAV. It does **not** use DAVx5 or the phone's built-in calendar, so there's no sync between the two that can break or reset.

- The calendar on mailbox.org is the only real copy. Edits go straight to the server and are refused if the event changed elsewhere first, so nothing gets silently overwritten.
- The app keeps an offline copy (30 days back, a year ahead) so the agenda opens without signal. Changes need a connection.
- If the offline copy is ever lost, the app just downloads it again. Nothing on mailbox.org is touched.
- Editing a repeating event asks whether to change **this one** or **all**. Edits change only the fields shown and keep everything else in the event (alarms, attendees and so on).

## Signing in

Use your mailbox.org email and password. If you use two-factor sign-in, make an app password in mailbox.org settings and use that instead. The server defaults to `https://dav.mailbox.org/`; any CalDAV server should work.

The password is kept on the phone, encrypted with a key in the Android Keystore, and only sent to that server.

## Permissions

Internet only, to reach the CalDAV server.

## Build and install

```
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat :app:assembleDebug
adb -s <serial> install -r app\build\outputs\apk\debug\app-debug.apk
```

## Trying it without an account

`tools/MockCalDav.java` is a fake CalDAV server with made-up events:

```
java tools/MockCalDav.java
```

On the emulator, sign in with any email and password and server `http://10.0.2.2:5232/`. (Only debug builds allow plain http, and only to that address.)

## License

MIT
