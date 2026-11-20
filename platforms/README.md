# LockedIn platform matrix

One set of rules, several native shells. The shared layer decides *what* should happen (session
times, entitlement gating, conflict resolution, AI request and response formats, the allowlist
decision). Each platform decides *how* it enforces that, because enforcement is OS-specific and can't
be shared.

| Platform | Shared rules | Native enforcement | Verified by |
| --- | --- | --- | --- |
| Android | `shared/src/main/kotlin` (compiled into the app) | AccessibilityService, Device Admin, alarms, telephony | JVM spec checks; debug APK built in CI |
| Web | `platforms/shared-ts` | None: companion and study tools only, cannot block other apps | Node spec tests in CI |
| Windows | `platforms/shared-ts` | Not started. Needs a native shell and an OS-level blocking design | Not built |
| macOS | `platforms/shared-ts` for the shell; `platforms/apple/LockedInCore` for rules | Not started. Needs a native shell | Swift spec tests in CI |
| iOS | `platforms/apple/LockedInCore` | Family Controls, ManagedSettings, DeviceActivity. Needs a paid Apple Developer membership and entitlement approval | Swift spec tests in CI; no device build |
| Backend | `backend/` | Supabase Edge Function and Postgres | SQL and Node tests in CI; not deployed |

## Rules that must stay identical

`shared/spec/vectors.json` is the single source of truth. The Kotlin reference, the TypeScript
package and the Swift package each run the same file. A change to a rule starts with a change to the
vectors, and every platform must pass before merge.

## What is not shared, and why

- Blocking apps and call handling: OS-specific APIs. Kept native on each platform.
- Sign-in storage: tokens are kept in memory on Android for now. Each platform needs its own secure
  store (Android Keystore, iOS Keychain, desktop credential stores).
- Gemini calls: never made from a client on any platform. They go through the server function.
