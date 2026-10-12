# Mailcal

A mailbox.org calendar for the Light Phone III: see your agenda or a month view, add, edit and delete events. Black and white, styled like LightOS.

A regular Android app (sideloaded, not a Light SDK tool). It still shows up in LightOS's tool list.

## Download

Get the APK from [Releases](https://github.com/jmsbrain/mailcal/releases) and install it from a computer:

```
adb install -r mailcal-1.0.0-beta.3.apk
```

## How it works

Mailcal talks to mailbox.org directly over CalDAV. It does **not** use DAVx5 or the phone's built-in calendar, so there's no sync between the two that can break or reset.

- The calendar on mailbox.org is the only real copy. Edits go straight to the server and are refused if the event changed elsewhere first, so nothing gets silently overwritten.
- The app keeps an offline copy (30 days back, a year ahead) so the agenda opens without signal. Changes need a connection.
- The copy refreshes when you open the app and in the background about every 30 minutes when online (Android may space this out when the phone is idle). Only calendars that changed are downloaded.
- If the offline copy is ever lost, the app just downloads it again. Nothing on mailbox.org is touched.
- Editing a repeating event asks whether to change **this one** or **all**. Edits change only the fields shown and keep everything else in the event (attendees and so on).

## Feeds

Mailcal can also show subscribed .ics feeds (what SubcalScriber does), read-only. Switch between **MAILBOX** and **FEEDS** with the tabs under the top bar, or swipe sideways on the agenda (in month view, swipe below the grid; swiping on the grid changes months). Add feeds from MENU → FEEDS by typing or pasting a link; Google share links work for public calendars. Feeds refresh hourly, when the app opens and in the background.

## Month view

Tap MONTH on the agenda for a month grid. Days with events get a dot per event (up to three). Tap a day to list its events under the grid; swipe or use PREV/NEXT to change months. NEW starts on the selected day. LIST goes back, and the app remembers which view you used last.

## Reminders

Pick a reminder when adding or editing an event: at the time of the event, 30 minutes, 1 hour, 12 hours, 1 day, 3 days, 1 week or 2 weeks before. It's saved in the event on mailbox.org, so other devices see it too.

The phone shows a notification (default sound) when a reminder is due. That includes reminders set elsewhere, such as in mailbox.org's web calendar.

Events with several reminders, or reminders at other times, show "Custom" and are left as they are unless you pick a new one.

## Signing in

Use your mailbox.org email and password. If you use two-factor sign-in, make an app password in mailbox.org settings and use that instead. The server defaults to `https://dav.mailbox.org/`; any CalDAV server should work.

The password is kept on the phone, encrypted with a key in the Android Keystore, and only sent to that server.

## Permissions

- Internet, to reach the CalDAV server.
- Notifications, exact alarms and start at boot, for reminders. Notifications are asked for the first time you pick a reminder.

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
