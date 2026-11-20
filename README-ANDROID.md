# Locked In — Android

This is the phone app. It is not a website. While a lock is running, if another app comes to the front, Locked In pulls the black screen back. Settings is included. The dialer is not.

## Install on your phone this week

1. Build `app-release.apk` (Android Studio: Open this `android` folder, then Build > Build APK).
2. Copy the APK to the phone and open it. Allow install from that source if Android asks.
3. Open Locked In.
4. Tap **Allow the lock**. In the list, turn on **Locked In**.
5. Come back. Tap **Block uninstall** and activate the admin.
6. Check the box. Type one line. Tap **Lock in**.
7. Test for 25 minutes before any long lock. Press Home, then try to open Instagram. The black screen has to return. Place a test call. Only then use a long lock for school.

There is no cancel. You get the phone back when the time ends, then tap **I'm back**.

Emergency numbers 911, 112, and 999 open the real dialer. That is the way out of a real emergency, not a way back into Instagram.

## What this week is

Sideload the APK and use it on your phone. The Play Store is a second step: a developer account costs $25 and review can take longer than a week. Do not wait on the store to start school.

The website version does not do this job. Do not ship that as the lock.

## Plus and redemption codes (behaviour change, 20 Nov 2026)

- The public redemption codes were compromised and are removed from the app. They no longer work.
- Passes stored by earlier app versions (`plusUntil`) are no longer honoured. They are deleted on first
  use. This is a deliberate change: Plus features are off on every device until a server-side grant
  exists.
- Plus will be stored under `serverPlusUntil`, written only from a server-confirmed expiry
  (`Pass.applyServerExpiry`). Server sync is not wired yet.
- Known limit: code already shipped in installed APKs and in git history still contains the old codes.
  Removing them from the source does not change old installs; that needs the server-side grant path.

## AI study session (unverified build)

- `AiSessionActivity` sends one request to the `ai-assist` function through `AiClient`. The device holds
  no Gemini key.
- `AiClient.FUNCTION_URL` is blank until the Supabase project exists. Device sign-in does not exist yet,
  so requests return "not signed in". The screen shows these states instead of fake answers.
- `app/build.gradle.kts` compiles `shared/src/main/kotlin` into the app. The shared code has no Android
  imports.
